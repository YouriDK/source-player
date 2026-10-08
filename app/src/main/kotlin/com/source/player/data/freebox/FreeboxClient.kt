package com.source.player.data.freebox

import com.source.player.data.freebox.api.FileInfoDto
import com.source.player.data.freebox.api.FreeboxApiClient
import com.source.player.data.freebox.api.PathCodec
import com.source.player.data.freebox.auth.AppTokenStore
import com.source.player.data.freebox.auth.FreeboxPairingManager
import com.source.player.data.freebox.auth.FreeboxSessionManager
import com.source.player.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
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

    suspend fun list(url: String?): List<RemoteEntry> {
        val parentUrl = url?.let { FreeboxPaths.toUrl(FreeboxPaths.toDisplayPath(it)) } ?: FreeboxPaths.SCHEME
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
        code.contains("not_found") || code.contains("no_such") -> FileNotFoundException("$context: $code")
        else -> IOException("$code: ${message ?: context}")
    }
}
