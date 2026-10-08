package com.source.player.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.source.player.data.smb.SmbAuthenticationException
import com.source.player.data.smb.SmbClient
import com.source.player.data.smb.SmbConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Minimal HTTP server that serves local audio files over the LAN so that
 * Chromecast and Sonos (which need an HTTP URL) can stream them from the phone.
 *
 * Usage:
 *   server.start()
 *   val url = server.getUrl("/storage/emulated/0/Music/track.mp3")
 *   // → "http://192.168.1.10:8888/storage/emulated/0/Music/track.mp3"
 */
@Singleton
class LocalAudioHttpServer
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val smbClient: SmbClient,
) {
    companion object {
        const val PORT = 8888
        private val ALLOWED_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "wav", "opus", "wma")
        private const val TAG = "LocalAudioHttpServer"
        private const val SMB_ROUTE = "smb"
        private const val COPY_CHUNK = 64 * 1024

        private val RANGE_REGEX = Regex("""^\s*bytes\s*=\s*(\d*)\s*-\s*(\d*)\s*$""", RegexOption.IGNORE_CASE)

        private fun contentTypeFor(extension: String): ContentType =
                when (extension.lowercase()) {
                    "mp3" -> ContentType("audio", "mpeg")
                    "flac" -> ContentType("audio", "flac")
                    "m4a" -> ContentType("audio", "mp4")
                    "aac" -> ContentType("audio", "aac")
                    "ogg",
                    "oga" -> ContentType("audio", "ogg")
                    "wav" -> ContentType("audio", "wav")
                    "opus" -> ContentType("audio", "opus")
                    else -> ContentType.Application.OctetStream
                }

        /** RFC 3986 percent-encoding of one path segment (UTF-8; only unreserved chars kept). */
        private fun encodePathSegment(segment: String): String {
            val sb = StringBuilder()
            for (b in segment.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                val ch = c.toChar()
                if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                                ch == '-' || ch == '.' || ch == '_' || ch == '~'
                ) {
                    sb.append(ch)
                } else {
                    sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
                }
            }
            return sb.toString()
        }

        /**
         * Parses a single `bytes=` range against [total]. Returns the inclusive range,
         * null when there is no usable Range header (→ 200 full body), or throws
         * [IllegalStateException] when the range is unsatisfiable (→ 416).
         */
        private fun parseRange(header: String?, total: Long): LongRange? {
            if (header.isNullOrBlank()) return null
            // Multi-range requests are not supported: serve the whole file.
            if (header.contains(',')) return null
            val m = RANGE_REGEX.matchEntire(header) ?: return null
            val (startStr, endStr) = m.destructured
            if (startStr.isEmpty() && endStr.isEmpty()) return null
            val range =
                    if (startStr.isEmpty()) {
                        // Suffix range: last N bytes
                        val n = endStr.toLongOrNull() ?: return null
                        if (n <= 0 || total == 0L) error("unsatisfiable")
                        (total - minOf(n, total))..(total - 1)
                    } else {
                        val start = startStr.toLongOrNull() ?: return null
                        val end =
                                if (endStr.isEmpty()) total - 1
                                else minOf(endStr.toLongOrNull() ?: return null, total - 1)
                        if (start >= total || start > end) error("unsatisfiable")
                        start..end
                    }
            return range
        }
    }

    private var engine: EmbeddedServer<*, *>? = null

    /** Per-session random token — remote clients must include this to access files. */
    private var accessToken: String = java.util.UUID.randomUUID().toString()

    /** Device LAN IP, resolved once per server session instead of per getUrl() call. */
    @Volatile private var cachedIp: String? = null

    // Synchronized: start() is reached from the main thread (Cast handover) and from
    // Dispatchers.IO (Sonos playFile) — an unsynchronized check-then-act can double-bind
    // port 8888 and regenerate accessToken under URLs already handed to a device.
    @Synchronized
    fun start() {
        if (engine != null) return
        accessToken = java.util.UUID.randomUUID().toString()
        cachedIp = null
        engine =
                embeddedServer(CIO, port = PORT) {
                            // Sonos and Chromecast seek via HTTP Range requests; without
                            // PartialContent every seek re-streams the whole file.
                            install(PartialContent)
                            // Sonos probes URLs with HEAD before playing.
                            install(AutoHeadResponse)
                            routing {
                                // Network-share tracks, proxied from SMB (Cast/Sonos can't
                                // speak SMB). Declared first so it wins over the local route.
                                get("/{token}/$SMB_ROUTE/{path...}") { serveSmb(call) }
                                get("/{token}/{path...}") {
                                    // Validate access token
                                    val token = call.parameters["token"]
                                    if (token != accessToken) {
                                        call.respond(HttpStatusCode.Forbidden)
                                        return@get
                                    }

                                    val pathSegments =
                                            call.parameters.getAll("path") ?: emptyList()
                                    val filePath =
                                            "/${pathSegments.joinToString("/")}".let {
                                                java.net.URLDecoder.decode(it, "UTF-8")
                                            }

                                    // Block path traversal
                                    val file = File(filePath).canonicalFile
                                    if (file.path != filePath.let { File(it).canonicalPath }) {
                                        call.respond(HttpStatusCode.Forbidden)
                                        return@get
                                    }

                                    // Only serve audio files
                                    if (file.extension.lowercase() !in ALLOWED_EXTENSIONS) {
                                        call.respond(HttpStatusCode.Forbidden)
                                        return@get
                                    }

                                    if (file.exists() && file.canRead()) {
                                        val ct = contentTypeFor(file.extension)
                                        call.response.header(
                                                HttpHeaders.ContentType,
                                                ct.toString(),
                                        )
                                        // Accept-Ranges is set by the PartialContent plugin.
                                        call.respondFile(file)
                                    } else {
                                        call.respond(HttpStatusCode.NotFound)
                                    }
                                }
                            }
                        }
                        .start(wait = false)
    }

    @Synchronized
    fun stop() {
        engine?.stop(500, 1000)
        engine = null
        cachedIp = null
    }

    /**
     * Serves an `smb://` file with hand-rolled Range support: the reader seeks straight
     * to the range start, whereas the PartialContent plugin would read and discard every
     * byte before it (re-streaming a whole FLAC over SMB on each seek).
     */
    private suspend fun serveSmb(call: ApplicationCall) {
        if (call.parameters["token"] != accessToken) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        // ktor has already percent-decoded each segment — do not decode again.
        val segments = call.parameters.getAll("path").orEmpty()
        if (segments.isEmpty() || segments.any { it == ".." }) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        val smbUrl = SmbConfig.SCHEME + segments.joinToString("/")
        val extension = segments.last().substringAfterLast('.', "").lowercase()
        if (extension !in ALLOWED_EXTENSIONS) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }

        // Open once up front for the length and to map errors to a status code;
        // the body is streamed by a fresh reader (HEAD never reaches writeTo()).
        val total =
                try {
                    withContext(Dispatchers.IO) { smbClient.openReader(smbUrl).use { it.length } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Log.w(TAG, "SMB open failed: ${e.javaClass.simpleName}: ${e.message}")
                    call.respond(
                            when (e) {
                                is FileNotFoundException -> HttpStatusCode.NotFound
                                is SmbAuthenticationException -> HttpStatusCode.Forbidden
                                else -> HttpStatusCode.BadGateway
                            }
                    )
                    return
                }

        val range =
                try {
                    parseRange(call.request.headers[HttpHeaders.Range], total)
                } catch (_: IllegalStateException) {
                    call.response.header(HttpHeaders.ContentRange, "bytes */$total")
                    call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                    return
                }
        val start = range?.first ?: 0L
        val length = if (range != null) range.last - range.first + 1 else total

        call.respond(
                object : OutgoingContent.WriteChannelContent() {
                    override val status: HttpStatusCode =
                            if (range != null) HttpStatusCode.PartialContent else HttpStatusCode.OK
                    override val contentType: ContentType = contentTypeFor(extension)
                    override val contentLength: Long = length
                    override val headers: Headers =
                            Headers.build {
                                append(HttpHeaders.AcceptRanges, "bytes")
                                if (range != null) {
                                    append(
                                            HttpHeaders.ContentRange,
                                            "bytes ${range.first}-${range.last}/$total",
                                    )
                                }
                            }

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        withContext(Dispatchers.IO) {
                            val reader = smbClient.openReader(smbUrl)
                            try {
                                reader.seek(start)
                                val buf = ByteArray(COPY_CHUNK)
                                var remaining = length
                                while (remaining > 0) {
                                    val n = reader.read(buf, 0, minOf(COPY_CHUNK.toLong(), remaining).toInt())
                                    if (n <= 0) break
                                    channel.writeFully(buf, 0, n)
                                    remaining -= n
                                }
                            } finally {
                                runCatching { reader.close() }
                            }
                        }
                    }
                }
        )
    }

    /**
     * Returns the HTTP URL for a local file path — or an `smb://` URL, proxied by this
     * server — accessible from other devices on the LAN.
     */
    fun getUrl(filePath: String): String? {
        // Cache the IP: Cast handover calls getUrl() once per queue item, and each
        // getDeviceIp() enumerates every network interface via syscalls.
        val ip = cachedIp ?: getDeviceIp()?.also { cachedIp = it } ?: return null
        if (SmbConfig.isSmbUrl(filePath)) {
            // smb URLs are raw (never percent-encoded); encode each segment strictly per
            // RFC 3986 so Cast/Sonos re-request a URL ktor decodes back to the exact original.
            val encoded =
                    filePath.substring(SmbConfig.SCHEME.length)
                            .split("/")
                            .joinToString("/") { encodePathSegment(it) }
            return "http://$ip:$PORT/$accessToken/$SMB_ROUTE/$encoded"
        }
        val encoded = filePath.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        return "http://$ip:$PORT/$accessToken/$encoded"
    }

    private fun getDeviceIp(): String? {
        // Use NetworkInterface enumeration — works on all API levels, no deprecation.
        // Site-local filter keeps cellular (CGNAT) addresses out: a mobile-data IP is
        // unreachable from Sonos/Cast and silently breaks remote playback.
        return try {
            NetworkInterface.getNetworkInterfaces()
                    ?.asSequence()
                    ?.filter { it.isUp && !it.isLoopback }
                    ?.flatMap { it.inetAddresses.asSequence() }
                    ?.filterIsInstance<Inet4Address>()
                    ?.filter { it.isSiteLocalAddress }
                    ?.firstOrNull()
                    ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
