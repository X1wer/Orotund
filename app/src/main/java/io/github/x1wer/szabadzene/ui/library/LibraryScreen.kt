package io.github.x1wer.szabadzene.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.library.LibraryRepository
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.LocalTrackMenu
import io.github.x1wer.szabadzene.ui.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    currentMediaId: String?,
    contentPadding: PaddingValues,
) {
    val container = LocalAppContainer.current ?: return
    val menu = LocalTrackMenu.current
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

    Column(Modifier.fillMaxSize()) {
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
        )
        val list = tracks
        when {
            !hasPermission -> PermissionContent(
                permanentlyDenied = permanentlyDenied,
                onRequest = { launcher.launch(LibraryRepository.requiredPermission()) },
                contentPadding = contentPadding,
            )
            list == null || (list.isEmpty() && (scanning || !scannedOnce)) -> LoadingContent(contentPadding)
            list.isEmpty() -> EmptyContent(contentPadding)
            else -> TrackList(
                tracks = list,
                currentMediaId = currentMediaId,
                contentPadding = contentPadding,
                onPlay = { index -> container.player.playTracks(list, index) },
                onMenu = { track -> menu.open(track) },
            )
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    currentMediaId: String?,
    contentPadding: PaddingValues,
    onPlay: (Int) -> Unit,
    onMenu: (Track) -> Unit,
) {
    val unknownArtist = stringResource(R.string.unknown_artist)
    LazyColumn(
        modifier = Modifier
            .testTag("track_list")
            .fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                title = track.title,
                subtitle = track.artist ?: unknownArtist,
                trackId = track.id,
                isCurrent = track.id.toString() == currentMediaId,
                onPlay = { onPlay(index) },
                onMenu = { onMenu(track) },
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun PermissionContent(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    CenteredMessage(contentPadding) {
        Icon(
            Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                if (permanentlyDenied) R.string.permission_denied_body else R.string.permission_body,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
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

@Composable
private fun LoadingContent(contentPadding: PaddingValues) {
    CenteredMessage(contentPadding) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.library_scanning),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyContent(contentPadding: PaddingValues) {
    CenteredMessage(contentPadding) {
        Icon(
            Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
