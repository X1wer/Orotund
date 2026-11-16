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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Lejátszásszámok importálása a valódi zenetárral: párosítás, „csak növel”, jelentés. */
@RunWith(AndroidJUnit4::class)
class PlayCountImportInstrumentedTest {

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

    private fun csv(vararg lines: String): Uri {
        val file = File(context.cacheDir, "counts_${System.nanoTime()}.csv")
        file.writeText("count;duration;title;artist\n" + lines.joinToString("\n") + "\n", Charsets.UTF_8)
        return Uri.fromFile(file)
    }

    @Test
    fun importRaisesCountsOnlyAndReportsMissingRows() = runBlocking {
        container.library.start()
        container.library.refresh().join()
        val tracks = withTimeout(30_000) { container.library.allTracks.first { it.isNotEmpty() } }
        val unique = tracks.groupBy { it.title.lowercase() }.values.first { it.size == 1 }.single()
        val seconds = (unique.durationMs / 1000).toInt()
        val duration = "%02d:%02d".format(seconds / 60, seconds % 60)
        val uri = csv("5;$duration;${unique.title};", "9;01:00;Ilyen szám biztosan nincs;")

        container.playStats.setPlayCount(unique.id, 2)
        val first = container.playCountImporter.import(uri)
        assertEquals(2, first.rows)
        assertEquals(1, first.updated)
        assertEquals(0, first.unchanged)
        assertEquals(1, first.missing)
        assertEquals(5, container.playStats.stats.first().getValue(unique.id).playCount)

        // Újra importálva nincs változás, és a nagyobb meglévő érték megmarad.
        val second = container.playCountImporter.import(uri)
        assertEquals(0, second.updated)
        assertEquals(1, second.unchanged)
        container.playStats.setPlayCount(unique.id, 50)
        container.playCountImporter.import(uri)
        assertEquals(50, container.playStats.stats.first().getValue(unique.id).playCount)
        container.playStats.setPlayCount(unique.id, 0)
    }
}
