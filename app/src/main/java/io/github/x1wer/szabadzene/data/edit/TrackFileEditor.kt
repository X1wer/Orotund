package io.github.x1wer.szabadzene.data.edit

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.github.x1wer.szabadzene.data.library.LibraryRepository
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.playlists.PlaylistRepository
import io.github.x1wer.szabadzene.playback.PlayerConnection
import io.github.x1wer.szabadzene.ui.components.ArtworkLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume

/** A címkementés eredménye. Sikert csak visszaolvasással ellenőrzött írás után jelez. */
sealed interface TagSaveResult {
    data object Success : TagSaveResult

    /** Nem sikerült; [restored] = az eredeti tartalom visszakerült (vagy hozzá sem nyúltunk). */
    data class Failure(val reason: String, val restored: Boolean) : TagSaveResult
}

/** Az elkészült vágás: hova került, és milyen módon készült. */
data class TrimResult(
    val uri: Uri,
    val displayName: String,
    val location: String,
    val mode: TrimMode,
    val durationMs: Long,
)

/**
 * A fájlt módosító műveletek (címke/borító, vágás, törlés, csengőhang) Android-oldali része.
 *
 * Adatbiztonság: a címkeírás mindig egy gyorsítótárbeli másolaton történik; az eredeti csak akkor
 * íródik felül, ha a másolat visszaolvasva helyes, és felülírás után is ellenőrizzük. Ha a
 * visszaírt fájl hibás, a mentett eredetit visszaírjuk. A vágás sosem módosítja az eredetit.
 */
class TrackFileEditor(
    private val context: Context,
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val artwork: ArtworkLoader,
    private val player: PlayerConnection,
) {
    private val resolver get() = context.contentResolver
    private val workDir: File get() = File(context.cacheDir, "edit").apply { mkdirs() }

    // --- Engedélyek ---

    /** Android 11-től a rendszer fájlonkénti írási/törlési kérését használjuk. */
    val usesSystemWriteRequest: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Android 10-ig a közös tárhely írási engedélye kell (címke, törlés; Android 9-ig a vágáshoz is). */
    fun legacyWritePermissionMissing(forNewFile: Boolean = false): Boolean {
        val needed = if (forNewFile) {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        } else {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        }
        return needed && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun writeRequest(tracks: List<Track>): IntentSender =
        MediaStore.createWriteRequest(resolver, tracks.map { it.contentUri }).intentSender

    @RequiresApi(Build.VERSION_CODES.R)
    fun deleteRequest(tracks: List<Track>): IntentSender =
        MediaStore.createDeleteRequest(resolver, tracks.map { it.contentUri }).intentSender

    // --- Címkék ---

    fun isTagWritable(track: Track): Boolean = TagFormat.of(track.displayName, track.mimeType) != null

    /** A fájl jelenlegi címkéi (közvetlenül a fájlból olvasva). */
    suspend fun readTags(track: Track): TagSnapshot = withContext(Dispatchers.IO) {
        val format = TagFormat.of(track.displayName, track.mimeType) ?: throw IOException("Nem írható formátum")
        val copy = File(workDir, "read_${track.id}.${format.extension}")
        try {
            copyFromUri(track.contentUri, copy)
            TagEditor.read(copy)
        } finally {
            copy.delete()
        }
    }

    /** Címkék és borító mentése. Az írási engedélyt a hívó szerzi meg előtte. */
    suspend fun saveTags(track: Track, values: TagValues, cover: CoverChange): TagSaveResult = withContext(Dispatchers.IO) {
        val format = TagFormat.of(track.displayName, track.mimeType)
            ?: return@withContext TagSaveResult.Failure("format", restored = true)
        val original = File(workDir, "orig_${track.id}.${format.extension}")
        val work = File(workDir, "work_${track.id}.${format.extension}")
        val readBack = File(workDir, "back_${track.id}.${format.extension}")
        try {
            copyFromUri(track.contentUri, original)
            original.copyTo(work, overwrite = true)
            try {
                TagEditor.write(work, values, cover)
            } catch (e: TagWriteException) {
                Log.w(TAG, "Címkeírás a másolaton nem sikerült", e)
                return@withContext TagSaveResult.Failure("write", restored = true)
            }
            if (!TagEditor.verify(work, values, cover)) {
                return@withContext TagSaveResult.Failure("verify", restored = true)
            }
            // Az eredeti felülírása, majd ellenőrzés a ténylegesen tárolt fájlon.
            try {
                writeToUri(work, track.contentUri)
            } catch (e: Exception) {
                Log.w(TAG, "Visszaírás nem sikerült", e)
                return@withContext TagSaveResult.Failure("permission", restored = restore(original, track))
            }
            copyFromUri(track.contentUri, readBack)
            val ok = readBack.length() == work.length() && TagEditor.verify(readBack, values, cover)
            if (!ok) {
                return@withContext TagSaveResult.Failure("verify", restored = restore(original, track))
            }
            rescan(track)
            library.refresh().join()
            artwork.invalidate(track.id)
            // A lejátszó (MediaController) csak a fő szálról kezelhető.
            withContext(Dispatchers.Main) { player.reloadTracks(listOf(track.id)) }
            TagSaveResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "Címkementés nem sikerült", e)
            TagSaveResult.Failure("io", restored = false)
        } finally {
            original.delete()
            work.delete()
            readBack.delete()
        }
    }

    private fun restore(original: File, track: Track): Boolean = try {
        writeToUri(original, track.contentUri)
        val back = File(workDir, "restore_${track.id}")
        try {
            copyFromUri(track.contentUri, back)
            back.readBytes().contentEquals(original.readBytes())
        } finally {
            back.delete()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Az eredeti visszaállítása nem sikerült", e)
        false
    }

    // --- Vágás ---

    fun trimModeFor(track: Track): TrimMode = TrimMode.forInput(track.displayName, track.mimeType)

    /**
     * Kivágás új fájlba (alapból Music/SzabadZene). [baseName] kiterjesztés nélküli név;
     * ütközésnél a rendszer (vagy mi, Android 9-ig) sorszámot tesz a végére.
     */
    suspend fun trim(track: Track, startMs: Long, endMs: Long, baseName: String): TrimResult = withContext(Dispatchers.IO) {
        val ext = track.displayName.substringAfterLast('.', "bin")
        val input = File(workDir, "trim_in_${track.id}.$ext")
        var output: File? = null
        try {
            copyFromUri(track.contentUri, input)
            val (file, outcome) = AudioTrimmer.trim(input, track.displayName, track.mimeType, startMs, endMs, workDir)
            output = file
            // A kimenet ellenőrzése: lejátszható-e, és a hossza a kért szakasznak felel-e meg.
            val measured = measureDurationMs(file)
                ?: throw IOException("A kivágott fájl nem olvasható vissza")
            val expected = outcome.endMs - outcome.startMs
            if (kotlin.math.abs(measured - expected) > DURATION_TOLERANCE_MS) {
                throw IOException("A kivágott fájl hossza eltér: $measured ms (várt: $expected ms)")
            }
            copyTags(input, file, baseName, outcome.mode)
            val name = sanitizeFileName(baseName).ifBlank { "trim" } + "." + outcome.mode.extension
            val (uri, location) = publish(file, name, outcome.mode.mimeType)
            library.refresh().join()
            TrimResult(uri, location.substringAfterLast('/'), location, outcome.mode, measured)
        } finally {
            input.delete()
            output?.delete()
        }
    }

    /** A cím a megadott név lesz, a többi címke és a borító az eredetiből jön (ahol a formátum írható). */
    private fun copyTags(original: File, output: File, title: String, mode: TrimMode) {
        if (mode == TrimMode.WAV_PCM) return
        val source = runCatching { TagEditor.read(original) }.getOrNull()
        val values = (source?.values ?: TagValues()).copy(title = title, trackNumber = "")
        val cover = source?.cover?.let { bytes ->
            val mime = if (bytes.size > 3 && bytes[0] == 0x89.toByte()) "image/png" else "image/jpeg"
            CoverChange.Replace(bytes, mime)
        } ?: CoverChange.Keep
        runCatching { TagEditor.write(output, values, cover) }
            .onFailure { Log.w(TAG, "A vágott fájl címkéi nem írhatók (a hang ettől még jó)", it) }
    }

    private fun publish(file: File, displayName: String, mimeType: String): Pair<Uri, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, OUTPUT_RELATIVE_PATH)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw IOException("Nem hozható létre a kimeneti fájl")
            try {
                writeToUri(file, uri)
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            val finalName = resolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: displayName
            return uri to "$OUTPUT_RELATIVE_PATH/$finalName"
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), OUTPUT_FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("A mappa nem hozható létre: $dir")
        var target = File(dir, displayName)
        var counter = 1
        while (target.exists()) {
            target = File(dir, "${displayName.substringBeforeLast('.')} ($counter).${displayName.substringAfterLast('.')}")
            counter++
        }
        file.copyTo(target)
        val uri = scanPathBlocking(target.absolutePath) ?: Uri.fromFile(target)
        return uri to "$OUTPUT_RELATIVE_PATH/${target.name}"
    }

    // --- Törlés ---

    /** Android 10-ig: törlés a közös tárhely írási engedélyével. */
    suspend fun deleteLegacy(track: Track): Boolean = withContext(Dispatchers.IO) {
        val deleted = runCatching { resolver.delete(track.contentUri, null, null) > 0 }.getOrDefault(false)
        if (!deleted) track.path?.let { runCatching { File(it).delete() } }
        !exists(track)
    }

    /** Valóban eltűnt-e a fájl a médiatárból. */
    suspend fun isGone(track: Track): Boolean = withContext(Dispatchers.IO) { !exists(track) }

    private fun exists(track: Track): Boolean = runCatching {
        resolver.query(track.contentUri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    /** Törlés után: várólista, listák, kedvencek és a zenetár következetessé tétele. */
    suspend fun afterDeleted(track: Track) {
        withContext(Dispatchers.Main) { player.removeTrackEverywhere(track.id) }
        playlists.forgetTrack(track.id)
        library.forget(track.id)
        artwork.invalidate(track.id)
        library.refresh().join()
    }

    // --- Csengőhang ---

    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    /** Beállítás csengőhangként; igaz, ha visszaolvasva is ez a csengőhang. */
    suspend fun setAsRingtone(track: Track): Boolean = withContext(Dispatchers.IO) {
        if (!canWriteSettings()) return@withContext false
        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, track.contentUri)
            val current = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            current?.lastPathSegment == track.contentUri.lastPathSegment
        } catch (e: Exception) {
            Log.w(TAG, "Csengőhang beállítása nem sikerült", e)
            false
        }
    }

    // --- Segédek ---

    private fun copyFromUri(uri: Uri, target: File) {
        val input = resolver.openInputStream(uri) ?: throw IOException("A fájl nem nyitható meg")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
    }

    /** Teljes felülírás („wt” = csonkolással), lemezre szinkronizálva. */
    private fun writeToUri(source: File, uri: Uri) {
        val descriptor = resolver.openFileDescriptor(uri, "wt") ?: throw IOException("A fájl nem írható")
        descriptor.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).use { out ->
                source.inputStream().use { it.copyTo(out) }
                out.flush()
                out.fd.sync()
            }
        }
    }

    private fun measureDurationMs(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private suspend fun rescan(track: Track) {
        val path = track.path ?: return
        withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> if (cont.isActive) cont.resume(Unit) }
            }
        }
    }

    private fun scanPathBlocking(path: String): Uri? {
        val lock = Object()
        var result: Uri? = null
        var done = false
        MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, uri ->
            synchronized(lock) {
                result = uri
                done = true
                lock.notifyAll()
            }
        }
        synchronized(lock) {
            val deadline = System.currentTimeMillis() + 10_000
            while (!done && System.currentTimeMillis() < deadline) lock.wait(500)
        }
        return result
    }

    companion object {
        private const val TAG = "TrackFileEditor"
        const val OUTPUT_FOLDER = "SzabadZene"
        const val OUTPUT_RELATIVE_PATH = "Music/$OUTPUT_FOLDER"

        /** A vágott fájl mért hossza ennyivel térhet el a kérttől (keretillesztés, kódoló-késleltetés). */
        const val DURATION_TOLERANCE_MS = 400L

        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().trimEnd('.').take(120)
    }
}
