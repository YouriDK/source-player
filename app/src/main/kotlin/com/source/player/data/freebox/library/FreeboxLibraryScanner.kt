package com.source.player.data.freebox.library

import android.media.MediaMetadataRetriever
import android.util.Log
import com.source.player.data.db.dao.AlbumDao
import com.source.player.data.db.dao.ArtistDao
import com.source.player.data.db.dao.GenreDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.AlbumEntity
import com.source.player.data.db.entity.ArtistEntity
import com.source.player.data.db.entity.GenreEntity
import com.source.player.data.db.entity.SOURCE_FREEBOX
import com.source.player.data.db.entity.SongEntity
import com.source.player.data.freebox.FreeboxAudio
import com.source.player.data.freebox.FreeboxAuthException
import com.source.player.data.freebox.FreeboxClient
import com.source.player.data.freebox.FreeboxFolder
import com.source.player.data.freebox.FreeboxPaths
import com.source.player.data.freebox.FreeboxUnreachableException
import com.source.player.data.freebox.RemoteEntry
import com.source.player.data.scanner.AlbumArtEnricher
import com.source.player.data.scanner.ScanProgress
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** How a [FreeboxLibraryScanner.scan] pass ended. */
enum class ScanStatus {
    /** Whole tree walked and read: orphans deleted, the pass can be stamped as a full scan. */
    COMPLETE,

    /**
     * Some folders could not be listed, or the box was lost mid-pass (left the LAN,
     * session refused): what was read is indexed, nothing is deleted.
     */
    PARTIAL,

    /** Nothing done: the root could not be listed, or the library was purged mid-pass. */
    ABORTED,

    /** Stopped by [FreeboxLibraryScanner.requestCancel]; rows written so far are kept, nothing is deleted. */
    CANCELLED,
}

data class ScanOutcome(
    val status: ScanStatus,
    /** Audio files found by the walk. */
    val filesSeen: Int = 0,
    /** Files whose tags were read over the network (successfully or not). */
    val extracted: Int = 0,
    /** Files reused from the previous scan without any read. */
    val reused: Int = 0,
    /** Files indexed from their name only (unreadable tags, read cap reached). */
    val fallbacks: Int = 0,
    /** Songs deleted because they are gone from the folder (COMPLETE passes only). */
    val deleted: Int = 0,
    /** Bytes fetched from the box (tags + folder covers). */
    val bytesRead: Long = 0,
    val durationMs: Long = 0,
    /** User-facing message when the pass was not COMPLETE, else null. */
    val error: String? = null,
)

/**
 * Indexes the Freebox music folder into the library tables, tagged with [SOURCE_FREEBOX].
 *
 * - Ids are negative ([FreeboxIds]) so they never collide with MediaStore ids.
 * - `SongEntity.path` is the file's `fbx://` URL (exactly what the live browser plays),
 *   `folderPath` its parent URL without trailing slash.
 * - Incremental: a file whose size and mtime match its row is reused without any network
 *   read; the others go through `MediaMetadataRetriever` over [FreeboxMediaDataSource].
 * - New rows are written every [BATCH_SIZE] files (with albums / artists / genres
 *   recomputed from the DB), so a scan killed midway resumes without re-reading them.
 * - A pass that hit unreadable sub-folders, or lost the box, is partial: rows are
 *   upserted but no song is deleted, so a flaky folder never wipes part of the library.
 * - Every list / range read requires the LAN (no scan over mobile data).
 *
 * Callers own the trigger policy (network, staleness, timestamps): [ScanOutcome.status]
 * COMPLETE is the signal that a full pass succeeded.
 */
@Singleton
class FreeboxLibraryScanner @Inject constructor(
    private val client: FreeboxClient,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val genreDao: GenreDao,
    private val artEnricher: AlbumArtEnricher,
    private val artCache: FreeboxArtCache,
) {
    /** Held by the coroutine running passes; [pending] callers are served by it. */
    private val scanMutex = Mutex()
    /** Guards [pending] / [pendingRoot] together with the [scanMutex] hand-off. */
    private val pendingLock = Any()
    private var pending: CompletableDeferred<ScanOutcome>? = null
    private var pendingRoot: FreeboxFolder? = null

    /** Serializes DB writes of a pass with [purge]. */
    private val writeMutex = Mutex()
    /** Bumped by [purge]; a pass started before a purge must not re-insert its rows. */
    @Volatile private var clearGeneration = 0
    @Volatile private var cancelRequested = false

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    private val _progressMessage = MutableStateFlow<String?>(null)
    /** Human-readable scan status ("Scanning Freebox… n / N"), null when idle. */
    val progressMessage: StateFlow<String?> = _progressMessage.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    /** Message of the last pass that did not complete, null after a complete pass. */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Scans [root] (the music folder). Concurrent calls are coalesced: a call made while
     * a pass is running waits for one extra pass (on the latest requested root) run right
     * after it, and returns that pass's outcome.
     */
    suspend fun scan(root: FreeboxFolder): ScanOutcome =
        withContext(Dispatchers.IO) {
            val waiter = synchronized(pendingLock) {
                if (scanMutex.tryLock()) {
                    null
                } else {
                    pendingRoot = root
                    pending ?: CompletableDeferred<ScanOutcome>().also { pending = it }
                }
            }
            if (waiter != null) return@withContext waiter.await()

            var currentRoot = root
            var currentWaiter: CompletableDeferred<ScanOutcome>? = null
            var holdsLock = true
            try {
                while (true) {
                    val outcome = runScanPass(currentRoot)
                    currentWaiter?.complete(outcome)
                    currentWaiter = null
                    val next = synchronized(pendingLock) {
                        val p = pending
                        if (p == null) {
                            // Unlock inside the same critical section: a caller can't
                            // enqueue after this check and then wait for nobody.
                            scanMutex.unlock()
                            holdsLock = false
                            null
                        } else {
                            pending = null
                            p to pendingRoot!!
                        }
                    }
                    if (next == null) return@withContext outcome
                    currentWaiter = next.first
                    currentRoot = next.second
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            } finally {
                if (holdsLock) {
                    val aborted = ScanOutcome(ScanStatus.ABORTED, error = "Scan interrupted")
                    currentWaiter?.complete(aborted)
                    synchronized(pendingLock) {
                        pending?.complete(aborted)
                        pending = null
                        scanMutex.unlock()
                    }
                }
            }
        }

    /** Stops the running pass (rows already written are kept) and drops any queued pass. */
    fun requestCancel() {
        cancelRequested = true
        synchronized(pendingLock) {
            pending?.complete(ScanOutcome(ScanStatus.CANCELLED, error = "Scan cancelled"))
            pending = null
        }
    }

    /**
     * Removes every Freebox row (songs, albums, artists, genres) and the cached artwork.
     * A pass running meanwhile stops at its next write and discards its results; queued
     * passes are dropped.
     */
    suspend fun purge(): Unit =
        withContext(Dispatchers.IO + NonCancellable) {
            synchronized(pendingLock) {
                pending?.complete(ScanOutcome(ScanStatus.ABORTED, error = "Library purged"))
                pending = null
            }
            writeMutex.withLock {
                clearGeneration++
                songDao.deleteBySource(SOURCE_FREEBOX)
                albumDao.deleteBySource(SOURCE_FREEBOX)
                artistDao.deleteBySource(SOURCE_FREEBOX)
                genreDao.deleteBySource(SOURCE_FREEBOX)
                artCache.clear()
            }
            _lastError.value = null
            if (!scanMutex.isLocked) setProgress(ScanProgress())
            Log.i(TAG, "Freebox library purged")
        }

    // ── Scan pass ─────────────────────────────────────────────────────────

    /** Why a pass stopped before reading every file. */
    private sealed class Stop {
        object Cancelled : Stop()
        object Purged : Stop()
        class BoxLost(val cause: Throwable) : Stop()
    }

    private class PassState(val generation: Int) {
        val stop = AtomicReference<Stop?>(null)
        val bytesRead = AtomicLong(0)
        val extracted = AtomicInteger(0)
        val reused = AtomicInteger(0)
        val fallbacks = AtomicInteger(0)
        val done = AtomicInteger(0)
        /** Ids of the songs this pass found (reused, read or name-only). */
        val keptIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()
        /** Albums whose embedded art is being fetched by some track right now. */
        val artClaims: MutableSet<Long> = ConcurrentHashMap.newKeySet()

        fun stopped(): Boolean = stop.get() != null
    }

    private suspend fun runScanPass(root: FreeboxFolder): ScanOutcome {
        cancelRequested = false
        val startedAt = System.currentTimeMillis()
        val state = PassState(clearGeneration)
        setProgress(ScanProgress(isScanning = true))
        try {
            // 1. Walk — a failure at the root aborts with the DB untouched
            val walk = try {
                walk(root.url, state)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Walk failed for ${root.url}: ${e.javaClass.simpleName}: ${e.message}")
                val message = describe(e)
                _lastError.value = message
                return ScanOutcome(
                    ScanStatus.ABORTED,
                    durationMs = System.currentTimeMillis() - startedAt,
                    error = message,
                )
            }
            val total = walk.files.size
            setProgress(ScanProgress(isScanning = true, totalCount = total))

            // 2. Tags — reuse unchanged rows, read the rest, write every BATCH_SIZE
            val previousSongs = songDao.getAllBySource(SOURCE_FREEBOX).associateBy { it.path }
            val previousAlbums = albumDao.getAllBySource(SOURCE_FREEBOX).associateBy { it.id }
            extractAll(walk, previousSongs, previousAlbums, state, total)

            // 3. Finalize: covers, aggregates, orphans — all from the DB
            val stop = state.stop.get()
            val complete = !walk.partial && stop == null
            var deleted = 0
            var albumsToEnrich: Collection<AlbumEntity> = emptyList()
            if (stop !is Stop.Purged) {
                if (stop !is Stop.BoxLost && stop !is Stop.Cancelled) {
                    fetchFolderCovers(songDao.getAllBySource(SOURCE_FREEBOX), walk.covers, state)
                }
                val result = writeMutex.withLock {
                    if (state.generation != clearGeneration) return@withLock null
                    if (complete) {
                        val orphans = songDao.getIdsBySource(SOURCE_FREEBOX).filterNot { it in state.keptIds }
                        orphans.chunked(DELETE_CHUNK).forEach { songDao.deleteByIds(it) }
                        deleted = orphans.size
                    }
                    val albums = refreshAggregates()
                    if (complete) {
                        val removedArt = artCache.retainOnly(albums.keys)
                        if (removedArt > 0) Log.d(TAG, "Removed $removedArt orphan cover(s)")
                    }
                    albums
                }
                if (result == null) state.stop.compareAndSet(null, Stop.Purged)
                else albumsToEnrich = result.values
            }

            val finalStop = state.stop.get()
            val status = when {
                finalStop is Stop.Purged -> ScanStatus.ABORTED
                finalStop is Stop.Cancelled -> ScanStatus.CANCELLED
                finalStop is Stop.BoxLost || walk.partial -> ScanStatus.PARTIAL
                else -> ScanStatus.COMPLETE
            }
            val error = when (finalStop) {
                is Stop.Purged -> "Library purged during scan"
                is Stop.Cancelled -> "Scan cancelled"
                is Stop.BoxLost -> describe(finalStop.cause)
                null -> if (walk.partial) "Some folders could not be read" else null
            }
            if (finalStop !is Stop.Purged) _lastError.value = error

            val outcome = ScanOutcome(
                status = status,
                filesSeen = total,
                extracted = state.extracted.get(),
                reused = state.reused.get(),
                fallbacks = state.fallbacks.get(),
                deleted = deleted,
                bytesRead = state.bytesRead.get(),
                durationMs = System.currentTimeMillis() - startedAt,
                error = error,
            )
            Log.i(
                TAG,
                "Freebox scan $status: ${outcome.filesSeen} files seen, ${outcome.extracted} read " +
                    "(${outcome.fallbacks} name-only), ${outcome.reused} reused, ${outcome.deleted} deleted, " +
                    "${outcome.bytesRead / 1024} KB read in ${outcome.durationMs} ms" +
                    (error?.let { " — $it" } ?: ""),
            )
            setProgress(ScanProgress(isScanning = false, scannedCount = state.done.get(), totalCount = total))

            // Online art enrichment, after the scan is reported done so the UI isn't
            // stuck in "Scanning…" through Last.fm calls
            if (albumsToEnrich.isNotEmpty() && clearGeneration == state.generation) {
                try {
                    artEnricher.enrich(albumsToEnrich) { artCache.has(it.id) || it.artUri != null }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Art enrichment failed: ${e.javaClass.simpleName}: ${e.message}")
                }
                // The enricher upserts album rows: undo them if a purge ran meanwhile.
                writeMutex.withLock {
                    if (clearGeneration != state.generation) {
                        albumsToEnrich.map { it.id }.chunked(DELETE_CHUNK).forEach { albumDao.deleteByIds(it) }
                    }
                }
            }
            return outcome
        } catch (e: CancellationException) {
            setProgress(ScanProgress(isScanning = false))
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Freebox scan failed", e)
            val message = describe(e)
            _lastError.value = message
            setProgress(ScanProgress(isScanning = false))
            return ScanOutcome(
                ScanStatus.PARTIAL,
                filesSeen = _progress.value.totalCount,
                extracted = state.extracted.get(),
                reused = state.reused.get(),
                fallbacks = state.fallbacks.get(),
                bytesRead = state.bytesRead.get(),
                durationMs = System.currentTimeMillis() - startedAt,
                error = message,
            )
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
        /** Listing mtime, epoch SECONDS (the unit of `SongEntity.remoteMtime`). */
        val mtime: Long,
    )

    private class WalkResult(
        val files: List<AudioFile>,
        /** Directory URL (no trailing slash) → cover image entry. */
        val covers: Map<String, RemoteEntry>,
        val partial: Boolean,
    )

    /**
     * Breadth-first walk. Throws if the root itself can't be listed. A sub-folder that
     * fails to list makes the walk partial; losing the box (left the LAN, session
     * refused) ends the walk and stops the pass.
     */
    private suspend fun walk(rootUrl: String, state: PassState): WalkResult {
        val files = ArrayList<AudioFile>()
        val covers = HashMap<String, RemoteEntry>()
        var partial = false
        val visited = HashSet<String>()
        val queue = ArrayDeque<Pair<String, List<String>>>()
        queue.addLast(normalize(rootUrl) to emptyList())

        while (queue.isNotEmpty()) {
            if (cancelRequested) {
                state.stop.compareAndSet(null, Stop.Cancelled)
                partial = true
                break
            }
            val (dirUrl, segments) = queue.removeFirst()
            if (!visited.add(dirUrl)) continue
            val entries = try {
                client.list(dirUrl, requireLan = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (segments.isEmpty()) throw e
                Log.w(TAG, "Unreadable folder $dirUrl: ${e.javaClass.simpleName}: ${e.message}")
                partial = true
                if (isBoxLost(e)) {
                    state.stop.compareAndSet(null, Stop.BoxLost(e))
                    break
                }
                continue
            }

            val images = ArrayList<RemoteEntry>()
            for (entry in entries) {
                val url = normalize(entry.url)
                if (entry.isDirectory) {
                    if (segments.size < MAX_DEPTH) queue.addLast(url to segments + entry.name)
                    continue
                }
                when {
                    FreeboxAudio.isAudio(entry.name) -> files.add(
                        AudioFile(
                            url = url,
                            name = entry.name,
                            dirUrl = FreeboxPaths.parentUrl(url),
                            dirSegments = segments,
                            size = entry.size,
                            mtime = entry.lastModified / 1000,
                        )
                    )
                    FreeboxAudio.isImage(entry.name) -> images.add(entry.copy(url = url))
                }
            }
            pickCover(images)?.let { covers[FreeboxPaths.parentUrl(it.url)] = it }
        }
        return WalkResult(files, covers, partial)
    }

    /** Same canonical form as `FreeboxPaths.toUrl` (what the live browser plays). */
    private fun normalize(url: String): String = FreeboxPaths.toUrl(FreeboxPaths.toDisplayPath(url))

    /** Well-known cover names first (in [COVER_NAMES] order), else the only image in the folder. */
    private fun pickCover(images: List<RemoteEntry>): RemoteEntry? {
        if (images.isEmpty()) return null
        for (base in COVER_NAMES) {
            images.firstOrNull { it.name.substringBeforeLast('.').equals(base, ignoreCase = true) }
                ?.let { return it }
        }
        return images.singleOrNull()
    }

    // ── Tags ──────────────────────────────────────────────────────────────

    private sealed class FileResult {
        /** Row already in the DB and still valid: nothing to write. */
        class Kept(val id: Long) : FileResult()
        /** New / changed row to write. */
        class Write(val song: SongEntity) : FileResult()
        /** Not indexed (too short, or pass stopped before it was read). */
        object Skip : FileResult()
    }

    private suspend fun extractAll(
        walk: WalkResult,
        previousSongs: Map<String, SongEntity>,
        previousAlbums: Map<Long, AlbumEntity>,
        state: PassState,
        total: Int,
    ) = coroutineScope {
        val semaphore = Semaphore(EXTRACT_CONCURRENCY)
        val bufferLock = Mutex()
        val buffer = ArrayList<SongEntity>(BATCH_SIZE)
        val nowSeconds = System.currentTimeMillis() / 1000

        suspend fun flush(force: Boolean) {
            val batch = bufferLock.withLock {
                if (buffer.isEmpty() || (!force && buffer.size < BATCH_SIZE)) return
                ArrayList(buffer).also { buffer.clear() }
            }
            writeBatch(batch, walk.covers, state)
        }

        walk.files.map { file ->
            async(Dispatchers.IO) {
                val result = processFile(file, previousSongs, previousAlbums, state, walk, nowSeconds, semaphore)
                when (result) {
                    is FileResult.Kept -> state.keptIds.add(result.id)
                    is FileResult.Write -> {
                        state.keptIds.add(result.song.id)
                        bufferLock.withLock { buffer.add(result.song) }
                        flush(force = false)
                    }
                    FileResult.Skip -> Unit
                }
                val n = state.done.incrementAndGet()
                if (n % PROGRESS_EVERY == 0 || n == total) {
                    setProgress(ScanProgress(isScanning = true, scannedCount = n, totalCount = total))
                }
            }
        }.awaitAll()
        flush(force = true)
    }

    private suspend fun processFile(
        file: AudioFile,
        previousSongs: Map<String, SongEntity>,
        previousAlbums: Map<Long, AlbumEntity>,
        state: PassState,
        walk: WalkResult,
        nowSeconds: Long,
        semaphore: Semaphore,
    ): FileResult {
        val previous = previousSongs[file.url]
        if (previous != null && previous.size == file.size && previous.remoteMtime == file.mtime) {
            state.reused.incrementAndGet()
            return FileResult.Kept(previous.id)
        }
        // Once the pass is stopped, known rows are kept as they are (no deletion follows)
        if (state.stopped() || cancelRequested) {
            if (cancelRequested) state.stop.compareAndSet(null, Stop.Cancelled)
            return previous?.let { FileResult.Kept(it.id) } ?: FileResult.Skip
        }
        return semaphore.withPermit {
            if (state.stopped() || cancelRequested) {
                if (cancelRequested) state.stop.compareAndSet(null, Stop.Cancelled)
                return@withPermit previous?.let { FileResult.Kept(it.id) } ?: FileResult.Skip
            }
            extractOne(
                file = file,
                // First seen: the file's mtime, so a first scan doesn't flood
                // "Recently added" with the whole folder
                dateAdded = previous?.dateAdded ?: file.mtime.takeIf { it > 0 } ?: nowSeconds,
                hasFolderCover = file.dirUrl in walk.covers,
                previous = previous,
                previousAlbums = previousAlbums,
                state = state,
            )
        }
    }

    /**
     * Reads the tags of one file. Unreadable tags (corrupt file, read cap) give a row
     * built from the file and folder names; a lost box stops the pass and keeps the
     * previous row if any.
     */
    private fun extractOne(
        file: AudioFile,
        dateAdded: Long,
        hasFolderCover: Boolean,
        previous: SongEntity?,
        previousAlbums: Map<Long, AlbumEntity>,
        state: PassState,
    ): FileResult {
        val startedAt = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        val source = FreeboxMediaDataSource(
            client = client,
            url = file.url,
            fileSize = file.size,
            shouldAbort = { cancelRequested || state.stopped() },
        )
        state.extracted.incrementAndGet()
        try {
            retriever.setDataSource(source)
            checkReads(source)

            fun tag(key: Int): String? = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }

            val duration = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (duration in 1 until MIN_DURATION_MS) return FileResult.Skip

            val title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name.substringBeforeLast('.')
            val artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: folderArtist(file)
            val albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: artist
            val album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: folderAlbum(file)
            val track = tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
            val year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
                ?: tag(MediaMetadataRetriever.METADATA_KEY_DATE)
                    ?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }?.toInt()
                ?: 0
            val genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            checkReads(source)

            val albumId = FreeboxIds.album(albumArtist, album)
            rememberAlbumArtist(albumId, albumArtist)

            // Embedded art only when the folder has no cover image and nothing is cached yet
            if (!hasFolderCover && !artCache.has(albumId) && state.artClaims.add(albumId)) {
                val picture = runCatching { retriever.embeddedPicture }.getOrNull()
                // A read that failed meanwhile may have truncated the picture: don't cache it
                val written = picture != null && source.failure == null && artCache.write(albumId, picture)
                if (!written) state.artClaims.remove(albumId) // let another track of the album try
            }
            // Box lost while reading the picture: the tags above are valid, but stop the pass
            source.failure?.let { if (isBoxLost(it)) state.stop.compareAndSet(null, Stop.BoxLost(it)) }

            Log.d(TAG, "Read ${file.url}: ${source.bytesRead} bytes in ${System.currentTimeMillis() - startedAt} ms")
            return FileResult.Write(
                songEntity(file, dateAdded, title, artist, album, albumId, duration, track, year, genre)
            )
        } catch (e: Exception) {
            val failure = source.failure
            when {
                failure != null && isBoxLost(failure) -> {
                    state.stop.compareAndSet(null, Stop.BoxLost(failure))
                    Log.w(TAG, "Box lost while reading ${file.url}: ${failure.message}")
                    return previous?.let { FileResult.Kept(it.id) } ?: FileResult.Skip
                }
                failure is FreeboxReadAbortedException ->
                    return previous?.let { FileResult.Kept(it.id) } ?: FileResult.Skip
                failure != null && failure !is FreeboxReadCapException && previous != null -> {
                    // Transient read error on a known file: keep its row, retry next scan.
                    Log.w(TAG, "Keeping previous row of ${file.url}: ${failure.message}")
                    return FileResult.Kept(previous.id)
                }
            }
            state.fallbacks.incrementAndGet()
            Log.w(
                TAG,
                "Name-only tags for ${file.url} (${source.bytesRead} bytes read): " +
                    (failure?.message ?: "${e.javaClass.simpleName}: ${e.message}"),
            )
            val artist = folderArtist(file)
            val album = folderAlbum(file)
            val albumId = FreeboxIds.album(artist, album)
            val song = songEntity(
                file, dateAdded, file.name.substringBeforeLast('.'), artist, album, albumId,
                duration = 0, track = 0, year = 0, genre = "",
            )
            // A transient read error must not freeze name-only tags: a negative mtime never
            // matches a listing, so the file is read again next scan.
            val retryNextScan = failure != null && failure !is FreeboxReadCapException
            return FileResult.Write(if (retryNextScan) song.copy(remoteMtime = RETRY_MTIME) else song)
        } finally {
            state.bytesRead.addAndGet(source.bytesRead)
            runCatching { retriever.release() }
            runCatching { source.close() }
        }
    }

    /**
     * The native retriever turns a readAt exception into EOF and may still return
     * (truncated) metadata: any read failure but the read cap (header already read)
     * invalidates the result.
     */
    private fun checkReads(source: FreeboxMediaDataSource) {
        source.failure?.let { if (it !is FreeboxReadCapException) throw it }
    }

    private fun folderArtist(file: AudioFile): String =
        file.dirSegments.takeIf { it.size >= 2 }?.let { it[it.size - 2] } ?: UNKNOWN

    private fun folderAlbum(file: AudioFile): String = file.dirSegments.lastOrNull() ?: UNKNOWN

    private fun songEntity(
        file: AudioFile,
        dateAdded: Long,
        title: String,
        artist: String,
        album: String,
        albumId: Long,
        duration: Long,
        track: Int,
        year: Int,
        genre: String,
    ) = SongEntity(
        id = FreeboxIds.song(file.url),
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        artistId = FreeboxIds.artist(artist),
        duration = duration,
        path = file.url,
        trackNumber = track,
        year = year,
        genre = genre,
        dateAdded = dateAdded,
        albumArtUri = null, // resolved from the art cache when the batch is written
        folderPath = file.dirUrl,
        size = file.size,
        source = SOURCE_FREEBOX,
        remoteMtime = file.mtime,
    )

    // ── Writes ────────────────────────────────────────────────────────────

    /**
     * Writes one batch: folder covers of its albums first, then the songs (with their
     * cached art), then albums / artists / genres recomputed from the DB so the library
     * fills in as the scan goes.
     */
    private suspend fun writeBatch(batch: List<SongEntity>, covers: Map<String, RemoteEntry>, state: PassState) {
        if (state.stop.get() !is Stop.BoxLost) fetchFolderCovers(batch, covers, state)
        val ok = writeMutex.withLock {
            if (state.generation != clearGeneration) return@withLock false
            val previousArt = HashMap<Long, String?>()
            songDao.upsertAll(
                batch.map { s ->
                    val art = previousArt.getOrPut(s.albumId) { artCache.uriFor(s.albumId) }
                    s.copy(albumArtUri = art)
                }
            )
            refreshAggregates()
            true
        }
        if (!ok) state.stop.compareAndSet(null, Stop.Purged)
        else Log.d(TAG, "Wrote batch of ${batch.size} songs")
    }

    /**
     * Rebuilds albums / artists / genres from every Freebox song in the DB, upserts them,
     * deletes the ones no song refers to any more, and aligns `songs.albumArtUri` on its
     * album's art. Returns the albums. Call under [writeMutex].
     */
    private suspend fun refreshAggregates(): Map<Long, AlbumEntity> {
        val songs = songDao.getAllBySource(SOURCE_FREEBOX)
        val previousAlbums = albumDao.getAllBySource(SOURCE_FREEBOX).associateBy { it.id }
        val albums = buildAlbums(songs, previousAlbums)
        val artists = buildArtists(songs, albums)
        val genres = buildGenres(songs)

        // Songs whose art lags behind their album's (cover fetched later, Last.fm URL…)
        val artFixes = songs.filter { it.albumArtUri != albums[it.albumId]?.artUri }
            .map { it.copy(albumArtUri = albums[it.albumId]?.artUri) }
        if (artFixes.isNotEmpty()) songDao.upsertAll(artFixes)

        albumDao.upsertAll(albums.values.toList())
        artistDao.upsertAll(artists.values.toList())
        genreDao.upsertAll(genres.values.toList())

        // Aggregates are derived from the songs table: one with no song left is stale
        // whatever the walk saw.
        deleteOrphans(albumDao.getIdsBySource(SOURCE_FREEBOX), albums.keys) { albumDao.deleteByIds(it) }
        deleteOrphans(artistDao.getIdsBySource(SOURCE_FREEBOX), artists.keys) { artistDao.deleteByIds(it) }
        deleteOrphans(genreDao.getIdsBySource(SOURCE_FREEBOX), genres.keys) { genreDao.deleteByIds(it) }
        return albums
    }

    // ── Art ───────────────────────────────────────────────────────────────

    /** Downloads the folder cover of every album of [songs] that has none cached yet. */
    private suspend fun fetchFolderCovers(
        songs: List<SongEntity>,
        covers: Map<String, RemoteEntry>,
        state: PassState,
    ) = coroutineScope {
        val albumDirs = HashMap<Long, MutableSet<String>>()
        for (s in songs) albumDirs.getOrPut(s.albumId) { LinkedHashSet() }.add(s.folderPath)
        val semaphore = Semaphore(EXTRACT_CONCURRENCY)
        albumDirs.mapNotNull { (albumId, dirs) ->
            val cover = dirs.firstNotNullOfOrNull { covers[it] } ?: return@mapNotNull null
            if (artCache.has(albumId) || !state.artClaims.add(albumId)) return@mapNotNull null
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val ok = !state.stopped() && downloadCover(albumId, cover, state)
                    if (!ok) state.artClaims.remove(albumId)
                }
            }
        }.awaitAll()
    }

    private suspend fun downloadCover(albumId: Long, cover: RemoteEntry, state: PassState): Boolean {
        if (cover.size <= 0 || cover.size > MAX_COVER_BYTES) {
            Log.w(TAG, "Ignoring cover ${cover.url} (${cover.size} bytes)")
            return false
        }
        return try {
            val bytes = client.readRange(cover.url, 0, cover.size.toInt(), requireLan = true)
            state.bytesRead.addAndGet(bytes.size.toLong())
            if (bytes.size.toLong() != cover.size) {
                Log.w(TAG, "Short read on cover ${cover.url} (${bytes.size}/${cover.size})")
                false
            } else {
                artCache.write(albumId, bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cover download failed for ${cover.url}: ${e.javaClass.simpleName}: ${e.message}")
            if (isBoxLost(e)) state.stop.compareAndSet(null, Stop.BoxLost(e))
            false
        }
    }

    // ── Aggregation ───────────────────────────────────────────────────────

    /**
     * Album artist is not stored on songs: it is taken from the existing album row when
     * the song's album id matches one, else from the song's artist.
     */
    private fun buildAlbums(
        songs: List<SongEntity>,
        previousAlbums: Map<Long, AlbumEntity>,
    ): Map<Long, AlbumEntity> {
        val albums = LinkedHashMap<Long, AlbumEntity>()
        for (song in songs) {
            val existing = albums[song.albumId]
            if (existing != null) {
                albums[song.albumId] = existing.copy(
                    songCount = existing.songCount + 1,
                    year = if (existing.year == 0) song.year else existing.year,
                )
                continue
            }
            val previous = previousAlbums[song.albumId]
            val albumArtist = albumArtists[song.albumId] ?: previous?.artist ?: song.artist
            // Cached file wins; else keep a previously enriched (Last.fm) URL so a
            // rescan doesn't blank it until the enricher runs again.
            val artUri = artCache.uriFor(song.albumId)
                ?: previous?.artUri?.takeUnless { it.startsWith("file://") }
            albums[song.albumId] = AlbumEntity(
                id = song.albumId,
                title = song.album,
                artist = albumArtist,
                artistId = FreeboxIds.artist(albumArtist),
                year = song.year,
                artUri = artUri,
                songCount = 1,
                source = SOURCE_FREEBOX,
            )
        }
        return albums
    }

    /**
     * Album artist per album id, as read from the tags in this process. Songs don't store
     * it, and the album row of a new album doesn't exist yet when [buildAlbums] first
     * sees it. The id hashes the album artist, so an entry can never go stale.
     */
    private val albumArtists = ConcurrentHashMap<Long, String>()

    private fun rememberAlbumArtist(albumId: Long, albumArtist: String) {
        albumArtists.putIfAbsent(albumId, albumArtist)
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
                source = SOURCE_FREEBOX,
            )
        }
    }

    private fun buildGenres(songs: List<SongEntity>): Map<Long, GenreEntity> {
        val genres = HashMap<Long, GenreEntity>()
        for (song in songs) {
            if (song.genre.isBlank()) continue
            val id = FreeboxIds.genre(song.genre)
            val existing = genres[id]
            genres[id] = existing?.copy(songCount = existing.songCount + 1)
                ?: GenreEntity(id = id, name = song.genre, songCount = 1, source = SOURCE_FREEBOX)
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
        existingIds.filterNot { it in activeIds }.chunked(DELETE_CHUNK).forEach { deleteByIds(it) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun setProgress(p: ScanProgress) {
        _progress.value = p
        _progressMessage.value = when {
            p.isScanning && p.totalCount > 0 -> "Scanning Freebox… ${p.scannedCount} / ${p.totalCount}"
            p.isScanning -> "Scanning Freebox…"
            else -> null
        }
    }

    /** The box can't be used any more for this pass: off the LAN, unreachable, or session refused. */
    private fun isBoxLost(e: Throwable): Boolean =
        e is FreeboxUnreachableException || e is FreeboxAuthException

    /** Short, user-facing message for a scan failure. */
    private fun describe(e: Throwable): String = when {
        e is FreeboxAuthException -> "Freebox refused access — pair the app again"
        e is FreeboxUnreachableException -> "Freebox not reachable on the local network"
        e is FileNotFoundException -> "Music folder not found on the Freebox"
        isTimeout(e) -> "Freebox timed out"
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }

    private fun isTimeout(e: Throwable): Boolean {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth++ < 8) {
            if (t is SocketTimeoutException || t.javaClass.simpleName.contains("Timeout")) return true
            t = t.cause
        }
        return false
    }

    private companion object {
        const val TAG = "FreeboxScanner"
        const val UNKNOWN = "<Unknown>" // same sentinel as MediaScanner
        const val RETRY_MTIME = -1L
        const val MIN_DURATION_MS = 30_000L
        const val EXTRACT_CONCURRENCY = 3
        const val BATCH_SIZE = 200
        const val PROGRESS_EVERY = 10
        const val DELETE_CHUNK = 500
        const val MAX_DEPTH = 32
        const val MAX_COVER_BYTES = 10L * 1024 * 1024
        val COVER_NAMES = listOf("cover", "folder", "front", "albumart", "album")
    }
}
