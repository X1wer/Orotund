package io.github.x1wer.szabadzene.data.playlists

import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.library.searchKey
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/** Egy listafájl egy bejegyzése: a hely, ahogy a fájlban áll, és a leíró adatok, ha vannak. */
data class PlaylistFileEntry(
    val location: String,
    val title: String? = null,
    val durationSeconds: Int? = null,
)

/** Egy beolvasott listafájl: a lista neve és a bejegyzései a fájlbeli sorrendben. */
data class ParsedPlaylist(val name: String, val entries: List<PlaylistFileEntry>)

/** A támogatott listafájl-formátumok. */
enum class PlaylistFormat {
    M3U,
    PLS,
    ;

    companion object {
        val EXTENSIONS = setOf("m3u", "m3u8", "pls")

        fun forFileName(name: String): PlaylistFormat? = when (name.substringAfterLast('.', "").lowercase()) {
            "m3u", "m3u8" -> M3U
            "pls" -> PLS
            else -> null
        }

        fun isPlaylistFile(name: String): Boolean = forFileName(name) != null
    }
}

/** M3U/M3U8 (kiterjesztett is) és PLS listafájlok olvasása. Tiszta Kotlin, unit-tesztelt. */
object PlaylistFileParser {
    /** A beolvasható fájl legnagyobb mérete; egy lista ennél jóval kisebb. */
    const val MAX_BYTES = 2 * 1024 * 1024

    private val PLS_LINE = Regex("""^(File|Title|Length)(\d+)\s*=\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(text: String, fileName: String): ParsedPlaylist {
        val name = fileName.substringBeforeLast('.').ifBlank { fileName }
        val clean = text.removePrefix("\uFEFF")
        val format = PlaylistFormat.forFileName(fileName)
            ?: if (clean.trimStart().startsWith("[playlist]", ignoreCase = true)) PlaylistFormat.PLS else PlaylistFormat.M3U
        return when (format) {
            PlaylistFormat.M3U -> parseM3u(clean, name)
            PlaylistFormat.PLS -> parsePls(clean, name)
        }
    }

    private fun parseM3u(text: String, defaultName: String): ParsedPlaylist {
        var name = defaultName
        var title: String? = null
        var duration: Int? = null
        val entries = mutableListOf<PlaylistFileEntry>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    // #EXTINF:<mp>[ attribútumok],<előadó - cím>
                    val body = line.substring("#EXTINF:".length)
                    val comma = body.indexOf(',')
                    val head = (if (comma >= 0) body.substring(0, comma) else body).trim().substringBefore(' ')
                    duration = head.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
                    title = if (comma >= 0) body.substring(comma + 1).trim().ifEmpty { null } else null
                }
                line.startsWith("#PLAYLIST:", ignoreCase = true) ->
                    line.substring("#PLAYLIST:".length).trim().takeIf { it.isNotEmpty() }?.let { name = it }
                line.startsWith("#") -> Unit
                else -> {
                    entries += PlaylistFileEntry(line, title, duration)
                    title = null
                    duration = null
                }
            }
        }
        return ParsedPlaylist(name, entries)
    }

    private fun parsePls(text: String, defaultName: String): ParsedPlaylist {
        val files = sortedMapOf<Int, String>()
        val titles = mutableMapOf<Int, String>()
        val lengths = mutableMapOf<Int, Int>()
        text.lineSequence().forEach { raw ->
            val match = PLS_LINE.matchEntire(raw.trim()) ?: return@forEach
            val index = match.groupValues[2].toIntOrNull() ?: return@forEach
            val value = match.groupValues[3].trim()
            when (match.groupValues[1].lowercase()) {
                "file" -> if (value.isNotEmpty()) files[index] = value
                "title" -> if (value.isNotEmpty()) titles[index] = value
                "length" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { lengths[index] = it }
            }
        }
        return ParsedPlaylist(defaultName, files.map { (index, file) -> PlaylistFileEntry(file, titles[index], lengths[index]) })
    }

    /**
     * A fájl szövege: UTF-8, ha érvényes UTF-8 (az .m3u8 mindig az), különben a régi Windows-os
     * listák közép-európai kódlapja (windows-1250).
     */
    fun decode(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1250"))
        }
    }
}

/** Útvonalak egységesítése a listafájl bejegyzéseihez. */
object PlaylistPaths {
    private val STORAGE_PREFIX = Regex(
        """^/(?:storage/emulated/\d+|storage/self/primary|sdcard|mnt/sdcard|storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}|mnt/media_rw/[^/]+)/""",
        RegexOption.IGNORE_CASE,
    )
    private val DRIVE = Regex("""^[A-Za-z]:/""")
    private val REMOTE = Regex("""^[a-z][a-z0-9+.-]*://""", RegexOption.IGNORE_CASE)

    /** Hálózati cím (pl. http://) – ilyen bejegyzést nem lehet helyi számmal párosítani. */
    fun isRemote(location: String): Boolean {
        val trimmed = location.trim()
        return REMOTE.containsMatchIn(trimmed) && !trimmed.startsWith("file:", ignoreCase = true)
    }

    /** Perjelek, `file://` dekódolva, Unicode NFC; null, ha üres vagy hálózati cím. */
    fun clean(location: String): String? {
        var path = location.trim()
        if (path.isEmpty() || isRemote(path)) return null
        if (path.startsWith("file:", ignoreCase = true)) {
            path = path.substring("file:".length).trimStart('/').let { "/$it" }
            path = runCatching { URLDecoder.decode(path.replace("+", "%2B"), "UTF-8") }.getOrDefault(path)
            // file:///C:/Zene/a.mp3 → C:/Zene/a.mp3
            if (DRIVE.containsMatchIn(path.removePrefix("/"))) path = path.removePrefix("/")
        }
        path = path.replace('\\', '/')
        return Normalizer.normalize(path, Normalizer.Form.NFC)
    }

    fun isAbsolute(path: String): Boolean = path.startsWith("/") || DRIVE.containsMatchIn(path)

    /** Teljes Android-útvonalból a tárhelyen belüli rész („Music/Album/a.mp3”), vagy null. */
    fun storageRelative(absolutePath: String): String? {
        val path = absolutePath.replace('\\', '/')
        val match = STORAGE_PREFIX.find(path) ?: return null
        return path.substring(match.range.last + 1)
    }

    /** A „./” és „../” elemek feloldása egy tárhelyen belüli útvonalban. */
    fun collapse(path: String): String {
        val out = ArrayDeque<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> out.removeLastOrNull()
                else -> out.addLast(part)
            }
        }
        return out.joinToString("/")
    }

    fun segments(path: String): List<String> = path.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
}

/** Egy lista párosításának eredménye: a megtalált számok a fájlbeli sorrendben, és a bejegyzések száma. */
data class PlaylistMatch(val trackIds: List<Long>, val total: Int) {
    val found: Int get() = trackIds.size
}

/**
 * A listafájl bejegyzéseit a zenetár számaival párosítja. Sorrendben próbálja:
 * 1. teljes útvonal; 2. a lista mappájához képest megadott (relatív) útvonal; 3. az útvonal utolsó
 * 4…1 eleme (más gépen készült listához), csak ha egyértelmű, és ha a bejegyzés hossza ismert, az is
 * egyezik; 4. a bejegyzés címe („Előadó - Cím” vagy „Cím”), ha egyértelmű. A kis- és nagybetű nem számít.
 */
class PlaylistMatcher(tracks: List<Track>) {
    private val byAbsolute = HashMap<String, Track>()
    private val bySuffix = HashMap<String, MutableList<Track>>()
    private val byTitle = HashMap<String, MutableList<Track>>()

    init {
        tracks.forEach { track ->
            track.path?.let { byAbsolute.putIfAbsent(key(it), track) }
            val parts = PlaylistPaths.segments(relativeKeyOf(track))
            for (count in 1..minOf(MAX_SUFFIX, parts.size)) {
                bySuffix.getOrPut(key(parts.takeLast(count).joinToString("/"))) { mutableListOf() }.add(track)
            }
            byTitle.getOrPut(titleKey(track.title)) { mutableListOf() }.add(track)
            track.artist?.let { artist ->
                byTitle.getOrPut(titleKey("$artist - ${track.title}")) { mutableListOf() }.add(track)
            }
        }
    }

    /** [playlistDir]: a listafájl mappája a tárhelyen belül (pl. „Music/Listák/”), ha ismert. */
    fun match(playlist: ParsedPlaylist, playlistDir: String?): PlaylistMatch {
        val ids = playlist.entries.mapNotNull { find(it, playlistDir)?.id }
        return PlaylistMatch(ids, playlist.entries.size)
    }

    fun find(entry: PlaylistFileEntry, playlistDir: String?): Track? {
        val path = PlaylistPaths.clean(entry.location)
        if (path != null) {
            if (PlaylistPaths.isAbsolute(path)) {
                byAbsolute[key(path)]?.let { return it }
                PlaylistPaths.storageRelative(path)?.let { relative -> exact(relative)?.let { return it } }
            } else if (playlistDir != null) {
                exact(PlaylistPaths.collapse("$playlistDir/$path"))?.let { return it }
            }
            bySuffixMatch(path)?.takeIf { durationFits(entry, it) }?.let { return it }
        }
        return entry.title?.let { title -> byTitle[titleKey(title)]?.singleOrNull() }
    }

    /** A tárhelyen belüli teljes útvonal pontos egyezése. */
    private fun exact(relative: String): Track? {
        val parts = PlaylistPaths.segments(relative)
        if (parts.isEmpty()) return null
        val candidates = bySuffix[key(parts.takeLast(MAX_SUFFIX).joinToString("/"))] ?: return null
        val wanted = key(parts.joinToString("/"))
        return candidates.singleOrNull { key(relativeKeyOf(it)) == wanted }
    }

    /** A leghosszabb egyező útvonalvég; több azonos végű fájlnál nincs találat (rövidebb véggel sem). */
    private fun bySuffixMatch(path: String): Track? {
        val parts = PlaylistPaths.segments(path)
        for (count in minOf(MAX_SUFFIX, parts.size) downTo 1) {
            val candidates = bySuffix[key(parts.takeLast(count).joinToString("/"))] ?: continue
            return candidates.singleOrNull()
        }
        return null
    }

    private fun durationFits(entry: PlaylistFileEntry, track: Track): Boolean {
        val seconds = entry.durationSeconds ?: return true
        if (track.durationMs <= 0) return true
        return kotlin.math.abs(track.durationMs / 1000 - seconds) <= DURATION_TOLERANCE_SECONDS
    }

    companion object {
        private const val MAX_SUFFIX = 4
        private const val DURATION_TOLERANCE_SECONDS = 3
        private val SPACES = Regex("""\s+""")

        private fun key(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC).lowercase()

        private fun titleKey(value: String): String = searchKey(value).trim().replace(SPACES, " ")

        /** A szám tárhelyen belüli útvonala („Music/Album/a.mp3”); ha nem ismert, a fájlnév. */
        fun relativeKeyOf(track: Track): String =
            track.relativePath?.let { it.trimEnd('/') + "/" + track.displayName }
                ?: track.path?.let { PlaylistPaths.storageRelative(it) ?: it.trimStart('/') }
                ?: track.displayName
    }
}

/** A lista neve a már létezők mellett: azonos névnél „Név (2)”, „Név (3)”… */
fun uniquePlaylistName(name: String, existing: Collection<String>): String {
    val taken = existing.map { it.trim().lowercase() }.toSet()
    val base = name.trim().ifEmpty { "Lista" }
    if (base.lowercase() !in taken) return base
    var index = 2
    while ("$base ($index)".lowercase() in taken) index++
    return "$base ($index)"
}
