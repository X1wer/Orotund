package io.github.x1wer.szabadzene

import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.x1wer.szabadzene.ui.PlaybackOrigin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A widget kérése (`EXTRA_OPEN_ORIGIN`) a lejátszás forrását nyitja meg, nem a lejátszót. */
@RunWith(AndroidJUnit4::class)
class WidgetOpensOriginTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as SzabadZeneApp).container

    @After
    fun reset() {
        runBlocking { container.settings.setPlaybackOrigin(PlaybackOrigin.LIBRARY.encode()) }
    }

    private fun launchFromWidget(): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ORIGIN, true),
    )

    @Test
    fun opensThePlaylistThatIsPlaying() {
        runBlocking { container.settings.setPlaybackOrigin(PlaybackOrigin.playlist(-1).encode()) }
        launchFromWidget().use {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("collection_menu").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("tab_playlists").assertIsSelected()
            compose.onNodeWithTag("collection_menu").assertExists()
        }
    }

    @Test
    fun opensTheLibraryWhenPlayingFromIt() {
        runBlocking { container.settings.setPlaybackOrigin(PlaybackOrigin.LIBRARY.encode()) }
        launchFromWidget().use {
            compose.waitForIdle()
            compose.onNodeWithTag("tab_library").assertIsSelected()
            compose.onNodeWithTag("collection_menu").assertDoesNotExist()
        }
    }
}
