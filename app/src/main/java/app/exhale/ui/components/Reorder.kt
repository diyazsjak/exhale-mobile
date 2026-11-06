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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * A column whose rows can be dragged into a new order by a handle. Each row gets a [Modifier] to put
 * on its handle and whether it is being dragged. The new order shows while dragging; [onMove] gets the
 * old and new position once the row is let go. Screen readers get "Move up" and "Move down" actions.
 */
@Composable
fun <T> ReorderColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    var order by remember(items) { mutableStateOf(items) }
    var dragKey by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val currentItems by rememberUpdatedState(items)
    val currentMove by rememberUpdatedState(onMove)

    fun dragBy(dy: Float) {
        val k = dragKey ?: return
        offset += dy
        // Past the middle of the neighbour it's moving toward, the two swap places.
        val i = order.indexOfFirst { keyOf(it) == k }
        val dir = if (offset > 0) 1 else -1
        val j = i + dir
        if (i < 0 || j !in order.indices) return
        val h = heights[keyOf(order[j])] ?: return
        if (abs(offset) > h / 2f) {
            order = order.toMutableList().apply { add(j, removeAt(i)) }
            offset -= dir * h
        }
    }

    fun drop() {
        val k = dragKey ?: return
        val from = currentItems.indexOfFirst { keyOf(it) == k }
        val to = order.indexOfFirst { keyOf(it) == k }
        dragKey = null
        offset = 0f
        if (from >= 0 && to >= 0 && from != to) currentMove(from, to)
    }

    Column(modifier) {
        order.forEachIndexed { i, item ->
            val k = keyOf(item)
            // Keyed by item, so a swap moves the row (and the drag it is running) rather than re-keying the slot.
            key(k) {
                val dragging = dragKey == k
                val handle = Modifier.pointerInput(k, items) {
                    detectVerticalDragGestures(
                        onDragStart = { dragKey = k; offset = 0f },
                        onDragEnd = { drop() },
                        onDragCancel = { drop() },
                    ) { change, dy -> change.consume(); dragBy(dy) }
                }
                val actions = listOfNotNull(
                    CustomAccessibilityAction("Move up") { currentMove(i, i - 1); true }.takeIf { i > 0 },
                    CustomAccessibilityAction("Move down") { currentMove(i, i + 1); true }.takeIf { i < order.lastIndex },
                )
                Box(
                    Modifier
                        .onSizeChanged { heights[k] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) offset else 0f }
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
        ExIconButton(action, actionLabel, onAction, filled = false)
    }
}
