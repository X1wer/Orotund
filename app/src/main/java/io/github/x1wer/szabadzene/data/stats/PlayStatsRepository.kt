package io.github.x1wer.szabadzene.data.stats

import io.github.x1wer.szabadzene.data.db.AppDatabase
import io.github.x1wer.szabadzene.data.library.LibraryRepository
import io.github.x1wer.szabadzene.data.library.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Egy szám statisztikája: hányszor hallgatták meg, és mikor utoljára (ms, null = még soha). */
data class PlayStats(val playCount: Int, val lastPlayedAt: Long?)

/** Szám a statisztikájával (a „Legtöbbet játszott” és a „Nemrég játszott” listához). */
data class RankedTrack(val track: Track, val playCount: Int, val lastPlayedAt: Long?)

/**
 * Az automatikus listák szabályai (tiszta Kotlin, JVM-en tesztelt). Csak a zenetárban látható
 * számok kerülnek a listákba; a törölt vagy elrejtett számok statisztikája megmarad, de nem látszik.
 */
object SmartLists {
    /** A „Nemrég játszott” lista hossza. A „Legtöbbet játszott” listának nincs korlátja. */
    const val LIMIT = 100

    /** A legtöbbször meghallgatott számok (legalább egyszer, mind); egyenlőségnél a frissebb lejátszás előbb. */
    fun mostPlayed(tracks: List<Track>, stats: Map<Long, PlayStats>, limit: Int = Int.MAX_VALUE): List<RankedTrack> =
        tracks.mapNotNull { track ->
            stats[track.id]?.takeIf { it.playCount > 0 }?.let { RankedTrack(track, it.playCount, it.lastPlayedAt) }
        }.sortedWith(
            compareByDescending<RankedTrack> { it.playCount }
                .thenByDescending { it.lastPlayedAt ?: Long.MIN_VALUE }
                .thenBy { it.track.title.lowercase() },
        ).take(limit)

    /** A legutóbb meghallgatott számok, a legfrissebbel kezdve. */
    fun recentlyPlayed(tracks: List<Track>, stats: Map<Long, PlayStats>, limit: Int = LIMIT): List<RankedTrack> =
        tracks.mapNotNull { track ->
            val s = stats[track.id] ?: return@mapNotNull null
            s.lastPlayedAt?.let { RankedTrack(track, s.playCount, it) }
        }.sortedByDescending { it.lastPlayedAt }.take(limit)
}

/** A lejátszási statisztika (számláló, előzmények) tárolója. */
class PlayStatsRepository(
    database: AppDatabase,
    library: LibraryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.playStatsDao()

    val stats: Flow<Map<Long, PlayStats>> = dao.observeAll()
        .map { list -> list.associate { it.trackId to PlayStats(it.playCount, it.lastPlayedAt) } }
        .distinctUntilChanged()

    val mostPlayed: Flow<List<RankedTrack>> = combine(library.tracks, stats) { tracks, s -> SmartLists.mostPlayed(tracks, s) }

    val recentlyPlayed: Flow<List<RankedTrack>> = combine(library.tracks, stats) { tracks, s -> SmartLists.recentlyPlayed(tracks, s) }

    fun playCount(trackId: Long): Flow<Int> = stats.map { it[trackId]?.playCount ?: 0 }.distinctUntilChanged()

    suspend fun recordPlay(trackId: Long) = dao.recordPlay(trackId, clock())

    suspend fun setPlayCount(trackId: Long, count: Int) = dao.setCount(trackId, count)

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun resetCounts() = dao.resetCounts()
}
