package io.github.x1wer.szabadzene.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    private var now = 1_000_000L
    private val timer = SleepTimer { now }

    @Test
    fun timedCountsDownAndPausesOnceAtExpiry() {
        timer.startTimed(10 * 60_000L)
        assertEquals(600_000L, timer.remainingMs())
        now += 599_000
        assertEquals(1_000L, timer.remainingMs())
        assertEquals(SleepTimerAction.NONE, timer.onTick())
        now += 1_000
        assertEquals(SleepTimerAction.PAUSE_NOW, timer.onTick())
        assertEquals(SleepTimerState.Off, timer.state)
        // Lejárat után nem ad újabb leállítást.
        now += 5_000
        assertEquals(SleepTimerAction.NONE, timer.onTick())
        assertNull(timer.remainingMs())
    }

    @Test
    fun lateTickStillPauses() {
        timer.startTimed(60_000L)
        now += 3_600_000
        assertEquals(0L, (timer.state as SleepTimerState.Timed).remainingMs(now))
        assertEquals(SleepTimerAction.PAUSE_NOW, timer.onTick())
    }

    @Test
    fun cancelStopsTimer() {
        timer.startTimed(60_000L)
        timer.cancel()
        assertEquals(SleepTimerState.Off, timer.state)
        now += 120_000
        assertEquals(SleepTimerAction.NONE, timer.onTick())
    }

    @Test
    fun restartReplacesPreviousTimer() {
        timer.startTimed(60_000L)
        now += 30_000
        timer.startTimed(120_000L)
        assertEquals(120_000L, timer.remainingMs())
        timer.startEndOfTrack()
        assertNull(timer.remainingMs())
        assertTrue(timer.pauseAtEndOfItems)
    }

    @Test
    fun endOfTrackModeEndsAfterTrackEnd() {
        timer.startEndOfTrack()
        assertTrue(timer.pauseAtEndOfItems)
        // Óra-ütés nem állítja le: csak a szám vége.
        now += 10 * 3_600_000L
        assertEquals(SleepTimerAction.NONE, timer.onTick())
        assertEquals(SleepTimerState.EndOfTrack, timer.state)
        timer.onTrackEnded()
        assertEquals(SleepTimerState.Off, timer.state)
        assertFalse(timer.pauseAtEndOfItems)
    }

    @Test
    fun trackEndIgnoredInTimedMode() {
        timer.startTimed(60_000L)
        timer.onTrackEnded()
        assertTrue(timer.state is SleepTimerState.Timed)
        assertFalse(timer.pauseAtEndOfItems)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationRejected() {
        timer.startTimed(0)
    }

    @Test
    fun formatting() {
        assertEquals("0:00", SleepTimer.formatRemaining(0))
        assertEquals("0:01", SleepTimer.formatRemaining(1))
        assertEquals("10:00", SleepTimer.formatRemaining(600_000))
        assertEquals("1:30:00", SleepTimer.formatRemaining(5_400_000))
        assertEquals("59:59", SleepTimer.formatRemaining(3_599_000))
    }
}
