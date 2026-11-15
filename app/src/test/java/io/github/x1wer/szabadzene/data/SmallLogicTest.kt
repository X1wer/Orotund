package io.github.x1wer.szabadzene.data

import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.SkipMath
import io.github.x1wer.szabadzene.ui.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmallLogicTest {

    @Test
    fun skipSettingsDefaultIsTenSecondsBothWays() {
        val settings = SkipSettings()
        assertEquals(10, settings.backSeconds)
        assertEquals(10, settings.forwardSeconds)
        assertEquals(10_000L, settings.backMs)
    }

    @Test
    fun skipSettingsAcceptsOnlyAllowedValues() {
        assertEquals(listOf(5, 10, 15, 30, 60), SkipSettings.ALLOWED_SECONDS)
        SkipSettings.ALLOWED_SECONDS.forEach { assertEquals(it, SkipSettings.sanitize(it)) }
        assertEquals(10, SkipSettings.sanitize(null))
        assertEquals(10, SkipSettings.sanitize(7))
        assertEquals(10, SkipSettings.sanitize(-5))
        assertEquals(10, SkipSettings.sanitize(3600))
    }

    @Test
    fun skipMathClampsBothEnds() {
        assertEquals(SkipMath.Resolution(0, false), SkipMath.resolve(-500, 60_000))
        assertEquals(SkipMath.Resolution(30_000, false), SkipMath.resolve(30_000, 60_000))
        val end = SkipMath.resolve(60_000, 60_000)
        assertTrue(end.reachedEnd)
        assertEquals(60_000 - SkipMath.END_GUARD_MS, end.positionMs)
        // Nagyon rövid szám: a vége előtti tartalék nem lehet negatív.
        assertEquals(SkipMath.Resolution(0, true), SkipMath.resolve(500, 100))
    }

    @Test
    fun missingTitleFallsBackToFileName() {
        assertEquals("Dal", Track.titleOrFileName(null, "Dal.mp3"))
        assertEquals("Dal", Track.titleOrFileName("  ", "Dal.mp3"))
        assertEquals("Dal.2024", Track.titleOrFileName(null, "Dal.2024.flac"))
        assertEquals("Cím", Track.titleOrFileName("Cím", "fajl.mp3"))
        assertEquals("fajl", Track.titleOrFileName("<unknown>", "fajl"))
    }

    @Test
    fun unknownTagsBecomeNull() {
        assertNull(Track.cleanTag("<unknown>"))
        assertNull(Track.cleanTag(""))
        assertNull(Track.cleanTag(null))
        assertEquals("Kovács Éva", Track.cleanTag(" Kovács Éva "))
        assertFalse(Track.cleanTag("Unknown Pleasures") == null)
    }

    @Test
    fun durationFormatting() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:09", formatDuration(9_999))
        assertEquals("4:05", formatDuration(245_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
        assertEquals("0:00", formatDuration(-5))
    }
}
