package io.github.x1wer.szabadzene

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.x1wer.szabadzene.data.library.Track
import io.github.x1wer.szabadzene.ui.LocalMenuState
import io.github.x1wer.szabadzene.ui.MenuState
import io.github.x1wer.szabadzene.ui.components.Icon
import io.github.x1wer.szabadzene.ui.components.TrackMenuTouchSize
import io.github.x1wer.szabadzene.ui.components.TrackMenuWidth
import io.github.x1wer.szabadzene.ui.components.TrackRow
import io.github.x1wer.szabadzene.ui.components.TrackRowTags
import io.github.x1wer.szabadzene.ui.library.TrackList
import io.github.x1wer.szabadzene.ui.library.TrackListTags
import io.github.x1wer.szabadzene.ui.library.TrackSelection
import io.github.x1wer.szabadzene.ui.theme.LocalIconScale
import io.github.x1wer.szabadzene.ui.theme.ProvideUiScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Több szám kijelölése a számlistában, a szélesebb menüterület, és a betű- és ikonméret beállítás. */
@RunWith(AndroidJUnit4::class)
class SelectionAndScaleTest {

    @get:Rule
    val compose = createComposeRule()

    private fun track(id: Long, title: String) = Track(
        id = id, title = title, artist = "Előadó", album = null, albumId = 0, durationMs = 60_000,
        displayName = "$title.mp3", path = null, relativePath = null, sizeBytes = 1, mimeType = "audio/mpeg",
        dateAddedSeconds = 0, genre = null, trackNumber = 0, year = 0,
    )

    private val tracks = listOf(track(1, "Első"), track(2, "Második"), track(3, "Harmadik"))

    @Test
    fun longPressStartsSelectionAndTapsToggle() {
        var active by mutableStateOf(false)
        var ids by mutableStateOf(emptySet<Long>())
        val menu = MenuState()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMenuState provides menu) {
                    TrackList(
                        tracks = tracks,
                        currentMediaId = null,
                        contentPadding = PaddingValues(),
                        selection = TrackSelection(
                            active = active,
                            selectedIds = ids,
                            onToggle = { ids = if (it.id in ids) ids - it.id else ids + it.id },
                            onStart = {
                                active = true
                                ids = setOf(it.id)
                            },
                        ),
                    )
                }
            }
        }
        compose.onAllNodesWithTag(TrackListTags.CHECKBOX).assertCountEquals(0)

        compose.onNodeWithText("Második").performTouchInput { longClick() }
        compose.waitForIdle()
        assertTrue(active)
        assertEquals(setOf(2L), ids)
        compose.onAllNodesWithTag(TrackListTags.CHECKBOX).assertCountEquals(3)

        // Koppintás kijelöl (nem indít lejátszást), újabb koppintás leveszi a jelölést.
        compose.onNodeWithText("Első").performClick()
        compose.onNodeWithText("Harmadik").performClick()
        compose.onNodeWithText("Második").performClick()
        compose.waitForIdle()
        assertEquals(setOf(1L, 3L), ids)
        compose.onAllNodesWithTag(TrackRowTags.BODY)[0].assertIsSelected()
        compose.onAllNodesWithTag(TrackRowTags.BODY)[1].assertIsNotSelected()

        // A jelölőnégyzet ugyanígy működik.
        compose.onAllNodesWithTag(TrackListTags.CHECKBOX)[1].performClick()
        compose.waitForIdle()
        assertEquals(setOf(1L, 2L, 3L), ids)

        // A menüterület kijelölő módban is csak a menüt nyitja meg.
        compose.onAllNodesWithTag(TrackRowTags.MENU)[0].performClick()
        compose.waitForIdle()
        assertEquals(tracks[0], menu.openTrack)
        assertEquals(setOf(1L, 2L, 3L), ids)
    }

    @Test
    fun menuAreaIsWiderAndKeepsMinimumWithLargeIconsAndText() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                MaterialTheme {
                    CompositionLocalProvider(LocalIconScale provides 1.5f) {
                        TrackRow(
                            title = "Nagyon hosszú cím, amely biztosan nem fér el egy sorban: árvíztűrő tükörfúrógép",
                            subtitle = "Kovács Éva",
                            trackId = null,
                            isCurrent = true,
                            onPlay = {},
                            onMenu = {},
                            modifier = Modifier.width(320.dp),
                        )
                    }
                }
            }
        }
        val menu = compose.onNodeWithTag(TrackRowTags.MENU)
        menu.assertWidthIsEqualTo(TrackMenuWidth)
        menu.assertWidthIsAtLeast(TrackMenuTouchSize).assertHeightIsAtLeast(TrackMenuTouchSize)
        val body = compose.onNodeWithTag(TrackRowTags.BODY).getBoundsInRoot()
        assertTrue("A törzs és a menü nem fedheti egymást", body.right <= menu.getBoundsInRoot().left)
    }

    @Test
    fun uiScaleEnlargesTextAndIcons() {
        var baseFontScale = 0f
        var scaledFontScale = 0f
        compose.setContent {
            MaterialTheme {
                baseFontScale = LocalDensity.current.fontScale
                ProvideUiScale(textScalePercent = 130, iconScalePercent = 150) {
                    scaledFontScale = LocalDensity.current.fontScale
                    Column {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.testTag("icon_default"))
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.testTag("icon_18"), size = 18.dp)
                        Text("Árvíztűrő")
                    }
                }
            }
        }
        compose.onNodeWithTag("icon_default").assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
        compose.onNodeWithTag("icon_18").assertWidthIsEqualTo(27.dp)
        assertEquals((baseFontScale * 1.3f).coerceAtMost(2f), scaledFontScale, 0.01f)
    }
}
