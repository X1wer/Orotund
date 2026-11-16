package io.github.x1wer.szabadzene.data

import io.github.x1wer.szabadzene.data.edit.TagValues
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.lyrics.LrcParser
import io.github.x1wer.szabadzene.data.stats.PlayStats
import io.github.x1wer.szabadzene.data.stats.SmartLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dalszöveg-feldolgozás és az automatikus listák szabályai. */
class LyricsAndStatsTest {

    // --- LRC ---

    @Test
    fun parsesTimedLyricsSortedWithOffsetAndHeaders() {
        val text = """
            [ar:Kovács Éva]
            [ti:Árvíztűrő]
            [offset:+500]
            [00:12.50]Első sor
            [00:05.00][01:00.00]Refrén
            [00:20]Harmadik <00:21.00>szó
        """.trimIndent()
        val lyrics = LrcParser.parse(text)
        assertTrue(lyrics.synced)
        assertEquals(listOf("Refrén", "Első sor", "Harmadik szó", "Refrén"), lyrics.lines.map { it.text })
        assertEquals(listOf(4_500L, 12_000L, 19_500L, 59_500L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun currentLineFollowsPosition() {
        val lyrics = LrcParser.parse("[00:01.00]a\n[00:03.00]b\n[00:05.5]c")
        assertEquals(-1, lyrics.indexAt(500))
        assertEquals(0, lyrics.indexAt(1_000))
        assertEquals(1, lyrics.indexAt(4_999))
        assertEquals(2, lyrics.indexAt(5_500))
        assertEquals(2, lyrics.indexAt(600_000))
    }

    @Test
    fun plainTextStaysPlain() {
        val lyrics = LrcParser.parse("\n\nElső versszak\nmásodik sor\n\nRefrén\n\n")
        assertFalse(lyrics.synced)
        assertEquals(listOf("Első versszak", "második sor", "", "Refrén"), lyrics.lines.map { it.text })
        assertEquals(-1, lyrics.indexAt(10_000))
        assertTrue(LrcParser.parse("").isEmpty)
        assertTrue(LrcParser.parse(null).isEmpty)
        assertTrue(LrcParser.parse("   \n  ").isEmpty)
    }

    @Test
    fun threeDigitAndOneDigitFractions() {
        val lyrics = LrcParser.parse("[01:02.345]x\n[00:00.5]y")
        assertEquals(listOf(500L, 62_345L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun lyricsNormalizationUnifiesLineEndings() {
        assertEquals("a\nb\n\nc", TagValues.normalizeLyrics("  a  \r\nb\r\r\nc  \n\n"))
    }

    // --- Automatikus listák ---

    private fun track(id: Long, title: String) = Track(
        id = id, title = title, artist = null, album = null, albumId = 0, durationMs = 1000, displayName = "$title.mp3",
        path = null, relativePath = null, sizeBytes = 0, mimeType = "audio/mpeg", dateAddedSeconds = 0, genre = null,
        trackNumber = 0, year = 0,
    )

    private val tracks = listOf(track(1, "A"), track(2, "B"), track(3, "C"), track(4, "D"))

    @Test
    fun mostPlayedOrdersByCountThenRecency() {
        val stats = mapOf(
            1L to PlayStats(3, lastPlayedAt = 100),
            2L to PlayStats(5, lastPlayedAt = 50),
            3L to PlayStats(3, lastPlayedAt = 200),
            4L to PlayStats(0, lastPlayedAt = 300),
            // Már nem létező szám: nem jelenik meg.
            99L to PlayStats(50, lastPlayedAt = 10),
        )
        val result = SmartLists.mostPlayed(tracks, stats)
        assertEquals(listOf(2L, 3L, 1L), result.map { it.track.id })
        assertEquals(listOf(5, 3, 3), result.map { it.playCount })
        assertEquals(listOf(2L), SmartLists.mostPlayed(tracks, stats, limit = 1).map { it.track.id })
    }

    @Test
    fun mostPlayedHasNoLimit() {
        val many = (1L..250L).map { track(it, "T$it") }
        val stats = many.associate { it.id to PlayStats(it.id.toInt(), lastPlayedAt = null) }
        assertEquals(250, SmartLists.mostPlayed(many, stats).size)
    }

    @Test
    fun recentlyPlayedOrdersByLastPlayAndSkipsManualOnly() {
        val stats = mapOf(
            1L to PlayStats(3, lastPlayedAt = 100),
            2L to PlayStats(7, lastPlayedAt = null),
            3L to PlayStats(1, lastPlayedAt = 300),
            4L to PlayStats(0, lastPlayedAt = 200),
        )
        assertEquals(listOf(3L, 4L, 1L), SmartLists.recentlyPlayed(tracks, stats).map { it.track.id })
    }
}
