package com.source.player.data.scanner

/**
 * Guesses "Artist - Title" from a file name, for files whose tags carry no artist
 * (YouTube downloads, rips). Pure Kotlin, no Android dependency.
 *
 * Both scanners go through [resolve], which holds the priority rule: real tags always
 * win, the file name is only consulted when the artist is missing, blank or
 * `<unknown>`.
 *
 * Parsing rules ([parse]):
 * 1. The extension is dropped (last `.xxx`, 1-5 letters/digits).
 * 2. A leading track number is dropped and returned: `03. `, `03 - `, `03_`, `(03)`,
 *    `[03]` (1-3 digits; a 4-digit number is more likely a year and is left alone).
 * 3. Noise groups are dropped: a `(…)` / `[…]` group, or the tail after ` | `, whose
 *    whole content is made of [NOISE] phrases (case-insensitive), or starts with one
 *    of [NOISE_PREFIXES] (`Video by …`, `prod. …`). `(feat. X)`, `(Remix)`, `(Live)`,
 *    `(Acoustic)`… are not noise and stay in the title. A bare `Video by X` that is
 *    not in brackets nor after `|` is kept: without the brackets nothing tells it
 *    apart from a real title.
 * 4. The name is split on the first ` - `, ` – `, ` — ` or ` _ ` (spaces required):
 *    left = artist, right = title. No split when either side is empty, longer than
 *    [MAX_PART_LENGTH], or when the left side is only digits (a number, not an artist).
 * 5. Whitespace is trimmed and runs of spaces collapsed.
 */
object FilenameTags {

    data class Parsed(val artist: String?, val title: String, val track: Int?)

    /** Result of [resolve]: what the song row should hold. */
    data class Resolved(val artist: String, val title: String, val track: Int)

    /** Same sentinel the scanners use for a missing artist. */
    const val UNKNOWN = "<Unknown>"

    const val MAX_PART_LENGTH = 80

    /**
     * Phrases that make up a YouTube-style noise suffix. Matched case-insensitively, as
     * whole words; a group is noise when it is entirely made of these (so
     * "Official Video HD" or "Clip officiel - 4K" are noise too). Extend freely.
     */
    val NOISE: Set<String> = setOf(
        "official video",
        "official music video",
        "official audio",
        "official lyric video",
        "official visualizer",
        "clip officiel",
        "clip",
        "audio",
        "audio officiel",
        "lyrics",
        "lyric video",
        "lyric",
        "paroles",
        "hd",
        "hq",
        "4k",
        "visualizer",
        "visualiser",
    )

    /** A group starting with one of these is noise whatever follows ("Video by Ikonic"). */
    val NOISE_PREFIXES: Set<String> = setOf(
        "video by ",
        "prod. ",
        "prod by ",
        "prod.by ",
        "produced by ",
    )

    private val SEPARATORS = listOf(" - ", " – ", " — ", " _ ")
    private val EXTENSION = Regex("""\.[A-Za-z0-9]{1,5}$""")
    private val TRACK_PREFIX = Regex("""^(?:\((\d{1,3})\)|\[(\d{1,3})\]|(\d{1,3})(?:\s*\.\s*|\s+[-–—]\s+|\s*_+\s*))""")
    private val TRAILING_GROUP = Regex("""\s*[(\[]([^()\[\]]*)[)\]]\s*$""")
    private val ANY_GROUP = Regex("""[(\[]([^()\[\]]*)[)\]]""")
    private val SPACES = Regex("""\s+""")
    private val NOISE_JOINERS = Regex("""^[\s\-–—/&,+|:]+""")

    /** Parses [fileName] (with or without extension). Null when nothing usable is left. */
    fun parse(fileName: String, @Suppress("UNUSED_PARAMETER") parentFolder: String?): Parsed? {
        // parentFolder is accepted for a future "Artist - Album" folder rule; the album is
        // never derived from the file name today, so it is not read yet.
        var name = collapse(stripExtension(fileName))
        if (name.isEmpty()) return null

        // Track number prefix
        var track: Int? = null
        TRACK_PREFIX.find(name)?.let { m ->
            val rest = name.substring(m.range.last + 1).trim()
            if (rest.isNotEmpty()) {
                track = (m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }).toIntOrNull()
                name = rest
            }
        }

        name = collapse(stripNoise(name))
        if (name.isEmpty()) return null

        val split = split(name)
        return if (split == null) {
            Parsed(artist = null, title = name, track = track)
        } else {
            Parsed(artist = split.first, title = split.second, track = track)
        }
    }

    /** True when [artist] does not name anyone: null, blank or `<unknown>` in any case. */
    fun isUnknownArtist(artist: String?): Boolean =
        artist.isNullOrBlank() || artist.trim().equals(UNKNOWN, ignoreCase = true)

    /**
     * Applies the priority rule shared by both scanners. Returns null when the tags must
     * be kept as they are: the artist is known, or the file name yields nothing.
     *
     * - [taggedTitle] is kept when it is non-blank and differs from the file name without
     *   extension (a real title tag); otherwise the parsed title is used.
     * - The artist is the parsed one, else [fallbackArtist] (what the scanner would have
     *   shown anyway).
     * - The track is the parsed one, else [track].
     * - The album is never touched.
     */
    fun resolve(
        fileName: String,
        parentFolder: String?,
        taggedArtist: String?,
        taggedTitle: String?,
        track: Int,
        fallbackArtist: String = UNKNOWN,
    ): Resolved? {
        if (!isUnknownArtist(taggedArtist)) return null
        val parsed = parse(fileName, parentFolder) ?: return null
        val baseName = stripExtension(fileName).trim()
        val realTitle = taggedTitle?.trim()?.takeIf { it.isNotEmpty() && it != baseName }
        return Resolved(
            artist = parsed.artist ?: fallbackArtist,
            title = realTitle ?: parsed.title,
            track = parsed.track ?: track,
        )
    }

    /**
     * Stable id for an artist name deduced on a local (MediaStore) track. Negative like
     * Freebox ids, but in its own namespace ("local-artist|") so it never collides with a
     * Freebox artist of the same name (the artists table is shared) nor with a MediaStore
     * id (always positive). Case-insensitive, so the same name keeps its id across scans.
     */
    fun derivedArtistId(name: String): Long {
        var hash = FNV_OFFSET
        for (b in "local-artist|${name.trim().lowercase()}".toByteArray(Charsets.UTF_8)) {
            hash = hash xor (b.toLong() and 0xFF)
            hash *= FNV_PRIME
        }
        val positive = hash and Long.MAX_VALUE
        return if (positive == 0L) -1L else -positive
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun stripExtension(fileName: String): String {
        val trimmed = fileName.trim()
        return EXTENSION.find(trimmed)?.let { trimmed.substring(0, it.range.first) } ?: trimmed
    }

    private fun collapse(s: String): String = s.replace(SPACES, " ").trim()

    /** Drops noise groups in brackets (anywhere) and a noise tail after " | ". */
    private fun stripNoise(input: String): String {
        var s = input
        // " | Official Audio" tails, possibly several ("Title | Lyrics | HD")
        while (true) {
            val bar = s.lastIndexOf('|')
            if (bar < 0 || !isNoise(s.substring(bar + 1))) break
            s = s.substring(0, bar).trimEnd()
        }
        // Bracketed groups anywhere in the name
        s = ANY_GROUP.replace(s) { m -> if (isNoise(m.groupValues[1])) " " else m.value }
        // Leftover trailing empty group or dangling separator ("Title -", "Title |")
        while (true) {
            val m = TRAILING_GROUP.find(s)
            if (m == null || m.groupValues[1].isNotBlank()) break
            s = s.substring(0, m.range.first)
        }
        return s.trim().trimEnd('-', '–', '—', '_', '|', ' ').trim()
    }

    /** True when [group] is entirely made of [NOISE] phrases or starts with a [NOISE_PREFIXES] one. */
    internal fun isNoise(group: String): Boolean {
        val text = collapse(group).lowercase()
        if (text.isEmpty()) return false
        if (NOISE_PREFIXES.any { text.startsWith(it) && text.length > it.length }) return true
        val phrases = NOISE.sortedByDescending { it.length }
        var rest = text
        while (rest.isNotEmpty()) {
            val hit = phrases.firstOrNull { p ->
                rest.startsWith(p) && (rest.length == p.length || !rest[p.length].isLetterOrDigit())
            } ?: return false
            rest = rest.substring(hit.length).replace(NOISE_JOINERS, "")
        }
        return true
    }

    private fun split(name: String): Pair<String, String>? {
        val (index, sep) = SEPARATORS
            .map { name.indexOf(it) to it }
            .filter { it.first >= 0 }
            .minByOrNull { it.first }
            ?: return null
        val artist = collapse(name.substring(0, index))
        val title = collapse(name.substring(index + sep.length))
        if (artist.isEmpty() || title.isEmpty()) return null
        if (artist.length > MAX_PART_LENGTH || title.length > MAX_PART_LENGTH) return null
        if (artist.all { it.isDigit() }) return null
        return artist to title
    }

    private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val FNV_PRIME = 0x100000001b3L
}
