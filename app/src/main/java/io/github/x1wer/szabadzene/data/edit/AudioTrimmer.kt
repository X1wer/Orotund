package io.github.x1wer.szabadzene.data.edit

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Milyen kimenet készül egy adott bemenetből. */
enum class TrimMode(val extension: String, val mimeType: String, val lossless: Boolean) {
    /** MP3-keretek másolása (veszteségmentes). */
    MP3_FRAMES("mp3", "audio/mpeg", true),

    /** PCM-minták másolása (veszteségmentes). */
    WAV_PCM("wav", "audio/x-wav", true),

    /** AAC-minták átcsomagolása új M4A-ba (veszteségmentes). */
    AAC_REMUX("m4a", "audio/mp4", true),

    /** Dekódolás és újrakódolás AAC-be (M4A), pl. FLAC és OGG esetén. */
    AAC_REENCODE("m4a", "audio/mp4", false),
    ;

    companion object {
        fun forInput(displayName: String, mimeType: String?): TrimMode {
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val mime = mimeType?.lowercase().orEmpty()
            return when {
                ext == "mp3" || mime == "audio/mpeg" -> MP3_FRAMES
                ext == "wav" || mime == "audio/x-wav" || mime == "audio/wav" -> WAV_PCM
                ext == "m4a" || ext == "aac" || ext == "mp4" || mime == "audio/mp4" -> AAC_REMUX
                else -> AAC_REENCODE
            }
        }
    }
}

/**
 * Hangkivágás helyi fájlon. A bemenet sosem módosul; a kimenet mindig új fájl.
 * Ha a veszteségmentes út nem járható (pl. nem PCM WAV vagy nem AAC az M4A-ban),
 * újrakódolt M4A/AAC készül – a visszaadott [Outcome.mode] ezt jelzi.
 */
object AudioTrimmer {

    data class Outcome(val mode: TrimMode, val startMs: Long, val endMs: Long)

    private const val AAC_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val AAC_BITRATE = 192_000
    private const val TIMEOUT_US = 10_000L

    fun trim(input: File, displayName: String, mimeType: String?, startMs: Long, endMs: Long, outputDir: File): Pair<File, Outcome> {
        require(endMs > startMs) { "A végpontnak a kezdőpont után kell lennie" }
        val preferred = TrimMode.forInput(displayName, mimeType)
        val attempts = if (preferred == TrimMode.AAC_REENCODE) listOf(preferred) else listOf(preferred, TrimMode.AAC_REENCODE)
        var lastError: Exception? = null
        for (mode in attempts) {
            val out = File(outputDir, "trim_${System.nanoTime()}.${mode.extension}")
            try {
                val result = when (mode) {
                    TrimMode.MP3_FRAMES -> LosslessTrim.trimMp3(input, out, startMs, endMs).let { it.startMs to it.endMs }
                    TrimMode.WAV_PCM -> LosslessTrim.trimWav(input, out, startMs, endMs).let { it.startMs to it.endMs }
                    TrimMode.AAC_REMUX -> remuxAac(input, out, startMs, endMs)
                    TrimMode.AAC_REENCODE -> reencodeToAac(input, out, startMs, endMs)
                }
                return out to Outcome(mode, result.first, result.second)
            } catch (e: Exception) {
                out.delete()
                lastError = e
            }
        }
        throw IOException("A vágás nem sikerült: ${lastError?.message}", lastError)
    }

    private fun audioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat> {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return i to format
        }
        throw IOException("Nincs hangsáv a fájlban")
    }

    /** AAC-minták másolása a kért szakaszból, újrakódolás nélkül. */
    private fun remuxAac(input: File, output: File, startMs: Long, endMs: Long): Pair<Long, Long> {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(input.absolutePath)
            val (track, format) = audioTrack(extractor)
            if (format.getString(MediaFormat.KEY_MIME) != AAC_MIME) throw IOException("Nem AAC")
            extractor.selectTrack(track)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val outTrack = mux.addTrack(format)
            mux.start()
            val buffer = ByteBuffer.allocate(format.maxInputSizeOr(256 * 1024))
            val info = MediaCodec.BufferInfo()
            var firstUs = -1L
            var lastUs = 0L
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                if (time >= endMs * 1000) break
                if (time >= startMs * 1000 - 1000) {
                    if (firstUs < 0) firstUs = time
                    info.set(0, size, time - firstUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    mux.writeSampleData(outTrack, buffer, info)
                    lastUs = time
                }
                extractor.advance()
            }
            if (firstUs < 0) throw IOException("A kijelölt szakasz üres")
            mux.stop()
            val frameUs = 1024L * 1_000_000 / format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            return firstUs / 1000 to (lastUs + frameUs) / 1000
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    private fun MediaFormat.maxInputSizeOr(default: Int): Int =
        if (containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxOf(getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), 1024) else default

    /**
     * Dekódolás a kért szakaszra (mintapontosan levágva), majd AAC-LC kódolás 192 kbit/s-mal M4A-ba.
     * A dekóder 16 bites vagy lebegőpontos PCM-et adhat; mindkettőt kezeli.
     */
    private fun reencodeToAac(input: File, output: File, startMs: Long, endMs: Long): Pair<Long, Long> {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(input.absolutePath)
            val (track, inFormat) = audioTrack(extractor)
            extractor.selectTrack(track)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val dec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder = dec
            dec.configure(inFormat, null, null, 0)
            dec.start()

            var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val startUs = startMs * 1000
            val endUs = endMs * 1000

            var muxTrack = -1
            var inputDone = false
            var decodeDone = false
            var encodeDone = false
            var encoderInputDone = false
            val pending = ArrayDeque<ByteArray>()
            var pendingOffset = 0
            var framesQueued = 0L
            val info = MediaCodec.BufferInfo()

            fun ensureEncoder(): MediaCodec {
                encoder?.let { return it }
                val format = MediaFormat.createAudioFormat(AAC_MIME, sampleRate, channels).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
                }
                val enc = MediaCodec.createEncoderByType(AAC_MIME)
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                enc.start()
                encoder = enc
                return enc
            }

            while (!encodeDone) {
                // 1) Bemenet a dekódernek.
                if (!inputDone) {
                    val index = dec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buf = dec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buf, 0)
                        val time = extractor.sampleTime
                        if (size < 0 || time > endUs + 200_000) {
                            dec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(index, 0, size, time, 0)
                            extractor.advance()
                        }
                    }
                }
                // 2) Dekódolt PCM: a kért szakaszra vágva, 16 bitesre alakítva.
                if (!decodeDone) {
                    val index = dec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = dec.outputFormat
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                f.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                        }
                        index >= 0 -> {
                            val buf = dec.getOutputBuffer(index)!!
                            if (info.size > 0) {
                                val pcm = toPcm16(buf, info, floatPcm)
                                val frameBytes = 2 * channels
                                val frames = pcm.size / frameBytes
                                val chunkStartUs = info.presentationTimeUs
                                val first = ((startUs - chunkStartUs) * sampleRate / 1_000_000).coerceIn(0, frames.toLong()).toInt()
                                val last = ((endUs - chunkStartUs) * sampleRate / 1_000_000).coerceIn(0, frames.toLong()).toInt()
                                if (last > first) pending.addLast(pcm.copyOfRange(first * frameBytes, last * frameBytes))
                            }
                            dec.releaseOutputBuffer(index, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decodeDone = true
                        }
                    }
                }
                // 3) PCM az enkódernek.
                if ((pending.isNotEmpty() || (decodeDone && !encoderInputDone))) {
                    val enc = ensureEncoder()
                    val index = enc.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buf = enc.getInputBuffer(index)!!
                        buf.clear()
                        val ptsUs = framesQueued * 1_000_000 / sampleRate
                        var written = 0
                        while (pending.isNotEmpty() && buf.remaining() > 0) {
                            val head = pending.first()
                            val n = minOf(buf.remaining(), head.size - pendingOffset)
                            buf.put(head, pendingOffset, n)
                            pendingOffset += n
                            written += n
                            if (pendingOffset == head.size) {
                                pending.removeFirst()
                                pendingOffset = 0
                            }
                        }
                        framesQueued += written / (2 * channels)
                        if (pending.isEmpty() && decodeDone) {
                            enc.queueInputBuffer(index, 0, written, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            encoderInputDone = true
                        } else {
                            enc.queueInputBuffer(index, 0, written, ptsUs, 0)
                        }
                    }
                }
                // 4) Kódolt AAC a muxernek.
                val enc = encoder ?: continue
                val outIndex = enc.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        muxer = mux
                        muxTrack = mux.addTrack(enc.outputFormat)
                        mux.start()
                    }
                    outIndex >= 0 -> {
                        val buf = enc.getOutputBuffer(outIndex)!!
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0 && muxTrack >= 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer!!.writeSampleData(muxTrack, buf, info)
                        }
                        enc.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encodeDone = true
                    }
                }
            }
            if (framesQueued == 0L) throw IOException("A kijelölt szakasz üres")
            muxer?.stop() ?: throw IOException("A kódoló nem adott kimenetet")
            val durationMs = framesQueued * 1000 / sampleRate
            return startMs to startMs + durationMs
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    private fun toPcm16(buffer: ByteBuffer, info: MediaCodec.BufferInfo, isFloat: Boolean): ByteArray {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val ordered = buffer.slice().order(ByteOrder.nativeOrder())
        if (!isFloat) {
            val bytes = ByteArray(info.size)
            ordered.get(bytes)
            if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {
                for (i in 0 until bytes.size - 1 step 2) {
                    val t = bytes[i]
                    bytes[i] = bytes[i + 1]
                    bytes[i + 1] = t
                }
            }
            return bytes
        }
        val floats = ordered.asFloatBuffer()
        val out = ByteArray(floats.remaining() * 2)
        var i = 0
        while (floats.hasRemaining()) {
            val v = (floats.get().coerceIn(-1f, 1f) * 32767f).roundToInt()
            out[i++] = v.toByte()
            out[i++] = (v shr 8).toByte()
        }
        return out
    }
}
