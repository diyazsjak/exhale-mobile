package app.exhale.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.exhale.ui.Fmt
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSpace
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** One change of [ExhaleText]'s text: from what (null on first show) to what. */
private class TextChange(val from: String?, val to: String)

/** A changed glyph's animation, in ms: the old one breathes out over OUT_MS; the new one settles in after IN_DELAY_MS. */
private const val OUT_MS = 340
private const val IN_DELAY_MS = 90
private const val IN_MS = 300
private const val TOTAL_MS = IN_DELAY_MS + IN_MS
/** Each glyph further left starts this much later, so a change reads right to left like a counter. */
private const val STAGGER_MS = 35

/**
 * Text that breathes out when it changes: each changed character drifts up, swells and blurs away
 * like a breath leaving, and the new one settles in under it (about 0.4 s). Unchanged characters
 * hold still; characters are matched from the right, so minutes line up with minutes. A change of
 * length ("1m" → "1h 30m") moves every character. The first show does not animate. Blur needs
 * Android 12; below that it is fade and drift only. Each glyph is drawn as its slice of one laid-out
 * line, so at rest it is exactly a plain Text; the old text stays centred where it was. For the heroes' big numbers.
 */
@Composable
fun ExhaleText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var change by remember { mutableStateOf(TextChange(null, text)) }
    if (change.to != text) change = TextChange(change.to, text)
    val from = change.from
    val measurer = rememberTextMeasurer()
    val centred = style.copy(textAlign = TextAlign.Center)
    fun line(s: String) = measurer.measure(s, centred, maxLines = 1, softWrap = false)
    val now = remember(text, style) { line(text) }
    val before = remember(from, style) { from?.let(::line) }
    // Built once per change, not on every recomposition (the hero's screen recomposes every second).
    val cells = remember(now) { List(text.length) { Cell(now, it, 0f) } }
    val oldCells = remember(before, now) { before?.let { b -> List(b.layoutInput.text.length) { Cell(b, it, (now.size.width - b.size.width) / 2f) } } }
    Box(
        modifier
            .layout { m, _ ->
                val pl = m.measure(Constraints.fixed(now.size.width, now.size.height))
                layout(pl.width, pl.height) { pl.place(0, 0) }
            }
            .clearAndSetSemantics { contentDescription = text },
    ) {
        // Matched from the right; a shorter text still breathes out the old characters on its left.
        for (fromRight in maxOf(text.length, from?.length ?: 0) - 1 downTo 0) {
            val i = text.length - 1 - fromRight
            val j = from?.let { it.length - 1 - fromRight } ?: -1
            val ch = text.getOrNull(i)
            val old = from?.getOrNull(j)
            val moved = from != null && (from.length != text.length || old != ch)
            key(fromRight) {
                ExhaleGlyph(
                    cell = cells.getOrNull(i),
                    old = if (moved && old != null && old != ' ') oldCells?.getOrNull(j) else null,
                    moved = moved, change = change, delayMs = fromRight * STAGGER_MS, color = color,
                )
            }
        }
    }
}

/**
 * Character [i] of a laid-out line, as the slice of the line between its edge and the next one's
 * (the first and last slices run on past the ends). Drawing every slice of a line rebuilds it
 * exactly, kerning and all. [dx] moves it sideways (an old line kept centred where it was).
 */
private class Cell(val line: TextLayoutResult, i: Int, val dx: Float) {
    private val start = line.getHorizontalPosition(i, true)
    private val end = if (i >= line.layoutInput.text.length - 1) line.size.width.toFloat() else line.getHorizontalPosition(i + 1, true)
    // Clip edges on whole pixels, so neighbouring slices meet without a seam; the ends run on.
    val left = if (i == 0) Float.NEGATIVE_INFINITY else start.roundToInt().toFloat()
    val right = if (i >= line.layoutInput.text.length - 1) Float.POSITIVE_INFINITY else end.roundToInt().toFloat()
    /** Where the character sits in the box, for sizing its layer and scaling about its middle. */
    val boxStart = start + dx
    val boxEnd = end + dx
    val mid = (boxStart + boxEnd) / 2f
}

@Composable
private fun ExhaleGlyph(cell: Cell?, old: Cell?, moved: Boolean, change: TextChange, delayMs: Int, color: Color) {
    // Time into this glyph's change, in ms; at TOTAL_MS it is at rest. Read only in layers, so frames don't recompose.
    val t = remember { Animatable(TOTAL_MS.toFloat()) }
    LaunchedEffect(change) {
        if (moved) {
            t.snapTo(-delayMs.toFloat())
            t.animateTo(TOTAL_MS.toFloat(), tween(TOTAL_MS + delayMs, easing = LinearEasing))
        } else {
            t.snapTo(TOTAL_MS.toFloat())
        }
    }
    val outgoing by remember { derivedStateOf { t.value < OUT_MS } }
    val em = (cell ?: old ?: return).line.layoutInput.style.fontSize
    if (old != null && outgoing) {
        Slice(old, color) {
            val k = FastOutSlowInEasing.transform((t.value / OUT_MS).coerceIn(0f, 1f))
            alpha = 1f - k
            translationY = -0.4f * em.toPx() * k
            scaleX = 1f + 0.1f * k; scaleY = scaleX
            renderEffect = blur(8.dp.toPx() * k)
        }
    }
    if (cell != null) Slice(cell, color) {
        val k = FastOutSlowInEasing.transform(((t.value - IN_DELAY_MS) / IN_MS).coerceIn(0f, 1f))
        alpha = k
        translationY = 0.12f * em.toPx() * (1f - k)
        scaleX = 0.94f + 0.06f * k; scaleY = scaleX
        renderEffect = blur(6.dp.toPx() * (1f - k))
    }
}

/** A blur of [r] px, or none when it would not show (or before Android 12, which can't). */
private fun blur(r: Float) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null

/** One [cell] in its own layer, just around its character, with room for the blur and the drift. */
@Composable
private fun Slice(cell: Cell, color: Color, layer: GraphicsLayerScope.() -> Unit) {
    val p = with(LocalDensity.current) { 16.dp.roundToPx() }
    val lo = floor(cell.boxStart).toInt() - p
    val hi = ceil(cell.boxEnd).toInt() + p
    Spacer(
        Modifier
            .layout { m, c ->
                val pl = m.measure(Constraints.fixed(hi - lo, c.maxHeight + 2 * p))
                layout(0, 0) { pl.place(lo, -p) }
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin((cell.mid - lo) / size.width, 0.5f)
                layer()
            }
            .drawBehind {
                translate(cell.dx - lo, p.toFloat()) {
                    clipRect(cell.left.coerceAtLeast(-1e5f), -1e5f, cell.right.coerceAtMost(1e5f), 1e5f) {
                        drawText(cell.line, color)
                    }
                }
            },
    )
}

/** For the UI kit: a sample that steps down a minute when tapped. */
@Composable
internal fun ExhaleTextSample(style: TextStyle) {
    var mins by remember { mutableIntStateOf(62) }
    val c = Ex.colors
    val label = Fmt.gap(Duration.ofMinutes(mins.toLong()))
    ExCard(onClick = { mins = if (mins <= 1) 62 else mins - 1 }, padding = ExSpace.sm) {
        ExhaleText(label, style, c.ink)
    }
}
