package app.exhale.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.exhale.domain.Milestones
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.components.Eyebrow
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val x: Float, var y: Float, val v: Float, val r: Float, val k: Float)

@Composable
fun AirScreen(state: ExhaleState) {
    val clear by animateFloatAsState(state.clearFraction, tween(1500), label = "clear")
    val since = state.sinceLast
    Box(Modifier.fillMaxSize()) {
        ParticleField(clear, Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Eyebrow("Recovery · if you stop now")
            Text(
                if (clear > 0.5f) "The air is clearing." else "Every gap counts.",
                style = ExType.hero.copy(fontSize = ExType.title.fontSize * 1.4f), color = Ex.Ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text(Fmt.gap(since), style = ExType.data, color = Ex.Oxy)
                    Text("since last", style = ExType.small, color = Ex.Mute)
                }
                Column {
                    Text(Fmt.gap(state.longestGapWeek), style = ExType.data, color = Ex.Ink)
                    Text("longest gap, 7 days", style = ExType.small, color = Ex.Mute)
                }
            }
            Box(Modifier.padding(top = 120.dp))
            Milestones.all.forEach { m ->
                val unlocked = since == null || since >= m.after
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ex.Void.copy(alpha = 0.78f))
                        .border(1.dp, if (unlocked) Ex.Oxy.copy(alpha = 0.6f) else Ex.Line, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text(m.label, style = ExType.dataSmall, color = if (unlocked) Ex.Oxy else Ex.Mute, modifier = Modifier.width(76.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.text, style = ExType.small, color = if (unlocked) Ex.Ink else Ex.Mute)
                        if (!unlocked && since != null) {
                            Text("in ${Fmt.gap(m.after.minus(since))}", style = ExType.dataSmall, color = Ex.Mute)
                        }
                    }
                }
            }
            Text("Source: WHO, Health benefits of smoking cessation.", style = ExType.small, color = Ex.Mute)
        }
    }
}

/** Ash-grey particles turn into oxygen sparks as [clear] goes 0 → 1. */
@Composable
private fun ParticleField(clear: Float, modifier: Modifier) {
    val particles = remember {
        val rnd = Random(7)
        List(260) { Particle(rnd.nextFloat(), rnd.nextFloat(), 0.004f + rnd.nextFloat() * 0.01f, 1f + rnd.nextFloat() * 2.6f, rnd.nextFloat()) }
    }
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                val dt = (now - last) / 1000f
                last = now
                particles.forEach { p -> p.y -= p.v * dt * 1.2f; if (p.y < -0.02f) p.y = 1.02f }
                time = now
            }
        }
    }
    Canvas(modifier) {
        drawRect(Brush.radialGradient(listOf(Color(0xFF0F3A4A), Ex.Void), Offset(size.width / 2, size.height * 0.45f), size.maxDimension * 0.7f))
        val t = time / 1800f
        particles.forEach { p ->
            val on = p.k < clear
            val x = (p.x + sin(t + p.k * 6f) * 0.006f) * size.width
            val c = if (on) Ex.Oxy.copy(alpha = 0.3f + p.k * 0.5f) else Ex.Mute.copy(alpha = 0.25f)
            drawCircle(c, p.r.dp.toPx(), Offset(x, p.y * size.height))
        }
    }
}
