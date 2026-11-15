package io.github.x1wer.szabadzene.data.library

import java.io.InputStream
import java.nio.charset.Charset

/**
 * Az ID3v2 címke szöveges mezőinek (cím, előadó, album, műfaj) saját olvasója.
 *
 * Miért kell: az Android MediaStore az ID3-címkék kódolását egyes fájloknál „kitalálja”, és a
 * magyar ékezetes neveket (pl. „Árvíztűrő”) kínai írásjelekké torzíthatja. Ilyen gyanús esetben
 * a zenetár a fájlból közvetlenül olvassa ki a szöveget. Csak olvas, a fájlt nem módosítja.
 */
object Id3TextReader {

    data class Tags(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val genre: String? = null,
    )

    private const val MAX_TAG_BYTES = 512 * 1024

    /** Null, ha nincs ID3v2 címke, vagy nem olvasható biztonságosan. */
    fun read(input: InputStream): Tags? {
        val header = input.readUpTo(10)
        if (header.size < 10 || header[0] != 'I'.code.toByte() ||
            header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()
        ) {
            return null
        }
        val major = header[3].toInt()
        if (major !in 2..4) return null
        val flags = header[5].toInt() and 0xFF
        // Az egész címkére kiterjedő „unsynchronisation” ritka; ilyenkor nem kockáztatunk.
        if (flags and 0x80 != 0) return null
        val size = synchsafe(header, 6)
        if (size <= 0) return null
        val body = input.readUpTo(minOf(size, MAX_TAG_BYTES))
        return parseFrames(body, major, flags)
    }

    fun read(bytes: ByteArray): Tags? = read(bytes.inputStream())

    private fun parseFrames(body: ByteArray, major: Int, flags: Int): Tags {
        var pos = 0
        if (major >= 3 && flags and 0x40 != 0 && body.size >= 4) {
            // Bővített fejléc átugrása.
            pos += if (major == 4) synchsafe(body, 0) else int32(body, 0) + 4
        }
        val idLength = if (major == 2) 3 else 4
        val headerLength = if (major == 2) 6 else 10
        var tags = Tags()
        while (pos >= 0 && pos + headerLength <= body.size) {
            if (body[pos].toInt() == 0) break // kitöltés
            val id = String(body, pos, idLength, Charsets.US_ASCII)
            val frameSize = when (major) {
                2 -> int24(body, pos + 3)
                3 -> int32(body, pos + 4)
                else -> synchsafe(body, pos + 4)
            }
            if (frameSize <= 0 || pos + headerLength + frameSize > body.size) break
            var dataStart = pos + headerLength
            var dataLength = frameSize
            var usable = true
            if (major == 3) {
                // Tömörített vagy titkosított keret: kihagyjuk.
                if (body[pos + 9].toInt() and 0xC0 != 0) usable = false
            } else if (major == 4) {
                val format = body[pos + 9].toInt()
                if (format and 0x0E != 0) usable = false
                if (format and 0x01 != 0) {
                    dataStart += 4
                    dataLength -= 4
                }
            }
            if (usable && dataLength > 1) {
                val text = decodeText(body, dataStart, dataLength)
                tags = when (id) {
                    "TIT2", "TT2" -> tags.copy(title = text ?: tags.title)
                    "TPE1", "TP1" -> tags.copy(artist = text ?: tags.artist)
                    "TALB", "TAL" -> tags.copy(album = text ?: tags.album)
                    "TCON", "TCO" -> tags.copy(genre = text ?: tags.genre)
                    else -> tags
                }
            }
            pos += headerLength + frameSize
        }
        return tags
    }

    private fun decodeText(bytes: ByteArray, start: Int, length: Int): String? {
        val charset: Charset = when (bytes[start].toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16 // bájtsorrend-jelzővel
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        val raw = String(bytes, start + 1, length - 1, charset)
        // Több érték esetén (ID3v2.4) az elsőt vesszük.
        return raw.substringBefore('\u0000').trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Gyanús-e a MediaStore szövege: kelet-ázsiai írásjegyet tartalmaz. Ilyenkor érdemes a fájlból
     * újraolvasni; ha a fájlban tényleg ilyen szöveg van, az újraolvasás ugyanazt adja vissza.
     */
    fun looksMisdecoded(vararg values: String?): Boolean = values.any { value ->
        value != null && value.any { ch -> isEastAsian(ch) }
    }

    private fun isEastAsian(ch: Char): Boolean {
        val c = ch.code
        return c in 0x3000..0x9FFF || c in 0xAC00..0xD7AF || c in 0xF900..0xFAFF || c in 0xFF00..0xFFEF
    }

    private fun synchsafe(b: ByteArray, offset: Int): Int {
        if (offset + 4 > b.size) return -1
        return ((b[offset].toInt() and 0x7F) shl 21) or
            ((b[offset + 1].toInt() and 0x7F) shl 14) or
            ((b[offset + 2].toInt() and 0x7F) shl 7) or
            (b[offset + 3].toInt() and 0x7F)
    }

    private fun int32(b: ByteArray, offset: Int): Int {
        if (offset + 4 > b.size) return -1
        return ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)
    }

    private fun int24(b: ByteArray, offset: Int): Int {
        if (offset + 3 > b.size) return -1
        return ((b[offset].toInt() and 0xFF) shl 16) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            (b[offset + 2].toInt() and 0xFF)
    }

    private fun InputStream.readUpTo(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = read(buffer, read, count - read)
            if (n < 0) break
            read += n
        }
        return if (read == count) buffer else buffer.copyOf(read)
    }
}
