package io.github.x1wer.szabadzene.playback

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Saját (fordítható) előbeállítások. A görbe néhány frekvencián adott (dB); a készülék
 * sávjaira logaritmikus frekvencia szerinti interpolációval számoljuk át, így bármennyi
 * sávos hangszínszabályzón ugyanazt a karaktert adja.
 */
enum class EqPreset(val curve: List<Pair<Int, Float>>) {
    FLAT(listOf(1000 to 0f)),
    BASS(listOf(60 to 6f, 230 to 4f, 910 to 0f, 3600 to 0f, 14000 to 0f)),
    TREBLE(listOf(60 to 0f, 230 to 0f, 910 to 0f, 3600 to 4f, 14000 to 6f)),
    VOCAL(listOf(60 to -2f, 230 to 0f, 910 to 3f, 3600 to 3f, 14000 to 0f)),
    ROCK(listOf(60 to 5f, 230 to 3f, 910 to -1f, 3600 to 3f, 14000 to 5f)),
    POP(listOf(60 to -1f, 230 to 2f, 910 to 4f, 3600 to 2f, 14000 to -1f)),
    JAZZ(listOf(60 to 3f, 230 to 2f, 910 to -1f, 3600 to 2f, 14000 to 4f)),
    CLASSICAL(listOf(60 to 4f, 230 to 2f, 910 to 0f, 3600 to 2f, 14000 to 4f)),

    /** A felhasználó saját sávértékei (a beállításokban tárolva). */
    CUSTOM(emptyList()),
    ;

    companion object {
        fun of(name: String?): EqPreset = entries.firstOrNull { it.name == name } ?: FLAT
    }
}

/** A készülék egy hangszínszabályzó-sávja: középfrekvencia (Hz) és a szint tartománya (millibel). */
data class EqBand(val centerHz: Int, val minMb: Int, val maxMb: Int)

/** Ami ezen a készüléken futásidőben ténylegesen elérhető. */
data class EffectsCapabilities(
    val equalizer: Boolean,
    val bands: List<EqBand>,
    val bassBoost: Boolean,
) {
    companion object {
        val NONE = EffectsCapabilities(equalizer = false, bands = emptyList(), bassBoost = false)
    }
}

object EqualizerLogic {

    /** A görbe értéke (dB) egy adott frekvencián: log-frekvencia szerinti lineáris interpoláció. */
    fun curveAt(curve: List<Pair<Int, Float>>, hz: Int): Float {
        if (curve.isEmpty()) return 0f
        val sorted = curve.sortedBy { it.first }
        if (hz <= sorted.first().first) return sorted.first().second
        if (hz >= sorted.last().first) return sorted.last().second
        val x = log10(hz.toDouble())
        for (i in 0 until sorted.lastIndex) {
            val (f0, g0) = sorted[i]
            val (f1, g1) = sorted[i + 1]
            if (hz in f0..f1) {
                val x0 = log10(f0.toDouble())
                val x1 = log10(f1.toDouble())
                val t = if (x1 == x0) 0.0 else (x - x0) / (x1 - x0)
                return (g0 + (g1 - g0) * t).toFloat()
            }
        }
        return 0f
    }

    /** Az egyes sávok szintje (millibel) az előbeállítás vagy az egyéni értékek alapján, a tartományra szorítva. */
    fun levelsFor(preset: EqPreset, custom: Map<Int, Int>, bands: List<EqBand>): List<Int> = bands.map { band ->
        val mb = if (preset == EqPreset.CUSTOM) {
            customAt(custom, band.centerHz)
        } else {
            (curveAt(preset.curve, band.centerHz) * 100).roundToInt()
        }
        mb.coerceIn(band.minMb, band.maxMb)
    }

    /**
     * Egyéni érték egy sávra. A tárolt értékek frekvenciához kötöttek; ha a készülék sávjai
     * mások (pl. másik telefon vagy hangkimenet), a legközelebbi (log-frekvencia) értékek közt interpolál.
     */
    fun customAt(custom: Map<Int, Int>, hz: Int): Int {
        if (custom.isEmpty()) return 0
        custom[hz]?.let { return it }
        val curve = custom.map { it.key to it.value / 100f }
        return (curveAt(curve, hz) * 100).roundToInt()
    }

    /** Egy sáv módosítása után az új egyéni térkép (a többi sáv a jelenlegi szintjét tartja). */
    fun withBandChanged(current: List<Int>, bands: List<EqBand>, bandIndex: Int, levelMb: Int): Map<Int, Int> =
        bands.mapIndexed { index, band ->
            band.centerHz to if (index == bandIndex) levelMb.coerceIn(band.minMb, band.maxMb) else current[index]
        }.toMap()

    /** A felületen mutatott frekvenciafelirat (pl. „60 Hz”, „3,6 kHz”). */
    fun frequencyLabel(hz: Int, decimalSeparator: Char = ','): String = when {
        hz < 1000 -> "$hz Hz"
        hz % 1000 == 0 -> "${hz / 1000} kHz"
        else -> {
            val tenths = (hz / 100.0).roundToInt()
            "${tenths / 10}$decimalSeparator${abs(tenths % 10)} kHz"
        }
    }
}
