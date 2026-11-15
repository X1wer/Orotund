package io.github.x1wer.szabadzene.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * A felhasználó saját adatai. Szándékosan NINCS idegen kulcs a `tracks` táblára: az csak
 * gyorsítótár, amelyet minden beolvasás újraír – a listák és a kedvencek ettől nem veszhetnek el.
 * A már nem létező számokra mutató bejegyzések megmaradnak, de a felület nem mutatja őket.
 */

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlist_id")],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "track_id") val trackId: Long,
    val position: Int,
)

/** Lejátszási lista a listanézethez: név és a bejegyzések száma. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "entry_count") val entryCount: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** A várólista mentett elemei, lejátszási (alap) sorrendben. */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    @ColumnInfo(name = "track_id") val trackId: Long,
)

/** A lejátszás mentett állapota; mindig egyetlen sor (id = 0). */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "current_index") val currentIndex: Int,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "repeat_mode") val repeatMode: Int,
    @ColumnInfo(name = "shuffle_enabled") val shuffleEnabled: Boolean,
    /** A kevert sorrend az alaplista indexeivel, vesszővel elválasztva (üres, ha nincs keverés). */
    @ColumnInfo(name = "shuffle_order") val shuffleOrder: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
