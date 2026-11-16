package io.github.x1wer.szabadzene.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.x1wer.szabadzene.data.stats.PlayCountImportReport
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * „Lejátszásszámok importálása”: a [request] növelésekor megnyílik a rendszer fájlválasztója, a kiválasztott
 * CSV-t beolvassa, és összesítő ablakot mutat. Csak növeli a számlálókat (lásd `PlayCountImporter`).
 */
@Composable
fun PlayCountImportHost(request: Int) {
    val container = LocalAppContainer.current ?: return
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<PlayCountImportReport?>(null) }
    var failed by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        running = true
        scope.launch {
            val result = runCatching { container.playCountImporter.import(uri) }
            running = false
            report = result.getOrNull()
            failed = result.isFailure
        }
    }
    LaunchedEffect(request) {
        if (request > 0) {
            try {
                pick.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*"))
            } catch (_: ActivityNotFoundException) {
                failed = true
            }
        }
    }

    if (running) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            confirmButton = {},
            title = { Text(stringResource(R.string.settings_import_counts)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.import_counts_running))
                }
            },
        )
    }
    val done = report
    if (done != null || failed) {
        val dismiss = {
            report = null
            failed = false
        }
        AlertDialog(
            onDismissRequest = dismiss,
            modifier = Modifier.testTag("import_counts_report"),
            title = { Text(stringResource(R.string.import_counts_done)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (failed || done == null) {
                        Text(stringResource(R.string.import_counts_failed))
                    } else if (done.rows == 0) {
                        Text(stringResource(R.string.import_counts_empty))
                    } else {
                        Text(stringResource(R.string.import_counts_rows, done.rows))
                        Text(stringResource(R.string.import_counts_updated, done.updated))
                        Text(stringResource(R.string.import_counts_unchanged, done.unchanged))
                        Text(stringResource(R.string.import_counts_not_matched, done.missing, done.ambiguous))
                        if (done.missingSample.isNotEmpty()) {
                            Text(
                                stringResource(R.string.import_counts_sample, done.missingSample.joinToString("\n")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = dismiss, modifier = Modifier.testTag("import_counts_ok")) {
                    Text(stringResource(R.string.dialog_close))
                }
            },
        )
    }
}
