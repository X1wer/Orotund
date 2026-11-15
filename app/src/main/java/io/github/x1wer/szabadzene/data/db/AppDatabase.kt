package io.github.x1wer.szabadzene.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/*
 * Verziók:
 * 1 – zenetár-gyorsítótár (tracks)
 * 2 – lejátszási listák, kedvencek, a várólista mentett állapota (automatikus migráció)
 */
@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        FavoriteEntity::class,
        QueueItemEntity::class,
        PlaybackStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun queueDao(): QueueDao

    companion object {
        const val NAME = "szabadzene.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .build()
    }
}
