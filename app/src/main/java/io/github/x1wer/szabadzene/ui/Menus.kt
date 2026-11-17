package io.github.x1wer.szabadzene.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryAdd
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
import io.github.x1wer.szabadzene.data.playlists.uniquePlaylistName
import io.github.x1wer.szabadzene.ui.components.AddToPlaylistDialog
import io.github.x1wer.szabadzene.ui.components.FileInfoDialog
import io.github.x1wer.szabadzene.ui.components.NameDialog
import io.github.x1wer.szabadzene.ui.components.TrackAction
import io.github.x1wer.szabadzene.ui.components.TrackActionsSheet
import io.github.x1wer.szabadzene.ui.edit.FileActionsHost
import io.github.x1wer.szabadzene.ui.sound.SleepTimerDialog
import io.github.x1wer.szabadzene.data.settings.PlaybackPrefs
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.RepeatSetting
import io.github.x1wer.szabadzene.ui.player.PlayCountDialog
import io.github.x1wer.szabadzene.ui.player.RepeatModeDialog
import io.github.x1wer.szabadzene.ui.player.SkipSettingsDialog
import io.github.x1wer.szabadzene.ui.player.SpeedDialog
import io.github.x1wer.szabadzene.ui.player.VolumeDialog
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
    /** Több kijelölt szám: a közös pontok mellett kedvencek, megosztás és törlés is. */
    val selectionActions: Boolean = false,
    /** Bármelyik menüpont után fut (pl. a kijelölő mód bezárása). */
    val onFinished: (() -> Unit)? = null,
    /** Ha nem null: „Mentés új listaként” ezzel a névvel (album, előadó, műfaj, mappa). */
    val playlistName: String? = null,
    /** A gyűjtemény képernyője: a menüből indított lejátszás ezt jegyzi meg (null = nem változik). */
    val origin: PlaybackOrigin? = null,
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
    /** A telefonról törlendő fájlok (egy vagy több kijelölt szám). */
    var deleteFiles by mutableStateOf<List<Track>?>(null)
    var editLyrics by mutableStateOf<Track?>(null)
    var sleepTimerOpen by mutableStateOf(false)

    // A lejátszó ablakai és a lejátszásszám szerkesztése.
    var repeatOpen by mutableStateOf(false)
    var speedOpen by mutableStateOf(false)
    var volumeOpen by mutableStateOf(false)
    var skipSettingsOpen by mutableStateOf(false)
    var playCountFor by mutableStateOf<Track?>(null)

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

/** Több szám megosztása egyszerre. */
fun shareTracks(context: Context, tracks: List<Track>) {
    if (tracks.size == 1) return shareTrack(context, tracks.first())
    if (tracks.isEmpty()) return
    val uris = ArrayList(tracks.map { it.contentUri })
    val send = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType("audio/*")
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(tracks.first().title, uris.first()).apply {
        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // Nincs megosztásra alkalmas alkalmazás.
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
    val stats by container.playStats.stats.collectAsStateWithLifecycle(emptyMap())

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
            val plays = stats[track.id]?.playCount ?: 0
            add(TrackAction(pluralStringResource(R.plurals.action_play_count, plays, plays), Icons.Rounded.BarChart, "action_play_count") {
                menu.playCountFor = track
            })
            add(TrackAction(stringResource(R.string.action_file_info), Icons.Rounded.Info, "action_file_info") {
                menu.infoTrack = track
            })
            // Fájlműveletek: csak ténylegesen működő pontok (a címkeszerkesztés csak írható formátumnál).
            if (container.fileEditor.isTagWritable(track)) {
                add(TrackAction(stringResource(R.string.action_edit_tags), Icons.Rounded.Edit, "action_edit_tags") {
                    menu.editTags = track
                })
                add(TrackAction(stringResource(R.string.action_edit_lyrics), Icons.Rounded.Lyrics, "action_edit_lyrics") {
                    menu.editLyrics = track
                })
            }
            add(TrackAction(stringResource(R.string.action_trim), Icons.Rounded.ContentCut, "action_trim") {
                menu.trim = track
            })
            add(TrackAction(stringResource(R.string.action_set_ringtone), Icons.Rounded.NotificationsActive, "action_set_ringtone") {
                menu.ringtone = track
            })
            add(TrackAction(stringResource(R.string.action_delete_file), Icons.Rounded.DeleteForever, "action_delete_file") {
                menu.deleteFiles = listOf(track)
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
                request.origin?.let(container::recordPlaybackOrigin)
                container.player.playTracks(tracks, 0)
            })
            add(TrackAction(stringResource(R.string.action_shuffle), Icons.Rounded.Shuffle, "action_shuffle_all") {
                request.origin?.let(container::recordPlaybackOrigin)
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
            request.playlistName?.let { suggested ->
                add(TrackAction(stringResource(R.string.action_save_as_playlist), Icons.Rounded.LibraryAdd, "action_save_as_playlist") {
                    scope.launch {
                        val name = uniquePlaylistName(suggested, playlists.map { it.name })
                        container.playlists.create(name, tracks.map { it.id })
                        snackbar.showSnackbar(resources.getString(R.string.toast_playlist_created, name))
                    }
                })
            }
            // Minden gyűjteményből (lista, album, előadó, mappa, kijelölés) egyben a kedvencekhez adható.
            add(TrackAction(stringResource(R.string.action_add_favorite), Icons.Rounded.FavoriteBorder, "action_favorite") {
                scope.launch { container.playlists.addFavorites(tracks.map { it.id }) }
                notify(resources.getQuantityString(R.plurals.toast_tracks_added_to_favorites, tracks.size, tracks.size))
            })
            if (request.selectionActions) {
                add(TrackAction(stringResource(R.string.action_share), Icons.Rounded.Share, "action_share") {
                    shareTracks(context, tracks)
                })
                add(TrackAction(stringResource(R.string.action_delete_file), Icons.Rounded.DeleteForever, "action_delete_file") {
                    menu.deleteFiles = tracks
                })
            }
            addAll(request.extraActions)
        }
        val shown = if (tracks.isEmpty()) request.extraActions else actions
        TrackActionsSheet(
            title = request.title,
            subtitle = request.subtitle,
            trackId = request.artworkTrackId,
            actions = request.onFinished?.let { finished ->
                shown.map { action -> action.copy(onClick = { action.onClick(); finished() }) }
            } ?: shown,
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

    PlayerDialogsHost(menu)

    menu.playCountFor?.let { target ->
        PlayCountDialog(
            title = target.title,
            current = stats[target.id]?.playCount ?: 0,
            onSave = { count ->
                menu.playCountFor = null
                scope.launch { container.playStats.setPlayCount(target.id, count) }
            },
            onDismiss = { menu.playCountFor = null },
        )
    }

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

/** A lejátszó ablakai: ismétlési mód, sebesség, hangerő, ugrógombok. */
@Composable
private fun PlayerDialogsHost(menu: MenuState) {
    val container = LocalAppContainer.current ?: return
    val scope = rememberCoroutineScope()
    val settings = container.settings
    if (menu.repeatOpen) {
        val playback by container.player.state.collectAsStateWithLifecycle()
        val prefs by settings.playback.collectAsStateWithLifecycle(PlaybackPrefs())
        RepeatModeDialog(
            selected = if (prefs.stopAfterCurrent) RepeatSetting.STOP_AFTER_CURRENT else playback.repeat,
            onSelect = { mode ->
                menu.repeatOpen = false
                scope.launch { settings.setStopAfterCurrent(mode == RepeatSetting.STOP_AFTER_CURRENT) }
                container.player.setRepeat(mode)
            },
            onDismiss = { menu.repeatOpen = false },
        )
    }
    if (menu.speedOpen) {
        val prefs by settings.playback.collectAsStateWithLifecycle(PlaybackPrefs())
        SpeedDialog(
            speed = prefs.speed,
            onChange = { speed -> scope.launch { settings.setSpeed(speed) } },
            onDismiss = { menu.speedOpen = false },
        )
    }
    if (menu.volumeOpen) {
        VolumeDialog(onDismiss = { menu.volumeOpen = false })
    }
    if (menu.skipSettingsOpen) {
        val skip by settings.skipSettings.collectAsStateWithLifecycle(SkipSettings())
        SkipSettingsDialog(
            skip = skip,
            onBack = { scope.launch { settings.setSkipBackSeconds(it) } },
            onForward = { scope.launch { settings.setSkipForwardSeconds(it) } },
            onExtra = { scope.launch { settings.setSkipExtraSeconds(it) } },
            onDismiss = { menu.skipSettingsOpen = false },
        )
    }
}

/** „N szám” felirat. */
@Composable
fun trackCountText(count: Int): String = pluralStringResource(R.plurals.track_count, count, count)
