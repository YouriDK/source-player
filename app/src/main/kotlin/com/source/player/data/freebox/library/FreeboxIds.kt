package com.source.player.data.freebox.library

/**
 * Stable primary keys for Freebox-indexed rows. Every id is NEGATIVE so it can never
 * collide with a MediaStore `_ID` (always positive), and is derived from a namespaced,
 * lowercased key so the same file / album / artist / genre keeps its id across scans.
 */
object FreeboxIds {
    /** [url] is the song's `fbx://` URL (case kept: Freebox paths are case-sensitive). */
    fun song(url: String): Long = negativeHash("song|$url")

    fun album(albumArtist: String, album: String): Long =
        negativeHash("album|${albumArtist.lowercase()}|${album.lowercase()}")

    fun artist(name: String): Long = negativeHash("artist|${name.lowercase()}")

    fun genre(name: String): Long = negativeHash("genre|${name.lowercase()}")

    /** 64-bit FNV-1a over the UTF-8 bytes of [key], forced into (Long.MIN_VALUE, 0). */
    private fun negativeHash(key: String): Long {
        var hash = FNV_OFFSET
        for (b in key.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (b.toLong() and 0xFF)
            hash *= FNV_PRIME
        }
        val positive = hash and Long.MAX_VALUE
        return if (positive == 0L) -1L else -positive
    }

    private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val FNV_PRIME = 0x100000001b3L
}
