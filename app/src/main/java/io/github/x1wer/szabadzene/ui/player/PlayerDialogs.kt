package io.github.x1wer.szabadzene.ui.player

import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import io.github.x1wer.szabadzene.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.PlaybackPrefs
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.RepeatSetting
import kotlin.math.roundToInt

/** Ismétlés és leállás: a négy mód egy választólistában (ikonnal, leírással). */
@Composable
fun RepeatModeDialog(selected: RepeatSetting, onSelect: (RepeatSetting) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("repeat_dialog"),
        title = { Text(stringResource(R.string.repeat_dialog_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                RepeatSetting.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .selectable(selected = mode == selected, role = Role.RadioButton) { onSelect(mode) }
                            .testTag("repeat_${mode.name.lowercase()}")
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(repeatIcon(mode), contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            stringResource(repeatDescription(mode)),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        RadioButton(selected = mode == selected, onClick = null)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}

private fun repeatDescription(mode: RepeatSetting): Int = when (mode) {
    RepeatSetting.STOP_AFTER_CURRENT -> R.string.repeat_desc_stop
    RepeatSetting.ONE -> R.string.repeat_desc_one
    RepeatSetting.OFF -> R.string.repeat_desc_off
    RepeatSetting.ALL -> R.string.repeat_desc_all
}

/** Lejátszási sebesség 0,5×–2,0× között, 0,05-ös lépéssel; a változás azonnal hallható. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedDialog(speed: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(speed) }
    fun set(v: Float) {
        value = PlaybackPrefs.sanitizeSpeed(v)
        onChange(value)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("speed_dialog"),
        title = { Text(stringResource(R.string.speed_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { set(value - 0.05f) }, modifier = Modifier.testTag("speed_minus")) {
                        Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.cd_speed_down))
                    }
                    Text(
                        speedText(value),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("speed_value"),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { set(value + 0.05f) }, modifier = Modifier.testTag("speed_plus")) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_speed_up))
                    }
                }
                Slider(
                    value = value,
                    onValueChange = { value = PlaybackPrefs.sanitizeSpeed(it) },
                    onValueChangeFinished = { onChange(value) },
                    valueRange = PlaybackPrefs.MIN_SPEED..PlaybackPrefs.MAX_SPEED,
                    steps = 29,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { preset ->
                        FilterChip(
                            selected = value == preset,
                            onClick = { set(preset) },
                            label = { Text(speedText(preset)) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.speed_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
        dismissButton = {
            TextButton(onClick = { set(1f) }, modifier = Modifier.testTag("speed_reset")) {
                Text(stringResource(R.string.speed_reset))
            }
        },
    )
}

/** A zenei hangerő (a rendszer médiahangereje) csúszkával. */
@Composable
fun VolumeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
    var volume by remember { mutableIntStateOf(audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) }
    val fixed = remember { audio?.isVolumeFixed == true }
    fun set(v: Int) {
        volume = v.coerceIn(0, max)
        runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("volume_dialog"),
        title = { Text(stringResource(R.string.volume_title)) },
        text = {
            if (fixed) {
                Text(stringResource(R.string.volume_fixed))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { set(volume - 1) }) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = stringResource(R.string.cd_volume_down))
                    }
                    Slider(
                        value = volume.toFloat(),
                        onValueChange = { set(it.roundToInt()) },
                        valueRange = 0f..max.toFloat(),
                        steps = (max - 1).coerceAtLeast(0),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { set(volume + 1) }) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = stringResource(R.string.cd_volume_up))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}

/**
 * Az ugrógombok beállítása: visszaugrás és előreugrás mértéke (ez látszik a minilejátszóban,
 * az értesítésben és a fejhallgatón is), valamint a teljes lejátszó további gombjai (legfeljebb kettő).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkipSettingsDialog(
    skip: SkipSettings,
    onBack: (Int) -> Unit,
    onForward: (Int) -> Unit,
    onExtra: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("skip_settings_dialog"),
        title = { Text(stringResource(R.string.skip_settings_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.settings_skip_back), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkipSettings.ALLOWED_SECONDS.forEach { s ->
                        FilterChip(
                            selected = skip.backSeconds == s,
                            onClick = { onBack(s) },
                            label = { Text(stringResource(R.string.settings_seconds_value, s)) },
                            modifier = Modifier.testTag("skip_back_choice_$s"),
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.settings_skip_forward), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkipSettings.ALLOWED_SECONDS.forEach { s ->
                        FilterChip(
                            selected = skip.forwardSeconds == s,
                            onClick = { onForward(s) },
                            label = { Text(stringResource(R.string.settings_seconds_value, s)) },
                            modifier = Modifier.testTag("skip_forward_choice_$s"),
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.skip_extra_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.skip_extra_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkipSettings.ALLOWED_SECONDS.forEach { s ->
                        val on = s in skip.extraSeconds
                        FilterChip(
                            selected = on,
                            onClick = {
                                val next = if (on) skip.extraSeconds - s else skip.extraSeconds + s
                                // Ha már kettő van kiválasztva, a korábbiak közül a kisebbik kiesik.
                                onExtra(if (next.size > SkipSettings.MAX_EXTRA) next.drop(1) else next)
                            },
                            label = { Text(stringResource(R.string.settings_seconds_value, s)) },
                            modifier = Modifier.testTag("skip_extra_choice_$s"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}

/** A lejátszásszám kézi módosítása (0 vagy nagyobb egész). */
@Composable
fun PlayCountDialog(title: String, current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.toString()) }
    val value = text.trim().toIntOrNull()?.takeIf { it in 0..MAX_PLAY_COUNT }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("play_count_dialog"),
        title = { Text(stringResource(R.string.play_count_title)) },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { text = ((value ?: 0) - 1).coerceAtLeast(0).toString() },
                        modifier = Modifier.testTag("play_count_minus"),
                    ) { Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.cd_play_count_down)) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
                        singleLine = true,
                        isError = value == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { value?.let(onSave) }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("play_count_field"),
                    )
                    IconButton(
                        onClick = { text = ((value ?: 0) + 1).coerceAtMost(MAX_PLAY_COUNT).toString() },
                        modifier = Modifier.testTag("play_count_plus"),
                    ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_play_count_up)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onSave) },
                enabled = value != null,
                modifier = Modifier.testTag("play_count_save"),
            ) { Text(stringResource(R.string.dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

private const val MAX_PLAY_COUNT = 999_999
