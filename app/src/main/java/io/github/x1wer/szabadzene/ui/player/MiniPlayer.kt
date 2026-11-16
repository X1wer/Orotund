package io.github.x1wer.szabadzene.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.PlaybackUiState
import io.github.x1wer.szabadzene.playback.SkipDirection
import io.github.x1wer.szabadzene.ui.components.CompactSkipButton
import io.github.x1wer.szabadzene.ui.components.TrackArtwork

object MiniPlayerTags {
    const val ROOT = "mini_player"
    const val BODY = "mini_player_body"
}

/** Ekkora vízszintes húzás vált számot a minilejátszó törzsén. */
private val SwipeThreshold = 56.dp

/**
 * Az alsó minilejátszó: borító, cím, előadó, a következő szám, a várólista sorszáma, valamint
 * a vezérlők (−N mp, előző, lejátszás/szünet, következő, +N mp). Az ugrógombok mindig közvetlenül
 * elérhetők, legalább 56 × 56 dp-sek. Széles kijelzőn egy sor, egyébként kétsoros elrendezés
 * (felül a szám adatai, alul a gombok). A törzsre koppintva a teljes lejátszó nyílik meg;
 * vízszintes húzással (balra: következő, jobbra: előző) számot lehet váltani.
 */
@Composable
fun MiniPlayerContent(
    state: PlaybackUiState,
    skip: SkipSettings,
    callbacks: PlayerCallbacks,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = modifier
            .testTag(MiniPlayerTags.ROOT)
            .fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val singleRow = maxWidth >= 600.dp && fontScale <= 1.3f
            Column(Modifier.fillMaxWidth()) {
                val fraction = if (state.durationMs > 0) {
                    (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    drawStopIndicator = {},
                )
                if (singleRow) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniBody(state, callbacks, onOpen, Modifier.weight(1f))
                        MiniControls(state, skip, callbacks, Modifier.width(320.dp))
                        callbacks.onOpenQueue?.let { QueuePositionButton(state, it) }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniBody(state, callbacks, onOpen, Modifier.weight(1f))
                        callbacks.onOpenQueue?.let { QueuePositionButton(state, it) }
                    }
                    MiniControls(
                        state,
                        skip,
                        callbacks,
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

/** −N mp, előző, lejátszás/szünet, következő, +N mp – egyenletesen elosztva. */
@Composable
private fun MiniControls(
    state: PlaybackUiState,
    skip: SkipSettings,
    callbacks: PlayerCallbacks,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactSkipButton(
            direction = SkipDirection.BACK,
            seconds = skip.backSeconds,
            enabled = state.canSeek,
            onClick = { callbacks.onSkip(SkipDirection.BACK) },
        )
        PreviousButton(state, callbacks.onPrevious, size = 48.dp)
        PlayPauseButton(
            isPlaying = state.playWhenReady,
            enabled = state.hasMedia,
            onClick = callbacks.onPlayPause,
            size = 56,
        )
        NextButton(state, callbacks.onNext, size = 48.dp)
        CompactSkipButton(
            direction = SkipDirection.FORWARD,
            seconds = skip.forwardSeconds,
            enabled = state.canSeek,
            onClick = { callbacks.onSkip(SkipDirection.FORWARD) },
        )
    }
}

/** A minilejátszó törzse: koppintásra a teljes lejátszó, vízszintes húzásra számváltás. */
@Composable
private fun MiniBody(state: PlaybackUiState, callbacks: PlayerCallbacks, onOpen: () -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val threshold = with(density) { SwipeThreshold.toPx() }
    val onNext by rememberUpdatedState(callbacks.onNext)
    val onPrevious by rememberUpdatedState(callbacks.onPrevious)
    val hasNext by rememberUpdatedState(state.hasNext)
    val nextLabel = stringResource(R.string.cd_next)
    val previousLabel = stringResource(R.string.cd_previous)
    Row(
        modifier = modifier
            .testTag(MiniPlayerTags.BODY)
            .heightIn(min = 64.dp)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total <= -threshold && hasNext -> onNext()
                            total >= threshold -> onPrevious()
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    total += dragAmount
                }
            }
            .clickable(
                onClickLabel = stringResource(R.string.cd_open_player),
                role = Role.Button,
                onClick = onOpen,
            )
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { onNext(); true },
                    CustomAccessibilityAction(previousLabel) { onPrevious(); true },
                )
            }
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(trackId = state.trackId, sizePx = 144, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.artist ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Nagyon nagy betűnél a következő szám sora elmarad, hogy a lista is látsszon.
            state.nextTitle?.takeIf { density.fontScale < 1.5f }?.let { next ->
                Text(
                    text = stringResource(R.string.next_up, next),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
