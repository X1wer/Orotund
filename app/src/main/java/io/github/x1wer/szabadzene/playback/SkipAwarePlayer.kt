package io.github.x1wer.szabadzene.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * A munkamenet ezt a lejátszót látja. A rendszer, a fejhallgató vagy más vezérlő
 * előre/vissza tekerési kérése így ugyanazon a közös ugrási logikán megy át,
 * mint az alkalmazás saját gombjai. A beszúrásokról is szól, hogy keverés közben
 * a „Lejátszás következőként” tényleg a következő legyen. Az „Előző” gomb (felület, értesítés,
 * fejhallgató, widget) a beállítható küszöb szerint előbb a szám elejére teker ([PreviousLogic]).
 */
@OptIn(UnstableApi::class)
class SkipAwarePlayer(
    player: Player,
    private val onSkip: (SkipDirection) -> Unit,
    private val stepMs: (SkipDirection) -> Long,
    private val onItemsInserted: (index: Int, count: Int, currentIndexBefore: Int) -> Unit = { _, _, _ -> },
    private val restartThresholdMs: () -> Long = { PreviousLogic.DEFAULT_THRESHOLD_SECONDS * 1000L },
) : ForwardingPlayer(player) {

    override fun getMaxSeekToPreviousPosition(): Long = restartThresholdMs()

    override fun seekToPrevious() {
        val action = PreviousLogic.decide(
            positionMs = currentPosition,
            thresholdMs = restartThresholdMs(),
            hasPrevious = hasPreviousMediaItem(),
            hasMedia = !currentTimeline.isEmpty && !isPlayingAd,
        )
        when (action) {
            PreviousAction.PREVIOUS_ITEM -> seekToPreviousMediaItem()
            PreviousAction.RESTART -> seekTo(0L)
            PreviousAction.NONE -> Unit
        }
    }

    override fun seekBack() = onSkip(SkipDirection.BACK)

    override fun seekForward() = onSkip(SkipDirection.FORWARD)

    override fun getSeekBackIncrement(): Long = stepMs(SkipDirection.BACK)

    override fun getSeekForwardIncrement(): Long = stepMs(SkipDirection.FORWARD)

    override fun addMediaItem(index: Int, mediaItem: MediaItem) = addMediaItems(index, listOf(mediaItem))

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        val currentBefore = currentMediaItemIndex
        val safeIndex = index.coerceIn(0, mediaItemCount)
        super.addMediaItems(safeIndex, mediaItems)
        onItemsInserted(safeIndex, mediaItems.size, currentBefore)
    }

    override fun addMediaItem(mediaItem: MediaItem) = addMediaItems(mediaItemCount, listOf(mediaItem))

    override fun addMediaItems(mediaItems: List<MediaItem>) = addMediaItems(mediaItemCount, mediaItems)
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
