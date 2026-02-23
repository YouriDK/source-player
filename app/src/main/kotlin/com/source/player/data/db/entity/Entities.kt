package com.source.player.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
        tableName = "songs",
        indices =
                [
                        Index("albumId"),
                        Index("artistId"),
                        Index("title"),
                        Index("dateAdded"),
                        Index("genre"),
                ]
)
data class SongEntity(
        @PrimaryKey val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: Long,
        val artistId: Long,
        val duration: Long, // milliseconds
        val path: String,
        val trackNumber: Int,
        val year: Int,
        val genre: String,
        val dateAdded: Long, // epoch seconds
        val albumArtUri: String?,
        val folderPath: String,
        val size: Long, // bytes
)

@Entity(tableName = "albums", indices = [Index("artistId"), Index("title")])
data class AlbumEntity(
        @PrimaryKey val id: Long,
        val title: String,
        val artist: String,
        val artistId: Long,
        val year: Int,
        val artUri: String?,
        val songCount: Int,
)

@Entity(tableName = "artists", indices = [Index("name")])
data class ArtistEntity(
        @PrimaryKey val id: Long,
        val name: String,
        val albumCount: Int,
        val songCount: Int,
)

@Entity(tableName = "genres", indices = [Index("name")])
data class GenreEntity(
        @PrimaryKey val id: Long,
        val name: String,
        val songCount: Int,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val name: String,
        val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
        tableName = "playlist_songs",
        primaryKeys = ["playlistId", "songId"],
        indices = [Index("songId")]
)
data class PlaylistSongEntity(
        val playlistId: Long,
        val songId: Long,
        val position: Int,
)

@Entity(tableName = "blacklisted_folders")
data class BlacklistedFolderEntity(
        @PrimaryKey val path: String,
)
