package com.source.player.data.tag

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

/**
 * Writes ID3 / Vorbis / MP4 tags directly into audio files so that tag edits are permanent,
 * visible to other apps, and survive a library rescan.
 *
 * Scoped-storage model (see [com.source.player.ui.viewmodel.TagEditorViewModel]):
 *  - API ≤ 29 : `WRITE_EXTERNAL_STORAGE` (+ requestLegacyExternalStorage on 29) lets us open the
 *               MediaStore file descriptor for writing directly.
 *  - API 30+  : the caller must first obtain per-file consent via
 *               [MediaStore.createWriteRequest]; only then can we open the descriptor "rwt".
 *
 * jaudiotagger operates on a real [File], not a content descriptor, so each write uses a
 * copy → retag → copy-back round-trip through the app cache. This works on every API level and
 * avoids assuming a usable filesystem path (which no longer exists under scoped storage).
 */
@Singleton
class AudioTagWriter
@Inject
constructor(
        @ApplicationContext private val context: Context,
) {
    /** Tag values to apply to one file. A null field means "leave this tag unchanged". */
    data class TagEdits(
            val title: String? = null,
            val artist: String? = null,
            val album: String? = null,
            val year: Int? = null,
            val trackNumber: Int? = null,
            val genre: String? = null,
    ) {
        val isEmpty: Boolean
            get() =
                    title == null &&
                            artist == null &&
                            album == null &&
                            year == null &&
                            trackNumber == null &&
                            genre == null
    }

    /** Builds the MediaStore content URI for a song's stable MediaStore id. */
    fun contentUriFor(songId: Long): Uri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

    /**
     * Writes [edits] into the file behind [songId]. [originalPath] is used only to recover the file
     * extension so jaudiotagger can detect the format. Must be called off the main thread; runs its
     * I/O on [Dispatchers.IO]. Returns a [Result] carrying the failure for the caller to surface.
     *
     * On API 30+ the caller must already hold write consent for this URI, otherwise opening the
     * descriptor throws [SecurityException].
     */
    suspend fun writeTags(songId: Long, originalPath: String, edits: TagEdits): Result<Unit> =
            withContext(Dispatchers.IO) {
                if (edits.isEmpty) return@withContext Result.success(Unit)

                val uri = contentUriFor(songId)
                val ext = originalPath.substringAfterLast('.', "").ifEmpty { "tmp" }
                val tmp = File(context.cacheDir, "tagedit_$songId.$ext")
                val resolver = context.contentResolver

                try {
                    // 1. Copy the original file out to a private cache copy we fully control.
                    resolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                            ?: return@withContext Result.failure(
                                    IllegalStateException("Could not open \"$originalPath\"")
                            )

                    // 2. Retag the copy in place.
                    val audioFile = AudioFileIO.read(tmp)
                    val tag = audioFile.tagOrCreateAndSetDefault
                    edits.title?.let { tag.setField(FieldKey.TITLE, it) }
                    edits.artist?.let { tag.setField(FieldKey.ARTIST, it) }
                    edits.album?.let { tag.setField(FieldKey.ALBUM, it) }
                    edits.year?.let { tag.setField(FieldKey.YEAR, it.toString()) }
                    edits.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
                    edits.genre?.let { tag.setField(FieldKey.GENRE, it) }
                    audioFile.commit()

                    // 3. Stream the retagged copy back over the original. "rwt" truncates first so a
                    //    size change (tags grew/shrank) doesn't leave trailing bytes.
                    resolver.openFileDescriptor(uri, "rwt")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { out ->
                            tmp.inputStream().use { it.copyTo(out) }
                        }
                    }
                            ?: return@withContext Result.failure(
                                    IllegalStateException("Could not write \"$originalPath\"")
                            )

                    // 4. Re-index the file. Writing through the descriptor changes the bytes
                    //    on disk but leaves MediaStore's cached title/artist/album columns on
                    //    their pre-edit values — and the library scanner reads those columns,
                    //    so without this the next scan would quietly undo the edit.
                    MediaScannerConnection.scanFile(context, arrayOf(originalPath), null, null)

                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    tmp.delete()
                }
            }
}
