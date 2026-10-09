package com.source.player.data.scanner

import com.source.player.data.scanner.FilenameTags.Parsed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameTagsTest {

    private fun parse(name: String, folder: String? = null) = FilenameTags.parse(name, folder)

    // ── Real files seen on the device ────────────────────────────────────

    @Test fun realCashFlowRiddim() =
        assertEquals(Parsed("CashFlow", "Lockdown", null), parse("CashFlow - Lockdown.mp3", "WYFL riddim"))

    @Test fun realLadyLykezVideoByIsKeptWhenBare() =
        // "Video by …" is only noise in brackets or after " | ": bare, it is the title.
        assertEquals(Parsed("Lady Lykez", "Video by Ikonic", null), parse("Lady Lykez - Video by Ikonic.mp3"))

    @Test fun realLadyLykezVideoByInBracketsIsDropped() =
        assertEquals(Parsed("Lady Lykez", "Dun Talk", null), parse("Lady Lykez - Dun Talk (Video by Ikonic).mp3"))

    @Test fun realAdmiralTWithoutSeparatorHasNoArtist() =
        assertEquals(
            Parsed(null, "Admiral T Médine La force du nombre", null),
            parse("Admiral T Médine La force du nombre.mp3"),
        )

    // ── Splitting ───────────────────────────────────────────────────────

    @Test fun simpleSplit() =
        assertEquals(Parsed("La Fouine", "CashFlow", null), parse("La Fouine - CashFlow.mp3"))

    @Test fun trackPrefixWithDot() =
        assertEquals(Parsed("Médine", "Grand Paris", 3), parse("03. Médine - Grand Paris.flac"))

    @Test fun trackPrefixWithDashThenArtist() =
        assertEquals(Parsed("Orelsan", "La Quête", 12), parse("12 - Orelsan - La Quête.mp3"))

    @Test fun trackPrefixUnderscoreAndBrackets() {
        assertEquals(Parsed("Nekfeu", "Ma dope", 7), parse("07_Nekfeu - Ma dope.mp3"))
        assertEquals(Parsed("Nekfeu", "Ma dope", 7), parse("(07) Nekfeu - Ma dope.mp3"))
        assertEquals(Parsed("Nekfeu", "Ma dope", 7), parse("[07] Nekfeu - Ma dope.mp3"))
    }

    @Test fun liveAsArtistIsAccepted() =
        assertEquals(Parsed("Live", "Paris 2019", null), parse("Live - Paris 2019.mp3"))

    @Test fun enDash() =
        assertEquals(Parsed("Nekfeu", "On verra", null), parse("Nekfeu – On verra.mp3"))

    @Test fun emDashAndSpacedUnderscore() {
        assertEquals(Parsed("Nekfeu", "On verra", null), parse("Nekfeu — On verra.mp3"))
        assertEquals(Parsed("Nekfeu", "On verra", null), parse("Nekfeu _ On verra.mp3"))
    }

    @Test fun splitsOnFirstSeparatorOnly() =
        assertEquals(Parsed("Kery James", "Lettre à la République - Live", null), parse("Kery James - Lettre à la République - Live.mp3"))

    @Test fun hyphenWithoutSpacesIsNotASeparator() =
        assertEquals(Parsed(null, "Jay-Z", null), parse("Jay-Z.mp3"))

    @Test fun noSeparatorMeansNoArtist() =
        assertEquals(Parsed(null, "Just a title", null), parse("Just a title.mp3"))

    @Test fun numericLeftSideIsNotAnArtist() =
        // 4 digits: not a track prefix (likely a year), and not an artist either.
        assertEquals(Parsed(null, "2024 - Something", null), parse("2024 - Something.mp3"))

    @Test fun trackPrefixWithoutArtist() =
        assertEquals(Parsed(null, "Something", 3), parse("03 - Something.mp3"))

    // ── Noise suffixes ──────────────────────────────────────────────────

    @Test fun clipOfficielInParentheses() =
        assertEquals(Parsed("Jul", "Tchikita", null), parse("Jul - Tchikita (Clip officiel).m4a"))

    @Test fun officialVideoInBrackets() =
        assertEquals(Parsed("Booba", "DKR", null), parse("Booba - DKR [Official Video].mp3"))

    @Test fun featIsKept() =
        assertEquals(Parsed("PNL", "Au DD (feat. X)", null), parse("PNL - Au DD (feat. X).mp3"))

    @Test fun remixLiveAcousticAreKept() {
        assertEquals("Song (Remix)", parse("A - Song (Remix).mp3")!!.title)
        assertEquals("Song (Live)", parse("A - Song (Live).mp3")!!.title)
        assertEquals("Song (Acoustic)", parse("A - Song (Acoustic).mp3")!!.title)
    }

    @Test fun pipeOfficialAudio() =
        assertEquals(Parsed("SCH", "Champs-Élysées", null), parse("SCH - Champs-Élysées | Official Audio.mp3"))

    @Test fun pipeWithRealContentIsKept() =
        assertEquals("Song | Part 2", parse("A - Song | Part 2.mp3")!!.title)

    @Test fun combinedAndStackedNoise() {
        assertEquals(Parsed("Ninho", "Lettre à une femme", null), parse("Ninho - Lettre à une femme (Clip Officiel) [HD].mp3"))
        assertEquals(Parsed("Ninho", "Jefe", null), parse("Ninho - Jefe (Official Music Video 4K).mp3"))
        assertEquals(Parsed("Ninho", "Jefe", null), parse("Ninho - Jefe (PAROLES).mp3"))
        assertEquals(Parsed("Ninho", "Jefe", null), parse("Ninho - Jefe (prod. Some Beatmaker).mp3"))
        assertEquals(Parsed("Ninho", "Jefe", null), parse("Ninho - Jefe [Lyric Video] | HQ.mp3"))
    }

    @Test fun noiseWordInsideARealGroupIsKept() =
        assertEquals("Song (Audio Remix)", parse("A - Song (Audio Remix).mp3")!!.title)

    // ── Guards ──────────────────────────────────────────────────────────

    @Test fun emptyNames() {
        assertNull(parse(""))
        assertNull(parse(".mp3"))
        assertNull(parse("   .flac"))
        assertNull(parse("(Official Video).mp3"))
    }

    @Test fun emptySideDoesNotSplit() =
        assertEquals(Parsed(null, "- Title", null), parse(" - Title.mp3"))

    @Test fun tooLongSideDoesNotSplit() {
        val long = "x".repeat(81)
        assertEquals(Parsed(null, "$long - Title", null), parse("$long - Title.mp3"))
    }

    @Test fun whitespaceIsCollapsed() =
        assertEquals(Parsed("La Fouine", "Ma Meilleure", null), parse("  La   Fouine  -   Ma  Meilleure .mp3"))

    @Test fun dotsInsideNameAreNotAnExtension() =
        assertEquals(Parsed("Mr. Oizo", "Flat Beat", null), parse("Mr. Oizo - Flat Beat.mp3"))

    // ── Priority rule ───────────────────────────────────────────────────

    @Test fun taggedArtistAlwaysWins() {
        assertNull(FilenameTags.resolve("Booba - DKR.mp3", null, "Booba", "DKR", 0))
        assertNull(FilenameTags.resolve("Other - Name.mp3", null, "Booba", "Other - Name", 4))
    }

    @Test fun unknownArtistInAnyCaseIsReplaced() {
        for (unknown in listOf("<unknown>", "<Unknown>", "<UNKNOWN>", "", "  ", null)) {
            val r = FilenameTags.resolve("La Fouine - CashFlow.mp3", null, unknown, "La Fouine - CashFlow", 0)
            assertEquals(FilenameTags.Resolved("La Fouine", "CashFlow", 0), r)
        }
    }

    @Test fun realTitleTagIsKept() =
        assertEquals(
            FilenameTags.Resolved("Booba", "DKR (Radio Edit)", 0),
            FilenameTags.resolve("Booba - DKR [Official Video].mp3", null, "<unknown>", "DKR (Radio Edit)", 0),
        )

    @Test fun parsedTrackOverridesOnlyWhenPresent() {
        assertEquals(5, FilenameTags.resolve("05. A - B.mp3", null, null, null, 0)!!.track)
        assertEquals(9, FilenameTags.resolve("A - B.mp3", null, null, null, 9)!!.track)
    }

    @Test fun noArtistFoundKeepsFallback() =
        assertEquals(
            FilenameTags.Resolved("<unknown>", "Just a title", 0),
            FilenameTags.resolve("Just a title (Official Video).mp3", null, "<unknown>", "Just a title (Official Video)", 0, "<unknown>"),
        )

    @Test fun derivedArtistIdIsStableNegativeAndCaseInsensitive() {
        val id = FilenameTags.derivedArtistId("La Fouine")
        assertTrue(id < 0)
        assertEquals(id, FilenameTags.derivedArtistId("la fouine"))
        assertNotEquals(id, FilenameTags.derivedArtistId("Booba"))
    }
}
