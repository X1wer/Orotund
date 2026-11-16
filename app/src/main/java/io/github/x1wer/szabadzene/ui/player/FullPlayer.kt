package io.github.x1wer.szabadzene.ui.player

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import io.github.x1wer.szabadzene.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.PlaybackUiState
import io.github.x1wer.szabadzene.playback.SleepTimerState
import io.github.x1wer.szabadzene.ui.components.TrackArtwork

object FullPlayerTags {
    const val ROOT = "full_player"
    const val MINIMIZE = "full_player_minimize"
    const val MORE = "full_player_more"
    const val ARTWORK = "full_player_artwork"
    const val TAB_SONG = "full_player_tab_song"
    const val TAB_LYRICS = "full_player_tab_lyrics"
    const val LYRICS = "full_player_lyrics"
}

/** Ennél kisebb borítót nem mutatunk: inkább eltűnik, hogy a kezelőszervek elférjenek. */
private val MinArtwork = 96.dp

/** A dalszöveg ennél kisebb helyen is megjelenik (ilyenkor a képernyő görgethető). */
private val MinLyricsHeight = 180.dp

/** A teljes lejátszó állapotai és kiegészítői, hogy a hívási lista rövid maradjon. */
data class FullPlayerExtras(
    val isFavorite: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState.Off,
    val speed: Float = 1f,
)

/**
 * A teljes lejátszó (állapot nélküli, ezért tesztelhető).
 *
 * Fentről lefelé: fejléc (kicsinyítés, várólista sorszámmal, Dal/Dalszöveg, szám menüje),
 * borító vagy dalszöveg, cím, előadó, a következő szám, műveletsor (sebesség, hangszínszabályzó,
 * kedvenc, listához adás, időzítő, hangerő), időcsúszka, közvetlenül alatta az ugrógombok, végül a
 * vezérlősor (keverés, előző, lejátszás, következő, ismétlés).
 *
 * Az elrendezés először a kezelőszerveknek ad helyet, a borító csak a maradékot kapja meg,
 * és ha nem fér el, eltűnik. Az ugrógombok sosem kerülnek menübe és nem kerülnek egymásra.
 * [lyrics] null esetén nincs Dalszöveg fül.
 */
@Composable
fun FullPlayerContent(
    state: PlaybackUiState,
    skip: SkipSettings,
    callbacks: PlayerCallbacks,
    onMinimize: () -> Unit,
    onMoreActions: (() -> Unit)?,
    modifier: Modifier = Modifier,
    extras: FullPlayerExtras = FullPlayerExtras(),
    lyrics: (@Composable (Modifier) -> Unit)? = null,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val lyricsShown = showLyrics && lyrics != null
    val topBar: @Composable () -> Unit = {
        PlayerTopBar(
            state = state,
            callbacks = callbacks,
            onMinimize = onMinimize,
            onMoreActions = onMoreActions,
            hasLyrics = lyrics != null,
            showLyrics = lyricsShown,
            onShowLyrics = { showLyrics = it },
        )
    }
    val parts = PlayerParts(state, skip, callbacks, extras, topBar)
    Surface(modifier = modifier.testTag(FullPlayerTags.ROOT).fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val lyricsSlot = if (lyricsShown) lyrics else null
            if (maxWidth > maxHeight) {
                LandscapePlayer(parts, maxWidth, maxHeight, lyricsSlot)
            } else {
                PortraitPlayer(parts, maxHeight, lyricsSlot)
            }
        }
    }
}

private class PlayerParts(
    val state: PlaybackUiState,
    val skip: SkipSettings,
    val callbacks: PlayerCallbacks,
    val extras: FullPlayerExtras,
    val topBar: @Composable () -> Unit,
)

/** Álló: felül a fejléc, alul a kezelőszervek, köztük a borító (vagy a dalszöveg) a fennmaradó helyen. */
@Composable
private fun PortraitPlayer(parts: PlayerParts, viewportHeight: Dp, lyrics: (@Composable (Modifier) -> Unit)?) {
    val roomy = viewportHeight >= 640.dp && LocalDensity.current.fontScale < 1.5f
    // Az ugrósor a teljes szélességet kapja (szinte margó nélkül), hogy minél több gomb elférjen;
    // a többi elem a szokásos oldalmargóval.
    val side = Modifier.padding(horizontal = if (roomy) 20.dp else 12.dp)
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ArtFitColumn(
            viewportHeight = viewportHeight,
            square = lyrics == null,
            artInset = if (roomy) 20.dp else 12.dp,
            top = { Box(side) { parts.topBar() } },
            art = { width, height ->
                if (lyrics != null) {
                    lyrics(
                        Modifier
                            .testTag(FullPlayerTags.LYRICS)
                            .size(width, height),
                    )
                } else {
                    PlayerArtwork(parts.state, width)
                }
            },
            bottom = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(side, horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayerDetails(parts.state, compact = !roomy)
                        Spacer(Modifier.height(4.dp))
                        PlayerActions(parts)
                        PlayerSeekBlock(parts.state, parts.callbacks)
                    }
                    SkipRow(
                        state = parts.state,
                        skip = parts.skip,
                        callbacks = parts.callbacks,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    TransportRow(state = parts.state, callbacks = parts.callbacks, modifier = side)
                    Spacer(Modifier.height(8.dp))
                }
            },
        )
    }
}

/**
 * Fekvő: bal oszlop a fejléccel, címmel és csúszkával; jobb oszlop az ugrógombokkal, a vezérlősorral
 * és a műveletsorral. A borító (vagy a dalszöveg) mellettük, ha elfér. Mindkét oszlop görgethető végső tartalékként.
 */
@Composable
private fun LandscapePlayer(
    parts: PlayerParts,
    maxWidth: Dp,
    maxHeight: Dp,
    lyrics: (@Composable (Modifier) -> Unit)?,
) {
    val fontScale = LocalDensity.current.fontScale
    val artSize = min(maxHeight - 32.dp, maxWidth * 0.32f)
    val columnWidthWithArt = (maxWidth - artSize - 64.dp) / 2
    val showArt = artSize >= 120.dp && fontScale < 1.5f && columnWidthWithArt >= 240.dp
    Row(Modifier.fillMaxSize()) {
        if (lyrics != null) {
            lyrics(
                Modifier
                    .testTag(FullPlayerTags.LYRICS)
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        } else if (showArt) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .padding(start = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                PlayerArtwork(parts.state, artSize)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            parts.topBar()
            PlayerDetails(parts.state, compact = true)
            Spacer(Modifier.height(8.dp))
            PlayerSeekBlock(parts.state, parts.callbacks)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.Center,
            ) {
                SkipRow(state = parts.state, skip = parts.skip, callbacks = parts.callbacks)
                Spacer(Modifier.height(8.dp))
                TransportRow(state = parts.state, callbacks = parts.callbacks, playSize = 64)
                PlayerActions(parts)
            }
        }
    }
}

@Composable
private fun PlayerActions(parts: PlayerParts) {
    ActionRow(
        state = parts.state,
        callbacks = parts.callbacks,
        isFavorite = parts.extras.isFavorite,
        speed = parts.extras.speed,
        sleepTimer = parts.extras.sleepTimer,
    )
}

/**
 * Egyedi elrendezés: a fejlécet és az alsó blokkot méri először, a középső rész a maradék helyet
 * kapja. Borítónál ([square]) négyzet, legfeljebb a teljes szélesség, és ha a maradék kevesebb
 * [MinArtwork]-nél, elmarad. Dalszövegnél a teljes szélességet és a maradék magasságot kapja
 * (legalább [MinLyricsHeight]-et). Az alsó blokk a képernyő aljához igazodik; ha a tartalom
 * így sem fér el, a szülő görget.
 */
@Composable
private fun ArtFitColumn(
    viewportHeight: Dp,
    square: Boolean,
    artInset: Dp,
    top: @Composable () -> Unit,
    art: @Composable (Dp, Dp) -> Unit,
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
        val gap = 8.dp.roundToPx()
        val available = viewport - topHeight - bottomHeight - 2 * gap
        val innerWidth = width - 2 * artInset.roundToPx()
        val (artWidth, artHeight) = if (square) {
            val side = minOf(innerWidth, available, 520.dp.roundToPx()).takeIf { it >= MinArtwork.roundToPx() } ?: 0
            side to side
        } else {
            innerWidth to maxOf(available, MinLyricsHeight.roundToPx())
        }
        val artPlaceables = if (artWidth > 0 && artHeight > 0) {
            subcompose("art") { art(artWidth.toDp(), artHeight.toDp()) }
                .map { it.measure(Constraints.fixed(artWidth, artHeight)) }
        } else {
            emptyList()
        }
        val contentHeight = topHeight + bottomHeight + if (artHeight > 0) artHeight + 2 * gap else 0
        val totalHeight = maxOf(viewport, contentHeight)
        layout(width, totalHeight) {
            var y = 0
            topPlaceables.forEach {
                it.place(0, y)
                y += it.height
            }
            val bottomTop = totalHeight - bottomHeight
            if (artHeight > 0) {
                val artTop = y + (bottomTop - y - artHeight) / 2
                artPlaceables.forEach { it.place((width - artWidth) / 2, artTop) }
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
private fun PlayerTopBar(
    state: PlaybackUiState,
    callbacks: PlayerCallbacks,
    onMinimize: () -> Unit,
    onMoreActions: (() -> Unit)?,
    hasLyrics: Boolean,
    showLyrics: Boolean,
    onShowLyrics: (Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showLabels = maxWidth >= 340.dp && LocalDensity.current.fontScale <= 1.3f
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
                    .size(48.dp),
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_minimize),
                    size = 32.dp,
                )
            }
            callbacks.onOpenQueue?.let { QueuePositionButton(state, it) }
            Spacer(Modifier.weight(1f))
            if (hasLyrics) {
                PageTab(
                    icon = Icons.Rounded.MusicNote,
                    label = stringResource(R.string.player_tab_song),
                    selected = !showLyrics,
                    showLabel = showLabels,
                    tag = FullPlayerTags.TAB_SONG,
                    onClick = { onShowLyrics(false) },
                )
                PageTab(
                    icon = Icons.Rounded.Lyrics,
                    label = stringResource(R.string.player_tab_lyrics),
                    selected = showLyrics,
                    showLabel = showLabels,
                    tag = FullPlayerTags.TAB_LYRICS,
                    onClick = { onShowLyrics(true) },
                )
            }
            Spacer(Modifier.weight(1f))
            if (onMoreActions != null) {
                IconButton(
                    onClick = onMoreActions,
                    modifier = Modifier
                        .testTag(FullPlayerTags.MORE)
                        .size(48.dp),
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
                }
            }
        }
    }
}

/** A „Dal” és a „Dalszöveg” nézet közötti váltó egy eleme. */
@Composable
private fun PageTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    showLabel: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.7f)
    Column(
        Modifier
            .testTag(tag)
            .heightIn(min = 48.dp)
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, size = 24.dp)
        if (showLabel) {
            Text(label, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
        state.nextTitle?.let { next ->
            Text(
                text = stringResource(R.string.next_up, next),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(PlayerTags.NEXT_UP),
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
