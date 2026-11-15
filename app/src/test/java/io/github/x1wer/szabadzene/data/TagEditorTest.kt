package io.github.x1wer.szabadzene.data

import io.github.x1wer.szabadzene.data.edit.CoverChange
import io.github.x1wer.szabadzene.data.edit.TagEditor
import io.github.x1wer.szabadzene.data.edit.TagFormat
import io.github.x1wer.szabadzene.data.edit.TagValues
import io.github.x1wer.szabadzene.data.edit.TagWriteException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Címkeírás és visszaolvasás a generált tesztfájlokon (`tools/generate_test_audio.py --fixtures`).
 * Minden teszt másolaton dolgozik; a hangadatnak a címkék módosítása után változatlannak kell maradnia.
 */
class TagEditorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val newValues = TagValues(
        title = "Új cím – őű ŐŰ",
        artist = "Szabó Ádám",
        album = "Próba album",
        albumArtist = "Különféle előadók",
        genre = "Magyar népzene",
        year = "2025",
        trackNumber = "7",
    )

    private fun fixture(name: String): File {
        val stream = javaClass.classLoader!!.getResourceAsStream("audio/$name")
            ?: error("Hiányzó tesztfájl: $name – futtasd: python tools/generate_test_audio.py --fixtures")
        val file = File(temp.newFolder(), name)
        stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    /** Egy kicsi, érvényes PNG (1×1 képpont). */
    private val png: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
        0x89.toByte(), 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
        0x42, 0x60, 0x82.toByte(),
    )

    @Test
    fun readsGeneratedTags() {
        for (name in listOf("fx_tagged.mp3", "fx_tagged.m4a", "fx_tagged.flac", "fx_tagged.ogg")) {
            val snapshot = TagEditor.read(fixture(name))
            assertEquals(name, "Árvíztűrő teszt", snapshot.values.title)
            assertEquals(name, "Kovács Éva", snapshot.values.artist)
            assertEquals(name, "Ékezetes dalok", snapshot.values.album)
        }
        assertNotNull(TagEditor.read(fixture("fx_tagged.mp3")).cover)
        assertNotNull(TagEditor.read(fixture("fx_tagged.flac")).cover)
        assertNotNull(TagEditor.read(fixture("fx_tagged.m4a")).cover)
    }

    @Test
    fun writeAndReadBackAllWritableFormats() {
        for (name in listOf("fx_tagged.mp3", "fx_untagged.mp3", "fx_tagged.m4a", "fx_tagged.flac", "fx_tagged.ogg")) {
            val file = fixture(name)
            val cover = CoverChange.Replace(png, "image/png")
            TagEditor.write(file, newValues, cover)
            assertTrue(name, TagEditor.verify(file, newValues, cover))
            val back = TagEditor.read(file)
            assertEquals(name, newValues.title, back.values.title)
            assertEquals(name, newValues.artist, back.values.artist)
            assertEquals(name, newValues.genre, back.values.genre)
            assertArrayEquals(name, png, back.cover)
        }
    }

    @Test
    fun removingCoverAndClearingFields() {
        for (name in listOf("fx_tagged.mp3", "fx_tagged.m4a", "fx_tagged.flac")) {
            val file = fixture(name)
            val values = TagValues(title = "Csak cím")
            TagEditor.write(file, values, CoverChange.Remove)
            val back = TagEditor.read(file)
            assertEquals(name, "Csak cím", back.values.title)
            assertEquals(name, "", back.values.artist)
            assertEquals(name, "", back.values.album)
            assertNull(name, back.cover)
            assertTrue(name, TagEditor.verify(file, values, CoverChange.Remove))
        }
    }

    @Test
    fun audioDataUnchangedAfterTagWrite() {
        // MP3: a hangkeretek a címke után változatlanok maradnak (a fájl vége bájtra azonos).
        val original = fixture("fx_tagged.mp3").readBytes()
        val file = fixture("fx_tagged.mp3")
        TagEditor.write(file, newValues, CoverChange.Keep)
        val edited = file.readBytes()
        val tail = 50_000
        // Az ID3v1 címke (utolsó 128 bájt) változhat; előtte a hangadat azonos.
        val o = original.copyOfRange(original.size - tail - 128, original.size - 128)
        val e = edited.copyOfRange(edited.size - tail - 128, edited.size - 128)
        assertArrayEquals(o, e)
    }

    @Test
    fun verifyDetectsMismatch() {
        val file = fixture("fx_tagged.flac")
        assertFalse(TagEditor.verify(file, newValues, CoverChange.Keep))
        assertFalse(TagEditor.verify(File(temp.root, "nincs.flac"), newValues, CoverChange.Keep))
    }

    @Test(expected = TagWriteException::class)
    fun corruptFileThrows() {
        val file = temp.newFile("serult.mp3")
        file.writeBytes(ByteArray(2_000) { (it * 31).toByte() })
        TagEditor.write(file, newValues, CoverChange.Keep)
    }

    @Test
    fun formatDetection() {
        assertEquals(TagFormat.MP3, TagFormat.of("a.mp3", "audio/mpeg"))
        assertEquals(TagFormat.M4A, TagFormat.of("a.m4a", "audio/mp4"))
        assertEquals(TagFormat.FLAC, TagFormat.of("a.flac", "audio/flac"))
        assertEquals(TagFormat.OGG, TagFormat.of("a.ogg", "audio/ogg"))
        assertNull(TagFormat.of("a.wav", "audio/x-wav"))
        assertNull(TagFormat.of("a.opus", "audio/ogg"))
        assertEquals(TagFormat.FLAC, TagFormat.of("a.flac", null))
    }

    /** Az ID3v2 szövegkeretek kódolásbájtja (keretazonosító → kódolás); v2.4-ben a keretméret szinkronbiztos. */
    private fun id3TextEncodings(file: File): Map<String, Int> {
        val b = file.readBytes()
        require(String(b, 0, 3, Charsets.ISO_8859_1) == "ID3")
        val size = ((b[6].toInt() and 0x7F) shl 21) or ((b[7].toInt() and 0x7F) shl 14) or
            ((b[8].toInt() and 0x7F) shl 7) or (b[9].toInt() and 0x7F)
        val result = HashMap<String, Int>()
        var i = 10
        while (i + 10 <= 10 + size) {
            val id = String(b, i, 4, Charsets.ISO_8859_1)
            if (id[0] == 0.toChar()) break
            val frameSize = if (b[3].toInt() == 4) {
                ((b[i + 4].toInt() and 0x7F) shl 21) or ((b[i + 5].toInt() and 0x7F) shl 14) or
                    ((b[i + 6].toInt() and 0x7F) shl 7) or (b[i + 7].toInt() and 0x7F)
            } else {
                ((b[i + 4].toInt() and 0xFF) shl 24) or ((b[i + 5].toInt() and 0xFF) shl 16) or
                    ((b[i + 6].toInt() and 0xFF) shl 8) or (b[i + 7].toInt() and 0xFF)
            }
            if (id.startsWith("T") && frameSize > 0) result[id] = b[i + 10].toInt()
            i += 10 + frameSize
        }
        return result
    }

    @Test
    fun mp3TextIsWrittenAsUtf8() {
        // Az Android ID3-olvasója a Latin-1 (és a Latin-1 tartományú UTF-16) ékezeteket elronthatja;
        // ezért ID3v2.4 és minden szövegkeret UTF-8 (3-as kód).
        for (name in listOf("fx_tagged.mp3", "fx_untagged.mp3")) {
            val file = fixture(name)
            TagEditor.write(file, newValues.copy(artist = "Árvai Ödön", album = "ASCII album"), CoverChange.Keep)
            assertEquals(name, 4, file.readBytes()[3].toInt())
            val encodings = id3TextEncodings(file)
            assertEquals(name, 3, encodings["TPE1"])
            assertEquals(name, 3, encodings["TALB"])
            assertEquals(name, 3, encodings["TIT2"])
        }
    }
}
