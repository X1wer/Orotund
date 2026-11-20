package io.github.x1wer.szabadzene.playback

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.integration.AlarmBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Ébresztés a Zengő saját lejátszóján (lásd [AlarmBridge] 2-es változat). Az ébresztőalkalmazás a
 * munkamenet-parancsokkal indítja, hangosítja és fejezi be; a szolgáltatás ebből csak az állapotot
 * kérdezi (ismétlés, „leállás a szám végén”, fejhallgató-kihúzás).
 *
 * Amíg tart: a lejátszás némán indul, a hangerőt az ébresztő adja meg; a felhasználó ismétlési módját
 * ideiglenesen felülírja; hangos ébresztésnél a riasztási hangfolyamon szól; fejhallgatós ébresztésnél
 * a kért kimenetet részesíti előnyben, és kihúzáskor mindig megáll. A végén mindez visszaáll.
 *
 * A fő szálon használandó.
 */
@OptIn(UnstableApi::class)
internal class AlarmPlayback(
    private val context: Context,
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    /** A lista (vagy a Kedvencek) még elérhető számai, a lista sorrendjében. */
    private val loadTracks: suspend (playlistId: Long) -> List<Track>,
    private val applyShuffleSequence: (List<Int>) -> Unit,
    /** Az ismétlés / „leállás a szám végén” / kihúzás-kezelés újraszámolása. */
    private val onModesChanged: () -> Unit,
) {
    private data class Active(
        val ownerPackage: String,
        val savedRepeatMode: Int,
        val alarmStream: Boolean,
        val stopAfterFirst: Boolean,
        val repeatLast: Boolean,
    )

    private var active: Active? = null
    private var lastKey: String? = null
    private var lastQueue: List<String> = emptyList()
    private var fadeJob: Job? = null

    val isActive: Boolean get() = active != null

    /** Ébresztés közben a „leállás a szám végén” helyett ez érvényes (null: nincs ébresztés). */
    val stopAfterFirst: Boolean? get() = active?.stopAfterFirst

    suspend fun play(ownerPackage: String, args: Bundle): SessionResult {
        val key = args.getString(AlarmBridge.EXTRA_KEY).orEmpty()
        val preferResume = args.getBoolean(AlarmBridge.EXTRA_PREFER_RESUME, false)
        val shuffle = args.getBoolean(AlarmBridge.EXTRA_SHUFFLE, false)
        val resume = preferResume && key.isNotEmpty() && key == lastKey && player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_ENDED && currentIds() == lastQueue
        // A lista a lejátszó módosítása előtt készül el: elutasított kérés semmit nem változtat.
        val fresh = if (resume) {
            null
        } else {
            val tracks = loadTracks(args.getLong(AlarmBridge.EXTRA_PLAYLIST_ID, AlarmBridge.FAVORITES_ID))
            val start = args.getLong(AlarmBridge.EXTRA_START_TRACK_ID, -1L).takeIf { it > 0 }
            val queue = AlarmBridge.alarmQueue(tracks.map { it.id }, start, shuffle, Random.Default)
                ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
            tracks to queue
        }
        fadeJob?.cancel()
        // Néma indulás: hallható csak az ébresztő ellenőrzése után lesz.
        player.volume = 0f
        if (fresh != null) {
            val (tracks, queue) = fresh
            val byId = tracks.associateBy { it.id }
            player.setMediaItems(queue.trackIds.map { byId.getValue(it).toMediaItem() }, queue.startIndex, 0L)
            player.shuffleModeEnabled = shuffle
            queue.shuffleOrder?.let(applyShuffleSequence)
        }
        val previous = active
        val alarmStream = args.getBoolean(AlarmBridge.EXTRA_ALARM_STREAM, false)
        active = Active(
            ownerPackage = ownerPackage,
            savedRepeatMode = previous?.savedRepeatMode ?: player.repeatMode,
            alarmStream = alarmStream,
            stopAfterFirst = args.getBoolean(AlarmBridge.EXTRA_STOP_AFTER_FIRST, false),
            repeatLast = args.getBoolean(AlarmBridge.EXTRA_REPEAT_LAST, false),
        )
        player.repeatMode = AlarmBridge.repeatModeFor(args.getString(AlarmBridge.EXTRA_REPEAT))
        // A Media3 automatikus hangfókusz-kezelése csak médiára/játékra működik (riasztásnál kivételt
        // dob): a riasztási hangfolyamon a fókuszt magunk kérjük, és a végén elengedjük.
        if (alarmStream) {
            player.setAudioAttributes(ALARM_ATTRIBUTES, false)
            requestAlarmFocus()
        } else {
            abandonAlarmFocus()
            player.setAudioAttributes(MEDIA_ATTRIBUTES, true)
        }
        player.setPreferredAudioDevice(outputDevice(args.getInt(AlarmBridge.EXTRA_DEVICE_ID, 0)))
        onModesChanged()
        onItemTransition()
        player.prepare()
        player.play()
        lastKey = key
        lastQueue = currentIds()
        return SessionResult(
            SessionResult.RESULT_SUCCESS,
            Bundle().apply {
                putInt(AlarmBridge.RESULT_TRACK_COUNT, player.mediaItemCount)
                putBoolean(AlarmBridge.RESULT_RESUMED, resume)
            },
        )
    }

    fun volume(args: Bundle) {
        if (active == null) return
        fadeTo(args.getFloat(AlarmBridge.EXTRA_VOLUME, 0f).coerceIn(0f, 1f), args.getLong(AlarmBridge.EXTRA_FADE_MS, 0L))
    }

    fun finish(keepPlaying: Boolean) {
        val a = active ?: return
        active = null
        fadeJob?.cancel()
        if (!keepPlaying) player.pause()
        player.repeatMode = a.savedRepeatMode
        if (a.alarmStream) {
            abandonAlarmFocus()
            player.setAudioAttributes(MEDIA_ATTRIBUTES, true)
        }
        player.setPreferredAudioDevice(null)
        onModesChanged()
        if (keepPlaying) fadeTo(1f, KEEP_PLAYING_FADE_MS) else player.volume = 1f
        lastQueue = currentIds()
    }

    /** Az ébresztést indító app vezérlője megszakadt (pl. leállt): a zene szól tovább, a szokásos módon. */
    fun onControllerDisconnected(packageName: String) {
        if (active?.ownerPackage == packageName) finish(keepPlaying = true)
    }

    /** „Az utolsó dal ismétlése”: amint az utolsó szám szól, azt ismétli. */
    fun onItemTransition() {
        val a = active ?: return
        if (a.repeatLast && player.repeatMode == Player.REPEAT_MODE_OFF && !player.hasNextMediaItem()) {
            player.repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    private fun fadeTo(target: Float, durationMs: Long) {
        fadeJob?.cancel()
        val from = player.volume
        if (durationMs <= 0 || from == target) {
            player.volume = target
            return
        }
        fadeJob = scope.launch {
            val steps = (durationMs / FADE_TICK_MS).coerceAtLeast(1)
            for (i in 1..steps) {
                delay(FADE_TICK_MS)
                player.volume = from + (target - from) * i / steps
            }
            player.volume = target
        }
    }

    private fun currentIds(): List<String> = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }

    private var focusRequest: AudioFocusRequest? = null

    /** Hangos ébresztés: átmeneti fókusz a riasztási hangfolyamon (ha nem kapjuk meg, akkor is szól). */
    private fun requestAlarmFocus() {
        if (focusRequest != null) return
        val manager = context.getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        runCatching { manager.requestAudioFocus(request) }
        focusRequest = request
    }

    private fun abandonAlarmFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { context.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request) }
    }

    private fun outputDevice(id: Int): AudioDeviceInfo? {
        if (id == 0) return null
        val manager = context.getSystemService(AudioManager::class.java) ?: return null
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
    }

    companion object {
        private const val FADE_TICK_MS = 50L
        private const val KEEP_PLAYING_FADE_MS = 1_500L

        val MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        /** Hangos ébresztés: riasztási hangfolyam (a telefon ébresztési hangereje, a „Ne zavarjanak” alatt is). */
        private val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        /** Megadta-e a felhasználó a hívó appnak a listák ébresztési használatát (vagy a saját app-e). */
        fun isAllowed(context: Context, packageName: String): Boolean =
            packageName == context.packageName ||
                context.packageManager.checkPermission(
                    context.packageName + ".permission.ALARM_PLAYLISTS",
                    packageName,
                ) == PackageManager.PERMISSION_GRANTED
    }
}
