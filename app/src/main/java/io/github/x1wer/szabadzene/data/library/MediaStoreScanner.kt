package io.github.x1wer.szabadzene.data.library

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import io.github.x1wer.szabadzene.data.db.TrackEntity

/** A MediaStore hangfájljainak beolvasása. Csak olvas, semmit nem módosít. */
class MediaStoreScanner(private val resolver: ContentResolver) {

    fun scan(): List<TrackEntity> {
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Audio.Media.RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.Media.GENRE)
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val result = ArrayList<TrackEntity>()
        resolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val c = Columns(cursor)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(c.id)
                val displayName = cursor.getStringOrNull(c.displayName)
                    ?: cursor.getStringOrNull(c.data)?.substringAfterLast('/')
                    ?: id.toString()
                var title = cursor.getStringOrNull(c.title)
                var artist = cursor.getStringOrNull(c.artist)
                var album = cursor.getStringOrNull(c.album)
                var genre = c.genre?.let { cursor.getStringOrNull(it) }
                val mimeType = cursor.getStringOrNull(c.mimeType)
                if (mimeType == "audio/mpeg" && Id3TextReader.looksMisdecoded(title, artist, album, genre)) {
                    repairFromFile(id)?.let { tags ->
                        title = tags.title ?: title
                        artist = tags.artist ?: artist
                        album = tags.album ?: album
                        genre = tags.genre ?: genre
                    }
                }
                result += TrackEntity(
                    id = id,
                    title = Track.titleOrFileName(title, displayName),
                    artist = Track.cleanTag(artist),
                    album = Track.cleanTag(album),
                    albumId = cursor.getLongOrZero(c.albumId),
                    durationMs = cursor.getLongOrZero(c.duration),
                    displayName = displayName,
                    path = cursor.getStringOrNull(c.data),
                    relativePath = c.relativePath?.let { cursor.getStringOrNull(it) },
                    sizeBytes = cursor.getLongOrZero(c.size),
                    mimeType = mimeType,
                    dateAddedSeconds = cursor.getLongOrZero(c.dateAdded),
                    genre = Track.cleanTag(genre),
                    // A MediaStore a lemez sorszámát ezresekként tárolja (pl. 2005 = 2. lemez 5. szám).
                    trackNumber = (cursor.getLongOrZero(c.track) % 1000).toInt(),
                    year = cursor.getLongOrZero(c.year).toInt(),
                )
            }
        }
        return result
    }

    /** A MediaStore által elrontott ID3-szöveg újraolvasása közvetlenül a fájlból. */
    private fun repairFromFile(id: Long): Id3TextReader.Tags? = try {
        resolver.openInputStream(Track.contentUriFor(id))?.use { Id3TextReader.read(it) }
    } catch (e: Exception) {
        // Eltűnt vagy olvashatatlan fájl: marad a MediaStore szövege.
        null
    }

    private class Columns(cursor: Cursor) {
        val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val displayName = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

        @Suppress("DEPRECATION")
        val data = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val mimeType = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
        } else {
            null
        }
        val genre = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
        } else {
            null
        }
    }
}

private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

private fun Cursor.getLongOrZero(index: Int): Long = if (isNull(index)) 0L else getLong(index)
