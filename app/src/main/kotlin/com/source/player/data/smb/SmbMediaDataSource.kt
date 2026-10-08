package com.source.player.data.smb

import android.media.MediaDataSource

/**
 * [MediaDataSource] over an [SmbReader], so the platform `MediaMetadataRetriever` can read
 * tags straight from the share without downloading the whole file. The reader is opened
 * eagerly in the constructor — construct it on `Dispatchers.IO`, never on the main thread.
 */
class SmbMediaDataSource(smbClient: SmbClient, url: String) : MediaDataSource() {
        private val reader: SmbReader = smbClient.openReader(url)
        private var closed = false

        @Synchronized
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (closed) return -1
                if (size == 0) return 0
                if (position >= reader.length) return -1
                reader.seek(position)
                var total = 0
                while (total < size) {
                        val n = reader.read(buffer, offset + total, size - total)
                        if (n < 0) break
                        if (n == 0) break
                        total += n
                }
                return if (total == 0) -1 else total
        }

        override fun getSize(): Long = reader.length

        @Synchronized
        override fun close() {
                if (closed) return
                closed = true
                reader.close()
        }
}
