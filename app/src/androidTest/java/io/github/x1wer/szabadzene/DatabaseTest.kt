package io.github.x1wer.szabadzene

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.x1wer.szabadzene.data.db.AppDatabase
import io.github.x1wer.szabadzene.data.db.FavoriteEntity
import io.github.x1wer.szabadzene.data.db.PlayStatsEntity
import io.github.x1wer.szabadzene.playback.QueueStore
import io.github.x1wer.szabadzene.playback.SavedQueue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Lejátszási listák, kedvencek és a várólista-mentés valódi (memóriabeli) Room-adatbázison. */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun playlistCreateAddReorderRemove() = runBlocking {
        val dao = db.playlistDao()
        val id = dao.createWithTracks("Reggeli", listOf(10, 20, 30), now = 1)
        dao.addTracks(id, listOf(40))
        var entries = dao.entries(id)
        assertEquals(listOf(10L, 20L, 30L, 40L), entries.map { it.trackId })
        assertEquals(listOf(0, 1, 2, 3), entries.map { it.position })

        // Az első bejegyzés a harmadik helyére kerül.
        dao.moveEntry(id, fromEntryId = entries[0].id, toEntryId = entries[2].id)
        entries = dao.entries(id)
        assertEquals(listOf(20L, 30L, 10L, 40L), entries.map { it.trackId })
        assertEquals(listOf(0, 1, 2, 3), entries.map { it.position })

        // Visszafelé is működik.
        dao.moveEntry(id, fromEntryId = entries[3].id, toEntryId = entries[0].id)
        entries = dao.entries(id)
        assertEquals(listOf(40L, 20L, 30L, 10L), entries.map { it.trackId })

        // Eltávolítás után a sorszámok folytonosak maradnak.
        dao.removeEntry(id, entries[1].id)
        entries = dao.entries(id)
        assertEquals(listOf(40L, 30L, 10L), entries.map { it.trackId })
        assertEquals(listOf(0, 1, 2), entries.map { it.position })

        val summary = dao.observePlaylists().first().single()
        assertEquals("Reggeli", summary.name)
        assertEquals(3, summary.entryCount)
    }

    @Test
    fun sameTrackCanBeInPlaylistTwice() = runBlocking {
        val dao = db.playlistDao()
        val id = dao.createWithTracks("Ismétlés", listOf(5, 5), now = 1)
        assertEquals(listOf(5L, 5L), dao.entries(id).map { it.trackId })
    }

    @Test
    fun renameAndDeleteCascades() = runBlocking {
        val dao = db.playlistDao()
        val id = dao.createWithTracks("Régi név", listOf(1, 2), now = 1)
        dao.rename(id, "Új név")
        assertEquals("Új név", dao.observePlaylist(id).first()?.name)
        dao.deletePlaylist(id)
        assertNull(dao.observePlaylist(id).first())
        assertTrue(dao.entries(id).isEmpty())
    }

    @Test
    fun favoritesIgnoreDuplicatesAndKeepNewestFirst() = runBlocking {
        val dao = db.favoriteDao()
        dao.insert(listOf(FavoriteEntity(1, 100), FavoriteEntity(2, 200)))
        dao.insert(listOf(FavoriteEntity(1, 300)))
        assertEquals(listOf(2L, 1L), dao.observeIds().first())
        dao.remove(2)
        assertEquals(listOf(1L), dao.observeIds().first())
    }

    @Test
    fun queueStateSurvivesSaveAndLoad() = runBlocking {
        val store = QueueStore(db.queueDao(), clock = { 42L })
        assertNull(store.load())
        val queue = SavedQueue(
            trackIds = listOf(7, 8, 9, 8),
            currentIndex = 2,
            positionMs = 61_000,
            repeatMode = 1,
            shuffleEnabled = true,
            shuffleOrder = listOf(3, 1, 2, 0),
        )
        store.save(queue)
        assertEquals(queue, store.load())

        // Rövidebb lista mentése nem hagy maga után régi elemeket.
        val shorter = queue.copy(trackIds = listOf(1), currentIndex = 0, shuffleOrder = listOf(0))
        store.save(shorter)
        assertEquals(shorter, store.load())
    }

    @Test
    fun playStatsCountManualEditHistoryAndReset() = runBlocking {
        val dao = db.playStatsDao()
        dao.recordPlay(1, at = 100)
        dao.recordPlay(1, at = 200)
        dao.recordPlay(2, at = 150)
        assertEquals(PlayStatsEntity(1, 2, 200), dao.get(1))

        // Kézi beállítás: a számláló változik, az utolsó lejátszás nem; új számnál nincs utolsó lejátszás.
        dao.setCount(1, 10)
        assertEquals(PlayStatsEntity(1, 10, 200), dao.get(1))
        dao.setCount(3, 4)
        assertEquals(PlayStatsEntity(3, 4, null), dao.get(3))
        dao.setCount(3, -5)
        assertEquals(0, dao.get(3)?.playCount)

        dao.clearHistory()
        assertEquals(listOf(null, null, null), dao.observeAll().first().map { it.lastPlayedAt })
        assertEquals(10, dao.get(1)?.playCount)
        dao.resetCounts()
        assertEquals(listOf(0, 0, 0), dao.observeAll().first().map { it.playCount })
        dao.delete(2)
        assertNull(dao.get(2))
    }
}

/** A tárolt adatbázis 1. verziójából a 2.-ba lépés (új táblák) adatvesztés nélkül. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsTracksAndAddsUserTables() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO tracks (id, title, artist, album, album_id, duration_ms, display_name, path,
                    relative_path, size_bytes, mime_type, date_added, genre, track_number, year)
                VALUES (5, 'Árvíztűrő', 'Kovács Éva', 'Album', 1, 1000, 'a.mp3', '/a.mp3', NULL, 10,
                    'audio/mpeg', 0, NULL, 1, 2024)
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true).use { db ->
            db.query("SELECT title FROM tracks WHERE id = 5").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Árvíztűrő", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM playlists").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate2To3KeepsUserDataAndAddsPlayStats() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL("INSERT INTO playlists (id, name, created_at) VALUES (1, 'Kedvenc lista', 5)")
            db.execSQL("INSERT INTO favorites (track_id, added_at) VALUES (9, 7)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 3, true).use { db ->
            db.query("SELECT name FROM playlists WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Kedvenc lista", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM favorites").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.execSQL("INSERT INTO play_stats (track_id, play_count, last_played_at) VALUES (9, 3, NULL)")
            db.query("SELECT play_count FROM play_stats WHERE track_id = 9").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
