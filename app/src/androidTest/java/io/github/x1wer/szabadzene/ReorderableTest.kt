package io.github.x1wer.szabadzene

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.x1wer.szabadzene.ui.components.DragHandle
import io.github.x1wer.szabadzene.ui.components.ReorderableColumn
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Az átrendezhető lista: húzással és az akadálymentes „Mozgatás le / fel” művelettel. */
@RunWith(AndroidJUnit4::class)
class ReorderableTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setList(onMove: (Int, Int) -> Unit, enabled: Boolean = true) {
        var items by mutableStateOf(listOf("Egy", "Kettő", "Három", "Négy"))
        compose.setContent {
            MaterialTheme {
                ReorderableColumn(
                    items = items,
                    key = { it },
                    enabled = enabled,
                    onMove = { from, to ->
                        onMove(from, to)
                        items = items.toMutableList().apply { add(to, removeAt(from)) }
                    },
                    contentPadding = PaddingValues(0.dp),
                ) { item, _, _, handle ->
                    androidx.compose.foundation.layout.Row(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    ) {
                        DragHandle(handle, enabled)
                        Text(item)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun accessibilityActionsMoveItems() {
        val moves = mutableListOf<Pair<Int, Int>>()
        setList({ from, to -> moves += from to to })
        val moveDown = context.getString(R.string.action_move_down)
        val moveUp = context.getString(R.string.action_move_up)
        val rows = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        assertEquals(4, rows.fetchSemanticsNodes().size)
        rows[0].performCustomAccessibilityActionWithLabel(moveDown)
        compose.waitForIdle()
        rows[3].performCustomAccessibilityActionWithLabel(moveUp)
        compose.waitForIdle()
        assertEquals(listOf(0 to 1, 3 to 2), moves)
    }

    @Test
    fun draggingHandleMovesItemOnce() {
        val moves = mutableListOf<Pair<Int, Int>>()
        setList({ from, to -> moves += from to to })
        val handles = compose.onAllNodesWithTag("drag_handle")
        // Az első elem fogantyúját két sornyit lefelé húzzuk.
        handles[0].performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, height * 2.2f / 20f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(1, moves.size)
        assertEquals(0, moves[0].first)
        assertEquals(2, moves[0].second)
    }

    @Test
    fun disabledListHasNoDragEffect() {
        val moves = mutableListOf<Pair<Int, Int>>()
        setList({ from, to -> moves += from to to }, enabled = false)
        compose.onAllNodesWithTag("drag_handle")[0].performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, height * 2f / 10f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(emptyList<Pair<Int, Int>>(), moves)
    }
}
