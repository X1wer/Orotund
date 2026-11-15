package io.github.x1wer.szabadzene.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Az ugrógombok beállítása másodpercben, irányonként külön. */
data class SkipSettings(
    val backSeconds: Int = DEFAULT_SECONDS,
    val forwardSeconds: Int = DEFAULT_SECONDS,
) {
    val backMs: Long get() = backSeconds * 1000L
    val forwardMs: Long get() = forwardSeconds * 1000L

    companion object {
        const val DEFAULT_SECONDS = 10
        val ALLOWED_SECONDS = listOf(5, 10, 15, 30, 60)

        /** Érvénytelen (pl. régi vagy sérült) tárolt értéknél az alapérték érvényes. */
        fun sanitize(value: Int?): Int = value?.takeIf { it in ALLOWED_SECONDS } ?: DEFAULT_SECONDS
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Választható kiemelőszín, ha a dinamikus (rendszer)szín ki van kapcsolva vagy nem elérhető. */
enum class AccentColor { GREEN, BLUE, PURPLE, ORANGE, RED }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: AccentColor = AccentColor.GREEN,
)

/** Fejhallgató és Bluetooth: kihúzáskor szünet (alapból be), visszacsatlakozáskor folytatás (alapból ki). */
data class HeadsetSettings(
    val pauseOnDisconnect: Boolean = true,
    val resumeOnReconnect: Boolean = false,
)

/** Mit rejtsen el a zenetár: kizárt mappák és a túl rövid hangfájlok. */
data class LibraryFilter(
    val excludedFolders: Set<String> = emptySet(),
    val minDurationSeconds: Int = 0,
) {
    companion object {
        val ALLOWED_MIN_DURATIONS = listOf(0, 10, 30, 60)

        fun sanitizeMinDuration(value: Int?): Int = value?.takeIf { it in ALLOWED_MIN_DURATIONS } ?: 0
    }
}

enum class SortField { TITLE, ARTIST, DURATION, DATE_ADDED }

data class LibrarySort(
    val field: SortField = SortField.TITLE,
    val descending: Boolean = false,
)

/**
 * Hangszínszabályzó: be/ki, előbeállítás neve (lásd `EqPreset`), egyéni sávértékek
 * (frekvencia Hz → millibel, a készülék sávjaitól függetlenül tárolva) és mélyhang-kiemelés (0–1000).
 */
data class EqSettings(
    val enabled: Boolean = false,
    val preset: String = "FLAT",
    val customLevels: Map<Int, Int> = emptyMap(),
    val bassBoost: Int = 0,
) {
    companion object {
        fun encodeLevels(levels: Map<Int, Int>): String =
            levels.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

        /** Hibás vagy régi tárolt szövegnél üres térkép (= sík). */
        fun decodeLevels(value: String?): Map<Int, Int> = value.orEmpty().split(',').mapNotNull { pair ->
            val parts = pair.split(':')
            val hz = parts.getOrNull(0)?.trim()?.toIntOrNull()
            val mb = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (parts.size == 2 && hz != null && mb != null && hz > 0) hz to mb.coerceIn(-1500, 1500) else null
        }.toMap()
    }
}

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val skipSettings: Flow<SkipSettings> = dataStore.data
        .map { prefs ->
            SkipSettings(
                backSeconds = SkipSettings.sanitize(prefs[KEY_SKIP_BACK]),
                forwardSeconds = SkipSettings.sanitize(prefs[KEY_SKIP_FORWARD]),
            )
        }
        .distinctUntilChanged()

    val appearance: Flow<AppearanceSettings> = dataStore.data
        .map { prefs ->
            AppearanceSettings(
                themeMode = enumOrDefault(prefs[KEY_THEME], ThemeMode.SYSTEM),
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
                accent = enumOrDefault(prefs[KEY_ACCENT], AccentColor.GREEN),
            )
        }
        .distinctUntilChanged()

    val headset: Flow<HeadsetSettings> = dataStore.data
        .map { prefs ->
            HeadsetSettings(
                pauseOnDisconnect = prefs[KEY_PAUSE_ON_DISCONNECT] ?: true,
                resumeOnReconnect = prefs[KEY_RESUME_ON_RECONNECT] ?: false,
            )
        }
        .distinctUntilChanged()

    val libraryFilter: Flow<LibraryFilter> = dataStore.data
        .map { prefs ->
            LibraryFilter(
                excludedFolders = prefs[KEY_EXCLUDED_FOLDERS] ?: emptySet(),
                minDurationSeconds = LibraryFilter.sanitizeMinDuration(prefs[KEY_MIN_DURATION]),
            )
        }
        .distinctUntilChanged()

    val librarySort: Flow<LibrarySort> = dataStore.data
        .map { prefs ->
            LibrarySort(
                field = enumOrDefault(prefs[KEY_SORT_FIELD], SortField.TITLE),
                descending = prefs[KEY_SORT_DESCENDING] ?: false,
            )
        }
        .distinctUntilChanged()

    val equalizer: Flow<EqSettings> = dataStore.data
        .map { prefs ->
            EqSettings(
                enabled = prefs[KEY_EQ_ENABLED] ?: false,
                preset = prefs[KEY_EQ_PRESET] ?: "FLAT",
                customLevels = EqSettings.decodeLevels(prefs[KEY_EQ_CUSTOM]),
                bassBoost = (prefs[KEY_BASS_BOOST] ?: 0).coerceIn(0, 1000),
            )
        }
        .distinctUntilChanged()

    suspend fun setEqualizerEnabled(enabled: Boolean) = dataStore.edit { it[KEY_EQ_ENABLED] = enabled }

    suspend fun setEqualizerPreset(preset: String) = dataStore.edit { it[KEY_EQ_PRESET] = preset }

    /** Egyéni sávértékek mentése; egyúttal az „Egyéni” előbeállítás lesz aktív. */
    suspend fun setEqualizerCustom(levels: Map<Int, Int>) = dataStore.edit {
        it[KEY_EQ_CUSTOM] = EqSettings.encodeLevels(levels)
        it[KEY_EQ_PRESET] = "CUSTOM"
    }

    suspend fun setBassBoost(strength: Int) = dataStore.edit { it[KEY_BASS_BOOST] = strength.coerceIn(0, 1000) }

    suspend fun setSkipBackSeconds(seconds: Int) {
        val value = SkipSettings.sanitize(seconds)
        dataStore.edit { it[KEY_SKIP_BACK] = value }
    }

    suspend fun setSkipForwardSeconds(seconds: Int) {
        val value = SkipSettings.sanitize(seconds)
        dataStore.edit { it[KEY_SKIP_FORWARD] = value }
    }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[KEY_THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun setAccent(accent: AccentColor) = dataStore.edit { it[KEY_ACCENT] = accent.name }

    suspend fun setPauseOnDisconnect(enabled: Boolean) = dataStore.edit { it[KEY_PAUSE_ON_DISCONNECT] = enabled }

    suspend fun setResumeOnReconnect(enabled: Boolean) = dataStore.edit { it[KEY_RESUME_ON_RECONNECT] = enabled }

    suspend fun setMinDurationSeconds(seconds: Int) {
        val value = LibraryFilter.sanitizeMinDuration(seconds)
        dataStore.edit { it[KEY_MIN_DURATION] = value }
    }

    suspend fun excludeFolder(path: String) = dataStore.edit {
        it[KEY_EXCLUDED_FOLDERS] = (it[KEY_EXCLUDED_FOLDERS] ?: emptySet()) + path
    }

    suspend fun includeFolder(path: String) = dataStore.edit {
        it[KEY_EXCLUDED_FOLDERS] = (it[KEY_EXCLUDED_FOLDERS] ?: emptySet()) - path
    }

    suspend fun setSort(sort: LibrarySort) = dataStore.edit {
        it[KEY_SORT_FIELD] = sort.field.name
        it[KEY_SORT_DESCENDING] = sort.descending
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val KEY_SKIP_BACK = intPreferencesKey("skip_back_seconds")
        val KEY_SKIP_FORWARD = intPreferencesKey("skip_forward_seconds")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_ACCENT = stringPreferencesKey("accent")
        val KEY_PAUSE_ON_DISCONNECT = booleanPreferencesKey("pause_on_disconnect")
        val KEY_RESUME_ON_RECONNECT = booleanPreferencesKey("resume_on_reconnect")
        val KEY_EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
        val KEY_MIN_DURATION = intPreferencesKey("min_duration_seconds")
        val KEY_SORT_FIELD = stringPreferencesKey("sort_field")
        val KEY_SORT_DESCENDING = booleanPreferencesKey("sort_descending")
        val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val KEY_EQ_PRESET = stringPreferencesKey("eq_preset")
        val KEY_EQ_CUSTOM = stringPreferencesKey("eq_custom_levels")
        val KEY_BASS_BOOST = intPreferencesKey("bass_boost")
    }
}
