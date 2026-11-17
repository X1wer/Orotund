package io.github.x1wer.szabadzene.data.stats

import android.content.Context
import android.net.Uri
import io.github.x1wer.szabadzene.data.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Az importálás összesítője. A hiányzó és kétértelmű sorokból legfeljebb [SAMPLE] db címe látszik. */
data class PlayCountImportReport(
    val rows: Int,
    val updated: Int,
    val unchanged: Int,
    val ambiguous: Int,
    val missing: Int,
    val missingSample: List<String>,
) {
    companion object {
        const val SAMPLE = 8
    }
}

/**
 * Lejátszásszámok importálása CSV-ből (a rendszer fájlválasztójával kiválasztott fájl; engedély nem kell).
 * Csak növel: ha a SzabadZenében egy számnak már nagyobb a számlálója, az marad. Az „utolsó lejátszás” ideje
 * nem változik, így a „Nemrég játszott” lista sem kerül össze.
 */
class PlayCountImporter(
    private val context: Context,
    private val library: LibraryRepository,
    private val stats: PlayStatsRepository,
) {
    /** Minden legalább egyszer lejátszott, a zenetárban meglévő szám CSV-ként; visszaadja a sorok számát. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val counts = stats.stats.first()
        val rows = library.allTracks.first().mapNotNull { track ->
            val count = counts[track.id]?.playCount ?: 0
            if (count <= 0) null else ImportedCount(count, (track.durationMs / 1000).toInt().takeIf { it > 0 }, track.title, track.artist)
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(PlayCountCsv.write(rows).toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("openOutputStream")
        rows.size
    }

    suspend fun import(uri: Uri): PlayCountImportReport = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > PlayCountCsv.MAX_BYTES) throw IOException("too large")
            }
            out.toByteArray()
        } ?: throw IOException("no stream")
        val entries = PlayCountCsv.parse(String(bytes, Charsets.UTF_8))
        val match = PlayCountMatcher(library.allTracks.first()).match(entries)
        val existing = stats.stats.first().mapValues { it.value.playCount }
        val toWrite = PlayCountMerge.apply(match.matched, existing)
        toWrite.forEach { (track, count) -> stats.setPlayCount(track.id, count) }
        PlayCountImportReport(
            rows = entries.size,
            updated = toWrite.size,
            unchanged = match.matched.size - toWrite.size,
            ambiguous = match.ambiguous.size,
            missing = match.missing.size,
            missingSample = (match.ambiguous + match.missing).take(PlayCountImportReport.SAMPLE).map { it.title },
        )
    }
}
