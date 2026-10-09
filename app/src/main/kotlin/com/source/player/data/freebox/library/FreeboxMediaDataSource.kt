package com.source.player.data.freebox.library

import android.media.MediaDataSource
import com.source.player.data.freebox.FreeboxClient
import java.io.IOException
import kotlinx.coroutines.runBlocking

/** The per-file read budget of [FreeboxMediaDataSource] is exhausted. */
class FreeboxReadCapException(msg: String) : IOException(msg)

/** The scan pass was stopped (cancel, purge, box lost) while this file was being read. */
class FreeboxReadAbortedException(msg: String) : IOException(msg)

/**
 * [MediaDataSource] over HTTP Range reads of a Freebox file, so the platform
 * `MediaMetadataRetriever` reads tags without downloading the whole file.
 *
 * - [getSize] is the size from the folder listing (no network).
 * - Reads go through 128 KB blocks aligned on the block size, kept in a 16-block LRU;
 *   the first access preloads the first 256 KB in one request.
 * - At most [maxBytes] are fetched per file: past that, [readAt] throws
 *   [FreeboxReadCapException] and the retriever fails (the scanner then falls back to
 *   tags derived from the file and folder names).
 * - Every failure surfaces as an [IOException] from [readAt] (what the retriever
 *   expects) and is kept in [failure], because the retriever only reports a generic
 *   error: the scanner reads [failure] to tell "box gone" from "unreadable file". Once
 *   a read failed, every later [readAt] rethrows it without touching the network.
 *
 * [readAt] runs on a binder / native thread and blocks it with [runBlocking] while the
 * block is fetched; it must never be called on the main thread.
 */
class FreeboxMediaDataSource(
    private val client: FreeboxClient,
    private val url: String,
    private val fileSize: Long,
    private val maxBytes: Long = MAX_BYTES_PER_FILE,
    /** Polled before each network read; true makes the read fail fast. */
    private val shouldAbort: () -> Boolean = { false },
) : MediaDataSource() {

    private val blocks =
        object : LinkedHashMap<Long, ByteArray>(MAX_BLOCKS + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>): Boolean =
                size > MAX_BLOCKS
        }
    private var preloaded = false
    private var closed = false

    /** Bytes fetched from the box for this file so far. */
    @Volatile
    var bytesRead: Long = 0L
        private set

    /** First read failure, null while every read succeeded. */
    @Volatile
    var failure: IOException? = null
        private set

    override fun getSize(): Long = fileSize

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (closed) throw IOException("Data source closed ($url)")
        failure?.let { throw it }
        if (size <= 0) return 0
        if (position < 0) throw IOException("Negative position $position ($url)")
        if (position >= fileSize) return -1
        if (!preloaded) {
            preloaded = true
            preload()
        }

        var total = 0
        var pos = position
        val wanted = minOf(size.toLong(), fileSize - position).toInt()
        while (total < wanted) {
            val index = pos / BLOCK_SIZE
            val block = blockAt(index)
            val inBlock = (pos - index * BLOCK_SIZE).toInt()
            // Block shorter than expected: the file shrank since the listing → EOF.
            if (inBlock >= block.size) break
            val n = minOf(block.size - inBlock, wanted - total)
            System.arraycopy(block, inBlock, buffer, offset + total, n)
            total += n
            pos += n
            if (block.size < BLOCK_SIZE) break // last (short) block
        }
        return if (total == 0) -1 else total
    }

    @Synchronized
    override fun close() {
        closed = true
        blocks.clear()
    }

    /** First [PRELOAD_BYTES] in a single request, split into blocks. */
    private fun preload() {
        val length = minOf(PRELOAD_BYTES.toLong(), fileSize).toInt()
        if (length <= BLOCK_SIZE) return // a single block: fetched on demand anyway
        val bytes = fetch(0L, length)
        var start = 0
        var index = 0L
        while (start < bytes.size) {
            val end = minOf(start + BLOCK_SIZE, bytes.size)
            blocks[index++] = bytes.copyOfRange(start, end)
            start = end
        }
    }

    private fun blockAt(index: Long): ByteArray {
        blocks[index]?.let { return it }
        val start = index * BLOCK_SIZE
        val length = minOf(BLOCK_SIZE.toLong(), fileSize - start).toInt()
        return fetch(start, length).also { blocks[index] = it }
    }

    private fun fetch(offset: Long, length: Int): ByteArray {
        try {
            if (shouldAbort()) throw FreeboxReadAbortedException("Scan stopped ($url)")
            if (bytesRead + length > maxBytes) {
                throw FreeboxReadCapException("Read cap of $maxBytes bytes reached ($url)")
            }
            val bytes = runBlocking { client.readRange(url, offset, length, requireLan = true) }
            bytesRead += bytes.size
            return bytes
        } catch (e: IOException) {
            failure = e
            throw e
        } catch (e: Exception) {
            // Anything else (runtime errors in the HTTP stack…) must still reach the
            // retriever as an IOException.
            val wrapped = IOException("Freebox read failed ($url): ${e.javaClass.simpleName}", e)
            failure = wrapped
            throw wrapped
        }
    }

    companion object {
        const val BLOCK_SIZE = 128 * 1024
        const val MAX_BLOCKS = 16
        const val PRELOAD_BYTES = 256 * 1024
        const val MAX_BYTES_PER_FILE = 16L * 1024 * 1024
    }
}
