package io.github.x1wer.szabadzene

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Az alsó fülek közötti váltás oldalra húzással, és a Vissza gomb a fülek saját navigációjával. */
@RunWith(AndroidJUnit4::class)
class MainNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun swipe(left: Boolean) {
        compose.onNodeWithTag("main_pager").performTouchInput { if (left) swipeLeft() else swipeRight() }
        compose.waitForIdle()
    }

    /**
     * A Súgó sora a lista alján a navigációs sáv alá is kerülhet (a lista a sáv mögött fut), ezért a
     * kattintást a sor saját műveletével váltjuk ki, nem érintéssel.
     */
    private fun openHelp() {
        compose.onNodeWithTag("setting_help").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun swipingMovesBetweenBottomTabs() {
        compose.onNodeWithTag("tab_library").assertIsSelected()
        swipe(left = true)
        compose.onNodeWithTag("tab_playlists").assertIsSelected()
        swipe(left = true)
        compose.onNodeWithTag("tab_folders").assertIsSelected()
        swipe(left = true)
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        // Az utolsó fülön tovább húzva nem történik semmi.
        swipe(left = true)
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        swipe(left = false)
        compose.onNodeWithTag("tab_folders").assertIsSelected()
        // Koppintással is lehet váltani.
        compose.onNodeWithTag("tab_playlists").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tab_playlists").assertIsSelected()
    }

    @Test
    fun backClosesTabScreenFirstThenReturnsToFirstTab() {
        compose.onNodeWithTag("tab_settings").performClick()
        compose.waitForIdle()
        openHelp()
        compose.waitForIdle()
        compose.onNodeWithTag("help_screen").assertExists()

        // Az első Vissza a Súgót zárja be (a Beállítások fülön maradunk) …
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("help_screen").assertDoesNotExist()
        compose.onNodeWithTag("tab_settings").assertIsSelected()

        // … a második az első fülre visz.
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("tab_library").assertIsSelected()
    }

    @Test
    fun tabKeepsItsOwnScreenWhenSwitching() {
        compose.onNodeWithTag("tab_settings").performClick()
        compose.waitForIdle()
        openHelp()
        compose.waitForIdle()
        swipe(left = false)
        compose.onNodeWithTag("tab_folders").assertIsSelected()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("help_screen").assertExists()
        // Az aktív fülre koppintva vissza a fül kezdőképernyőjére.
        compose.onNodeWithTag("tab_settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("help_screen").assertDoesNotExist()
    }
}
