package io.github.x1wer.szabadzene.ui.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.db.PlaylistSummary
import io.github.x1wer.szabadzene.data.playlists.PlaylistItem
import io.github.x1wer.szabadzene.ui.CollectionMenuRequest
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.LocalMenuState
import io.github.x1wer.szabadzene.ui.TrackMenuContext
import io.github.x1wer.szabadzene.ui.components.CollectionRow
import io.github.x1wer.szabadzene.ui.components.ConfirmDialog
import io.github.x1wer.szabadzene.ui.components.DragHandle
import io.github.x1wer.szabadzene.ui.components.NameDialog
import io.github.x1wer.szabadzene.ui.components.ReorderableColumn
import io.github.x1wer.szabadzene.ui.components.TrackAction
import io.github.x1wer.szabadzene.ui.components.TrackRow
import io.github.x1wer.szabadzene.ui.library.CenteredMessage
import io.github.x1wer.szabadzene.ui.library.LibraryViewModel
import io.github.x1wer.szabadzene.ui.library.PlayShuffleButtons
import io.github.x1wer.szabadzene.ui.library.TrackList
import io.github.x1wer.szabadzene.ui.trackCountText
import kotlinx.coroutines.launch

/** A kedvencek „listájának” azonosítója a navigációban. */
const val FAVORITES_ID = -1L

private sealed interface PlaylistDialog {
    data object Create : PlaylistDialog
    data class Rename(val id: Long, val name: String) : PlaylistDialog
    data class Delete(val id: Long, val name: String) : PlaylistDialog
}

/** Rename/Delete ablakok állapota, képernyők között újrahasznosítva. */
@Composable
private fun PlaylistDialogs(dialog: PlaylistDialog?, onDone: () -> Unit, onDeleted: (Long) -> Unit = {}) {
    val container = LocalAppContainer.current ?: return
    val scope = rememberCoroutineScope()
    when (dialog) {
        PlaylistDialog.Create -> NameDialog(
            title = stringResource(R.string.playlist_new),
            initial = "",
            confirmLabel = stringResource(R.string.dialog_create),
            onConfirm = { name ->
                onDone()
                scope.launch { container.playlists.create(name) }
            },
            onDismiss = onDone,
        )
        is PlaylistDialog.Rename -> NameDialog(
            title = stringResource(R.string.playlist_rename),
            initial = dialog.name,
            confirmLabel = stringResource(R.string.dialog_save),
            onConfirm = { name ->
                onDone()
                scope.launch { container.playlists.rename(dialog.id, name) }
            },
            onDismiss = onDone,
        )
        is PlaylistDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.playlist_delete_title),
            message = stringResource(R.string.playlist_delete_message, dialog.name),
            confirmLabel = stringResource(R.string.dialog_delete),
            onConfirm = {
                onDone()
                scope.launch { container.playlists.delete(dialog.id) }
                onDeleted(dialog.id)
            },
            onDismiss = onDone,
        )
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onOpenPlaylist: (Long) -> Unit,
) {
    val menu = LocalMenuState.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }
    val favoritesTitle = stringResource(R.string.favorites_title)
    val renameLabel = stringResource(R.string.playlist_rename)
    val deleteLabel = stringResource(R.string.dialog_delete)
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.playlists_title)) },
            actions = {
                IconButton(onClick = { dialog = PlaylistDialog.Create }, modifier = Modifier.testTag("playlist_create")) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.playlist_new))
                }
            },
        )
        LazyColumn(
            modifier = Modifier
                .testTag("playlists_list")
                .fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "favorites") {
                CollectionRow(
                    title = favoritesTitle,
                    subtitle = trackCountText(favorites.size),
                    artworkTrackId = null,
                    icon = Icons.Rounded.Favorite,
                    onOpen = { onOpenPlaylist(FAVORITES_ID) },
                    onMenu = {
                        menu.openCollection(
                            CollectionMenuRequest(favoritesTitle, "", favorites.firstOrNull()?.id, favorites),
                        )
                    },
                    modifier = Modifier.testTag("favorites_row"),
                )
            }
            items(playlists, key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onOpen = { onOpenPlaylist(playlist.id) },
                    onMenu = {
                        scope.launch {
                            val items = container?.playlists?.playlistItemsOnce(playlist.id).orEmpty()
                            menu.openCollection(
                                CollectionMenuRequest(
                                    title = playlist.name,
                                    subtitle = "",
                                    artworkTrackId = items.firstOrNull()?.track?.id,
                                    tracks = items.map { it.track },
                                    extraActions = listOf(
                                        TrackAction(renameLabel, Icons.Rounded.DriveFileRenameOutline, "action_rename") {
                                            dialog = PlaylistDialog.Rename(playlist.id, playlist.name)
                                        },
                                        TrackAction(deleteLabel, Icons.Rounded.Delete, "action_delete") {
                                            dialog = PlaylistDialog.Delete(playlist.id, playlist.name)
                                        },
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
            if (playlists.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.playlists_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
    PlaylistDialogs(dialog, onDone = { dialog = null })
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onOpen: () -> Unit, onMenu: () -> Unit) {
    CollectionRow(
        title = playlist.name,
        subtitle = trackCountText(playlist.entryCount),
        artworkTrackId = null,
        icon = Icons.AutoMirrored.Rounded.QueueMusic,
        onOpen = onOpen,
        onMenu = onMenu,
        modifier = Modifier.testTag("playlist_row_${playlist.id}"),
    )
}

/** Lejátszási lista vagy a Kedvencek tartalma. A saját listák áthúzással átrendezhetők. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: LibraryViewModel,
    playlistId: Long,
    currentMediaId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current ?: return
    val menu = LocalMenuState.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }
    val isFavorites = playlistId == FAVORITES_ID
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val playlistFlow = remember(playlistId) { container.playlists.playlist(playlistId) }
    val itemsFlow = remember(playlistId) { container.playlists.playlistItems(playlistId) }
    val playlist by playlistFlow.collectAsStateWithLifecycle(null)
    val items by itemsFlow.collectAsStateWithLifecycle(emptyList<PlaylistItem>())
    val title = if (isFavorites) stringResource(R.string.favorites_title) else playlist?.name.orEmpty()
    val tracks = if (isFavorites) favorites else items.map { it.track }
    val unknownArtist = stringResource(R.string.unknown_artist)
    val renameLabel = stringResource(R.string.playlist_rename)
    val deleteLabel = stringResource(R.string.dialog_delete)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        trackCountText(tracks.size),
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
            actions = {
                IconButton(
                    onClick = {
                        val extra = if (isFavorites) {
                            emptyList()
                        } else {
                            listOf(
                                TrackAction(renameLabel, Icons.Rounded.DriveFileRenameOutline, "action_rename") {
                                    dialog = PlaylistDialog.Rename(playlistId, title)
                                },
                                TrackAction(deleteLabel, Icons.Rounded.Delete, "action_delete") {
                                    dialog = PlaylistDialog.Delete(playlistId, title)
                                },
                            )
                        }
                        menu.openCollection(CollectionMenuRequest(title, "", tracks.firstOrNull()?.id, tracks, extra))
                    },
                    modifier = Modifier.testTag("collection_menu"),
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
                }
            },
        )
        if (tracks.isEmpty()) {
            CenteredMessage(
                contentPadding = contentPadding,
                body = stringResource(if (isFavorites) R.string.favorites_empty else R.string.playlist_empty),
            )
        } else if (isFavorites) {
            TrackList(
                tracks = tracks,
                currentMediaId = currentMediaId,
                contentPadding = contentPadding,
                header = { item(key = "header") { PlayShuffleButtons(tracks) } },
            )
        } else {
            ReorderableColumn(
                items = items,
                key = { it.entryId },
                enabled = true,
                onMove = { from, to ->
                    val fromEntry = items.getOrNull(from)?.entryId
                    val toEntry = items.getOrNull(to)?.entryId
                    if (fromEntry != null && toEntry != null) {
                        scope.launch { container.playlists.moveEntry(playlistId, fromEntry, toEntry) }
                    }
                },
                contentPadding = contentPadding,
                modifier = Modifier
                    .testTag("playlist_items")
                    .fillMaxSize(),
                header = { item(key = "header") { PlayShuffleButtons(tracks) } },
            ) { item, index, _, handle ->
                TrackRow(
                    title = item.track.title,
                    subtitle = item.track.artist ?: unknownArtist,
                    trackId = item.track.id,
                    isCurrent = item.track.id.toString() == currentMediaId,
                    onPlay = { container.player.playTracks(tracks, index) },
                    onMenu = { menu.open(item.track, TrackMenuContext.Playlist(playlistId, item.entryId)) },
                    leading = { DragHandle(handle, enabled = true) },
                )
            }
        }
    }
    PlaylistDialogs(dialog, onDone = { dialog = null }, onDeleted = { onBack() })
}
