package app.exhale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType

/**
 * The drag in a [ReorderColumn]. One object for the column's life, so the handles' gesture handlers
 * capture only it and are never restarted while a finger is down.
 *
 * While dragging, the rows keep their place in the layout and only slide (the dragged one with the
 * finger, the ones it passes by one row height). Moving the rows in the layout mid-drag moved the very
 * node following the finger, and the drag froze. The new order is laid out when the finger lifts.
 */
private class ReorderState<T>(var keyOf: (T) -> Any) {
    var items: List<T> = emptyList()
    var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** The order just dropped, shown until [items] catches up with the move. */
    private var dropped by mutableStateOf<List<T>?>(null)
    private var droppedFor: List<T>? = null
    var dragKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    val heights = mutableStateMapOf<Any, Int>()

    fun order(): List<T> = dropped?.takeIf { droppedFor == items } ?: items

    private fun from(): Int = order().indexOfFirst { keyOf(it) == dragKey }

    /** Where the dragged row would land: past a neighbour's middle, it takes that neighbour's place. */
    val target by derivedStateOf {
        val list = order()
        val i = from()
        if (i < 0) return@derivedStateOf -1
        var t = i
        var left = offset
        val dir = if (left > 0) 1 else -1
        while (t + dir in list.indices) {
            val h = heights[keyOf(list[t + dir])] ?: break
            if (abs(left) <= h / 2f) break
            left -= dir * h
            t += dir
        }
        t
    }

    /** How far the row at [index] slides to make room: one dragged-row height, or none. */
    fun shiftOf(index: Int): Float {
        val i = from()
        val t = target
        if (i < 0 || index == i) return 0f
        val h = (heights[dragKey] ?: 0).toFloat()
        return when {
            index in (i + 1)..t -> -h
            index in t until i -> h
            else -> 0f
        }
    }

    fun start(k: Any) { dragKey = k; offset = 0f }

    fun dragBy(dy: Float) { if (dragKey != null) offset += dy }

    fun drop() {
        val list = order()
        val i = from()
        val t = target
        dragKey = null
        offset = 0f
        if (i < 0 || t < 0 || i == t) return
        dropped = list.toMutableList().apply { add(t, removeAt(i)) }
        droppedFor = items
        onMove(i, t)
    }
}

/**
 * A column whose rows can be dragged into a new order by a handle. Each row gets a [Modifier] to put
 * on its handle and whether it is being dragged. While dragging, the other rows slide aside; [onMove]
 * gets the old and new position once the row is let go. Screen readers get "Move up" and "Move down".
 */
@Composable
fun <T> ReorderColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    val state = remember { ReorderState(keyOf) }
    state.keyOf = keyOf
    state.items = items
    state.onMove = onMove
    val order = state.order()

    Column(modifier) {
        order.forEachIndexed { i, item ->
            val k = keyOf(item)
            key(k) {
                val dragging = state.dragKey == k
                val shift by animateFloatAsState(state.shiftOf(i), tween(160), label = "reorder-shift")
                val handle = Modifier.pointerInput(state, k) {
                    try {
                        detectVerticalDragGestures(
                            onDragStart = { state.start(k) },
                            onDragEnd = { state.drop() },
                            onDragCancel = { state.drop() },
                        ) { change, dy -> change.consume(); state.dragBy(dy) }
                    } finally {
                        // If the handler is ever torn down mid-drag, end the drag rather than leave the row lifted.
                        if (state.dragKey == k) state.drop()
                    }
                }
                val actions = listOfNotNull(
                    CustomAccessibilityAction("Move up") { onMove(i, i - 1); true }.takeIf { i > 0 },
                    CustomAccessibilityAction("Move down") { onMove(i, i + 1); true }.takeIf { i < order.lastIndex },
                )
                Box(
                    Modifier
                        .onSizeChanged { state.heights[k] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) state.offset else shift }
                        .semantics { customActions = actions }
                ) {
                    row(item, handle, dragging)
                }
            }
        }
    }
}

/**
 * A row in a list card that can be dragged by its handle and has one trailing icon action, such as a
 * trigger in Settings → Triggers. Tapping the row opens it. [handle] null leaves the handle out
 * (a hidden trigger). [badge] is an [ExBadge] in `sky` after the label ("Yours"); [dimmed] softens the label.
 */
@Composable
fun HandleRow(
    label: String,
    onClick: () -> Unit,
    action: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    handle: Modifier? = null,
    dragging: Boolean = false,
    badge: String? = null,
    sub: String? = null,
    dimmed: Boolean = false,
) {
    val c = Ex.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(ExRadius.sm)
            .background(if (dragging) c.surfaceHigh else c.surface)
            .clickable(role = Role.Button, onClickLabel = "Edit", onClick = onClick)
            .heightIn(min = ExSize.row)
            .padding(start = if (handle != null) 0.dp else ExSpace.xxs, top = if (sub != null) 6.dp else 0.dp, bottom = if (sub != null) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (handle != null) {
            Box(handle.size(ExSize.touch), contentAlignment = Alignment.Center) {
                Icon(ExIcons.Drag, null, Modifier.size(20.dp), tint = if (dragging) c.ink else c.inkFaint)
            }
        }
        Column(Modifier.weight(1f).padding(end = ExSpace.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
                Text(
                    label, style = ExType.body, color = if (dimmed) c.inkSoft else c.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) ExBadge(badge, accent = c.sky)
            }
            if (sub != null) Text(sub, style = ExType.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // The icon crossfades when the action flips (an eye closing as a trigger is hidden).
        Crossfade(action, animationSpec = tween(180), label = "row-action") { icon ->
            ExIconButton(icon, actionLabel, onAction, filled = false)
        }
    }
}
