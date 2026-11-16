package io.github.x1wer.szabadzene.ui.edit

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import io.github.x1wer.szabadzene.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.edit.TrackFileEditor
import io.github.x1wer.szabadzene.data.edit.TrimMode
import io.github.x1wer.szabadzene.data.library.Track
import kotlinx.coroutines.delay
import java.util.Locale

/** Idő tizedmásodperccel: „1:05,3”. */
fun formatTrimTime(ms: Long, decimal: Char): String {
    val tenths = (ms.coerceAtLeast(0) + 50) / 100
    val minutes = tenths / 600
    val seconds = (tenths / 10) % 60
    return String.format(Locale.ROOT, "%d:%02d%c%d", minutes, seconds, decimal, tenths % 10)
}

/**
 * Kivágás: kezdő- és végpont (csúszka és finomhangoló gombok), előhallgatás, új fájlnév.
 * Az előhallgatás saját, ideiglenes lejátszót használ, a várólistát nem érinti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimDialog(
    track: Track,
    mode: TrimMode,
    saving: Boolean,
    error: String?,
    onPausePlayer: () -> Unit,
    onSave: (startMs: Long, endMs: Long, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val decimal = java.text.DecimalFormatSymbols.getInstance(LocalConfiguration.current.locales[0]).decimalSeparator
    val duration = track.durationMs.coerceAtLeast(1_000L).toFloat()
    var start by remember { mutableFloatStateOf(0f) }
    var end by remember { mutableFloatStateOf(duration) }
    val suffix = stringResource(R.string.trim_default_suffix)
    var name by remember { mutableStateOf("${track.displayName.substringBeforeLast('.')} ($suffix)") }
    var previewing by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableStateOf<Long?>(null) }
    var previewError by remember { mutableStateOf(false) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPreview() {
        player.value?.let { runCatching { it.stop() }; it.release() }
        player.value = null
        previewing = false
        previewPosition = null
    }

    DisposableEffect(Unit) { onDispose { stopPreview() } }

    LaunchedEffect(previewing) {
        if (!previewing) return@LaunchedEffect
        val mp = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(context, track.contentUri)
                prepare()
                seekTo(start.toInt())
                start()
            }
        } catch (e: Exception) {
            previewError = true
            previewing = false
            return@LaunchedEffect
        }
        player.value = mp
        while (previewing) {
            val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(end.toLong())
            previewPosition = pos
            if (pos >= end.toLong() || !mp.isPlaying) {
                stopPreview()
                break
            }
            delay(50)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.testTag("trim_dialog"),
        title = { Text(stringResource(R.string.trim_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                val rangeDescription = stringResource(R.string.trim_range)
                RangeSlider(
                    value = start..end,
                    onValueChange = { range ->
                        if (previewing) stopPreview()
                        start = range.start
                        end = range.endInclusive.coerceAtLeast(range.start + MIN_LENGTH_MS)
                            .coerceAtMost(duration)
                    },
                    valueRange = 0f..duration,
                    enabled = !saving,
                    modifier = Modifier
                        .testTag("trim_range")
                        .semantics { contentDescription = rangeDescription },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.trim_start, formatTrimTime(start.toLong(), decimal)), modifier = Modifier.weight(1f))
                    Nudge("−0${decimal}5", R.string.cd_trim_start_earlier, !saving) { start = (start - STEP).coerceAtLeast(0f) }
                    Nudge("+0${decimal}5", R.string.cd_trim_start_later, !saving) { start = (start + STEP).coerceAtMost(end - MIN_LENGTH_MS) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.trim_end, formatTrimTime(end.toLong(), decimal)), modifier = Modifier.weight(1f))
                    Nudge("−0${decimal}5", R.string.cd_trim_end_earlier, !saving) { end = (end - STEP).coerceAtLeast(start + MIN_LENGTH_MS) }
                    Nudge("+0${decimal}5", R.string.cd_trim_end_later, !saving) { end = (end + STEP).coerceAtMost(duration) }
                }
                Text(
                    stringResource(R.string.trim_length, formatTrimTime((end - start).toLong(), decimal)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        if (previewing) {
                            stopPreview()
                        } else {
                            previewError = false
                            onPausePlayer()
                            previewing = true
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.testTag("trim_preview"),
                ) {
                    Icon(if (previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(if (previewing) R.string.trim_stop_preview else R.string.trim_preview))
                    previewPosition?.let { Text("  " + formatTrimTime(it, decimal)) }
                }
                if (previewError) Text(stringResource(R.string.error_preview), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text(stringResource(R.string.trim_name)) },
                    singleLine = true,
                    enabled = !saving,
                    suffix = { Text(".${mode.extension}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trim_name"),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (mode.lossless) {
                        stringResource(R.string.trim_output_lossless, mode.extension.uppercase(Locale.ROOT))
                    } else {
                        stringResource(R.string.trim_output_reencode)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("trim_mode_note"),
                )
                Text(
                    stringResource(R.string.trim_location, TrackFileEditor.OUTPUT_RELATIVE_PATH),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (saving) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.trim_saving))
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp).testTag("trim_error"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    stopPreview()
                    onSave(start.toLong(), end.toLong(), TrackFileEditor.sanitizeFileName(name))
                },
                enabled = !saving && TrackFileEditor.sanitizeFileName(name).isNotBlank() && end - start >= MIN_LENGTH_MS,
                modifier = Modifier.testTag("trim_save"),
            ) { Text(stringResource(R.string.trim_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@Composable
private fun Nudge(label: String, description: Int, enabled: Boolean, onClick: () -> Unit) {
    val cd = stringResource(description)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(width = 64.dp, height = 48.dp)
            .semantics { contentDescription = cd },
    ) { Text(label) }
}

private const val STEP = 500f
private const val MIN_LENGTH_MS = 1_000f
