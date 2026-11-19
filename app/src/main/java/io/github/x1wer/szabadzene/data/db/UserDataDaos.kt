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
        SELECT p.id AS id, p.name AS name, p.created_at AS created_at,
               (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlist_id = p.id) AS entry_count
        FROM playlists p
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.created_at AS created_at,
               (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlist_id = p.id) AS entry_count
        FROM playlists p
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    suspend fun playlists(): List<PlaylistSummary>

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

    @Query("SELECT DISTINCT playlist_id FROM playlist_entries WHERE track_id = :trackId")
    suspend fun playlistsContaining(trackId: Long): List<Long>

    @Query("DELETE FROM playlist_entries WHERE track_id = :trackId")
    suspend fun deleteEntriesForTrack(trackId: Long)

    /**
     * A fájl törlése után: a szám minden előfordulása kikerül minden listából, és az érintett
     * listák sorszámai folytonosak maradnak.
     */
    @Transaction
    suspend fun removeTrackEverywhere(trackId: Long) {
        val affected = playlistsContaining(trackId)
        deleteEntriesForTrack(trackId)
        for (playlistId in affected) {
            updateEntries(entries(playlistId).mapIndexed { index, entry -> entry.copy(position = index) })
        }
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

    @Query("SELECT track_id FROM favorites ORDER BY added_at DESC")
    suspend fun ids(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE track_id = :trackId)")
    suspend fun contains(trackId: Long): Boolean

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

@Dao
interface PlayStatsDao {
    @Query("SELECT * FROM play_stats")
    fun observeAll(): Flow<List<PlayStatsEntity>>

    @Query("SELECT * FROM play_stats WHERE track_id = :trackId")
    suspend fun get(trackId: Long): PlayStatsEntity?

    /** -1-et ad vissza, ha a szám már szerepel (ilyenkor frissíteni kell). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(stats: PlayStatsEntity): Long

    @Query("UPDATE play_stats SET play_count = play_count + 1, last_played_at = :at WHERE track_id = :trackId")
    suspend fun increment(trackId: Long, at: Long)

    @Query("UPDATE play_stats SET play_count = :count WHERE track_id = :trackId")
    suspend fun updateCount(trackId: Long, count: Int)

    /** Egy beszámított lejátszás: a számláló nő, és ez lesz az utolsó lejátszás ideje. */
    @Transaction
    suspend fun recordPlay(trackId: Long, at: Long) {
        if (insertIgnore(PlayStatsEntity(trackId, playCount = 1, lastPlayedAt = at)) == -1L) increment(trackId, at)
    }

    /** A számláló kézi beállítása; az utolsó lejátszás ideje nem változik. */
    @Transaction
    suspend fun setCount(trackId: Long, count: Int) {
        val value = count.coerceAtLeast(0)
        if (insertIgnore(PlayStatsEntity(trackId, playCount = value, lastPlayedAt = null)) == -1L) {
            updateCount(trackId, value)
        }
    }

    /** A „Nemrég játszott” lista ürítése (a számlálók megmaradnak). */
    @Query("UPDATE play_stats SET last_played_at = NULL")
    suspend fun clearHistory()

    /** Minden számláló nullázása (az előzmények megmaradnak). */
    @Query("UPDATE play_stats SET play_count = 0")
    suspend fun resetCounts()

    @Query("DELETE FROM play_stats WHERE track_id = :trackId")
    suspend fun delete(trackId: Long)
}
