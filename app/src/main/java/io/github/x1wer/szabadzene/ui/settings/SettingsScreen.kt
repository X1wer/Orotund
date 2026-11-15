package io.github.x1wer.szabadzene.ui.settings

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.x1wer.szabadzene.BuildConfig
import io.github.x1wer.szabadzene.R
import io.github.x1wer.szabadzene.data.settings.AccentColor
import io.github.x1wer.szabadzene.data.settings.AppearanceSettings
import io.github.x1wer.szabadzene.data.settings.EqSettings
import io.github.x1wer.szabadzene.data.settings.HeadsetSettings
import io.github.x1wer.szabadzene.data.settings.LibraryFilter
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.data.settings.ThemeMode
import io.github.x1wer.szabadzene.ui.LocalAppContainer
import io.github.x1wer.szabadzene.ui.LocalMenuState
import io.github.x1wer.szabadzene.ui.sound.equalizerSummary
import io.github.x1wer.szabadzene.ui.sound.sleepTimerSummary
import io.github.x1wer.szabadzene.ui.components.ChoiceDialog
import kotlinx.coroutines.launch

private enum class SettingsDialog { THEME, ACCENT, LANGUAGE, SKIP_BACK, SKIP_FORWARD, MIN_DURATION, EXCLUDED }

/** Alkalmazásszintű nyelv: üres = rendszer. A címke „hu”, nem „hu-HU” (lásd ALLAPOT.md). */
enum class AppLanguage(val tag: String, val label: Int) {
    SYSTEM("", R.string.language_system),
    HUNGARIAN("hu", R.string.language_hungarian),
    ENGLISH("en", R.string.language_english),
}

fun currentAppLanguage(): AppLanguage {
    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return when {
        tags.isBlank() -> AppLanguage.SYSTEM
        tags.startsWith("hu") -> AppLanguage.HUNGARIAN
        tags.startsWith("en") -> AppLanguage.ENGLISH
        else -> AppLanguage.SYSTEM
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    snackbar: SnackbarHostState,
    onOpenHelp: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
) {
    val container = LocalAppContainer.current ?: return
    val menu = LocalMenuState.current
    val eq by container.settings.equalizer.collectAsStateWithLifecycle(EqSettings())
    val sleepTimer by container.player.sleepTimer.collectAsStateWithLifecycle()
    val settings = container.settings
    val skip by settings.skipSettings.collectAsStateWithLifecycle(SkipSettings())
    val appearance by settings.appearance.collectAsStateWithLifecycle(AppearanceSettings())
    val headset by settings.headset.collectAsStateWithLifecycle(HeadsetSettings())
    val filter by settings.libraryFilter.collectAsStateWithLifecycle(LibraryFilter())
    val scanning by container.library.isScanning.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val language = currentAppLanguage()
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            SectionHeader(stringResource(R.string.settings_section_appearance))
            ClickableSetting(
                title = stringResource(R.string.settings_theme),
                value = stringResource(themeLabel(appearance.themeMode)),
                tag = "setting_theme",
            ) { dialog = SettingsDialog.THEME }
            if (dynamicAvailable) {
                SwitchSetting(
                    title = stringResource(R.string.settings_dynamic_color),
                    description = stringResource(R.string.settings_dynamic_color_desc),
                    checked = appearance.dynamicColor,
                    tag = "setting_dynamic_color",
                ) { enabled -> scope.launch { settings.setDynamicColor(enabled) } }
            }
            if (!dynamicAvailable || !appearance.dynamicColor) {
                ClickableSetting(
                    title = stringResource(R.string.settings_accent),
                    value = stringResource(accentLabel(appearance.accent)),
                    tag = "setting_accent",
                ) { dialog = SettingsDialog.ACCENT }
            }
            ClickableSetting(
                title = stringResource(R.string.settings_language),
                value = stringResource(language.label),
                tag = "setting_language",
            ) { dialog = SettingsDialog.LANGUAGE }

            SectionHeader(stringResource(R.string.settings_section_playback))
            ClickableSetting(
                title = stringResource(R.string.settings_skip_back),
                value = stringResource(R.string.settings_seconds_value, skip.backSeconds),
                tag = "setting_skip_back",
            ) { dialog = SettingsDialog.SKIP_BACK }
            ClickableSetting(
                title = stringResource(R.string.settings_skip_forward),
                value = stringResource(R.string.settings_seconds_value, skip.forwardSeconds),
                tag = "setting_skip_forward",
            ) { dialog = SettingsDialog.SKIP_FORWARD }

            SectionHeader(stringResource(R.string.settings_section_sound))
            ClickableSetting(
                title = stringResource(R.string.settings_equalizer),
                value = equalizerSummary(eq),
                tag = "setting_equalizer",
                onClick = onOpenEqualizer,
            )
            ClickableSetting(
                title = stringResource(R.string.settings_sleep_timer),
                value = sleepTimerSummary(sleepTimer),
                tag = "setting_sleep_timer",
            ) { menu.sleepTimerOpen = true }

            SectionHeader(stringResource(R.string.settings_section_headset))
            SwitchSetting(
                title = stringResource(R.string.settings_pause_on_disconnect),
                description = stringResource(R.string.settings_pause_on_disconnect_desc),
                checked = headset.pauseOnDisconnect,
                tag = "setting_pause_on_disconnect",
            ) { enabled -> scope.launch { settings.setPauseOnDisconnect(enabled) } }
            SwitchSetting(
                title = stringResource(R.string.settings_resume_on_reconnect),
                description = stringResource(R.string.settings_resume_on_reconnect_desc),
                checked = headset.resumeOnReconnect,
                tag = "setting_resume_on_reconnect",
            ) { enabled -> scope.launch { settings.setResumeOnReconnect(enabled) } }

            SectionHeader(stringResource(R.string.settings_section_library))
            ClickableSetting(
                title = stringResource(R.string.settings_rescan),
                value = stringResource(if (scanning) R.string.library_scanning else R.string.settings_rescan_desc),
                tag = "setting_rescan",
            ) {
                if (!scanning) {
                    container.library.refresh()
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.toast_rescan_started)) }
                }
            }
            ClickableSetting(
                title = stringResource(R.string.settings_min_duration),
                value = minDurationLabel(filter.minDurationSeconds),
                tag = "setting_min_duration",
            ) { dialog = SettingsDialog.MIN_DURATION }
            ClickableSetting(
                title = stringResource(R.string.settings_excluded_folders),
                value = if (filter.excludedFolders.isEmpty()) {
                    stringResource(R.string.settings_excluded_none)
                } else {
                    filter.excludedFolders.size.toString()
                },
                tag = "setting_excluded",
            ) { dialog = SettingsDialog.EXCLUDED }

            SectionHeader(stringResource(R.string.settings_section_about))
            ClickableSetting(
                title = stringResource(R.string.settings_help),
                value = stringResource(R.string.settings_help_desc),
                tag = "setting_help",
                onClick = onOpenHelp,
            )
            ClickableSetting(
                title = stringResource(R.string.settings_licenses),
                value = stringResource(R.string.settings_licenses_desc),
                tag = "setting_licenses",
                onClick = onOpenLicenses,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
            )
        }
    }

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.toList(),
            selected = appearance.themeMode,
            label = { stringResource(themeLabel(it)) },
            onSelect = {
                dialog = null
                scope.launch { settings.setThemeMode(it) }
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.ACCENT -> AccentDialog(
            selected = appearance.accent,
            onSelect = {
                dialog = null
                scope.launch { settings.setAccent(it) }
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = AppLanguage.entries.toList(),
            selected = language,
            label = { stringResource(it.label) },
            onSelect = {
                dialog = null
                // Az AppCompat menti a választást, és újraindítja az Activityt az új nyelvvel.
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it.tag))
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.SKIP_BACK -> ChoiceDialog(
            title = stringResource(R.string.settings_skip_back),
            options = SkipSettings.ALLOWED_SECONDS,
            selected = skip.backSeconds,
            label = { stringResource(R.string.settings_seconds_value, it) },
            onSelect = {
                dialog = null
                scope.launch { settings.setSkipBackSeconds(it) }
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.SKIP_FORWARD -> ChoiceDialog(
            title = stringResource(R.string.settings_skip_forward),
            options = SkipSettings.ALLOWED_SECONDS,
            selected = skip.forwardSeconds,
            label = { stringResource(R.string.settings_seconds_value, it) },
            onSelect = {
                dialog = null
                scope.launch { settings.setSkipForwardSeconds(it) }
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.MIN_DURATION -> ChoiceDialog(
            title = stringResource(R.string.settings_min_duration),
            options = LibraryFilter.ALLOWED_MIN_DURATIONS,
            selected = filter.minDurationSeconds,
            label = { minDurationLabel(it) },
            onSelect = {
                dialog = null
                scope.launch { settings.setMinDurationSeconds(it) }
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.EXCLUDED -> ExcludedFoldersDialog(
            folders = filter.excludedFolders.sorted(),
            onInclude = { path -> scope.launch { settings.includeFolder(path) } },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun accentLabel(accent: AccentColor): Int = when (accent) {
    AccentColor.GREEN -> R.string.accent_green
    AccentColor.BLUE -> R.string.accent_blue
    AccentColor.PURPLE -> R.string.accent_purple
    AccentColor.ORANGE -> R.string.accent_orange
    AccentColor.RED -> R.string.accent_red
}

@Composable
private fun minDurationLabel(seconds: Int): String =
    if (seconds <= 0) stringResource(R.string.min_duration_off) else stringResource(R.string.min_duration_value, seconds)

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
private fun ClickableSetting(title: String, value: String, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier
            .testTag(tag)
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier
            .testTag(tag)
            .heightIn(min = 64.dp)
            .clickable(role = Role.Switch) { onChange(!checked) },
    )
}

@Composable
private fun AccentDialog(selected: AccentColor, onSelect: (AccentColor) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = stringResource(R.string.settings_accent),
        options = AccentColor.entries.toList(),
        selected = selected,
        label = { stringResource(accentLabel(it)) },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
private fun ExcludedFoldersDialog(folders: List<String>, onInclude: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_excluded_folders)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (folders.isEmpty()) {
                    Text(stringResource(R.string.settings_excluded_empty))
                }
                folders.forEach { path ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            path,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onInclude(path) },
                            modifier = Modifier
                                .testTag("include_folder")
                                .size(56.dp),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_include_folder, path))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}

/** Rövid súgó a fontos funkciókról. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val sections = listOf(
        R.string.help_menu_title to R.string.help_menu_body,
        R.string.help_skip_title to R.string.help_skip_body,
        R.string.help_queue_title to R.string.help_queue_body,
        R.string.help_playlists_title to R.string.help_playlists_body,
        R.string.help_folders_title to R.string.help_folders_body,
        R.string.help_headset_title to R.string.help_headset_body,
        R.string.help_tools_title to R.string.help_tools_body,
        R.string.help_widget_title to R.string.help_widget_body,
    )
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_help)) },
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
                .padding(contentPadding)
                .padding(horizontal = 16.dp),
        ) {
            sections.forEach { (title, body) ->
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** A felhasznált külső összetevők és licencük. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val components = listOf(
        "SzabadZene" to "GNU General Public License v3.0",
        "Kotlin, kotlinx.coroutines (JetBrains)" to "Apache License 2.0",
        "AndroidX Core, AppCompat, Activity, Lifecycle (Google)" to "Apache License 2.0",
        "Jetpack Compose, Material 3, Material Icons (Google)" to "Apache License 2.0",
        "AndroidX Navigation (Google)" to "Apache License 2.0",
        "AndroidX Media3 – ExoPlayer, Session (Google)" to "Apache License 2.0",
        "AndroidX Room (Google)" to "Apache License 2.0",
        "AndroidX DataStore (Google)" to "Apache License 2.0",
        "Guava (Google)" to "Apache License 2.0",
        "kotlinx.serialization (JetBrains), JetBrains Annotations" to "Apache License 2.0",
        "Okio (Square), JSpecify" to "Apache License 2.0",
        "JAudioTagger (jthink.net)" to "GNU Lesser General Public License v2.1",
    )
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_licenses)) },
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
                .padding(contentPadding),
        ) {
            Text(
                stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            components.forEach { (name, license) ->
                ListItem(headlineContent = { Text(name) }, supportingContent = { Text(license) })
            }
        }
    }
}
