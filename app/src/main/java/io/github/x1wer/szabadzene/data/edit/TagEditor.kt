package io.github.x1wer.szabadzene.data.edit

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.valuepair.ImageFormats
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.reference.ID3V2Version
import org.jaudiotagger.tag.reference.PictureTypes
import org.jaudiotagger.tag.id3.valuepair.TextEncoding
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A szerkeszthető címkék. Üres szöveg = a mező törlése. A dalszöveg ([lyrics]) null értéke azt
 * jelenti, hogy a mentés nem nyúl hozzá (a címkeszerkesztő így menti a többi mezőt).
 */
data class TagValues(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val lyrics: String? = null,
) {
    /** Szóközök levágása, hogy az összehasonlítás és a mentés ugyanazt lássa. */
    fun normalized(): TagValues = TagValues(
        title = title.trim(),
        artist = artist.trim(),
        album = album.trim(),
        albumArtist = albumArtist.trim(),
        genre = genre.trim(),
        year = year.trim(),
        trackNumber = trackNumber.trim(),
        lyrics = lyrics?.let(::normalizeLyrics),
    )

    companion object {
        /** Egységes sorvége, a sorok végéről és a szöveg két végéről a felesleges szóköz le. */
        fun normalizeLyrics(text: String): String =
            text.lines().joinToString("\n") { it.trimEnd() }.trim()
    }
}

/** Mi történjen a borítóval mentéskor. */
sealed interface CoverChange {
    data object Keep : CoverChange
    data object Remove : CoverChange

    /** Új kép: JPEG vagy PNG bájtok. */
    class Replace(val bytes: ByteArray, val mimeType: String) : CoverChange
}

/** Egy fájl címkéi és beágyazott borítója. */
class TagSnapshot(val values: TagValues, val cover: ByteArray?)

/** A címkeírás formátumai. Csak ezek írhatók: a többit a felület nem kínálja fel. */
enum class TagFormat(val extension: String) {
    MP3("mp3"),
    M4A("m4a"),
    FLAC("flac"),
    OGG("ogg"),
    ;

    companion object {
        /**
         * A fájl formátuma a MIME-típus vagy a kiterjesztés alapján; null, ha nem írható.
         * WAV: a JAudioTagger írná, de az Android médiatára nem olvassa vissza a címkéit,
         * ezért nem kínáljuk fel (lásd ALLAPOT.md).
         */
        fun of(displayName: String?, mimeType: String?): TagFormat? {
            when (mimeType?.lowercase()) {
                "audio/mpeg", "audio/mp3" -> return MP3
                "audio/mp4", "audio/m4a", "audio/x-m4a" -> return M4A
                "audio/flac", "audio/x-flac" -> return FLAC
                "audio/ogg", "application/ogg", "audio/vorbis" -> {
                    // Opus is ogg-tárolóban lehet; a kiterjesztés dönt.
                    return if (displayName?.substringAfterLast('.', "")?.lowercase() == "opus") null else OGG
                }
            }
            return when (displayName?.substringAfterLast('.', "")?.lowercase()) {
                "mp3" -> MP3
                "m4a", "mp4" -> M4A
                "flac" -> FLAC
                "ogg", "oga" -> OGG
                else -> null
            }
        }
    }
}

/** A címkeírás hibája: a felület ennek alapján ad érthető üzenetet. */
class TagWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Címkék és borító olvasása és írása helyi (gyorsítótárbeli) fájlon a JAudioTagger könyvtárral.
 * A fájl kiterjesztésének a formátumhoz kell illenie. Az Android-oldali folyamat
 * (másolat, ellenőrzés, visszaírás) a [TrackFileEditor]-ban van.
 */
object TagEditor {

    init {
        // A könyvtár nagyon bőbeszédű; hibát kivételként kapunk, a naplóra nincs szükség.
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
        TagOptionSingleton.getInstance().apply {
            // ID3v2.4, UTF-8 szöveggel. Az Android ID3-olvasója a Latin-1-et, sőt a csak Latin-1
            // tartományba eső UTF-16 szöveget is „helyi kódolásúnak” veheti, és a magyar ékezetekből
            // (Á, Ö, É…) kínai írásjelek lesznek. A UTF-8 (csak v2.4-ben létezik) ezt elkerüli.
            setID3V2Version(ID3V2Version.ID3_V24)
            id3v24DefaultTextEncoding = TextEncoding.UTF_8
            id3v24UnicodeTextEncoding = TextEncoding.UTF_8
            isResetTextEncodingForExistingFrames = true
            isWriteMp3GenresAsText = true
            isWriteMp4GenresAsText = true
            isAndroid = true
        }
    }

    fun read(file: File): TagSnapshot {
        val audio = readAudio(file)
        val tag = audio.tag
        val values = if (tag == null) {
            TagValues()
        } else {
            TagValues(
                title = tag.first(FieldKey.TITLE),
                artist = tag.first(FieldKey.ARTIST),
                album = tag.first(FieldKey.ALBUM),
                albumArtist = tag.first(FieldKey.ALBUM_ARTIST),
                genre = tag.first(FieldKey.GENRE),
                year = tag.first(FieldKey.YEAR),
                trackNumber = tag.first(FieldKey.TRACK),
                lyrics = TagValues.normalizeLyrics(tag.first(FieldKey.LYRICS)),
            )
        }
        val cover = tag?.firstArtwork?.binaryData?.takeIf { it.isNotEmpty() }
        return TagSnapshot(values, cover)
    }

    /** A címkék írása a fájlba (helyben). Hiba esetén kivételt dob. */
    fun write(file: File, values: TagValues, cover: CoverChange) {
        val normalized = values.normalized()
        try {
            val audio = readAudio(file)
            if (audio is MP3File && audio.hasID3v2Tag() && audio.iD3v2Tag !is ID3v24Tag) {
                // A meglévő v2.2/v2.3 címke átalakítása v2.4-re (a keretek megmaradnak), hogy UTF-8 lehessen.
                audio.iD3v2Tag = ID3v24Tag(audio.iD3v2Tag)
            }
            val tag = audio.tagOrCreateAndSetDefault
            tag.put(FieldKey.TITLE, normalized.title)
            tag.put(FieldKey.ARTIST, normalized.artist)
            tag.put(FieldKey.ALBUM, normalized.album)
            tag.put(FieldKey.ALBUM_ARTIST, normalized.albumArtist)
            tag.put(FieldKey.GENRE, normalized.genre)
            tag.put(FieldKey.YEAR, normalized.year)
            tag.put(FieldKey.TRACK, normalized.trackNumber)
            normalized.lyrics?.let { tag.put(FieldKey.LYRICS, it) }
            when (cover) {
                CoverChange.Keep -> Unit
                CoverChange.Remove -> tag.deleteArtworkField()
                is CoverChange.Replace -> {
                    tag.deleteArtworkField()
                    val size = ImageSize.of(cover.bytes)
                        ?: throw TagWriteException("A kép formátuma nem támogatott (JPEG vagy PNG kell)")
                    val artwork = SizedArtwork().apply {
                        binaryData = cover.bytes
                        mimeType = cover.mimeType.ifBlank { ImageFormats.getMimeTypeForBinarySignature(cover.bytes) }
                        pictureType = PictureTypes.DEFAULT_ID
                        description = ""
                        width = size.first
                        height = size.second
                    }
                    tag.setField(artwork)
                }
            }
            AudioFileIO.write(audio)
        } catch (e: TagWriteException) {
            throw e
        } catch (e: Exception) {
            throw TagWriteException("A címkék írása nem sikerült: ${e.message}", e)
        } catch (e: LinkageError) {
            // A könyvtár egyes ágai Androidon nem elérhető Java-osztályt használhatnak.
            throw TagWriteException("A címkék írása ezen a készüléken nem támogatott", e)
        }
    }

    /**
     * Visszaolvasással ellenőrzi, hogy a fájlban valóban a kért értékek vannak-e.
     * Az évnél és a sorszámnál a formátum saját alakját is elfogadja (pl. „3” = „3/12” első tagja).
     */
    fun verify(file: File, expected: TagValues, cover: CoverChange): Boolean {
        val snapshot = runCatching { read(file) }.getOrNull() ?: return false
        return matches(snapshot, expected, cover)
    }

    fun matches(snapshot: TagSnapshot, expected: TagValues, cover: CoverChange): Boolean {
        val want = expected.normalized()
        val got = snapshot.values
        val textOk = got.title == want.title &&
            got.artist == want.artist &&
            got.album == want.album &&
            got.albumArtist == want.albumArtist &&
            got.genre == want.genre &&
            sameNumber(got.trackNumber, want.trackNumber) &&
            sameYear(got.year, want.year) &&
            (want.lyrics == null || TagValues.normalizeLyrics(got.lyrics.orEmpty()) == want.lyrics)
        val coverOk = when (cover) {
            CoverChange.Keep -> true
            CoverChange.Remove -> snapshot.cover == null
            is CoverChange.Replace -> snapshot.cover?.contentEquals(cover.bytes) == true
        }
        return textOk && coverOk
    }

    private fun sameNumber(got: String, want: String): Boolean =
        got.substringBefore('/').trim().trimStart('0') == want.substringBefore('/').trim().trimStart('0')

    private fun sameYear(got: String, want: String): Boolean =
        got == want || (want.length == 4 && got.startsWith(want))

    private fun readAudio(file: File): AudioFile = try {
        AudioFileIO.read(file)
    } catch (e: Exception) {
        throw TagWriteException("A fájl nem olvasható: ${e.message}", e)
    }

    private fun Tag.first(key: FieldKey): String = runCatching { getFirst(key) }.getOrNull().orEmpty().trim()

    /** Üres értéknél a mező törlődik, különben beállítódik. */
    private fun Tag.put(key: FieldKey, value: String) {
        if (value.isEmpty()) {
            runCatching { deleteField(key) }
        } else {
            setField(key, value)
        }
    }
}

/**
 * A könyvtár Android-ágán a kép méretét nem tudja magától kiolvasni (FLAC és Vorbis képblokkhoz kell);
 * a méretet mi adjuk meg a kép fejlécéből.
 */
private class SizedArtwork : AndroidArtwork() {
    override fun setImageFromData(): Boolean = true
}

/** JPEG és PNG kép mérete (szélesség, magasság) a fejlécből; null, ha nem ismerhető fel. */
object ImageSize {
    fun of(bytes: ByteArray): Pair<Int, Int>? = png(bytes) ?: jpeg(bytes)

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun u16(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)
    private fun u32(b: ByteArray, i: Int) = (u16(b, i) shl 16) or u16(b, i + 2)

    private fun png(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 24 || u8(b, 0) != 0x89 || b[1] != 'P'.code.toByte() || b[2] != 'N'.code.toByte()) return null
        return u32(b, 16) to u32(b, 20)
    }

    private fun jpeg(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8) return null
        var i = 2
        while (i + 9 < b.size) {
            if (u8(b, i) != 0xFF) return null
            val marker = u8(b, i + 1)
            if (marker == 0xFF) {
                i++
                continue
            }
            val length = u16(b, i + 2)
            // SOF0–SOF15, kivéve a DHT (C4), JPG (C8) és DAC (CC) jelölőket.
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                return u16(b, i + 7) to u16(b, i + 5)
            }
            i += 2 + length
        }
        return null
    }
}
