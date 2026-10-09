package com.source.player.data.freebox

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import com.source.player.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-wide "can the Freebox be reached right now, and how" state.
 *
 * [EndpointResolver] alone cannot answer it: it reports REMOTE as soon as a remote
 * config is stored, without checking that the box answers. A [probe] therefore does a
 * forced endpoint resolution plus a real login ([FreeboxClient.checkConnection]).
 *
 * Probes are triggered by pairing changes, default-network changes (the cached endpoint
 * and session are dropped first, debounced [NETWORK_DEBOUNCE_MS]) and [reportFailure].
 * Non-forced probes are throttled to one per [MIN_PROBE_INTERVAL_MS]. Started from
 * SourceApplication, like MediaStoreWatcher.
 */
@Singleton
class FreeboxReachability
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val client: FreeboxClient,
        private val resolver: EndpointResolver,
) {

    /**
     * [AUTH_REVOKED]: a token is stored but the box refuses it (app access revoked or
     * deleted in Freebox OS) — re-pairing fixes it, waiting does not.
     */
    enum class Reach { UNKNOWN, LAN, REMOTE, UNREACHABLE, NOT_PAIRED, AUTH_REVOKED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(Reach.UNKNOWN)
    val state: StateFlow<Reach> = _state.asStateFlow()

    /** False for NOT_PAIRED / AUTH_REVOKED / UNREACHABLE; UNKNOWN counts as available (no flash at launch). */
    val available: StateFlow<Boolean> =
            _state.map { it.isAvailable() }.stateIn(scope, SharingStarted.Eagerly, true)

    private val started = AtomicBoolean(false)
    private val probeMutex = Mutex()

    /** elapsedRealtime of the last probe start; 0 = never. */
    @Volatile private var lastProbeAt = 0L
    @Volatile private var scheduledProbe: Job? = null

    /** Set by network callbacks; consumed (endpoint + session dropped) before the next probe. */
    @Volatile private var networkChanged = false
    /** replay = 1: a trigger emitted before the debounced collector subscribes is not lost. */
    private val probeTriggers =
            MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Last default network and its transports, to ignore capability noise (signal strength…). */
    private var lastNetworkKey: String? = null

    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return

        // The launch probe and the callback's initial onAvailable land in the same
        // debounce window: one login at startup, not two.
        scope.launch {
            probeTriggers.debounce(NETWORK_DEBOUNCE_MS).collect {
                if (networkChanged) {
                    networkChanged = false
                    resolver.invalidate()
                    client.dropSession()
                }
                probe(force = true)
            }
        }

        scope.launch {
            client.isPaired.collect { paired ->
                if (!paired) {
                    scheduledProbe?.cancel()
                    _state.value = Reach.NOT_PAIRED
                } else {
                    if (_state.value == Reach.NOT_PAIRED) _state.value = Reach.UNKNOWN
                    probeTriggers.tryEmit(Unit)
                }
            }
        }

        registerNetworkCallback()
    }

    /**
     * Forced endpoint resolution + real login. Skipped when the last probe is younger
     * than [MIN_PROBE_INTERVAL_MS], unless [force]. Always runs off the main thread.
     */
    suspend fun probe(force: Boolean = false) {
        withContext(Dispatchers.IO) {
            probeMutex.withLock {
                val now = SystemClock.elapsedRealtime()
                if (!force && lastProbeAt != 0L && now - lastProbeAt < MIN_PROBE_INTERVAL_MS) return@withLock
                lastProbeAt = now

                if (!client.isPaired.first()) {
                    _state.value = Reach.NOT_PAIRED
                    return@withLock
                }
                val next =
                        client.checkConnection().fold(
                                onSuccess = { status ->
                                    if (status.mode == ConnectionMode.LAN) Reach.LAN else Reach.REMOTE
                                },
                                onFailure = { e ->
                                    log("probe failed: ${e.javaClass.simpleName}: ${e.message}")
                                    // The token may have been cleared while the probe ran.
                                    when {
                                        !client.isPaired.first() -> Reach.NOT_PAIRED
                                        e is FreeboxAuthException -> Reach.AUTH_REVOKED
                                        else -> Reach.UNREACHABLE
                                    }
                                },
                        )
                log("probe: $next")
                _state.value = next
            }
        }
    }

    /**
     * A Freebox network operation just failed (e.g. playback could not connect): mark
     * the box unreachable now and re-probe as soon as the throttle allows.
     */
    fun reportFailure() {
        if (_state.value == Reach.NOT_PAIRED) return
        // A revoked pairing stays revoked: the re-probe below confirms or clears it.
        if (_state.value != Reach.AUTH_REVOKED) _state.value = Reach.UNREACHABLE
        if (scheduledProbe?.isActive == true) return
        scheduledProbe =
                scope.launch {
                    val elapsed = SystemClock.elapsedRealtime() - lastProbeAt
                    if (lastProbeAt != 0L && elapsed < MIN_PROBE_INTERVAL_MS) {
                        delay(MIN_PROBE_INTERVAL_MS - elapsed)
                    }
                    probe(force = false)
                }
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        onNetwork(network, cm.getNetworkCapabilities(network))
                    }

                    override fun onLost(network: Network) {
                        onNetwork(null, null)
                    }

                    override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                    ) {
                        onNetwork(network, networkCapabilities)
                    }
                }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
                .onFailure { log("network callback not registered: ${it.message}") }
    }

    /** Called on the ConnectivityThread: only flags + emits, the work happens on [scope]. */
    @Synchronized
    private fun onNetwork(network: Network?, caps: NetworkCapabilities?) {
        val key = network?.let { "$it:${caps?.transportKey().orEmpty()}" }
        if (key == lastNetworkKey) return
        lastNetworkKey = key
        log("default network changed: $key")
        networkChanged = true
        probeTriggers.tryEmit(Unit)
    }

    private fun NetworkCapabilities.transportKey(): String =
            TRANSPORTS.filter { hasTransport(it) }.joinToString(",")

    private fun Reach.isAvailable(): Boolean =
            this != Reach.NOT_PAIRED && this != Reach.UNREACHABLE && this != Reach.AUTH_REVOKED

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "SourceFbx"
        const val NETWORK_DEBOUNCE_MS = 2_000L
        const val MIN_PROBE_INTERVAL_MS = 30_000L
        val TRANSPORTS =
                intArrayOf(
                        NetworkCapabilities.TRANSPORT_WIFI,
                        NetworkCapabilities.TRANSPORT_CELLULAR,
                        NetworkCapabilities.TRANSPORT_ETHERNET,
                        NetworkCapabilities.TRANSPORT_VPN,
                )
    }
}
