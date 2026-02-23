package com.source.player.data.scanner

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.source.player.data.db.dao.*
import com.source.player.data.db.entity.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class ScanProgress(
        val isScanning: Boolean = false,
        val scannedCount: Int = 0,
        val totalCount: Int = 0,
)

@Singleton
class MediaScanner
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
        private val artistDao: ArtistDao,
        private val genreDao: GenreDao,
        private val blacklistDao: BlacklistDao,
) {
        private val _progress = MutableStateFlow(ScanProgress())
        val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

        /** Human-readable scan status for the HomeScreen UI (null = idle) */
        val progressMessage: Flow<String?> =
                _progress.map { p ->
                        when {
                                p.isScanning && p.totalCount > 0 ->
                                        "Scanning\u2026 ${p.scannedCount} / ${p.totalCount} tracks"
                                p.isScanning -> "Scanning\u2026"
                                else -> null
                        }
                }

        suspend fun scan() =
                withContext(Dispatchers.IO) {
                        _progress.value = ScanProgress(isScanning = true)
                        try {
                                val blacklisted =
                                        blacklistDao.getAllFlow().let { flow ->
                                                // snapshot read
                                                val result = mutableListOf<String>()
                                                val resolver = context.contentResolver
                                                resolver // just to reference; we'll query directly
                                                result
                                        }
                                val blacklistedPaths = mutableSetOf<String>()

                                val songs = mutableListOf<SongEntity>()
                                val albums = mutableMapOf<Long, AlbumEntity>()
                                val artists = mutableMapOf<Long, ArtistEntity>()
                                val genres = mutableMapOf<Long, GenreEntity>()

                                val projection =
                                        arrayOf(
                                                MediaStore.Audio.Media._ID,
                                                MediaStore.Audio.Media.TITLE,
                                                MediaStore.Audio.Media.ARTIST,
                                                MediaStore.Audio.Media.ARTIST_ID,
                                                MediaStore.Audio.Media.ALBUM,
                                                MediaStore.Audio.Media.ALBUM_ID,
                                                MediaStore.Audio.Media.DURATION,
                                                MediaStore.Audio.Media.DATA,
                                                MediaStore.Audio.Media.TRACK,
                                                MediaStore.Audio.Media.YEAR,
                                                MediaStore.Audio.Media.DATE_ADDED,
                                                MediaStore.Audio.Media.SIZE,
                                        )

                                val selection =
                                        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
                                val cursor: Cursor? =
                                        context.contentResolver.query(
                                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                                projection,
                                                selection,
                                                null,
                                                "${MediaStore.Audio.Media.TITLE} ASC",
                                        )

                                val total = cursor?.count ?: 0
                                _progress.value = _progress.value.copy(totalCount = total)
                                var count = 0

                                cursor?.use {
                                        val idCol =
                                                it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                                        val titleCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.TITLE
                                                )
                                        val artistCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.ARTIST
                                                )
                                        val artistIdCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.ARTIST_ID
                                                )
                                        val albumCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.ALBUM
                                                )
                                        val albumIdCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.ALBUM_ID
                                                )
                                        val durCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.DURATION
                                                )
                                        val dataCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.DATA
                                                )
                                        val trackCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.TRACK
                                                )
                                        val yearCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.YEAR
                                                )
                                        val dateCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.DATE_ADDED
                                                )
                                        val sizeCol =
                                                it.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Media.SIZE
                                                )

                                        while (it.moveToNext()) {
                                                val path = it.getString(dataCol) ?: continue
                                                val folder = path.substringBeforeLast("/")
                                                if (blacklistedPaths.any { bl ->
                                                                path.startsWith(bl)
                                                        }
                                                )
                                                        continue

                                                val id = it.getLong(idCol)
                                                val title = it.getString(titleCol) ?: "<Unknown>"
                                                val artist = it.getString(artistCol) ?: "<Unknown>"
                                                val artistId = it.getLong(artistIdCol)
                                                val album = it.getString(albumCol) ?: "<Unknown>"
                                                val albumId = it.getLong(albumIdCol)
                                                val duration = it.getLong(durCol)
                                                val track = it.getInt(trackCol)
                                                val year = it.getInt(yearCol)
                                                val dateAdded = it.getLong(dateCol)
                                                val size = it.getLong(sizeCol)
                                                val artUri =
                                                        "content://media/external/audio/albumart/$albumId"

                                                songs.add(
                                                        SongEntity(
                                                                id = id,
                                                                title = title,
                                                                artist = artist,
                                                                album = album,
                                                                albumId = albumId,
                                                                artistId = artistId,
                                                                duration = duration,
                                                                path = path,
                                                                trackNumber = track,
                                                                year = year,
                                                                genre = "",
                                                                dateAdded = dateAdded,
                                                                albumArtUri = artUri,
                                                                folderPath = folder,
                                                                size = size,
                                                        )
                                                )

                                                albums
                                                        .getOrPut(albumId) {
                                                                AlbumEntity(
                                                                        albumId,
                                                                        album,
                                                                        artist,
                                                                        artistId,
                                                                        year,
                                                                        artUri,
                                                                        0
                                                                )
                                                        }
                                                        .let { a ->
                                                                albums[albumId] =
                                                                        a.copy(
                                                                                songCount =
                                                                                        a.songCount +
                                                                                                1
                                                                        )
                                                        }

                                                artists
                                                        .getOrPut(artistId) {
                                                                ArtistEntity(artistId, artist, 0, 0)
                                                        }
                                                        .let { a ->
                                                                artists[artistId] =
                                                                        a.copy(
                                                                                songCount =
                                                                                        a.songCount +
                                                                                                1
                                                                        )
                                                        }

                                                count++
                                                if (count % 100 == 0) {
                                                        _progress.value =
                                                                _progress.value.copy(
                                                                        scannedCount = count
                                                                )
                                                }
                                        }
                                }

                                // Batch upsert — fast single transaction per type
                                songDao.upsertAll(songs)
                                albumDao.upsertAll(albums.values.toList())
                                artistDao.upsertAll(artists.values.toList())

                                // Remove orphaned entries no longer in MediaStore
                                val activeIds = songs.map { it.id }
                                if (activeIds.isNotEmpty()) {
                                        songDao.deleteOrphans(activeIds)
                                        albumDao.deleteOrphans(albums.keys.toList())
                                        artistDao.deleteOrphans(artists.keys.toList())
                                }

                                _progress.value =
                                        ScanProgress(
                                                isScanning = false,
                                                scannedCount = count,
                                                totalCount = total
                                        )
                        } catch (e: SecurityException) {
                                // Permission not granted — fail gracefully, UI shows Grant
                                // Permission card
                                android.util.Log.w(
                                        "MediaScanner",
                                        "Storage permission denied: ${e.message}"
                                )
                                _progress.value = ScanProgress(isScanning = false)
                        } catch (e: Exception) {
                                _progress.value = ScanProgress(isScanning = false)
                                android.util.Log.e("MediaScanner", "Scan failed", e)
                        }
                }
}
