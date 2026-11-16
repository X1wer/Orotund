package io.github.x1wer.szabadzene.data.lyrics

/** A dalszöveg egy sora; [timeMs] csak időzített (LRC) szövegnél van megadva. */
data class LyricLine(val timeMs: Long?, val text: String)

/** Feldolgozott dalszöveg. Időzítettnél a sorok időrendben vannak. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {

    val isEmpty: Boolean get() = lines.all { it.text.isBlank() }

    /** Az adott pozícióban énekelt sor indexe (időzített szövegnél), vagy -1 az első sor előtt. */
    fun indexAt(positionMs: Long): Int {
        if (!synced) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if ((lines[mid].timeMs ?: 0L) <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    companion object {
        val EMPTY = Lyrics(emptyList(), synced = false)
    }
}

/**
 * Dalszöveg feldolgozása (tiszta Kotlin, JVM-en tesztelt). Az LRC formátumot felismeri:
 * `[mm:ss.xx]` időbélyegek (soronként több is), `[offset:±ms]`, a fejléc-címkéket (`[ar:…]`,
 * `[ti:…]` stb.) kihagyja, a szavankénti `<mm:ss.xx>` jelölést eltávolítja. Időbélyeg nélkül
 * sima szövegként kezeli.
 */
object LrcParser {
    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val LEADING_TIMES = Regex("""^\s*((?:\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?])+)(.*)$""")
    private val HEADER = Regex("""^\s*\[([a-zA-Z#]+):(.*)]\s*$""")
    private val WORD_TIME = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parse(text: String?): Lyrics {
        if (text.isNullOrBlank()) return Lyrics.EMPTY
        val rawLines = text.lines()
        var offsetMs = 0L
        val timed = ArrayList<LyricLine>()
        for (line in rawLines) {
            val match = LEADING_TIMES.find(line)
            if (match != null) {
                val content = WORD_TIME.replace(match.groupValues[2], "").trim()
                TIME.findAll(match.groupValues[1]).forEach { stamp ->
                    timed += LyricLine(timeOf(stamp), content)
                }
                continue
            }
            HEADER.find(line)?.let { header ->
                if (header.groupValues[1].equals("offset", ignoreCase = true)) {
                    offsetMs = header.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: 0L
                }
            }
        }
        if (timed.isNotEmpty()) {
            // Pozitív eltolás: a szöveg korábban jelenik meg.
            val shifted = timed.map { it.copy(timeMs = ((it.timeMs ?: 0L) - offsetMs).coerceAtLeast(0L)) }
            return Lyrics(shifted.sortedBy { it.timeMs }, synced = true)
        }
        val plain = rawLines.map { it.trimEnd() }.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        return Lyrics(plain.map { LyricLine(null, it) }, synced = false)
    }

    private fun timeOf(stamp: MatchResult): Long {
        val minutes = stamp.groupValues[1].toLong()
        val seconds = stamp.groupValues[2].toLong()
        val fraction = stamp.groupValues[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return minutes * 60_000 + seconds * 1_000 + fractionMs
    }
}
