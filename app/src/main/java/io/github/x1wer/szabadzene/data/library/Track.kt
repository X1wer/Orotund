package io.github.x1wer.szabadzene.data.library

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/** Egy helyi hangfájl a zenetárban. Az azonosító a MediaStore-azonosító. */
data class Track(
    val id: Long,
    /** A címkéből származó cím; ha hiányzik, a fájlnév kiterjesztés nélkül. */
    val title: String,
    /** Null, ha a fájlban nincs előadó. */
    val artist: String?,
    /** Null, ha a fájlban nincs album. */
    val album: String?,
    val albumId: Long,
    val durationMs: Long,
    val displayName: String,
    /** A fájl teljes útvonala, ha a rendszer megadja. */
    val path: String?,
    /** A közös tárhelyen belüli mappa (pl. "Music/Kedvencek/"), ha a rendszer megadja. */
    val relativePath: String?,
    val sizeBytes: Long,
    val mimeType: String?,
    val dateAddedSeconds: Long,
    val genre: String?,
    val trackNumber: Int,
    val year: Int,
) {
    val contentUri: Uri
        get() = contentUriFor(id)

    companion object {
        fun contentUriFor(id: Long): Uri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

        /** A MediaStore a hiányzó előadót és albumot ilyen helyettesítő szöveggel adja vissza. */
        private val UNKNOWN_MARKERS = setOf("<unknown>", "unknown")

        fun cleanTag(value: String?): String? {
            val trimmed = value?.trim().orEmpty()
            return trimmed.takeIf { it.isNotEmpty() && it.lowercase() !in UNKNOWN_MARKERS }
        }

        /** Cím a címkéből, vagy ha hiányzik, a fájlnév kiterjesztés nélkül. */
        fun titleOrFileName(title: String?, displayName: String): String {
            cleanTag(title)?.let { return it }
            val withoutExtension = displayName.substringBeforeLast('.', displayName)
            return withoutExtension.ifBlank { displayName }
        }
    }
}
