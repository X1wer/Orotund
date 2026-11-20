package io.github.x1wer.szabadzene.integration

import androidx.media3.common.Player

/**
 * Szerződés az ébresztőalkalmazások (Headphone Alarm / FülesÉbresztő) felé: mit lát egy másik app
 * a Zengő lejátszási listáiból. Tiszta Kotlin, unit-tesztelt; a [AlarmPlaylistProvider] használja.
 *
 * - `content://<csomag>.alarmplaylists/playlists` – `_id`, `name`, `track_count`, `is_favorites`
 *   (a Kedvencek azonosítója [FAVORITES_ID], a neve üres – a kliens a saját nyelvén nevezi el)
 * - `…/playlists/<id>/tracks` – `_id`, `title`, `artist`, `duration_ms`, `position`, `audio_uri`
 * - `…/playback_mode` – `shuffle` (0/1), `repeat` ([REPEAT_ALL] | [REPEAT_ONE] | [REPEAT_OFF] | [REPEAT_STOP_AFTER_CURRENT]),
 *   `api_version`
 * - `…/audio/<trackId>` – csak olvasható hangfájl, kizárólag listában vagy a Kedvencek között szereplő számra
 *
 * A hozzáférést a `<csomag>.permission.ALARM_PLAYLISTS` veszélyes szintű engedély védi, amit a
 * felhasználó ad meg az ébresztőalkalmazásnak.
 *
 * 2-es változat (1.8.0): ugyanezzel az engedéllyel a lejátszási munkamenet ([ACTION_ALARM_PLAY],
 * [ACTION_ALARM_VOLUME], [ACTION_ALARM_FINISH] parancsok) – az ébresztő a listát magában a Zengőben
 * indítja el, és a csengetés végén a zene a Zengőben szólhat tovább.
 */
object AlarmBridge {
    const val API_VERSION = 2
    const val FAVORITES_ID = 0L

    /**
     * Lejátszás ébresztésként. Argumentumok: [EXTRA_PLAYLIST_ID], [EXTRA_START_TRACK_ID] (-1 = nincs),
     * [EXTRA_SHUFFLE], [EXTRA_REPEAT] ([REPEAT_ALL] / [REPEAT_ONE] / [REPEAT_OFF]), [EXTRA_STOP_AFTER_FIRST],
     * [EXTRA_REPEAT_LAST], [EXTRA_ALARM_STREAM] (riasztási hangfolyam – hangos ébresztés), [EXTRA_DEVICE_ID]
     * (előnyben részesített kimenet, 0 = nincs), [EXTRA_KEY] és [EXTRA_PREFER_RESUME] (szundi után ugyanott
     * folytatja, ha a várólista azóta nem változott). A lejátszás **némán** indul; a hangerőt az ébresztő
     * az útvonal ellenőrzése után [ACTION_ALARM_VOLUME]-mal adja meg. Eredmény: [RESULT_TRACK_COUNT],
     * [RESULT_RESUMED].
     */
    const val ACTION_ALARM_PLAY = "io.github.x1wer.szabadzene.alarm.PLAY"

    /** A lejátszó saját hangereje ([EXTRA_VOLUME], 0–1) [EXTRA_FADE_MS] alatt elérve. */
    const val ACTION_ALARM_VOLUME = "io.github.x1wer.szabadzene.alarm.VOLUME"

    /**
     * Az ébresztés vége. [EXTRA_KEEP_PLAYING] = igaz: a zene szól tovább (a felhasználó félrehúzta az
     * ébresztést), egyébként szünet. Mindkét esetben visszaáll a felhasználó ismétlési módja, a hangerő,
     * a médiahangfolyam és a szokásos kimenet.
     */
    const val ACTION_ALARM_FINISH = "io.github.x1wer.szabadzene.alarm.FINISH"

    const val EXTRA_PLAYLIST_ID = "playlist_id"
    const val EXTRA_START_TRACK_ID = "start_track_id"
    const val EXTRA_SHUFFLE = "shuffle"
    const val EXTRA_REPEAT = "repeat"
    const val EXTRA_STOP_AFTER_FIRST = "stop_after_first"
    const val EXTRA_REPEAT_LAST = "repeat_last"
    const val EXTRA_ALARM_STREAM = "alarm_stream"
    const val EXTRA_DEVICE_ID = "device_id"
    const val EXTRA_KEY = "key"
    const val EXTRA_PREFER_RESUME = "prefer_resume"
    const val EXTRA_VOLUME = "volume"
    const val EXTRA_FADE_MS = "fade_ms"
    const val EXTRA_KEEP_PLAYING = "keep_playing"
    const val RESULT_TRACK_COUNT = "track_count"
    const val RESULT_RESUMED = "resumed"

    val ALARM_ACTIONS = setOf(ACTION_ALARM_PLAY, ACTION_ALARM_VOLUME, ACTION_ALARM_FINISH)

    /** Az ébresztés várólistája: a számok sorrendje, a kezdő elem és keverésnél a lejátszási sorrend. */
    data class AlarmQueue(val trackIds: List<Long>, val startIndex: Int, val shuffleOrder: List<Int>?)

    /**
     * A lista számaiból ([trackIds], a lista sorrendjében) az ébresztés várólistája. A kezdő szám
     * ([startTrackId]) mindig elsőként szól, keverésnél is; kezdő szám nélkül sorrendben az első,
     * keverve egy véletlen szám kezd. Üres listára null.
     */
    fun alarmQueue(trackIds: List<Long>, startTrackId: Long?, shuffle: Boolean, random: kotlin.random.Random): AlarmQueue? {
        if (trackIds.isEmpty()) return null
        val chosen = startTrackId?.let { trackIds.indexOf(it) }?.takeIf { it >= 0 }
        if (!shuffle) return AlarmQueue(trackIds, chosen ?: 0, null)
        val start = chosen ?: random.nextInt(trackIds.size)
        val rest = trackIds.indices.filter { it != start }.shuffled(random)
        return AlarmQueue(trackIds, start, listOf(start) + rest)
    }

    /** Az ébresztő által kért ismétlési mód a lejátszó állandójára (ismeretlenre: nincs ismétlés). */
    fun repeatModeFor(name: String?): Int = when (name) {
        REPEAT_ALL -> Player.REPEAT_MODE_ALL
        REPEAT_ONE -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }

    const val REPEAT_ALL = "ALL"
    const val REPEAT_ONE = "ONE"
    const val REPEAT_OFF = "OFF"
    const val REPEAT_STOP_AFTER_CURRENT = "STOP_AFTER_CURRENT"

    val PLAYLIST_COLUMNS = arrayOf("_id", "name", "track_count", "is_favorites")
    val TRACK_COLUMNS = arrayOf("_id", "title", "artist", "duration_ms", "position", "audio_uri")
    val MODE_COLUMNS = arrayOf("shuffle", "repeat", "api_version")

    /** A Zengő mentett ismétlési módja a kliens felé (a „leállás a szám végén” külön beállítás). */
    fun repeatName(playerRepeatMode: Int?, stopAfterCurrent: Boolean): String = when {
        stopAfterCurrent -> REPEAT_STOP_AFTER_CURRENT
        playerRepeatMode == Player.REPEAT_MODE_ALL -> REPEAT_ALL
        playerRepeatMode == Player.REPEAT_MODE_ONE -> REPEAT_ONE
        else -> REPEAT_OFF
    }

    data class BridgeTrack(val id: Long, val title: String, val artist: String?, val durationMs: Long)

    /**
     * A lista sorrendje szerint a még létező számok; a zenetárból eltűnt fájlok kimaradnak,
     * a pozíció az átadott sorban folytonos.
     */
    fun orderedTracks(entryTrackIds: List<Long>, known: Map<Long, BridgeTrack>): List<Pair<Int, BridgeTrack>> =
        entryTrackIds.mapNotNull { known[it] }.mapIndexed { index, track -> index to track }

    /** A kérés útvonalából a lista azonosítója (`playlists/<id>/tracks`), vagy null. */
    fun playlistIdFrom(pathSegments: List<String>): Long? =
        if (pathSegments.size == 3 && pathSegments[0] == "playlists" && pathSegments[2] == "tracks") {
            pathSegments[1].toLongOrNull()?.takeIf { it >= 0 }
        } else {
            null
        }

    /** A kérés útvonalából a szám azonosítója (`audio/<id>`), vagy null. */
    fun audioIdFrom(pathSegments: List<String>): Long? =
        if (pathSegments.size == 2 && pathSegments[0] == "audio") pathSegments[1].toLongOrNull()?.takeIf { it > 0 } else null
}
