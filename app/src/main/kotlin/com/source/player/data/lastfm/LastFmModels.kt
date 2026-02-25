package com.source.player.data.lastfm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LastFmSessionResponse(
        val session: LastFmSession? = null,
        val error: Int? = null,
        val message: String? = null,
)

@Serializable
data class LastFmSession(
        val name: String,
        val key: String,
        @SerialName("subscriber") val subscriber: Int = 0,
)

@Serializable
data class LastFmAlbumInfoResponse(
        val album: LastFmAlbumInfo? = null,
        val error: Int? = null,
        val message: String? = null,
)

@Serializable
data class LastFmAlbumInfo(
        val name: String = "",
        val artist: String = "",
        val image: List<LastFmImage> = emptyList(),
)

@Serializable
data class LastFmImage(
        @SerialName("#text") val text: String = "",
        val size: String = "",
)
