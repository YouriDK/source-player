package com.source.player.data.freebox.library

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.SOURCE_FREEBOX
import com.source.player.data.freebox.FreeboxClient
import com.source.player.data.freebox.FreeboxFolder
import com.source.player.data.freebox.FreeboxReachability
import com.source.player.data.freebox.FreeboxReachability.Reach
import com.source.player.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What [FreeboxLibrarySync.maybeScan] decided. */
enum class ScanTrigger {
    /** A pass was launched in the background. */
    STARTED,
    SKIPPED_NO_FOLDER,
    /** Not paired, or the box refuses the token (pairing revoked). */
    SKIPPED_NOT_PAIRED,
    /** Box not on the LAN (or not reachable at all), or the network is metered. */
    SKIPPED_NOT_LAN,
    /** Last complete pass is younger than [FreeboxLibrarySync.MIN_SCAN_INTERVAL_MS] (non-forced calls only). */
    SKIPPED_RECENT,
    /** A pass is already running. */
    SKIPPED_RUNNING,
}

/**
 * Decides when the Freebox music folder is (re)indexed by [FreeboxLibraryScanner].
 *
 * - Music folder changed (vs. [AppPreferences.freeboxIndexedRoot]): purge the old index,
 *   then a forced scan of the new folder. No folder (none chosen, or unpaired): purge.
 * - [maybeScan] from `MainActivity.onStart`, from the box becoming reachable on the LAN,
 *   and from the Settings Scan button (forced, optionally over mobile data).
 * - Automatic scans only on the LAN over an unmetered network, at most one complete
 *   pass per [MIN_SCAN_INTERVAL_MS].
 *
 * Scans run in this singleton's scope, never in the caller's. Every decision is taken
 * under one lock, so simultaneous triggers start a single pass. Started from
 * SourceApplication, like MediaStoreWatcher.
 */
@Singleton
class FreeboxLibrarySync
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val client: FreeboxClient,
        private val reachability: FreeboxReachability,
        private val scanner: FreeboxLibraryScanner,
        private val artCache: FreeboxArtCache,
        private val songDao: SongDao,
        private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    /** Guards the folder reconciliation and the scan decision. Never held during a pass. */
    private val lock = Mutex()

    private val _isScanning = MutableStateFlow(false)
    /** True from the moment a pass is launched until it returns. */
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** "Scanning Freebox… n / N", null when idle. */
    val progressMessage: StateFlow<String?> = scanner.progressMessage

    /** Message of the last pass that did not complete, null after a complete pass. */
    val lastError: StateFlow<String?> = scanner.lastError

    fun start() {
        if (!started.compareAndSet(false, true)) return

        scope.launch {
            client.startFolder.distinctUntilChanged().collect { folder ->
                val changed = lock.withLock { reconcile(folder) }
                if (changed && folder != null) {
                    log("music folder changed to ${folder.url}: ${maybeScan(force = true)}")
                }
            }
        }

        // Back on the home network: catch up (still subject to the 30 min rule).
        scope.launch {
            reachability.state.collect { reach ->
                if (reach == Reach.LAN) log("LAN reachable: ${maybeScan()}")
            }
        }
    }

    /**
     * Launches a background scan of the music folder if the conditions hold: folder set,
     * paired, box on the LAN (or reachable remotely when [allowRemote]), unmetered
     * network (unless [allowRemote]), and — unless [force] — no complete pass in the last
     * [MIN_SCAN_INTERVAL_MS]. [allowRemote] = true is the user-confirmed scan over mobile
     * data: reads are then allowed outside the LAN. Returns as soon as the decision is
     * made; the pass runs in this singleton's scope.
     */
    suspend fun maybeScan(force: Boolean = false, allowRemote: Boolean = false): ScanTrigger =
            withContext(Dispatchers.IO) {
                lock.withLock {
                    val folder = client.startFolder.first()
                    reconcile(folder)
                    if (folder == null) return@withLock ScanTrigger.SKIPPED_NO_FOLDER
                    if (!client.isPaired.first()) return@withLock ScanTrigger.SKIPPED_NOT_PAIRED
                    if (_isScanning.value) return@withLock ScanTrigger.SKIPPED_RUNNING
                    if (!force) {
                        val last = prefs.freeboxLastScanAt.first()
                        val age = System.currentTimeMillis() - last
                        if (last > 0 && age in 0 until MIN_SCAN_INTERVAL_MS) {
                            return@withLock ScanTrigger.SKIPPED_RECENT
                        }
                    }

                    reachability.probe()
                    when (reachability.state.value) {
                        Reach.LAN -> Unit
                        Reach.REMOTE -> if (!allowRemote) return@withLock ScanTrigger.SKIPPED_NOT_LAN
                        Reach.NOT_PAIRED, Reach.AUTH_REVOKED -> return@withLock ScanTrigger.SKIPPED_NOT_PAIRED
                        Reach.UNKNOWN, Reach.UNREACHABLE -> return@withLock ScanTrigger.SKIPPED_NOT_LAN
                    }
                    if (!allowRemote && isNetworkMetered()) return@withLock ScanTrigger.SKIPPED_NOT_LAN

                    launchScan(folder, requireLan = !allowRemote)
                    ScanTrigger.STARTED
                }
            }

    /**
     * Aligns the index on [folder]. Returns true when the index was purged because the
     * music folder changed (or disappeared). Call under [lock].
     */
    private suspend fun reconcile(folder: FreeboxFolder?): Boolean {
        val indexed = prefs.freeboxIndexedRoot.first()
        if (folder?.url == indexed) {
            // Unpairing clears the indexed root together with the folder: rows may remain.
            if (folder != null || !hasFreeboxRows()) return false
        }
        log("index root ${indexed ?: "none"} -> ${folder?.url ?: "none"}: purging")
        if (_isScanning.value) scanner.requestCancel()
        scanner.purge()
        prefs.setFreeboxIndexedRoot(folder?.url)
        return true
    }

    private suspend fun hasFreeboxRows(): Boolean = songDao.countBySourceFlow(SOURCE_FREEBOX).first() > 0

    /** Call under [lock]. */
    private fun launchScan(root: FreeboxFolder, requireLan: Boolean) {
        _isScanning.value = true
        scope.launch {
            try {
                val outcome = scanner.scan(root, requireLan = requireLan)
                log("scan of ${root.url} (requireLan=$requireLan): ${outcome.status}")
                if (outcome.status == ScanStatus.COMPLETE) {
                    lock.withLock {
                        // A purge for another folder may have run meanwhile.
                        if (prefs.freeboxIndexedRoot.first() == root.url) {
                            prefs.setFreeboxLastScanAt(System.currentTimeMillis())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Freebox scan failed: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                runCatching { artCache.refreshSize() }
                _isScanning.value = false
            }
            // The folder changed during the pass: its trigger saw SKIPPED_RUNNING and the
            // purge discarded this pass, so scan the new folder now.
            val current = prefs.freeboxIndexedRoot.first()
            if (current != null && current != root.url) {
                log("music folder changed during the scan: ${maybeScan(force = true)}")
            }
        }
    }

    private fun isNetworkMetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        return cm.isActiveNetworkMetered
    }

    private fun log(message: String) = Log.i(TAG, message)

    companion object {
        private const val TAG = "FreeboxSync"
        /** Minimum age of the last complete pass before an automatic rescan. */
        const val MIN_SCAN_INTERVAL_MS = 30L * 60 * 1000
    }
}
