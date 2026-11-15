package io.github.x1wer.szabadzene.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    suspend fun setSkipBackSeconds(seconds: Int) {
        val value = SkipSettings.sanitize(seconds)
        dataStore.edit { it[KEY_SKIP_BACK] = value }
    }

    suspend fun setSkipForwardSeconds(seconds: Int) {
        val value = SkipSettings.sanitize(seconds)
        dataStore.edit { it[KEY_SKIP_FORWARD] = value }
    }

    private companion object {
        val KEY_SKIP_BACK = intPreferencesKey("skip_back_seconds")
        val KEY_SKIP_FORWARD = intPreferencesKey("skip_forward_seconds")
    }
}
