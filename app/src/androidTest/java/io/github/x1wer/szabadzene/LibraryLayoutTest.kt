package io.github.x1wer.szabadzene

import android.Manifest
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.x1wer.szabadzene.data.settings.LibrarySection
import io.github.x1wer.szabadzene.data.settings.PlayButtonMode
import io.github.x1wer.szabadzene.playback.RepeatSetting
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import kotlin.math.abs

/**
 * A Zenetár 1.3.0-s viselkedése a valódi appban (a telefonon lévő tesztszámokkal): eltűnő fejléc,
 * választható fülek, a keresőmező melletti Lejátszás/Keverés ikon és a „mostani szám végigszól” mód,
 * valamint a listaexport kiválasztó ablaka.
 */
@RunWith(AndroidJUnit4::class)
class LibraryLayoutTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as SzabadZeneApp).container

    /** Az engedélyt még az Activity indulása előtt megadjuk, hogy a zenetár azonnal betöltsön. */
    @get:Rule(order = 0)
    val permissions = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, perm)
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun restore() {
        // A MediaController csak a fő szálról hívható.
        instrumentation.runOnMainSync {
            container.player.setRepeat(RepeatSetting.OFF)
            container.player.pause()
        }
        runBlocking {
            container.settings.setLibrarySections(LibrarySection.entries)
            container.settings.setPlayButtonMode(PlayButtonMode.RESTART)
        }
    }

    private fun waitForTracks() {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("search_open").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun dp(value: Float): Float = value * instrumentation.targetContext.resources.displayMetrics.density

    @Test
    fun titleBarHidesWhileScrollingDownAndReturnsAtTop() {
        waitForTracks()
        val bar = compose.onNodeWithTag("library_top_bar")
        val full = bar.bounds().height
        // A lista alja a navigációs sáv mögé fut: a húzás a lista közepén történik.
        compose.onNodeWithTag("track_list").performTouchInput {
            swipe(Offset(centerX, height * 0.6f), Offset(centerX, height * 0.2f), durationMillis = 400)
        }
        compose.waitForIdle()
        val collapsed = bar.bounds().height
        assertTrue("A fejléc nem tűnt el: $full → $collapsed", collapsed < full - dp(48f))

        repeat(4) {
            compose.onNodeWithTag("track_list").performTouchInput {
                swipe(Offset(centerX, height * 0.3f), Offset(centerX, height * 0.8f), durationMillis = 300)
            }
        }
        compose.waitForIdle()
        assertEquals(full, bar.bounds().height, 1f)
    }

    @Test
    fun singleVisibleTabHidesTheTabRow() {
        waitForTracks()
        compose.onNodeWithTag("library_tab_row").assertExists()
        compose.onNodeWithTag("library_more").performClick()
        compose.onNodeWithTag("library_tabs_open").performClick()
        listOf("tracks", "artists", "genres").forEach { compose.onNodeWithTag("library_tab_option_$it").performClick() }
        // Az utolsó bejelölt fület nem lehet kikapcsolni.
        compose.onNodeWithTag("library_tab_option_albums").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("library_tabs_save").performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithTag("library_tab_row").fetchSemanticsNodes().isEmpty() }
        // A lapozó a többi fület is komponálva tartja (a Mappák fülnek is van „group_list”-je): a látható kell.
        assertTrue(compose.onAllNodesWithTag("group_list").fetchSemanticsNodes().any { it.boundsInRoot.left == 0f })
        compose.onNodeWithTag("search_open_top").assertExists()
        compose.onNodeWithTag("track_list").assertDoesNotExist()
    }

    @Test
    fun playAndShuffleIconsSitNextToTheSearchField() {
        waitForTracks()
        val search = compose.onNodeWithTag("search_open").bounds()
        val play = compose.onNodeWithTag("play_all").bounds()
        val shuffle = compose.onNodeWithTag("shuffle_all").bounds()
        assertTrue(play.left >= search.right && shuffle.left >= play.right)
        assertTrue(abs(play.center.y - search.center.y) < dp(4f) && abs(shuffle.center.y - search.center.y) < dp(4f))
        assertTrue(play.width >= dp(47.5f) && shuffle.height >= dp(47.5f))
        // A kijelölés és a rendezés a fejlécbe került.
        compose.onNodeWithTag("select_mode").assertExists()
        compose.onNodeWithTag("sort_open").assertExists()
    }

    @Test
    fun afterCurrentModeKeepsTheCurrentSongPlaying() {
        runBlocking { container.settings.setPlayButtonMode(PlayButtonMode.AFTER_CURRENT) }
        waitForTracks()
        val state = container.player.state

        // Nem szól zene: a Lejátszás azonnal indul, sorrendben.
        compose.onNodeWithTag("play_all").performClick()
        compose.waitUntil(15_000) { state.value.hasMedia && state.value.playWhenReady && state.value.queueSize > 1 }
        // A rövid tesztszámok alatt se lépjen tovább magától.
        instrumentation.runOnMainSync { container.player.setRepeat(RepeatSetting.ONE) }
        compose.waitUntil(5_000) { state.value.repeat == RepeatSetting.ONE }
        val current = state.value.mediaId
        val size = state.value.queueSize
        val inOrder = container.player.queue.value.map { it.mediaId }

        // Szól zene: a Keverés nem szakítja meg a mostani számot, utána keverve folytatódik.
        compose.onNodeWithTag("shuffle_all").performClick()
        compose.waitUntil(10_000) { state.value.shuffle }
        assertEquals(current, state.value.mediaId)
        assertEquals(size, state.value.queueSize)
        assertTrue(state.value.playWhenReady)
        compose.waitUntil(5_000) { container.player.queue.value.firstOrNull()?.mediaId == current }

        // A Lejátszás visszavált sorrendre: a mostani szám marad, utána a lista következő száma jön.
        compose.onNodeWithTag("play_all").performClick()
        compose.waitUntil(10_000) { !state.value.shuffle }
        assertEquals(current, state.value.mediaId)
        compose.waitUntil(5_000) { container.player.queue.value.map { it.mediaId } == inOrder }
    }

    @Test
    fun importMenuOffersFavoritesAndPlayCounts() {
        compose.onNodeWithTag("tab_playlists").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("playlist_import").performClick()
        compose.onNodeWithTag("playlist_import_files").assertExists()
        compose.onNodeWithTag("playlist_import_folder").assertExists()
        compose.onNodeWithTag("import_favorites").assertExists()
        compose.onNodeWithTag("import_counts").assertExists()
    }

    @Test
    fun exportDialogSelectsPlaylists() {
        compose.onNodeWithTag("tab_playlists").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("playlist_export").performClick()
        // Az exportgomb menüt nyit: listák, Kedvencek, lejátszásszámok.
        compose.onNodeWithTag("export_favorites").assertExists()
        compose.onNodeWithTag("export_counts").assertExists()
        compose.onNodeWithTag("export_lists").performClick()
        compose.onNodeWithTag("playlist_export_dialog").assertExists()
        // Alapból a saját listák vannak kijelölve, a Kedvencek nem.
        compose.onNodeWithTag("export_option_-1").assertIsOff()
        compose.onNodeWithTag("export_overwrite").assertIsOff()

        compose.onNodeWithTag("export_all").performClick()
        compose.onNodeWithTag("export_option_-1").assertIsOn()
        compose.onNodeWithTag("export_option_-3").assertIsOn()
        compose.onNodeWithTag("export_choose_folder").assertIsEnabled()

        compose.onNodeWithTag("export_all").performClick()
        compose.onNodeWithTag("export_option_-1").assertIsOff()
        compose.onNodeWithTag("export_choose_folder").assertIsNotEnabled()

        compose.onNodeWithTag("export_option_-2").performClick()
        compose.onNodeWithTag("export_option_-2").assertIsOn()
        compose.onNodeWithTag("export_choose_folder").assertIsEnabled()
    }
}
