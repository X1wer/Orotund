package io.github.x1wer.szabadzene.playback

/** Mit tegyen az „Előző” gomb. */
enum class PreviousAction { NONE, RESTART, PREVIOUS_ITEM }

/**
 * Az „Előző” gomb szabálya (tiszta Kotlin, JVM-en tesztelt): ha a szám elejétől legfeljebb
 * [thresholdMs] telt el és van előző szám, arra ugrik; különben az aktuális szám elejére teker.
 * Így az első nyomás a szám elejére visz, a második (az elején) az előző számra.
 */
object PreviousLogic {
    const val DEFAULT_THRESHOLD_SECONDS = 10
    val ALLOWED_THRESHOLD_SECONDS = listOf(3, 5, 10, 15, 30)

    fun decide(positionMs: Long, thresholdMs: Long, hasPrevious: Boolean, hasMedia: Boolean): PreviousAction = when {
        !hasMedia -> PreviousAction.NONE
        hasPrevious && positionMs <= thresholdMs -> PreviousAction.PREVIOUS_ITEM
        else -> PreviousAction.RESTART
    }
}
