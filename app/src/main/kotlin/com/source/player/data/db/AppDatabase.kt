package com.source.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.source.player.data.db.dao.*
import com.source.player.data.db.entity.*

@Database(
        entities =
                [
                        SongEntity::class,
                        AlbumEntity::class,
                        ArtistEntity::class,
                        GenreEntity::class,
                        PlaylistEntity::class,
                        PlaylistSongEntity::class,
                        BlacklistedFolderEntity::class,
                ],
        version = 1,
        exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
        abstract fun songDao(): SongDao
        abstract fun albumDao(): AlbumDao
        abstract fun artistDao(): ArtistDao
        abstract fun genreDao(): GenreDao
        abstract fun playlistDao(): PlaylistDao
        abstract fun blacklistDao(): BlacklistDao
}
