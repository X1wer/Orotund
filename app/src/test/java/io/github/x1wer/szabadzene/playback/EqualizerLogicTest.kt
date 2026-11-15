package io.github.x1wer.szabadzene.playback

import io.github.x1wer.szabadzene.data.settings.EqSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerLogicTest {
    /** Az Android szoftveres hangszínszabályzójának tipikus 5 sávja. */
    private val fiveBands = listOf(60, 230, 910, 3600, 14000).map { EqBand(it, -1500, 1500) }
    private val tenBands = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000).map { EqBand(it, -1200, 1200) }

    @Test
    fun flatIsZeroEverywhere() {
        assertEquals(List(5) { 0 }, EqualizerLogic.levelsFor(EqPreset.FLAT, emptyMap(), fiveBands))
        assertEquals(List(10) { 0 }, EqualizerLogic.levelsFor(EqPreset.FLAT, emptyMap(), tenBands))
    }

    @Test
    fun presetHitsDefinedPointsExactly() {
        assertEquals(listOf(600, 400, 0, 0, 0), EqualizerLogic.levelsFor(EqPreset.BASS, emptyMap(), fiveBands))
        assertEquals(listOf(500, 300, -100, 300, 500), EqualizerLogic.levelsFor(EqPreset.ROCK, emptyMap(), fiveBands))
    }

    @Test
    fun interpolatesOnLogFrequencyAndExtrapolatesFlat() {
        // 31 Hz a görbe első pontja alatt: az első érték; 16 kHz felett: az utolsó.
        val levels = EqualizerLogic.levelsFor(EqPreset.BASS, emptyMap(), tenBands)
        assertEquals(600, levels.first())
        assertEquals(0, levels.last())
        // Monoton csökken a mélyektől a közepéig.
        assertTrue(levels.zipWithNext().take(5).all { (a, b) -> a >= b })
    }

    @Test
    fun levelsAreClampedToDeviceRange() {
        val narrow = listOf(EqBand(60, -300, 300), EqBand(14000, -300, 300))
        assertEquals(listOf(300, 300), EqualizerLogic.levelsFor(EqPreset.ROCK, emptyMap(), narrow))
        val custom = mapOf(60 to 1500, 14000 to -1500)
        assertEquals(listOf(300, -300), EqualizerLogic.levelsFor(EqPreset.CUSTOM, custom, narrow))
    }

    @Test
    fun customLevelsSurviveDifferentBandLayout() {
        // Ötsávos készüléken mentett egyéni beállítás tízsávoson is értelmes marad.
        val custom = mapOf(60 to 900, 230 to 300, 910 to 0, 3600 to -300, 14000 to -900)
        val five = EqualizerLogic.levelsFor(EqPreset.CUSTOM, custom, fiveBands)
        assertEquals(listOf(900, 300, 0, -300, -900), five)
        val ten = EqualizerLogic.levelsFor(EqPreset.CUSTOM, custom, tenBands)
        assertEquals(900, ten.first())
        assertEquals(-900, ten.last())
        assertTrue(ten.zipWithNext().all { (a, b) -> a >= b })
    }

    @Test
    fun changingOneBandKeepsOthers() {
        val current = listOf(600, 400, 0, 0, 0)
        val changed = EqualizerLogic.withBandChanged(current, fiveBands, 2, 700)
        assertEquals(mapOf(60 to 600, 230 to 400, 910 to 700, 3600 to 0, 14000 to 0), changed)
        assertEquals(listOf(600, 400, 700, 0, 0), EqualizerLogic.levelsFor(EqPreset.CUSTOM, changed, fiveBands))
    }

    @Test
    fun settingsEncodingRoundTripAndGarbage() {
        val levels = mapOf(60 to -300, 230 to 0, 14000 to 1200)
        assertEquals(levels, EqSettings.decodeLevels(EqSettings.encodeLevels(levels)))
        assertEquals(emptyMap<Int, Int>(), EqSettings.decodeLevels(null))
        assertEquals(mapOf(60 to 1500), EqSettings.decodeLevels("60:99999,abc,:5,0:3,x:y"))
    }

    @Test
    fun unknownPresetFallsBackToFlat() {
        assertEquals(EqPreset.FLAT, EqPreset.of("NINCS_ILYEN"))
        assertEquals(EqPreset.CUSTOM, EqPreset.of("CUSTOM"))
    }

    @Test
    fun frequencyLabels() {
        assertEquals("60 Hz", EqualizerLogic.frequencyLabel(60))
        assertEquals("14 kHz", EqualizerLogic.frequencyLabel(14000))
        assertEquals("3,6 kHz", EqualizerLogic.frequencyLabel(3600))
        assertEquals("3.6 kHz", EqualizerLogic.frequencyLabel(3600, '.'))
    }
}
