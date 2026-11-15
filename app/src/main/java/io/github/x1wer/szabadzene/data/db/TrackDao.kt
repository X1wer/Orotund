package io.github.x1wer.szabadzene.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    /** Legfeljebb 900 azonosítóval hívd (régi SQLite-változó-korlát). */
    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<TrackEntity>

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    /** Egyetlen tranzakcióban cseréli a teljes gyorsítótárat, így a lista nem villan üresre. */
    @Transaction
    suspend fun replaceAll(tracks: List<TrackEntity>) {
        deleteAll()
        insertAll(tracks)
    }
}
