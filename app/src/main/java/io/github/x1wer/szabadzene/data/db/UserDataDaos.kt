package io.github.x1wer.szabadzene.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id AS id, p.name AS name,
               (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlist_id = p.id) AS entry_count
        FROM playlists p
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** A bejegyzések az idegen kulcs miatt maguktól törlődnek. */
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_entries WHERE playlist_id = :playlistId ORDER BY position")
    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntryEntity>>

    @Query("SELECT * FROM playlist_entries WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun entries(playlistId: Long): List<PlaylistEntryEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Update
    suspend fun updateEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    /** Új lista létrehozása, opcionálisan kezdő számokkal. */
    @Transaction
    suspend fun createWithTracks(name: String, trackIds: List<Long>, now: Long): Long {
        val id = insertPlaylist(PlaylistEntity(name = name, createdAt = now))
        addTracks(id, trackIds)
        return id
    }

    /** Számok hozzáfűzése a lista végére, a megadott sorrendben. */
    @Transaction
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        var position = maxPosition(playlistId)
        insertEntries(trackIds.map { PlaylistEntryEntity(playlistId = playlistId, trackId = it, position = ++position) })
    }

    /** A [fromEntryId] bejegyzés a [toEntryId] helyére kerül; a többi eltolódik. */
    @Transaction
    suspend fun moveEntry(playlistId: Long, fromEntryId: Long, toEntryId: Long) {
        val list = entries(playlistId).toMutableList()
        val from = list.indexOfFirst { it.id == fromEntryId }
        val to = list.indexOfFirst { it.id == toEntryId }
        if (from < 0 || to < 0 || from == to) return
        list.add(to, list.removeAt(from))
        updateEntries(list.mapIndexed { index, entry -> entry.copy(position = index) })
    }

    /** Egy bejegyzés eltávolítása a listából (a fájlt nem érinti). */
    @Transaction
    suspend fun removeEntry(playlistId: Long, entryId: Long) {
        deleteEntry(entryId)
        val list = entries(playlistId)
        updateEntries(list.mapIndexed { index, entry -> entry.copy(position = index) })
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT track_id FROM favorites ORDER BY added_at DESC")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE track_id = :trackId")
    suspend fun remove(trackId: Long)
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position")
    suspend fun items(): List<QueueItemEntity>

    @Query("SELECT * FROM playback_state WHERE id = 0")
    suspend fun state(): PlaybackStateEntity?

    @Query("DELETE FROM queue_items")
    suspend fun clearItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<QueueItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: PlaybackStateEntity)

    /** A teljes várólista és az állapot mentése egy tranzakcióban. */
    @Transaction
    suspend fun saveAll(trackIds: List<Long>, state: PlaybackStateEntity) {
        clearItems()
        insertItems(trackIds.mapIndexed { index, id -> QueueItemEntity(index, id) })
        saveState(state)
    }
}
