package io.github.x1wer.szabadzene

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.x1wer.szabadzene.ui.components.TrackMenuTouchSize
import io.github.x1wer.szabadzene.ui.components.TrackRow
import io.github.x1wer.szabadzene.ui.components.TrackRowTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A számsor menüterületének mérete és a kattintások szétválasztása. */
@RunWith(AndroidJUnit4::class)
class TrackRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val plays = mutableIntStateOf(0)
    private val menus = mutableIntStateOf(0)

    private fun setRow(
        title: String = "Árvíztűrő tükörfúrógép",
        fontScale: Float = 1f,
        widthDp: Int = 360,
    ) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                MaterialTheme {
                    TrackRow(
                        title = title,
                        subtitle = "Kovács Éva",
                        trackId = null,
                        isCurrent = false,
                        onPlay = { plays.intValue++ },
                        onMenu = { menus.intValue++ },
                        modifier = Modifier.width(widthDp.dp),
                    )
                }
            }
        }
    }

    @Test
    fun menuAreaIsAtLeast64dp() {
        setRow()
        compose.onNodeWithTag(TrackRowTags.MENU)
            .assertWidthIsAtLeast(TrackMenuTouchSize)
            .assertHeightIsAtLeast(TrackMenuTouchSize)
    }

    @Test
    fun tapsAnywhereInMenuAreaOpenMenuOnly() {
        setRow()
        val menu = compose.onNodeWithTag(TrackRowTags.MENU)
        // Közép, négy sarok és négy él közepe – az ikon körüli üres rész is.
        val points: List<(Float, Float) -> Offset> = listOf(
            { w, h -> Offset(w / 2, h / 2) },
            { _, _ -> Offset(2f, 2f) },
            { w, _ -> Offset(w - 3f, 2f) },
            { _, h -> Offset(2f, h - 3f) },
            { w, h -> Offset(w - 3f, h - 3f) },
            { _, h -> Offset(2f, h / 2) },
            { w, h -> Offset(w - 3f, h / 2) },
            { w, _ -> Offset(w / 2, 2f) },
            { w, h -> Offset(w / 2, h - 3f) },
        )
        points.forEach { point ->
            menu.performTouchInput { click(point(width.toFloat(), height.toFloat())) }
            compose.waitForIdle()
        }
        assertEquals(points.size, menus.intValue)
        assertEquals("A menü megnyitása nem indíthat lejátszást", 0, plays.intValue)
    }

    @Test
    fun tapOnRowBodyPlaysAndDoesNotOpenMenu() {
        setRow()
        compose.onNodeWithText("Árvíztűrő tükörfúrógép").performClick()
        compose.onNodeWithTag(TrackRowTags.BODY).performTouchInput { click(Offset(4f, height / 2f)) }
        compose.waitForIdle()
        assertEquals(2, plays.intValue)
        assertEquals(0, menus.intValue)
    }

    @Test
    fun longTitleAndLargeFontKeepMenuArea() {
        val longTitle = "Nagyon hosszú cím, amely biztosan nem fér el egy sorban, " +
            "és ékezeteket is tartalmaz: árvíztűrő tükörfúrógép – ÁRVÍZTŰRŐ TÜKÖRFÚRÓGÉP"
        setRow(title = longTitle, fontScale = 2f, widthDp = 320)
        val menu = compose.onNodeWithTag(TrackRowTags.MENU)
        menu.assertWidthIsAtLeast(TrackMenuTouchSize).assertHeightIsAtLeast(TrackMenuTouchSize)

        // A szöveges rész nem lóg bele a menüterületbe.
        val body = compose.onNodeWithTag(TrackRowTags.BODY).getBoundsInRoot()
        val menuBounds = menu.getBoundsInRoot()
        assertTrue("A törzs és a menü nem fedheti egymást", body.right <= menuBounds.left)

        // A menüterület a teljes sormagasságot kitölti.
        val row = compose.onNodeWithTag(TrackRowTags.ROW).getBoundsInRoot()
        assertEquals(row.top.value, menuBounds.top.value, 0.5f)
        assertEquals(row.bottom.value, menuBounds.bottom.value, 0.5f)

        menu.performTouchInput { click(Offset(3f, height - 4f)) }
        compose.waitForIdle()
        assertEquals(1, menus.intValue)
        assertEquals(0, plays.intValue)
    }

    @Test
    fun menuHasAccessibleLabelWithTitle() {
        setRow(title = "Teszt szám")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val label = context.getString(R.string.cd_track_actions, "Teszt szám")
        compose.onNodeWithContentDescription(label).performClick()
        compose.waitForIdle()
        assertEquals(1, menus.intValue)
        assertEquals(0, plays.intValue)
    }
}
