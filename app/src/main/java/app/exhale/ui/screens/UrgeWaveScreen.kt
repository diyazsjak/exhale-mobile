package app.exhale.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.exhale.domain.Trigger
import app.exhale.domain.UrgeOutcome
import app.exhale.ui.Fmt
import app.exhale.ui.components.Eyebrow
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.TriggerRow
import app.exhale.ui.components.rememberHaptics
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import java.time.Instant
import kotlin.math.exp
import kotlin.math.sin

/** Length of one craving wave. Research: most urges pass within 3–5 minutes. */
const val WAVE_SECONDS = 240f

private enum class BreathPhase(val label: String, val seconds: Float) {
    IN("Inhale", 4f), HOLD("Hold", 2f), OUT("Exhale", 6f)
}

private fun breathAt(t: Float): Pair<BreathPhase, Int> {
    val cycle = BreathPhase.entries.sumOf { it.seconds.toDouble() }.toFloat()
    var x = t % cycle
    for (p in BreathPhase.entries) {
        if (x < p.seconds) return p to (p.seconds - x).toInt() + 1
        x -= p.seconds
    }
    return BreathPhase.OUT to 1
}

/** Wave height 0..1 at progress x (0..1): a crest around 46%, ripples on top. */
private fun waveY(x: Float, phase: Float): Float {
    val env = exp(-((x - 0.46f) / 0.23f).let { it * it })
    return env * 0.85f + sin(x * 26f + phase) * 0.035f * (0.4f + env)
}

@Composable
fun UrgeWaveScreen(onFinish: (started: Instant, outcome: UrgeOutcome, trigger: Trigger?) -> Unit, onClose: () -> Unit) {
    val started = remember { Instant.now() }
    val haptics = rememberHaptics()
    var elapsed by remember { mutableFloatStateOf(0f) }
    var trigger by remember { mutableStateOf<Trigger?>(null) }
    var lastPhase by remember { mutableStateOf(BreathPhase.IN) }

    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (elapsed < WAVE_SECONDS) {
            withFrameNanos { elapsed = ((it - t0) / 1e9f).coerceAtMost(WAVE_SECONDS) }
        }
    }
    val (phase, count) = breathAt(elapsed)
    LaunchedEffect(phase) { if (phase != lastPhase) { haptics.breath(); lastPhase = phase } }

    val done = elapsed >= WAVE_SECONDS
    val headline = when {
        done -> "The wave passed. Still want it?"
        elapsed < 100 -> "This wave peaks around minute 2."
        elapsed < 150 -> "You're at the crest. It only goes down from here."
        else -> "It's fading. Keep breathing."
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ex.Void, Ex.Panel, Color(0xFF0F3A4A))))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Craving", Modifier.weight(1f))
            Text(Fmt.mmss(elapsed.toLong()), style = ExType.dataSmall, color = Ex.Ink)
            Text(
                "CLOSE", style = ExType.eyebrow, color = Ex.Mute,
                modifier = Modifier.padding(start = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(8.dp),
            )
        }
        Text(headline, style = ExType.title, color = Ex.Ink)

        val p = elapsed / WAVE_SECONDS
        Canvas(
            Modifier.fillMaxWidth().height(200.dp).semantics {
                contentDescription = "Craving wave, ${(p * 100).toInt()} percent through"
            }
        ) {
            val w = size.width
            val h = size.height
            fun pt(x: Float, ph: Float) = Offset(x * w, h - 12.dp.toPx() - waveY(x, ph) * (h - 40.dp.toPx()))
            val ghost = Path().apply { moveTo(0f, pt(0f, 0f).y); var x = 0f; while (x <= 1f) { lineTo(x * w, pt(x, 0f).y); x += 0.01f } }
            drawPath(ghost, Ex.Line, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))))
            val ph = elapsed * 1.6f
            val live = Path().apply { moveTo(0f, pt(0f, ph).y); var x = 0f; while (x <= 1f) { lineTo(x * w, pt(x, ph).y); x += 0.01f } }
            drawPath(live, Brush.horizontalGradient(listOf(Ex.Sky, Ex.Flare, Ex.Oxy)), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            val dot = pt(p, ph)
            val pulse = when (phase) { BreathPhase.IN -> 1f - count / 4f; BreathPhase.HOLD -> 1f; BreathPhase.OUT -> count / 6f }
            drawCircle(Ex.Oxy.copy(alpha = 0.25f), 18.dp.toPx() + pulse * 10.dp.toPx(), dot)
            drawCircle(Ex.Ink, 8.dp.toPx(), dot)
        }

        Text(
            if (done) "Breathe normally" else "${phase.label} · $count",
            style = ExType.title, color = Ex.Oxy,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Eyebrow("What's behind it? (optional)")
        TriggerRow(trigger, { trigger = if (trigger == it) null else it })

        Spacer(Modifier.weight(1f))
        if (done) {
            ExButton("No. I rode it out.", { onFinish(started, UrgeOutcome.SURVIVED, trigger) }, Modifier.fillMaxWidth())
            ExButton("Yes, log one", { onFinish(started, UrgeOutcome.SMOKED, trigger) }, Modifier.fillMaxWidth(), color = Ex.Ember, filled = false)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExButton("I'm okay now", { onFinish(started, UrgeOutcome.SURVIVED, trigger) }, Modifier.weight(1f))
                ExButton("Smoke anyway", { onFinish(started, UrgeOutcome.SMOKED, trigger) }, Modifier.weight(1f), color = Ex.Ember, filled = false)
            }
        }
    }
}
