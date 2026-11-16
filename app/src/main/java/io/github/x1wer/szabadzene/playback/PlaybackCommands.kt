package io.github.x1wer.szabadzene.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.SessionCommand
import io.github.x1wer.szabadzene.data.library.Track

/** A felület és a lejátszószolgáltatás közötti saját parancsok és események. */
object PlaybackCommands {
    const val ACTION_SKIP_BACK = "io.github.x1wer.szabadzene.SKIP_BACK"
    const val ACTION_SKIP_FORWARD = "io.github.x1wer.szabadzene.SKIP_FORWARD"

    const val ACTION_SLEEP_TIMER_SET = "io.github.x1wer.szabadzene.SLEEP_TIMER_SET"
    const val ACTION_SLEEP_TIMER_CANCEL = "io.github.x1wer.szabadzene.SLEEP_TIMER_CANCEL"

    /** Fájlmódosítás (címke, borító) után: a megadott számok újratöltése a lejátszóban. */
    const val ACTION_RELOAD_TRACKS = "io.github.x1wer.szabadzene.RELOAD_TRACKS"

    /**
     * A zenetár Lejátszás/Keverés gombja „a mostani szám végigszól” módban: a várólista a megadott
     * számokra cserélődik úgy, hogy a most szóló szám zavartalanul folytatódik ([EXTRA_TRACK_IDS],
     * [EXTRA_SHUFFLE]).
     */
    const val ACTION_PLAY_KEEP_CURRENT = "io.github.x1wer.szabadzene.PLAY_KEEP_CURRENT"

    /** A szolgáltatás küldi, ha egy szám nem játszható le. */
    const val EVENT_PLAYBACK_ERROR = "io.github.x1wer.szabadzene.EVENT_PLAYBACK_ERROR"

    /** Opcionális: az ugrás mértéke ezredmásodpercben. Hiányában a beállítás érvényes. */
    const val EXTRA_STEP_MS = "step_ms"
    const val EXTRA_TITLE = "title"

    /** Igaz, ha a hiba után a lejátszás leállt (nem volt lejátszható következő szám). */
    const val EXTRA_STOPPED = "stopped"

    /** Időzítő: időtartam ezredmásodpercben, vagy [EXTRA_END_OF_TRACK] = igaz. */
    const val EXTRA_DURATION_MS = "duration_ms"
    const val EXTRA_END_OF_TRACK = "end_of_track"
    const val EXTRA_TRACK_IDS = "track_ids"
    const val EXTRA_SHUFFLE = "shuffle"

    /** A munkamenet-extrák kulcsai: az időzítő állapota (a felület ebből számolja a hátralévő időt). */
    const val SESSION_SLEEP_MODE = "sleep_mode"
    const val SESSION_SLEEP_END_ELAPSED = "sleep_end_elapsed"
    const val SESSION_SLEEP_DURATION = "sleep_duration"
    const val SLEEP_MODE_OFF = 0
    const val SLEEP_MODE_TIMED = 1
    const val SLEEP_MODE_END_OF_TRACK = 2

    val skipBack = SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY)
    val sleepTimerSet = SessionCommand(ACTION_SLEEP_TIMER_SET, Bundle.EMPTY)
    val sleepTimerCancel = SessionCommand(ACTION_SLEEP_TIMER_CANCEL, Bundle.EMPTY)
    val reloadTracks = SessionCommand(ACTION_RELOAD_TRACKS, Bundle.EMPTY)
    val playKeepCurrent = SessionCommand(ACTION_PLAY_KEEP_CURRENT, Bundle.EMPTY)
    val skipForward = SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY)
    val playbackError = SessionCommand(EVENT_PLAYBACK_ERROR, Bundle.EMPTY)
}

/**
 * Egy szám a lejátszó számára. A fájl címét a requestMetadata is hordozza, mert a vezérlő
 * és a munkamenet között a MediaItem helyi URI-ja nem mindig utazik át.
 */
fun Track.toMediaItem(): MediaItem {
    val uri = contentUri
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setDurationMs(durationMs.takeIf { it > 0 })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
}

/** Az időzítő állapota munkamenet-extrákként (a szolgáltatás → vezérlő irányban). */
fun SleepTimerState.toBundle(): Bundle = Bundle().apply {
    when (val state = this@toBundle) {
        SleepTimerState.Off -> putInt(PlaybackCommands.SESSION_SLEEP_MODE, PlaybackCommands.SLEEP_MODE_OFF)
        SleepTimerState.EndOfTrack -> putInt(PlaybackCommands.SESSION_SLEEP_MODE, PlaybackCommands.SLEEP_MODE_END_OF_TRACK)
        is SleepTimerState.Timed -> {
            putInt(PlaybackCommands.SESSION_SLEEP_MODE, PlaybackCommands.SLEEP_MODE_TIMED)
            putLong(PlaybackCommands.SESSION_SLEEP_END_ELAPSED, state.endAtElapsedMs)
            putLong(PlaybackCommands.SESSION_SLEEP_DURATION, state.durationMs)
        }
    }
}

fun sleepTimerStateFrom(extras: Bundle?): SleepTimerState = when (extras?.getInt(PlaybackCommands.SESSION_SLEEP_MODE)) {
    PlaybackCommands.SLEEP_MODE_TIMED -> SleepTimerState.Timed(
        endAtElapsedMs = extras.getLong(PlaybackCommands.SESSION_SLEEP_END_ELAPSED),
        durationMs = extras.getLong(PlaybackCommands.SESSION_SLEEP_DURATION),
    )
    PlaybackCommands.SLEEP_MODE_END_OF_TRACK -> SleepTimerState.EndOfTrack
    else -> SleepTimerState.Off
}
