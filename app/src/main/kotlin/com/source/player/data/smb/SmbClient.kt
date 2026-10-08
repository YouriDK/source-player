package com.source.player.data.smb

import android.util.Log
import com.source.player.data.preferences.AppPreferences
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtStatus
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** One entry of an SMB directory listing. Directory [url]s end with '/'. */
data class SmbEntry(
        val name: String,
        val url: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
)

/** Random-access, blocking reader over one SMB file. Not thread-safe. */
interface SmbReader : Closeable {
        val length: Long
        fun seek(position: Long)
        /** Reads up to [length] bytes into [buffer] at [offset]; returns bytes read or -1 at EOF. */
        fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

/** Credentials were rejected by the server (or guest access is refused). */
class SmbAuthenticationException(message: String, cause: Throwable? = null) :
        IOException(message, cause)

/** An operation needing the persisted share was called while none is configured. */
class SmbNotConfiguredException : IOException("No SMB share configured")

/**
 * Thin wrapper around jcifs-ng for the network (Freebox / NAS) library source.
 *
 * Error contract: every failure surfaces as an [IOException] — [SmbAuthenticationException]
 * for rejected credentials, [FileNotFoundException] for a missing file/dir,
 * [SmbNotConfiguredException] when no share is persisted, otherwise the jcifs
 * `SmbException` (itself an IOException) or a socket/timeout exception.
 */
@Singleton
class SmbClient @Inject constructor(private val prefs: AppPreferences) {

        /** Shared transport/config; per-share credentials are layered on top of it. */
        private val baseContext: CIFSContext by lazy {
                val props =
                        Properties().apply {
                                setProperty("jcifs.smb.client.minVersion", "SMB202")
                                setProperty("jcifs.smb.client.maxVersion", "SMB311")
                                // Default connTimeout is 35 s — far too long for the Settings "Test" button
                                setProperty("jcifs.smb.client.connTimeout", "10000")
                                setProperty("jcifs.smb.client.responseTimeout", "15000")
                                setProperty("jcifs.smb.client.soTimeout", "20000")
                                setProperty("jcifs.resolveOrder", "DNS")
                                setProperty("jcifs.smb.client.dfs.disabled", "true")
                        }
                BaseContext(PropertyConfiguration(props))
        }

        private val contextLock = Any()
        private var cachedConfig: SmbConfig? = null
        private var cachedContext: CIFSContext? = null

        /** True if a share is persisted. */
        val isConfigured: Flow<Boolean> = prefs.smbConfig.map { it != null }.distinctUntilChanged()

        /** Validates a config without persisting it. Success value = number of entries at the root. */
        suspend fun testConnection(config: SmbConfig): Result<Int> =
                withContext(Dispatchers.IO) {
                        try {
                                val count =
                                        SmbFile(config.rootUrl, buildContext(config)).use { root ->
                                                root.list().count { !it.startsWith('.') }
                                        }
                                Log.i(TAG, "Connection OK to ${config.host}/${config.share} ($count entries)")
                                Result.success(count)
                        } catch (e: CancellationException) {
                                throw e
                        } catch (e: Exception) {
                                val mapped = mapException(e, config.rootUrl)
                                Log.w(
                                        TAG,
                                        "Connection test failed for ${config.host}/${config.share}: " +
                                                "${mapped.javaClass.simpleName}: ${mapped.message}"
                                )
                                Result.failure(mapped)
                        }
                }

        /**
         * Lists one directory (non-recursive), skipping hidden ('.'-prefixed) entries.
         * Uses the persisted config's credentials. Throws (see class doc) on IO/auth errors.
         */
        suspend fun list(url: String): List<SmbEntry> =
                withContext(Dispatchers.IO) {
                        val config = prefs.smbConfig.first() ?: throw SmbNotConfiguredException()
                        val dirUrl = if (url.endsWith('/')) url else "$url/"
                        try {
                                SmbFile(dirUrl, contextFor(config)).use { dir ->
                                        dir.listFiles().mapNotNull { f ->
                                                f.use {
                                                        val name = f.name.trimEnd('/')
                                                        if (name.isEmpty() || name.startsWith('.')) {
                                                                return@use null
                                                        }
                                                        val isDir = f.isDirectory
                                                        SmbEntry(
                                                                name = name,
                                                                url = f.url.toString(),
                                                                isDirectory = isDir,
                                                                size = if (isDir) 0L else f.length(),
                                                                lastModified = f.lastModified(),
                                                        )
                                                }
                                        }
                                }
                        } catch (e: CancellationException) {
                                throw e
                        } catch (e: Exception) {
                                throw mapException(e, dirUrl)
                        }
                }

        /**
         * Opens a random-access reader on a file URL using the persisted config. BLOCKING —
         * meant for an ExoPlayer DataSource's loader thread; never call on the main thread.
         */
        fun openReader(url: String): SmbReader {
                val config =
                        runBlocking { prefs.smbConfig.first() } ?: throw SmbNotConfiguredException()
                var file: SmbFile? = null
                try {
                        file = SmbFile(url, contextFor(config))
                        val raf = SmbRandomAccessFile(file, "r")
                        val length =
                                try {
                                        file.length()
                                } catch (e: Exception) {
                                        raf.close()
                                        throw e
                                }
                        return JcifsReader(file, raf, length)
                } catch (e: Exception) {
                        runCatching { file?.close() }
                        throw mapException(e, url)
                }
        }

        private class JcifsReader(
                private val file: SmbFile,
                private val raf: SmbRandomAccessFile,
                override val length: Long,
        ) : SmbReader {
                override fun seek(position: Long) = raf.seek(position)

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                        raf.read(buffer, offset, length)

                override fun close() {
                        try {
                                raf.close()
                        } finally {
                                file.close()
                        }
                }
        }

        /** Credentialed context for the persisted config, rebuilt only when the config changes. */
        private fun contextFor(config: SmbConfig): CIFSContext =
                synchronized(contextLock) {
                        cachedContext?.takeIf { cachedConfig == config }
                                ?: buildContext(config).also {
                                        cachedConfig = config
                                        cachedContext = it
                                }
                }

        private fun buildContext(config: SmbConfig): CIFSContext =
                if (config.isGuest) {
                        baseContext.withGuestCrendentials() // sic: jcifs-ng's spelling
                } else {
                        // Accept "DOMAIN\\user" as well as a bare user name
                        val raw = config.username.trim()
                        val domain = raw.substringBefore('\\', missingDelimiterValue = "").ifEmpty { null }
                        val user = raw.substringAfter('\\')
                        baseContext.withCredentials(
                                NtlmPasswordAuthenticator(domain, user, config.password)
                        )
                }

        private fun mapException(e: Exception, url: String): IOException =
                when (e) {
                        is SmbAuthException ->
                                SmbAuthenticationException(
                                        e.message ?: "Authentication failed",
                                        e
                                )
                        is SmbException ->
                                when (e.ntStatus) {
                                        NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND,
                                        NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND,
                                        NtStatus.NT_STATUS_NO_SUCH_FILE,
                                        NtStatus.NT_STATUS_BAD_NETWORK_NAME ->
                                                FileNotFoundException(url).apply { initCause(e) }
                                        NtStatus.NT_STATUS_ACCESS_DENIED,
                                        NtStatus.NT_STATUS_LOGON_FAILURE,
                                        NtStatus.NT_STATUS_ACCOUNT_DISABLED,
                                        NtStatus.NT_STATUS_WRONG_PASSWORD ->
                                                SmbAuthenticationException(
                                                        e.message ?: "Access denied",
                                                        e
                                                )
                                        else -> e
                                }
                        is IOException -> e
                        else -> IOException(e.message ?: e.javaClass.simpleName, e)
                }

        private companion object {
                const val TAG = "SmbClient"
        }
}
