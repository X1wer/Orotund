package io.github.x1wer.szabadzene.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.LibraryLayout
import io.github.x1wer.szabadzene.data.settings.LibrarySection

/** A zenetár egy belső fülének felirata. */
fun sectionLabel(section: LibrarySection): Int = when (section) {
    LibrarySection.TRACKS -> R.string.library_tab_tracks
    LibrarySection.ALBUMS -> R.string.library_tab_albums
    LibrarySection.ARTISTS -> R.string.library_tab_artists
    LibrarySection.GENRES -> R.string.library_tab_genres
}

/** A látható fülek felsorolása a beállítások sorában („Számok, Albumok”). */
@Composable
fun sectionsSummary(sections: List<LibrarySection>): String =
    sections.map { stringResource(sectionLabel(it)) }.joinToString(", ")

/**
 * Mely fülek jelenjenek meg a zenetárban. Legalább egynek maradnia kell; ha csak egy marad, a
 * fülsáv eltűnik. A zenetár fejlécéből és a Beállításokból is megnyílik.
 */
@Composable
fun LibraryTabsDialog(
    current: List<LibrarySection>,
    onSave: (List<LibrarySection>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf(current.map { it.name }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("library_tabs_dialog"),
        title = { Text(stringResource(R.string.settings_library_tabs)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.library_tabs_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LibrarySection.entries.forEach { section ->
                    val checked = section.name in chosen
                    // Az utolsó bejelölt fület nem lehet kikapcsolni.
                    val enabled = !checked || chosen.size > 1
                    Row(
                        Modifier
                            .testTag("library_tab_option_${section.name.lowercase()}")
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(
                                value = checked,
                                enabled = enabled,
                                role = Role.Checkbox,
                                onValueChange = { on ->
                                    chosen = if (on) chosen + section.name else chosen - section.name
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(sectionLabel(section)), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(LibraryLayout.sanitize(LibrarySection.entries.filter { it.name in chosen })) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.testTag("library_tabs_save"),
            ) { Text(stringResource(R.string.dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}
