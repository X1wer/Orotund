package io.github.x1wer.szabadzene

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.x1wer.szabadzene.data.settings.SkipSettings
import io.github.x1wer.szabadzene.playback.PlaybackUiState
import io.github.x1wer.szabadzene.playback.SkipDirection
import io.github.x1wer.szabadzene.ui.components.SkipButtonMinSize
import io.github.x1wer.szabadzene.ui.components.SkipButtonTags
import io.github.x1wer.szabadzene.ui.player.FullPlayerContent
import io.github.x1wer.szabadzene.ui.player.MiniPlayerContent
import io.github.x1wer.szabadzene.ui.player.MiniPlayerTags
import io.github.x1wer.szabadzene.ui.player.PlayerCallbacks
import io.github.x1wer.szabadzene.ui.player.PlayerTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Az állandó ugrógombok láthatósága, mérete és elrendezése a teljes és a minilejátszóban. */
@RunWith(AndroidJUnit4::class)
class PlayerLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val playing = PlaybackUiState(
        connected = true,
        mediaId = "1",
        title = "Árvíztűrő tükörfúrógép – egy nagyon hosszú cím, ami két sorba sem fér el biztosan",
        artist = "Kovács Éva és a Hosszú Nevű Zenekar",
        album = "Teszt album",
        isPlaying = true,
        playWhenReady = true,
        positionMs = 30_000,
        durationMs = 240_000,
        canSeek = true,
        hasPrevious = true,
        hasNext = true,
    )
    private val paused = playing.copy(isPlaying = false, playWhenReady = false)

    private val skips = mutableListOf<SkipDirection>()
    private var opens = 0
    private var seeks = 0

    private val callbacks = PlayerCallbacks(
        onSkip = { skips += it },
        onSeek = { seeks++ },
    )

    private fun SemanticsNodeInteraction.scrollIntoViewIfPossible(): SemanticsNodeInteraction =
        try {
            performScrollTo()
        } catch (_: AssertionError) {
            this
        }

    private fun overlaps(a: DpRect, b: DpRect) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun assertSkipButtonsUsable() {
        listOf(SkipButtonTags.BACK, SkipButtonTags.FORWARD).forEach { tag ->
            compose.onNodeWithTag(tag)
                .scrollIntoViewIfPossible()
                .assertIsDisplayed()
                .assertWidthIsAtLeast(SkipButtonMinSize)
                .assertHeightIsAtLeast(SkipButtonMinSize)
        }
    }

    private fun assertNoOverlap(tags: List<String>) {
        val bounds = tags.associateWith { compose.onNodeWithTag(it).getBoundsInRoot() }
        for (i in tags.indices) {
            for (j in i + 1 until tags.size) {
                assertFalse(
                    "${tags[i]} és ${tags[j]} fedi egymást: ${bounds[tags[i]]} / ${bounds[tags[j]]}",
                    overlaps(bounds.getValue(tags[i]), bounds.getValue(tags[j])),
                )
            }
        }
    }

    private val controlTags = listOf(
        SkipButtonTags.BACK,
        SkipButtonTags.FORWARD,
        PlayerTags.PREVIOUS,
        PlayerTags.PLAY_PAUSE,
        PlayerTags.NEXT,
    )

    // --- Teljes lejátszó ---

    @Test
    fun skipButtonsAreVisibleWhilePlayingAndPausedAndAfterSeeking() {
        var state by mutableStateOf(playing)
        compose.setContent {
            MaterialTheme {
                FullPlayerContent(state, SkipSettings(), callbacks, onMinimize = {}, onMoreActions = null)
            }
        }
        assertSkipButtonsUsable()
        state = paused
        compose.waitForIdle()
        assertSkipButtonsUsable()
        // Tekerés a csúszkán (lenyomás, húzás, felengedés) – utána is ott vannak a gombok.
        val seekBar = compose.onNodeWithTag(PlayerTags.SEEK_BAR)
        seekBar.performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(width / 20f, 0f)) }
            up()
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertSkipButtonsUsable()
        seekBar.performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(-width / 20f, 0f)) }
            up()
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertSkipButtonsUsable()
        assertEquals(2, seeks)
    }

    @Test
    fun skipButtonsShowConfiguredValuesPerDirection() {
        compose.setContent {
            MaterialTheme {
                FullPlayerContent(
                    playing,
                    SkipSettings(backSeconds = 5, forwardSeconds = 30),
                    callbacks,
                    onMinimize = {},
                    onMoreActions = null,
                )
            }
        }
        compose.onNodeWithTag(SkipButtonTags.BACK)
            .assertTextContains(context.getString(R.string.skip_back_label, 5))
        compose.onNodeWithTag(SkipButtonTags.FORWARD)
            .assertTextContains(context.getString(R.string.skip_forward_label, 30))
    }

    @Test
    fun skipButtonsUseSharedActionWithDirection() {
        compose.setContent {
            MaterialTheme {
                FullPlayerContent(playing, SkipSettings(), callbacks, onMinimize = {}, onMoreActions = null)
            }
        }
        compose.onNodeWithTag(SkipButtonTags.BACK).performClick()
        compose.onNodeWithTag(SkipButtonTags.FORWARD).performClick()
        compose.onNodeWithTag(SkipButtonTags.FORWARD).performClick()
        compose.waitForIdle()
        assertEquals(listOf(SkipDirection.BACK, SkipDirection.FORWARD, SkipDirection.FORWARD), skips)
    }

    @Test
    fun skipButtonsStayVisibleButDisabledWhenNotSeekable() {
        compose.setContent {
            MaterialTheme {
                FullPlayerContent(
                    playing.copy(canSeek = false, durationMs = 0),
                    SkipSettings(),
                    callbacks,
                    onMinimize = {},
                    onMoreActions = null,
                )
            }
        }
        compose.onNodeWithTag(SkipButtonTags.BACK).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag(SkipButtonTags.FORWARD).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun smallLandscapeWithLargeFontKeepsControlsSeparate() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(568.dp, 320.dp)) then
                    DeviceConfigurationOverride.FontScale(2f),
            ) {
                MaterialTheme {
                    FullPlayerContent(playing, SkipSettings(), callbacks, onMinimize = {}, onMoreActions = {})
                }
            }
        }
        // Görgetés nélkül is látszik minden fő kezelőszerv.
        controlTags.forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        assertSkipButtonsUsable()
        assertNoOverlap(controlTags)
        compose.onNodeWithTag(SkipButtonTags.FORWARD).assertIsEnabled()
    }

    @Test
    fun smallPortraitWithLargeFontKeepsControlsSeparate() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
                    DeviceConfigurationOverride.FontScale(2f),
            ) {
                MaterialTheme {
                    FullPlayerContent(paused, SkipSettings(), callbacks, onMinimize = {}, onMoreActions = {})
                }
            }
        }
        // Görgetés nélkül is látszik minden fő kezelőszerv (a borító adja át a helyet).
        controlTags.forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        assertSkipButtonsUsable()
        assertNoOverlap(controlTags)
    }

    @Test
    fun normalPhonePortraitShowsAllControlsWithoutScrolling() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 780.dp))) {
                MaterialTheme {
                    FullPlayerContent(playing, SkipSettings(), callbacks, onMinimize = {}, onMoreActions = {})
                }
            }
        }
        controlTags.forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        assertNoOverlap(controlTags)
    }

    // --- Minilejátszó ---

    @Test
    fun miniPlayerSingleRowHasDirectSkipButtons() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(411.dp, 200.dp))) {
                MaterialTheme {
                    MiniPlayerContent(playing, SkipSettings(), callbacks, onOpen = { opens++ })
                }
            }
        }
        assertSkipButtonsUsable()
        assertNoOverlap(listOf(SkipButtonTags.BACK, SkipButtonTags.FORWARD, PlayerTags.PLAY_PAUSE, MiniPlayerTags.BODY))
        compose.onNodeWithTag(SkipButtonTags.FORWARD).performClick()
        compose.onNodeWithTag(SkipButtonTags.BACK).performClick()
        compose.waitForIdle()
        assertEquals(listOf(SkipDirection.FORWARD, SkipDirection.BACK), skips)
        assertEquals("Az ugrógomb nem nyithatja meg a teljes lejátszót", 0, opens)
        compose.onNodeWithTag(MiniPlayerTags.BODY).performClick()
        compose.waitForIdle()
        assertEquals(1, opens)
    }

    @Test
    fun miniPlayerUsesTwoRowsOnNarrowScreenWithLargeFont() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 400.dp)) then
                    DeviceConfigurationOverride.FontScale(2f),
            ) {
                MaterialTheme {
                    MiniPlayerContent(paused, SkipSettings(), callbacks, onOpen = { opens++ })
                }
            }
        }
        assertSkipButtonsUsable()
        assertNoOverlap(listOf(SkipButtonTags.BACK, SkipButtonTags.FORWARD, PlayerTags.PLAY_PAUSE, MiniPlayerTags.BODY))
        compose.onNodeWithTag(SkipButtonTags.BACK)
            .assertTextContains(context.getString(R.string.skip_back_label, 10))
        compose.onNodeWithTag(SkipButtonTags.FORWARD).performClick()
        compose.waitForIdle()
        assertEquals(listOf(SkipDirection.FORWARD), skips)
        assertEquals(0, opens)
    }
}
