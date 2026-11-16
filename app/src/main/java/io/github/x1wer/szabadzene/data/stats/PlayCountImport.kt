package io.github.x1wer.szabadzene.data.stats

import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.library.searchKey

/*
 * Lejátszásszámok importálása egy másik lejátszóból kiolvasott táblázatból (CSV). Tiszta Kotlin,
 * JVM-en tesztelhető: a fájl olvasása és a párosítás a zenetár számaival.
 */

/** Egy sor a táblázatból. A hossz (másodperc) és az előadó elmaradhat. */
data class ImportedCount(val count: Int, val durationSeconds: Int?, val title: String, val artist: String?)

object PlayCountCsv {
    const val MAX_BYTES = 4 * 1024 * 1024

    /**
     * Pontosvesszővel, vesszővel vagy tabulátorral tagolt szöveg. Ha van fejléc (`count`/`lejatszas`,
     * `title`/`cim`, `artist`/`eloado`, `duration`/`hossz`), az oszlopokat a nevük adja; fejléc nélkül a
     * sorrend: számláló, hossz, cím, előadó. Hibás vagy 0 számlálójú sort kihagy.
     */
    fun parse(text: String): List<ImportedCount> {
        val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val delimiter = listOf(';', '\t', ',').maxBy { d -> lines.first().count { it == d } }
        val header = splitRow(lines.first(), delimiter).map { searchKey(it).trim() }
        val named = header.any { it in COUNT_NAMES } && header.any { it in TITLE_NAMES }
        val index = if (named) {
            Columns(
                count = header.indexOfFirst { it in COUNT_NAMES },
                duration = header.indexOfFirst { it in DURATION_NAMES },
                title = header.indexOfFirst { it in TITLE_NAMES },
                artist = header.indexOfFirst { it in ARTIST_NAMES },
            )
        } else {
            Columns(count = 0, duration = 1, title = 2, artist = 3)
        }
        return lines.drop(if (named) 1 else 0).mapNotNull { line ->
            val cells = splitRow(line, delimiter)
            val count = cells.getOrNull(index.count)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val title = cells.getOrNull(index.title)?.trim().orEmpty()
            if (count <= 0 || title.isEmpty()) return@mapNotNull null
            ImportedCount(
                count = count,
                durationSeconds = cells.getOrNull(index.duration)?.let(::parseDuration),
                title = title,
                artist = cells.getOrNull(index.artist)?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", true) },
            )
        }
    }

    /** „03:35” vagy „1:02:03” → másodperc; hibás szövegre null. */
    fun parseDuration(text: String): Int? {
        val parts = text.trim().split(':').map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private class Columns(val count: Int, val duration: Int, val title: Int, val artist: Int)

    private val COUNT_NAMES = setOf("count", "playcount", "lejatszas", "lejatszasszam", "db")
    private val TITLE_NAMES = setOf("title", "cim", "dal", "szam")
    private val ARTIST_NAMES = setOf("artist", "eloado")
    private val DURATION_NAMES = setOf("duration", "hossz", "length")

    /** Egy CSV-sor mezőkre bontása; az idézőjeles mező tartalmazhat elválasztót, a `""` egy idézőjel. */
    private fun splitRow(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> { cells += cell.toString(); cell.setLength(0) }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString()
        return cells
    }
}

/** A párosítás eredménye. [matched]: szám → importált számláló (több sor ugyanarra a számra: a legnagyobb). */
data class PlayCountMatch(
    val matched: Map<Track, Int>,
    val ambiguous: List<ImportedCount>,
    val missing: List<ImportedCount>,
)

/**
 * A táblázat soraihoz a zenetár számát keresi: egyező cím (ékezet- és kisbetű-független, szóközök összevonva)
 * vagy fájlnév. Több jelölt esetén az előadó, majd a hossz (±3 mp) dönt; ha így sem egyértelmű, a sor a
 * kétértelműek közé kerül. Egyetlen jelöltnél, ha a hossz ismert és 10 mp-nél jobban eltér, más változatnak
 * számít (nem találat).
 */
class PlayCountMatcher(tracks: List<Track>) {
    private val byTitle = HashMap<String, MutableList<Track>>()

    init {
        tracks.forEach { track ->
            byTitle.getOrPut(key(track.title)) { mutableListOf() }.add(track)
            val fileKey = key(track.displayName.substringBeforeLast('.', track.displayName))
            if (fileKey != key(track.title)) byTitle.getOrPut(fileKey) { mutableListOf() }.add(track)
        }
    }

    fun match(entries: List<ImportedCount>): PlayCountMatch {
        val matched = LinkedHashMap<Track, Int>()
        val ambiguous = mutableListOf<ImportedCount>()
        val missing = mutableListOf<ImportedCount>()
        for (entry in entries) {
            val candidates = byTitle[key(entry.title)].orEmpty().distinctBy { it.id }
            val chosen: Track? = when {
                candidates.isEmpty() -> { missing += entry; continue }
                candidates.size == 1 -> {
                    val only = candidates.single()
                    if (entry.durationSeconds != null && only.durationMs > 0 &&
                        kotlin.math.abs(only.durationMs / 1000 - entry.durationSeconds) > SINGLE_TOLERANCE
                    ) {
                        missing += entry
                        continue
                    }
                    only
                }
                else -> narrow(candidates, entry)
            }
            if (chosen == null) {
                ambiguous += entry
            } else {
                matched.merge(chosen, entry.count) { a, b -> maxOf(a, b) }
            }
        }
        return PlayCountMatch(matched, ambiguous, missing)
    }

    private fun narrow(candidates: List<Track>, entry: ImportedCount): Track? {
        var left = candidates
        entry.artist?.let { artist ->
            val key = key(artist)
            left.filter { it.artist != null && key(it.artist) == key }.takeIf { it.isNotEmpty() }?.let { left = it }
        }
        if (left.size > 1 && entry.durationSeconds != null) {
            left.filter { it.durationMs > 0 && kotlin.math.abs(it.durationMs / 1000 - entry.durationSeconds) <= DURATION_TOLERANCE }
                .takeIf { it.isNotEmpty() }?.let { left = it }
        }
        return left.singleOrNull()
    }

    private fun key(text: String): String = searchKey(text).replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val DURATION_TOLERANCE = 3
        const val SINGLE_TOLERANCE = 10
    }
}

/** Mit tegyen az importálás egy szám számlálójával: csak növel (a nagyobbik érték marad). */
object PlayCountMerge {
    fun apply(matched: Map<Track, Int>, existing: Map<Long, Int>): Map<Track, Int> =
        matched.filter { (track, imported) -> imported > (existing[track.id] ?: 0) }
}
