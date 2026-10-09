package com.source.player.data.preferences

import com.source.player.data.db.entity.SOURCE_FREEBOX
import com.source.player.data.db.entity.SOURCE_LOCAL

/**
 * Which library the whole app shows. The two sources never mix: Library, Home, Search,
 * Playlists and Folders all follow the active mode.
 */
enum class LibraryMode(
        /** Value of the `source` column of the rows this mode shows. */
        val source: String,
) {
  /** On-device files indexed from MediaStore. */
  LOCAL(SOURCE_LOCAL),

  /** The Freebox music folder (indexed rows + live browsing in Folders). */
  STREAM(SOURCE_FREEBOX);

  companion object {
    /** Unknown / missing stored value → LOCAL. */
    fun fromKey(key: String?): LibraryMode = entries.firstOrNull { it.name == key } ?: LOCAL
  }
}
