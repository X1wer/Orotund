package io.github.x1wer.szabadzene.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.PlaybackUiState
import io.github.x1wer.szabadzene.playback.RepeatSetting
import io.github.x1wer.szabadzene.playback.SkipDirection
import io.github.x1wer.szabadzene.ui.components.SkipButton
import io.github.x1wer.szabadzene.ui.formatDuration
import kotlinx.coroutines.delay

/** A lejátszófelületek műveletei. A teljes és a minilejátszó ugyanezt használja. */
data class PlayerCallbacks(
    val onPlayPause: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onSkip: (SkipDirection) -> Unit = {},
    val onToggleShuffle: () -> Unit = {},
    val onCycleRepeat: () -> Unit = {},
    /** Null esetén a kedvenc kapcsoló nem jelenik meg. */
    val onToggleFavorite: (() -> Unit)? = null,
    /** Null esetén a várólista gomb nem jelenik meg. */
    val onOpenQueue: (() -> Unit)? = null,
)

object PlayerTags {
    const val SEEK_BAR = "seek_bar"
    const val PLAY_PAUSE = "play_pause"
    const val PREVIOUS = "previous"
    const val NEXT = "next"
    const val SHUFFLE = "shuffle"
    const val REPEAT = "repeat"
    const val FAVORITE = "favorite"
    const val QUEUE = "queue"
    const val SKIP_ROW = "skip_row"
}

/**
 * Időcsúszka. Húzás közben a felhasználó által választott pozíciót mutatja; a háttérből érkező
 * állapotfrissítés ilyenkor nem rántja vissza.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var settleTarget by remember { mutableStateOf<Long?>(null) }

    // Elengedés után a cél látszik, amíg a lejátszó új pozíciót nem jelent (legfeljebb 1 mp-ig).
    LaunchedEffect(positionMs) { settleTarget = null }
    LaunchedEffect(settleTarget) {
        if (settleTarget != null) {
            delay(1_000)
            settleTarget = null
        }
    }

    val canSeek = enabled && durationMs > 0
    val shownMs = when {
        !canSeek -> positionMs.coerceAtLeast(0L)
        dragFraction != null -> (dragFraction!! * durationMs).toLong()
        settleTarget != null -> settleTarget!!
        else -> positionMs
    }.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val fraction = if (durationMs > 0) shownMs.toFloat() / durationMs else 0f
    val description = stringResource(R.string.cd_seek_bar)

    Column(modifier) {
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                val f = dragFraction
                if (f != null) {
                    val target = (f * durationMs).toLong()
                    settleTarget = target
                    dragFraction = null
                    onSeek(target)
                }
            },
            enabled = canSeek,
            modifier = Modifier
                .testTag(PlayerTags.SEEK_BAR)
                .fillMaxWidth()
                .semantics { contentDescription = description },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(shownMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (durationMs > 0) formatDuration(durationMs) else "–:––",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Az állandóan látható ugrógombok sora: közvetlenül az időcsúszka alatt. */
@Composable
fun SkipRow(
    state: PlaybackUiState,
    skip: SkipSettings,
    onSkip: (SkipDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showIcon = LocalDensity.current.fontScale < 1.5f
    Row(
        modifier = modifier
            .testTag(PlayerTags.SKIP_ROW)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkipButton(
            direction = SkipDirection.BACK,
            seconds = skip.backSeconds,
            enabled = state.canSeek,
            onClick = { onSkip(SkipDirection.BACK) },
            showIcon = showIcon,
            modifier = Modifier.weight(1f, fill = false),
        )
        SkipButton(
            direction = SkipDirection.FORWARD,
            seconds = skip.forwardSeconds,
            enabled = state.canSeek,
            onClick = { onSkip(SkipDirection.FORWARD) },
            showIcon = showIcon,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Külön vezérlősor: előző szám, nagy lejátszás/szünet, következő szám. */
@Composable
fun TransportRow(
    state: PlaybackUiState,
    callbacks: PlayerCallbacks,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = callbacks.onPrevious,
            enabled = state.hasPrevious,
            modifier = Modifier
                .testTag(PlayerTags.PREVIOUS)
                .size(56.dp),
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous),
                modifier = Modifier.size(36.dp),
            )
        }
        PlayPauseButton(
            isPlaying = state.playWhenReady,
            enabled = state.hasMedia,
            onClick = callbacks.onPlayPause,
            size = 76,
        )
        IconButton(
            onClick = callbacks.onNext,
            enabled = state.hasNext,
            modifier = Modifier
                .testTag(PlayerTags.NEXT)
                .size(56.dp),
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Int,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .testTag(PlayerTags.PLAY_PAUSE)
            .size(size.dp),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

/** Véletlen sorrend és ismétlés. */
@Composable
fun ModeRow(
    state: PlaybackUiState,
    callbacks: PlayerCallbacks,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = LocalContentColor.current.copy(alpha = 0.7f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = callbacks.onToggleShuffle,
            enabled = state.hasMedia,
            colors = IconButtonDefaults.iconButtonColors(contentColor = if (state.shuffle) active else inactive),
            modifier = Modifier
                .testTag(PlayerTags.SHUFFLE)
                .size(56.dp),
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = stringResource(
                    if (state.shuffle) R.string.cd_shuffle_on else R.string.cd_shuffle_off,
                ),
            )
        }
        callbacks.onToggleFavorite?.let { toggle ->
            IconButton(
                onClick = toggle,
                enabled = state.hasMedia,
                colors = IconButtonDefaults.iconButtonColors(contentColor = if (isFavorite) active else inactive),
                modifier = Modifier
                    .testTag(PlayerTags.FAVORITE)
                    .size(56.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(if (isFavorite) R.string.cd_favorite_on else R.string.cd_favorite_off),
                )
            }
        }
        callbacks.onOpenQueue?.let { open ->
            IconButton(
                onClick = open,
                enabled = state.hasMedia,
                colors = IconButtonDefaults.iconButtonColors(contentColor = inactive),
                modifier = Modifier
                    .testTag(PlayerTags.QUEUE)
                    .size(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = stringResource(R.string.cd_open_queue))
            }
        }
        val repeatOn = state.repeat != RepeatSetting.OFF
        IconButton(
            onClick = callbacks.onCycleRepeat,
            enabled = state.hasMedia,
            colors = IconButtonDefaults.iconButtonColors(contentColor = if (repeatOn) active else inactive),
            modifier = Modifier
                .testTag(PlayerTags.REPEAT)
                .size(56.dp),
        ) {
            Icon(
                imageVector = if (state.repeat == RepeatSetting.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = stringResource(
                    when (state.repeat) {
                        RepeatSetting.OFF -> R.string.cd_repeat_off
                        RepeatSetting.ALL -> R.string.cd_repeat_all
                        RepeatSetting.ONE -> R.string.cd_repeat_one
                    },
                ),
            )
        }
    }
}
