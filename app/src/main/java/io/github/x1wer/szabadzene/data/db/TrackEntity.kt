package io.github.x1wer.szabadzene.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.x1wer.szabadzene.data.library.Track

/**
 * A MediaStore-ból beolvasott számok helyi gyorsítótára. Bármikor újraépíthető
 * a MediaStore-ból, ezért nem tartalmaz felhasználói adatot.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "display_name") val displayName: String,
    val path: String?,
    @ColumnInfo(name = "relative_path") val relativePath: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "date_added") val dateAddedSeconds: Long,
    val genre: String?,
    @ColumnInfo(name = "track_number") val trackNumber: Int,
    val year: Int,
) {
    fun toTrack() = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        displayName = displayName,
        path = path,
        relativePath = relativePath,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        dateAddedSeconds = dateAddedSeconds,
        genre = genre,
        trackNumber = trackNumber,
        year = year,
    )
}
