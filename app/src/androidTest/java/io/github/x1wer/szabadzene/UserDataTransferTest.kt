package io.github.x1wer.szabadzene

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A Kedvencek és a lejátszásszámok exportja, majd visszaimportálása a valódi zenetárral. */
@RunWith(AndroidJUnit4::class)
class UserDataTransferTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val container get() = (context.applicationContext as SzabadZeneApp).container

    @Before
    fun grantPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        perms.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
    }

    private fun file(name: String): Pair<File, Uri> {
        val f = File(context.cacheDir, "${System.nanoTime()}_$name").apply { createNewFile() }
        return f to Uri.fromFile(f)
    }

    private suspend fun uniqueTracks() = run {
        container.library.start()
        container.library.refresh().join()
        val tracks = withTimeout(30_000) { container.library.allTracks.first { it.isNotEmpty() } }
        tracks.groupBy { it.title.lowercase() }.values.filter { it.size == 1 }.map { it.single() }
    }

    @Test
    fun favoritesExportAndImportBack() = runBlocking {
        val picked = uniqueTracks().take(2)
        assertTrue("legalább 2 egyedi című szám kell", picked.size == 2)
        picked.forEach { container.playlists.removeFavorite(it.id) }
        container.playlists.addFavorites(picked.map { it.id })
        val favorites = withTimeout(10_000) { container.playlists.favoriteTracks.first { list -> picked.all { p -> list.any { it.id == p.id } } } }

        val (out, uri) = file("Kedvencek.m3u8")
        val written = container.playlistExporter.exportSingle(uri, "Kedvencek", favorites)
        assertEquals(favorites.size, written)
        assertTrue(out.readText().startsWith("#EXTM3U"))

        picked.forEach { container.playlists.removeFavorite(it.id) }
        val outcome = container.playlistImporter.importFavorites(uri, "Kedvencek")
        assertEquals(favorites.size, outcome.total)
        val ids = container.playlists.favoriteIds.first()
        assertTrue(picked.all { it.id in ids })
        picked.forEach { container.playlists.removeFavorite(it.id) }
    }

    @Test
    fun playCountsExportAndImportBack() = runBlocking {
        val track = uniqueTracks().first()
        container.playStats.setPlayCount(track.id, 17)
        val (out, uri) = file("lejatszasszamok.csv")
        val rows = container.playCountImporter.export(uri)
        assertTrue(rows >= 1)
        assertTrue(out.readText().lineSequence().any { it.startsWith("17;") && it.contains(track.title) })

        container.playStats.setPlayCount(track.id, 0)
        val report = container.playCountImporter.import(uri)
        assertTrue(report.updated >= 1)
        assertEquals(17, container.playStats.stats.first().getValue(track.id).playCount)
        container.playStats.setPlayCount(track.id, 0)
    }
}
