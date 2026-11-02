package app.exhale.ui.screens

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.exhale.ui.components.rememberHaptics
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/*
 * "Gather": the ember as a molten core with four droplets orbiting it, ported 1:1 from the Ember Lab.
 * At rest the core pulses every 4 s and the drops drift in and out, sometimes touching the core
 * and merging with a liquid bridge. Holding pulls them in until they merge and the core swells;
 * a log flings them back out with a ring and a splash of droplets.
 */

/** How long the ember must be held to log. */
const val HOLD_MS = 1500

/** Haptic ticks during a hold, rising in strength. */
private const val CHARGE_TICKS = 12

private const val PULSE_S = 4f
private const val BURST_MS = 900
/** Time for a full hold to drain back to zero after release. */
private const val RELEASE_MS = 450

private const val TAU = (2 * PI).toFloat()

/** The ember's drawing size. */
val EmberSize: Dp = 300.dp

private class Drop(val angle: Float, val dist: Float, val size: Float, val speed: Float, val bob: Float)

/**
 * Four drops with fixed orbits. Each drop's rest distance is set from its size so that, at the
 * bottom of its slow in-and-out drift, it just reaches the core and melts into it: about a third
 * of each 12.6 s drift cycle. The drift phases are staggered so the touches take turns.
 */
private val DROPS: List<Drop> = listOf(
    Drop(angle = 0.3f, dist = 1.50f, size = 0.22f, speed = 0.12f, bob = 0f),
    Drop(angle = 1.9f, dist = 1.56f, size = 0.26f, speed = 0.16f, bob = TAU * 0.25f),
    Drop(angle = 3.4f, dist = 1.45f, size = 0.20f, speed = 0.14f, bob = TAU * 0.5f),
    Drop(angle = 5.0f, dist = 1.58f, size = 0.28f, speed = 0.19f, bob = TAU * 0.75f),
)

private fun easeOut(t: Float) = 1f - (1f - t).pow(3)
private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/**
 * The ember: press and hold to log a cigarette. Releasing early cancels.
 * 12 haptic ticks rise in strength during the hold; a thud when it logs.
 * TalkBack's long-press action logs directly.
 */
@Composable
fun EmberOrb(heat: Float, onLog: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val hold = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val currentOnLog by rememberUpdatedState(onLog)

    // One frame clock: `clock` drives the pulse, `phase` the drops (faster while held).
    var clock by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                clock += dt
                phase += dt * (1f + 3f * hold.value)
            }
        }
    }
    val breathe = (1f - cos(clock / PULSE_S * TAU)) / 2f

    fun release() = scope.launch {
        hold.animateTo(0f, tween((RELEASE_MS * hold.value).toInt().coerceAtLeast(1), easing = LinearEasing))
    }

    EmberOrbCanvas(
        heat = heat,
        hold = hold.value,
        burst = burst.value,
        breathe = breathe,
        phase = phase,
        modifier = modifier
            .size(EmberSize)
            .semantics {
                contentDescription = "Ember. Press and hold to log a cigarette."
                onLongClick("Log cigarette") { haptics.logged(); currentOnLog(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    var committed = false
                    val job = scope.launch {
                        val ticks = launch {
                            repeat(CHARGE_TICKS) { k ->
                                delay(HOLD_MS.toLong() / CHARGE_TICKS)
                                haptics.charge((k + 1f) / CHARGE_TICKS)
                            }
                        }
                        hold.animateTo(1f, tween((HOLD_MS * (1f - hold.value)).toInt().coerceAtLeast(1), easing = LinearEasing))
                        ticks.cancel()
                        committed = true
                        haptics.logged()
                        currentOnLog()
                        launch {
                            burst.snapTo(0f)
                            burst.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
                            burst.snapTo(0f)
                        }
                        release()
                    }
                    tryAwaitRelease()
                    if (!committed) {
                        job.cancel()
                        haptics.cancel()
                        release()
                    }
                })
            },
    )
}

/** Blur + alpha threshold: overlapping blobs melt into one liquid shape (Android 12+). */
@Composable
private fun rememberGoo(blur: Dp): androidx.compose.ui.graphics.RenderEffect? {
    val px = with(LocalDensity.current) { blur.toPx() }
    return remember(px) {
        if (Build.VERSION.SDK_INT >= 31) {
            val threshold = ColorMatrixColorFilter(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 22f, -9f * 255f,
                )
            )
            RenderEffect.createColorFilterEffect(threshold, RenderEffect.createBlurEffect(px, px, Shader.TileMode.DECAL))
                .asComposeRenderEffect()
        } else {
            null
        }
    }
}

/**
 * Stateless drawing of the ember so screenshot tests can render any moment.
 * [heat] 0..1 cool → hot, [hold] and [burst] 0..1, [breathe] 0..1 pulse, [phase] drop clock in seconds.
 */
@Composable
fun EmberOrbCanvas(heat: Float, hold: Float, burst: Float, breathe: Float, phase: Float, modifier: Modifier = Modifier) {
    val c = Ex.colors
    val goo = rememberGoo(EmberSize * 0.035f)
    val h = heat + (1f - heat) * hold
    val body = c.heat(h)
    val accent = c.heatAccent(h)
    val shade = if (c.isDark) Color.Black else lerp(c.ink, Color.Black, 0.3f)
    val pull = easeInOut(hold)
    val fling = if (burst > 0f && burst < 1f) sin(burst * PI.toFloat()) * 0.6f else 0f

    fun coreRadius(s: Float) = s * 0.2f * (1f + 0.03f * breathe) * (1f + 0.22f * pull)

    Box(modifier) {
        // Soft glow underneath. Pastels on a light background need more alpha to read the same.
        Canvas(Modifier.matchParentSize()) {
            val r = coreRadius(size.minDimension)
            drawCircle(
                Brush.radialGradient(listOf(body.copy(alpha = if (c.isDark) 0.18f else 0.3f), body.copy(alpha = 0f)), center, r * 2.1f),
                r * 2.1f, center,
            )
        }
        // Liquid: flat shapes through the goo effect, then one lighting pass clipped to them.
        Box(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            Canvas(Modifier.matchParentSize().graphicsLayer { renderEffect = goo }) {
                val s = size.minDimension
                val r = coreRadius(s)
                drawCircle(Color.White, r, center)
                DROPS.forEach { d ->
                    val a = d.angle + phase * d.speed
                    val dist = r * (d.dist + 0.3f * sin(phase * 0.5f + d.bob)) * (1f - pull) * (1f + fling) + r * 0.2f * pull
                    drawCircle(Color.White, r * d.size * (1f - 0.5f * pull), center + Offset(cos(a), sin(a)) * dist)
                }
            }
            Canvas(Modifier.matchParentSize()) {
                val r = coreRadius(size.minDimension)
                drawRect(
                    Brush.radialGradient(
                        0f to lerp(body, Color.White, 0.5f),
                        0.3f to body,
                        0.7f to lerp(accent, shade, 0.2f),
                        1f to lerp(accent, shade, 0.4f),
                        center = center + Offset(-0.4f * r, -0.45f * r),
                        radius = size.minDimension * 0.55f,
                    ),
                    blendMode = BlendMode.SrcIn,
                )
            }
        }
        // Highlight, charge trail and the completion burst, drawn crisp on top.
        Canvas(Modifier.matchParentSize()) {
            val s = size.minDimension
            val r = coreRadius(s)
            val spec = center + Offset(-0.38f * r, -0.42f * r)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = if (c.isDark) 0.42f else 0.55f), Color.White.copy(alpha = 0f)), spec, r * 0.5f),
                r * 0.5f, spec,
            )
            comet(c, s * 0.44f, hold, heat, (hold * 6f).coerceIn(0f, 1f) * (1f - burst) * 0.7f)
            burstFx(c, s, r, burst)
        }
    }
}

/** Progress trail around a circle, brightest at its head, coloured along the heat scale. */
private fun DrawScope.comet(c: ExColors, radius: Float, p: Float, h0: Float, alpha: Float) {
    if (p <= 0f || alpha <= 0f) return
    val steps = (160 * p).toInt().coerceAtLeast(2)
    fun at(f: Float): Offset { val a = -TAU / 4f + f * TAU; return center + Offset(cos(a), sin(a)) * radius }
    for (i in 0 until steps) {
        val f0 = i.toFloat() / steps * p
        val f1 = (i + 1f) / steps * p
        val k = (i + 1f) / steps
        drawLine(
            c.heat(h0 + (1f - h0) * f1).copy(alpha = alpha * (0.15f + 0.85f * k * k)),
            at(f0), at(f1), strokeWidth = (1f + 2f * k).dp.toPx(), cap = StrokeCap.Round,
        )
    }
    val head = at(p)
    val hc = c.heat(h0 + (1f - h0) * p)
    drawCircle(Brush.radialGradient(listOf(hc.copy(alpha = 0.45f * alpha), hc.copy(alpha = 0f)), head, 12.dp.toPx()), 12.dp.toPx(), head)
    drawCircle(lerp(hc, Color.White, 0.4f).copy(alpha = alpha), 3.dp.toPx(), head)
}

/** Completion: a ring expanding past the drops and a splash of small droplets. */
private fun DrawScope.burstFx(c: ExColors, s: Float, r: Float, b: Float) {
    if (b <= 0f || b >= 1f) return
    val e = easeOut(b)
    drawCircle(c.ember.copy(alpha = 0.6f * (1f - b)), r + (s * 0.47f - r) * e, center, style = Stroke((1f + 3f * (1f - b)).dp.toPx()))
    for (i in 0 until 12) {
        val a = i / 12f * TAU + 0.7f
        val d = r + s * (0.1f + 0.08f * ((i * 7) % 3)) * e
        drawCircle(
            (if (i % 2 == 1) c.ember else c.flare).copy(alpha = 0.9f * (1f - b)),
            s * 0.011f * (1f - b) + 0.5f,
            center + Offset(cos(a), sin(a)) * d,
        )
    }
}
