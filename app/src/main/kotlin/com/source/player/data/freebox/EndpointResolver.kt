package com.source.player.data.freebox

import android.util.Log
import com.source.player.BuildConfig
import com.source.player.data.freebox.api.ApiUrls
import com.source.player.data.freebox.api.FreeboxApiClient
import com.source.player.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the base URL for API calls: LAN first (quick discovery probe, which also
 * refreshes the stored endpoint metadata), remote *.fbxos.fr as fallback.
 *
 * On LAN the API is reached over plain HTTP at mafreebox.freebox.fr (or the manual
 * host override) — explicitly supported by the API. Remote access is HTTPS-only,
 * validated against the embedded Freebox Root CAs (network_security_config.xml).
 */
@Singleton
class EndpointResolver @Inject constructor(
    private val api: FreeboxApiClient,
    private val prefs: AppPreferences,
) {

    data class Endpoint(val base: String, val mode: ConnectionMode)

    @Volatile
    var current: Endpoint? = null
        private set

    /** Duration of the last successful LAN discovery probe. */
    @Volatile
    var lastLatencyMs: Long? = null
        private set

    private val mutex = Mutex()

    suspend fun resolve(forceProbe: Boolean = false): FbxResult<Endpoint> = mutex.withLock {
        if (!forceProbe) current?.let { return it.ok() }

        val snapshot = prefs.freeboxSettings.first()
        val lanHost = snapshot.manualHost ?: ApiUrls.LAN_HOST

        val startedAt = System.nanoTime()
        when (val discovery = api.apiVersion(lanHost).also {
            if (it is FbxResult.Ok) lastLatencyMs = (System.nanoTime() - startedAt) / 1_000_000
        }) {
            is FbxResult.Ok -> {
                prefs.saveFreeboxDiscovery(discovery.value)
                val dto = discovery.value
                val base = ApiUrls.apiBase("http", lanHost, null, dto.apiBaseUrl, dto.apiVersion)
                log("LAN endpoint: $base (api_version=${dto.apiVersion}, https_available=${dto.httpsAvailable})")
                Endpoint(base, ConnectionMode.LAN).also { current = it }.ok()
            }
            is FbxResult.Err -> {
                val remoteBase = snapshot.remoteBase()
                if (remoteBase != null) {
                    log("LAN discovery failed (${discovery.error}), falling back to remote: $remoteBase")
                    Endpoint(remoteBase, ConnectionMode.REMOTE).also { current = it }.ok()
                } else {
                    log("LAN discovery failed (${discovery.error}) and no remote config stored")
                    FbxResult.Err(FreeboxError.BoxNotFound)
                }
            }
        }
    }

    fun invalidate() {
        current = null
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "SourceFbx"
    }
}
