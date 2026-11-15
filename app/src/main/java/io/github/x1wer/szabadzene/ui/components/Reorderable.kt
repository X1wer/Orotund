package io.github.x1wer.szabadzene.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.x1wer.szabadzene.R
import kotlinx.coroutines.launch

/**
 * Áthúzással átrendezhető lista. A fogantyút a hívó helyezi el ([handle] módosítóval);
 * húzás közben csak a helyi sorrend változik, elengedéskor egyetlen [onMove] hívás menti.
 * Akadálymentesen (és teszthez) minden sor „Mozgatás fel / le” egyéni műveletet is kap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    enabled: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: (LazyListScope.() -> Unit)? = null,
    itemContent: @Composable (item: T, index: Int, isDragging: Boolean, handle: Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    var local by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var startIndex by remember { mutableIntStateOf(-1) }

    // Húzás közben nem írjuk felül a helyi sorrendet a kívülről érkező listával.
    LaunchedEffect(items) { if (draggingKey == null) local = items }

    fun trySwap() {
        val layout = listState.layoutInfo
        val dragged = layout.visibleItemsInfo.firstOrNull { it.key == draggingKey } ?: return
        val center = dragged.offset + dragOffset + dragged.size / 2f
        val target = layout.visibleItemsInfo.firstOrNull { info ->
            info.key != draggingKey && local.any { key(it) == info.key } &&
                center >= info.offset && center <= info.offset + info.size
        } ?: return
        val from = local.indexOfFirst { key(it) == draggingKey }
        val to = local.indexOfFirst { key(it) == target.key }
        if (from < 0 || to < 0) return
        local = local.toMutableList().apply { add(to, removeAt(from)) }
        dragOffset += (dragged.offset - target.offset).toFloat()
    }

    fun finishDrag() {
        val finalIndex = local.indexOfFirst { key(it) == draggingKey }
        val from = startIndex
        draggingKey = null
        dragOffset = 0f
        startIndex = -1
        if (from >= 0 && finalIndex >= 0 && from != finalIndex) onMove(from, finalIndex)
    }

    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        header?.invoke(this)
        itemsIndexed(local, key = { _, item -> key(item) }) { index, item ->
            val itemKey = key(item)
            val isDragging = itemKey == draggingKey
            val handle = if (enabled) {
                Modifier.pointerInput(itemKey) {
                    detectDragGestures(
                        onDragStart = {
                            draggingKey = itemKey
                            startIndex = local.indexOfFirst { key(it) == itemKey }
                            dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            trySwap()
                            // A lista széléhez érve görgetünk, hogy hosszú listán is el lehessen jutni.
                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }
                            if (info != null) {
                                val top = info.offset + dragOffset
                                val bottom = top + info.size
                                val delta = when {
                                    bottom > listState.layoutInfo.viewportEndOffset - edge -> 24f
                                    top < listState.layoutInfo.viewportStartOffset + edge -> -24f
                                    else -> 0f
                                }
                                if (delta != 0f) {
                                    scope.launch {
                                        val consumed = listState.scrollBy(delta)
                                        dragOffset += consumed
                                        trySwap()
                                    }
                                }
                            }
                        },
                        onDragEnd = { finishDrag() },
                        onDragCancel = { finishDrag() },
                    )
                }
            } else {
                Modifier
            }
            val rowModifier = Modifier
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                .then(if (isDragging) Modifier else Modifier.animateItem())
                .semantics {
                    if (enabled) {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveUp) { onMove(index, index - 1); true })
                            if (index < local.lastIndex) add(CustomAccessibilityAction(moveDown) { onMove(index, index + 1); true })
                        }
                    }
                }
            Surface(
                modifier = rowModifier,
                tonalElevation = if (isDragging) 6.dp else 0.dp,
                shadowElevation = if (isDragging) 6.dp else 0.dp,
            ) {
                itemContent(item, index, isDragging, handle)
            }
        }
    }
}

/**
 * Átrendező fogantyú: legalább 48 dp széles, a sor teljes magasságában.
 * A [modifier] a [ReorderableColumn] által adott húzásérzékelő.
 */
@Composable
fun DragHandle(modifier: Modifier, enabled: Boolean) {
    Box(
        modifier = modifier
            .testTag("drag_handle")
            .width(48.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.cd_drag_handle),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            },
        )
    }
}
