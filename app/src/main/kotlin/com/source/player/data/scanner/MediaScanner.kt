package com.source.player.data.scanner

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.source.player.data.db.dao.*
import com.source.player.data.db.entity.*
import com.source.player.data.lastfm.LastFmRepository
import com.source.player.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
        private val lastFm: LastFmRepository,
        private val prefs: AppPreferences,
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
                                val blacklistedPaths =
                                        blacklistDao.getAllFlow().first().map { it.path }.toSet()

                                val songs = mutableListOf<SongEntity>()
                                val albums = mutableMapOf<Long, AlbumEntity>()
                                val artists = mutableMapOf<Long, ArtistEntity>()
                                // songId → genre, queried up front so SongEntity.genre can be
                                // populated during the main pass
                                val songGenreMap = buildSongGenreMap()

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
                                                                genre = songGenreMap[id]?.name
                                                                                ?: "",
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

                                // Genres derived from the up-front MediaStore genre pass
                                val genreEntities = buildGenreEntities(songs, songGenreMap)
                                genreDao.upsertAll(genreEntities.values.toList())

                                // Remove orphaned entries no longer in MediaStore
                                val activeIds = songs.mapTo(HashSet()) { it.id }
                                if (activeIds.isNotEmpty()) {
                                        deleteOrphans(songDao.getAllIds(), activeIds) {
                                                songDao.deleteByIds(it)
                                        }
                                        deleteOrphans(albumDao.getAllIds(), albums.keys) {
                                                albumDao.deleteByIds(it)
                                        }
                                        deleteOrphans(artistDao.getAllIds(), artists.keys) {
                                                artistDao.deleteByIds(it)
                                        }
                                        if (genreEntities.isNotEmpty()) {
                                                deleteOrphans(
                                                        genreDao.getAllIds(),
                                                        genreEntities.keys
                                                ) { genreDao.deleteByIds(it) }
                                        }
                                }

                                _progress.value =
                                        ScanProgress(
                                                isScanning = false,
                                                scannedCount = count,
                                                totalCount = total
                                        )

                                // Online art enrichment — after the scan is reported complete
                                // so the UI isn't stuck in "Scanning…" through network fetches
                                enrichAlbumArtFromLastFm(songs, albums)
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

        private data class GenreRef(val id: Long, val name: String)

        /**
         * Queries MediaStore.Audio.Genres once and maps songId → genre, so the main scan
         * pass can populate SongEntity.genre and genre counts can be derived without a
         * second per-genre query pass.
         */
        private fun buildSongGenreMap(): Map<Long, GenreRef> {
                val songGenres = mutableMapOf<Long, GenreRef>()

                val genreCursor =
                        context.contentResolver.query(
                                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                                null,
                                null,
                                "${MediaStore.Audio.Genres.NAME} ASC",
                        )
                                ?: return songGenres

                // Build id→name map
                val genreNames = mutableMapOf<Long, String>()
                genreCursor.use { c ->
                        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                        val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                        while (c.moveToNext()) {
                                val gid = c.getLong(idCol)
                                val name =
                                        c.getString(nameCol)?.takeIf { it.isNotBlank() } ?: continue
                                genreNames[gid] = name
                        }
                }

                for ((gid, name) in genreNames) {
                        val memberUri =
                                MediaStore.Audio.Genres.Members.getContentUri("external", gid)
                        context.contentResolver
                                .query(
                                        memberUri,
                                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                                        null,
                                        null,
                                        null,
                                )
                                ?.use { mc ->
                                        val audioIdCol =
                                                mc.getColumnIndexOrThrow(
                                                        MediaStore.Audio.Genres.Members.AUDIO_ID
                                                )
                                        while (mc.moveToNext()) {
                                                songGenres[mc.getLong(audioIdCol)] =
                                                        GenreRef(gid, name)
                                        }
                                }
                }
                return songGenres
        }

        /** Counts scanned songs per genre. Only genres with at least one song are kept. */
        private fun buildGenreEntities(
                songs: List<SongEntity>,
                songGenres: Map<Long, GenreRef>,
        ): Map<Long, GenreEntity> {
                val genreEntities = mutableMapOf<Long, GenreEntity>()
                for (song in songs) {
                        val ref = songGenres[song.id] ?: continue
                        val existing = genreEntities[ref.id]
                        genreEntities[ref.id] =
                                existing?.copy(songCount = existing.songCount + 1)
                                        ?: GenreEntity(id = ref.id, name = ref.name, songCount = 1)
                }
                android.util.Log.d("MediaScanner", "Built ${genreEntities.size} genres")
                return genreEntities
        }

        /**
         * SQLite caps bound variables (999 on API 26-29), so `NOT IN (:activeIds)` throws on
         * large libraries. Compute the orphan set in memory and delete via chunked IN lists.
         */
        private suspend fun deleteOrphans(
                existingIds: List<Long>,
                activeIds: Set<Long>,
                deleteByIds: suspend (List<Long>) -> Unit,
        ) {
                val orphans = existingIds.filterNot { it in activeIds }
                orphans.chunked(500).forEach { deleteByIds(it) }
        }

        /**
         * Post-scan: for each album with no local embedded art, fetch from Last.fm. Respects the
         * artDownloadPolicy pref (NEVER skips everything). Makes exactly one API call per album —
         * not per song. Updates both songs and albums tables with the fetched URL.
         */
        private suspend fun enrichAlbumArtFromLastFm(
                songs: List<SongEntity>,
                albums: Map<Long, AlbumEntity>,
        ) {
                val policy = prefs.artDownloadPolicy.firstOrNull() ?: "NEVER"
                if (policy == "NEVER") return
                if (policy == "WIFI" && !isOnUnmeteredNetwork()) return

                // Determine albums that have no valid local art by probing ContentResolver
                val resolver = context.contentResolver
                val albumsNeedingArt =
                        albums.values.filter { album ->
                                val localUri =
                                        android.net.Uri.parse(
                                                "content://media/external/audio/albumart/${album.id}"
                                        )
                                try {
                                        resolver.openInputStream(localUri)?.use { true } == null
                                } catch (_: Exception) {
                                        true // no local art
                                }
                        }

                val fetched = mutableMapOf<Long, String>() // albumId → art URL
                for (album in albumsNeedingArt) {
                        val artist = album.artist.takeIf { it != "<Unknown>" } ?: continue
                        val title = album.title.takeIf { it != "<Unknown>" } ?: continue

                        val url = lastFm.fetchAlbumArt(artist, title) ?: continue
                        fetched[album.id] = url

                        android.util.Log.d(
                                "MediaScanner",
                                "Fetched Last.fm art for \"$title\" by $artist"
                        )
                }
                if (fetched.isEmpty()) return

                // Two batched writes (one per table) → two Room invalidations total,
                // instead of one per song plus one per album. Every live library Flow
                // re-queries on each invalidation, so this is what keeps a post-scan
                // enrichment from turning into hundreds of full-library re-emissions.
                songDao.updateAlbumArtBatch(fetched)
                albumDao.upsertAll(
                        albumsNeedingArt.mapNotNull { album ->
                                fetched[album.id]?.let { album.copy(artUri = it) }
                        }
                )
        }

        private fun isOnUnmeteredNetwork(): Boolean {
                val cm =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE)
                                as? android.net.ConnectivityManager
                                ?: return false
                val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
                return caps.hasCapability(
                        android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                )
        }
}
