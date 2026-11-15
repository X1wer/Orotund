package io.github.x1wer.szabadzene

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.x1wer.szabadzene.data.db.AppDatabase
import io.github.x1wer.szabadzene.data.db.FavoriteEntity
import io.github.x1wer.szabadzene.data.edit.AudioTrimmer
import io.github.x1wer.szabadzene.data.edit.CoverChange
import io.github.x1wer.szabadzene.data.edit.TagEditor
import io.github.x1wer.szabadzene.data.edit.TagSaveResult
import io.github.x1wer.szabadzene.data.edit.TagValues
import io.github.x1wer.szabadzene.data.edit.TrimMode
import io.github.x1wer.szabadzene.data.library.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Fájlműveletek valódi Android-futtatókörnyezetben, kizárólag a generált tesztfájlokon
 * (`tools/generate_test_audio.py --fixtures`): vágás hossza és lejátszhatósága (teljes dekódolással),
 * címkeírás visszaolvasása az Android saját olvasójával, és a teljes MediaStore-folyamat
 * (címkementés → vágás új fájlba → törlés → következetesség).
 */
@RunWith(AndroidJUnit4::class)
class FileOperationsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val workDir = File(context.cacheDir, "test_fixtures").apply { mkdirs() }

    @Before
    fun grantPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        perms.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
    }

    private fun fixture(name: String): File {
        val out = File(workDir, name)
        instrumentation.context.assets.open("audio/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    /** Teljes dekódolás: a fájl lejátszható, és ennyi ms hang van benne. */
    private fun decodedDurationMs(file: File): Long {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).first {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")
        }
        val format = extractor.getTrackFormat(track)
        extractor.selectTrack(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var frames = 0L
        var inputDone = false
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (o >= 0) {
                    frames += info.size / (2 * channels)
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
        return frames * 1000 / rate
    }

    private fun retrieverDurationMs(file: File): Long {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            return r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        } finally {
            r.release()
        }
    }

    @Test
    fun trimmedFilesHaveRequestedLengthAndArePlayable() {
        val cases = listOf(
            Triple("fx_tagged.mp3", "audio/mpeg", TrimMode.MP3_FRAMES),
            Triple("fx_untagged.mp3", "audio/mpeg", TrimMode.MP3_FRAMES),
            Triple("fx_tagged.m4a", "audio/mp4", TrimMode.AAC_REMUX),
            Triple("fx_tagged.flac", "audio/flac", TrimMode.AAC_REENCODE),
            Triple("fx_tagged.ogg", "audio/ogg", TrimMode.AAC_REENCODE),
            Triple("fx_plain.wav", "audio/x-wav", TrimMode.WAV_PCM),
        )
        for ((name, mime, expectedMode) in cases) {
            val input = fixture(name)
            val before = input.readBytes()
            val (output, outcome) = AudioTrimmer.trim(input, name, mime, 1_000, 3_000, workDir)
            try {
                assertEquals(name, expectedMode, outcome.mode)
                assertTrue("$name: az eredeti nem változhat", before.contentEquals(input.readBytes()))
                val reported = retrieverDurationMs(output)
                val decoded = decodedDurationMs(output)
                assertTrue("$name: jelentett hossz $reported ms", abs(reported - 2_000) <= 400)
                assertTrue("$name: dekódolt hossz $decoded ms", abs(decoded - 2_000) <= 400)
            } finally {
                output.delete()
            }
        }
    }

    @Test
    fun tagWriteIsReadBackByAndroid() {
        val jpeg = run {
            val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(30, 110, 90))
            java.io.ByteArrayOutputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
        }
        val values = TagValues(title = "Őszi szél – teszt", artist = "Árvai Ödön", album = "Próbalemez", genre = "Népzene", year = "2025", trackNumber = "4")
        for (name in listOf("fx_tagged.mp3", "fx_untagged.mp3", "fx_tagged.m4a", "fx_tagged.flac", "fx_tagged.ogg")) {
            val file = fixture(name)
            val cover = CoverChange.Replace(jpeg, "image/jpeg")
            TagEditor.write(file, values, cover)
            assertTrue("$name: saját visszaolvasás", TagEditor.verify(file, values, cover))
            // Az Android saját metaadat-olvasója is ugyanazt látja, és a hang továbbra is lejátszható.
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.absolutePath)
                assertEquals("$name: cím", values.title, r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                assertEquals("$name: előadó", values.artist, r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                assertNotNull("$name: borító", r.embeddedPicture)
            } finally {
                r.release()
            }
            assertTrue("$name: lejátszható", abs(decodedDurationMs(file) - 4_000) <= 300)
        }
    }

    @Test
    fun deleteConsistencyInDatabase() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.playlistDao()
            val a = dao.createWithTracks("A", listOf(1, 2, 1, 3), now = 1)
            val b = dao.createWithTracks("B", listOf(2, 1), now = 2)
            val c = dao.createWithTracks("C", listOf(3), now = 3)
            db.favoriteDao().insert(listOf(FavoriteEntity(1, 1), FavoriteEntity(3, 2)))
            dao.removeTrackEverywhere(1)
            db.favoriteDao().remove(1)
            assertEquals(listOf(2L, 3L), dao.entries(a).map { it.trackId })
            assertEquals(listOf(0, 1), dao.entries(a).map { it.position })
            assertEquals(listOf(2L), dao.entries(b).map { it.trackId })
            assertEquals(listOf(0), dao.entries(b).map { it.position })
            assertEquals(listOf(3L), dao.entries(c).map { it.trackId })
            assertEquals(listOf(3L), db.favoriteDao().observeIds().first())
        } finally {
            db.close()
        }
    }

    /**
     * Teljes folyamat a MediaStore-ral, egy erre a célra feltöltött (az app saját) tesztfájlon:
     * címkementés ellenőrzéssel → zenetár frissül; vágás új fájlba; törlés → listák, kedvencek
     * és zenetár következetes.
     */
    @Test
    fun endToEndSaveTrimDeleteOnMediaStore() = runBlocking {
        assumeTrue("RELATIVE_PATH kell (Android 10+)", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val container = (context.applicationContext as SzabadZeneApp).container
        val resolver = context.contentResolver
        val source = fixture("fx_tagged.flac")
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "szabadzene_e2e_${System.currentTimeMillis()}.flac")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/flac")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SzabadZeneTeszt3")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            },
        )!!
        val id = ContentUris.parseId(uri)
        var trimmedUri: android.net.Uri? = null
        try {
            resolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            val track = waitForTrack(container, id) { true }
            // A médiatár másodpercre kerekített módosítási időt tárol: a beszúrás és a módosítás
            // ne essen ugyanabba a másodpercbe (valós használatban a fájl jóval régebbi).
            delay(2_000)

            // 1) Címkementés, visszaolvasva és a zenetárban is.
            val values = TagValues(title = "E2E Árvíztűrő", artist = "Teszt Előadó", album = "E2E")
            val result = container.fileEditor.saveTags(track, values, CoverChange.Keep)
            assertEquals(TagSaveResult.Success, result)
            val updated = waitForTrack(container, id) { it.title == "E2E Árvíztűrő" }
            assertEquals("Teszt Előadó", updated.artist)

            // 2) Vágás új fájlba (FLAC → újrakódolt M4A), az eredeti megmarad.
            val trim = container.fileEditor.trim(updated, 500, 2_500, "szabadzene_e2e_vagas")
            trimmedUri = trim.uri
            assertEquals(TrimMode.AAC_REENCODE, trim.mode)
            assertTrue("vágott hossz: ${trim.durationMs}", abs(trim.durationMs - 2_000) <= 400)
            assertTrue(trim.location.startsWith("Music/SzabadZene/"))
            assertFalse(container.fileEditor.isGone(updated))

            // 3) Lista és kedvenc, majd törlés: minden helyről eltűnik.
            val playlistId = container.playlists.create("E2E teszt", listOf(id, id))
            container.playlists.addFavorites(listOf(id))
            assertTrue(container.fileEditor.deleteLegacy(updated))
            container.fileEditor.afterDeleted(updated)
            assertTrue(container.fileEditor.isGone(updated))
            assertTrue(container.library.tracksByIds(listOf(id)).isEmpty())
            assertTrue(container.playlists.playlistItemsOnce(playlistId).isEmpty())
            assertFalse(id in container.playlists.favoriteIds.first())
            container.playlists.delete(playlistId)
        } finally {
            runCatching { resolver.delete(uri, null, null) }
            trimmedUri?.let { runCatching { resolver.delete(it, null, null) } }
        }
    }

    private suspend fun waitForTrack(container: AppContainer, id: Long, predicate: (Track) -> Boolean): Track {
        repeat(40) {
            container.library.refresh().join()
            container.library.tracksByIds(listOf(id))[id]?.takeIf(predicate)?.let { return it }
            delay(250)
        }
        val seen = container.library.tracksByIds(listOf(id))[id]
        val store = context.contentResolver.query(
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
            arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.IS_MUSIC),
            null, null, null,
        )?.use { if (it.moveToFirst()) "${it.getString(0)} / is_music=${it.getInt(1)}" else "nincs sor" }
        error("A szám ($id) nem jelent meg a várt állapotban: zenetár=${seen?.title}, MediaStore=$store")
    }
}
