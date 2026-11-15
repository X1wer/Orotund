package io.github.x1wer.szabadzene.playback

import kotlin.math.abs

/** Az aktuális számon belüli ugrás iránya. */
enum class SkipDirection { BACK, FORWARD }

/**
 * A lejátszónak az a része, amire az ugrási logikának szüksége van.
 * Külön felület, hogy a logika lejátszó nélkül, JVM-en is tesztelhető legyen.
 */
interface SkipTarget {
    /** Az aktuális elem azonosítója, vagy null, ha nincs betöltött szám. */
    val currentMediaId: String?

    /** A lejátszó által jelentett pozíció. Késleltetett lejátszónál elmaradhat a valóditól. */
    val currentPositionMs: Long

    /** A szám hossza; nem pozitív érték, ha ismeretlen. */
    val durationMs: Long

    /** Tekerhető-e az aktuális tartalom. */
    val isSeekable: Boolean

    /** A felhasználó szándéka szerint szól-e (playWhenReady). */
    val isPlaying: Boolean

    fun seekTo(positionMs: Long)

    fun pause()
}

/** Egy ugrás eredménye. */
data class SkipResult(val positionMs: Long, val pausedAtEnd: Boolean)

/** Tiszta számítás: a nyers célpozícióból a ténylegesen beállítandó pozíció. */
object SkipMath {
    /**
     * A szám végére érő ugrás ennyivel a vége elé áll. Így a szüneteltetett lejátszó biztosan
     * a számon marad, és nem lép a várólista következő elemére.
     */
    const val END_GUARD_MS = 200L

    data class Resolution(val positionMs: Long, val reachedEnd: Boolean)

    fun resolve(rawTargetMs: Long, durationMs: Long): Resolution {
        require(durationMs > 0) { "A hossznak ismertnek kell lennie." }
        return if (rawTargetMs >= durationMs) {
            Resolution((durationMs - END_GUARD_MS).coerceAtLeast(0L), reachedEnd = true)
        } else {
            Resolution(rawTargetMs.coerceAtLeast(0L), reachedEnd = false)
        }
    }
}

/**
 * Az alkalmazás egyetlen közös ugrási művelete. A teljes lejátszó, a minilejátszó, az értesítés
 * gombjai és a fejhallgató előre/vissza gombja is ezen megy keresztül.
 *
 * Gyors, egymás utáni koppintásoknál az új célpozíció a még vissza nem igazolt (függő)
 * célpozícióból számolódik, így egy koppintás sem vész el akkor sem, ha a lejátszó
 * késve jelenti az új pozíciót.
 */
class SkipController(
    private val clock: () -> Long,
    private val pendingWindowMs: Long = DEFAULT_PENDING_WINDOW_MS,
) {
    private data class Pending(
        val mediaId: String?,
        val targetMs: Long,
        val issuedAtMs: Long,
        val playing: Boolean,
    )

    private var pending: Pending? = null

    /** A kiadott, de a lejátszó által még vissza nem igazolt célpozíciók, kiadási sorrendben. */
    private val outstanding = ArrayDeque<Long>()

    /**
     * Ugrás az aktuális számon belül. Null, ha nincs mit tekerni (nincs szám, nem tekerhető,
     * vagy ismeretlen a hossza) – ilyenkor a lejátszó állapota nem változik.
     */
    fun skip(target: SkipTarget, direction: SkipDirection, stepMs: Long): SkipResult? {
        require(stepMs > 0) { "Az ugrás mértékének pozitívnak kell lennie." }
        if (target.currentMediaId == null || !target.isSeekable) return null
        val duration = target.durationMs
        if (duration <= 0) return null

        val now = clock()
        val base = basePosition(target, now)
        val raw = when (direction) {
            SkipDirection.BACK -> base - stepMs
            SkipDirection.FORWARD -> base + stepMs
        }
        val resolution = SkipMath.resolve(raw, duration)

        // Csak a szám végét elérő előreugrás változtat a lejátszás/szünet állapoton.
        if (resolution.reachedEnd && target.isPlaying) target.pause()

        // A függő célt a tekerés ELŐTT jegyezzük fel: az azonnal jelentő lejátszó
        // (pl. ExoPlayer) még a seekTo hívás alatt visszaigazolhatja.
        pending = Pending(
            mediaId = target.currentMediaId,
            targetMs = resolution.positionMs,
            issuedAtMs = now,
            playing = target.isPlaying,
        )
        outstanding.addLast(resolution.positionMs)
        while (outstanding.size > MAX_OUTSTANDING) outstanding.removeFirst()

        target.seekTo(resolution.positionMs)
        return SkipResult(resolution.positionMs, resolution.reachedEnd)
    }

    /**
     * A lejátszó egy tekerést jelentett. Ha ez az egyik saját, még függő célpozíciónk, az addigiak
     * igazoltak; ha egyikkel sem egyezik, külső tekerés történt (pl. csúszka), és a függő
     * állapot érvényét veszti.
     */
    fun onSeekReported(mediaId: String?, positionMs: Long) {
        val current = pending ?: return
        if (mediaId != current.mediaId) {
            reset()
            return
        }
        val index = outstanding.indexOfFirst { abs(it - positionMs) <= MATCH_TOLERANCE_MS }
        if (index < 0) {
            reset()
            return
        }
        repeat(index + 1) { outstanding.removeFirst() }
        if (outstanding.isEmpty()) pending = null
    }

    /** Számváltáskor vagy a lejátszó újratöltésekor. */
    fun reset() {
        pending = null
        outstanding.clear()
    }

    private fun basePosition(target: SkipTarget, now: Long): Long {
        val current = pending ?: return target.currentPositionMs
        val age = now - current.issuedAtMs
        if (current.mediaId != target.currentMediaId || age < 0 || age > pendingWindowMs) {
            reset()
            return target.currentPositionMs
        }
        // Lejátszás közben a függő célpozíció óta eltelt idő is hozzáadódik.
        return current.targetMs + if (current.playing) age else 0L
    }

    companion object {
        const val DEFAULT_PENDING_WINDOW_MS = 2_000L
        private const val MATCH_TOLERANCE_MS = 250L
        private const val MAX_OUTSTANDING = 32
    }
}
