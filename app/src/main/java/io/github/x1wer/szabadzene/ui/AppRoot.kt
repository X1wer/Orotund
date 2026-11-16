package io.github.x1wer.szabadzene.ui

import android.net.Uri
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import io.github.x1wer.szabadzene.ui.components.Icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.x1wer.szabadzene.AppContainer
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.PlaybackPrefs
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.RepeatSetting
import io.github.x1wer.szabadzene.ui.lyrics.LyricsPane
import io.github.x1wer.szabadzene.ui.player.FullPlayerExtras
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
import io.github.x1wer.szabadzene.ui.theme.LocalIconScale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Az alkalmazás összetevői a felület számára. Tesztben null lehet. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer?> { null }

/** A fő képernyő üzenetsávja (rövid visszajelzésekhez a képernyőkből). Tesztben null lehet. */
val LocalSnackbarHost = staticCompositionLocalOf<SnackbarHostState?> { null }

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

    val rawPlayback by container.player.state.collectAsStateWithLifecycle()
    val playbackPrefs by container.settings.playback.collectAsStateWithLifecycle(PlaybackPrefs())
    // A „leállás az aktuális szám végén” módot a beállítás tárolja; a felület egy ismétlési módként mutatja.
    val playback = if (playbackPrefs.stopAfterCurrent) {
        rawPlayback.copy(repeat = RepeatSetting.STOP_AFTER_CURRENT)
    } else {
        rawPlayback
    }
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
            onSkipBy = { direction, seconds -> player.skip(direction, seconds) },
            onToggleShuffle = player::toggleShuffle,
            onOpenRepeat = { menu.repeatOpen = true },
            onOpenQueue = { nav.navigate(ROUTE_QUEUE) { launchSingleTop = true } },
        )
    }
    val tracks by libraryViewModel.allTracks.collectAsStateWithLifecycle()
    val currentTrack = tracks.firstOrNull { it.id == playback.trackId }
    val fullCallbacks = callbacks.copy(
        onToggleFavorite = {
            val id = playback.trackId
            if (id != null) {
                scope.launch {
                    if (id in favorites) container.playlists.removeFavorite(id) else container.playlists.addFavorites(listOf(id))
                }
            }
        },
        onOpenEqualizer = { nav.navigate(ROUTE_EQUALIZER) { launchSingleTop = true } },
        onOpenSpeed = { menu.speedOpen = true },
        onOpenVolume = { menu.volumeOpen = true },
        onAddToPlaylist = { currentTrack?.let { menu.addToPlaylist = listOf(it) } },
        onOpenSleepTimer = { menu.sleepTimerOpen = true },
        onOpenSkipSettings = { menu.skipSettingsOpen = true },
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

    CompositionLocalProvider(LocalMenuState provides menu, LocalSnackbarHost provides snackbar) {
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
                Box(Modifier.fillMaxSize()) {
                    FullPlayerContent(
                        state = playback,
                        skip = skip,
                        callbacks = fullCallbacks,
                        onMinimize = { nav.popBackStack() },
                        onMoreActions = currentTrack?.let { track ->
                            { menu.open(track, TrackMenuContext.NowPlaying) }
                        },
                        extras = FullPlayerExtras(isFavorite = isFavorite, sleepTimer = sleepTimer, speed = playbackPrefs.speed),
                        lyrics = { modifier ->
                            LyricsPane(
                                track = currentTrack,
                                positionMs = playback.positionMs,
                                onSeek = container.player::seekTo,
                                onEdit = { menu.editLyrics = it },
                                modifier = modifier,
                            )
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
    // Fülenként saját navigáció (külön hívási helyen, hogy a mentett állapotuk ne keveredjen).
    val libraryNav = rememberNavController()
    val playlistsNav = rememberNavController()
    val foldersNav = rememberNavController()
    val settingsNav = rememberNavController()
    val navs = listOf(libraryNav, playlistsNav, foldersNav, settingsNav)
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    // A fül gyökerén a Vissza gomb az első fülre visz (a fülek saját visszalépése ennél erősebb).
    BackHandler(enabled = pager.currentPage != 0) { scope.launch { pager.animateScrollToPage(0) } }

    // Csak a látható fül kapja meg a Vissza gombot; a többi fül egy néma diszpécsert lát.
    val activityBackOwner = LocalOnBackPressedDispatcherOwner.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val inactiveBackOwner = remember(lifecycleOwner) {
        object : OnBackPressedDispatcherOwner {
            override val onBackPressedDispatcher = OnBackPressedDispatcher()
            override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
        }
    }

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
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = pager.targetPage == index,
                            onClick = {
                                if (pager.currentPage == index) {
                                    // Az aktuális fülre koppintva vissza a fül kezdőképernyőjére.
                                    val nav = navs[index]
                                    nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false)
                                } else {
                                    scope.launch { pager.animateScrollToPage(index) }
                                }
                            },
                            // Az ikon legfeljebb 1,3-szorosra nő, hogy a kiemelés „pirulájában” maradjon.
                            icon = {
                                CompositionLocalProvider(LocalIconScale provides LocalIconScale.current.coerceAtMost(1.3f)) {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            // Négy fül 320 dp-n 2,0× betűnél már nem fér el: a címke betűméretét 1,3×-nél
                            // megfogjuk (az ikon és a teljes feliratú akadálymentes leírás megmarad).
                            label = {
                                val density = LocalDensity.current
                                CompositionLocalProvider(
                                    LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.3f)),
                                ) {
                                    Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            modifier = Modifier.testTag(tab.graph),
                        )
                    }
                }
            }
        },
    ) { inner ->
        val current = playback.mediaId
        // A fülek között oldalra húzással is lehet váltani. Mind a négy fül komponálva marad, így a
        // saját navigációjuk (pl. egy megnyitott album) váltáskor megmarad.
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = tabs.size - 1,
            key = { tabs[it].graph },
            modifier = Modifier
                .fillMaxSize()
                .testTag("main_pager"),
        ) { page ->
            CompositionLocalProvider(
                LocalOnBackPressedDispatcherOwner provides
                    if (page == pager.currentPage && activityBackOwner != null) activityBackOwner else inactiveBackOwner,
            ) {
                when (page) {
                    0 -> NavHost(libraryNav, startDestination = "library", modifier = Modifier.fillMaxSize()) {
                        composable("library") {
                            LibraryScreen(
                                viewModel = libraryViewModel,
                                currentMediaId = current,
                                contentPadding = inner,
                                onOpenCollection = { type, key -> libraryNav.navigate(collectionRoute("library", type, key)) },
                            )
                        }
                        collectionDestination("library", libraryViewModel, current, inner, libraryNav)
                    }
                    1 -> NavHost(playlistsNav, startDestination = "playlists", modifier = Modifier.fillMaxSize()) {
                        composable("playlists") {
                            PlaylistsScreen(
                                viewModel = libraryViewModel,
                                contentPadding = inner,
                                onOpenPlaylist = { id -> playlistsNav.navigate("playlists/playlist/$id") },
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
                                onBack = { playlistsNav.popBackStack() },
                            )
                        }
                    }
                    2 -> NavHost(foldersNav, startDestination = "folders", modifier = Modifier.fillMaxSize()) {
                        composable("folders") {
                            FoldersScreen(
                                viewModel = libraryViewModel,
                                contentPadding = inner,
                                onOpenFolder = { key -> foldersNav.navigate(collectionRoute("folders", CollectionType.FOLDER, key)) },
                            )
                        }
                        collectionDestination("folders", libraryViewModel, current, inner, foldersNav)
                    }
                    else -> NavHost(settingsNav, startDestination = "settings", modifier = Modifier.fillMaxSize()) {
                        composable("settings") {
                            SettingsScreen(
                                contentPadding = inner,
                                snackbar = snackbar,
                                onOpenHelp = { settingsNav.navigate("settings/help") },
                                onOpenLicenses = { settingsNav.navigate("settings/licenses") },
                                onOpenEqualizer = onOpenEqualizer,
                            )
                        }
                        composable("settings/help") { HelpScreen(inner) { settingsNav.popBackStack() } }
                        composable("settings/licenses") { LicensesScreen(inner) { settingsNav.popBackStack() } }
                    }
                }
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
