package io.github.x1wer.szabadzene.ui.playlists

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.playlists.PlaylistExportReport
import io.github.x1wer.szabadzene.data.playlists.PlaylistExportSource
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.components.Icon
import io.github.x1wer.szabadzene.ui.library.LibraryViewModel
import io.github.x1wer.szabadzene.ui.trackCountText
import kotlinx.coroutines.launch

/** A listafájl típusa a „Mentés másként” ablakhoz (ha a rendszer nem ismeri az m3u8-at, általános bináris). */
private val PLAYLIST_MIME: String =
    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension("m3u8") ?: "application/octet-stream"

/** Egy választható lista az export ablakban. */
private data class ExportChoice(val id: Long, val name: String, val count: Int)

/**
 * „Listák exportálása” gomb a Listák fejlécében: a kijelölt listák (a saját listák, a Kedvencek és az
 * automatikus listák közül) .m3u8 fájlba kerülnek a rendszer fájlválasztójával kiválasztott mappába.
 */
@Composable
fun PlaylistExportButton(viewModel: LibraryViewModel) {
    val container = LocalAppContainer.current ?: return
    val scope = rememberCoroutineScope()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val favoritesTitle = stringResource(R.string.favorites_title)
    val mostPlayedTitle = stringResource(R.string.most_played_title)
    val recentTitle = stringResource(R.string.recently_played_title)

    var choosing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var overwrite by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<PlaylistExportReport?>(null) }
    var failed by remember { mutableStateOf(false) }

    val choices = listOf(
        ExportChoice(FAVORITES_ID, favoritesTitle, favorites.size),
        ExportChoice(MOST_PLAYED_ID, mostPlayedTitle, mostPlayed.size),
        ExportChoice(RECENTLY_PLAYED_ID, recentTitle, recentlyPlayed.size),
    ) + playlists.map { ExportChoice(it.id, it.name, it.entryCount) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val chosen = choices.filter { it.id in selected }
        running = true
        scope.launch {
            val result = runCatching {
                val sources = chosen.map { choice ->
                    val tracks = when (choice.id) {
                        FAVORITES_ID -> favorites
                        MOST_PLAYED_ID -> mostPlayed.map { it.track }
                        RECENTLY_PLAYED_ID -> recentlyPlayed.map { it.track }
                        else -> container.playlists.playlistItemsOnce(choice.id).map { it.track }
                    }
                    PlaylistExportSource(choice.name, tracks)
                }
                container.playlistExporter.export(uri, sources, overwrite)
            }
            running = false
            report = result.getOrNull()
            failed = result.isFailure
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var singleResult by remember { mutableStateOf<String?>(null) }
    val favoritesFileName = stringResource(R.string.export_favorites_file_name)
    val countsFileName = stringResource(R.string.export_counts_file_name)
    val resources = androidx.compose.ui.platform.LocalResources.current
    fun runSingle(block: suspend () -> String) {
        running = true
        scope.launch {
            val result = runCatching { block() }
            running = false
            singleResult = result.getOrNull()
            failed = result.isFailure
        }
    }
    val saveFavorites = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PLAYLIST_MIME),
    ) { uri ->
        if (uri != null) {
            runSingle {
                val count = container.playlistExporter.exportSingle(uri, favoritesTitle, favorites)
                resources.getQuantityString(R.plurals.export_favorites_done, count, count)
            }
        }
    }
    val saveCounts = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runSingle {
                val count = container.playCountImporter.export(uri)
                resources.getQuantityString(R.plurals.export_counts_done, count, count)
            }
        }
    }

    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("playlist_export")) {
            Icon(Icons.Rounded.Output, contentDescription = stringResource(R.string.playlist_export))
        }
        androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.export_lists_item)) },
                onClick = {
                    menuOpen = false
                    // Alapból a saját listák vannak kijelölve.
                    selected = playlists.map { it.id }
                    choosing = true
                },
                modifier = Modifier.testTag("export_lists").heightIn(min = 56.dp),
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.export_favorites)) },
                onClick = {
                    menuOpen = false
                    try {
                        saveFavorites.launch(favoritesFileName)
                    } catch (_: ActivityNotFoundException) {
                        failed = true
                    }
                },
                modifier = Modifier.testTag("export_favorites").heightIn(min = 56.dp),
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.export_counts)) },
                onClick = {
                    menuOpen = false
                    try {
                        saveCounts.launch(countsFileName)
                    } catch (_: ActivityNotFoundException) {
                        failed = true
                    }
                },
                modifier = Modifier.testTag("export_counts").heightIn(min = 56.dp),
            )
        }
    }

    singleResult?.let { message ->
        AlertDialog(
            onDismissRequest = { singleResult = null },
            modifier = Modifier.testTag("export_single_report"),
            title = { Text(stringResource(R.string.playlist_export_done)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { singleResult = null }) { Text(stringResource(R.string.dialog_close)) } },
        )
    }

    if (choosing) {
        ExportChooserDialog(
            choices = choices,
            selected = selected,
            overwrite = overwrite,
            onSelectedChange = { selected = it },
            onOverwriteChange = { overwrite = it },
            onConfirm = {
                choosing = false
                try {
                    pickFolder.launch(null)
                } catch (_: ActivityNotFoundException) {
                    failed = true
                }
            },
            onDismiss = { choosing = false },
        )
    }

    if (running) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            confirmButton = {},
            title = { Text(stringResource(R.string.playlist_export)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.playlist_export_running))
                }
            },
        )
    }

    val done = report
    if (done != null || (failed && singleResult == null)) {
        ExportReportDialog(done, failed) {
            report = null
            failed = false
        }
    }
}

@Composable
private fun ExportChooserDialog(
    choices: List<ExportChoice>,
    selected: List<Long>,
    overwrite: Boolean,
    onSelectedChange: (List<Long>) -> Unit,
    onOverwriteChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val chosenCount = choices.count { it.id in selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("playlist_export_dialog"),
        title = { Text(stringResource(R.string.playlist_export)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.playlist_export_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val allState = when (chosenCount) {
                    0 -> ToggleableState.Off
                    choices.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
                Row(
                    Modifier
                        .testTag("export_all")
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .triStateToggleable(
                            state = allState,
                            role = Role.Checkbox,
                            onClick = {
                                onSelectedChange(if (allState == ToggleableState.On) emptyList() else choices.map { it.id })
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TriStateCheckbox(state = allState, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.action_select_all), style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
                choices.forEach { choice ->
                    val checked = choice.id in selected
                    Row(
                        Modifier
                            .testTag("export_option_${choice.id}")
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { on -> onSelectedChange(if (on) selected + choice.id else selected - choice.id) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                choice.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                trackCountText(choice.count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier
                        .testTag("export_overwrite")
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = overwrite, role = Role.Checkbox, onValueChange = onOverwriteChange),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = overwrite, onCheckedChange = null)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.playlist_export_overwrite), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = chosenCount > 0, modifier = Modifier.testTag("export_choose_folder")) {
                Text(stringResource(R.string.playlist_export_choose_folder))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@Composable
private fun ExportReportDialog(report: PlaylistExportReport?, failed: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("playlist_export_report"),
        icon = { Icon(Icons.Rounded.Output, contentDescription = null) },
        title = { Text(stringResource(R.string.playlist_export_done)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (failed || report == null) {
                    Text(stringResource(R.string.playlist_export_failed))
                } else {
                    report.folder?.let {
                        Text(
                            stringResource(R.string.playlist_export_folder, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    report.outcomes.forEach { outcome ->
                        val line = if (outcome.fileName != null) {
                            pluralStringResource(R.plurals.playlist_export_line, outcome.trackCount, outcome.fileName, outcome.trackCount)
                        } else {
                            stringResource(R.string.playlist_export_line_failed, outcome.playlistName)
                        }
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("playlist_export_ok")) {
                Text(stringResource(R.string.dialog_close))
            }
        },
    )
}
