package io.github.x1wer.szabadzene.playback

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import io.github.x1wer.szabadzene.data.settings.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A lejátszószolgáltatás közzéteszi, mely hanghatások működnek ténylegesen ezen a készüléken.
 * Egy példány az [io.github.x1wer.szabadzene.AppContainer]-ben; null = még nincs futó lejátszó.
 */
class AudioEffectsRegistry {
    private val _capabilities = MutableStateFlow<EffectsCapabilities?>(null)
    val capabilities: StateFlow<EffectsCapabilities?> = _capabilities.asStateFlow()

    internal fun publish(value: EffectsCapabilities?) {
        _capabilities.value = value
    }
}

/**
 * A lejátszó hangmunkamenetére kötött hangszínszabályzó és mélyhang-kiemelés. A hatás a
 * munkamenethez tartozik, ezért számváltáskor is érvényben marad; a beállítás a DataStore-ban él,
 * így újraindítás után is. Csak az a hatás kerül a felületre, amelyiket ténylegesen létre lehetett hozni.
 */
class AudioEffectsController(private val registry: AudioEffectsRegistry) {

    private var sessionId = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var bands: List<EqBand> = emptyList()
    private var settings = EqSettings()

    /** Új hangmunkamenet (a lejátszó létrehozásakor vagy ha a lejátszó újat kap). */
    fun attach(audioSessionId: Int) {
        if (audioSessionId == sessionId && (equalizer != null || bassBoost != null)) return
        release(publish = false)
        sessionId = audioSessionId
        if (audioSessionId <= 0) {
            registry.publish(EffectsCapabilities.NONE)
            return
        }
        equalizer = if (isAvailable(AudioEffect.EFFECT_TYPE_EQUALIZER)) createEqualizer(audioSessionId) else null
        bassBoost = if (isAvailable(AudioEffect.EFFECT_TYPE_BASS_BOOST)) createBassBoost(audioSessionId) else null
        bands = equalizer?.let(::readBands).orEmpty()
        if (bands.isEmpty()) {
            equalizer?.release()
            equalizer = null
        }
        registry.publish(
            EffectsCapabilities(
                equalizer = equalizer != null,
                bands = bands,
                bassBoost = bassBoost != null,
            ),
        )
        apply(settings)
    }

    fun apply(newSettings: EqSettings) {
        settings = newSettings
        equalizer?.let { eq ->
            runCatching {
                val levels = EqualizerLogic.levelsFor(EqPreset.of(newSettings.preset), newSettings.customLevels, bands)
                levels.forEachIndexed { index, level -> eq.setBandLevel(index.toShort(), level.toShort()) }
                eq.enabled = newSettings.enabled
            }.onFailure { Log.w(TAG, "A hangszínszabályzó beállítása nem sikerült", it) }
        }
        bassBoost?.let { boost ->
            runCatching {
                boost.setStrength(newSettings.bassBoost.toShort())
                boost.enabled = newSettings.enabled && newSettings.bassBoost > 0
            }.onFailure { Log.w(TAG, "A mélyhang-kiemelés beállítása nem sikerült", it) }
        }
    }

    fun release(publish: Boolean = true) {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        bands = emptyList()
        if (publish) registry.publish(null)
    }

    private fun isAvailable(type: java.util.UUID): Boolean =
        runCatching { AudioEffect.queryEffects().orEmpty().any { it.type == type } }.getOrDefault(false)

    private fun createEqualizer(session: Int): Equalizer? = try {
        Equalizer(0, session)
    } catch (e: Exception) {
        Log.w(TAG, "Hangszínszabályzó nem hozható létre", e)
        null
    }

    private fun createBassBoost(session: Int): BassBoost? = try {
        BassBoost(0, session).takeIf { it.strengthSupported }.also { if (it == null) Log.i(TAG, "Mélyhang-erősség nem állítható") }
    } catch (e: Exception) {
        Log.w(TAG, "Mélyhang-kiemelés nem hozható létre", e)
        null
    }

    private fun readBands(eq: Equalizer): List<EqBand> = try {
        val range = eq.bandLevelRange
        (0 until eq.numberOfBands).map { index ->
            EqBand(
                centerHz = eq.getCenterFreq(index.toShort()) / 1000,
                minMb = range[0].toInt(),
                maxMb = range[1].toInt(),
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "A sávok lekérdezése nem sikerült", e)
        emptyList()
    }

    private companion object {
        const val TAG = "AudioEffects"
    }
}
