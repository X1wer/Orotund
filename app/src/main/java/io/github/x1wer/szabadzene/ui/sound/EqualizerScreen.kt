package io.github.x1wer.szabadzene.ui.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.EqSettings
import io.github.x1wer.szabadzene.playback.EqPreset
import io.github.x1wer.szabadzene.playback.EqualizerLogic
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import kotlinx.coroutines.launch
import java.text.DecimalFormatSymbols
import kotlin.math.roundToInt

fun eqPresetLabel(preset: EqPreset): Int = when (preset) {
    EqPreset.FLAT -> R.string.eq_preset_flat
    EqPreset.BASS -> R.string.eq_preset_bass
    EqPreset.TREBLE -> R.string.eq_preset_treble
    EqPreset.VOCAL -> R.string.eq_preset_vocal
    EqPreset.ROCK -> R.string.eq_preset_rock
    EqPreset.POP -> R.string.eq_preset_pop
    EqPreset.JAZZ -> R.string.eq_preset_jazz
    EqPreset.CLASSICAL -> R.string.eq_preset_classical
    EqPreset.CUSTOM -> R.string.eq_preset_custom
}

/** A hangszínszabályzó rövid állapota a beállítások listájához. */
@Composable
fun equalizerSummary(settings: EqSettings): String = if (settings.enabled) {
    stringResource(R.string.eq_state_on, stringResource(eqPresetLabel(EqPreset.of(settings.preset))))
} else {
    stringResource(R.string.eq_state_off)
}

/**
 * Hangszínszabályzó: be/ki, előbeállítások, egyéni sávok és mélyhang-erősítés. Csak az jelenik meg,
 * amit a lejátszószolgáltatás ténylegesen létre tudott hozni ezen a készüléken.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current ?: return
    val settingsRepo = container.settings
    val capabilities by container.audioEffects.capabilities.collectAsStateWithLifecycle()
    val settings by settingsRepo.equalizer.collectAsStateWithLifecycle(EqSettings())
    val scope = rememberCoroutineScope()
    // Húzás közben a csúszka helyi értéket mutat; elengedéskor mentődik.
    val dragging = remember { mutableStateMapOf<Int, Float>() }
    val locale = LocalConfiguration.current.locales[0]
    val decimal = DecimalFormatSymbols.getInstance(locale).decimalSeparator

    Surface(Modifier.fillMaxSize().testTag("equalizer_screen")) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.equalizer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp),
            ) {
                val caps = capabilities
                if (caps == null) {
                    Text(stringResource(R.string.eq_waiting), modifier = Modifier.padding(vertical = 16.dp))
                    return@Column
                }
                if (!caps.equalizer && !caps.bassBoost) {
                    Text(
                        stringResource(R.string.eq_not_supported),
                        modifier = Modifier
                            .testTag("eq_not_supported")
                            .padding(vertical = 16.dp),
                    )
                    return@Column
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.eq_enabled)) },
                    supportingContent = { Text(stringResource(R.string.eq_enabled_desc)) },
                    trailingContent = {
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { scope.launch { settingsRepo.setEqualizerEnabled(it) } },
                            modifier = Modifier.testTag("eq_switch"),
                        )
                    },
                )
                if (!settings.enabled) {
                    Text(
                        stringResource(R.string.eq_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                if (caps.equalizer) {
                    val preset = EqPreset.of(settings.preset)
                    val levels = EqualizerLogic.levelsFor(preset, settings.customLevels, caps.bands)
                    Text(
                        stringResource(R.string.eq_preset),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EqPreset.entries.forEach { option ->
                            FilterChip(
                                selected = option == preset,
                                onClick = {
                                    scope.launch {
                                        if (option == EqPreset.CUSTOM) {
                                            // Az egyéni a jelenlegi görbéből indul, ha még nincs saját.
                                            val start = settings.customLevels.ifEmpty {
                                                caps.bands.mapIndexed { i, b -> b.centerHz to levels[i] }.toMap()
                                            }
                                            settingsRepo.setEqualizerCustom(start)
                                        } else {
                                            settingsRepo.setEqualizerPreset(option.name)
                                        }
                                    }
                                },
                                label = { Text(stringResource(eqPresetLabel(option))) },
                                modifier = Modifier.testTag("eq_preset_${option.name}"),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.eq_bands),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    caps.bands.forEachIndexed { index, band ->
                        val shownMb = dragging[index] ?: levels[index].toFloat()
                        val freq = EqualizerLogic.frequencyLabel(band.centerHz, decimal)
                        val db = String.format(locale, "%+.1f", shownMb / 100f)
                        val bandDescription = stringResource(R.string.cd_eq_band, freq)
                        val dbText = stringResource(R.string.eq_db_value, db)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(freq, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                            Slider(
                                value = shownMb,
                                onValueChange = { dragging[index] = it },
                                onValueChangeFinished = {
                                    val value = dragging.remove(index)?.roundToInt() ?: return@Slider
                                    val custom = EqualizerLogic.withBandChanged(levels, caps.bands, index, value)
                                    scope.launch { settingsRepo.setEqualizerCustom(custom) }
                                },
                                valueRange = band.minMb.toFloat()..band.maxMb.toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("eq_band_$index")
                                    .semantics {
                                        contentDescription = bandDescription
                                        stateDescription = dbText
                                    },
                            )
                            Text(
                                dbText,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(64.dp),
                            )
                        }
                    }
                    TextButton(
                        onClick = { scope.launch { settingsRepo.setEqualizerPreset(EqPreset.FLAT.name) } },
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text(stringResource(R.string.eq_reset)) }
                }

                Text(
                    stringResource(R.string.eq_bass_boost),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                if (caps.bassBoost) {
                    val shown = dragging[BASS_KEY] ?: settings.bassBoost.toFloat()
                    val percent = stringResource(R.string.eq_bass_boost_value, (shown / 10).roundToInt())
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = shown,
                            onValueChange = { dragging[BASS_KEY] = it },
                            onValueChangeFinished = {
                                val value = dragging.remove(BASS_KEY)?.roundToInt() ?: return@Slider
                                scope.launch { settingsRepo.setBassBoost(value) }
                            },
                            valueRange = 0f..1000f,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("eq_bass_boost")
                                .semantics { stateDescription = percent },
                        )
                        Text(percent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                    }
                } else {
                    Text(
                        stringResource(R.string.eq_bass_not_supported),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val BASS_KEY = -1
