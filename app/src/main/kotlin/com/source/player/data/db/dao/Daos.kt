package com.source.player.data.db.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.source.player.data.db.entity.*
import kotlinx.coroutines.flow.Flow

// ---- Songs ----
@Dao
interface SongDao {
  @Query("SELECT * FROM songs ORDER BY title ASC") fun getAllPaged(): PagingSource<Int, SongEntity>

  @Query("SELECT * FROM songs ORDER BY title ASC") fun getAllFlow(): Flow<List<SongEntity>>

  @Query("SELECT * FROM songs WHERE id = :id LIMIT 1") suspend fun getById(id: Long): SongEntity?

  @Query(
          """
        SELECT * FROM songs
        WHERE title LIKE '%' || :q || '%'
           OR artist LIKE '%' || :q || '%'
           OR album LIKE '%' || :q || '%'
        LIMIT 60
    """
  )
  suspend fun search(q: String): List<SongEntity>

  @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY trackNumber ASC")
  fun getByAlbum(albumId: Long): Flow<List<SongEntity>>

  @Query("SELECT * FROM songs WHERE artistId = :artistId ORDER BY album ASC, trackNumber ASC")
  fun getByArtist(artistId: Long): Flow<List<SongEntity>>

  @Query("SELECT * FROM songs WHERE folderPath = :folder ORDER BY title ASC")
  fun getByFolder(folder: String): Flow<List<SongEntity>>

  @Query("SELECT * FROM songs WHERE dateAdded >= :since ORDER BY dateAdded DESC")
  fun getAddedSince(since: Long): Flow<List<SongEntity>>

  @Query("SELECT * FROM songs WHERE id NOT IN (SELECT path FROM blacklisted_folders)")
  fun getAllExcludingBlacklisted(): Flow<List<SongEntity>>

  @Upsert suspend fun upsertAll(songs: List<SongEntity>)

  @Update suspend fun update(song: SongEntity)

  @Query("DELETE FROM songs WHERE id NOT IN (:activeIds)")
  suspend fun deleteOrphans(activeIds: List<Long>)

  @Query("DELETE FROM songs") suspend fun deleteAll()

  @Query("SELECT COUNT(*) FROM songs") suspend fun count(): Int
}

// ---- Albums ----
@Dao
interface AlbumDao {
  @Query("SELECT * FROM albums ORDER BY title ASC") fun getAllFlow(): Flow<List<AlbumEntity>>

  @Query("SELECT * FROM albums WHERE id = :id LIMIT 1") suspend fun getById(id: Long): AlbumEntity?

  @Query(
          "SELECT * FROM albums WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' LIMIT 20"
  )
  suspend fun search(q: String): List<AlbumEntity>

  @Upsert suspend fun upsertAll(albums: List<AlbumEntity>)

  @Query("DELETE FROM albums WHERE id NOT IN (:activeIds)")
  suspend fun deleteOrphans(activeIds: List<Long>)
}

// ---- Artists ----
@Dao
interface ArtistDao {
  @Query("SELECT * FROM artists ORDER BY name ASC") fun getAllFlow(): Flow<List<ArtistEntity>>

  @Query("SELECT * FROM artists WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): ArtistEntity?

  @Query("SELECT * FROM artists WHERE name LIKE '%' || :q || '%' LIMIT 20")
  suspend fun search(q: String): List<ArtistEntity>

  @Upsert suspend fun upsertAll(artists: List<ArtistEntity>)

  @Query("DELETE FROM artists WHERE id NOT IN (:activeIds)")
  suspend fun deleteOrphans(activeIds: List<Long>)
}

// ---- Genres ----
@Dao
interface GenreDao {
  @Query("SELECT * FROM genres ORDER BY name ASC") fun getAllFlow(): Flow<List<GenreEntity>>

  @Upsert suspend fun upsertAll(genres: List<GenreEntity>)

  @Query("DELETE FROM genres WHERE id NOT IN (:activeIds)")
  suspend fun deleteOrphans(activeIds: List<Long>)
}

// ---- Playlists ----
@Dao
interface PlaylistDao {
  @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
  fun getAllFlow(): Flow<List<PlaylistEntity>>

  @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): PlaylistEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(playlist: PlaylistEntity): Long

  @Update suspend fun update(playlist: PlaylistEntity)

  @Delete suspend fun delete(playlist: PlaylistEntity)

  @Query(
          "SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC"
  )
  fun getSongsForPlaylist(playlistId: Long): Flow<List<SongEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addSongToPlaylist(entry: PlaylistSongEntity)

  @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
  suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

  @Query("SELECT MAX(position) FROM playlist_songs WHERE playlistId = :playlistId")
  suspend fun maxPosition(playlistId: Long): Int?
}

// ---- Blacklist ----
@Dao
interface BlacklistDao {
  @Query("SELECT * FROM blacklisted_folders") fun getAllFlow(): Flow<List<BlacklistedFolderEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(entry: BlacklistedFolderEntity)

  @Delete suspend fun remove(entry: BlacklistedFolderEntity)
}
