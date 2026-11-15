package io.github.x1wer.szabadzene.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.BuildConfig
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import kotlinx.coroutines.launch

private enum class SkipDialog { BACK, FORWARD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val container = LocalAppContainer.current ?: return
    val skip by container.settings.skipSettings.collectAsStateWithLifecycle(SkipSettings())
    val scope = rememberCoroutineScope()
    var dialog by rememberSaveable { mutableStateOf<SkipDialog?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            SectionHeader(stringResource(R.string.settings_section_playback))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_skip_back)) },
                supportingContent = { Text(stringResource(R.string.settings_seconds_value, skip.backSeconds)) },
                modifier = Modifier
                    .testTag("setting_skip_back")
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button) { dialog = SkipDialog.BACK },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_skip_forward)) },
                supportingContent = { Text(stringResource(R.string.settings_seconds_value, skip.forwardSeconds)) },
                modifier = Modifier
                    .testTag("setting_skip_forward")
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button) { dialog = SkipDialog.FORWARD },
            )
            SectionHeader(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
                },
            )
        }
    }

    when (dialog) {
        SkipDialog.BACK -> SecondsDialog(
            title = stringResource(R.string.settings_skip_back),
            selected = skip.backSeconds,
            onSelect = { value ->
                dialog = null
                scope.launch { container.settings.setSkipBackSeconds(value) }
            },
            onDismiss = { dialog = null },
        )
        SkipDialog.FORWARD -> SecondsDialog(
            title = stringResource(R.string.settings_skip_forward),
            selected = skip.forwardSeconds,
            onSelect = { value ->
                dialog = null
                scope.launch { container.settings.setSkipForwardSeconds(value) }
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SecondsDialog(
    title: String,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                SkipSettings.ALLOWED_SECONDS.forEach { seconds ->
                    Row(
                        Modifier
                            .testTag("seconds_option_$seconds")
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = seconds == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(seconds) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = seconds == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.settings_seconds_value, seconds),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}
