package io.github.x1wer.szabadzene.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.x1wer.szabadzene.data.library.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Az ismétlési mód a felület számára. [STOP_AFTER_CURRENT]: az aktuális szám végén megáll
 * (a lejátszóban ilyenkor nincs ismétlés; a módot a beállítás tárolja, a szolgáltatás hajtja végre).
 */
enum class RepeatSetting { STOP_AFTER_CURRENT, ONE, OFF, ALL }

/** A lejátszás egyetlen hiteles állapota, ahogy a felület látja. */
data class PlaybackUiState(
    val connected: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canSeek: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val repeat: RepeatSetting = RepeatSetting.OFF,
    val shuffle: Boolean = false,
    /** A következő szám címe (ismétlés és keverés szerint), ha van. */
    val nextTitle: String? = null,
    /** Az aktuális szám helye a lejátszási sorrendben (1-től), 0, ha nincs. */
    val queuePosition: Int = 0,
    val queueSize: Int = 0,
) {
    val hasMedia: Boolean get() = mediaId != null
    val trackId: Long? get() = mediaId?.toLongOrNull()
}

/** A várólista egy eleme a felület számára. [index] az alaplista-index (a műveletekhez). */
data class QueueEntry(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val isCurrent: Boolean,
)

sealed interface PlaybackEvent {
    data class Error(val title: String, val stopped: Boolean) : PlaybackEvent
}

/**
 * A felület kapcsolata a lejátszószolgáltatással. A vezérlőt az Activity láthatósága idejére
 * tartja fenn; a lejátszó maga a szolgáltatásban él, így ettől nem áll le a zene.
 */
@OptIn(UnstableApi::class)
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())

    /** A várólista lejátszási sorrendben (keverésnél a kevert sorrendben). */
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)

    /** Az elalvásidőzítő állapota (a szolgáltatás munkamenet-extráiból). */
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null

    /** A kapcsolódás előtt kért műveletek; kapcsolódás után sorban lefutnak. */
    private val pendingActions = ArrayDeque<(MediaController) -> Unit>()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // Előbb a várólista: a lejátszási állapot ebből számolja a sorszámot.
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                )
            ) {
                publishQueue()
            }
            publish()
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (command.customAction == PlaybackCommands.EVENT_PLAYBACK_ERROR) {
                _events.tryEmit(
                    PlaybackEvent.Error(
                        title = args.getString(PlaybackCommands.EXTRA_TITLE).orEmpty(),
                        stopped = args.getBoolean(PlaybackCommands.EXTRA_STOPPED, false),
                    ),
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            _sleepTimer.value = sleepTimerStateFrom(extras)
        }

        override fun onDisconnected(controller: MediaController) {
            if (this@PlayerConnection.controller === controller) {
                this@PlayerConnection.controller = null
                controllerFuture = null
                positionJob?.cancel()
                _state.value = _state.value.copy(connected = false)
            }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                val connected = runCatching { future.get() }.getOrNull() ?: run {
                    controllerFuture = null
                    return@addListener
                }
                controller = connected
                connected.addListener(playerListener)
                _sleepTimer.value = sleepTimerStateFrom(connected.sessionExtras)
                publishQueue()
                publish()
                while (pendingActions.isNotEmpty()) pendingActions.removeFirst().invoke(connected)
                startPositionUpdates()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun disconnect() {
        positionJob?.cancel()
        positionJob = null
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    private fun withController(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) {
            action(current)
            publish()
        } else {
            pendingActions.addLast(action)
            connect()
        }
    }

    /** A teljes lista lejátszása a kiválasztott számtól. */
    fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, items.lastIndex)
        withController { c ->
            c.setMediaItems(items, index, C.TIME_UNSET)
            c.prepare()
            c.play()
        }
    }

    /** A lista lejátszása sorrendben, az első számtól (a keverés kikapcsol). */
    fun playInOrder(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem() }
        withController { c ->
            c.shuffleModeEnabled = false
            c.setMediaItems(items, 0, C.TIME_UNSET)
            c.prepare()
            c.play()
        }
    }

    /**
     * A zenetár Lejátszás ([shuffle] = hamis) és Keverés gombja. Ha [keepCurrent] és épp szól zene, a
     * mostani szám végigszól, utána folytatódik az új szabály szerint; különben azonnal indul. Igaz, ha
     * a mostani szám megmaradt.
     */
    fun playAll(tracks: List<Track>, shuffle: Boolean, keepCurrent: Boolean): Boolean {
        if (tracks.isEmpty()) return false
        val c = controller
        val playing = c != null && c.currentMediaItem != null && c.playWhenReady &&
            c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE
        if (keepCurrent && playing) {
            val args = Bundle().apply {
                putLongArray(PlaybackCommands.EXTRA_TRACK_IDS, tracks.map { it.id }.toLongArray())
                putBoolean(PlaybackCommands.EXTRA_SHUFFLE, shuffle)
            }
            c.sendCustomCommand(PlaybackCommands.playKeepCurrent, args)
            return true
        }
        if (shuffle) playShuffled(tracks) else playInOrder(tracks)
        return false
    }

    /** A lista lejátszása kevert sorrendben (a keverés bekapcsol). */
    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem() }
        withController { c ->
            c.shuffleModeEnabled = true
            // resetPosition = true: a kevert sorrend első elemével indul.
            c.setMediaItems(items, true)
            c.prepare()
            c.play()
        }
    }

    /** Számok beszúrása közvetlenül az aktuális szám után (keverés közben is ezek jönnek). */
    fun playNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem() }
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(items)
                c.prepare()
            } else {
                c.addMediaItems(c.currentMediaItemIndex + 1, items)
            }
        }
    }

    /** Számok hozzáfűzése a várólista végéhez. */
    fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem() }
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(items)
                c.prepare()
            } else {
                c.addMediaItems(items)
            }
        }
    }

    fun playNext(track: Track) = playNext(listOf(track))

    fun addToQueue(track: Track) = addToQueue(listOf(track))

    /** A várólista egy elemének lejátszása (alaplista-index). */
    fun playQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, C.TIME_UNSET)
            c.prepare()
            c.play()
        }
    }

    /** Átrendezés a várólistában (alaplista-indexek). */
    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) {
            c.moveMediaItem(from, to)
        }
    }

    /** Eltávolítás a várólistából (a fájlt nem érinti). */
    fun removeQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun pause() = withController { c -> c.pause() }

    fun togglePlayPause() = withController { c -> Util.handlePlayPauseButtonAction(c) }

    fun next() = withController { c -> c.seekToNext() }

    fun previous() = withController { c -> c.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { c -> c.seekTo(positionMs.coerceAtLeast(0L)) }

    fun toggleShuffle() = withController { c -> c.shuffleModeEnabled = !c.shuffleModeEnabled }

    /**
     * Ismétlési mód a lejátszóban. A „leállás az aktuális szám végén” módot a beállítás tárolja
     * (a hívó írja); itt ilyenkor csak az ismétlés kapcsol ki.
     */
    fun setRepeat(mode: RepeatSetting) = withController { c ->
        c.repeatMode = when (mode) {
            RepeatSetting.ALL -> Player.REPEAT_MODE_ALL
            RepeatSetting.ONE -> Player.REPEAT_MODE_ONE
            RepeatSetting.OFF, RepeatSetting.STOP_AFTER_CURRENT -> Player.REPEAT_MODE_OFF
        }
    }

    /**
     * Ugrás az aktuális számon belül. A tényleges számítás a szolgáltatásban, a közös
     * ugrási logikában történik; a felület csak az irányt és a kijelzett mértéket küldi.
     */
    fun skip(direction: SkipDirection, stepSeconds: Int) = withController { c ->
        val command = when (direction) {
            SkipDirection.BACK -> PlaybackCommands.skipBack
            SkipDirection.FORWARD -> PlaybackCommands.skipForward
        }
        val args = Bundle().apply { putLong(PlaybackCommands.EXTRA_STEP_MS, stepSeconds * 1000L) }
        c.sendCustomCommand(command, args)
    }

    /** Elalvásidőzítő: leállás [minutes] perc múlva. */
    fun startSleepTimer(minutes: Int) = withController { c ->
        val args = Bundle().apply { putLong(PlaybackCommands.EXTRA_DURATION_MS, minutes * 60_000L) }
        c.sendCustomCommand(PlaybackCommands.sleepTimerSet, args)
    }

    /** Elalvásidőzítő: leállás az aktuális szám végén. */
    fun startSleepTimerEndOfTrack() = withController { c ->
        val args = Bundle().apply { putBoolean(PlaybackCommands.EXTRA_END_OF_TRACK, true) }
        c.sendCustomCommand(PlaybackCommands.sleepTimerSet, args)
    }

    fun cancelSleepTimer() = withController { c -> c.sendCustomCommand(PlaybackCommands.sleepTimerCancel, Bundle.EMPTY) }

    /** A fájl megváltozott (címke, borító): a lejátszó töltse újra ezeket a számokat. */
    fun reloadTracks(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        withController { c ->
            val args = Bundle().apply { putLongArray(PlaybackCommands.EXTRA_TRACK_IDS, ids.toLongArray()) }
            c.sendCustomCommand(PlaybackCommands.reloadTracks, args)
        }
    }

    /** A szám minden előfordulásának eltávolítása a várólistából (pl. a fájl törlése után). */
    fun removeTrackEverywhere(trackId: Long) = withController { c ->
        val ids = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        QueueLogic.indicesToRemove(ids, trackId).forEach { c.removeMediaItem(it) }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                publish()
                delay(if (_state.value.isPlaying) 250L else 1_000L)
            }
        }
    }

    private fun publishQueue() {
        val c = controller ?: return
        val timeline = c.currentTimeline
        if (timeline.isEmpty) {
            _queue.value = emptyList()
            return
        }
        val shuffle = c.shuffleModeEnabled
        val window = Timeline.Window()
        val current = c.currentMediaItemIndex
        val result = ArrayList<QueueEntry>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(shuffle)
        while (index != C.INDEX_UNSET && result.size < timeline.windowCount) {
            val item = timeline.getWindow(index, window).mediaItem
            result += QueueEntry(
                index = index,
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString(),
                isCurrent = index == current,
            )
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        _queue.value = result
    }

    private fun publish() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val metadata = c.mediaMetadata
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        _state.value = PlaybackUiState(
            connected = true,
            mediaId = item?.mediaId,
            title = metadata.title?.toString() ?: item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = metadata.artist?.toString(),
            album = metadata.albumTitle?.toString(),
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE),
            durationMs = duration,
            canSeek = item != null && duration > 0 && c.isCurrentMediaItemSeekable &&
                c.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            hasPrevious = item != null,
            hasNext = c.hasNextMediaItem(),
            repeat = when (c.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatSetting.ALL
                Player.REPEAT_MODE_ONE -> RepeatSetting.ONE
                else -> RepeatSetting.OFF
            },
            shuffle = c.shuffleModeEnabled,
            nextTitle = c.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET && it < c.mediaItemCount }
                ?.let { c.getMediaItemAt(it).mediaMetadata.title?.toString() },
            queuePosition = _queue.value.indexOfFirst { it.isCurrent } + 1,
            queueSize = c.mediaItemCount,
        )
    }
}
