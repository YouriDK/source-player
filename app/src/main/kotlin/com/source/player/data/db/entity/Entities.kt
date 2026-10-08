package com.source.player.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Library source of a song/album/artist/genre row: MediaStore (on-device) files. */
const val SOURCE_LOCAL = "local"
/** Library source of a row indexed from an SMB/CIFS share; `SongEntity.path` is an `smb://` URL. */
const val SOURCE_SMB = "smb"

@Entity(
        tableName = "songs",
        indices =
                [
                        Index("albumId"),
                        Index("artistId"),
                        Index("title"),
                        Index("dateAdded"),
                        Index("genre"),
                        Index("folderPath"),
                        Index("source"),
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
        @ColumnInfo(defaultValue = SOURCE_LOCAL) val source: String = SOURCE_LOCAL,
)

@Entity(tableName = "albums", indices = [Index("artistId"), Index("title"), Index("source")])
data class AlbumEntity(
        @PrimaryKey val id: Long,
        val title: String,
        val artist: String,
        val artistId: Long,
        val year: Int,
        val artUri: String?,
        val songCount: Int,
        @ColumnInfo(defaultValue = SOURCE_LOCAL) val source: String = SOURCE_LOCAL,
)

@Entity(tableName = "artists", indices = [Index("name"), Index("source")])
data class ArtistEntity(
        @PrimaryKey val id: Long,
        val name: String,
        val albumCount: Int,
        val songCount: Int,
        @ColumnInfo(defaultValue = SOURCE_LOCAL) val source: String = SOURCE_LOCAL,
)

@Entity(tableName = "genres", indices = [Index("name"), Index("source")])
data class GenreEntity(
        @PrimaryKey val id: Long,
        val name: String,
        val songCount: Int,
        @ColumnInfo(defaultValue = SOURCE_LOCAL) val source: String = SOURCE_LOCAL,
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
