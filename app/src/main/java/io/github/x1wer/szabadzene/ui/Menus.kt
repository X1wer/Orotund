package io.github.x1wer.szabadzene.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.RemoveFromQueue
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.ui.components.AddToPlaylistDialog
import io.github.x1wer.szabadzene.ui.components.FileInfoDialog
import io.github.x1wer.szabadzene.ui.components.NameDialog
import io.github.x1wer.szabadzene.ui.components.TrackAction
import io.github.x1wer.szabadzene.ui.components.TrackActionsSheet
import io.github.x1wer.szabadzene.ui.edit.FileActionsHost
import io.github.x1wer.szabadzene.ui.sound.SleepTimerDialog
import kotlinx.coroutines.launch

/** Honnan nyílt a számmenü: ettől függ, mely menüpontok jelennek meg. */
sealed interface TrackMenuContext {
    data object Library : TrackMenuContext
    data object NowPlaying : TrackMenuContext
    data class Queue(val index: Int) : TrackMenuContext
    data class Playlist(val playlistId: Long, val entryId: Long) : TrackMenuContext
}

private data class TrackMenuRequest(val track: Track, val context: TrackMenuContext)

/** Gyűjtemény (album, előadó, mappa, lista) menüje: közös műveletek + a hívó saját pontjai. */
data class CollectionMenuRequest(
    val title: String,
    val subtitle: String,
    val artworkTrackId: Long?,
    val tracks: List<Track>,
    val extraActions: List<TrackAction> = emptyList(),
)

/** A menük közös állapota: bármelyik képernyő megnyithatja, a megjelenítés egy helyen történik. */
@Stable
class MenuState {
    private var trackRequest by mutableStateOf<TrackMenuRequest?>(null)
    var collectionRequest by mutableStateOf<CollectionMenuRequest?>(null)
        private set
    var infoTrack by mutableStateOf<Track?>(null)
    var addToPlaylist by mutableStateOf<List<Track>?>(null)
    var newPlaylistFor by mutableStateOf<List<Track>?>(null)

    // Fájlműveletek (ui/edit/FileActionsHost) és az időzítő ablaka.
    var editTags by mutableStateOf<Track?>(null)
    var trim by mutableStateOf<Track?>(null)
    var ringtone by mutableStateOf<Track?>(null)
    var deleteFile by mutableStateOf<Track?>(null)
    var sleepTimerOpen by mutableStateOf(false)

    val openTrack: Track? get() = trackRequest?.track
    val openContext: TrackMenuContext? get() = trackRequest?.context

    fun open(track: Track, context: TrackMenuContext = TrackMenuContext.Library) {
        collectionRequest = null
        trackRequest = TrackMenuRequest(track, context)
    }

    fun openCollection(request: CollectionMenuRequest) {
        trackRequest = null
        collectionRequest = request
    }

    fun close() {
        trackRequest = null
        collectionRequest = null
    }
}

val LocalMenuState = staticCompositionLocalOf { MenuState() }

/** Megosztás az Android megosztási felületével (a fájl tartalmát osztja meg). */
fun shareTrack(context: Context, track: Track) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(track.mimeType ?: "audio/*")
        .putExtra(Intent.EXTRA_STREAM, track.contentUri)
        .putExtra(Intent.EXTRA_TITLE, track.title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // Nincs megosztásra alkalmas alkalmazás: csendben nem történik semmi.
    }
}

/** A számmenük, a gyűjteménymenük és a hozzájuk tartozó ablakok egyetlen gazdája. */
@Composable
fun MenuHost(menu: MenuState, snackbar: SnackbarHostState) {
    val container = LocalAppContainer.current ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val unknownArtist = stringResource(R.string.unknown_artist)
    val favorites by container.playlists.favoriteIds.collectAsStateWithLifecycle(emptySet())
    val playlists by container.playlists.playlists.collectAsStateWithLifecycle(emptyList())

    fun notify(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    val track = menu.openTrack
    val trackContext = menu.openContext
    if (track != null && trackContext != null) {
        val isFavorite = track.id in favorites
        val actions = buildList {
            if (trackContext == TrackMenuContext.Library || trackContext is TrackMenuContext.Playlist) {
                add(TrackAction(stringResource(R.string.action_play_next), Icons.Rounded.QueuePlayNext, "action_play_next") {
                    container.player.playNext(track)
                    notify(resources.getString(R.string.toast_play_next, track.title))
                })
                add(TrackAction(stringResource(R.string.action_add_to_queue), Icons.Rounded.AddToQueue, "action_add_to_queue") {
                    container.player.addToQueue(track)
                    notify(resources.getString(R.string.toast_added_to_queue, track.title))
                })
            }
            if (trackContext is TrackMenuContext.Queue) {
                add(TrackAction(stringResource(R.string.action_remove_from_queue), Icons.Rounded.RemoveFromQueue, "action_remove_from_queue") {
                    container.player.removeQueueItem(trackContext.index)
                })
            }
            if (trackContext is TrackMenuContext.Playlist) {
                add(TrackAction(stringResource(R.string.action_remove_from_playlist), Icons.Rounded.PlaylistRemove, "action_remove_from_playlist") {
                    scope.launch { container.playlists.removeEntry(trackContext.playlistId, trackContext.entryId) }
                })
            }
            add(TrackAction(stringResource(R.string.action_add_to_playlist), Icons.AutoMirrored.Rounded.PlaylistAdd, "action_add_to_playlist") {
                menu.addToPlaylist = listOf(track)
            })
            add(
                TrackAction(
                    stringResource(if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "action_favorite",
                ) {
                    scope.launch {
                        if (isFavorite) container.playlists.removeFavorite(track.id) else container.playlists.addFavorites(listOf(track.id))
                    }
                },
            )
            add(TrackAction(stringResource(R.string.action_share), Icons.Rounded.Share, "action_share") {
                shareTrack(context, track)
            })
            add(TrackAction(stringResource(R.string.action_file_info), Icons.Rounded.Info, "action_file_info") {
                menu.infoTrack = track
            })
            // Fájlműveletek: csak ténylegesen működő pontok (a címkeszerkesztés csak írható formátumnál).
            if (container.fileEditor.isTagWritable(track)) {
                add(TrackAction(stringResource(R.string.action_edit_tags), Icons.Rounded.Edit, "action_edit_tags") {
                    menu.editTags = track
                })
            }
            add(TrackAction(stringResource(R.string.action_trim), Icons.Rounded.ContentCut, "action_trim") {
                menu.trim = track
            })
            add(TrackAction(stringResource(R.string.action_set_ringtone), Icons.Rounded.NotificationsActive, "action_set_ringtone") {
                menu.ringtone = track
            })
            add(TrackAction(stringResource(R.string.action_delete_file), Icons.Rounded.DeleteForever, "action_delete_file") {
                menu.deleteFile = track
            })
        }
        TrackActionsSheet(
            title = track.title,
            subtitle = track.artist ?: unknownArtist,
            trackId = track.id,
            actions = actions,
            onDismiss = { menu.close() },
        )
    }

    menu.collectionRequest?.let { request ->
        val tracks = request.tracks
        val actions = buildList {
            add(TrackAction(stringResource(R.string.action_play), Icons.Rounded.PlayArrow, "action_play_all") {
                container.player.playTracks(tracks, 0)
            })
            add(TrackAction(stringResource(R.string.action_shuffle), Icons.Rounded.Shuffle, "action_shuffle_all") {
                container.player.playShuffled(tracks)
            })
            add(TrackAction(stringResource(R.string.action_play_next), Icons.Rounded.QueuePlayNext, "action_play_next") {
                container.player.playNext(tracks)
                notify(resources.getQuantityString(R.plurals.toast_tracks_play_next, tracks.size, tracks.size))
            })
            add(TrackAction(stringResource(R.string.action_add_to_queue), Icons.Rounded.AddToQueue, "action_add_to_queue") {
                container.player.addToQueue(tracks)
                notify(resources.getQuantityString(R.plurals.toast_tracks_added_to_queue, tracks.size, tracks.size))
            })
            add(TrackAction(stringResource(R.string.action_add_to_playlist), Icons.AutoMirrored.Rounded.PlaylistAdd, "action_add_to_playlist") {
                menu.addToPlaylist = tracks
            })
            addAll(request.extraActions)
        }
        TrackActionsSheet(
            title = request.title,
            subtitle = request.subtitle,
            trackId = request.artworkTrackId,
            actions = if (tracks.isEmpty()) request.extraActions else actions,
            onDismiss = { menu.close() },
        )
    }

    menu.addToPlaylist?.let { tracks ->
        AddToPlaylistDialog(
            playlists = playlists,
            trackCount = tracks.size,
            onPick = { playlist ->
                menu.addToPlaylist = null
                scope.launch {
                    container.playlists.addTracks(playlist.id, tracks.map { it.id })
                    snackbar.showSnackbar(resources.getString(R.string.toast_added_to_playlist, playlist.name))
                }
            },
            onCreateNew = {
                menu.addToPlaylist = null
                menu.newPlaylistFor = tracks
            },
            onDismiss = { menu.addToPlaylist = null },
        )
    }

    menu.newPlaylistFor?.let { tracks ->
        NameDialog(
            title = stringResource(R.string.playlist_new),
            initial = "",
            confirmLabel = stringResource(R.string.dialog_create),
            onConfirm = { name ->
                menu.newPlaylistFor = null
                scope.launch {
                    container.playlists.create(name, tracks.map { it.id })
                    snackbar.showSnackbar(resources.getString(R.string.toast_added_to_playlist, name))
                }
            },
            onDismiss = { menu.newPlaylistFor = null },
        )
    }

    menu.infoTrack?.let { info ->
        FileInfoDialog(track = info, onDismiss = { menu.infoTrack = null })
    }

    FileActionsHost(menu, snackbar)

    if (menu.sleepTimerOpen) {
        val timer by container.player.sleepTimer.collectAsStateWithLifecycle()
        SleepTimerDialog(
            state = timer,
            onPickMinutes = { minutes ->
                menu.sleepTimerOpen = false
                container.player.startSleepTimer(minutes)
                notify(resources.getString(R.string.toast_sleep_set, resources.getQuantityString(R.plurals.sleep_minutes, minutes, minutes)))
            },
            onPickEndOfTrack = {
                menu.sleepTimerOpen = false
                container.player.startSleepTimerEndOfTrack()
                notify(resources.getString(R.string.toast_sleep_set, resources.getString(R.string.sleep_end_of_track)))
            },
            onCancelTimer = {
                menu.sleepTimerOpen = false
                container.player.cancelSleepTimer()
                notify(resources.getString(R.string.toast_sleep_cancelled))
            },
            onDismiss = { menu.sleepTimerOpen = false },
        )
    }
}

/** „N szám” felirat. */
@Composable
fun trackCountText(count: Int): String = pluralStringResource(R.plurals.track_count, count, count)
