package app.exhale.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import kotlin.math.ceil
import kotlin.math.cos

/** Paced breathing: in 4, hold 2, out 6. A longer out-breath calms the body. */
enum class BreathPhase(val verb: String, val seconds: Float) {
    IN("Breathe in", 4f),
    HOLD("Hold", 2f),
    OUT("Breathe out", 6f);

    val next: BreathPhase get() = entries[(ordinal + 1) % entries.size]
}

/** Where the breath is at time t: phase, progress through it 0..1, whole seconds left. */
data class BreathState(val phase: BreathPhase, val progress: Float, val secondsLeft: Int) {
    /** How full the lungs are, 0..1, eased like a real breath. Drives the wave dot's halo. */
    val fullness: Float
        get() = when (phase) {
            BreathPhase.IN -> easeInOutSine(progress)
            BreathPhase.HOLD -> 1f
            BreathPhase.OUT -> 1f - easeInOutSine(progress)
        }
}

private fun easeInOutSine(x: Float) = ((1 - cos(Math.PI * x)) / 2).toFloat()

private val CYCLE = BreathPhase.entries.sumOf { it.seconds.toDouble() }.toFloat()

fun breathAt(t: Float): BreathState {
    var x = t % CYCLE
    for (p in BreathPhase.entries) {
        if (x < p.seconds) return BreathState(p, x / p.seconds, ceil(p.seconds - x).toInt().coerceAtLeast(1))
        x -= p.seconds
    }
    return BreathState(BreathPhase.OUT, 1f, 1)
}

/** Next line sits this far below the current one and at this scale, so it can grow into place. */
private const val NEXT_SCALE = 0.62f
private val LINE_GAP = 46.dp
private val Smooth = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Text-only breathing guide.
 *
 * - The current instruction ("Breathe in") fills with colour letter by letter, left to right, over
 *   the phase's duration: how long it lasts is visible in the word itself.
 * - The next one waits underneath, small and faded ("then hold · 2s"); in the final second it
 *   brightens and lifts a little, so the change is expected.
 * - On a change, the next line glides up and grows into the current position while the old one
 *   floats away: one continuous scroll instead of a swap.
 */
@Composable
fun BreathGuide(t: Float, done: Boolean, modifier: Modifier = Modifier) {
    val c = Ex.colors
    val b = breathAt(t)
    val gapPx = with(LocalDensity.current) { LINE_GAP.toPx() }

    // Phase change transition, 0 → 1.
    var shown by remember { mutableStateOf(b.phase) }
    var previous by remember { mutableStateOf<BreathPhase?>(null) }
    val move = remember { Animatable(1f) }
    LaunchedEffect(b.phase) {
        if (b.phase != shown) {
            previous = shown
            shown = b.phase
            move.snapTo(0f)
            move.animateTo(1f, tween(700, easing = Smooth))
            previous = null
        }
    }
    val m = move.value

    fun accentOf(p: BreathPhase) = when (p) {
        BreathPhase.IN -> c.skyInk
        BreathPhase.HOLD -> lerp(c.skyInk, c.oxyInk, 0.5f)
        BreathPhase.OUT -> c.oxyInk
    }
    val unfilled = c.inkFaint.copy(alpha = if (c.isDark) 0.55f else 0.45f)

    /** Left-to-right fill at [p] with a soft leading edge. */
    fun fillBrush(color: Color, p: Float): Brush {
        val edge = 0.08f
        val x = (-edge + p * (1f + 2 * edge)).coerceIn(-edge, 1f + edge)
        return Brush.horizontalGradient(
            0f to (if (x <= 0f) unfilled else color),
            (x - edge).coerceIn(0f, 1f) to color,
            (x + edge).coerceIn(0f, 1f) to unfilled,
            1f to (if (x >= 1f) color else unfilled),
        )
    }

    val soon = !done && b.secondsLeft <= 1
    val anticipation by androidx.compose.animation.core.animateFloatAsState(if (soon) 1f else 0f, tween(450), label = "soon")

    Box(
        modifier
            .fillMaxWidth()
            .height(LINE_GAP * 2)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = if (done) "Breathe normally" else "${b.phase.verb}. Next: ${b.phase.next.verb}."
            },
    ) {
        if (done) {
            Text("Breathe normally", style = ExType.titleItalic, color = c.oxyInk, modifier = Modifier.align(Alignment.TopCenter))
            return@Box
        }
        val cur = shown
        // Lines grow and shrink from their centre so the scroll stays symmetric.
        val origin = TransformOrigin(0.5f, 0f)

        // Outgoing: floats up and fades.
        previous?.let { p ->
            Text(
                p.verb,
                style = ExType.titleItalic.copy(brush = fillBrush(accentOf(p), 1f)),
                modifier = Modifier.align(Alignment.TopCenter).graphicsLayer {
                    transformOrigin = origin
                    translationY = -gapPx * 0.6f * m
                    alpha = 1f - m
                    val s = 1f - 0.08f * m
                    scaleX = s; scaleY = s
                },
            )
        }
        // Current: grows up from the next line's place, then fills over its duration.
        Text(
            cur.verb,
            style = ExType.titleItalic.copy(brush = fillBrush(accentOf(cur), if (cur == b.phase) b.progress else 0f)),
            modifier = Modifier.align(Alignment.TopCenter).graphicsLayer {
                transformOrigin = origin
                translationY = gapPx * (1f - m)
                val s = NEXT_SCALE + (1f - NEXT_SCALE) * m
                scaleX = s; scaleY = s
                alpha = 0.55f + 0.45f * m
            },
        )
        // Next: waits below, brightens and lifts slightly in the final second.
        val next = cur.next
        Text(
            "then ${next.verb.lowercase()} · ${next.seconds.toInt()}s",
            style = ExType.titleItalic,
            color = lerp(c.inkFaint, c.inkSoft, anticipation),
            modifier = Modifier.align(Alignment.TopCenter).graphicsLayer {
                transformOrigin = origin
                translationY = gapPx * (1f + 0.35f * (1f - m)) - 4.dp.toPx() * anticipation
                scaleX = NEXT_SCALE; scaleY = NEXT_SCALE
                alpha = m * (0.55f + 0.45f * anticipation)
            },
        )
    }
}
