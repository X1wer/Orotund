package io.github.x1wer.szabadzene.ui.player

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.PlaybackUiState
import io.github.x1wer.szabadzene.playback.SkipDirection
import io.github.x1wer.szabadzene.ui.components.CompactSkipButton
import io.github.x1wer.szabadzene.ui.components.SkipButton
import io.github.x1wer.szabadzene.ui.components.TrackArtwork

object MiniPlayerTags {
    const val ROOT = "mini_player"
    const val BODY = "mini_player_body"
}

/**
 * Az alsó minilejátszó. A két ugrógomb itt is közvetlenül elérhető: széles kijelzőn és normál
 * betűméretnél egy sorban, egyébként kétsoros elrendezésben, teljes méretű gombokkal.
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
            val singleRow = maxWidth >= 360.dp && fontScale <= 1.15f
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
                        MiniBody(state, onOpen, Modifier.weight(1f))
                        CompactSkipButton(
                            direction = SkipDirection.BACK,
                            seconds = skip.backSeconds,
                            enabled = state.canSeek,
                            onClick = { callbacks.onSkip(SkipDirection.BACK) },
                        )
                        Spacer(Modifier.width(4.dp))
                        PlayPauseButton(
                            isPlaying = state.playWhenReady,
                            enabled = state.hasMedia,
                            onClick = callbacks.onPlayPause,
                            size = 56,
                        )
                        Spacer(Modifier.width(4.dp))
                        CompactSkipButton(
                            direction = SkipDirection.FORWARD,
                            seconds = skip.forwardSeconds,
                            enabled = state.canSeek,
                            onClick = { callbacks.onSkip(SkipDirection.FORWARD) },
                        )
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniBody(state, onOpen, Modifier.weight(1f))
                        PlayPauseButton(
                            isPlaying = state.playWhenReady,
                            enabled = state.hasMedia,
                            onClick = callbacks.onPlayPause,
                            size = 56,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val showIcon = fontScale < 1.5f
                        SkipButton(
                            direction = SkipDirection.BACK,
                            seconds = skip.backSeconds,
                            enabled = state.canSeek,
                            onClick = { callbacks.onSkip(SkipDirection.BACK) },
                            showIcon = showIcon,
                            modifier = Modifier.weight(1f),
                        )
                        SkipButton(
                            direction = SkipDirection.FORWARD,
                            seconds = skip.forwardSeconds,
                            enabled = state.canSeek,
                            onClick = { callbacks.onSkip(SkipDirection.FORWARD) },
                            showIcon = showIcon,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** A minilejátszó törzse: koppintásra a teljes lejátszó nyílik meg. */
@Composable
private fun MiniBody(state: PlaybackUiState, onOpen: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .testTag(MiniPlayerTags.BODY)
            .heightIn(min = 64.dp)
            .clickable(
                onClickLabel = stringResource(R.string.cd_open_player),
                role = Role.Button,
                onClick = onOpen,
            )
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(trackId = state.trackId, sizePx = 144, modifier = Modifier.size(44.dp))
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
        }
    }
}
