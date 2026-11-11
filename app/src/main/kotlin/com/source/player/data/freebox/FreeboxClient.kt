package com.source.player.data.freebox

import com.source.player.data.freebox.api.FileInfoDto
import com.source.player.data.freebox.api.FreeboxApiClient
import com.source.player.data.freebox.api.FsTaskDto
import com.source.player.data.freebox.api.PathCodec
import com.source.player.data.freebox.auth.AppTokenStore
import com.source.player.data.freebox.auth.FreeboxPairingManager
import com.source.player.data.freebox.auth.FreeboxSessionManager
import com.source.player.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source-side identifiers of Freebox files: "fbx://" + the display path the API
 * decodes to, minus its leading '/'. Example: "fbx://Disque dur/Musique/A/B.flac".
 * Not a strict URI (spaces are kept verbatim) — it is an opaque, stable id.
 */
object FreeboxPaths {
    const val SCHEME = "fbx://"

    fun isFreeboxUrl(s: String?): Boolean = s != null && s.startsWith(SCHEME)

    /** Display path (as PathCodec.decode gives it, e.g. "/Disque dur/Musique/A/B.flac") → fbx:// url. */
    fun toUrl(displayPath: String): String = SCHEME + displayPath.trim('/')

    /** "fbx://Disque dur/Musique" → "Disque dur/Musique"; root → "". */
    fun toDisplayPath(url: String): String = url.removePrefix(SCHEME).trim('/')

    /**
     * Base64 path for the API. Real Freebox paths are rooted ("/Disque dur/…", the
     * v16 lesson), so the leading '/' stripped by [toUrl] is restored before
     * encoding; the root url maps to PathCodec.ROOT (the disks listing).
     */
    fun toB64(url: String): String = PathCodec.encode("/" + toDisplayPath(url))

    /** No trailing slash; the parent of a top-level entry (a disk) is "fbx://". */
    fun parentUrl(url: String): String {
        val display = toDisplayPath(url)
        val slash = display.lastIndexOf('/')
        return if (slash < 0) SCHEME else SCHEME + display.substring(0, slash)
    }

    fun name(url: String): String = toDisplayPath(url).substringAfterLast('/')

    /** Url of the entry [name] inside the folder [parentUrl]. */
    fun child(parentUrl: String, name: String): String {
        val parent = toDisplayPath(parentUrl)
        return toUrl(if (parent.isEmpty()) name else "$parent/$name")
    }

    /** True when [url] is [ancestorUrl] itself or lies anywhere below it. */
    fun isSameOrUnder(url: String, ancestorUrl: String): Boolean {
        val path = toDisplayPath(url)
        val ancestor = toDisplayPath(ancestorUrl)
        return path == ancestor || path.startsWith("$ancestor/")
    }
}

data class RemoteEntry(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val size: Long,
    /** Epoch MILLIS. */
    val lastModified: Long,
)

/** Not paired, pairing revoked, or the box refused the session. */
class FreeboxAuthException(msg: String, cause: Throwable? = null) : IOException(msg, cause)

/**
 * The box refused a file operation because Source's app permissions don't include
 * "explorer" (Freebox OS > Paramètres > Gestion des accès > Applications).
 */
class FreeboxPermissionException(msg: String) : IOException(msg)

/** An fs task (move) is still running on the box after the polling budget ran out. */
class FreeboxTaskTimeoutException(msg: String) : IOException(msg)

/** Box not reachable on LAN nor remotely (DNS, timeout, TLS, no remote config…). */
class FreeboxUnreachableException(msg: String, cause: Throwable? = null) : IOException(msg, cause)

data class FreeboxFolder(val url: String, val displayPath: String, val name: String)

enum class ConnectionMode { LAN, REMOTE }

data class FreeboxStatus(val boxName: String?, val mode: ConnectionMode, val latencyMs: Long?)

data class StreamingAccess(val base: String, val sessionToken: String) {
    fun downloadUrl(fbxUrl: String): String = "$base/dl/${FreeboxPaths.toB64(fbxUrl)}"

    companion object {
        const val AUTH_HEADER = "X-Fbx-App-Auth"
    }
}

/**
 * Frozen facade of the Freebox OS API network source. Everything outside
 * `data.freebox` should go through this class (plus FreeboxApiClient.openDownloadStream
 * for the HTTP proxy, fed by [streamingAccess]).
 */
@Singleton
class FreeboxClient @Inject constructor(
    private val api: FreeboxApiClient,
    private val sessions: FreeboxSessionManager,
    private val pairing: FreeboxPairingManager,
    private val resolver: EndpointResolver,
    private val tokenStore: AppTokenStore,
    private val prefs: AppPreferences,
) {

    /** Bumped whenever the app_token changes (encrypted prefs are not observable). */
    private val tokenRevision = MutableStateFlow(0)

    val isPaired: Flow<Boolean> =
        tokenRevision
            .map { withContext(Dispatchers.IO) { readToken() != null } }
            .distinctUntilChanged()

    /** Optional folder the Freebox browser opens on; null = the disks listing. */
    val startFolder: Flow<FreeboxFolder?> =
        prefs.freeboxSettings
            .map { s ->
                val display = s.startFolderDisplay
                    ?: s.startFolderB64?.let { runCatching { PathCodec.decode(it) }.getOrNull() }
                    ?: return@map null
                val url = FreeboxPaths.toUrl(display)
                FreeboxFolder(url = url, displayPath = FreeboxPaths.toDisplayPath(url), name = FreeboxPaths.name(url))
            }
            .distinctUntilChanged()

    val boxName: Flow<String?> = prefs.freeboxSettings.map { it.boxName }.distinctUntilChanged()

    fun pair(manualHost: String?): Flow<FreeboxPairingManager.PairingEvent> =
        pairing.pair(manualHost)
            .onEach { event ->
                if (event is FreeboxPairingManager.PairingEvent.Granted) {
                    sessions.dropSession()
                    tokenRevision.update { it + 1 }
                }
            }
            .flowOn(Dispatchers.IO)

    suspend fun unpair() {
        withContext(Dispatchers.IO) { runCatching { tokenStore.clear() } }
        sessions.dropSession()
        prefs.clearFreeboxSettings()
        resolver.invalidate()
        tokenRevision.update { it + 1 }
    }

    suspend fun checkConnection(): Result<FreeboxStatus> {
        val endpoint = when (val resolved = resolver.resolve(forceProbe = true)) {
            is FbxResult.Ok -> resolved.value
            is FbxResult.Err -> return Result.failure(resolved.error.toException("resolve"))
        }
        val startedAt = System.nanoTime()
        when (val login = sessions.freshLogin()) {
            is FbxResult.Ok -> Unit
            is FbxResult.Err -> return Result.failure(login.error.toException("login"))
        }
        val loginMs = (System.nanoTime() - startedAt) / 1_000_000
        val latency = if (endpoint.mode == ConnectionMode.LAN) resolver.lastLatencyMs ?: loginMs else loginMs
        val name = prefs.freeboxSettings.first().boxName
        return Result.success(FreeboxStatus(boxName = name, mode = endpoint.mode, latencyMs = latency))
    }

    /**
     * Lists a folder ([url] null = the disks). [requireLan] = true throws
     * [FreeboxUnreachableException] unless the box is reached over the LAN (the
     * library scan must never run over mobile data).
     *
     * Hidden and dot-named entries are dropped. That is also what keeps the Folders
     * tab's trash (".corbeille" at the root of the music folder, see
     * FreeboxFileOperations) out of the browser and out of the library scan.
     */
    suspend fun list(url: String?, requireLan: Boolean = false): List<RemoteEntry> {
        val parentUrl = url?.let { FreeboxPaths.toUrl(FreeboxPaths.toDisplayPath(it)) } ?: FreeboxPaths.SCHEME
        if (requireLan) ensureLan(parentUrl)
        val pathB64 = FreeboxPaths.toB64(parentUrl)
        val outcome = sessions.withSession { base, token ->
            api.ls(base, token, pathB64, removeHidden = true)
        }
        val entries = when (outcome) {
            is FbxResult.Ok -> outcome.value
            is FbxResult.Err -> throw outcome.error.toException(parentUrl)
        }
        return entries
            .filter { !it.hidden && !it.name.startsWith(".") && it.name != "." && it.name != ".." }
            .map { it.toRemoteEntry(parentUrl) }
    }

    /**
     * Like [list] but keeps hidden and dot-named entries (the trash folder, its content):
     * file operations need the real listing to detect name conflicts.
     */
    suspend fun listAll(url: String): List<RemoteEntry> {
        val parentUrl = FreeboxPaths.toUrl(FreeboxPaths.toDisplayPath(url))
        val pathB64 = FreeboxPaths.toB64(parentUrl)
        val outcome = sessions.withSession { base, token -> api.ls(base, token, pathB64) }
        val entries = when (outcome) {
            is FbxResult.Ok -> outcome.value
            is FbxResult.Err -> throw outcome.error.toException(parentUrl)
        }
        return entries
            .filter { it.name.isNotEmpty() && it.name != "." && it.name != ".." && '/' !in it.name }
            .map { it.toRemoteEntry(parentUrl) }
    }

    /** Creates the folder [name] in [parentUrl]; returns its url. */
    suspend fun mkdir(parentUrl: String, name: String): String {
        val parent = FreeboxPaths.toUrl(FreeboxPaths.toDisplayPath(parentUrl))
        val outcome = sessions.withSession { base, token ->
            api.mkdir(base, token, FreeboxPaths.toB64(parent), name)
        }
        if (outcome is FbxResult.Err) throw outcome.error.toException("mkdir $parent/$name")
        return FreeboxPaths.child(parent, name)
    }

    /** Renames [url] in place to [newName]; returns the new url. */
    suspend fun rename(url: String, newName: String): String {
        val source = FreeboxPaths.toUrl(FreeboxPaths.toDisplayPath(url))
        val outcome = sessions.withSession { base, token ->
            api.rename(base, token, FreeboxPaths.toB64(source), newName)
        }
        if (outcome is FbxResult.Err) throw outcome.error.toException("rename $source")
        return FreeboxPaths.child(FreeboxPaths.parentUrl(source), newName)
    }

    /**
     * Moves [urls] into the folder [destUrl] (fs/mv, mode "skip": an existing destination
     * entry is never overwritten — callers check for conflicts first) and waits for the
     * box's task to finish. Works over the remote access as well as on the LAN.
     */
    suspend fun move(urls: List<String>, destUrl: String) {
        if (urls.isEmpty()) return
        val dest = FreeboxPaths.toUrl(FreeboxPaths.toDisplayPath(destUrl))
        val files = urls.map { FreeboxPaths.toB64(it) }
        // One session call per request: a re-login while polling must retry the poll,
        // never re-issue the move.
        val started = sessions.withSession { base, token -> api.mv(base, token, files, FreeboxPaths.toB64(dest)) }
        val outcome = when (started) {
            is FbxResult.Ok -> awaitFsTask(
                initial = started.value,
                poll = { id -> sessions.withSession { base, token -> api.fsTask(base, token, id) } },
                forget = { id -> sessions.withSession { base, token -> api.deleteTask(base, token, id) } },
            )
            is FbxResult.Err -> started
        }
        if (outcome is FbxResult.Err) {
            val error = outcome.error
            if (error is FreeboxError.Api && error.code == TASK_TIMEOUT) {
                throw FreeboxTaskTimeoutException("The Freebox is still moving the files")
            }
            throw error.toException("move to $dest")
        }
    }

    /**
     * Reads at most [length] bytes of the file at [url] from [offset] (HTTP Range on
     * /dl/, the response is released as soon as enough bytes arrived). A short array
     * means end of file. Re-logs in transparently once on an expired session.
     * [requireLan] = true throws [FreeboxUnreachableException] unless on the LAN.
     */
    suspend fun readRange(url: String, offset: Long, length: Int, requireLan: Boolean = false): ByteArray {
        val fileUrl = FreeboxPaths.toUrl(FreeboxPaths.toDisplayPath(url))
        if (requireLan) ensureLan(fileUrl)
        val pathB64 = FreeboxPaths.toB64(fileUrl)
        val outcome = sessions.withSession { base, token ->
            api.readRange(base, token, pathB64, offset, length)
        }
        return when (outcome) {
            is FbxResult.Ok -> outcome.value
            is FbxResult.Err -> throw outcome.error.toException(fileUrl)
        }
    }

    /**
     * How the box is currently reached (cached endpoint, LAN probe when none is cached);
     * null when neither the LAN nor a stored remote config is usable. REMOTE only means
     * a remote config exists — FreeboxReachability confirms it actually answers.
     */
    suspend fun currentMode(): ConnectionMode? = resolver.resolve().getOrNull()?.mode

    private suspend fun ensureLan(context: String) {
        val mode = currentMode()
        if (mode != ConnectionMode.LAN) {
            throw FreeboxUnreachableException("Freebox not on the local network ($context, mode=$mode)")
        }
    }

    suspend fun streamingAccess(): StreamingAccess =
        when (val outcome = sessions.streamingAccess()) {
            is FbxResult.Ok -> StreamingAccess(outcome.value.first, outcome.value.second)
            is FbxResult.Err -> throw outcome.error.toException("streaming")
        }

    suspend fun dropSession() {
        sessions.dropSession()
    }

    suspend fun setStartFolder(folder: FreeboxFolder?) {
        prefs.saveFreeboxStartFolder(
            b64 = folder?.let { FreeboxPaths.toB64(it.url) },
            display = folder?.let { FreeboxPaths.toDisplayPath(it.url) },
        )
    }

    private fun readToken(): String? = runCatching { tokenStore.appToken }.getOrNull()

    private fun FileInfoDto.toRemoteEntry(parentUrl: String): RemoteEntry {
        val display = runCatching { PathCodec.decode(path) }.getOrNull()
        val entryUrl = if (!display.isNullOrBlank() && display.trim('/').isNotEmpty()) {
            FreeboxPaths.toUrl(display)
        } else {
            val parent = FreeboxPaths.toDisplayPath(parentUrl)
            FreeboxPaths.toUrl(if (parent.isEmpty()) name else "$parent/$name")
        }
        return RemoteEntry(
            name = name,
            url = entryUrl,
            isDirectory = isDirectory,
            size = size,
            lastModified = modification * 1000L,
        )
    }
}

/** Maps typed API failures onto the IOException family the rest of the app handles. */
internal fun FreeboxError.toException(context: String): IOException = when (this) {
    FreeboxError.NotPaired -> FreeboxAuthException("Freebox not paired ($context)")
    FreeboxError.PairingDenied -> FreeboxAuthException("Freebox pairing denied ($context)")
    FreeboxError.PairingTimeout -> FreeboxAuthException("Freebox pairing timed out ($context)")
    FreeboxError.BoxNotFound -> FreeboxUnreachableException("Freebox not reachable ($context)")
    is FreeboxError.Network -> FreeboxUnreachableException(
        "Freebox network error ($context): ${cause.javaClass.simpleName}",
        cause,
    )
    is FreeboxError.Http -> when {
        isAuthError() -> FreeboxAuthException("Freebox refused the session ($context): HTTP $status")
        status == 404 -> FileNotFoundException("$context: HTTP 404")
        else -> IOException("Freebox HTTP $status ($context)")
    }
    is FreeboxError.Api -> when {
        isAuthError() -> FreeboxAuthException("Freebox auth error ($context): $code")
        code == "insufficient_rights" || code == "access_denied" -> FreeboxPermissionException(
            "The Freebox refused the file operation ($code): allow \"Accès au gestionnaire de " +
                "fichiers\" for Source in Freebox OS (Gestion des accès > Applications)",
        )
        code.contains("not_found") || code.contains("no_such") -> FileNotFoundException("$context: $code")
        else -> IOException("$code: ${message ?: context}")
    }
}

/** Error code [awaitFsTask] reports when the task is still running after its polling budget. */
internal const val TASK_TIMEOUT = "task_timeout"

private const val TASK_POLL_INTERVAL_MS = 300L
private const val TASK_POLL_ATTEMPTS = 200 // ~60 s

/**
 * fs/mv is asynchronous on the box: polls the task until it settles, then forgets it
 * (the box keeps every task until told otherwise and stops running new ones once its
 * list is full). A task still running after the budget is left alone — deleting it
 * would abort the move half-way with no rollback — and reported as [TASK_TIMEOUT].
 */
internal suspend fun awaitFsTask(
    initial: FsTaskDto,
    poll: suspend (Int) -> FbxResult<FsTaskDto>,
    forget: suspend (Int) -> FbxResult<Unit>,
    pollIntervalMs: Long = TASK_POLL_INTERVAL_MS,
    attempts: Int = TASK_POLL_ATTEMPTS,
): FbxResult<Unit> {
    var task = initial
    repeat(attempts) {
        if (task.isDone) {
            forget(task.id)
            return FbxResult.Ok(Unit)
        }
        if (task.isFailed) {
            forget(task.id)
            return FbxResult.Err(FreeboxError.Api(task.errorCode ?: "task_failed", "Freebox fs task ${task.id} failed"))
        }
        delay(pollIntervalMs)
        task = when (val polled = poll(initial.id)) {
            is FbxResult.Ok -> polled.value
            is FbxResult.Err -> return polled
        }
    }
    return FbxResult.Err(FreeboxError.Api(TASK_TIMEOUT))
}
