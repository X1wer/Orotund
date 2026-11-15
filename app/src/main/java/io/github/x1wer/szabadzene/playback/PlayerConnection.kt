package io.github.x1wer.szabadzene.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
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

/** Az ismétlési mód a felület számára. */
enum class RepeatSetting { OFF, ALL, ONE }

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
) {
    val hasMedia: Boolean get() = mediaId != null
    val trackId: Long? get() = mediaId?.toLongOrNull()
}

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

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null

    /** A kapcsolódás előtt kért műveletek; kapcsolódás után sorban lefutnak. */
    private val pendingActions = ArrayDeque<(MediaController) -> Unit>()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
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

    fun playNext(track: Track) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItem(track.toMediaItem())
            c.prepare()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
        }
    }

    fun addToQueue(track: Track) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItem(track.toMediaItem())
            c.prepare()
        } else {
            c.addMediaItem(track.toMediaItem())
        }
    }

    fun togglePlayPause() = withController { c -> Util.handlePlayPauseButtonAction(c) }

    fun next() = withController { c -> c.seekToNext() }

    fun previous() = withController { c -> c.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { c -> c.seekTo(positionMs.coerceAtLeast(0L)) }

    fun toggleShuffle() = withController { c -> c.shuffleModeEnabled = !c.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
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

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                publish()
                delay(if (_state.value.isPlaying) 250L else 1_000L)
            }
        }
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
        )
    }
}
