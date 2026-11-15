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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.library.TrackGroup
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.LocalMenuState
import io.github.x1wer.szabadzene.ui.TrackMenuContext
import io.github.x1wer.szabadzene.ui.components.CollectionRow
import io.github.x1wer.szabadzene.ui.components.TrackRow

/**
 * Számlista a közös számsorral. Egy sorra koppintva a lista ettől a számtól szól;
 * a menüterület a számmenüt nyitja meg.
 */
@Composable
fun TrackList(
    tracks: List<Track>,
    currentMediaId: String?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: (LazyListScope.() -> Unit)? = null,
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
            TrackRow(
                title = track.title,
                subtitle = track.artist ?: unknownArtist,
                trackId = track.id,
                isCurrent = track.id.toString() == currentMediaId,
                onPlay = { container?.player?.playTracks(tracks, index) },
                onMenu = { menu.open(track, TrackMenuContext.Library) },
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

/** „Lejátszás” és „Keverés” gombok egy lista tetején. */
@Composable
fun PlayShuffleButtons(tracks: List<Track>, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { container?.player?.playTracks(tracks, 0) },
            enabled = tracks.isNotEmpty(),
            modifier = Modifier
                .testTag("play_all")
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_play))
        }
        FilledTonalButton(
            onClick = { container?.player?.playShuffled(tracks) },
            enabled = tracks.isNotEmpty(),
            modifier = Modifier
                .testTag("shuffle_all")
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_shuffle))
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
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
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
