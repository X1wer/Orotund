package io.github.x1wer.szabadzene.data.playlists

import io.github.x1wer.szabadzene.data.library.Track

/*
 * Listák exportálásának tiszta logikája (Android-függőség nélkül, JVM-en tesztelhető):
 * fájlnév, a számok útvonala a célmappához képest, és a kiterjesztett M3U (M3U8) szöveg.
 */

/** Egy hely a telefon tárhelyén: a tárhely azonosítója („primary” vagy pl. „1A2B-3C4D”) és azon belüli útvonal. */
data class StorageLocation(val volume: String, val path: String) {
    companion object {
        const val PRIMARY = "primary"
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private val PREFIX = Regex(
            """^/(?:(storage/emulated/\d+|storage/self/primary|sdcard|mnt/sdcard)|storage/([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}))(?:/|$)""",
            RegexOption.IGNORE_CASE,
        )

        /** Teljes Android-útvonalból: „/storage/emulated/0/Music/a.mp3” → (primary, „Music/a.mp3”). */
        fun fromAbsolutePath(absolutePath: String?): StorageLocation? {
            val path = absolutePath?.replace('\\', '/') ?: return null
            val match = PREFIX.find(path) ?: return null
            val volume = match.groupValues[2].takeIf { it.isNotEmpty() }?.uppercase() ?: PRIMARY
            return StorageLocation(volume, path.substring(match.range.last + 1).trim('/'))
        }

        /**
         * Egy mappa (a rendszer fájlválasztójából) dokumentumazonosítójából: „primary:Music/Listák” →
         * (primary, „Music/Listák”); „raw:/storage/…” → a teljes útvonal szerint. Más szolgáltatónál null.
         */
        fun fromDocumentId(authority: String?, documentId: String): StorageLocation? = when {
            documentId.startsWith("raw:") -> fromAbsolutePath(documentId.removePrefix("raw:"))
            authority == EXTERNAL_STORAGE_AUTHORITY && documentId.contains(':') -> {
                val volume = documentId.substringBefore(':')
                StorageLocation(
                    volume = if (volume.equals(PRIMARY, ignoreCase = true)) PRIMARY else volume.uppercase(),
                    path = documentId.substringAfter(':').trim('/'),
                )
            }
            else -> null
        }
    }
}

object PlaylistFileWriter {
    const val EXTENSION = "m3u8"
    private const val MAX_NAME_LENGTH = 100
    private const val FORBIDDEN = "\\/:*?\"<>|"

    /** Fájlnév a lista nevéből: a fájlnévben tiltott karakterek helyett „_”, legfeljebb 100 karakter. */
    fun fileNameFor(playlistName: String): String {
        val cleaned = playlistName
            .map { c -> if (c in FORBIDDEN || c.code < 32) '_' else c }
            .joinToString("")
            .trim()
            .trim('.')
            .take(MAX_NAME_LENGTH)
            .trim()
        return cleaned.ifEmpty { "Playlist" } + ".$EXTENSION"
    }

    /** Ha a név már foglalt (kis- és nagybetű nem számít): „Név (2).m3u8”, „Név (3).m3u8” … */
    fun uniqueFileName(fileName: String, taken: Collection<String>): String {
        val lower = taken.map { it.lowercase() }.toSet()
        if (fileName.lowercase() !in lower) return fileName
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        var n = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
            if (candidate.lowercase() !in lower) return candidate
            n++
        }
    }

    /**
     * Útvonal a [fromDir] mappából a [to] fájlhoz, ugyanazon a tárhelyen belül: „Music/Listák” és
     * „Music/Album/a.mp3” → „../Album/a.mp3”.
     */
    fun relativePath(fromDir: String, to: String): String {
        val from = PlaylistPaths.segments(fromDir)
        val target = PlaylistPaths.segments(to)
        var common = 0
        while (common < from.size && common < target.size - 1 && from[common].equals(target[common], ignoreCase = true)) common++
        val up = List(from.size - common) { ".." }
        return (up + target.drop(common)).joinToString("/")
    }

    /**
     * A szám helye a listafájlban. Ha a célmappa és a szám ugyanazon a tárhelyen van, a mappához képest
     * relatív (így a lista a zenével együtt másolva, más lejátszóban is működik); különben a teljes útvonal.
     */
    fun locationFor(track: Track, target: StorageLocation?): String {
        val trackLocation = StorageLocation.fromAbsolutePath(track.path)
        if (target != null && trackLocation != null && trackLocation.volume == target.volume) {
            return relativePath(target.path, trackLocation.path)
        }
        return track.path
            ?: track.relativePath?.let { it.trimEnd('/') + "/" + track.displayName }
            ?: track.displayName
    }

    /** Kiterjesztett M3U, UTF-8 kódolásra szánt szöveg (sorvég: \n). */
    fun m3u8(playlistName: String, tracks: List<Track>, target: StorageLocation?): String = buildString {
        append("#EXTM3U\n")
        append("#PLAYLIST:").append(singleLine(playlistName)).append('\n')
        tracks.forEach { track ->
            val seconds = if (track.durationMs > 0) (track.durationMs + 500) / 1000 else -1
            val label = track.artist?.let { "$it - ${track.title}" } ?: track.title
            append("#EXTINF:").append(seconds).append(',').append(singleLine(label)).append('\n')
            append(locationFor(track, target)).append('\n')
        }
    }

    private fun singleLine(text: String): String = text.replace('\r', ' ').replace('\n', ' ').trim()
}
