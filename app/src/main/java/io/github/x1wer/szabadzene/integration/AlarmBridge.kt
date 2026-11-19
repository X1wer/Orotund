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
 */
object AlarmBridge {
    const val API_VERSION = 1
    const val FAVORITES_ID = 0L

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
