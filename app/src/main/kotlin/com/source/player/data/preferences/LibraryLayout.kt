package com.source.player.data.preferences

/** How a Library tab (Songs / Albums / Artists) lays out its items. */
enum class LibraryLayout {
  /** Compact rows. */
  LIST,

  /** Grid of cover tiles. */
  GALLERY,

  /** Tall rows with a large cover and extra metadata. */
  DETAILS;

  companion object {
    /** Unknown / missing stored value → [default]. */
    fun fromKey(key: String?, default: LibraryLayout): LibraryLayout =
            entries.firstOrNull { it.name == key } ?: default
  }
}

/**
 * Per-tab layout choices plus the column count shared by every gallery. Defaults reproduce the
 * original fixed rendering: Songs list, Albums grid, Artists list, 2 columns.
 */
data class LibraryLayoutSettings(
        val songs: LibraryLayout = LibraryLayout.LIST,
        val albums: LibraryLayout = LibraryLayout.GALLERY,
        val artists: LibraryLayout = LibraryLayout.LIST,
        val galleryColumns: Int = 2,
)
