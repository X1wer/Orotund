package io.github.x1wer.szabadzene.data

import io.github.x1wer.szabadzene.data.library.Id3TextReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class Id3TextReaderTest {

    private fun synchsafe(value: Int) = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private fun int32(value: Int) = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte(),
    )

    /** Szöveges keret: kódolásjelző + szöveg. */
    private fun textFrame(id: String, encoding: Int, text: String, major: Int): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(encoding)
            when (encoding) {
                0 -> write(text.toByteArray(Charsets.ISO_8859_1))
                1 -> {
                    write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
                    write(text.toByteArray(Charsets.UTF_16LE))
                    write(byteArrayOf(0, 0))
                }
                2 -> write(text.toByteArray(Charsets.UTF_16BE))
                3 -> write(text.toByteArray(Charsets.UTF_8))
            }
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write(id.toByteArray(Charsets.US_ASCII))
            when (major) {
                2 -> write(byteArrayOf((payload.size shr 16).toByte(), (payload.size shr 8).toByte(), payload.size.toByte()))
                3 -> write(int32(payload.size))
                else -> write(synchsafe(payload.size))
            }
            if (major != 2) write(byteArrayOf(0, 0))
            write(payload)
        }.toByteArray()
    }

    private fun tag(major: Int, vararg frames: ByteArray, padding: Int = 32, flags: Int = 0): ByteArray {
        val body = ByteArrayOutputStream().apply {
            frames.forEach { write(it) }
            write(ByteArray(padding))
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(major.toByte(), 0, flags.toByte()))
            write(synchsafe(body.size))
            write(body)
            write(ByteArray(64) { 0x55 }) // hangadat helyett
        }.toByteArray()
    }

    @Test
    fun readsUtf16HungarianTextFromId3v23() {
        val bytes = tag(
            3,
            textFrame("TIT2", 1, "Árvíztűrő tükörfúrógép", 3),
            textFrame("TPE1", 1, "Kovács Éva", 3),
            textFrame("TALB", 1, "Ékezetes dalok", 3),
            textFrame("TCON", 0, "Népzene", 3),
        )
        val tags = Id3TextReader.read(bytes)!!
        assertEquals("Árvíztűrő tükörfúrógép", tags.title)
        assertEquals("Kovács Éva", tags.artist)
        assertEquals("Ékezetes dalok", tags.album)
        assertEquals("Népzene", tags.genre)
    }

    @Test
    fun readsUtf8FromId3v24() {
        val bytes = tag(4, textFrame("TIT2", 3, "Őszi reggel", 4), textFrame("TPE1", 2, "Szabó Ádám", 4))
        val tags = Id3TextReader.read(bytes)!!
        assertEquals("Őszi reggel", tags.title)
        assertEquals("Szabó Ádám", tags.artist)
    }

    @Test
    fun readsLatin1FromId3v22() {
        val bytes = tag(2, textFrame("TT2", 0, "Csendes éj", 2), textFrame("TAL", 0, "Évszakok", 2))
        val tags = Id3TextReader.read(bytes)!!
        assertEquals("Csendes éj", tags.title)
        assertEquals("Évszakok", tags.album)
        assertNull(tags.artist)
    }

    @Test
    fun takesFirstOfMultipleValues() {
        val bytes = tag(4, textFrame("TPE1", 3, "Első\u0000Második", 4))
        assertEquals("Első", Id3TextReader.read(bytes)!!.artist)
    }

    @Test
    fun rejectsNonId3AndBrokenData() {
        assertNull(Id3TextReader.read(ByteArray(0)))
        assertNull(Id3TextReader.read("RIFF....WAVE".toByteArray()))
        assertNull(Id3TextReader.read(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 9, 0, 0, 0, 0, 0, 10)))
        // Sérült keretméret: nem omlik össze, üres eredményt ad.
        val broken = tag(3, byteArrayOf('T'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte(), '2'.code.toByte(), 0x7F, 0, 0, 0, 0, 0, 1, 65))
        assertEquals(Id3TextReader.Tags(), Id3TextReader.read(broken))
    }

    @Test
    fun skipsUnsynchronisedTags() {
        val bytes = tag(3, textFrame("TIT2", 0, "X", 3), flags = 0x80)
        assertNull(Id3TextReader.read(bytes))
    }

    @Test
    fun detectsMisdecodedMediaStoreText() {
        // Ezt adta vissza az Android MediaStore egy hibátlan UTF-16 címkére.
        assertTrue(Id3TextReader.looksMisdecoded("脕rv铆zt疟r艖 t眉k枚rf煤r贸g茅p"))
        assertTrue(Id3TextReader.looksMisdecoded("Rendben", null, "蒶ezetes dalok"))
        assertFalse(Id3TextReader.looksMisdecoded("Árvíztűrő tükörfúrógép", "Kovács Éva", null))
        assertFalse(Id3TextReader.looksMisdecoded(null, null))
    }
}
