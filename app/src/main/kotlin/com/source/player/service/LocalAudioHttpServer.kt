package com.source.player.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

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
) {
    companion object {
        const val PORT = 8888
        private val ALLOWED_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "wav", "opus", "wma")
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
                                        val ct =
                                                when (file.extension.lowercase()) {
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

    /** Returns the HTTP URL for a local file path accessible from other devices on the LAN. */
    fun getUrl(filePath: String): String? {
        // Cache the IP: Cast handover calls getUrl() once per queue item, and each
        // getDeviceIp() enumerates every network interface via syscalls.
        val ip = cachedIp ?: getDeviceIp()?.also { cachedIp = it } ?: return null
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
