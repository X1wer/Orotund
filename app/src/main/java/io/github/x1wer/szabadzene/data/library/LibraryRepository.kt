package io.github.x1wer.szabadzene.data.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.x1wer.szabadzene.data.db.TrackDao
import io.github.x1wer.szabadzene.data.settings.LibraryFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A zenetár egyetlen forrása. A MediaStore-t a háttérben olvassa be, az eredményt a helyi
 * adatbázisban tárolja, így képernyőváltáskor nem kell újraolvasni a teljes könyvtárat.
 */
class LibraryRepository(
    private val context: Context,
    private val dao: TrackDao,
    filter: Flow<LibraryFilter>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scanner = MediaStoreScanner(context.contentResolver)
    private val scanMutex = Mutex()
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hasScannedOnce = MutableStateFlow(false)

    /** Igaz, ha ebben a folyamatban már lefutott legalább egy beolvasás. */
    val hasScannedOnce: StateFlow<Boolean> = _hasScannedOnce.asStateFlow()

    private var observerRegistered = false
    private var pendingRescan: Job? = null

    /** Minden beolvasott szám (szűrés nélkül), magyar ábécé szerint cím alapján rendezve. */
    val allTracks: Flow<List<Track>> = dao.observeAll()
        .map { entities ->
            val collator = hungarianCollator()
            entities.map { it.toTrack() }.sortedWith { a, b -> collator.compare(a.title, b.title) }
        }
        .flowOn(ioDispatcher)
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** A zenetárban látható számok: a kizárt mappák és a túl rövid fájlok nélkül. */
    val tracks: Flow<List<Track>> = combine(allTracks, filter) { list, currentFilter ->
        applyFilter(list, currentFilter)
    }
        .flowOn(ioDispatcher)
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Számok azonosító szerint (a várólista visszaállításához); a hiányzók kimaradnak. */
    suspend fun tracksByIds(ids: Collection<Long>): Map<Long, Track> = withContext(ioDispatcher) {
        ids.distinct().chunked(900).flatMap { dao.byIds(it) }.associate { it.id to it.toTrack() }
    }

    /** A törölt fájl azonnal kikerül a gyorsítótárból (a teljes újraolvasás ezután fut). */
    suspend fun forget(trackId: Long) = withContext(ioDispatcher) { dao.deleteById(trackId) }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, requiredPermission()) ==
        PackageManager.PERMISSION_GRANTED

    /**
     * Ha van engedély, elindítja a beolvasást és a változásfigyelést – csak egyszer (utána a figyelő
     * frissít). Többször is hívható: a felület minden olyan esetben hívja, amikor engedélyt lát (pl. ha a
     * folyamat engedély nélkül indult, és az engedélyt később a rendszerbeállításokban adták meg).
     */
    fun start() {
        if (started || !hasPermission()) return
        started = true
        registerObserver()
        refresh()
    }

    /** Beolvasás a háttérben. Párhuzamos kérések nem indítanak párhuzamos beolvasást. */
    fun refresh(): Job = scope.launch {
        if (!hasPermission()) return@launch
        scanMutex.withLock {
            _isScanning.value = true
            try {
                val scanned = withContext(ioDispatcher) { scanner.scan() }
                withContext(ioDispatcher) { dao.replaceAll(scanned) }
            } catch (e: SecurityException) {
                Log.w(TAG, "Nincs engedély a zenetár olvasásához", e)
            } catch (e: RuntimeException) {
                // Sérült MediaStore-adat vagy eltávolított tárhely: a régi lista megmarad.
                Log.w(TAG, "A zenetár beolvasása nem sikerült", e)
            } finally {
                _isScanning.value = false
                _hasScannedOnce.value = true
            }
        }
    }

    private var started = false

    private fun registerObserver() {
        if (observerRegistered) return
        observerRegistered = true
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // A MediaStore egy másolás közben sok értesítést küld: összevonjuk őket.
                pendingRescan?.cancel()
                pendingRescan = scope.launch {
                    delay(RESCAN_DEBOUNCE_MS)
                    refresh()
                }
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
    }

    companion object {
        private const val TAG = "LibraryRepository"
        private const val RESCAN_DEBOUNCE_MS = 1_500L

        fun requiredPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
}
