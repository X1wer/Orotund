package io.github.x1wer.szabadzene.data.edit

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Veszteségmentes vágás tiszta Kotlinban (JVM-en tesztelhető):
 * - MP3: egész MPEG-hangkeretek másolása (kb. 26 ms-os pontosság), az ID3v2 címke megmarad;
 *   a Xing/Info (VBR-fejléc) keret kimarad, mert a régi hosszt írná le.
 * - WAV (PCM): a minták bájtpontos másolása új RIFF-fejléccel.
 */
object LosslessTrim {

    /** Eredmény: a ténylegesen kivágott szakasz (a keretillesztés miatt eltérhet a kérttől). */
    data class Result(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    // --- MP3 ---

    private val BITRATES_V1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val BITRATES_V2_L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
    private val SAMPLE_RATES = intArrayOf(44100, 48000, 32000)

    /** Egy MPEG-1/2/2.5 Layer III keret fejléce. */
    internal data class Mp3Frame(val offset: Long, val size: Int, val samples: Int, val sampleRate: Int)

    internal fun parseMp3Header(b0: Int, b1: Int, b2: Int, offset: Long): Mp3Frame? {
        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null
        val version = (b1 shr 3) and 0x03 // 0 = 2.5, 2 = 2, 3 = 1
        val layer = (b1 shr 1) and 0x03 // 1 = Layer III
        if (version == 1 || layer != 1) return null
        val bitrateIndex = (b2 shr 4) and 0x0F
        val rateIndex = (b2 shr 2) and 0x03
        if (bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) return null
        val padding = (b2 shr 1) and 0x01
        val sampleRate = when (version) {
            3 -> SAMPLE_RATES[rateIndex]
            2 -> SAMPLE_RATES[rateIndex] / 2
            else -> SAMPLE_RATES[rateIndex] / 4
        }
        val bitrate = (if (version == 3) BITRATES_V1_L3 else BITRATES_V2_L3)[bitrateIndex] * 1000
        val samples = if (version == 3) 1152 else 576
        val size = samples / 8 * bitrate / sampleRate + padding
        if (size < 4) return null
        return Mp3Frame(offset, size, samples, sampleRate)
    }

    /** Az ID3v2 címke hossza a fájl elején (0, ha nincs). */
    internal fun id3v2Length(head: ByteArray): Int {
        if (head.size < 10 || head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) return 0
        val size = ((head[6].toInt() and 0x7F) shl 21) or ((head[7].toInt() and 0x7F) shl 14) or
            ((head[8].toInt() and 0x7F) shl 7) or (head[9].toInt() and 0x7F)
        val footer = if ((head[5].toInt() and 0x10) != 0) 10 else 0
        return 10 + size + footer
    }

    /** A hangkeretek listája (az ID3v2 címke után, az ID3v1 címke előtt). */
    internal fun mp3Frames(bytes: ByteArray): Pair<Int, List<Mp3Frame>> {
        val tagLength = id3v2Length(bytes).coerceAtMost(bytes.size)
        var end = bytes.size
        if (end - tagLength >= 128 && bytes[end - 128] == 'T'.code.toByte() &&
            bytes[end - 127] == 'A'.code.toByte() && bytes[end - 126] == 'G'.code.toByte()
        ) {
            end -= 128
        }
        val frames = ArrayList<Mp3Frame>()
        var i = tagLength
        while (i + 4 <= end) {
            val frame = parseMp3Header(
                bytes[i].toInt() and 0xFF,
                bytes[i + 1].toInt() and 0xFF,
                bytes[i + 2].toInt() and 0xFF,
                i.toLong(),
            )
            if (frame != null && i + frame.size <= end) {
                // Szinkron ellenőrzése: a következő keret is érvényes (vagy ez az utolsó).
                val next = i + frame.size
                val ok = next + 4 > end || parseMp3Header(
                    bytes[next].toInt() and 0xFF,
                    bytes[next + 1].toInt() and 0xFF,
                    bytes[next + 2].toInt() and 0xFF,
                    next.toLong(),
                ) != null
                if (ok || frames.isNotEmpty()) {
                    frames += frame
                    i = next
                    continue
                }
            }
            i++
        }
        return tagLength to frames
    }

    /** VBR-fejléckeret: „Xing”/„Info” az oldalinformáció után (13, 21 vagy 36), „VBRI” a 36. bájton. */
    private fun isXingFrame(bytes: ByteArray, frame: Mp3Frame): Boolean {
        val start = frame.offset.toInt()
        fun tagAt(pos: Int): String? = if (start + pos + 4 <= bytes.size) {
            String(bytes, start + pos, 4, Charsets.ISO_8859_1)
        } else {
            null
        }
        return listOf(13, 21, 36).any { tagAt(it) == "Xing" || tagAt(it) == "Info" } || tagAt(36) == "VBRI"
    }

    fun trimMp3(input: File, output: File, startMs: Long, endMs: Long): Result {
        require(endMs > startMs) { "A végpontnak a kezdőpont után kell lennie" }
        val bytes = input.readBytes()
        val (tagLength, allFrames) = mp3Frames(bytes)
        if (allFrames.isEmpty()) throw IOException("Nem található MP3-hangkeret")
        val frames = if (isXingFrame(bytes, allFrames.first())) allFrames.drop(1) else allFrames
        var timeUs = 0L
        var firstUs = -1L
        var lastUs = 0L
        output.outputStream().buffered().use { out ->
            out.write(bytes, 0, tagLength)
            for (frame in frames) {
                val durationUs = frame.samples * 1_000_000L / frame.sampleRate
                val frameStartMs = timeUs / 1000
                if (frameStartMs >= startMs && frameStartMs < endMs) {
                    out.write(bytes, frame.offset.toInt(), frame.size)
                    if (firstUs < 0) firstUs = timeUs
                    lastUs = timeUs + durationUs
                }
                timeUs += durationUs
                if (timeUs / 1000 >= endMs) break
            }
        }
        if (firstUs < 0) {
            output.delete()
            throw IOException("A kijelölt szakasz üres")
        }
        return Result(firstUs / 1000, lastUs / 1000)
    }

    // --- WAV ---

    /** A WAV-fájl formátuma: csak egyszerű PCM (1-es formátumkód) vágható így. */
    internal data class WavInfo(
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataLength: Long,
    ) {
        val blockAlign: Int get() = channels * bitsPerSample / 8
    }

    internal fun readWavInfo(file: RandomAccessFile): WavInfo {
        fun u16(): Int = file.read() or (file.read() shl 8)
        fun u32(): Long = (u16().toLong()) or (u16().toLong() shl 16)
        val riff = ByteArray(12)
        file.seek(0)
        file.readFully(riff)
        if (String(riff, 0, 4, Charsets.US_ASCII) != "RIFF" || String(riff, 8, 4, Charsets.US_ASCII) != "WAVE") {
            throw IOException("Nem WAV-fájl")
        }
        var channels = 0
        var rate = 0
        var bits = 0
        var format = 0
        val id = ByteArray(4)
        while (file.filePointer + 8 <= file.length()) {
            file.readFully(id)
            val size = u32()
            val name = String(id, Charsets.US_ASCII)
            val bodyStart = file.filePointer
            when (name) {
                "fmt " -> {
                    format = u16()
                    channels = u16()
                    rate = u32().toInt()
                    u32()
                    u16()
                    bits = u16()
                    if (format == 0xFFFE && size >= 40) {
                        // WAVE_FORMAT_EXTENSIBLE: a valódi formátumkód az alformátum GUID elején.
                        file.seek(bodyStart + 24)
                        format = u16()
                    }
                }
                "data" -> {
                    if (format != 1 || channels == 0 || bits == 0) throw IOException("Csak PCM WAV vágható veszteségmentesen")
                    val length = minOf(size, file.length() - bodyStart)
                    return WavInfo(channels, rate, bits, bodyStart, length)
                }
            }
            file.seek(bodyStart + size + (size and 1))
        }
        throw IOException("Hiányzó WAV-adatblokk")
    }

    fun trimWav(input: File, output: File, startMs: Long, endMs: Long): Result {
        require(endMs > startMs) { "A végpontnak a kezdőpont után kell lennie" }
        RandomAccessFile(input, "r").use { raf ->
            val info = readWavInfo(raf)
            val totalFrames = info.dataLength / info.blockAlign
            val startFrame = (startMs * info.sampleRate / 1000).coerceIn(0, totalFrames)
            val endFrame = (endMs * info.sampleRate / 1000).coerceIn(startFrame, totalFrames)
            if (endFrame <= startFrame) throw IOException("A kijelölt szakasz üres")
            val length = (endFrame - startFrame) * info.blockAlign
            output.outputStream().buffered().use { out ->
                fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
                fun le32(v: Long) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
                out.write("RIFF".toByteArray(Charsets.US_ASCII))
                le32(36 + length + (length and 1))
                out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                le32(16)
                le16(1)
                le16(info.channels)
                le32(info.sampleRate.toLong())
                le32(info.sampleRate.toLong() * info.blockAlign)
                le16(info.blockAlign)
                le16(info.bitsPerSample)
                out.write("data".toByteArray(Charsets.US_ASCII))
                le32(length)
                raf.seek(info.dataOffset + startFrame * info.blockAlign)
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) throw IOException("Váratlan fájlvég")
                    out.write(buffer, 0, read)
                    remaining -= read
                }
                if (length and 1L == 1L) out.write(0)
            }
            return Result(startFrame * 1000 / info.sampleRate, endFrame * 1000 / info.sampleRate)
        }
    }

    /** A WAV hossza ezredmásodpercben (teszteléshez és ellenőrzéshez). */
    fun wavDurationMs(file: File): Long = RandomAccessFile(file, "r").use { raf ->
        val info = readWavInfo(raf)
        info.dataLength / info.blockAlign * 1000 / info.sampleRate
    }

    /** Az MP3 hossza a keretek alapján, ezredmásodpercben (teszteléshez és ellenőrzéshez). */
    fun mp3DurationMs(file: File): Long {
        val bytes = file.readBytes()
        val (_, frames) = mp3Frames(bytes)
        val audio = if (frames.isNotEmpty() && isXingFrame(bytes, frames.first())) frames.drop(1) else frames
        return audio.sumOf { it.samples * 1_000_000L / it.sampleRate } / 1000
    }
}
