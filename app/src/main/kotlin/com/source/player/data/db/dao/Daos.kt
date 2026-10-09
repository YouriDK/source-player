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

  @Query("SELECT * FROM songs WHERE id IN (:ids)")
  suspend fun getByIds(ids: List<Long>): List<SongEntity>

  @Query("SELECT * FROM songs WHERE path = :path LIMIT 1")
  suspend fun getByPath(path: String): SongEntity?

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

  @Query("SELECT * FROM songs WHERE folderPath = :folder ORDER BY title ASC")
  suspend fun getByFolderPath(folder: String): List<SongEntity>

  @Query("UPDATE songs SET genre = :genre WHERE folderPath = :folder AND id != :excludeId")
  suspend fun updateGenreForFolder(genre: String, folder: String, excludeId: Long)

  @Query("UPDATE songs SET artist = :artist WHERE folderPath = :folder AND id != :excludeId")
  suspend fun updateArtistForFolder(artist: String, folder: String, excludeId: Long)

  @Query("UPDATE songs SET album = :album WHERE folderPath = :folder AND id != :excludeId")
  suspend fun updateAlbumForFolder(album: String, folder: String, excludeId: Long)

  @Query("UPDATE songs SET year = :year WHERE folderPath = :folder AND id != :excludeId")
  suspend fun updateYearForFolder(year: Int, folder: String, excludeId: Long)

  @Query("SELECT * FROM songs WHERE dateAdded >= :since ORDER BY dateAdded DESC")
  fun getAddedSince(since: Long): Flow<List<SongEntity>>

  @Query("SELECT * FROM songs ORDER BY dateAdded DESC LIMIT :limit")
  fun getRecentlyAdded(limit: Int): Flow<List<SongEntity>>

  @Query("SELECT COUNT(*) FROM songs") fun countFlow(): Flow<Int>

  @Query("SELECT * FROM songs WHERE folderPath NOT IN (SELECT path FROM blacklisted_folders)")
  fun getAllExcludingBlacklisted(): Flow<List<SongEntity>>

  @Upsert suspend fun upsertAll(songs: List<SongEntity>)

  @Update suspend fun update(song: SongEntity)

  @Query("UPDATE songs SET albumArtUri = :uri WHERE id = :id")
  suspend fun updateArtUri(id: Long, uri: String)

  @Query("UPDATE songs SET albumArtUri = :uri WHERE albumId = :albumId")
  suspend fun updateArtUriForAlbum(albumId: Long, uri: String)

  /** One transaction → one invalidation, instead of one per updated song. */
  @Transaction
  suspend fun updateAlbumArtBatch(artByAlbumId: Map<Long, String>) {
    for ((albumId, uri) in artByAlbumId) updateArtUriForAlbum(albumId, uri)
  }

  @Query("SELECT * FROM songs") suspend fun getAllSongs(): List<SongEntity>

  @Query("SELECT id FROM songs") suspend fun getAllIds(): List<Long>

  @Query("DELETE FROM songs WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)

  @Query("DELETE FROM songs") suspend fun deleteAll()

  @Query("SELECT COUNT(*) FROM songs") suspend fun count(): Int

  @Query("SELECT id FROM songs WHERE source = :source")
  suspend fun getIdsBySource(source: String): List<Long>

  @Query("SELECT * FROM songs WHERE source = :source")
  suspend fun getAllBySource(source: String): List<SongEntity>

  @Query("SELECT * FROM songs WHERE source = :source ORDER BY title ASC")
  fun getAllBySourceFlow(source: String): Flow<List<SongEntity>>

  @Query("SELECT COUNT(*) FROM songs WHERE source = :source")
  fun countBySourceFlow(source: String): Flow<Int>

  @Query("DELETE FROM songs WHERE source = :source") suspend fun deleteBySource(source: String)

  // Library mode
  @Query(
          """
        SELECT * FROM songs
        WHERE source = :source
          AND (title LIKE '%' || :q || '%'
           OR artist LIKE '%' || :q || '%'
           OR album LIKE '%' || :q || '%')
        LIMIT 60
    """
  )
  suspend fun searchBySource(q: String, source: String): List<SongEntity>

  @Query("SELECT * FROM songs WHERE source = :source ORDER BY dateAdded DESC LIMIT :limit")
  fun getRecentlyAddedBySource(limit: Int, source: String): Flow<List<SongEntity>>
}

// ---- Albums ----
@Dao
interface AlbumDao {
  @Query("SELECT * FROM albums ORDER BY title ASC") fun getAllFlow(): Flow<List<AlbumEntity>>

  @Query("SELECT * FROM albums WHERE id = :id LIMIT 1") suspend fun getById(id: Long): AlbumEntity?

  @Query("SELECT * FROM albums WHERE id IN (:ids)")
  suspend fun getByIds(ids: List<Long>): List<AlbumEntity>

  @Query(
          "SELECT * FROM albums WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' LIMIT 20"
  )
  suspend fun search(q: String): List<AlbumEntity>

  @Upsert suspend fun upsertAll(albums: List<AlbumEntity>)

  @Query("SELECT id FROM albums") suspend fun getAllIds(): List<Long>

  @Query("DELETE FROM albums WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)

  @Query("SELECT id FROM albums WHERE source = :source")
  suspend fun getIdsBySource(source: String): List<Long>

  @Query("SELECT * FROM albums WHERE source = :source")
  suspend fun getAllBySource(source: String): List<AlbumEntity>

  @Query("SELECT COUNT(*) FROM albums WHERE source = :source")
  fun countBySourceFlow(source: String): Flow<Int>

  @Query("DELETE FROM albums WHERE source = :source") suspend fun deleteBySource(source: String)

  // Library mode
  @Query("SELECT * FROM albums WHERE source = :source ORDER BY title ASC")
  fun getAllBySourceFlow(source: String): Flow<List<AlbumEntity>>

  @Query(
          "SELECT * FROM albums WHERE source = :source AND (title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%') LIMIT 20"
  )
  suspend fun searchBySource(q: String, source: String): List<AlbumEntity>
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

  @Query("SELECT id FROM artists") suspend fun getAllIds(): List<Long>

  @Query("DELETE FROM artists WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)

  @Query("SELECT id FROM artists WHERE source = :source")
  suspend fun getIdsBySource(source: String): List<Long>

  @Query("SELECT * FROM artists WHERE source = :source")
  suspend fun getAllBySource(source: String): List<ArtistEntity>

  @Query("SELECT COUNT(*) FROM artists WHERE source = :source")
  fun countBySourceFlow(source: String): Flow<Int>

  @Query("DELETE FROM artists WHERE source = :source") suspend fun deleteBySource(source: String)

  // Library mode
  @Query("SELECT * FROM artists WHERE source = :source ORDER BY name ASC")
  fun getAllBySourceFlow(source: String): Flow<List<ArtistEntity>>

  @Query("SELECT * FROM artists WHERE source = :source AND name LIKE '%' || :q || '%' LIMIT 20")
  suspend fun searchBySource(q: String, source: String): List<ArtistEntity>
}

// ---- Genres ----
@Dao
interface GenreDao {
  @Query("SELECT * FROM genres ORDER BY name ASC") fun getAllFlow(): Flow<List<GenreEntity>>

  @Upsert suspend fun upsertAll(genres: List<GenreEntity>)

  @Query("SELECT id FROM genres") suspend fun getAllIds(): List<Long>

  @Query("DELETE FROM genres WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)

  @Query("SELECT id FROM genres WHERE source = :source")
  suspend fun getIdsBySource(source: String): List<Long>

  @Query("SELECT * FROM genres WHERE source = :source")
  suspend fun getAllBySource(source: String): List<GenreEntity>

  @Query("SELECT COUNT(*) FROM genres WHERE source = :source")
  fun countBySourceFlow(source: String): Flow<Int>

  @Query("DELETE FROM genres WHERE source = :source") suspend fun deleteBySource(source: String)

  // Library mode
  @Query("SELECT * FROM genres WHERE source = :source ORDER BY name ASC")
  fun getAllBySourceFlow(source: String): Flow<List<GenreEntity>>
}

// ---- Playlists ----
@Dao
interface PlaylistDao {
  @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
  fun getAllFlow(): Flow<List<PlaylistEntity>>

  @Query("SELECT * FROM playlists WHERE source = :source ORDER BY createdAt DESC")
  fun getAllBySourceFlow(source: String): Flow<List<PlaylistEntity>>

  /**
   * Drops a mode's playlists and their entries in one transaction (`playlist_songs` has no foreign
   * key, so entries are removed explicitly).
   */
  @Transaction
  suspend fun deleteBySource(source: String) {
    deleteEntriesBySource(source)
    deletePlaylistsBySource(source)
  }

  @Query(
          "DELETE FROM playlist_songs WHERE playlistId IN (SELECT id FROM playlists WHERE source = :source)"
  )
  suspend fun deleteEntriesBySource(source: String)

  @Query("DELETE FROM playlists WHERE source = :source")
  suspend fun deletePlaylistsBySource(source: String)

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

  /** Single transaction/invalidation for multi-song adds. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addSongsToPlaylist(entries: List<PlaylistSongEntity>)

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
