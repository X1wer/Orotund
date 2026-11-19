package io.github.x1wer.szabadzene

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.x1wer.szabadzene.data.db.AppDatabase
import io.github.x1wer.szabadzene.data.db.FavoriteEntity
import io.github.x1wer.szabadzene.integration.AlarmBridge
import io.github.x1wer.szabadzene.integration.AlarmPlaylistProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException

/** Az ébresztőalkalmazásoknak szóló, csak olvasható lejátszásilista-híd. */
@RunWith(AndroidJUnit4::class)
class AlarmPlaylistProviderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority = context.packageName + AlarmPlaylistProvider.AUTHORITY_SUFFIX
    private fun uri(vararg path: String): Uri =
        path.fold(Uri.Builder().scheme("content").authority(authority)) { b, p -> b.appendPath(p) }.build()

    @Test
    fun newDaoQueries() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.playlistDao().createWithTracks("B lista", listOf(1, 2), now = 1)
            db.playlistDao().createWithTracks("a lista", listOf(3), now = 2)
            assertEquals(listOf("a lista" to 1, "B lista" to 2), db.playlistDao().playlists().map { it.name to it.entryCount })
            db.favoriteDao().insert(listOf(FavoriteEntity(5, addedAt = 10), FavoriteEntity(6, addedAt = 20)))
            assertEquals(listOf(6L, 5L), db.favoriteDao().ids())
            assertTrue(db.favoriteDao().contains(5))
            assertFalse(db.favoriteDao().contains(7))
        } finally {
            db.close()
        }
    }

    @Test
    fun providerListsPlaylistsWithFavoritesFirst() = runBlocking {
        val dao = (context.applicationContext as SzabadZeneApp).container.database.playlistDao()
        val id = dao.createWithTracks("Ébresztő-teszt", listOf(987_654_321L), now = 1)
        try {
            val rows = context.contentResolver.query(uri("playlists"), null, null, null, null)!!.use { c ->
                generateSequence { if (c.moveToNext()) Triple(c.getLong(0), c.getString(1), c.getInt(3)) else null }.toList()
            }
            assertEquals(AlarmBridge.FAVORITES_ID to 1, rows.first().let { it.first to it.third })
            assertTrue(rows.any { it.first == id && it.second == "Ébresztő-teszt" && it.third == 0 })

            // A zenetárban nem létező szám kimarad a listából.
            val tracks = context.contentResolver.query(uri("playlists", id.toString(), "tracks"), null, null, null, null)!!
            tracks.use { assertEquals(0, it.count) }

            val mode = context.contentResolver.query(uri("playback_mode"), null, null, null, null)!!
            mode.use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getString(1) in setOf("ALL", "ONE", "OFF", "STOP_AFTER_CURRENT"))
                assertEquals(AlarmBridge.API_VERSION, it.getInt(2))
            }
        } finally {
            dao.deletePlaylist(id)
        }
    }

    @Test
    fun audioOfTracksOutsidePlaylistsIsNotServed() {
        try {
            context.contentResolver.openFileDescriptor(uri("audio", "123456789"), "r")
            fail("expected FileNotFoundException")
        } catch (expected: FileNotFoundException) {
            // ok
        }
    }
}
