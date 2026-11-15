package io.github.x1wer.szabadzene.ui

import android.net.Uri
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.x1wer.szabadzene.AppContainer
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.PlaybackEvent
import io.github.x1wer.szabadzene.playback.PlaybackUiState
import io.github.x1wer.szabadzene.playback.SkipDirection
import io.github.x1wer.szabadzene.ui.folders.FoldersScreen
import io.github.x1wer.szabadzene.ui.library.CollectionDetailScreen
import io.github.x1wer.szabadzene.ui.library.CollectionType
import io.github.x1wer.szabadzene.ui.library.LibraryScreen
import io.github.x1wer.szabadzene.ui.library.LibraryViewModel
import io.github.x1wer.szabadzene.ui.player.FullPlayerContent
import io.github.x1wer.szabadzene.ui.player.MiniPlayerContent
import io.github.x1wer.szabadzene.ui.player.PlayerCallbacks
import io.github.x1wer.szabadzene.ui.playlists.PlaylistDetailScreen
import io.github.x1wer.szabadzene.ui.playlists.PlaylistsScreen
import io.github.x1wer.szabadzene.ui.queue.QueueScreen
import io.github.x1wer.szabadzene.ui.settings.HelpScreen
import io.github.x1wer.szabadzene.ui.settings.LicensesScreen
import io.github.x1wer.szabadzene.ui.settings.SettingsScreen
import io.github.x1wer.szabadzene.ui.sound.EqualizerScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Az alkalmazás összetevői a felület számára. Tesztben null lehet. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer?> { null }

private const val ROUTE_MAIN = "main"
private const val ROUTE_PLAYER = "player"
private const val ROUTE_QUEUE = "queue"
private const val ROUTE_EQUALIZER = "equalizer"

private const val GRAPH_LIBRARY = "tab_library"
private const val GRAPH_PLAYLISTS = "tab_playlists"
private const val GRAPH_FOLDERS = "tab_folders"
private const val GRAPH_SETTINGS = "tab_settings"

private data class Tab(val graph: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(GRAPH_LIBRARY, R.string.nav_library, Icons.Rounded.LibraryMusic),
    Tab(GRAPH_PLAYLISTS, R.string.nav_playlists, Icons.AutoMirrored.Rounded.QueueMusic),
    Tab(GRAPH_FOLDERS, R.string.nav_folders, Icons.Rounded.Folder),
    Tab(GRAPH_SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
)

@Composable
fun AppRoot(openPlayerRequests: StateFlow<Int>) {
    val container = LocalAppContainer.current ?: return
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val menu = remember { MenuState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))

    val playback by container.player.state.collectAsStateWithLifecycle()
    val skip by container.settings.skipSettings.collectAsStateWithLifecycle(SkipSettings())
    val favorites by libraryViewModel.favoriteIds.collectAsStateWithLifecycle()
    val isFavorite = playback.trackId?.let { it in favorites } == true
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
    val fullCallbacks = callbacks.copy(
        onToggleFavorite = {
            val id = playback.trackId
            if (id != null) {
                scope.launch {
                    if (id in favorites) container.playlists.removeFavorite(id) else container.playlists.addFavorites(listOf(id))
                }
            }
        },
        onOpenQueue = { nav.navigate(ROUTE_QUEUE) { launchSingleTop = true } },
        onOpenEqualizer = { nav.navigate(ROUTE_EQUALIZER) { launchSingleTop = true } },
    )
    val sleepTimer by container.player.sleepTimer.collectAsStateWithLifecycle()

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

    CompositionLocalProvider(LocalMenuState provides menu) {
        NavHost(nav, startDestination = ROUTE_MAIN, modifier = Modifier.fillMaxSize()) {
            composable(ROUTE_MAIN) {
                MainScaffold(
                    libraryViewModel = libraryViewModel,
                    playback = playback,
                    skip = skip,
                    callbacks = callbacks,
                    snackbar = snackbar,
                    onOpenPlayer = { nav.navigate(ROUTE_PLAYER) { launchSingleTop = true } },
                    onOpenEqualizer = { nav.navigate(ROUTE_EQUALIZER) { launchSingleTop = true } },
                )
            }
            composable(
                ROUTE_PLAYER,
                enterTransition = { slideInVertically { it } },
                popExitTransition = { slideOutVertically { it } },
            ) {
                val tracks by libraryViewModel.allTracks.collectAsStateWithLifecycle()
                val currentTrack = tracks.firstOrNull { it.id == playback.trackId }
                Box(Modifier.fillMaxSize()) {
                    FullPlayerContent(
                        state = playback,
                        skip = skip,
                        callbacks = fullCallbacks,
                        onMinimize = { nav.popBackStack() },
                        onMoreActions = currentTrack?.let { track ->
                            { menu.open(track, TrackMenuContext.NowPlaying) }
                        },
                        isFavorite = isFavorite,
                        sleepTimer = sleepTimer,
                        onOpenSleepTimer = { menu.sleepTimerOpen = true },
                    )
                    SnackbarHost(
                        snackbar,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding(),
                    )
                }
            }
            composable(
                ROUTE_EQUALIZER,
                enterTransition = { slideInHorizontally { it } },
                popExitTransition = { slideOutHorizontally { it } },
            ) {
                EqualizerScreen(onBack = { nav.popBackStack() })
            }
            composable(
                ROUTE_QUEUE,
                enterTransition = { slideInHorizontally { it } },
                popExitTransition = { slideOutHorizontally { it } },
            ) {
                val tracks by libraryViewModel.allTracks.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                    QueueScreen(allTracks = tracks, onBack = { nav.popBackStack() })
                    SnackbarHost(
                        snackbar,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding(),
                    )
                }
            }
        }
        MenuHost(menu, snackbar)
    }
}

private fun collectionRoute(prefix: String, type: CollectionType, key: String) =
    "$prefix/collection/${type.name}/${Uri.encode(key)}"

@Composable
private fun MainScaffold(
    libraryViewModel: LibraryViewModel,
    playback: PlaybackUiState,
    skip: SkipSettings,
    callbacks: PlayerCallbacks,
    snackbar: SnackbarHostState,
    onOpenPlayer: () -> Unit,
    onOpenEqualizer: () -> Unit,
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
                            selected = destination?.hierarchy?.any { it.route == tab.graph } == true,
                            onClick = { tabNav.navigateToTab(tab.graph) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                            modifier = Modifier.testTag(tab.graph),
                        )
                    }
                }
            }
        },
    ) { inner ->
        val current = playback.mediaId
        NavHost(tabNav, startDestination = GRAPH_LIBRARY, modifier = Modifier.fillMaxSize()) {
            navigation(startDestination = "library", route = GRAPH_LIBRARY) {
                composable("library") {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        currentMediaId = current,
                        contentPadding = inner,
                        onOpenCollection = { type, key -> tabNav.navigate(collectionRoute("library", type, key)) },
                    )
                }
                collectionDestination("library", libraryViewModel, current, inner, tabNav)
            }
            navigation(startDestination = "playlists", route = GRAPH_PLAYLISTS) {
                composable("playlists") {
                    PlaylistsScreen(
                        viewModel = libraryViewModel,
                        contentPadding = inner,
                        onOpenPlaylist = { id -> tabNav.navigate("playlists/playlist/$id") },
                    )
                }
                composable(
                    "playlists/playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    PlaylistDetailScreen(
                        viewModel = libraryViewModel,
                        playlistId = entry.arguments?.getLong("id") ?: 0L,
                        currentMediaId = current,
                        contentPadding = inner,
                        onBack = { tabNav.popBackStack() },
                    )
                }
            }
            navigation(startDestination = "folders", route = GRAPH_FOLDERS) {
                composable("folders") {
                    FoldersScreen(
                        viewModel = libraryViewModel,
                        contentPadding = inner,
                        onOpenFolder = { key -> tabNav.navigate(collectionRoute("folders", CollectionType.FOLDER, key)) },
                    )
                }
                collectionDestination("folders", libraryViewModel, current, inner, tabNav)
            }
            navigation(startDestination = "settings", route = GRAPH_SETTINGS) {
                composable("settings") {
                    SettingsScreen(
                        contentPadding = inner,
                        snackbar = snackbar,
                        onOpenHelp = { tabNav.navigate("settings/help") },
                        onOpenLicenses = { tabNav.navigate("settings/licenses") },
                        onOpenEqualizer = onOpenEqualizer,
                    )
                }
                composable("settings/help") { HelpScreen(inner) { tabNav.popBackStack() } }
                composable("settings/licenses") { LicensesScreen(inner) { tabNav.popBackStack() } }
            }
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.collectionDestination(
    prefix: String,
    viewModel: LibraryViewModel,
    currentMediaId: String?,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    tabNav: NavHostController,
) {
    composable(
        "$prefix/collection/{type}/{key}",
        arguments = listOf(
            navArgument("type") { type = NavType.StringType },
            navArgument("key") { type = NavType.StringType },
        ),
    ) { entry ->
        val type = CollectionType.valueOf(entry.arguments?.getString("type") ?: CollectionType.ALBUM.name)
        val key = Uri.decode(entry.arguments?.getString("key").orEmpty())
        CollectionDetailScreen(
            viewModel = viewModel,
            type = type,
            key = key,
            currentMediaId = currentMediaId,
            contentPadding = contentPadding,
            onBack = { tabNav.popBackStack() },
        )
    }
}

/** Fülváltás: a fülek saját visszalépési veremmel, állapotukat megőrizve. */
private fun NavHostController.navigateToTab(graph: String) {
    navigate(graph) {
        popUpTo(graph.let { this@navigateToTab.graph.findStartDestination().id }) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
