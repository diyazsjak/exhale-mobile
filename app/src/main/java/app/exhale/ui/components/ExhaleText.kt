package app.exhale.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.TextMeasurer
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

/** A changed number's animation, in ms: the old one breathes out over OUT_MS; the new one settles in after IN_DELAY_MS. */
private const val OUT_MS = 340
private const val IN_DELAY_MS = 90
private const val IN_MS = 300
private const val TOTAL_MS = IN_DELAY_MS + IN_MS

/**
 * Text that breathes out when it changes: a number that changed drifts up, swells and blurs away
 * like a breath leaving, and the new one settles in under it, all its digits together (about 0.4 s).
 * Numbers and units that didn't change hold still, gliding aside if the text's width changed.
 * Numbers and the words between them are matched from the right, so minutes line up with minutes.
 * The first show does not animate. Blur needs Android 12; below that it is fade and drift only.
 * Each piece is drawn as its slice of one laid-out line, so at rest it is exactly a plain Text; the
 * old text stays centred where it was. It stays on one line, shrinking to fit the width if it must.
 * For the heroes' big numbers.
 */
@Composable
fun ExhaleText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var change by remember { mutableStateOf(TextChange(null, text)) }
    if (change.to != text) change = TextChange(change.to, text)
    val from = change.from
    BoxWithConstraints(modifier) {
        // A new measurer comes with a new density, font scale or layout direction, so those re-measure too.
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val now = remember(text, style, measurer, maxWidth) { measureToFit(measurer, text, style, maxWidth) }
        val before = remember(from, style, measurer, maxWidth) { from?.let { measureToFit(measurer, it, style, maxWidth) } }
        ExhaleLines(text, from, now, before, change, color)
    }
}

/**
 * [text] on one line in [style], its font made smaller if it is wider than [maxWidth] (a large
 * font scale or a narrow phone), so it never runs off the screen.
 */
private fun measureToFit(measurer: TextMeasurer, text: String, style: TextStyle, maxWidth: Int): TextLayoutResult {
    val centred = style.copy(textAlign = TextAlign.Center)
    val line = measurer.measure(text, centred, maxLines = 1, softWrap = false)
    if (maxWidth == Constraints.Infinity || line.size.width <= maxWidth) return line
    val smaller = centred.copy(fontSize = centred.fontSize * (maxWidth.toFloat() / line.size.width) * 0.98f)
    return measurer.measure(text, smaller, maxLines = 1, softWrap = false)
}

/**
 * [text] split into numbers and what is between them ("1h 42m" → "1", "h ", "42", "m"): the
 * pieces that change together.
 */
internal fun exhaleTokens(text: String): List<IntRange> {
    val out = mutableListOf<IntRange>()
    var start = 0
    for (i in 1..text.length) {
        if (i == text.length || text[i].isDigit() != text[i - 1].isDigit()) {
            out += start until i
            start = i
        }
    }
    return out
}

/** One piece of the new text ([to], a range in it) matched with the old one in the same place from the right ([from]). */
internal class TokenPair(val to: IntRange?, val from: IntRange?, val changed: Boolean)

/**
 * The pieces of [text] matched from the right with those of [old] (null on first show): a piece
 * changed if its text did; pieces only one side has come or go.
 */
internal fun exhalePairs(old: String?, text: String): List<TokenPair> {
    val now = exhaleTokens(text)
    val was = old?.let(::exhaleTokens).orEmpty()
    return (maxOf(now.size, was.size) - 1 downTo 0).map { fromRight ->
        val n = now.getOrNull(now.size - 1 - fromRight)
        val o = was.getOrNull(was.size - 1 - fromRight)
        val changed = old != null && (n == null || o == null || text.substring(n) != old.substring(o))
        TokenPair(n, o, changed)
    }
}

@Composable
private fun ExhaleLines(text: String, from: String?, now: TextLayoutResult, before: TextLayoutResult?, change: TextChange, color: Color) {
    // Built once per change, not on every recomposition (the hero's screen recomposes every second).
    val pieces = remember(now, before) {
        val shift = before?.let { (now.size.width - it.size.width) / 2f } ?: 0f
        exhalePairs(from, text).map { pair ->
            val cell = pair.to?.let { Cell(now, it, 0f) }
            val old = if (before != null && from != null && pair.from != null) Cell(before, pair.from, shift) else null
            // The old piece breathes out only if it changed and has something to show (not a space).
            val leaving = old != null && pair.changed && from!!.substring(pair.from!!).isNotBlank()
            Piece(
                cell = cell,
                old = if (leaving) old else null,
                changed = pair.changed,
                // A piece that stayed the same glides from where it was, if the text's width changed.
                slideFrom = if (!pair.changed && cell != null && old != null) old.boxStart - cell.boxStart else 0f,
            )
        }
    }
    Box(
        Modifier
            .layout { m, _ ->
                val pl = m.measure(Constraints.fixed(now.size.width, now.size.height))
                layout(pl.width, pl.height) { pl.place(0, 0) }
            }
            .clearAndSetSemantics { contentDescription = text },
    ) {
        pieces.forEachIndexed { i, piece ->
            // Keyed from the right, as the pieces are matched.
            key(pieces.size - 1 - i) { ExhalePiece(piece, change, color) }
        }
    }
}

/** What one piece does in a change: [old] breathes out and [cell] settles in, or [cell] glides [slideFrom] px back into place. */
private class Piece(val cell: Cell?, val old: Cell?, val changed: Boolean, val slideFrom: Float)

/**
 * Characters [range] of a laid-out line, as the slice of the line between its edge and the next
 * piece's (the first and last slices run on past the ends). Drawing every slice of a line rebuilds
 * it exactly, kerning and all. [dx] moves it sideways (an old line kept centred where it was).
 */
private class Cell(val line: TextLayoutResult, range: IntRange, val dx: Float) {
    private val last = line.layoutInput.text.length - 1
    private val start = line.getHorizontalPosition(range.first, true)
    private val end = if (range.last >= last) line.size.width.toFloat() else line.getHorizontalPosition(range.last + 1, true)
    // Clip edges on whole pixels, so neighbouring slices meet without a seam; the ends run on.
    val left = if (range.first == 0) Float.NEGATIVE_INFINITY else start.roundToInt().toFloat()
    val right = if (range.last >= last) Float.POSITIVE_INFINITY else end.roundToInt().toFloat()
    /** Where the piece sits in the box, for sizing its layer and scaling about its middle. */
    val boxStart = start + dx
    val boxEnd = end + dx
    val mid = (boxStart + boxEnd) / 2f
}

@Composable
private fun ExhalePiece(piece: Piece, change: TextChange, color: Color) {
    // Time into this piece's change, in ms; at TOTAL_MS it is at rest. Read only in layers, so frames don't recompose.
    // Made with the change, so the change's first frame already draws its start (the old text as it
    // was); starting it from the effect, which runs after that frame, flashed the new text for a frame.
    val animates = piece.changed || piece.slideFrom != 0f
    val t = remember(change) { Animatable(if (animates) 0f else TOTAL_MS.toFloat()) }
    LaunchedEffect(change) {
        if (animates) t.animateTo(TOTAL_MS.toFloat(), tween(TOTAL_MS, easing = LinearEasing))
    }
    val outgoing by remember(t) { derivedStateOf { t.value < OUT_MS } }
    val cell = piece.cell
    val old = piece.old
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
        if (piece.changed) {
            val k = FastOutSlowInEasing.transform(((t.value - IN_DELAY_MS) / IN_MS).coerceIn(0f, 1f))
            alpha = k
            translationY = 0.12f * em.toPx() * (1f - k)
            scaleX = 0.94f + 0.06f * k; scaleY = scaleX
            renderEffect = blur(6.dp.toPx() * (1f - k))
        } else {
            translationX = piece.slideFrom * (1f - FastOutSlowInEasing.transform(t.value / TOTAL_MS))
        }
    }
}

/** A blur of [r] px, or none when it would not show (or before Android 12, which can't). */
private fun blur(r: Float) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null

/** One [cell] in its own layer, just around its piece, with room for the blur and the drift. */
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
