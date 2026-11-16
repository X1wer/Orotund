package io.github.x1wer.szabadzene.playback

import io.github.x1wer.szabadzene.data.settings.LibrarySort
import io.github.x1wer.szabadzene.data.settings.PlaybackPrefs
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.data.settings.SortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Az „Előző” gomb küszöbe, a teljes lejátszó ugrógombjai, a sebesség és az alapértelmezett rendezés. */
class PlayerExtrasLogicTest {

    // --- Előző gomb ---

    @Test
    fun previousRestartsSongAfterThresholdAndGoesBackWithinIt() {
        val threshold = 10_000L
        assertEquals(PreviousAction.RESTART, PreviousLogic.decide(45_000, threshold, hasPrevious = true, hasMedia = true))
        assertEquals(PreviousAction.RESTART, PreviousLogic.decide(10_001, threshold, hasPrevious = true, hasMedia = true))
        assertEquals(PreviousAction.PREVIOUS_ITEM, PreviousLogic.decide(10_000, threshold, hasPrevious = true, hasMedia = true))
        assertEquals(PreviousAction.PREVIOUS_ITEM, PreviousLogic.decide(0, threshold, hasPrevious = true, hasMedia = true))
    }

    @Test
    fun previousWithoutPreviousSongAlwaysRestarts() {
        assertEquals(PreviousAction.RESTART, PreviousLogic.decide(2_000, 10_000, hasPrevious = false, hasMedia = true))
        assertEquals(PreviousAction.RESTART, PreviousLogic.decide(0, 10_000, hasPrevious = false, hasMedia = true))
        assertEquals(PreviousAction.NONE, PreviousLogic.decide(0, 10_000, hasPrevious = true, hasMedia = false))
    }

    @Test
    fun restartThresholdDefaultsToTenSecondsAndAcceptsOnlyAllowedValues() {
        assertEquals(10, PlaybackPrefs().restartThresholdSeconds)
        assertEquals(10_000L, PlaybackPrefs().restartThresholdMs)
        PreviousLogic.ALLOWED_THRESHOLD_SECONDS.forEach { assertEquals(it, PlaybackPrefs.sanitizeThreshold(it)) }
        assertEquals(10, PlaybackPrefs.sanitizeThreshold(null))
        assertEquals(10, PlaybackPrefs.sanitizeThreshold(7))
    }

    // --- Sebesség ---

    @Test
    fun speedIsClampedAndRoundedToFiveHundredths() {
        assertEquals(1f, PlaybackPrefs.sanitizeSpeed(null))
        assertEquals(0.5f, PlaybackPrefs.sanitizeSpeed(0.1f))
        assertEquals(2f, PlaybackPrefs.sanitizeSpeed(3f))
        assertEquals(1.25f, PlaybackPrefs.sanitizeSpeed(1.26f))
        assertEquals(1f, PlaybackPrefs.sanitizeSpeed(Float.NaN))
    }

    // --- Ugrógombok a teljes lejátszóban ---

    @Test
    fun defaultFullPlayerStepsMatchThirtyTenFive() {
        val skip = SkipSettings()
        assertEquals(listOf(5, 30), skip.extraSeconds)
        assertEquals(listOf(5, 10, 30), skip.stepsFor(back = true))
        assertEquals(listOf(5, 10, 30), skip.stepsFor(back = false))
    }

    @Test
    fun mainStepIsAlwaysIncludedEvenIfNotAmongExtras() {
        val skip = SkipSettings(backSeconds = 15, forwardSeconds = 60, extraSeconds = listOf(5, 30))
        assertEquals(listOf(5, 15, 30), skip.stepsFor(back = true))
        assertEquals(listOf(5, 30, 60), skip.stepsFor(back = false))
        // Kevés helynél a fő érték marad meg, mellette a legnagyobb további érték.
        assertEquals(listOf(15, 30), SkipSettings.visibleSteps(skip.stepsFor(back = true), main = 15, count = 2))
        assertEquals(listOf(15), SkipSettings.visibleSteps(skip.stepsFor(back = true), main = 15, count = 1))
    }

    @Test
    fun extraStepsAreSanitizedAndStored() {
        assertEquals(listOf(5, 30), SkipSettings.decodeExtra(null))
        assertEquals(emptyList<Int>(), SkipSettings.decodeExtra(""))
        assertEquals(listOf(10, 60), SkipSettings.decodeExtra("60, 10, 7, x"))
        assertEquals(listOf(5, 10), SkipSettings.sanitizeExtra(listOf(30, 10, 5)))
        assertEquals("15,30", SkipSettings.encodeExtra(listOf(30, 15, 15)))
    }

    @Test
    fun buttonsPerSideFitsTheAvailableWidth() {
        // 6 × 56 + 5 × 4 = 356 dp kell három-három gombhoz.
        assertEquals(3, SkipSettings.buttonsPerSide(3, availableDp = 356f, buttonDp = 56f, gapDp = 4f))
        assertEquals(2, SkipSettings.buttonsPerSide(3, availableDp = 355f, buttonDp = 56f, gapDp = 4f))
        assertEquals(2, SkipSettings.buttonsPerSide(3, availableDp = 236f, buttonDp = 56f, gapDp = 4f))
        assertEquals(1, SkipSettings.buttonsPerSide(3, availableDp = 235f, buttonDp = 56f, gapDp = 4f))
        assertEquals(1, SkipSettings.buttonsPerSide(1, availableDp = 1000f, buttonDp = 56f, gapDp = 4f))
    }

    // --- Rendezés ---

    @Test
    fun libraryDefaultSortIsNewestFirst() {
        val sort = LibrarySort()
        assertEquals(SortField.DATE_ADDED, sort.field)
        assertTrue(sort.descending)
        assertEquals(LibrarySort(SortField.DATE_ADDED, true), LibrarySort.naturalFor(SortField.DATE_ADDED))
        assertEquals(LibrarySort(SortField.TITLE, false), LibrarySort.naturalFor(SortField.TITLE))
    }
}
