package com.source.player.data.smb

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.source.player.data.db.dao.AlbumDao
import com.source.player.data.db.dao.ArtistDao
import com.source.player.data.db.dao.GenreDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.AlbumEntity
import com.source.player.data.db.entity.ArtistEntity
import com.source.player.data.db.entity.GenreEntity
import com.source.player.data.db.entity.SOURCE_SMB
import com.source.player.data.db.entity.SongEntity
import com.source.player.data.preferences.AppPreferences
import com.source.player.data.scanner.AlbumArtEnricher
import com.source.player.data.scanner.ScanProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Indexes the configured SMB share (Freebox / NAS) into the same Room tables as the
 * MediaStore scanner, tagged with [com.source.player.data.db.entity.SOURCE_SMB].
 *
 * - Ids are negative ([SmbIds]) so they never collide with MediaStore ids.
 * - `SongEntity.path` is the full file URL; `folderPath` is the parent directory URL
 *   without trailing slash.
 * - Files whose URL and size are unchanged since the last scan are reused without any
 *   network read; others go through `MediaMetadataRetriever` over [SmbMediaDataSource].
 * - A walk that hit unreadable sub-directories is "partial": rows are upserted but no
 *   orphan is deleted, so a flaky folder never wipes part of the library.
 *
 * Public surface is frozen for the UI layer:
 * [progress], [progressMessage], [lastError], [scan], [scanIfStale], [clear].
 */
@Singleton
class SmbScanner
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val smbClient: SmbClient,
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
        private val artistDao: ArtistDao,
        private val genreDao: GenreDao,
        private val prefs: AppPreferences,
        private val artEnricher: AlbumArtEnricher,
        private val artCache: SmbArtCache,
) {
        private val scanMutex = Mutex()
        @Volatile private var rescanRequested = false

        /** Serializes the DB write phase of a scan with [clear]. */
        private val writeMutex = Mutex()
        /** Bumped by [clear]; a scan started before a clear must not re-insert its rows. */
        @Volatile private var clearGeneration = 0

        private val _progress = MutableStateFlow(ScanProgress())
        val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

        /** Human-readable scan status for Settings (null = idle). */
        val progressMessage: Flow<String?> =
                _progress.map { p ->
                        when {
                                p.isScanning && p.totalCount > 0 ->
                                        "Scanning network… ${p.scannedCount} / ${p.totalCount} tracks"
                                p.isScanning -> "Scanning network…"
                                else -> null
                        }
                }

        private val _lastError = MutableStateFlow<String?>(null)
        /** Message of the last failed scan (unreachable host, bad credentials…), null when the last scan succeeded. */
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        /**
         * Full scan of the configured share. No-op when no share is configured. Concurrent
         * callers are coalesced like [com.source.player.data.scanner.MediaScanner.scan]: a
         * call while a pass is running only flags a rescan, performed once it finishes.
         */
        suspend fun scan(): Unit =
                withContext(Dispatchers.IO) {
                        if (!scanMutex.tryLock()) {
                                rescanRequested = true
                                return@withContext
                        }
                        try {
                                do {
                                        rescanRequested = false
                                        runScanPass()
                                } while (rescanRequested)
                        } finally {
                                scanMutex.unlock()
                        }
                }

        /** Scans only when a share is configured, Wi-Fi is up and the last scan is older than 24 h. */
        suspend fun scanIfStale(): Unit =
                withContext(Dispatchers.IO) {
                        if (prefs.smbConfig.first() == null) return@withContext
                        if (!isOnUnmeteredNetwork()) return@withContext
                        val lastScanAt = prefs.smbLastScanAt.first()
                        if (System.currentTimeMillis() - lastScanAt > STALE_AFTER_MS) scan()
                }

        /** Removes every SMB-sourced row (songs, albums, artists, genres) and cached artwork. */
        suspend fun clear(): Unit =
                withContext(Dispatchers.IO) {
                        writeMutex.withLock {
                                clearGeneration++
                                rescanRequested = false
                                songDao.deleteBySource(SOURCE_SMB)
                                albumDao.deleteBySource(SOURCE_SMB)
                                artistDao.deleteBySource(SOURCE_SMB)
                                genreDao.deleteBySource(SOURCE_SMB)
                                artCache.clear()
                        }
                        _progress.value = ScanProgress()
                        _lastError.value = null
                }

        // ── Scan pass ─────────────────────────────────────────────────────────

        private suspend fun runScanPass() {
                val config = prefs.smbConfig.first() ?: return
                val generation = clearGeneration
                _progress.value = ScanProgress(isScanning = true)
                try {
                        // 1. Walk — a failure at the root aborts with the DB untouched
                        val walk =
                                try {
                                        walk(config.rootUrl)
                                } catch (e: CancellationException) {
                                        throw e
                                } catch (e: Exception) {
                                        Log.w(TAG, "Walk failed for $config: ${e.javaClass.simpleName}: ${e.message}")
                                        _lastError.value = describe(e)
                                        _progress.value = ScanProgress(isScanning = false)
                                        return
                                }
                        val total = walk.files.size
                        _progress.value = ScanProgress(isScanning = true, totalCount = total)

                        // 2. Metadata — reuse unchanged rows, extract the rest concurrently
                        val previousSongs = songDao.getAllBySource(SOURCE_SMB).associateBy { it.path }
                        val previousAlbums = albumDao.getAllBySource(SOURCE_SMB).associateBy { it.id }
                        val songs = extractAll(walk, previousSongs, previousAlbums)

                        // 3. Art — folder cover first, embedded picture already cached in step 2
                        val albumDirs = HashMap<Long, MutableSet<String>>()
                        for (s in songs) {
                                albumDirs.getOrPut(s.song.albumId) { LinkedHashSet() }.add(s.song.folderPath)
                        }
                        fetchFolderCovers(albumDirs, walk.covers)

                        // 4. Aggregation
                        val albums = buildAlbums(songs, previousAlbums)
                        val finalSongs =
                                songs.map { s -> s.song.copy(albumArtUri = albums[s.song.albumId]?.artUri) }
                        val artists = buildArtists(finalSongs, albums)
                        val genres = buildGenres(finalSongs)

                        // 5. Writes (+ orphan deletion only when the walk was complete)
                        val written =
                                writeMutex.withLock {
                                        if (generation != clearGeneration) {
                                                Log.i(TAG, "SMB library cleared during scan — discarding results")
                                                return@withLock false
                                        }
                                        songDao.upsertAll(finalSongs)
                                        albumDao.upsertAll(albums.values.toList())
                                        artistDao.upsertAll(artists.values.toList())
                                        genreDao.upsertAll(genres.values.toList())

                                        if (!walk.partial) {
                                                val activeIds = finalSongs.mapTo(HashSet()) { it.id }
                                                deleteOrphans(songDao.getIdsBySource(SOURCE_SMB), activeIds) {
                                                        songDao.deleteByIds(it)
                                                }
                                                deleteOrphans(albumDao.getIdsBySource(SOURCE_SMB), albums.keys) {
                                                        albumDao.deleteByIds(it)
                                                }
                                                deleteOrphans(artistDao.getIdsBySource(SOURCE_SMB), artists.keys) {
                                                        artistDao.deleteByIds(it)
                                                }
                                                deleteOrphans(genreDao.getIdsBySource(SOURCE_SMB), genres.keys) {
                                                        genreDao.deleteByIds(it)
                                                }
                                        }
                                        true
                                }

                        if (written) {
                                if (walk.partial) {
                                        // Not stamped: the next foreground retries the full walk.
                                        _lastError.value = "Some folders could not be read"
                                } else {
                                        prefs.setSmbLastScanAt(System.currentTimeMillis())
                                        _lastError.value = null
                                }
                        }
                        Log.i(
                                TAG,
                                "SMB scan done: ${finalSongs.size}/$total tracks, ${albums.size} albums" +
                                        if (walk.partial) " (partial walk, orphans kept)" else ""
                        )

                        _progress.value =
                                ScanProgress(isScanning = false, scannedCount = finalSongs.size, totalCount = total)

                        // Online art enrichment — after the scan is reported complete so the
                        // UI isn't stuck in "Scanning…" through network fetches
                        if (written) artEnricher.enrich(albums.values) { it.artUri != null }
                } catch (e: CancellationException) {
                        _progress.value = ScanProgress(isScanning = false)
                        throw e
                } catch (e: Exception) {
                        Log.e(TAG, "SMB scan failed", e)
                        _lastError.value = describe(e)
                        _progress.value = ScanProgress(isScanning = false)
                }
        }

        // ── Walk ──────────────────────────────────────────────────────────────

        private class AudioFile(
                val url: String,
                val name: String,
                /** Parent directory URL, no trailing slash. */
                val dirUrl: String,
                /** Directory names between the root and the file, e.g. [Artist, Album]. */
                val dirSegments: List<String>,
                val size: Long,
                /** Server-side modification time, epoch ms. */
                val lastModified: Long,
        )

        private class WalkResult(
                val files: List<AudioFile>,
                /** Directory URL (no trailing slash) → cover image URL. */
                val covers: Map<String, String>,
                val partial: Boolean,
        )

        /** Breadth-first walk. Throws if the root itself can't be listed. */
        private suspend fun walk(rootUrl: String): WalkResult {
                val files = ArrayList<AudioFile>()
                val covers = HashMap<String, String>()
                var partial = false
                val visited = HashSet<String>()
                val queue = ArrayDeque<Pair<String, List<String>>>()
                queue.addLast(rootUrl to emptyList())

                while (queue.isNotEmpty()) {
                        val (dirUrl, segments) = queue.removeFirst()
                        if (!visited.add(dirUrl.lowercase())) continue
                        val entries =
                                try {
                                        smbClient.list(dirUrl)
                                } catch (e: CancellationException) {
                                        throw e
                                } catch (e: Exception) {
                                        if (segments.isEmpty()) throw e
                                        Log.w(TAG, "Skipping unreadable folder $dirUrl: ${e.javaClass.simpleName}: ${e.message}")
                                        partial = true
                                        continue
                                }

                        val dirKey = dirUrl.trimEnd('/')
                        val images = ArrayList<SmbEntry>()
                        for (entry in entries) {
                                if (entry.isDirectory) {
                                        if (segments.size < MAX_DEPTH) {
                                                val childUrl = if (entry.url.endsWith('/')) entry.url else "${entry.url}/"
                                                queue.addLast(childUrl to segments + entry.name)
                                        }
                                        continue
                                }
                                val ext = entry.name.substringAfterLast('.', "").lowercase()
                                when (ext) {
                                        in AUDIO_EXTENSIONS ->
                                                files.add(
                                                        AudioFile(
                                                                url = entry.url,
                                                                name = entry.name,
                                                                dirUrl = dirKey,
                                                                dirSegments = segments,
                                                                size = entry.size,
                                                                lastModified = entry.lastModified,
                                                        )
                                                )
                                        in IMAGE_EXTENSIONS -> images.add(entry)
                                }
                        }
                        pickCover(images)?.let { covers[dirKey] = it.url }
                }
                return WalkResult(files, covers, partial)
        }

        /** Well-known cover names first (in [COVER_NAMES] order), else the only image in the folder. */
        private fun pickCover(images: List<SmbEntry>): SmbEntry? {
                if (images.isEmpty()) return null
                for (base in COVER_NAMES) {
                        images.firstOrNull { it.name.substringBeforeLast('.').equals(base, ignoreCase = true) }
                                ?.let { return it }
                }
                return images.singleOrNull()
        }

        // ── Metadata ──────────────────────────────────────────────────────────

        private class ScannedSong(val song: SongEntity, val albumArtist: String)

        private suspend fun extractAll(
                walk: WalkResult,
                previousSongs: Map<String, SongEntity>,
                previousAlbums: Map<Long, AlbumEntity>,
        ): List<ScannedSong> = coroutineScope {
                val semaphore = Semaphore(EXTRACT_CONCURRENCY)
                val done = AtomicInteger(0)
                // Albums whose embedded art is being fetched by some track right now
                val artClaims: MutableSet<Long> = ConcurrentHashMap.newKeySet()
                val nowSeconds = System.currentTimeMillis() / 1000

                walk.files
                        .map { file ->
                                async(Dispatchers.IO) {
                                        val previous = previousSongs[file.url]
                                        val result =
                                                if (previous != null && previous.size == file.size) {
                                                        ScannedSong(
                                                                previous,
                                                                previousAlbums[previous.albumId]?.artist ?: previous.artist
                                                        )
                                                } else {
                                                        semaphore.withPermit {
                                                                extractOne(
                                                                        file = file,
                                                                        // First seen: the file's mtime on the NAS, so a first
                                                                        // scan doesn't flood "Recently added" with the whole share
                                                                        dateAdded =
                                                                                previous?.dateAdded
                                                                                        ?: file.lastModified.takeIf { it > 0 }?.div(1000)
                                                                                        ?: nowSeconds,
                                                                        hasFolderCover = file.dirUrl in walk.covers,
                                                                        artClaims = artClaims,
                                                                        fallback = previous,
                                                                        previousAlbums = previousAlbums,
                                                                )
                                                        }
                                                }
                                        val n = done.incrementAndGet()
                                        if (n % PROGRESS_EVERY == 0) {
                                                _progress.value = _progress.value.copy(scannedCount = n)
                                        }
                                        result
                                }
                        }
                        .awaitAll()
                        .filterNotNull()
        }

        /**
         * Reads tags of one file. Returns null for too-short tracks, or on failure — except
         * that a failing file with a previous row keeps that row, so a transient network
         * error doesn't make a known track vanish from the library.
         */
        private fun extractOne(
                file: AudioFile,
                dateAdded: Long,
                hasFolderCover: Boolean,
                artClaims: MutableSet<Long>,
                fallback: SongEntity?,
                previousAlbums: Map<Long, AlbumEntity>,
        ): ScannedSong? {
                val retriever = MediaMetadataRetriever()
                var dataSource: SmbMediaDataSource? = null
                try {
                        dataSource = SmbMediaDataSource(smbClient, file.url)
                        retriever.setDataSource(dataSource)

                        fun tag(key: Int): String? =
                                retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }

                        val duration = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        if (duration in 1 until MIN_DURATION_MS) return null

                        val title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name.substringBeforeLast('.')
                        val artist =
                                tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                        ?: file.dirSegments.takeIf { it.size >= 2 }?.let { it[it.size - 2] }
                                        ?: UNKNOWN
                        val albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: artist
                        val album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: file.dirSegments.lastOrNull() ?: UNKNOWN
                        val track =
                                tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                                        ?.substringBefore('/')
                                        ?.trim()
                                        ?.toIntOrNull() ?: 0
                        val year =
                                tag(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
                                        ?: tag(MediaMetadataRetriever.METADATA_KEY_DATE)
                                                ?.take(4)
                                                ?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                                                ?.toInt()
                                        ?: 0
                        val genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""

                        val albumId = SmbIds.albumId(albumArtist, album)

                        // Embedded art only when the folder has no cover image and nothing is cached yet
                        if (!hasFolderCover && !artCache.has(albumId) && artClaims.add(albumId)) {
                                val written = retriever.embeddedPicture?.let { artCache.write(albumId, it) } ?: false
                                if (!written) artClaims.remove(albumId) // let another track of the album try
                        }

                        return ScannedSong(
                                SongEntity(
                                        id = SmbIds.songId(file.url),
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        albumId = albumId,
                                        artistId = SmbIds.artistId(artist),
                                        duration = duration,
                                        path = file.url,
                                        trackNumber = track,
                                        year = year,
                                        genre = genre,
                                        dateAdded = dateAdded,
                                        albumArtUri = null, // resolved per album after the art phase
                                        folderPath = file.dirUrl,
                                        size = file.size,
                                        source = SOURCE_SMB,
                                ),
                                albumArtist,
                        )
                } catch (e: Exception) {
                        Log.w(TAG, "Skipping ${file.url}: ${e.javaClass.simpleName}: ${e.message}")
                        return fallback?.let {
                                ScannedSong(it, previousAlbums[it.albumId]?.artist ?: it.artist)
                        }
                } finally {
                        runCatching { retriever.release() }
                        runCatching { dataSource?.close() }
                }
        }

        // ── Art ───────────────────────────────────────────────────────────────

        /** Downloads the folder cover of every album that has none cached yet. */
        private suspend fun fetchFolderCovers(
                albumDirs: Map<Long, Set<String>>,
                covers: Map<String, String>,
        ) = coroutineScope {
                val semaphore = Semaphore(EXTRACT_CONCURRENCY)
                albumDirs
                        .mapNotNull { (albumId, dirs) ->
                                if (artCache.has(albumId)) return@mapNotNull null
                                val coverUrl = dirs.firstNotNullOfOrNull { covers[it] } ?: return@mapNotNull null
                                async(Dispatchers.IO) {
                                        semaphore.withPermit { downloadCover(albumId, coverUrl) }
                                }
                        }
                        .awaitAll()
        }

        private fun downloadCover(albumId: Long, url: String) {
                try {
                        smbClient.openReader(url).use { reader ->
                                val length = reader.length
                                if (length <= 0 || length > MAX_COVER_BYTES) {
                                        Log.w(TAG, "Ignoring cover $url ($length bytes)")
                                        return
                                }
                                val bytes = ByteArray(length.toInt())
                                reader.seek(0)
                                var read = 0
                                while (read < bytes.size) {
                                        val n = reader.read(bytes, read, bytes.size - read)
                                        if (n <= 0) break
                                        read += n
                                }
                                if (read == bytes.size) artCache.write(albumId, bytes)
                                else Log.w(TAG, "Short read on cover $url ($read/${bytes.size})")
                        }
                } catch (e: Exception) {
                        Log.w(TAG, "Cover download failed for $url: ${e.javaClass.simpleName}: ${e.message}")
                }
        }

        // ── Aggregation ───────────────────────────────────────────────────────

        private fun buildAlbums(
                songs: List<ScannedSong>,
                previousAlbums: Map<Long, AlbumEntity>,
        ): Map<Long, AlbumEntity> {
                val albums = LinkedHashMap<Long, AlbumEntity>()
                for (s in songs) {
                        val song = s.song
                        val existing = albums[song.albumId]
                        if (existing != null) {
                                albums[song.albumId] =
                                        existing.copy(
                                                songCount = existing.songCount + 1,
                                                year = if (existing.year == 0) song.year else existing.year,
                                        )
                                continue
                        }
                        // Cached file wins; else keep a previously enriched (Last.fm) URL so a
                        // rescan doesn't blank it until the enricher runs again.
                        val artUri =
                                artCache.uriFor(song.albumId)
                                        ?: previousAlbums[song.albumId]?.artUri?.takeUnless { it.startsWith("file://") }
                        albums[song.albumId] =
                                AlbumEntity(
                                        id = song.albumId,
                                        title = song.album,
                                        artist = s.albumArtist,
                                        artistId = SmbIds.artistId(s.albumArtist),
                                        year = song.year,
                                        artUri = artUri,
                                        songCount = 1,
                                        source = SOURCE_SMB,
                                )
                }
                return albums
        }

        /**
         * One row per track artist (song + distinct album counts), plus album artists that
         * credit no track themselves (e.g. "Various Artists"), so `AlbumEntity.artistId`
         * always resolves.
         */
        private fun buildArtists(
                songs: List<SongEntity>,
                albums: Map<Long, AlbumEntity>,
        ): Map<Long, ArtistEntity> {
                val names = HashMap<Long, String>()
                val songCounts = HashMap<Long, Int>()
                val albumSets = HashMap<Long, MutableSet<Long>>()
                for (song in songs) {
                        names.getOrPut(song.artistId) { song.artist }
                        songCounts[song.artistId] = (songCounts[song.artistId] ?: 0) + 1
                        albumSets.getOrPut(song.artistId) { HashSet() }.add(song.albumId)
                }
                for (album in albums.values) {
                        names.getOrPut(album.artistId) { album.artist }
                        albumSets.getOrPut(album.artistId) { HashSet() }.add(album.id)
                }
                return names.mapValues { (id, name) ->
                        ArtistEntity(
                                id = id,
                                name = name,
                                albumCount = albumSets[id]?.size ?: 0,
                                songCount = songCounts[id] ?: 0,
                                source = SOURCE_SMB,
                        )
                }
        }

        private fun buildGenres(songs: List<SongEntity>): Map<Long, GenreEntity> {
                val genres = HashMap<Long, GenreEntity>()
                for (song in songs) {
                        if (song.genre.isBlank()) continue
                        val id = SmbIds.genreId(song.genre)
                        val existing = genres[id]
                        genres[id] =
                                existing?.copy(songCount = existing.songCount + 1)
                                        ?: GenreEntity(id = id, name = song.genre, songCount = 1, source = SOURCE_SMB)
                }
                return genres
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

        // ── Helpers ───────────────────────────────────────────────────────────

        /** Short, user-facing message for a scan failure. */
        private fun describe(e: Throwable): String =
                when {
                        e is SmbAuthenticationException -> "Access denied — check username/password"
                        e is FileNotFoundException -> "Share or folder not found"
                        e is SmbNotConfiguredException -> "No network share configured"
                        isUnreachable(e) -> "Server unreachable"
                        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                }

        private fun isUnreachable(e: Throwable): Boolean {
                var t: Throwable? = e
                var depth = 0
                while (t != null && depth++ < 8) {
                        when (t) {
                                is UnknownHostException,
                                is ConnectException,
                                is NoRouteToHostException,
                                is SocketTimeoutException,
                                is SocketException -> return true
                        }
                        if (t.javaClass.name == "jcifs.util.transport.TransportException") return true
                        if (t is IOException && t.message?.contains("Failed to connect", ignoreCase = true) == true) {
                                return true
                        }
                        t = t.cause
                }
                return false
        }

        private fun isOnUnmeteredNetwork(): Boolean {
                val cm =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE)
                                as? android.net.ConnectivityManager
                                ?: return false
                val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
                return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }

        private companion object {
                const val TAG = "SmbScanner"
                const val UNKNOWN = "<Unknown>" // same sentinel as MediaScanner
                const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L
                const val MIN_DURATION_MS = 30_000L
                const val EXTRACT_CONCURRENCY = 4
                const val PROGRESS_EVERY = 20
                const val MAX_DEPTH = 32
                const val MAX_COVER_BYTES = 10L * 1024 * 1024

                val AUDIO_EXTENSIONS =
                        setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "aiff", "aif")
                val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
                val COVER_NAMES = listOf("cover", "folder", "front", "albumart", "album")
        }
}
