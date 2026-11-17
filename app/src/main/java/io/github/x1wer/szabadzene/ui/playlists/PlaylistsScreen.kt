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
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.ui.platform.LocalResources
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.x1wer.szabadzene.ui.components.Icon
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
import io.github.x1wer.szabadzene.ui.PlaybackOrigin
import io.github.x1wer.szabadzene.ui.recordPlaybackOrigin
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import io.github.x1wer.szabadzene.data.playlists.PlaylistListEntry
import io.github.x1wer.szabadzene.data.playlists.PlaylistListSettings
import io.github.x1wer.szabadzene.data.playlists.PlaylistOrdering
import io.github.x1wer.szabadzene.data.playlists.PlaylistSortField
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Az automatikus listák azonosítója a navigációban (a saját listák azonosítója pozitív). */
const val FAVORITES_ID = -1L
const val MOST_PLAYED_ID = -2L
const val RECENTLY_PLAYED_ID = -3L

private sealed interface PlaylistDialog {
    data object Create : PlaylistDialog
    data class Rename(val id: Long, val name: String) : PlaylistDialog
    data class Delete(val id: Long, val name: String) : PlaylistDialog
    data object ClearHistory : PlaylistDialog
    data object ResetCounts : PlaylistDialog
}

/** Az automatikus listák saját menüpontjai: előzmények törlése, illetve számlálók nullázása. */
@Composable
private fun smartListActions(playlistId: Long, onDialog: (PlaylistDialog) -> Unit): List<TrackAction> = when (playlistId) {
    RECENTLY_PLAYED_ID -> listOf(
        TrackAction(stringResource(R.string.recent_clear), Icons.Rounded.DeleteSweep, "action_clear_history") {
            onDialog(PlaylistDialog.ClearHistory)
        },
    )
    MOST_PLAYED_ID -> listOf(
        TrackAction(stringResource(R.string.most_played_reset), Icons.Rounded.RestartAlt, "action_reset_counts") {
            onDialog(PlaylistDialog.ResetCounts)
        },
    )
    else -> emptyList()
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
        PlaylistDialog.ClearHistory -> ConfirmDialog(
            title = stringResource(R.string.recent_clear),
            message = stringResource(R.string.recent_clear_message),
            confirmLabel = stringResource(R.string.dialog_delete),
            onConfirm = {
                onDone()
                scope.launch { container.playStats.clearHistory() }
            },
            onDismiss = onDone,
        )
        PlaylistDialog.ResetCounts -> ConfirmDialog(
            title = stringResource(R.string.most_played_reset),
            message = stringResource(R.string.most_played_reset_message),
            confirmLabel = stringResource(R.string.most_played_reset_confirm),
            onConfirm = {
                onDone()
                scope.launch { container.playStats.resetCounts() }
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
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }
    val favoritesTitle = stringResource(R.string.favorites_title)
    val mostPlayedTitle = stringResource(R.string.most_played_title)
    val recentTitle = stringResource(R.string.recently_played_title)
    val mostPlayedActions = smartListActions(MOST_PLAYED_ID) { dialog = it }
    val recentActions = smartListActions(RECENTLY_PLAYED_ID) { dialog = it }
    val renameLabel = stringResource(R.string.playlist_rename)
    val deleteLabel = stringResource(R.string.dialog_delete)
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    val listSettingsFlow = remember(container) { container?.settings?.playlistList ?: flowOf(PlaylistListSettings()) }
    val listSettings by listSettingsFlow.collectAsStateWithLifecycle(PlaylistListSettings())
    val byId = remember(playlists) { playlists.associateBy { it.id } }
    val ordered = remember(playlists, favorites.size, mostPlayed.size, recentlyPlayed.size, listSettings, favoritesTitle) {
        PlaylistOrdering.order(
            listOf(
                PlaylistListEntry(FAVORITES_ID, favoritesTitle, favorites.size, createdAt = 0, specialRank = 0),
                PlaylistListEntry(MOST_PLAYED_ID, mostPlayedTitle, mostPlayed.size, createdAt = 0, specialRank = 1),
                PlaylistListEntry(RECENTLY_PLAYED_ID, recentTitle, recentlyPlayed.size, createdAt = 0, specialRank = 2),
            ) + playlists.map { PlaylistListEntry(it.id, it.name, it.entryCount, it.createdAt) },
            listSettings,
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.playlists_title)) },
            actions = {
                PlaylistSortMenu(listSettings) { changed -> scope.launch { container?.settings?.setPlaylistList(changed) } }
                PlaylistImportButton()
                PlaylistExportButton(viewModel)
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
            items(ordered, key = { it.key }) { entry ->
                when (entry.key) {
                FAVORITES_ID -> CollectionRow(
                    title = favoritesTitle,
                    subtitle = trackCountText(favorites.size),
                    artworkTrackId = null,
                    icon = Icons.Rounded.Favorite,
                    onOpen = { onOpenPlaylist(FAVORITES_ID) },
                    onMenu = {
                        menu.openCollection(
                            CollectionMenuRequest(favoritesTitle, "", favorites.firstOrNull()?.id, favorites, origin = PlaybackOrigin.playlist(FAVORITES_ID)),
                        )
                    },
                    modifier = Modifier.testTag("favorites_row"),
                )
                MOST_PLAYED_ID -> {
                val list = mostPlayed.map { it.track }
                CollectionRow(
                    title = mostPlayedTitle,
                    subtitle = trackCountText(list.size),
                    artworkTrackId = null,
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    onOpen = { onOpenPlaylist(MOST_PLAYED_ID) },
                    onMenu = {
                        menu.openCollection(CollectionMenuRequest(mostPlayedTitle, "", list.firstOrNull()?.id, list, mostPlayedActions, origin = PlaybackOrigin.playlist(MOST_PLAYED_ID)))
                    },
                    modifier = Modifier.testTag("most_played_row"),
                )
                }
                RECENTLY_PLAYED_ID -> {
                val list = recentlyPlayed.map { it.track }
                CollectionRow(
                    title = recentTitle,
                    subtitle = trackCountText(list.size),
                    artworkTrackId = null,
                    icon = Icons.Rounded.History,
                    onOpen = { onOpenPlaylist(RECENTLY_PLAYED_ID) },
                    onMenu = {
                        menu.openCollection(CollectionMenuRequest(recentTitle, "", list.firstOrNull()?.id, list, recentActions, origin = PlaybackOrigin.playlist(RECENTLY_PLAYED_ID)))
                    },
                    modifier = Modifier.testTag("recently_played_row"),
                )
                }
                else -> byId[entry.key]?.let { playlist ->
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
                                    origin = PlaybackOrigin.playlist(playlist.id),
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
                }
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

/** „12 lejátszás · Előadó” formázó (a Legtöbbet és a Nemrég játszott listához). */
@Composable
private fun playCountSubtitleFormatter(): (Int, String) -> String {
    val resources = LocalResources.current
    return { count, artist -> resources.getQuantityString(R.plurals.play_count_subtitle, count, count, artist) }
}

/** Lejátszási lista, a Kedvencek vagy egy automatikus lista tartalma. A saját listák áthúzással átrendezhetők. */
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
    val isSmart = playlistId == MOST_PLAYED_ID || playlistId == RECENTLY_PLAYED_ID
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val ranked = when (playlistId) {
        MOST_PLAYED_ID -> mostPlayed
        RECENTLY_PLAYED_ID -> recentlyPlayed
        else -> emptyList()
    }
    val countsById = ranked.associate { it.track.id to it.playCount }
    val smartActions = smartListActions(playlistId) { dialog = it }
    val playlistFlow = remember(playlistId) { container.playlists.playlist(playlistId) }
    val itemsFlow = remember(playlistId) { container.playlists.playlistItems(playlistId) }
    val playlist by playlistFlow.collectAsStateWithLifecycle(null)
    val items by itemsFlow.collectAsStateWithLifecycle(emptyList<PlaylistItem>())
    val title = when (playlistId) {
        FAVORITES_ID -> stringResource(R.string.favorites_title)
        MOST_PLAYED_ID -> stringResource(R.string.most_played_title)
        RECENTLY_PLAYED_ID -> stringResource(R.string.recently_played_title)
        else -> playlist?.name.orEmpty()
    }
    val tracks = when {
        isFavorites -> favorites
        isSmart -> ranked.map { it.track }
        else -> items.map { it.track }
    }
    val playCountFormat = playCountSubtitleFormatter()
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
                        val extra = if (isFavorites || isSmart) {
                            smartActions
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
                        menu.openCollection(CollectionMenuRequest(title, "", tracks.firstOrNull()?.id, tracks, extra, origin = PlaybackOrigin.playlist(playlistId)))
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
                body = stringResource(
                    when (playlistId) {
                        FAVORITES_ID -> R.string.favorites_empty
                        MOST_PLAYED_ID -> R.string.most_played_empty
                        RECENTLY_PLAYED_ID -> R.string.recently_played_empty
                        else -> R.string.playlist_empty
                    },
                ),
            )
        } else if (isSmart) {
            TrackList(
                tracks = tracks,
                currentMediaId = currentMediaId,
                contentPadding = contentPadding,
                header = { item(key = "header") { PlayShuffleButtons(tracks) } },
                subtitleFor = { track -> playCountFormat(countsById[track.id] ?: 0, track.artist ?: unknownArtist) },
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
                    onPlay = {
                        container.recordPlaybackOrigin(PlaybackOrigin.playlist(playlistId))
                        container.player.playTracks(tracks, index)
                    },
                    onMenu = { menu.open(item.track, TrackMenuContext.Playlist(playlistId, item.entryId)) },
                    leading = { DragHandle(handle, enabled = true) },
                )
            }
        }
    }
    PlaylistDialogs(dialog, onDone = { dialog = null }, onDeleted = { onBack() })
}

/** A Listák fül rendezése: név, létrehozás vagy számok száma; csökkenő; a különleges listák mindig elöl. */
@Composable
private fun PlaylistSortMenu(settings: PlaylistListSettings, onChange: (PlaylistListSettings) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("playlist_sort")) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.cd_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                PlaylistSortField.NAME to R.string.playlist_sort_name,
                PlaylistSortField.CREATED to R.string.playlist_sort_created,
                PlaylistSortField.SIZE to R.string.playlist_sort_size,
            ).forEach { (field, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = { onChange(settings.copy(field = field)) },
                    trailingIcon = { if (settings.field == field) Icon(Icons.Rounded.Check, contentDescription = null) },
                    modifier = Modifier.testTag("playlist_sort_${field.name.lowercase()}").heightIn(min = 56.dp),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_descending)) },
                onClick = { onChange(settings.copy(descending = !settings.descending)) },
                trailingIcon = { if (settings.descending) Icon(Icons.Rounded.Check, contentDescription = null) },
                modifier = Modifier.testTag("playlist_sort_descending").heightIn(min = 56.dp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_special_first)) },
                onClick = { onChange(settings.copy(specialFirst = !settings.specialFirst)) },
                trailingIcon = { if (settings.specialFirst) Icon(Icons.Rounded.Check, contentDescription = null) },
                modifier = Modifier.testTag("playlist_special_first").heightIn(min = 56.dp),
            )
        }
    }
}
