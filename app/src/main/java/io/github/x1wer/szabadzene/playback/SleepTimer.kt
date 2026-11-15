package io.github.x1wer.szabadzene.playback

import java.util.Locale

/** Az elalvásidőzítő állapota. Az időpontok `SystemClock.elapsedRealtime()` szerintiek. */
sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** A lejátszás [endAtElapsedMs]-kor áll le. */
    data class Timed(val endAtElapsedMs: Long, val durationMs: Long) : SleepTimerState {
        fun remainingMs(nowElapsedMs: Long): Long = (endAtElapsedMs - nowElapsedMs).coerceAtLeast(0L)
    }

    /** A lejátszás az aktuális szám végén áll le. */
    data object EndOfTrack : SleepTimerState
}

/** Mi a teendő egy lejátszási esemény után. */
enum class SleepTimerAction { NONE, PAUSE_NOW }

/**
 * Az elalvásidőzítő tiszta logikája (unit-tesztelt). A szolgáltatás hívja: indítás, leállítás,
 * óra-ütés, és a lejátszó eseményei (szám vége, lejátszás leállása).
 */
class SleepTimer(private val clock: () -> Long) {

    var state: SleepTimerState = SleepTimerState.Off
        private set

    fun startTimed(durationMs: Long): SleepTimerState {
        require(durationMs > 0) { "Az időtartamnak pozitívnak kell lennie" }
        state = SleepTimerState.Timed(clock() + durationMs, durationMs)
        return state
    }

    fun startEndOfTrack(): SleepTimerState {
        state = SleepTimerState.EndOfTrack
        return state
    }

    fun cancel() {
        state = SleepTimerState.Off
    }

    /** A még hátralévő idő; null, ha nincs időzített leállás. */
    fun remainingMs(): Long? = (state as? SleepTimerState.Timed)?.remainingMs(clock())

    /** Óra-ütés: lejárt idő esetén leáll (egyszer), és az időzítő kikapcsol. */
    fun onTick(): SleepTimerAction {
        val timed = state as? SleepTimerState.Timed ?: return SleepTimerAction.NONE
        if (timed.remainingMs(clock()) > 0) return SleepTimerAction.NONE
        state = SleepTimerState.Off
        return SleepTimerAction.PAUSE_NOW
    }

    /**
     * A lejátszó egy szám végén megállt (a „szünet a szám végén” beállítás miatt), vagy a várólista
     * véget ért. „Szám végén” módban ez maga a leállás: az időzítő kikapcsol.
     */
    fun onTrackEnded(): SleepTimerAction {
        if (state != SleepTimerState.EndOfTrack) return SleepTimerAction.NONE
        state = SleepTimerState.Off
        // A lejátszó már szünetel; további teendő nincs.
        return SleepTimerAction.NONE
    }

    /** A lejátszónak kell-e minden szám végén megállnia (csak „szám végén” módban). */
    val pauseAtEndOfItems: Boolean
        get() = state == SleepTimerState.EndOfTrack

    companion object {
        /** A felületen kínált időtartamok percben. */
        val PRESET_MINUTES = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

        /** Megjelenítés: „1:05:09” vagy „12:34”. */
        fun formatRemaining(ms: Long): String {
            val totalSeconds = (ms + 999) / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
            }
        }
    }
}
