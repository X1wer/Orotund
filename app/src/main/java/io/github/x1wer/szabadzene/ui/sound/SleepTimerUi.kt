package io.github.x1wer.szabadzene.ui.sound

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.playback.SleepTimer
import io.github.x1wer.szabadzene.playback.SleepTimerState
import kotlinx.coroutines.delay

/** A hátralévő idő másodpercenként frissülő szövege; null, ha nincs időzített leállás. */
@Composable
fun rememberSleepRemaining(state: SleepTimerState): String? {
    val text by produceState<String?>(initialValue = remainingText(state), state) {
        while (true) {
            value = remainingText(state)
            if (state !is SleepTimerState.Timed) break
            delay(500)
        }
    }
    return text
}

private fun remainingText(state: SleepTimerState): String? = (state as? SleepTimerState.Timed)
    ?.let { SleepTimer.formatRemaining(it.remainingMs(SystemClock.elapsedRealtime())) }

/** Az időzítő állapota egy rövid szövegben (beállítások, akadálymentes leírás). */
@Composable
fun sleepTimerSummary(state: SleepTimerState): String {
    val remaining = rememberSleepRemaining(state)
    return when (state) {
        SleepTimerState.Off -> stringResource(R.string.sleep_off)
        SleepTimerState.EndOfTrack -> stringResource(R.string.sleep_end_of_track_active)
        is SleepTimerState.Timed -> stringResource(R.string.sleep_remaining, remaining.orEmpty())
    }
}

/**
 * Az elalvásidőzítő beállítása: időtartam, vagy az aktuális szám vége; ha fut, a hátralévő idő
 * és a kikapcsolás is itt látszik.
 */
@Composable
fun SleepTimerDialog(
    state: SleepTimerState,
    onPickMinutes: (Int) -> Unit,
    onPickEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("sleep_timer_dialog"),
        title = { Text(stringResource(R.string.sleep_timer_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state != SleepTimerState.Off) {
                    Text(
                        sleepTimerSummary(state),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .testTag("sleep_timer_remaining")
                            .padding(bottom = 8.dp),
                    )
                    DialogRow(stringResource(R.string.sleep_cancel), "sleep_cancel", onCancelTimer)
                    HorizontalDivider()
                }
                SleepTimer.PRESET_MINUTES.forEach { minutes ->
                    DialogRow(pluralStringResource(R.plurals.sleep_minutes, minutes, minutes), "sleep_$minutes") { onPickMinutes(minutes) }
                }
                DialogRow(stringResource(R.string.sleep_end_of_track), "sleep_end_of_track", onPickEndOfTrack)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}

@Composable
private fun DialogRow(text: String, tag: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(vertical = 16.dp),
    )
}
