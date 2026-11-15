package io.github.x1wer.szabadzene.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.PlaybackUiState
import io.github.x1wer.szabadzene.ui.components.TrackArtwork

object FullPlayerTags {
    const val ROOT = "full_player"
    const val MINIMIZE = "full_player_minimize"
    const val MORE = "full_player_more"
    const val ARTWORK = "full_player_artwork"
}

/** Ennél kisebb borítót nem mutatunk: inkább eltűnik, hogy a kezelőszervek elférjenek. */
private val MinArtwork = 96.dp

/**
 * A teljes lejátszó (állapot nélküli, ezért tesztelhető).
 *
 * Az elrendezés először a kezelőszerveknek ad helyet, a borító csak a maradékot kapja meg,
 * és ha nem fér el, eltűnik. Az ugrógombok sosem kerülnek menübe és nem kerülnek egymásra.
 */
@Composable
fun FullPlayerContent(
    state: PlaybackUiState,
    skip: SkipSettings,
    callbacks: PlayerCallbacks,
    onMinimize: () -> Unit,
    onMoreActions: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
) {
    Surface(modifier = modifier.testTag(FullPlayerTags.ROOT).fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (maxWidth > maxHeight) {
                LandscapePlayer(state, skip, callbacks, onMinimize, onMoreActions, maxWidth, maxHeight, isFavorite)
            } else {
                PortraitPlayer(state, skip, callbacks, onMinimize, onMoreActions, maxHeight, isFavorite)
            }
        }
    }
}

/** Álló: felül a fejléc, alul a kezelőszervek, köztük a borító a fennmaradó helyen. */
@Composable
private fun PortraitPlayer(
    state: PlaybackUiState,
    skip: SkipSettings,
    callbacks: PlayerCallbacks,
    onMinimize: () -> Unit,
    onMoreActions: (() -> Unit)?,
    viewportHeight: Dp,
    isFavorite: Boolean,
) {
    val roomy = viewportHeight >= 640.dp && LocalDensity.current.fontScale < 1.5f
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (roomy) 24.dp else 16.dp),
    ) {
        ArtFitColumn(
            viewportHeight = viewportHeight,
            top = { PlayerTopBar(onMinimize, onMoreActions) },
            art = { size -> PlayerArtwork(state, size) },
            bottom = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PlayerDetails(state, compact = !roomy)
                    Spacer(Modifier.height(8.dp))
                    PlayerSeekBlock(state, callbacks)
                    Spacer(Modifier.height(4.dp))
                    PlayerButtonsBlock(state, skip, callbacks, isFavorite)
                    Spacer(Modifier.height(8.dp))
                }
            },
        )
    }
}

/**
 * Fekvő: két oszlop (bal: cím és csúszka, jobb: ugrógombok és vezérlők), mellettük a borító,
 * ha mindkét oszlop mellett is elfér. Mindkét oszlop görgethető végső tartalékként.
 */
@Composable
private fun LandscapePlayer(
    state: PlaybackUiState,
    skip: SkipSettings,
    callbacks: PlayerCallbacks,
    onMinimize: () -> Unit,
    onMoreActions: (() -> Unit)?,
    maxWidth: Dp,
    maxHeight: Dp,
    isFavorite: Boolean,
) {
    val fontScale = LocalDensity.current.fontScale
    val artSize = min(maxHeight - 32.dp, maxWidth * 0.32f)
    val columnWidthWithArt = (maxWidth - artSize - 64.dp) / 2
    val showArt = artSize >= 120.dp && fontScale < 1.5f && columnWidthWithArt >= 240.dp
    Row(Modifier.fillMaxSize()) {
        if (showArt) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .padding(start = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                PlayerArtwork(state, artSize)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            PlayerTopBar(onMinimize, onMoreActions)
            PlayerDetails(state, compact = true)
            Spacer(Modifier.height(8.dp))
            PlayerSeekBlock(state, callbacks)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.Center,
            ) {
                PlayerButtonsBlock(state, skip, callbacks, isFavorite)
            }
        }
    }
}

/**
 * Egyedi elrendezés: a fejlécet és az alsó blokkot méri először, a borító a maradék helyet kapja
 * (legfeljebb a teljes szélességet). Ha a maradék kevesebb [MinArtwork]-nél, a borító elmarad.
 * Az alsó blokk a képernyő aljához igazodik; ha a tartalom még így sem fér el, a szülő görget.
 */
@Composable
private fun ArtFitColumn(
    viewportHeight: Dp,
    top: @Composable () -> Unit,
    art: @Composable (Dp) -> Unit,
    bottom: @Composable () -> Unit,
) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val topPlaceables = subcompose("top", top).map { it.measure(loose) }
        val bottomPlaceables = subcompose("bottom", bottom).map { it.measure(loose) }
        val topHeight = topPlaceables.sumOf { it.height }
        val bottomHeight = bottomPlaceables.sumOf { it.height }
        val viewport = viewportHeight.roundToPx()
        val gap = 12.dp.roundToPx()
        val available = viewport - topHeight - bottomHeight - 2 * gap
        val artPx = minOf(width, available, 520.dp.roundToPx()).takeIf { it >= MinArtwork.roundToPx() } ?: 0
        val artPlaceables = if (artPx > 0) {
            subcompose("art") { art(artPx.toDp()) }.map { it.measure(Constraints.fixed(artPx, artPx)) }
        } else {
            emptyList()
        }
        val contentHeight = topHeight + bottomHeight + if (artPx > 0) artPx + 2 * gap else 0
        val totalHeight = maxOf(viewport, contentHeight)
        layout(width, totalHeight) {
            var y = 0
            topPlaceables.forEach {
                it.place(0, y)
                y += it.height
            }
            val bottomTop = totalHeight - bottomHeight
            if (artPx > 0) {
                val artTop = y + (bottomTop - y - artPx) / 2
                artPlaceables.forEach { it.place((width - artPx) / 2, artTop) }
            }
            var by = bottomTop
            bottomPlaceables.forEach {
                it.place((width - it.width) / 2, by)
                by += it.height
            }
        }
    }
}

@Composable
private fun PlayerTopBar(onMinimize: () -> Unit, onMoreActions: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onMinimize,
            modifier = Modifier
                .testTag(FullPlayerTags.MINIMIZE)
                .size(56.dp),
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_minimize),
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (onMoreActions != null) {
            IconButton(
                onClick = onMoreActions,
                modifier = Modifier
                    .testTag(FullPlayerTags.MORE)
                    .size(56.dp),
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
            }
        }
    }
}

@Composable
private fun PlayerArtwork(state: PlaybackUiState, size: Dp) {
    TrackArtwork(
        trackId = state.trackId,
        sizePx = 720,
        cornerRadius = 20.dp,
        modifier = Modifier
            .testTag(FullPlayerTags.ARTWORK)
            .size(size),
    )
}

@Composable
private fun PlayerDetails(state: PlaybackUiState, compact: Boolean) {
    val title = state.title.ifBlank { stringResource(R.string.player_nothing_loaded) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = state.artist ?: stringResource(R.string.unknown_artist),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!compact) {
            Text(
                text = state.album ?: stringResource(R.string.unknown_album),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerSeekBlock(state: PlaybackUiState, callbacks: PlayerCallbacks) {
    SeekBar(
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        enabled = state.canSeek,
        onSeek = callbacks.onSeek,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Az ugrógombok (közvetlenül a csúszka alatt), alatta a vezérlősor és a módok. */
@Composable
private fun PlayerButtonsBlock(
    state: PlaybackUiState,
    skip: SkipSettings,
    callbacks: PlayerCallbacks,
    isFavorite: Boolean,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SkipRow(state = state, skip = skip, onSkip = callbacks.onSkip)
        Spacer(Modifier.height(12.dp))
        TransportRow(state = state, callbacks = callbacks)
        Spacer(Modifier.height(4.dp))
        ModeRow(state = state, callbacks = callbacks, isFavorite = isFavorite)
    }
}
