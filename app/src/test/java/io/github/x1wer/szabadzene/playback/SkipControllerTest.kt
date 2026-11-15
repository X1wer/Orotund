package io.github.x1wer.szabadzene.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Az ugrási logika határhelyzetei és a gyors egymás utáni koppintások.
 *
 * A [FakePlayer] kétféleképpen viselkedhet: azonnal jelenti az új pozíciót (mint az ExoPlayer),
 * vagy késve (mint egy lassan frissülő vezérlő) – ilyenkor a tesztek kézzel igazolják a tekeréseket.
 */
class SkipControllerTest {

    private var now = 1_000_000L
    private val controller = SkipController(clock = { now })

    private class FakePlayer(
        var mediaId: String? = "1",
        var reportedPositionMs: Long = 0L,
        var duration: Long = 240_000L,
        var seekable: Boolean = true,
        var playing: Boolean = true,
        /** Ha igaz, a seekTo nem frissíti azonnal a jelentett pozíciót. */
        val delayed: Boolean = false,
    ) : SkipTarget {
        val seeks = mutableListOf<Long>()
        var pauseCalls = 0
        var onSeek: (Long) -> Unit = {}

        override val currentMediaId get() = mediaId
        override val currentPositionMs get() = reportedPositionMs
        override val durationMs get() = duration
        override val isSeekable get() = seekable
        override val isPlaying get() = playing

        override fun seekTo(positionMs: Long) {
            seeks += positionMs
            if (!delayed) {
                reportedPositionMs = positionMs
                onSeek(positionMs)
            }
        }

        override fun pause() {
            playing = false
            pauseCalls++
        }
    }

    /** Azonnal jelentő lejátszó, amely a tekerést a valódi szolgáltatáshoz hasonlóan visszaigazolja. */
    private fun immediatePlayer(position: Long, playing: Boolean = true) =
        FakePlayer(reportedPositionMs = position, playing = playing).also { p ->
            p.onSeek = { pos -> controller.onSeekReported(p.mediaId, pos) }
        }

    // --- Alapesetek ---

    @Test
    fun forwardAddsStepAndKeepsPlaying() {
        val player = immediatePlayer(30_000)
        val result = controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertEquals(listOf(40_000L), player.seeks)
        assertEquals(SkipResult(40_000, pausedAtEnd = false), result)
        assertTrue(player.playing)
        assertEquals(0, player.pauseCalls)
    }

    @Test
    fun backSubtractsStep() {
        val player = immediatePlayer(30_000)
        controller.skip(player, SkipDirection.BACK, 10_000)
        assertEquals(listOf(20_000L), player.seeks)
        assertTrue(player.playing)
    }

    @Test
    fun backNearStartClampsToZeroAndStaysOnTrack() {
        val player = immediatePlayer(4_000)
        val result = controller.skip(player, SkipDirection.BACK, 10_000)
        assertEquals(listOf(0L), player.seeks)
        assertEquals("1", player.mediaId)
        assertFalse(result!!.pausedAtEnd)
        assertTrue(player.playing)
    }

    @Test
    fun backAtVeryStartStaysAtZero() {
        val player = immediatePlayer(0)
        controller.skip(player, SkipDirection.BACK, 10_000)
        controller.skip(player, SkipDirection.BACK, 10_000)
        assertEquals(listOf(0L, 0L), player.seeks)
        assertEquals("1", player.mediaId)
    }

    // --- A szám vége ---

    @Test
    fun forwardBeyondEndPausesAtEndOfSameTrack() {
        val player = immediatePlayer(235_000)
        val result = controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertEquals(listOf(240_000L - SkipMath.END_GUARD_MS), player.seeks)
        assertTrue(result!!.pausedAtEnd)
        assertFalse(player.playing)
        assertEquals(1, player.pauseCalls)
        assertEquals("1", player.mediaId)
    }

    @Test
    fun forwardExactlyToEndCountsAsReachingEnd() {
        val player = immediatePlayer(230_000)
        val result = controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertTrue(result!!.pausedAtEnd)
        assertFalse(player.playing)
    }

    @Test
    fun forwardJustBeforeEndDoesNotPause() {
        val player = immediatePlayer(229_000)
        val result = controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertEquals(listOf(239_000L), player.seeks)
        assertFalse(result!!.pausedAtEnd)
        assertTrue(player.playing)
    }

    @Test
    fun pausedForwardBeyondEndStaysPausedWithoutExtraPauseCall() {
        val player = immediatePlayer(235_000, playing = false)
        val result = controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertTrue(result!!.pausedAtEnd)
        assertFalse(player.playing)
        assertEquals(0, player.pauseCalls)
    }

    @Test
    fun repeatedForwardAtEndStaysAtEnd() {
        val player = immediatePlayer(235_000)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        val end = 240_000L - SkipMath.END_GUARD_MS
        assertEquals(listOf(end, end), player.seeks)
        assertEquals("1", player.mediaId)
        assertFalse(player.playing)
    }

    // --- Lejátszás/szünet megtartása ---

    @Test
    fun pausedSkipStaysPaused() {
        val player = immediatePlayer(50_000, playing = false)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        controller.skip(player, SkipDirection.BACK, 10_000)
        assertFalse(player.playing)
        assertEquals(0, player.pauseCalls)
        assertEquals(listOf(60_000L, 50_000L), player.seeks)
    }

    @Test
    fun playingSkipKeepsPlaying() {
        val player = immediatePlayer(50_000, playing = true)
        controller.skip(player, SkipDirection.BACK, 10_000)
        assertTrue(player.playing)
        assertEquals(0, player.pauseCalls)
    }

    // --- Gyors, ismételt koppintások ---

    @Test
    fun rapidTapsAccumulateWithImmediatePlayer() {
        val player = immediatePlayer(10_000)
        repeat(3) { controller.skip(player, SkipDirection.FORWARD, 10_000) }
        assertEquals(listOf(20_000L, 30_000L, 40_000L), player.seeks)
    }

    @Test
    fun immediateConfirmationMakesPlayerPositionAuthoritative() {
        val player = immediatePlayer(10_000, playing = true)
        controller.skip(player, SkipDirection.FORWARD, 10_000) // 20 000, azonnal visszaigazolva
        // A felhasználó közben szünetet nyom; a pozíció 20 000-en marad.
        player.playing = false
        now += 1_500
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        // Nem adódik hozzá a szünet alatt „eltelt” idő: a lejátszó pozíciója a hiteles.
        assertEquals(listOf(20_000L, 30_000L), player.seeks)
    }

    @Test
    fun rapidTapsAccumulateWhenPlayerReportsLate() {
        val player = FakePlayer(reportedPositionMs = 10_000, delayed = true)
        repeat(3) { controller.skip(player, SkipDirection.FORWARD, 10_000) }
        // Három +10 mp összesen 30 mp: a késve frissülő pozíció nem nyel el koppintást.
        assertEquals(listOf(20_000L, 30_000L, 40_000L), player.seeks)
    }

    @Test
    fun mixedDirectionsAccumulateWhenPlayerReportsLate() {
        val player = FakePlayer(reportedPositionMs = 60_000, delayed = true)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        controller.skip(player, SkipDirection.BACK, 30_000)
        assertEquals(listOf(70_000L, 80_000L, 50_000L), player.seeks)
    }

    @Test
    fun lateConfirmationOfOlderSeekDoesNotLoseNewerTaps() {
        val player = FakePlayer(reportedPositionMs = 10_000, delayed = true)
        controller.skip(player, SkipDirection.FORWARD, 10_000) // 20 000
        controller.skip(player, SkipDirection.FORWARD, 10_000) // 30 000
        // A lejátszó most igazolja az első tekerést – a második még függőben van.
        player.reportedPositionMs = 20_000
        controller.onSeekReported("1", 20_000)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertEquals(listOf(20_000L, 30_000L, 40_000L), player.seeks)
    }

    @Test
    fun elapsedPlaybackTimeIsAddedBetweenLateTaps() {
        val player = FakePlayer(reportedPositionMs = 10_000, delayed = true, playing = true)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        now += 500
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertEquals(listOf(20_000L, 30_500L), player.seeks)
    }

    @Test
    fun noElapsedTimeIsAddedWhilePaused() {
        val player = FakePlayer(reportedPositionMs = 10_000, delayed = true, playing = false)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        now += 800
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertEquals(listOf(20_000L, 30_000L), player.seeks)
    }

    @Test
    fun manyRapidTapsNearEndStopAtEndAndPauseOnce() {
        val player = FakePlayer(reportedPositionMs = 200_000, delayed = true)
        repeat(6) { controller.skip(player, SkipDirection.FORWARD, 10_000) }
        val end = 240_000L - SkipMath.END_GUARD_MS
        assertEquals(listOf(210_000L, 220_000L, 230_000L, end, end, end), player.seeks)
        assertEquals(1, player.pauseCalls)
        assertEquals("1", player.mediaId)
    }

    // --- Külső események ---

    @Test
    fun externalSeekReplacesPendingTarget() {
        val player = FakePlayer(reportedPositionMs = 10_000, delayed = true)
        controller.skip(player, SkipDirection.FORWARD, 10_000) // függő: 20 000
        // A felhasználó a csúszkával máshová tekert; a lejátszó ezt jelenti.
        player.reportedPositionMs = 100_000
        controller.onSeekReported("1", 100_000)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertEquals(listOf(20_000L, 110_000L), player.seeks)
    }

    @Test
    fun trackChangeResetsPendingTarget() {
        val player = FakePlayer(reportedPositionMs = 50_000, delayed = true)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        player.mediaId = "2"
        player.reportedPositionMs = 0
        controller.reset()
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertEquals(listOf(60_000L, 10_000L), player.seeks)
    }

    @Test
    fun differentTrackWithoutResetStillUsesReportedPosition() {
        val player = FakePlayer(reportedPositionMs = 50_000, delayed = true)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        player.mediaId = "2"
        player.reportedPositionMs = 5_000
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertEquals(listOf(60_000L, 15_000L), player.seeks)
    }

    @Test
    fun stalePendingTargetExpires() {
        val player = FakePlayer(reportedPositionMs = 10_000, delayed = true, playing = false)
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        now += SkipController.DEFAULT_PENDING_WINDOW_MS + 1
        player.reportedPositionMs = 20_000
        controller.skip(player, SkipDirection.FORWARD, 10_000)
        assertEquals(listOf(20_000L, 30_000L), player.seeks)
    }

    // --- Inaktív állapotok ---

    @Test
    fun noTrackLoadedDoesNothing() {
        val player = FakePlayer(mediaId = null)
        assertNull(controller.skip(player, SkipDirection.FORWARD, 10_000))
        assertTrue(player.seeks.isEmpty())
    }

    @Test
    fun unseekableContentDoesNothing() {
        val player = FakePlayer(seekable = false, reportedPositionMs = 10_000)
        assertNull(controller.skip(player, SkipDirection.FORWARD, 10_000))
        assertTrue(player.seeks.isEmpty())
        assertTrue(player.playing)
    }

    @Test
    fun unknownDurationDoesNothing() {
        val player = FakePlayer(duration = -9_223_372_036_854_775_807L, reportedPositionMs = 10_000)
        assertNull(controller.skip(player, SkipDirection.FORWARD, 10_000))
        assertTrue(player.seeks.isEmpty())
    }

    @Test
    fun differentStepsPerDirection() {
        val player = immediatePlayer(100_000)
        controller.skip(player, SkipDirection.BACK, 5_000)
        controller.skip(player, SkipDirection.FORWARD, 60_000)
        assertEquals(listOf(95_000L, 155_000L), player.seeks)
    }
}
