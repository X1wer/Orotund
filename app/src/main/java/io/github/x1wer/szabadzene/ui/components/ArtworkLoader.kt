package io.github.x1wer.szabadzene.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.x1wer.szabadzene.data.library.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/** Borítóképek betöltése a fájlba ágyazott képből, memóriás gyorsítótárral. */
class ArtworkLoader(private val context: Context) {

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** Azok a számok, amelyeknek nincs borítója – ezeket nem próbáljuk újra minden görgetésnél. */
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(4)

    fun peek(trackId: Long, sizePx: Int): ImageBitmap? = cache.get(key(trackId, sizePx))

    suspend fun load(trackId: Long, sizePx: Int): ImageBitmap? {
        val key = key(trackId, sizePx)
        cache.get(key)?.let { return it }
        if (key in missing) return null
        return withContext(dispatcher) {
            val bitmap = runCatching { decode(trackId, sizePx) }.getOrNull()
            if (bitmap == null) {
                missing += key
                null
            } else {
                bitmap.asImageBitmap().also { cache.put(key, it) }
            }
        }
    }

    /** Android-bitmap (pl. a widgethez); nem kerül a gyorsítótárba. */
    suspend fun loadBitmap(trackId: Long, sizePx: Int): Bitmap? = withContext(dispatcher) {
        runCatching { decode(trackId, sizePx) }.getOrNull()
    }

    /** A szám borítója megváltozott (pl. borítószerkesztés): a régi kép ne maradjon a gyorsítótárban. */
    fun invalidate(trackId: Long) {
        val prefix = "$trackId@"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
        synchronized(missing) { missing.removeAll { it.startsWith(prefix) } }
        _version.value++
    }

    private val _version = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** Borítóváltozáskor nő; a borítót megjelenítő elemek ennek alapján töltenek újra. */
    val version: kotlinx.coroutines.flow.StateFlow<Int> = _version

    private fun decode(trackId: Long, sizePx: Int): Bitmap? {
        val uri = Track.contentUriFor(trackId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) {
                sample *= 2
            }
            return BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } finally {
            retriever.release()
        }
    }

    private fun key(trackId: Long, sizePx: Int) = "$trackId@$sizePx"

    private companion object {
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}
