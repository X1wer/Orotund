package io.github.x1wer.szabadzene.playback

/**
 * Mikor számít egy szám meghallgatottnak (tiszta Kotlin, JVM-en tesztelt).
 *
 * Szabály: a ténylegesen lejátszott idő (szünet és előretekerés nem számít) eléri a szám hosszának
 * felét, de legfeljebb 4 percet; ismeretlen hossznál 30 mp-et. Lejátszásonként egyszer számít.
 * Új lejátszás kezdődik számváltáskor (az ismétlés is az), és ha egy már beszámított lejátszás után
 * a felhasználó a szám elejére teker vissza.
 */
class PlayCountTracker(
    private val clock: () -> Long,
    private val onCounted: (mediaId: String) -> Unit,
) {
    private var mediaId: String? = null
    private var durationMs = 0L
    private var listenedMs = 0L
    private var playingSince: Long? = null
    private var counted = false

    /** Új szám (vagy ugyanaz újra, pl. ismétlésnél) kezdődik. */
    fun onItemStarted(mediaId: String?, durationMs: Long, isPlaying: Boolean) {
        this.mediaId = mediaId
        this.durationMs = durationMs.coerceAtLeast(0L)
        listenedMs = 0L
        counted = false
        playingSince = if (isPlaying && mediaId != null) clock() else null
    }

    /** A hossz sokszor csak a betöltés után ismert. */
    fun onDurationKnown(durationMs: Long) {
        if (durationMs > 0) this.durationMs = durationMs
        check()
    }

    fun onPlayingChanged(isPlaying: Boolean) {
        val now = clock()
        if (isPlaying) {
            if (playingSince == null && mediaId != null) playingSince = now
        } else {
            playingSince?.let { listenedMs += now - it }
            playingSince = null
        }
        check()
    }

    /** A felhasználó a szám elejére tekert: egy már beszámított lejátszás után ez új lejátszás. */
    fun onSeekToStart(isPlaying: Boolean) {
        if (counted) onItemStarted(mediaId, durationMs, isPlaying)
    }

    /** Időnként (lejátszás közben másodpercenként) hívandó; ha elérte a küszöböt, egyszer jelez. */
    fun check() {
        val id = mediaId ?: return
        if (counted) return
        val running = playingSince?.let { clock() - it } ?: 0L
        if (listenedMs + running >= thresholdMs(durationMs)) {
            counted = true
            onCounted(id)
        }
    }

    companion object {
        const val MAX_THRESHOLD_MS = 4 * 60 * 1000L
        const val UNKNOWN_DURATION_THRESHOLD_MS = 30_000L

        fun thresholdMs(durationMs: Long): Long =
            if (durationMs <= 0) UNKNOWN_DURATION_THRESHOLD_MS else minOf(durationMs / 2, MAX_THRESHOLD_MS)
    }
}
