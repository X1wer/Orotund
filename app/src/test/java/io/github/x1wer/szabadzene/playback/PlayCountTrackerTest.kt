package io.github.x1wer.szabadzene.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** Mikor számít egy szám meghallgatottnak. */
class PlayCountTrackerTest {

    private var now = 0L
    private val counted = mutableListOf<String>()
    private val tracker = PlayCountTracker(clock = { now }) { counted += it }

    private fun play(ms: Long) {
        now += ms
        tracker.check()
    }

    @Test
    fun thresholdIsHalfTheSongButAtMostFourMinutes() {
        assertEquals(90_000L, PlayCountTracker.thresholdMs(180_000))
        assertEquals(240_000L, PlayCountTracker.thresholdMs(20 * 60_000L))
        assertEquals(30_000L, PlayCountTracker.thresholdMs(0))
        assertEquals(1_500L, PlayCountTracker.thresholdMs(3_000))
    }

    @Test
    fun countsOnceAfterHalfOfTheSong() {
        tracker.onItemStarted("1", durationMs = 200_000, isPlaying = true)
        play(99_000)
        assertEquals(emptyList<String>(), counted)
        play(1_000)
        assertEquals(listOf("1"), counted)
        play(100_000)
        assertEquals("Lejátszásonként csak egyszer számít", listOf("1"), counted)
    }

    @Test
    fun pausedTimeDoesNotCount() {
        tracker.onItemStarted("1", durationMs = 100_000, isPlaying = true)
        play(30_000)
        tracker.onPlayingChanged(false)
        play(60_000)
        assertEquals(emptyList<String>(), counted)
        tracker.onPlayingChanged(true)
        play(20_000)
        assertEquals(listOf("1"), counted)
    }

    @Test
    fun skippingAheadDoesNotCountAsListening() {
        // A pozíció ugrása (előretekerés) nem számít: csak az eltelt lejátszási idő.
        tracker.onItemStarted("1", durationMs = 100_000, isPlaying = true)
        play(10_000)
        tracker.onItemStarted("2", durationMs = 100_000, isPlaying = true)
        play(10_000)
        assertEquals(emptyList<String>(), counted)
    }

    @Test
    fun durationKnownLaterUsesRealThreshold() {
        tracker.onItemStarted("1", durationMs = 0, isPlaying = true)
        play(20_000)
        tracker.onDurationKnown(40_000)
        assertEquals(listOf("1"), counted)
    }

    @Test
    fun repeatAndRestartCountAgain() {
        tracker.onItemStarted("1", durationMs = 10_000, isPlaying = true)
        play(6_000)
        // Ismétlés: számváltásként érkezik.
        tracker.onItemStarted("1", durationMs = 10_000, isPlaying = true)
        play(6_000)
        // Vissza az elejére egy beszámított lejátszás után: új lejátszás.
        tracker.onSeekToStart(isPlaying = true)
        play(6_000)
        assertEquals(listOf("1", "1", "1"), counted)
    }

    @Test
    fun restartBeforeCountingKeepsAccumulating() {
        tracker.onItemStarted("1", durationMs = 10_000, isPlaying = true)
        play(3_000)
        tracker.onSeekToStart(isPlaying = true)
        play(2_000)
        assertEquals(listOf("1"), counted)
    }

    @Test
    fun startingPausedDoesNotCount() {
        tracker.onItemStarted("1", durationMs = 10_000, isPlaying = false)
        play(60_000)
        assertEquals(emptyList<String>(), counted)
        tracker.onItemStarted(null, durationMs = 0, isPlaying = true)
        play(60_000)
        assertEquals(emptyList<String>(), counted)
    }
}
