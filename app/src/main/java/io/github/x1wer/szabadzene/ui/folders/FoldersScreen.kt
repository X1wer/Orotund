package io.github.x1wer.szabadzene.ui.folders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.x1wer.szabadzene.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.ui.CollectionMenuRequest
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.LocalMenuState
import io.github.x1wer.szabadzene.ui.components.TrackAction
import io.github.x1wer.szabadzene.ui.library.CenteredMessage
import io.github.x1wer.szabadzene.ui.library.CollectionType
import io.github.x1wer.szabadzene.ui.library.GroupList
import io.github.x1wer.szabadzene.ui.library.LibraryViewModel
import io.github.x1wer.szabadzene.ui.library.groupSubtitle
import kotlinx.coroutines.launch

/** Mappák szerinti böngészés; a mappa kizárható (a Beállításokban visszavehető). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onOpenFolder: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val menu = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val scanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val unknown = stringResource(R.string.unknown_folder)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val excludeLabel = stringResource(R.string.action_exclude_folder)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.folders_title)) },
            actions = {
                IconButton(onClick = { viewModel.rescan() }, enabled = !scanning, modifier = Modifier.testTag("rescan")) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.settings_rescan))
                }
            },
        )
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (folders.isEmpty()) {
            CenteredMessage(
                contentPadding = contentPadding,
                icon = Icons.Rounded.Folder,
                body = stringResource(R.string.folders_empty),
            )
        } else {
            GroupList(
                groups = folders,
                unknownName = unknown,
                subtitle = { group -> groupSubtitle(CollectionType.FOLDER, group, unknownArtist) },
                onOpen = { onOpenFolder(it.key) },
                onMenu = { group ->
                    menu.openCollection(
                        CollectionMenuRequest(
                            title = group.name ?: unknown,
                            subtitle = group.detail.orEmpty(),
                            artworkTrackId = group.artworkTrackId,
                            tracks = group.tracks,
                            playlistName = group.name ?: unknown,
                            origin = io.github.x1wer.szabadzene.ui.PlaybackOrigin.collection(CollectionType.FOLDER, group.key),
                            extraActions = listOf(
                                TrackAction(excludeLabel, Icons.Rounded.FolderOff, "action_exclude_folder") {
                                    scope.launch { container?.settings?.excludeFolder(group.key) }
                                },
                            ),
                        ),
                    )
                },
                contentPadding = contentPadding,
                icon = Icons.Rounded.Folder,
            )
        }
    }
}
