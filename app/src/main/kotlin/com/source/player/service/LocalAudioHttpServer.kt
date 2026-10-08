package com.source.player.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.source.player.data.freebox.FreeboxAuthException
import com.source.player.data.freebox.FreeboxClient
import com.source.player.data.freebox.FreeboxPaths
import com.source.player.data.freebox.api.FreeboxApiClient
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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyTo
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

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
        private val freeboxClient: FreeboxClient,
        private val freeboxApi: FreeboxApiClient,
) {
    companion object {
        const val PORT = 8888
        private val ALLOWED_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "wav", "opus", "wma")
        private const val TAG = "LocalAudioHttpServer"
        private const val FBX_ROUTE = "fbx"

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
                                // Freebox disk tracks, proxied from the box's /dl/ endpoint
                                // (Cast/Sonos can't send the session header). Declared
                                // first so it wins over the local route.
                                get("/{token}/$FBX_ROUTE/{path...}") { serveFreebox(call) }
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
     * Proxies an `fbx://` track from the Freebox `/dl/` endpoint, streaming the body
     * without buffering. The client's Range header is forwarded verbatim and the box's
     * status (200/206), Content-Length and Content-Range are passed back, so seeking
     * costs one ranged request on the box. A 403 means the Freebox session expired: it
     * is dropped and the request retried once.
     */
    private suspend fun serveFreebox(call: ApplicationCall) {
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
        val extension = segments.last().substringAfterLast('.', "").lowercase()
        if (extension !in ALLOWED_EXTENSIONS) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        val fbxUrl = FreeboxPaths.toUrl(segments.joinToString("/"))
        val pathB64 = FreeboxPaths.toB64(fbxUrl)
        val rangeHeader = call.request.headers[HttpHeaders.Range]

        try {
            repeat(2) { attempt ->
                val access = freeboxClient.streamingAccess()
                val sessionExpired =
                        freeboxApi.openDownloadStream(
                                access.base,
                                access.sessionToken,
                                pathB64,
                                rangeHeader,
                        ) { response ->
                            if (response.status == HttpStatusCode.Forbidden && attempt == 0) {
                                true
                            } else {
                                forwardFreeboxResponse(call, response, extension)
                                false
                            }
                        }
                if (!sessionExpired) return
                freeboxClient.dropSession()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Freebox proxy failed: ${e.javaClass.simpleName}: ${e.message}")
            // Once the body started streaming (client gone, box dropped) nothing can be sent.
            if (!call.response.isCommitted) {
                call.respond(
                        if (e is FreeboxAuthException) HttpStatusCode.Forbidden
                        else HttpStatusCode.BadGateway
                )
            }
        }
    }

    private suspend fun forwardFreeboxResponse(
            call: ApplicationCall,
            response: HttpResponse,
            extension: String,
    ) {
        val status = response.status
        if (!status.isSuccess()) {
            when (status) {
                HttpStatusCode.Forbidden, HttpStatusCode.NotFound -> call.respond(status)
                HttpStatusCode.RequestedRangeNotSatisfiable -> {
                    response.headers[HttpHeaders.ContentRange]?.let {
                        call.response.header(HttpHeaders.ContentRange, it)
                    }
                    call.respond(status)
                }
                else -> {
                    Log.w(TAG, "Freebox /dl/ answered HTTP ${status.value}")
                    call.respond(HttpStatusCode.BadGateway)
                }
            }
            return
        }
        val contentRange = response.headers[HttpHeaders.ContentRange]
        val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        call.respond(
                object : OutgoingContent.WriteChannelContent() {
                    override val status: HttpStatusCode =
                            if (status == HttpStatusCode.PartialContent) HttpStatusCode.PartialContent
                            else HttpStatusCode.OK
                    override val contentType: ContentType = contentTypeFor(extension)
                    override val contentLength: Long? = length
                    override val headers: Headers =
                            Headers.build {
                                append(HttpHeaders.AcceptRanges, "bytes")
                                contentRange?.let { append(HttpHeaders.ContentRange, it) }
                            }

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        response.bodyAsChannel().copyTo(channel)
                    }
                }
        )
    }

    /**
     * Returns the HTTP URL for a local file path — or an `fbx://` URL, proxied by this
     * server — accessible from other devices on the LAN.
     */
    fun getUrl(filePath: String): String? {
        // Cache the IP: Cast handover calls getUrl() once per queue item, and each
        // getDeviceIp() enumerates every network interface via syscalls.
        val ip = cachedIp ?: getDeviceIp()?.also { cachedIp = it } ?: return null
        if (FreeboxPaths.isFreeboxUrl(filePath)) {
            // fbx URLs are raw (never percent-encoded); encode each segment strictly per
            // RFC 3986 so Cast/Sonos re-request a URL ktor decodes back to the exact original.
            val encoded =
                    FreeboxPaths.toDisplayPath(filePath)
                            .split("/")
                            .joinToString("/") { encodePathSegment(it) }
            return "http://$ip:$PORT/$accessToken/$FBX_ROUTE/$encoded"
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
