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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * A zenetár egyetlen forrása. A MediaStore-t a háttérben olvassa be, az eredményt a helyi
 * adatbázisban tárolja, így képernyőváltáskor nem kell újraolvasni a teljes könyvtárat.
 */
class LibraryRepository(
    private val context: Context,
    private val dao: TrackDao,
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

    /** Minden szám, magyar ábécé szerint cím alapján rendezve. */
    val tracks: Flow<List<Track>> = dao.observeAll().map { entities ->
        val collator = Collator.getInstance(Locale.forLanguageTag("hu-HU")).apply {
            strength = Collator.SECONDARY
        }
        entities.map { it.toTrack() }.sortedWith { a, b -> collator.compare(a.title, b.title) }
    }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, requiredPermission()) ==
        PackageManager.PERMISSION_GRANTED

    /** Ha van engedély, elindítja a beolvasást és a változásfigyelést. */
    fun start() {
        if (!hasPermission()) return
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
