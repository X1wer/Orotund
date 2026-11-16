package io.github.x1wer.szabadzene.ui.playlists

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.playlists.PlaylistImportOutcome
import io.github.x1wer.szabadzene.data.playlists.PlaylistImporter
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.components.Icon
import kotlinx.coroutines.launch

/**
 * „Listák importálása” gomb a Listák fejlécében: listafájlok vagy egy egész mappa kiválasztása a
 * rendszer fájlválasztójával, majd összesítő ablak a megtalált számokról.
 */
@Composable
fun PlaylistImportButton() {
    val container = LocalAppContainer.current ?: return
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<List<PlaylistImportOutcome>?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun run(block: suspend () -> List<PlaylistImportOutcome>) {
        running = true
        scope.launch {
            val result = runCatching { block() }
            running = false
            report = result.getOrNull()
            failed = result.isFailure
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) run { container.playlistImporter.importFiles(uris) }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) run { container.playlistImporter.importFolder(uri) }
    }

    Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("playlist_import")) {
            Icon(Icons.Rounded.FileOpen, contentDescription = stringResource(R.string.playlist_import))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_import_files)) },
                leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    try {
                        pickFiles.launch(PlaylistImporter.PICKER_MIME_TYPES)
                    } catch (_: ActivityNotFoundException) {
                        failed = true
                    }
                },
                modifier = Modifier
                    .testTag("playlist_import_files")
                    .heightIn(min = 56.dp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_import_folder)) },
                leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    try {
                        pickFolder.launch(null)
                    } catch (_: ActivityNotFoundException) {
                        failed = true
                    }
                },
                modifier = Modifier
                    .testTag("playlist_import_folder")
                    .heightIn(min = 56.dp),
            )
        }
    }

    if (running) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            confirmButton = {},
            title = { Text(stringResource(R.string.playlist_import)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.playlist_import_running))
                }
            },
        )
    }

    val outcomes = report
    if (outcomes != null || failed) {
        ImportReportDialog(outcomes.orEmpty(), failed) {
            report = null
            failed = false
        }
    }
}

@Composable
private fun ImportReportDialog(outcomes: List<PlaylistImportOutcome>, failed: Boolean, onDismiss: () -> Unit) {
    val created = outcomes.count { it.status == PlaylistImportOutcome.Status.CREATED }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("playlist_import_report"),
        icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
        title = { Text(stringResource(R.string.playlist_import_done)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    failed -> Text(stringResource(R.string.playlist_import_failed))
                    outcomes.isEmpty() -> Text(stringResource(R.string.playlist_import_none_found))
                    else -> {
                        outcomes.forEach { outcome -> Text(outcomeText(outcome), style = MaterialTheme.typography.bodyMedium) }
                        if (created > 0 && outcomes.any { it.found < it.total }) {
                            Text(
                                stringResource(R.string.playlist_import_missing_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("playlist_import_ok")) {
                Text(stringResource(R.string.dialog_close))
            }
        },
    )
}

@Composable
private fun outcomeText(outcome: PlaylistImportOutcome): String = when (outcome.status) {
    PlaylistImportOutcome.Status.CREATED ->
        stringResource(R.string.playlist_import_line, outcome.playlistName.orEmpty(), outcome.found, outcome.total)
    PlaylistImportOutcome.Status.NO_MATCH -> stringResource(R.string.playlist_import_line_no_match, outcome.fileName)
    PlaylistImportOutcome.Status.UNREADABLE -> stringResource(R.string.playlist_import_line_unreadable, outcome.fileName)
    PlaylistImportOutcome.Status.NOT_PLAYLIST -> stringResource(R.string.playlist_import_line_not_playlist, outcome.fileName)
}
