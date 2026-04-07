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

    fun start() {
        if (engine != null) return
        accessToken = java.util.UUID.randomUUID().toString()
        engine =
                embeddedServer(CIO, port = PORT) {
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
                                        call.response.header(
                                                HttpHeaders.AcceptRanges,
                                                "bytes",
                                        )
                                        call.respondFile(file)
                                    } else {
                                        call.respond(HttpStatusCode.NotFound)
                                    }
                                }
                            }
                        }
                        .start(wait = false)
    }

    fun stop() {
        engine?.stop(500, 1000)
        engine = null
    }

    /** Returns the HTTP URL for a local file path accessible from other devices on the LAN. */
    fun getUrl(filePath: String): String? {
        val ip = getDeviceIp() ?: return null
        val encoded = filePath.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        return "http://$ip:$PORT/$accessToken/$encoded"
    }

    private fun getDeviceIp(): String? {
        // Use NetworkInterface enumeration — works on all API levels, no deprecation
        return try {
            NetworkInterface.getNetworkInterfaces()
                    ?.asSequence()
                    ?.filter { it.isUp && !it.isLoopback }
                    ?.flatMap { it.inetAddresses.asSequence() }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull()
                    ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
