package io.github.x1wer.szabadzene.ui

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.x1wer.szabadzene.AppContainer
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.PlaybackEvent
import io.github.x1wer.szabadzene.playback.PlaybackUiState
import io.github.x1wer.szabadzene.playback.SkipDirection
import io.github.x1wer.szabadzene.ui.components.FileInfoDialog
import io.github.x1wer.szabadzene.ui.components.TrackAction
import io.github.x1wer.szabadzene.ui.components.TrackActionsSheet
import io.github.x1wer.szabadzene.ui.library.LibraryScreen
import io.github.x1wer.szabadzene.ui.library.LibraryViewModel
import io.github.x1wer.szabadzene.ui.player.FullPlayerContent
import io.github.x1wer.szabadzene.ui.player.MiniPlayerContent
import io.github.x1wer.szabadzene.ui.player.PlayerCallbacks
import io.github.x1wer.szabadzene.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Az alkalmazás összetevői a felület számára. Tesztben null lehet. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer?> { null }

/** A számsorok menüjének közös állapota: bármelyik lista megnyithatja. */
@Stable
class TrackMenuState {
    var track by mutableStateOf<Track?>(null)
        private set
    var queueActions by mutableStateOf(true)
        private set
    var infoTrack by mutableStateOf<Track?>(null)

    fun open(track: Track, withQueueActions: Boolean = true) {
        queueActions = withQueueActions
        this.track = track
    }

    fun close() {
        track = null
    }
}

val LocalTrackMenu = staticCompositionLocalOf { TrackMenuState() }

private const val ROUTE_MAIN = "main"
private const val ROUTE_PLAYER = "player"
private const val TAB_LIBRARY = "library"
private const val TAB_SETTINGS = "settings"

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(TAB_LIBRARY, R.string.nav_library, Icons.Rounded.LibraryMusic),
    Tab(TAB_SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
)

@Composable
fun AppRoot(openPlayerRequests: StateFlow<Int>) {
    val container = LocalAppContainer.current ?: return
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val menu = remember { TrackMenuState() }
    val resources = LocalResources.current
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))

    val playback by container.player.state.collectAsStateWithLifecycle()
    val skip by container.settings.skipSettings.collectAsStateWithLifecycle(SkipSettings())
    val callbacks = remember(container) {
        val player = container.player
        PlayerCallbacks(
            onPlayPause = player::togglePlayPause,
            onPrevious = player::previous,
            onNext = player::next,
            onSeek = player::seekTo,
            onSkip = { direction ->
                val current = skip
                val seconds = when (direction) {
                    SkipDirection.BACK -> current.backSeconds
                    SkipDirection.FORWARD -> current.forwardSeconds
                }
                player.skip(direction, seconds)
            },
            onToggleShuffle = player::toggleShuffle,
            onCycleRepeat = player::cycleRepeat,
        )
    }

    val openRequest by openPlayerRequests.collectAsState()
    LaunchedEffect(openRequest) {
        if (openRequest > 0 && nav.currentDestination?.route != ROUTE_PLAYER) {
            nav.navigate(ROUTE_PLAYER) { launchSingleTop = true }
        }
    }

    LaunchedEffect(container, resources) {
        container.player.events.collect { event ->
            when (event) {
                is PlaybackEvent.Error -> {
                    val title = event.title.ifBlank { resources.getString(R.string.unknown_track) }
                    snackbar.showSnackbar(
                        resources.getString(
                            if (event.stopped) R.string.error_playback_stopped else R.string.error_playback,
                            title,
                        ),
                    )
                }
            }
        }
    }

    CompositionLocalProvider(LocalTrackMenu provides menu) {
        NavHost(nav, startDestination = ROUTE_MAIN, modifier = Modifier.fillMaxSize()) {
            composable(ROUTE_MAIN) {
                MainScaffold(
                    libraryViewModel = libraryViewModel,
                    playback = playback,
                    skip = skip,
                    callbacks = callbacks,
                    snackbar = snackbar,
                    onOpenPlayer = { nav.navigate(ROUTE_PLAYER) { launchSingleTop = true } },
                )
            }
            composable(
                ROUTE_PLAYER,
                enterTransition = { slideInVertically { it } },
                popExitTransition = { slideOutVertically { it } },
            ) {
                val tracks by libraryViewModel.tracks.collectAsStateWithLifecycle()
                val currentTrack = tracks?.firstOrNull { it.id == playback.trackId }
                Box(Modifier.fillMaxSize()) {
                    FullPlayerContent(
                        state = playback,
                        skip = skip,
                        callbacks = callbacks,
                        onMinimize = { nav.popBackStack() },
                        onMoreActions = currentTrack?.let { track ->
                            { menu.open(track, withQueueActions = false) }
                        },
                    )
                    SnackbarHost(
                        snackbar,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding(),
                    )
                }
            }
        }
        TrackMenuHost(menu, snackbar)
    }
}

@Composable
private fun MainScaffold(
    libraryViewModel: LibraryViewModel,
    playback: PlaybackUiState,
    skip: SkipSettings,
    callbacks: PlayerCallbacks,
    snackbar: SnackbarHostState,
    onOpenPlayer: () -> Unit,
) {
    val tabNav = rememberNavController()
    val backStack by tabNav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                // A minilejátszó a navigáció fölött ül; a Scaffold a lista alján ennyi helyet hagy.
                if (playback.hasMedia) {
                    MiniPlayerContent(
                        state = playback,
                        skip = skip,
                        callbacks = callbacks,
                        onOpen = onOpenPlayer,
                    )
                }
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                tabNav.navigate(tab.route) {
                                    popUpTo(tabNav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                            modifier = Modifier.testTag("tab_${tab.route}"),
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(tabNav, startDestination = TAB_LIBRARY, modifier = Modifier.fillMaxSize()) {
            composable(TAB_LIBRARY) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    currentMediaId = playback.mediaId,
                    contentPadding = inner,
                )
            }
            composable(TAB_SETTINGS) { SettingsScreen(contentPadding = inner) }
        }
    }
}

/** A számmenü alsó panelje és a fájladatok ablak – egy helyen, minden lista számára. */
@Composable
private fun TrackMenuHost(menu: TrackMenuState, snackbar: SnackbarHostState) {
    val container = LocalAppContainer.current ?: return
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val unknownArtist = stringResource(R.string.unknown_artist)
    val track = menu.track
    if (track != null) {
        val actions = buildList {
            if (menu.queueActions) {
                add(
                    TrackAction(
                        label = stringResource(R.string.action_play_next),
                        icon = Icons.Rounded.QueuePlayNext,
                        tag = "action_play_next",
                    ) {
                        container.player.playNext(track)
                        scope.launch {
                            snackbar.showSnackbar(resources.getString(R.string.toast_play_next, track.title))
                        }
                    },
                )
                add(
                    TrackAction(
                        label = stringResource(R.string.action_add_to_queue),
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        tag = "action_add_to_queue",
                    ) {
                        container.player.addToQueue(track)
                        scope.launch {
                            snackbar.showSnackbar(resources.getString(R.string.toast_added_to_queue, track.title))
                        }
                    },
                )
            }
            add(
                TrackAction(
                    label = stringResource(R.string.action_file_info),
                    icon = Icons.Rounded.Info,
                    tag = "action_file_info",
                ) { menu.infoTrack = track },
            )
        }
        TrackActionsSheet(
            title = track.title,
            subtitle = track.artist ?: unknownArtist,
            trackId = track.id,
            actions = actions,
            onDismiss = { menu.close() },
        )
    }
    menu.infoTrack?.let { info ->
        FileInfoDialog(track = info, onDismiss = { menu.infoTrack = null })
    }
}
