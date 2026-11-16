package io.github.x1wer.szabadzene.ui.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RemoveFromQueue
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.x1wer.szabadzene.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.playback.QueueEntry
import io.github.x1wer.szabadzene.ui.CollectionMenuRequest
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.LocalMenuState
import io.github.x1wer.szabadzene.ui.TrackMenuContext
import io.github.x1wer.szabadzene.ui.components.DragHandle
import io.github.x1wer.szabadzene.ui.components.ReorderableColumn
import io.github.x1wer.szabadzene.ui.components.TrackAction
import io.github.x1wer.szabadzene.ui.components.TrackRow
import io.github.x1wer.szabadzene.ui.library.CenteredMessage
import io.github.x1wer.szabadzene.ui.trackCountText

/**
 * A várólista lejátszási sorrendben. Koppintásra az adott szám szól; a fogantyúval átrendezhető
 * (keverés közben nem, mert ott a kevert sorrend számít); a menüből eltávolítható.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(allTracks: List<Track>, onBack: () -> Unit) {
    val container = LocalAppContainer.current ?: return
    val menu = LocalMenuState.current
    val queue by container.player.queue.collectAsStateWithLifecycle()
    val playback by container.player.state.collectAsStateWithLifecycle()
    val byId = remember(allTracks) { allTracks.associateBy { it.id.toString() } }
    val unknownArtist = stringResource(R.string.unknown_artist)
    val canReorder = !playback.shuffle
    val removeLabel = stringResource(R.string.action_remove_from_queue)

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.queue_title))
                        Text(
                            trackCountText(queue.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
            if (!canReorder && queue.isNotEmpty()) {
                Text(
                    stringResource(R.string.queue_shuffle_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val bottomInset = WindowInsets.navigationBars.asPaddingValues()
            if (queue.isEmpty()) {
                CenteredMessage(
                    contentPadding = bottomInset,
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    body = stringResource(R.string.queue_empty),
                )
            } else {
                ReorderableColumn(
                    items = queue,
                    key = { entry -> "${entry.index}_${entry.mediaId}" },
                    enabled = canReorder,
                    onMove = { from, to ->
                        val fromIndex = queue.getOrNull(from)?.index
                        val toIndex = queue.getOrNull(to)?.index
                        if (fromIndex != null && toIndex != null) container.player.moveQueueItem(fromIndex, toIndex)
                    },
                    contentPadding = PaddingValues(bottom = bottomInset.calculateBottomPadding() + 8.dp),
                    modifier = Modifier
                        .testTag("queue_list")
                        .fillMaxSize(),
                ) { entry: QueueEntry, _, _, handle ->
                    val track = byId[entry.mediaId]
                    TrackRow(
                        title = entry.title.ifBlank { track?.title.orEmpty() },
                        subtitle = entry.artist ?: track?.artist ?: unknownArtist,
                        trackId = entry.mediaId.toLongOrNull(),
                        isCurrent = entry.isCurrent,
                        onPlay = { container.player.playQueueItem(entry.index) },
                        onMenu = {
                            if (track != null) {
                                menu.open(track, TrackMenuContext.Queue(entry.index))
                            } else {
                                // A szám már nincs a zenetárban: csak az eltávolítás értelmes.
                                menu.openCollection(
                                    CollectionMenuRequest(
                                        title = entry.title,
                                        subtitle = entry.artist ?: unknownArtist,
                                        artworkTrackId = null,
                                        tracks = emptyList(),
                                        extraActions = listOf(
                                            TrackAction(removeLabel, Icons.Rounded.RemoveFromQueue, "action_remove_from_queue") {
                                                container.player.removeQueueItem(entry.index)
                                            },
                                        ),
                                    ),
                                )
                            }
                        },
                        leading = { DragHandle(handle, enabled = canReorder) },
                    )
                }
            }
        }
    }
}
