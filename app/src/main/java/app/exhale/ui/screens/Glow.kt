package app.exhale.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType

/*
 * "Glow" (Floor, edge glow, from the First Screen Lab): the home without an object. A big number in
 * the middle, a straight countdown line above the craving button, and soft light along the screen's
 * edges coloured by heat. Hold anywhere in the middle to log; a warm trail runs along the line.
 *
 * The edge light ([GlowEdge]) is drawn once, under all the tabs, so it stays fixed while pages
 * slide; it fades out as the Ember tab is left. The line and the number belong to the page.
 */

/** The edge light's breath: one slow in and out every 4 s, like the ember's pulse. */
@Composable
private fun rememberBreath(): State<Float> = rememberInfiniteTransition(label = "glow-breath")
    .animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")

/**
 * The middle of the Glow home: [big] and [sub] (the same words as the Ember home) in a hold area that
 * fills the space, then the countdown line. [progress] is how much of the gap is earned (0..1).
 * [onLight] reports the hold (0..1) and the log's flash (1 → 0) to the edge light on every change.
 */
@Composable
internal fun GlowHero(
    big: String,
    sub: AnnotatedString,
    heat: Float,
    progress: Float,
    onLog: () -> Unit,
    modifier: Modifier = Modifier,
    nextLogIsOver: Boolean = false,
    onHoldStart: () -> Unit = {},
    onHoldEnd: (held: Float) -> Unit = {},
    onLight: (hold: Float, flash: Float) -> Unit = { _, _ -> },
) {
    val c = Ex.colors
    val h = rememberHoldToLog(onLog, nextLogIsOver, onHoldStart, onHoldEnd)
    val currentOnLight by rememberUpdatedState(onLight)
    LaunchedEffect(h) {
        snapshotFlow { h.hold.value to h.burst.value }.collect { (hold, burst) -> currentOnLight(hold, if (burst > 0f) 1f - burst else 0f) }
    }
    val breath = rememberBreath()
    Column(modifier.fillMaxWidth().holdToLog(h, "Press and hold anywhere to log a cigarette.")) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ExSpace.xxs)) {
                Text(big, style = ExType.display.copy(fontSize = 88.sp), color = c.ink, textAlign = TextAlign.Center, maxLines = 1)
                Text(sub, style = ExType.titleItalic.copy(fontSize = 21.sp), color = c.inkSoft, textAlign = TextAlign.Center)
            }
        }
        GlowLine(
            progress = progress,
            heat = heat,
            hold = { h.hold.value },
            burst = { h.burst.value },
            breath = { breath.value },
        )
        Spacer(Modifier.height(ExSpace.xs))
    }
}

/**
 * The countdown as one straight line that fills left to right, sky → oxygen, between "last one"
 * and "next one earned". Once earned it is a full oxygen line with a soft breathing glow. While
 * holding, the cool fill fades and a warm trail runs along the line on the heat scale, brightest
 * at its head.
 */
@Composable
internal fun GlowLine(
    progress: Float,
    heat: Float,
    hold: () -> Float,
    burst: () -> Float,
    breath: () -> Float,
    modifier: Modifier = Modifier,
) {
    val c = Ex.colors
    // Colours read before the draw lambda.
    val track = c.ink.copy(alpha = 0.1f)
    val sky = c.sky
    val oxy = c.oxy
    val colors = c
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ExSpace.xxs)) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val w = 3.dp.toPx()
            val y = size.height / 2f
            val x0 = w
            val x1 = size.width - w
            val len = x1 - x0
            drawLine(track, Offset(x0, y), Offset(x1, y), w, StrokeCap.Round)
            val hd = hold()
            val show = 1f - (hd * 4f).coerceIn(0f, 1f)
            if (progress > 0.002f && show > 0f) {
                if (progress >= 0.999f) {
                    drawLine(oxy.copy(alpha = (0.12f + 0.1f * breath()) * show), Offset(x0, y), Offset(x1, y), w * 3.5f, StrokeCap.Round)
                    drawLine(oxy.copy(alpha = show), Offset(x0, y), Offset(x1, y), w, StrokeCap.Round)
                } else {
                    val end = x0 + len * progress
                    drawLine(
                        Brush.horizontalGradient(listOf(sky, oxy), startX = x0, endX = end),
                        Offset(x0, y), Offset(end, y), w, StrokeCap.Round, alpha = show,
                    )
                }
            }
            val trail = (hd * 5f).coerceIn(0f, 1f) * (1f - burst())
            if (hd > 0f && trail > 0f) {
                val head = x0 + len * hd
                val stops = (0..6).map { i ->
                    val k = i / 6f
                    k to colors.heat(heat + (1f - heat) * k * hd).copy(alpha = trail * (0.2f + 0.8f * k * k))
                }.toTypedArray()
                drawLine(Brush.horizontalGradient(*stops, startX = x0, endX = head), Offset(x0, y), Offset(head, y), w + 1.dp.toPx(), StrokeCap.Butt)
                val hc = colors.heat(heat + (1f - heat) * hd)
                val r = 22.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(hc.copy(alpha = 0.6f * trail), hc.copy(alpha = 0f)), Offset(head, y), r), r, Offset(head, y))
                drawCircle(lerp(hc, Color.White, 0.4f).copy(alpha = trail), 3.5.dp.toPx(), Offset(head, y))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            Text("last one", style = ExType.caption, color = c.inkFaint)
            Spacer(Modifier.weight(1f))
            Text("next one earned", style = ExType.caption, color = c.inkFaint)
        }
    }
}

/**
 * Soft light along the four screen edges, coloured by heat: warm right after a cigarette, cooling
 * to oxygen over the gap. It breathes slowly, brightens and deepens while holding ([hold] 0..1) and
 * flashes on a log ([flash] 1 → 0). [alpha] fades all of it (leaving the Ember tab). Draw it once,
 * behind the tabs, so it stays fixed while pages slide.
 */
@Composable
internal fun GlowEdge(
    heat: Float,
    alpha: () -> Float,
    hold: () -> Float,
    flash: () -> Float,
    modifier: Modifier = Modifier,
) {
    val c = Ex.colors
    val colors = c
    val base = if (c.isDark) 0.26f else 0.32f
    val breath = rememberBreath()
    Canvas(modifier.fillMaxSize()) {
        val fade = alpha()
        if (fade <= 0f) return@Canvas
        val hd = hold()
        val fl = flash()
        val col = colors.heat(heat + (1f - heat) * hd)
        val a = ((base * (0.8f + 0.2f * breath.value)) + 0.3f * hd + 0.35f * fl).coerceAtMost(1f) * fade
        val d = (64f + 50f * hd + 30f * fl).dp.toPx().coerceAtMost(size.minDimension / 2f)
        val stops = arrayOf(0f to col.copy(alpha = a), 0.35f to col.copy(alpha = a * 0.35f), 1f to col.copy(alpha = 0f))
        val (wd, ht) = size.width to size.height
        drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = d), Offset.Zero, Size(wd, d))
        drawRect(Brush.verticalGradient(*stops, startY = ht, endY = ht - d), Offset(0f, ht - d), Size(wd, d))
        drawRect(Brush.horizontalGradient(*stops, startX = 0f, endX = d), Offset.Zero, Size(d, ht))
        drawRect(Brush.horizontalGradient(*stops, startX = wd, endX = wd - d), Offset(wd - d, 0f), Size(d, ht))
    }
}
