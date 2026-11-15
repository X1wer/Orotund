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

    /** A szolgáltatás küldi, ha egy szám nem játszható le. */
    const val EVENT_PLAYBACK_ERROR = "io.github.x1wer.szabadzene.EVENT_PLAYBACK_ERROR"

    /** Opcionális: az ugrás mértéke ezredmásodpercben. Hiányában a beállítás érvényes. */
    const val EXTRA_STEP_MS = "step_ms"
    const val EXTRA_TITLE = "title"

    /** Igaz, ha a hiba után a lejátszás leállt (nem volt lejátszható következő szám). */
    const val EXTRA_STOPPED = "stopped"

    val skipBack = SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY)
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
