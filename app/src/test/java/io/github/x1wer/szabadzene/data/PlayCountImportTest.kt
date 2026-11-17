package io.github.x1wer.szabadzene.data

import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.stats.ImportedCount
import io.github.x1wer.szabadzene.data.stats.PlayCountCsv
import io.github.x1wer.szabadzene.data.stats.PlayCountMatcher
import io.github.x1wer.szabadzene.data.stats.PlayCountMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lejátszásszámok importálása: CSV olvasás, párosítás a zenetárral, csak növelő összevonás. */
class PlayCountImportTest {

    private fun track(id: Long, title: String, artist: String?, seconds: Int, displayName: String = "$title.mp3") = Track(
        id = id, title = title, artist = artist, album = null, albumId = 0, durationMs = seconds * 1000L,
        displayName = displayName, path = null, relativePath = "Music/", sizeBytes = 1, mimeType = "audio/mpeg",
        dateAddedSeconds = 0, genre = null, trackNumber = 0, year = 0,
    )

    // --- CSV ---

    @Test
    fun parsesSemicolonCsvWithHeaderAndQuotes() {
        val text = "\uFEFFcount;duration;title;artist\r\n" +
            "460;02:59;Plastic Bertrand  - Ça plane pour moi (Official Audio);\r\n" +
            "372;02:05;\"Naruto; Jinchuuriki \"\"Opening\"\"\";Valaki\r\n" +
            "0;01:00;Nulla;\r\n" +
            "x;01:00;Hibás;\r\n"
        val rows = PlayCountCsv.parse(text)
        assertEquals(2, rows.size)
        assertEquals(ImportedCount(460, 179, "Plastic Bertrand  - Ça plane pour moi (Official Audio)", null), rows[0])
        assertEquals(ImportedCount(372, 125, "Naruto; Jinchuuriki \"Opening\"", "Valaki"), rows[1])
    }

    @Test
    fun readsNamedColumnsInAnyOrderAndOtherDelimiters() {
        val rows = PlayCountCsv.parse("Előadó,Cím,Lejátszás\nKovács Éva,Árvíztűrő,12\n")
        assertEquals(listOf(ImportedCount(12, null, "Árvíztűrő", "Kovács Éva")), rows)
    }

    @Test
    fun headerlessFileUsesTheDefaultColumnOrder() {
        val rows = PlayCountCsv.parse("5;1:02:03;Hosszú;Valaki\n")
        assertEquals(ImportedCount(5, 3723, "Hosszú", "Valaki"), rows.single())
        assertNull(PlayCountCsv.parseDuration("12"))
        assertNull(PlayCountCsv.parseDuration("a:b"))
    }

    // --- Párosítás ---

    @Test
    fun matchesByTitleIgnoringCaseAccentsAndSpaces() {
        val library = listOf(track(1, "Árvíztűrő  tükörfúrógép", "Kovács Éva", 200))
        val match = PlayCountMatcher(library).match(listOf(ImportedCount(7, 201, "arvizturo tukorfurogep", null)))
        assertEquals(mapOf(library[0] to 7), match.matched)
    }

    @Test
    fun matchesByFileNameWhenTheTitleDiffers() {
        val library = listOf(track(1, "Cím a címkéből", null, 100, displayName = "Fájl neve.mp3"))
        val match = PlayCountMatcher(library).match(listOf(ImportedCount(3, null, "Fájl neve", null)))
        assertEquals(1, match.matched.size)
    }

    @Test
    fun singleCandidateWithVeryDifferentLengthIsAnotherVersion() {
        val library = listOf(track(1, "Dal", null, 200))
        val match = PlayCountMatcher(library).match(listOf(ImportedCount(3, 260, "Dal", null)))
        assertEquals(0, match.matched.size)
        assertEquals(1, match.missing.size)
    }

    @Test
    fun duplicateTitlesAreResolvedByArtistThenLengthOrReportedAmbiguous() {
        val a = track(1, "Intro", "A", 100)
        val b = track(2, "Intro", "B", 100)
        val c = track(3, "Intro", "B", 160)
        val matcher = PlayCountMatcher(listOf(a, b, c))
        val result = matcher.match(
            listOf(
                ImportedCount(5, null, "Intro", "A"),
                ImportedCount(6, 161, "Intro", "B"),
                ImportedCount(7, null, "Intro", "B"),
                ImportedCount(8, null, "Intro", null),
                ImportedCount(9, null, "Nincs ilyen", null),
            ),
        )
        assertEquals(mapOf(a to 5, c to 6), result.matched)
        assertEquals(2, result.ambiguous.size)
        assertEquals(1, result.missing.size)
    }

    @Test
    fun repeatedRowsForOneTrackKeepTheLargestCount() {
        val library = listOf(track(1, "Dal", null, 100))
        val match = PlayCountMatcher(library).match(listOf(ImportedCount(3, null, "Dal", null), ImportedCount(9, null, "Dal", null)))
        assertEquals(9, match.matched.getValue(library[0]))
    }

    // --- Összevonás ---

    @Test
    fun mergeOnlyRaisesCounts() {
        val a = track(1, "A", null, 100)
        val b = track(2, "B", null, 100)
        val c = track(3, "C", null, 100)
        val merged = PlayCountMerge.apply(mapOf(a to 10, b to 4, c to 5), existing = mapOf(1L to 3, 2L to 9, 3L to 5))
        assertEquals(mapOf(a to 10), merged)
    }

    @Test
    fun writtenCsvReadsBackTheSameRows() {
        val rows = listOf(
            ImportedCount(3, 125, "Egyszerű", null),
            ImportedCount(460, 179, "Plastic Bertrand  - Ça plane pour moi", "Előadó; vesszővel"),
            ImportedCount(12, 3723, "Idéző \"jel\" van benne", "A"),
        )
        val text = PlayCountCsv.write(rows)
        assertEquals("count;duration;title;artist", text.lineSequence().first())
        assertEquals(rows.sortedByDescending { it.count }, PlayCountCsv.parse(text))
        assertEquals("1:02:03", PlayCountCsv.formatDuration(3723))
        assertEquals("02:05", PlayCountCsv.formatDuration(125))
    }
}
