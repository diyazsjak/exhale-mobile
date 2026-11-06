package app.exhale.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.exhale.domain.BreathPhase
import app.exhale.domain.Trigger
import app.exhale.domain.UrgeOutcome
import app.exhale.domain.WAVE_SECONDS
import app.exhale.domain.breathAt
import app.exhale.ui.Fmt
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.ExBadge
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExIconButton
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.HeaderLabel
import app.exhale.ui.components.Overline
import app.exhale.ui.components.TriggerChips
import app.exhale.ui.components.rememberHaptics
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.Instant
import kotlin.math.exp
import kotlin.math.sin

/** Wave height 0..1 at progress x (0..1): a crest around 46%, ripples on top. */
private fun waveY(x: Float, phase: Float): Float {
    val env = exp(-((x - 0.46f) / 0.23f).let { it * it })
    return env * 0.85f + sin(x * 26f + phase) * 0.03f * (0.4f + env)
}

@Composable
fun UrgeWaveScreen(
    onFinish: (started: Instant, outcome: UrgeOutcome, trigger: Trigger?) -> Unit,
    onClose: () -> Unit,
    startAt: Float = 0f,
    /** The user's chips, in order (Settings → Triggers). */
    triggers: List<Trigger> = Trigger.BUILT_IN,
    /** "Your own": adds a trigger by name and returns it so it can be picked; null if it couldn't be. */
    onAddTrigger: (String) -> Trigger? = { null },
) {
    val c = Ex.colors
    val started = remember { Instant.now() }
    val haptics = rememberHaptics()
    var elapsed by remember { mutableFloatStateOf(startAt) }
    var trigger by remember { mutableStateOf<Trigger?>(null) }
    var lastPhase by remember { mutableStateOf(BreathPhase.IN) }

    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (elapsed < WAVE_SECONDS) {
            withFrameNanos { elapsed = (startAt + (it - t0) / 1e9f).coerceAtMost(WAVE_SECONDS) }
        }
    }
    val breath = breathAt(elapsed)
    val phase = breath.phase
    LaunchedEffect(phase) { if (phase != lastPhase) { haptics.breath(); lastPhase = phase } }

    val done = elapsed >= WAVE_SECONDS
    val headline = when {
        done -> "The wave passed.\nStill want it?"
        elapsed < 100 -> "This wave peaks\naround minute two."
        elapsed < 150 -> "You're at the crest.\nIt only goes down from here."
        else -> "It's fading.\nKeep breathing."
    }
    val waveColors = listOf(c.sky, c.flare, c.oxy)
    val ghost = c.line
    val dotColor = c.ink
    val halo = c.oxy

    ExScreen(
        scroll = false,
        bottomClearance = ExSpace.md,
        header = {
            ExIconButton(ExIcons.Close, "Close", onClose)
            HeaderLabel("Craving")
            ExBadge(Fmt.mmss(elapsed.toLong()))
        },
    ) {
        Text(headline, style = ExType.title, color = c.ink)
        val p = elapsed / WAVE_SECONDS
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).semantics {
                contentDescription = "Craving wave, ${(p * 100).toInt()} percent through"
            }
        ) {
            val w = size.width
            val h = size.height
            // Inset so the dot and its breathing halo never clip at the edges.
            val inset = 30.dp.toPx()
            fun pt(x: Float, ph: Float) = Offset(inset + x * (w - 2 * inset), h - 30.dp.toPx() - waveY(x, ph) * (h - 60.dp.toPx()))
            fun line(ph: Float) = Path().apply {
                moveTo(pt(0f, ph).x, pt(0f, ph).y)
                var x = 0f
                while (x <= 1f) { val q = pt(x, ph); lineTo(q.x, q.y); x += 0.01f }
            }
            drawPath(line(0f), ghost, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))))
            val ph = elapsed * 1.6f
            drawPath(line(ph), Brush.horizontalGradient(waveColors), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            val dot = pt(p, ph)
            // The halo swells with the in-breath, rests on the hold and shrinks on the out-breath.
            val f = breath.fullness
            drawCircle(halo.copy(alpha = 0.12f), 18.dp.toPx() + f * 18.dp.toPx(), dot)
            drawCircle(halo.copy(alpha = 0.28f), 12.dp.toPx() + f * 10.dp.toPx(), dot)
            drawCircle(dotColor, 8.dp.toPx(), dot)
        }
        BreathGuide(elapsed, done, Modifier.fillMaxWidth())
        Overline("What's behind it? Optional", Modifier.padding(top = ExSpace.xs))
        TriggerChips(
            triggers, trigger, { trigger = if (trigger == it) null else it },
            onAdd = { name -> onAddTrigger(name)?.let { trigger = it } },
        )
        Spacer(Modifier.weight(1f))
        if (done) {
            ExButton("No. I rode it out.", { onFinish(started, UrgeOutcome.SURVIVED, trigger) }, Modifier.fillMaxWidth(), style = ButtonStyle.Accent)
            ExButton("Yes, smoke anyway", { onFinish(started, UrgeOutcome.SMOKED, trigger) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
                ExButton("I'm okay now", { onFinish(started, UrgeOutcome.SURVIVED, trigger) }, Modifier.weight(1f), style = ButtonStyle.Accent)
                ExButton("Smoke anyway", { onFinish(started, UrgeOutcome.SMOKED, trigger) }, Modifier.weight(1f), style = ButtonStyle.Secondary)
            }
        }
    }
}
