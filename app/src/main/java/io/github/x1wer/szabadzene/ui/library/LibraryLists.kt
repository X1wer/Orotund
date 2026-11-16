package io.github.x1wer.szabadzene.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.data.settings.PlayButtonMode
import io.github.x1wer.szabadzene.data.settings.PlaybackPrefs
import io.github.x1wer.szabadzene.ui.LocalSnackbarHost
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import io.github.x1wer.szabadzene.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.library.TrackGroup
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.LocalMenuState
import io.github.x1wer.szabadzene.ui.TrackMenuContext
import io.github.x1wer.szabadzene.ui.components.CollectionRow
import io.github.x1wer.szabadzene.ui.components.TrackRow

/** A kijelölő mód állapota egy számlistához. */
class TrackSelection(
    val active: Boolean,
    val selectedIds: Set<Long>,
    val onToggle: (Track) -> Unit,
    /** Hosszú nyomás a kijelölő módon kívül: belépés a kijelölő módba ezzel a számmal. */
    val onStart: (Track) -> Unit,
)

object TrackListTags {
    const val CHECKBOX = "track_select_checkbox"
}

/**
 * Számlista a közös számsorral. Egy sorra koppintva a lista ettől a számtól szól;
 * a menüterület a számmenüt nyitja meg. Kijelölő módban ([selection]) a koppintás ki-/bejelöl.
 */
@Composable
fun TrackList(
    tracks: List<Track>,
    currentMediaId: String?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: (LazyListScope.() -> Unit)? = null,
    /** Egyedi alcím (pl. lejátszásszám); null esetén az előadó. */
    subtitleFor: ((Track) -> String)? = null,
    selection: TrackSelection? = null,
) {
    val container = LocalAppContainer.current
    val menu = LocalMenuState.current
    val unknownArtist = stringResource(R.string.unknown_artist)
    LazyColumn(
        modifier = modifier
            .testTag("track_list")
            .fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        header?.invoke(this)
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            val selecting = selection?.active == true
            val isSelected = selection != null && track.id in selection.selectedIds
            TrackRow(
                title = track.title,
                subtitle = subtitleFor?.invoke(track) ?: track.artist ?: unknownArtist,
                trackId = track.id,
                isCurrent = track.id.toString() == currentMediaId,
                onPlay = {
                    if (selecting) selection?.onToggle(track) else container?.player?.playTracks(tracks, index)
                },
                onMenu = { menu.open(track, TrackMenuContext.Library) },
                onLongPress = selection?.let { s -> { if (s.active) s.onToggle(track) else s.onStart(track) } },
                selected = selecting && isSelected,
                leading = if (selecting) {
                    {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { selection?.onToggle(track) },
                            modifier = Modifier
                                .testTag(TrackListTags.CHECKBOX)
                                .padding(start = 4.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** Gyűjtemények (albumok, előadók, műfajok, mappák) listája. */
@Composable
fun GroupList(
    groups: List<TrackGroup>,
    unknownName: String,
    subtitle: @Composable (TrackGroup) -> String,
    onOpen: (TrackGroup) -> Unit,
    onMenu: (TrackGroup) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    LazyColumn(
        modifier = modifier
            .testTag("group_list")
            .fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(groups, key = { it.key }) { group ->
            CollectionRow(
                title = group.name ?: unknownName,
                subtitle = subtitle(group),
                artworkTrackId = if (icon == null) group.artworkTrackId else null,
                onOpen = { onOpen(group) },
                onMenu = { onMenu(group) },
                icon = icon,
            )
        }
    }
}

/** Kisebb belső margó, hogy nagy betűvel és ikonnal is egy sorban elférjen a felirat. */
private val PlayShufflePadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

/**
 * A Lejátszás/Keverés gombok közös művelete a beállított viselkedéssel: ha már szól zene, és a
 * beállítás „a mostani szám végigszól”, a szám nem szakad meg, és erről rövid üzenet szól.
 */
@Composable
fun rememberPlayAll(): (tracks: List<Track>, shuffle: Boolean) -> Unit {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val prefsFlow = remember(container) { container?.settings?.playback ?: flowOf(PlaybackPrefs()) }
    val prefs by prefsFlow.collectAsStateWithLifecycle(PlaybackPrefs())
    return { tracks, shuffle ->
        val keepCurrent = prefs.playButtons == PlayButtonMode.AFTER_CURRENT
        val kept = container?.player?.playAll(tracks, shuffle, keepCurrent) == true
        if (kept && snackbar != null) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(
                    resources.getString(if (shuffle) R.string.toast_shuffle_after_current else R.string.toast_order_after_current),
                )
            }
        }
    }
}

/** A zenetár keresőmezője melletti kis „Lejátszás” (sorrendben) és „Keverés” ikongomb. */
@Composable
fun PlayShuffleIconButtons(tracks: List<Track>) {
    val playAll = rememberPlayAll()
    FilledIconButton(
        onClick = { playAll(tracks, false) },
        enabled = tracks.isNotEmpty(),
        modifier = Modifier
            .testTag("play_all")
            .size(48.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_play))
    }
    FilledTonalIconButton(
        onClick = { playAll(tracks, true) },
        enabled = tracks.isNotEmpty(),
        modifier = Modifier
            .testTag("shuffle_all")
            .size(48.dp),
    ) {
        Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.action_shuffle))
    }
}

/** „Lejátszás” és „Keverés” gombok egy lista (album, lista) tetején. */
@Composable
fun PlayShuffleButtons(tracks: List<Track>, modifier: Modifier = Modifier) {
    val playAll = rememberPlayAll()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { playAll(tracks, false) },
            enabled = tracks.isNotEmpty(),
            contentPadding = PlayShufflePadding,
            modifier = Modifier
                .testTag("play_all")
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_play), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(
            onClick = { playAll(tracks, true) },
            enabled = tracks.isNotEmpty(),
            contentPadding = PlayShufflePadding,
            modifier = Modifier
                .testTag("shuffle_all")
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_shuffle), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Középre igazított üzenet (üres lista, betöltés, engedélykérés). */
@Composable
fun CenteredMessage(
    contentPadding: PaddingValues,
    icon: ImageVector? = null,
    title: String? = null,
    body: String? = null,
    content: @Composable () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, size = 56.dp)
            }
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            }
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            content()
        }
    }
}
