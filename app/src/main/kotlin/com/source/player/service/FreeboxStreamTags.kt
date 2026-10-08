package com.source.player.service

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.source.player.data.freebox.FreeboxPaths

/**
 * Copies the tags ExoPlayer extracted from a Freebox stream (ID3 / Vorbis / FLAC / MP4:
 * title, artist, album, embedded picture) into the current [androidx.media3.common.MediaItem].
 *
 * Freebox items are built from a file name only, and ExoPlayer gives MediaItem metadata
 * priority over stream metadata, so without this the notification, the controller and
 * the queue would keep showing the file name. The item is swapped with
 * [Player.replaceMediaItem]: same uri, so ExoPlayer updates it in place without
 * re-buffering. Attach to the local ExoPlayer only.
 */
class FreeboxStreamTags(private val player: Player) : Player.Listener {

    override fun onTracksChanged(tracks: Tracks) {
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri?.toString() ?: return
        if (!FreeboxPaths.isFreeboxUrl(uri)) return

        val stream = MediaMetadata.Builder()
        var found = false
        for (group in tracks.groups) {
            if (!group.isSelected) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSelected(i)) continue
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) metadata.get(j).populateMediaMetadata(stream)
                found = true
            }
        }
        if (!found) return

        val merged = mergeStreamTags(item.mediaMetadata, stream.build())
        if (merged == item.mediaMetadata) return
        player.replaceMediaItem(
                player.currentMediaItemIndex,
                item.buildUpon().setMediaMetadata(merged).build(),
        )
    }

    companion object {
        /** [base] with title/artist/album/artwork taken from [tags] when present. */
        fun mergeStreamTags(base: MediaMetadata, tags: MediaMetadata): MediaMetadata =
                base.buildUpon()
                        .apply {
                            tags.title?.let { setTitle(it) }
                            tags.artist?.let { setArtist(it) }
                            tags.albumTitle?.let { setAlbumTitle(it) }
                            tags.artworkData?.let { setArtworkData(it, tags.artworkDataType) }
                            tags.artworkUri?.let { setArtworkUri(it) }
                        }
                        .build()
    }
}
