package io.github.x1wer.szabadzene.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.library.LibraryRepository
import io.github.x1wer.szabadzene.data.library.TrackGroup
import io.github.x1wer.szabadzene.data.settings.LibrarySort
import io.github.x1wer.szabadzene.data.settings.SortField
import io.github.x1wer.szabadzene.ui.CollectionMenuRequest
import io.github.x1wer.szabadzene.ui.LocalMenuState
import io.github.x1wer.szabadzene.ui.trackCountText

private enum class LibraryTab(val label: Int) {
    TRACKS(R.string.library_tab_tracks),
    ALBUMS(R.string.library_tab_albums),
    ARTISTS(R.string.library_tab_artists),
    GENRES(R.string.library_tab_genres),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    currentMediaId: String?,
    contentPadding: PaddingValues,
    onOpenCollection: (CollectionType, String) -> Unit,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val scanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scannedOnce by viewModel.hasScannedOnce.collectAsStateWithLifecycle()

    var hasPermission by rememberSaveable { mutableStateOf(viewModel.hasPermission()) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.onPermissionGranted()
        } else {
            permanentlyDenied = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, LibraryRepository.requiredPermission())
            } ?: false
        }
    }
    // A felhasználó a rendszerbeállításokban is megadhatja az engedélyt: visszatéréskor ellenőrizzük.
    LifecycleResumeEffect(Unit) {
        val now = viewModel.hasPermission()
        if (now && !hasPermission) viewModel.onPermissionGranted()
        hasPermission = now
        onPauseOrDispose { }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    Column(Modifier.fillMaxSize()) {
        if (searching) {
            SearchBarRow(
                query = query,
                onQueryChange = { query = it },
                onClose = {
                    searching = false
                    query = ""
                },
            )
        } else {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.library_title))
                        val count = tracks?.size ?: 0
                        if (hasPermission && count > 0) {
                            Text(
                                text = pluralStringResource(R.plurals.library_track_count, count, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = { searching = true }, modifier = Modifier.testTag("search_open")) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.cd_search))
                        }
                        if (tab == LibraryTab.TRACKS.ordinal) SortMenu(viewModel)
                    }
                },
            )
        }
        val list = tracks
        when {
            !hasPermission -> PermissionContent(
                permanentlyDenied = permanentlyDenied,
                onRequest = { launcher.launch(LibraryRepository.requiredPermission()) },
                contentPadding = contentPadding,
            )
            list == null || (list.isEmpty() && (scanning || !scannedOnce)) -> CenteredMessage(contentPadding) {
                CircularProgressIndicator()
                Text(stringResource(R.string.library_scanning), style = MaterialTheme.typography.bodyLarge)
            }
            list.isEmpty() -> CenteredMessage(
                contentPadding = contentPadding,
                icon = Icons.Rounded.LibraryMusic,
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body),
            )
            searching -> SearchResults(viewModel, query, currentMediaId, contentPadding)
            else -> {
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    LibraryTab.entries.forEach { entry ->
                        Tab(
                            selected = tab == entry.ordinal,
                            onClick = { tab = entry.ordinal },
                            text = { Text(stringResource(entry.label)) },
                            modifier = Modifier
                                .testTag("library_tab_${entry.name.lowercase()}")
                                .heightIn(min = 48.dp),
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (LibraryTab.entries[tab]) {
                        LibraryTab.TRACKS -> {
                            val sorted by viewModel.sortedTracks.collectAsStateWithLifecycle()
                            TrackList(sorted ?: list, currentMediaId, contentPadding)
                        }
                        LibraryTab.ALBUMS -> GroupTab(viewModel, CollectionType.ALBUM, contentPadding, onOpenCollection)
                        LibraryTab.ARTISTS -> GroupTab(viewModel, CollectionType.ARTIST, contentPadding, onOpenCollection)
                        LibraryTab.GENRES -> GroupTab(viewModel, CollectionType.GENRE, contentPadding, onOpenCollection)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBarRow(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Surface(tonalElevation = 3.dp) {
        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_close_search))
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .testTag("search_field")
                    .weight(1f)
                    .focusRequester(focus),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    viewModel: LibraryViewModel,
    query: String,
    currentMediaId: String?,
    contentPadding: PaddingValues,
) {
    val index by viewModel.searchIndex.collectAsStateWithLifecycle()
    val results = remember(index, query) { index?.search(query).orEmpty() }
    when {
        query.isBlank() -> CenteredMessage(
            contentPadding = contentPadding,
            icon = Icons.Rounded.Search,
            body = stringResource(R.string.search_prompt),
        )
        results.isEmpty() -> CenteredMessage(
            contentPadding = contentPadding,
            icon = Icons.Rounded.SearchOff,
            title = stringResource(R.string.search_no_results),
        )
        else -> TrackList(results, currentMediaId, contentPadding)
    }
}

@Composable
private fun SortMenu(viewModel: LibraryViewModel) {
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("sort_open")) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.cd_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                SortField.TITLE to R.string.sort_title,
                SortField.ARTIST to R.string.sort_artist,
                SortField.DURATION to R.string.sort_duration,
                SortField.DATE_ADDED to R.string.sort_date_added,
            ).forEach { (field, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        viewModel.setSort(sort.copy(field = field))
                        open = false
                    },
                    trailingIcon = { if (sort.field == field) Icon(Icons.Rounded.Check, contentDescription = null) },
                    modifier = Modifier
                        .testTag("sort_${field.name.lowercase()}")
                        .heightIn(min = 56.dp),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_descending)) },
                onClick = {
                    viewModel.setSort(LibrarySort(sort.field, !sort.descending))
                    open = false
                },
                trailingIcon = { if (sort.descending) Icon(Icons.Rounded.Check, contentDescription = null) },
                modifier = Modifier
                    .testTag("sort_descending")
                    .heightIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun GroupTab(
    viewModel: LibraryViewModel,
    type: CollectionType,
    contentPadding: PaddingValues,
    onOpenCollection: (CollectionType, String) -> Unit,
) {
    val menu = LocalMenuState.current
    val resources = LocalResources.current
    val source = when (type) {
        CollectionType.ALBUM -> viewModel.albums
        CollectionType.ARTIST -> viewModel.artists
        CollectionType.GENRE -> viewModel.genres
        CollectionType.FOLDER -> viewModel.folders
    }
    val groups by source.collectAsStateWithLifecycle()
    val unknown = stringResource(unknownNameFor(type))
    val unknownArtist = stringResource(R.string.unknown_artist)
    GroupList(
        groups = groups,
        unknownName = unknown,
        subtitle = { group -> groupSubtitle(type, group, unknownArtist) },
        onOpen = { onOpenCollection(type, it.key) },
        onMenu = { group ->
            menu.openCollection(
                CollectionMenuRequest(
                    title = group.name ?: unknown,
                    subtitle = resources.getQuantityString(R.plurals.track_count, group.tracks.size, group.tracks.size),
                    artworkTrackId = group.artworkTrackId,
                    tracks = group.tracks,
                ),
            )
        },
        contentPadding = contentPadding,
    )
}

fun unknownNameFor(type: CollectionType): Int = when (type) {
    CollectionType.ALBUM -> R.string.unknown_album
    CollectionType.ARTIST -> R.string.unknown_artist
    CollectionType.GENRE -> R.string.unknown_genre
    CollectionType.FOLDER -> R.string.unknown_folder
}

/** Albumnál „Előadó · N szám”, mappánál „útvonal · N szám”, egyébként „N szám”. */
@Composable
fun groupSubtitle(type: CollectionType, group: TrackGroup, unknownArtist: String): String {
    val count = trackCountText(group.tracks.size)
    return when (type) {
        CollectionType.ALBUM -> "${group.detail ?: unknownArtist} · $count"
        CollectionType.FOLDER -> "${group.detail.orEmpty()} · $count"
        else -> count
    }
}

@Composable
private fun PermissionContent(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    CenteredMessage(
        contentPadding = contentPadding,
        icon = Icons.Rounded.LibraryMusic,
        title = stringResource(R.string.permission_title),
        body = stringResource(if (permanentlyDenied) R.string.permission_denied_body else R.string.permission_body),
    ) {
        Spacer(Modifier.height(4.dp))
        if (permanentlyDenied) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.permission_open_settings)) }
        } else {
            Button(
                onClick = onRequest,
                modifier = Modifier
                    .testTag("grant_permission")
                    .heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.permission_grant)) }
        }
    }
}
