package io.github.x1wer.szabadzene.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.x1wer.szabadzene.MainActivity
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.SzabadZeneApp
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A lejátszó és a MediaSession egyetlen otthona. A felület MediaControlleren keresztül
 * kapcsolódik, így forgatás vagy képernyőváltás nem hoz létre új lejátszót.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = MainScope()
    private val skipController = SkipController(clock = SystemClock::elapsedRealtime)
    private var skipSettings = SkipSettings()

    /** Egymást követő lejátszási hibák száma; véd a végtelen továbbugrás ellen. */
    private var consecutiveErrors = 0

    override fun onCreate() {
        super.onCreate()
        val container = (application as SzabadZeneApp).container

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Fejhallgató kihúzásakor vagy Bluetooth-kimenet megszakadásakor szünet.
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PlayerEvents())

        val sessionPlayer = SkipAwarePlayer(
            player = player,
            onSkip = { direction -> performSkip(direction, stepMs = null) },
            stepMs = { direction -> stepFor(direction) },
        )

        session = MediaSession.Builder(this, sessionPlayer)
            .setCallback(SessionCallback())
            .setSessionActivity(sessionActivity())
            .setMediaButtonPreferences(mediaButtons())
            .build()

        scope.launch {
            container.settings.skipSettings.collect { settings ->
                skipSettings = settings
                session?.setMediaButtonPreferences(mediaButtons())
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Ha a felhasználó bezárja az appot, és nem szól zene, a szolgáltatás is álljon le.
        val current = session?.player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0 ||
            current.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun stepFor(direction: SkipDirection): Long = when (direction) {
        SkipDirection.BACK -> skipSettings.backMs
        SkipDirection.FORWARD -> skipSettings.forwardMs
    }

    private fun performSkip(direction: SkipDirection, stepMs: Long?) {
        val step = stepMs?.takeIf { it > 0 } ?: stepFor(direction)
        skipController.skip(PlayerSkipTarget(player), direction, step)
    }

    private fun sessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Az értesítésben és a zárolási képernyőn megjelenő ugrógombok (ahol a rendszer mutatja őket). */
    private fun mediaButtons(): ImmutableList<CommandButton> {
        val back = CommandButton.Builder(backIcon(skipSettings.backSeconds))
            .setDisplayName(getString(R.string.cd_skip_back, skipSettings.backSeconds))
            .setSessionCommand(PlaybackCommands.skipBack)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        val forward = CommandButton.Builder(forwardIcon(skipSettings.forwardSeconds))
            .setDisplayName(getString(R.string.cd_skip_forward, skipSettings.forwardSeconds))
            .setSessionCommand(PlaybackCommands.skipForward)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        return ImmutableList.of(back, forward)
    }

    private fun backIcon(seconds: Int): Int = when (seconds) {
        5 -> CommandButton.ICON_SKIP_BACK_5
        10 -> CommandButton.ICON_SKIP_BACK_10
        15 -> CommandButton.ICON_SKIP_BACK_15
        30 -> CommandButton.ICON_SKIP_BACK_30
        else -> CommandButton.ICON_SKIP_BACK
    }

    private fun forwardIcon(seconds: Int): Int = when (seconds) {
        5 -> CommandButton.ICON_SKIP_FORWARD_5
        10 -> CommandButton.ICON_SKIP_FORWARD_10
        15 -> CommandButton.ICON_SKIP_FORWARD_15
        30 -> CommandButton.ICON_SKIP_FORWARD_30
        else -> CommandButton.ICON_SKIP_FORWARD
    }

    private fun broadcastError(title: String, stopped: Boolean) {
        val args = Bundle().apply {
            putString(PlaybackCommands.EXTRA_TITLE, title)
            putBoolean(PlaybackCommands.EXTRA_STOPPED, stopped)
        }
        session?.broadcastCustomCommand(PlaybackCommands.playbackError, args)
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                skipController.onSeekReported(newPosition.mediaItem?.mediaId, newPosition.positionMs)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            skipController.reset()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) consecutiveErrors = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            val title = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
            Log.w(TAG, "Lejátszási hiba: $title", error)
            consecutiveErrors++
            val canSkip = player.hasNextMediaItem() && consecutiveErrors < player.mediaItemCount
            if (canSkip) {
                // A hibás számot átlépjük, de csak annyiszor, ahány elem van: nincs végtelen próbálkozás.
                broadcastError(title, stopped = false)
                player.seekToNextMediaItem()
                player.prepare()
            } else {
                // Megállunk; a felhasználó választhat másik számot, vagy újra megnyomhatja a lejátszást.
                broadcastError(title, stopped = true)
                consecutiveErrors = 0
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // Más alkalmazás vezérlője szűkebb alapkészletet kap; az ugrás mindenkinek elérhető.
            val base = if (controller.isTrusted) {
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_COMMANDS
            }
            val commands = base.buildUpon()
                .add(PlaybackCommands.skipBack)
                .add(PlaybackCommands.skipForward)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(mediaButtons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val step = args.getLong(PlaybackCommands.EXTRA_STEP_MS, 0L).takeIf { it > 0 }
            when (customCommand.customAction) {
                PlaybackCommands.ACTION_SKIP_BACK -> performSkip(SkipDirection.BACK, step)
                PlaybackCommands.ACTION_SKIP_FORWARD -> performSkip(SkipDirection.FORWARD, step)
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { item -> resolve(item) }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    /** A vezérlőtől érkező elemhez visszaállítja a lejátszható URI-t. */
    private fun resolve(item: MediaItem): MediaItem? {
        if (item.localConfiguration != null) return item
        val uri = item.requestMetadata.mediaUri
            ?: item.mediaId.toLongOrNull()?.let { Track.contentUriFor(it) }
            ?: return null
        return item.buildUpon().setUri(uri).build()
    }

    private companion object {
        const val TAG = "PlaybackService"
    }
}
