package com.source.player.data.lastfm

import com.source.player.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LastFmApi
 *
 * Raw HTTP wrapper for the Last.fm API.
 * - Endpoint: https://ws.audioscrobbler.com/2.0/
 * - All calls use POST + form body (write operations).
 * - Every signed call requires api_sig = MD5(sorted params + shared_secret).
 *
 * Replace API_KEY and API_SECRET with your Last.fm developer credentials:
 * https://www.last.fm/api/account/create
 */
@Singleton
class LastFmApi @Inject constructor(private val client: HttpClient) {

        companion object {
                val API_KEY: String
                        get() = BuildConfig.LASTFM_API_KEY
                val API_SECRET: String
                        get() = BuildConfig.LASTFM_API_SECRET
                private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
        }

        // ---- Auth ----

        /**
         * auth.getMobileSession — exchange username + MD5(password) for a persistent session key.
         * Returns the session key string, or throws if login fails.
         */
        suspend fun getMobileSession(username: String, password: String): String {
                val authToken = md5(username.lowercase() + md5(password))
                val params =
                        mutableMapOf(
                                "method" to "auth.getMobileSession",
                                "username" to username,
                                "authToken" to authToken,
                                "api_key" to API_KEY,
                                "format" to "json",
                        )
                params["api_sig"] = apiSig(params)

                val response =
                        client.submitForm(
                                url = BASE_URL,
                                formParameters =
                                        Parameters.build {
                                                params.forEach { (k, v) -> append(k, v) }
                                        },
                        )
                val body = response.body<LastFmSessionResponse>()
                return body.session?.key ?: throw LastFmException(body.error, body.message)
        }

        // ---- Now Playing ----

        suspend fun updateNowPlaying(
                sessionKey: String,
                artist: String,
                track: String,
                album: String?,
                durationSec: Int,
        ) {
                val params =
                        buildSignedParams("track.updateNowPlaying", sessionKey) {
                                put("artist", artist)
                                put("track", track)
                                if (!album.isNullOrBlank()) put("album", album)
                                if (durationSec > 0) put("duration", durationSec.toString())
                        }
                client.submitForm(
                        url = BASE_URL,
                        formParameters =
                                Parameters.build { params.forEach { (k, v) -> append(k, v) } },
                )
        }

        // ---- Scrobble ----

        suspend fun scrobble(
                sessionKey: String,
                artist: String,
                track: String,
                album: String?,
                timestamp: Long, // Unix epoch seconds when track started
                durationSec: Int,
        ) {
                val params =
                        buildSignedParams("track.scrobble", sessionKey) {
                                put("artist", artist)
                                put("track", track)
                                if (!album.isNullOrBlank()) put("album", album)
                                put("timestamp", timestamp.toString())
                                if (durationSec > 0) put("duration", durationSec.toString())
                        }
                client.submitForm(
                        url = BASE_URL,
                        formParameters =
                                Parameters.build { params.forEach { (k, v) -> append(k, v) } },
                )
        }

        // ---- Album Art ----

        /**
         * Fetches the best available album art URL from Last.fm. Returns null if the album is
         * unknown or the image is not available. No signing or session key required — read-only
         * public API.
         */
        suspend fun getAlbumArtUrl(artist: String, album: String): String? {
                val response =
                        client.get(BASE_URL) {
                                url {
                                        parameters.append("method", "album.getInfo")
                                        parameters.append("artist", artist)
                                        parameters.append("album", album)
                                        parameters.append("api_key", API_KEY)
                                        parameters.append("format", "json")
                                }
                        }
                val body = response.body<LastFmAlbumInfoResponse>()
                // Last.fm returns sizes: small, medium, large, extralarge, mega
                // Pick the largest non-empty one
                return body.album
                        ?.image
                        ?.filter { it.text.isNotBlank() }
                        ?.maxByOrNull { sizeRank(it.size) }
                        ?.text
        }

        private fun sizeRank(size: String) =
                when (size) {
                        "mega" -> 5
                        "extralarge" -> 4
                        "large" -> 3
                        "medium" -> 2
                        "small" -> 1
                        else -> 0
                }

        // ---- Helpers ----

        private fun buildSignedParams(
                method: String,
                sessionKey: String,
                extra: MutableMap<String, String>.() -> Unit,
        ): Map<String, String> {
                val params =
                        mutableMapOf(
                                "method" to method,
                                "api_key" to API_KEY,
                                "sk" to sessionKey,
                                "format" to "json",
                        )
                params.extra()
                params["api_sig"] = apiSig(params)
                return params
        }

        /**
         * Compute api_sig: MD5 of all params (excluding 'format') sorted alphabetically + secret.
         */
        private fun apiSig(params: Map<String, String>): String {
                val sorted =
                        params
                                .filterKeys { it != "format" }
                                .entries
                                .sortedBy { it.key }
                                .joinToString("") { "${it.key}${it.value}" }
                return md5(sorted + API_SECRET)
        }

        fun md5(input: String): String {
                val md = MessageDigest.getInstance("MD5")
                val hash = md.digest(input.toByteArray(Charsets.UTF_8))
                return BigInteger(1, hash).toString(16).padStart(32, '0')
        }
}

class LastFmException(val code: Int?, val msg: String?) : Exception("Last.fm error $code: $msg")
