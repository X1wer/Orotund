package io.github.x1wer.szabadzene.data

import io.github.x1wer.szabadzene.data.edit.LosslessTrim
import io.github.x1wer.szabadzene.data.edit.TagEditor
import io.github.x1wer.szabadzene.data.edit.TrimMode
import io.github.x1wer.szabadzene.playback.QueueLogic
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.math.abs

/** Veszteségmentes vágás a generált tesztfájlokon; az eredeti fájl nem változhat. */
class LosslessTrimTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun fixture(name: String): File {
        val stream = javaClass.classLoader!!.getResourceAsStream("audio/$name") ?: error("Hiányzó tesztfájl: $name")
        val file = File(temp.newFolder(), name)
        stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    @Test
    fun mp3TrimLengthTagsAndOriginalUntouched() {
        val input = fixture("fx_tagged.mp3")
        val before = input.readBytes()
        val output = File(temp.root, "out.mp3")
        val result = LosslessTrim.trimMp3(input, output, 1_000, 3_000)
        assertArrayEquals("az eredeti fájl nem változhat", before, input.readBytes())
        val measured = LosslessTrim.mp3DurationMs(output)
        assertTrue("hossz: $measured", abs(measured - 2_000) <= 60)
        assertTrue(abs(result.durationMs - measured) <= 2)
        // A címkék megmaradnak (az ID3v2 blokk átkerül).
        assertEquals("Árvíztűrő teszt", TagEditor.read(output).values.title)
    }

    @Test
    fun mp3WithoutTagTrimsToRequestedEnd() {
        val input = fixture("fx_untagged.mp3")
        val output = File(temp.root, "out.mp3")
        val full = LosslessTrim.mp3DurationMs(input)
        assertTrue("teljes hossz: $full", abs(full - 4_000) <= 60)
        LosslessTrim.trimMp3(input, output, 0, 500)
        val measured = LosslessTrim.mp3DurationMs(output)
        assertTrue("hossz: $measured", abs(measured - 500) <= 60)
    }

    @Test
    fun mp3RangeBeyondEndIsClamped() {
        val input = fixture("fx_tagged.mp3")
        val output = File(temp.root, "out.mp3")
        LosslessTrim.trimMp3(input, output, 3_000, 60_000)
        val measured = LosslessTrim.mp3DurationMs(output)
        assertTrue("hossz: $measured", abs(measured - 1_000) <= 60)
    }

    @Test(expected = IOException::class)
    fun mp3EmptyRangeFails() {
        LosslessTrim.trimMp3(fixture("fx_tagged.mp3"), File(temp.root, "out.mp3"), 10_000, 20_000)
    }

    @Test
    fun wavTrimIsSampleExact() {
        val input = fixture("fx_plain.wav")
        val before = input.readBytes()
        val output = File(temp.root, "out.wav")
        val result = LosslessTrim.trimWav(input, output, 1_000, 3_000)
        assertArrayEquals(before, input.readBytes())
        assertEquals(2_000L, result.durationMs)
        assertEquals(2_000L, LosslessTrim.wavDurationMs(output))
        // A minták bájtra azonosak az eredeti megfelelő szakaszával (44 bájtos fejléc, 16 bit, mono, 44,1 kHz).
        val out = output.readBytes()
        val dataStart = 44
        val bytesPerMs = 44_100 * 2 / 1000.0
        val origOffset = before.size - (4_000 * bytesPerMs).toInt() + (1_000 * bytesPerMs).toInt()
        assertArrayEquals(
            before.copyOfRange(origOffset, origOffset + 1024),
            out.copyOfRange(dataStart, dataStart + 1024),
        )
        assertEquals(44 + 2 * 44_100 * 2, out.size)
    }

    @Test
    fun trimModeSelection() {
        assertEquals(TrimMode.MP3_FRAMES, TrimMode.forInput("a.mp3", "audio/mpeg"))
        assertEquals(TrimMode.AAC_REMUX, TrimMode.forInput("a.m4a", "audio/mp4"))
        assertEquals(TrimMode.WAV_PCM, TrimMode.forInput("a.wav", "audio/x-wav"))
        assertEquals(TrimMode.AAC_REENCODE, TrimMode.forInput("a.flac", "audio/flac"))
        assertEquals(TrimMode.AAC_REENCODE, TrimMode.forInput("a.ogg", "audio/ogg"))
        assertTrue(TrimMode.MP3_FRAMES.lossless && !TrimMode.AAC_REENCODE.lossless)
    }

    @Test
    fun queueRemovalIndicesAreDescendingAndComplete() {
        val queue = listOf("5", "7", "5", "9", "5")
        assertEquals(listOf(4, 2, 0), QueueLogic.indicesToRemove(queue, 5))
        assertEquals(emptyList<Int>(), QueueLogic.indicesToRemove(queue, 1))
        // Csökkenő sorrendben eltávolítva a többi elem sorrendje megmarad.
        val mutable = queue.toMutableList()
        QueueLogic.indicesToRemove(queue, 5).forEach { mutable.removeAt(it) }
        assertEquals(listOf("7", "9"), mutable)
    }
}
