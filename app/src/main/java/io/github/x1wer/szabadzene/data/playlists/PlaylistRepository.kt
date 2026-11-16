package io.github.x1wer.szabadzene.data.playlists

import io.github.x1wer.szabadzene.data.db.AppDatabase
import io.github.x1wer.szabadzene.data.db.FavoriteEntity
import io.github.x1wer.szabadzene.data.db.PlaylistEntity
import io.github.x1wer.szabadzene.data.db.PlaylistSummary
import io.github.x1wer.szabadzene.data.library.LibraryRepository
import io.github.x1wer.szabadzene.data.library.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Egy lejátszási lista eleme: a bejegyzés azonosítója (átrendezéshez, törléshez) és a szám. */
data class PlaylistItem(val entryId: Long, val track: Track)

/**
 * Lejátszási listák és kedvencek. A listák a felhasználó adatai: a zenetár újraolvasása nem törli
 * őket; a már nem elérhető (vagy kizárt mappában lévő) számok csak nem látszanak.
 */
class PlaylistRepository(
    database: AppDatabase,
    private val library: LibraryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val playlistDao = database.playlistDao()
    private val favoriteDao = database.favoriteDao()
    private val playStatsDao = database.playStatsDao()

    val playlists: Flow<List<PlaylistSummary>> = playlistDao.observePlaylists()

    val favoriteIds: Flow<Set<Long>> = favoriteDao.observeIds().map { it.toSet() }

    /** Kedvencek, a legutóbb hozzáadottal kezdve. */
    val favoriteTracks: Flow<List<Track>> = combine(favoriteDao.observeIds(), library.tracks) { ids, tracks ->
        val byId = tracks.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }

    fun playlist(id: Long): Flow<PlaylistEntity?> = playlistDao.observePlaylist(id)

    fun playlistItems(id: Long): Flow<List<PlaylistItem>> =
        combine(playlistDao.observeEntries(id), library.tracks) { entries, tracks ->
            val byId = tracks.associateBy { it.id }
            entries.mapNotNull { entry -> byId[entry.trackId]?.let { PlaylistItem(entry.id, it) } }
        }

    /** A lista pillanatnyi tartalma (pl. a listamenü megnyitásakor). */
    suspend fun playlistItemsOnce(id: Long): List<PlaylistItem> = playlistItems(id).first()

    suspend fun create(name: String, trackIds: List<Long> = emptyList()): Long =
        playlistDao.createWithTracks(name.trim(), trackIds, clock())

    suspend fun rename(id: Long, name: String) = playlistDao.rename(id, name.trim())

    suspend fun delete(id: Long) = playlistDao.deletePlaylist(id)

    suspend fun addTracks(playlistId: Long, trackIds: List<Long>) = playlistDao.addTracks(playlistId, trackIds)

    suspend fun removeEntry(playlistId: Long, entryId: Long) = playlistDao.removeEntry(playlistId, entryId)

    suspend fun moveEntry(playlistId: Long, fromEntryId: Long, toEntryId: Long) =
        playlistDao.moveEntry(playlistId, fromEntryId, toEntryId)

    suspend fun addFavorites(trackIds: List<Long>) {
        val now = clock()
        favoriteDao.insert(trackIds.mapIndexed { index, id -> FavoriteEntity(id, now + index) })
    }

    suspend fun removeFavorite(trackId: Long) = favoriteDao.remove(trackId)

    /** A fájl törlése után: a szám kikerül minden listából, a kedvencek közül és a statisztikából. */
    suspend fun forgetTrack(trackId: Long) {
        playlistDao.removeTrackEverywhere(trackId)
        favoriteDao.remove(trackId)
        playStatsDao.delete(trackId)
    }
}
