package io.github.x1wer.szabadzene.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.x1wer.szabadzene.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.ui.CollectionMenuRequest
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.LocalMenuState
import io.github.x1wer.szabadzene.ui.components.TrackAction
import io.github.x1wer.szabadzene.ui.trackCountText
import kotlinx.coroutines.launch

/** Album, előadó, műfaj vagy mappa számai, „Lejátszás” és „Keverés” gombbal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    viewModel: LibraryViewModel,
    type: CollectionType,
    key: String,
    currentMediaId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val menu = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val flow = remember(type, key) { viewModel.collection(type, key) }
    val group by flow.collectAsStateWithLifecycle(null)
    val unknown = stringResource(unknownNameFor(type))
    val unknownArtist = stringResource(R.string.unknown_artist)
    val excludeLabel = stringResource(R.string.action_exclude_folder)
    val title = group?.name ?: unknown

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    group?.let {
                        Text(
                            groupSubtitle(type, it, unknownArtist),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
            actions = {
                val current = group
                if (current != null) {
                    IconButton(
                        onClick = {
                            val extra = if (type == CollectionType.FOLDER && container != null) {
                                listOf(
                                    TrackAction(excludeLabel, Icons.Rounded.FolderOff, "action_exclude_folder") {
                                        scope.launch { container.settings.excludeFolder(current.key) }
                                        onBack()
                                    },
                                )
                            } else {
                                emptyList()
                            }
                            menu.openCollection(
                                CollectionMenuRequest(
                                    title = title,
                                    subtitle = current.detail ?: "",
                                    artworkTrackId = current.artworkTrackId,
                                    tracks = current.tracks,
                                    extraActions = extra,
                                    playlistName = title,
                                ),
                            )
                        },
                        modifier = Modifier.testTag("collection_menu"),
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
                    }
                }
            },
        )
        val current = group
        if (current == null || current.tracks.isEmpty()) {
            CenteredMessage(contentPadding = contentPadding, body = stringResource(R.string.collection_empty))
        } else {
            TrackList(
                tracks = current.tracks,
                currentMediaId = currentMediaId,
                contentPadding = contentPadding,
                header = {
                    item(key = "header") {
                        Column {
                            PlayShuffleButtons(current.tracks)
                            Text(
                                trackCountText(current.tracks.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                },
            )
        }
    }
}
