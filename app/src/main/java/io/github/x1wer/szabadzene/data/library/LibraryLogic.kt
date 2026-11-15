package io.github.x1wer.szabadzene.data.library

import io.github.x1wer.szabadzene.data.settings.LibraryFilter
import io.github.x1wer.szabadzene.data.settings.LibrarySort
import io.github.x1wer.szabadzene.data.settings.SortField
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/*
 * A zenetár tiszta logikája: keresés, rendezés, csoportosítás és szűrés.
 * Nincs Android-függősége, ezért JVM-en tesztelhető.
 */

/** Magyar ábécé szerinti összehasonlító (kis- és nagybetűt nem különböztet meg). */
fun hungarianCollator(): Collator = Collator.getInstance(Locale.forLanguageTag("hu-HU")).apply {
    strength = Collator.SECONDARY
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * Keresőkulcs: kisbetűs, ékezet nélküli alak. Így „arvizturo” megtalálja az „Árvíztűrő”-t,
 * és „ÁRVÍZTŰRŐ” az „arvizturo”-t (az ő és ű is o-ra és u-ra egyszerűsödik).
 */
fun searchKey(text: String): String =
    COMBINING_MARKS.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")

/** Előre kiszámolt keresőkulcsok, hogy gépelés közben ne kelljen minden számot újra normalizálni. */
class TrackSearchIndex(val tracks: List<Track>) {
    private val keys: List<String> = tracks.map { track ->
        searchKey(
            listOfNotNull(track.title, track.artist, track.album, track.displayName).joinToString(" \u0000 "),
        )
    }

    /** Minden szónak szerepelnie kell a címben, az előadóban, az albumban vagy a fájlnévben. */
    fun search(query: String): List<Track> {
        val words = searchKey(query).split(' ', '\t').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return tracks.filterIndexed { index, _ -> words.all { keys[index].contains(it) } }
    }
}

/** Rendezés a választott mező szerint. Ismeretlen előadó a végére kerül. */
fun sortTracks(tracks: List<Track>, sort: LibrarySort, collator: Collator = hungarianCollator()): List<Track> {
    val byTitle = Comparator<Track> { a, b -> collator.compare(a.title, b.title) }
    val comparator: Comparator<Track> = when (sort.field) {
        SortField.TITLE -> byTitle
        SortField.ARTIST -> Comparator<Track> { a, b ->
            when {
                a.artist == null && b.artist == null -> 0
                a.artist == null -> 1
                b.artist == null -> -1
                else -> collator.compare(a.artist, b.artist)
            }
        }.then(byTitle)
        SortField.DURATION -> compareBy<Track> { it.durationMs }.then(byTitle)
        SortField.DATE_ADDED -> compareBy<Track> { it.dateAddedSeconds }.then(byTitle)
    }
    return tracks.sortedWith(if (sort.descending) comparator.reversed() else comparator)
}

/** A szám mappája: a teljes útvonal szülőkönyvtára, vagy ennek hiányában a relatív útvonal. */
val Track.folderPath: String
    get() = path?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }
        ?: relativePath?.trimEnd('/')
        ?: ""

/** Kizárt mappák (és almappáik) és a túl rövid fájlok elrejtése. */
fun applyFilter(tracks: List<Track>, filter: LibraryFilter): List<Track> {
    val minMs = filter.minDurationSeconds * 1000L
    val excluded = filter.excludedFolders.map { it.trimEnd('/') }
    return tracks.filter { track ->
        val folder = track.folderPath
        val inExcluded = excluded.any { folder == it || folder.startsWith("$it/") }
        !inExcluded && (minMs <= 0 || track.durationMs >= minMs)
    }
}

/** Számok gyűjteménye (album, előadó, műfaj vagy mappa). */
data class TrackGroup(
    /** Stabil azonosító a navigációhoz (album-azonosító, név vagy útvonal). */
    val key: String,
    /** Megjelenített név; null, ha ismeretlen (pl. előadó nélküli számok). */
    val name: String?,
    /** Másodlagos szöveg (albumnál az előadó, mappánál a teljes útvonal). */
    val detail: String?,
    val tracks: List<Track>,
) {
    /** A borítóhoz használt szám (az első, amelyiknek lehet borítója). */
    val artworkTrackId: Long? get() = tracks.firstOrNull()?.id
}

private fun <T> List<T>.mostCommonOrNull(): T? = groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

private fun sortGroups(groups: List<TrackGroup>, collator: Collator): List<TrackGroup> =
    groups.sortedWith { a, b ->
        when {
            a.name == null && b.name == null -> 0
            a.name == null -> 1
            b.name == null -> -1
            else -> collator.compare(a.name, b.name)
        }
    }

/**
 * Albumkulcs: az albumnév (kisbetűsen) és a mappa. A MediaStore album-azonosítójára nem
 * támaszkodunk, mert az az (esetleg elrontott kódolású) eredeti címkéből számolódik, így
 * ugyanaz az album több azonosítót kaphat. Albumnév nélkül marad a MediaStore-azonosító.
 */
private fun albumKey(track: Track): String =
    track.album?.let { "${it.lowercase(Locale.ROOT)}|${track.folderPath}" } ?: "id:${track.albumId}"

/** Albumok név és mappa szerint; a számok sorszám, majd cím szerint. */
fun groupAlbums(tracks: List<Track>, collator: Collator = hungarianCollator()): List<TrackGroup> =
    sortGroups(
        tracks.groupBy { albumKey(it) }.map { (key, list) ->
            val sorted = list.sortedWith(compareBy<Track> { it.trackNumber }.thenComparator { a, b -> collator.compare(a.title, b.title) })
            TrackGroup(
                key = key,
                name = sorted.firstNotNullOfOrNull { it.album },
                detail = sorted.mapNotNull { it.artist }.mostCommonOrNull(),
                tracks = sorted,
            )
        },
        collator,
    )

/** Előadók név szerint (kis- és nagybetűtől függetlenül egy csoport). */
fun groupArtists(tracks: List<Track>, collator: Collator = hungarianCollator()): List<TrackGroup> =
    groupByName(tracks, collator) { it.artist }

/** Műfajok név szerint. */
fun groupGenres(tracks: List<Track>, collator: Collator = hungarianCollator()): List<TrackGroup> =
    groupByName(tracks, collator) { it.genre }

private fun groupByName(tracks: List<Track>, collator: Collator, name: (Track) -> String?): List<TrackGroup> =
    sortGroups(
        tracks.groupBy { name(it)?.lowercase(Locale.ROOT) }.map { (key, list) ->
            val sorted = list.sortedWith { a, b -> collator.compare(a.title, b.title) }
            TrackGroup(
                key = key ?: UNKNOWN_KEY,
                name = sorted.firstNotNullOfOrNull(name),
                detail = null,
                tracks = sorted,
            )
        },
        collator,
    )

/** Mappák a szülőkönyvtár szerint; a megjelenített név a mappa neve, a részlet a teljes útvonal. */
fun groupFolders(tracks: List<Track>, collator: Collator = hungarianCollator()): List<TrackGroup> =
    sortGroups(
        tracks.groupBy { it.folderPath }.map { (folder, list) ->
            TrackGroup(
                key = folder,
                name = folder.substringAfterLast('/').ifEmpty { folder.ifEmpty { null } },
                detail = folder.ifEmpty { null },
                tracks = list.sortedWith { a, b -> collator.compare(a.displayName, b.displayName) },
            )
        },
        collator,
    )

/** A névtelen (ismeretlen előadójú vagy műfajú) csoport kulcsa. */
const val UNKNOWN_KEY = "\u0000unknown"
