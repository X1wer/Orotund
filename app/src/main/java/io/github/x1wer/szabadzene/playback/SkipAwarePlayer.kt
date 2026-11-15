package io.github.x1wer.szabadzene.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * A munkamenet ezt a lejátszót látja. A rendszer, a fejhallgató vagy más vezérlő
 * előre/vissza tekerési kérése így ugyanazon a közös ugrási logikán megy át,
 * mint az alkalmazás saját gombjai.
 */
@OptIn(UnstableApi::class)
class SkipAwarePlayer(
    player: Player,
    private val onSkip: (SkipDirection) -> Unit,
    private val stepMs: (SkipDirection) -> Long,
) : ForwardingPlayer(player) {

    override fun seekBack() = onSkip(SkipDirection.BACK)

    override fun seekForward() = onSkip(SkipDirection.FORWARD)

    override fun getSeekBackIncrement(): Long = stepMs(SkipDirection.BACK)

    override fun getSeekForwardIncrement(): Long = stepMs(SkipDirection.FORWARD)
}

/** A valódi lejátszó illesztése az ugrási logikához. */
class PlayerSkipTarget(private val player: Player) : SkipTarget {
    override val currentMediaId: String?
        get() = player.currentMediaItem?.mediaId

    override val currentPositionMs: Long
        get() = player.currentPosition

    override val durationMs: Long
        get() = player.duration

    override val isSeekable: Boolean
        get() = player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
            player.isCurrentMediaItemSeekable

    override val isPlaying: Boolean
        get() = player.playWhenReady

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun pause() = player.pause()
}
