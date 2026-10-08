package com.source.player.data.smb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk album art for SMB albums: `filesDir/smb_art/<albumId>.jpg`. Bytes are stored
 * as fetched (JPEG, PNG or WebP) — Coil sniffs the real format, the extension is cosmetic.
 */
@Singleton
class SmbArtCache @Inject constructor(@ApplicationContext private val context: Context) {

        private val dir: File
                get() = File(context.filesDir, DIR_NAME)

        fun fileFor(albumId: Long): File = File(dir, "$albumId.jpg")

        fun has(albumId: Long): Boolean = fileFor(albumId).let { it.isFile && it.length() > 0 }

        /** `file://` URI of the cached art, or null when none is cached. */
        fun uriFor(albumId: Long): String? =
                fileFor(albumId).takeIf { it.isFile && it.length() > 0 }?.let { "file://${it.absolutePath}" }

        /** Writes atomically (temp file + rename) so readers never see a half-written image. */
        fun write(albumId: Long, bytes: ByteArray): Boolean {
                if (bytes.isEmpty()) return false
                val target = fileFor(albumId)
                return runCatching {
                                dir.mkdirs()
                                val tmp = File(dir, "$albumId.${System.nanoTime()}.tmp")
                                tmp.writeBytes(bytes)
                                if (!tmp.renameTo(target)) {
                                        tmp.delete()
                                        false
                                } else {
                                        true
                                }
                        }
                        .getOrDefault(false)
        }

        fun clear() {
                dir.deleteRecursively()
        }

        private companion object {
                const val DIR_NAME = "smb_art"
        }
}
