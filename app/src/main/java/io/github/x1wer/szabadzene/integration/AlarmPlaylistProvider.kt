package io.github.x1wer.szabadzene.integration

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import io.github.x1wer.szabadzene.SzabadZeneApp
import io.github.x1wer.szabadzene.integration.AlarmBridge.BridgeTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Csak olvasható híd az ébresztőalkalmazások felé (lásd [AlarmBridge]). Az exportált szolgáltatót a
 * manifest `readPermission`-je védi, ezért ide csak az a hívó jut be, amelyiknek a felhasználó
 * megadta a `ALARM_PLAYLISTS` engedélyt. Semmit nem ír, és csak a listákban szereplő számokat adja ki.
 */
class AlarmPlaylistProvider : ContentProvider() {

    private val container get() = (context!!.applicationContext as SzabadZeneApp).container

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = runBlocking {
        val segments = uri.pathSegments
        when {
            segments == listOf("playlists") -> playlists()
            segments == listOf("playback_mode") -> playbackMode()
            else -> AlarmBridge.playlistIdFrom(segments)?.let { tracks(it) }
        }
    }

    private suspend fun playlists(): Cursor {
        val db = container.database
        val cursor = MatrixCursor(AlarmBridge.PLAYLIST_COLUMNS)
        cursor.addRow(arrayOf<Any?>(AlarmBridge.FAVORITES_ID, "", db.favoriteDao().ids().size, 1))
        db.playlistDao().playlists().forEach { cursor.addRow(arrayOf<Any?>(it.id, it.name, it.entryCount, 0)) }
        return cursor
    }

    private suspend fun tracks(playlistId: Long): Cursor {
        val db = container.database
        val ids = if (playlistId == AlarmBridge.FAVORITES_ID) {
            db.favoriteDao().ids()
        } else {
            db.playlistDao().entries(playlistId).map { it.trackId }
        }
        val known = db.trackDao().byIds(ids.distinct()).associate {
            it.id to BridgeTrack(it.id, it.title, it.artist, it.durationMs)
        }
        val authority = context!!.packageName + AUTHORITY_SUFFIX
        val cursor = MatrixCursor(AlarmBridge.TRACK_COLUMNS)
        AlarmBridge.orderedTracks(ids, known).forEach { (position, track) ->
            val audio = Uri.Builder().scheme("content").authority(authority).appendPath("audio")
                .appendPath(track.id.toString()).build()
            cursor.addRow(arrayOf<Any?>(track.id, track.title, track.artist, track.durationMs, position, audio.toString()))
        }
        return cursor
    }

    private suspend fun playbackMode(): Cursor {
        val state = container.database.queueDao().state()
        val stopAfterCurrent = container.settings.playback.first().stopAfterCurrent
        val cursor = MatrixCursor(AlarmBridge.MODE_COLUMNS)
        cursor.addRow(
            arrayOf<Any?>(
                if (state?.shuffleEnabled == true) 1 else 0,
                AlarmBridge.repeatName(state?.repeatMode, stopAfterCurrent),
                AlarmBridge.API_VERSION,
            ),
        )
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("read-only")
        val trackId = AlarmBridge.audioIdFrom(uri.pathSegments) ?: throw FileNotFoundException(uri.toString())
        val shared = runBlocking {
            val db = container.database
            db.playlistDao().playlistsContaining(trackId).isNotEmpty() || db.favoriteDao().contains(trackId)
        }
        if (!shared) throw FileNotFoundException("not in a playlist")
        val media = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
        return context!!.contentResolver.openFileDescriptor(media, "r") ?: throw FileNotFoundException(uri.toString())
    }

    override fun getType(uri: Uri): String? = when {
        AlarmBridge.audioIdFrom(uri.pathSegments) != null -> "audio/*"
        else -> "vnd.android.cursor.dir/vnd.${context!!.packageName}.alarm"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")

    companion object {
        const val AUTHORITY_SUFFIX = ".alarmplaylists"
    }
}
