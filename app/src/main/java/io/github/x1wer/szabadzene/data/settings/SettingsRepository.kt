package io.github.x1wer.szabadzene.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.x1wer.szabadzene.data.playlists.PlaylistListSettings
import io.github.x1wer.szabadzene.data.playlists.PlaylistSortField
import io.github.x1wer.szabadzene.playback.PreviousLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Az ugrógombok beállítása másodpercben, irányonként külön. A fő érték ([backSeconds],
 * [forwardSeconds]) mindenhol megjelenik (teljes és minilejátszó, értesítés, fejhallgató);
 * az [extraSeconds] további gombokat tesz a teljes lejátszóba, mindkét irányba (ha elférnek).
 */
data class SkipSettings(
    val backSeconds: Int = DEFAULT_SECONDS,
    val forwardSeconds: Int = DEFAULT_SECONDS,
    val extraSeconds: List<Int> = DEFAULT_EXTRA,
) {
    val backMs: Long get() = backSeconds * 1000L
    val forwardMs: Long get() = forwardSeconds * 1000L

    /** A teljes lejátszó gombjai egy irányba, növekvő sorrendben; a fő érték mindig benne van. */
    fun stepsFor(back: Boolean): List<Int> =
        (extraSeconds + (if (back) backSeconds else forwardSeconds)).distinct().sorted()

    companion object {
        const val DEFAULT_SECONDS = 10
        val ALLOWED_SECONDS = listOf(5, 10, 15, 30, 60)
        const val MAX_EXTRA = 2
        val DEFAULT_EXTRA = listOf(5, 30)

        /** Érvénytelen (pl. régi vagy sérült) tárolt értéknél az alapérték érvényes. */
        fun sanitize(value: Int?): Int = value?.takeIf { it in ALLOWED_SECONDS } ?: DEFAULT_SECONDS

        /** Csak engedélyezett, különböző értékek, legfeljebb [MAX_EXTRA] darab, növekvő sorrendben. */
        fun sanitizeExtra(values: Collection<Int>): List<Int> =
            values.filter { it in ALLOWED_SECONDS }.distinct().sorted().take(MAX_EXTRA)

        fun encodeExtra(values: Collection<Int>): String = sanitizeExtra(values).joinToString(",")

        /**
         * Hány ugrógomb fér el irányonként egy sorban (legalább 1): [maxPerSide]-tól lefelé az első,
         * amelynél a 2·k gomb és a köztük lévő térközök elférnek.
         */
        fun buttonsPerSide(maxPerSide: Int, availableDp: Float, buttonDp: Float, gapDp: Float): Int {
            for (k in maxPerSide downTo 2) {
                val need = 2 * k * buttonDp + (2 * k - 1) * gapDp
                if (need <= availableDp) return k
            }
            return 1
        }

        /** A megjelenő értékek: a fő érték és mellette a legnagyobb további értékek, összesen [count] darab, növekvő sorrendben. */
        fun visibleSteps(steps: List<Int>, main: Int, count: Int): List<Int> {
            val others = steps.filter { it != main }.sortedDescending().take((count - 1).coerceAtLeast(0))
            return (others + main).sorted()
        }

        /** Hiányzó érték: alapértelmezés; üres szöveg: nincs további gomb. */
        fun decodeExtra(text: String?): List<Int> = when (text) {
            null -> DEFAULT_EXTRA
            else -> sanitizeExtra(text.split(',').mapNotNull { it.trim().toIntOrNull() })
        }
    }
}

/**
 * Lejátszási beállítások: mennyi idő után teker az „Előző” gomb a szám elejére az előző szám
 * helyett, lejátszási sebesség, és „leállás az aktuális szám végén” mód.
 */
data class PlaybackPrefs(
    val restartThresholdSeconds: Int = PreviousLogic.DEFAULT_THRESHOLD_SECONDS,
    val speed: Float = 1f,
    val stopAfterCurrent: Boolean = false,
    /** Mit tegyen a zenetár Lejátszás/Keverés gombja, ha már szól zene. */
    val playButtons: PlayButtonMode = PlayButtonMode.RESTART,
) {
    val restartThresholdMs: Long get() = restartThresholdSeconds * 1000L

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f

        fun sanitizeThreshold(value: Int?): Int =
            value?.takeIf { it in PreviousLogic.ALLOWED_THRESHOLD_SECONDS } ?: PreviousLogic.DEFAULT_THRESHOLD_SECONDS

        /** 0,05-ös lépésre kerekítve, a megengedett tartományon belül. */
        fun sanitizeSpeed(value: Float?): Float {
            val v = value?.takeIf { it.isFinite() } ?: 1f
            return (Math.round(v.coerceIn(MIN_SPEED, MAX_SPEED) * 20f) / 20f)
        }
    }
}

/**
 * A zenetár Lejátszás és Keverés gombja, ha már szól zene: [RESTART] – azonnal az új sorrend indul
 * (az első vagy egy véletlen számmal); [AFTER_CURRENT] – a mostani szám végigszól, utána folytatódik az
 * új szabály szerint (sorrendben a lista következő számával, illetve keverve). Ha nem szól zene, mindkét
 * esetben azonnal indul.
 */
enum class PlayButtonMode { RESTART, AFTER_CURRENT }

/** A zenetár belső fülei (a megjelenítés sorrendjében). */
enum class LibrarySection { TRACKS, ALBUMS, ARTISTS, GENRES }

/** Mely fülek látszanak a zenetárban (legalább egy mindig). Egyetlen fülnél a fülsáv el is tűnik. */
data class LibraryLayout(val sections: List<LibrarySection> = LibrarySection.entries) {
    val showTabRow: Boolean get() = sections.size > 1

    companion object {
        /** Kanonikus sorrend, ismétlés nélkül; üres vagy érvénytelen értéknél az összes fül. */
        fun sanitize(values: Collection<LibrarySection>): List<LibrarySection> =
            LibrarySection.entries.filter { it in values }.ifEmpty { LibrarySection.entries }

        fun encode(values: Collection<LibrarySection>): String = sanitize(values).joinToString(",") { it.name }

        /** Hiányzó érték: minden fül; ismeretlen nevek kimaradnak. */
        fun decode(text: String?): LibraryLayout {
            if (text == null) return LibraryLayout()
            val names = text.split(',').map { it.trim() }.toSet()
            return LibraryLayout(sanitize(LibrarySection.entries.filter { it.name in names }))
        }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Választható kiemelőszín, ha a dinamikus (rendszer)szín ki van kapcsolva vagy nem elérhető. */
enum class AccentColor { GREEN, BLUE, PURPLE, ORANGE, RED }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: AccentColor = AccentColor.GREEN,
    /** A betűméret a rendszer betűméretéhez képest, százalékban. */
    val textScalePercent: Int = 100,
    /** Az ikonok (szimbólumok) mérete, százalékban. */
    val iconScalePercent: Int = 100,
) {
    companion object {
        val ALLOWED_SCALE_PERCENTS = listOf(85, 100, 115, 130, 150)

        /** A rendszer legnagyobb betűmérete 2,0×; ennél nagyobbra a saját szorzó sem viszi. */
        const val MAX_FONT_SCALE = 2f
        const val MIN_FONT_SCALE = 0.7f

        fun sanitizeScale(value: Int?): Int = value?.takeIf { it in ALLOWED_SCALE_PERCENTS } ?: 100

        /** A rendszer betűmérete és a saját szorzó együtt, a tesztelt tartományba szorítva. */
        fun effectiveFontScale(systemFontScale: Float, textScalePercent: Int): Float =
            (systemFontScale * sanitizeScale(textScalePercent) / 100f).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    }
}

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

/**
 * Alapból a legutóbb hozzáadott szám van felül. Az irány alapértéke a mező természetes iránya:
 * dátumnál a legújabb elöl, a többinél növekvő.
 */
data class LibrarySort(
    val field: SortField = SortField.DATE_ADDED,
    val descending: Boolean = field == SortField.DATE_ADDED,
) {
    companion object {
        /** Mezőváltáskor a természetes irány. */
        fun naturalFor(field: SortField): LibrarySort = LibrarySort(field)
    }
}

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
                extraSeconds = SkipSettings.decodeExtra(prefs[KEY_SKIP_EXTRA]),
            )
        }
        .distinctUntilChanged()

    val appearance: Flow<AppearanceSettings> = dataStore.data
        .map { prefs ->
            AppearanceSettings(
                themeMode = enumOrDefault(prefs[KEY_THEME], ThemeMode.SYSTEM),
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
                accent = enumOrDefault(prefs[KEY_ACCENT], AccentColor.GREEN),
                textScalePercent = AppearanceSettings.sanitizeScale(prefs[KEY_TEXT_SCALE]),
                iconScalePercent = AppearanceSettings.sanitizeScale(prefs[KEY_ICON_SCALE]),
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
                field = enumOrDefault(prefs[KEY_SORT_FIELD], SortField.DATE_ADDED),
                descending = prefs[KEY_SORT_DESCENDING] ?: (prefs[KEY_SORT_FIELD] == null),
            )
        }
        .distinctUntilChanged()

    val playback: Flow<PlaybackPrefs> = dataStore.data
        .map { prefs ->
            PlaybackPrefs(
                restartThresholdSeconds = PlaybackPrefs.sanitizeThreshold(prefs[KEY_RESTART_THRESHOLD]),
                speed = PlaybackPrefs.sanitizeSpeed(prefs[KEY_SPEED]),
                stopAfterCurrent = prefs[KEY_STOP_AFTER_CURRENT] ?: false,
                playButtons = enumOrDefault(prefs[KEY_PLAY_BUTTONS], PlayButtonMode.RESTART),
            )
        }
        .distinctUntilChanged()

    val playlistList: Flow<PlaylistListSettings> = dataStore.data
        .map { prefs ->
            PlaylistListSettings(
                field = enumOrDefault(prefs[KEY_PLAYLIST_SORT], PlaylistSortField.NAME),
                descending = prefs[KEY_PLAYLIST_DESC] ?: false,
                specialFirst = prefs[KEY_PLAYLIST_SPECIAL_FIRST] ?: true,
            )
        }
        .distinctUntilChanged()

    suspend fun setPlaylistList(settings: PlaylistListSettings) = dataStore.edit {
        it[KEY_PLAYLIST_SORT] = settings.field.name
        it[KEY_PLAYLIST_DESC] = settings.descending
        it[KEY_PLAYLIST_SPECIAL_FIRST] = settings.specialFirst
    }

    /** Honnan indult a lejátszás (lásd `ui/PlaybackOrigin`); a widget ezt nyitja meg. */
    val playbackOrigin: Flow<String?> = dataStore.data.map { it[KEY_PLAYBACK_ORIGIN] }.distinctUntilChanged()

    suspend fun setPlaybackOrigin(value: String) = dataStore.edit { it[KEY_PLAYBACK_ORIGIN] = value }

    val libraryLayout: Flow<LibraryLayout> = dataStore.data
        .map { prefs -> LibraryLayout.decode(prefs[KEY_LIBRARY_SECTIONS]) }
        .distinctUntilChanged()

    suspend fun setLibrarySections(sections: Collection<LibrarySection>) {
        val value = LibraryLayout.encode(sections)
        dataStore.edit { it[KEY_LIBRARY_SECTIONS] = value }
    }

    suspend fun setPlayButtonMode(mode: PlayButtonMode) = dataStore.edit { it[KEY_PLAY_BUTTONS] = mode.name }

    suspend fun setRestartThresholdSeconds(seconds: Int) {
        val value = PlaybackPrefs.sanitizeThreshold(seconds)
        dataStore.edit { it[KEY_RESTART_THRESHOLD] = value }
    }

    suspend fun setSpeed(speed: Float) {
        val value = PlaybackPrefs.sanitizeSpeed(speed)
        dataStore.edit { it[KEY_SPEED] = value }
    }

    suspend fun setStopAfterCurrent(enabled: Boolean) = dataStore.edit { it[KEY_STOP_AFTER_CURRENT] = enabled }

    suspend fun setSkipExtraSeconds(values: Collection<Int>) {
        val value = SkipSettings.encodeExtra(values)
        dataStore.edit { it[KEY_SKIP_EXTRA] = value }
    }

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

    suspend fun setTextScalePercent(percent: Int) =
        dataStore.edit { it[KEY_TEXT_SCALE] = AppearanceSettings.sanitizeScale(percent) }

    suspend fun setIconScalePercent(percent: Int) =
        dataStore.edit { it[KEY_ICON_SCALE] = AppearanceSettings.sanitizeScale(percent) }

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
        val KEY_SKIP_EXTRA = stringPreferencesKey("skip_extra_seconds")
        val KEY_RESTART_THRESHOLD = intPreferencesKey("restart_threshold_seconds")
        val KEY_SPEED = floatPreferencesKey("playback_speed")
        val KEY_STOP_AFTER_CURRENT = booleanPreferencesKey("stop_after_current")
        val KEY_PLAY_BUTTONS = stringPreferencesKey("play_buttons_mode")
        val KEY_LIBRARY_SECTIONS = stringPreferencesKey("library_sections")
        val KEY_PLAYBACK_ORIGIN = stringPreferencesKey("playback_origin")
        val KEY_PLAYLIST_SORT = stringPreferencesKey("playlist_sort")
        val KEY_PLAYLIST_DESC = booleanPreferencesKey("playlist_sort_descending")
        val KEY_PLAYLIST_SPECIAL_FIRST = booleanPreferencesKey("playlist_special_first")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_ACCENT = stringPreferencesKey("accent")
        val KEY_TEXT_SCALE = intPreferencesKey("text_scale_percent")
        val KEY_ICON_SCALE = intPreferencesKey("icon_scale_percent")
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
