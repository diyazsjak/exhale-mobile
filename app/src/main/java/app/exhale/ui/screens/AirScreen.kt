package app.exhale.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.exhale.domain.Milestones
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.Overline
import app.exhale.ui.components.ScreenTitle
import app.exhale.ui.components.Stat
import app.exhale.ui.components.VSpace
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val x: Float, var y: Float, val v: Float, val r: Float, val k: Float)

@Composable
fun AirScreen(state: ExhaleState) {
    val c = Ex.colors
    val clear by animateFloatAsState(state.clearFraction, tween(1500), label = "clear")
    val since = state.sinceLast
    ExScreen(
        header = { Overline("Recovery · if you stop now") },
        background = { ParticleField(clear, Modifier.fillMaxSize()) },
    ) {
        ScreenTitle(if (clear > 0.5f) "The air\nis clearing." else "Every gap\ncounts.")
        Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.xl)) {
            Stat(Fmt.gap(since), "since your last one", color = c.oxyInk)
            Stat(Fmt.gap(state.longestGapWeek), "longest gap, 7 days")
        }
        VSpace(96.dp)
        Milestones.all.forEach { m ->
            val unlocked = since == null || since >= m.after
            ExCard(color = c.surface.copy(alpha = 0.88f), padding = ExSpace.md) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.label, style = ExType.numberSmall, color = if (unlocked) c.oxyInk else c.inkFaint, modifier = Modifier.width(76.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.text, style = ExType.body, color = if (unlocked) c.ink else c.inkSoft)
                        if (!unlocked && since != null) {
                            Text("in ${Fmt.gap(m.after.minus(since))}", style = ExType.caption, color = c.inkFaint)
                        }
                    }
                    if (unlocked) Icon(ExIcons.Check, "Reached", Modifier.size(20.dp), tint = c.oxyInk)
                }
            }
        }
        Text("Source: WHO, Health benefits of smoking cessation.", style = ExType.caption, color = c.inkFaint)
    }
}

/** Grey particles turn into oxygen sparks as [clear] goes 0 → 1. Drawn over the shared background. */
@Composable
private fun ParticleField(clear: Float, modifier: Modifier) {
    val c = Ex.colors
    val particles = remember {
        val rnd = Random(7)
        List(220) { Particle(rnd.nextFloat(), rnd.nextFloat(), 0.004f + rnd.nextFloat() * 0.01f, 1f + rnd.nextFloat() * 2.4f, rnd.nextFloat()) }
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
    val on = c.oxy
    val off = c.inkFaint
    Canvas(modifier) {
        val t = time / 1800f
        particles.forEach { p ->
            val x = (p.x + sin(t + p.k * 6f) * 0.006f) * size.width
            val col = if (p.k < clear) on.copy(alpha = 0.25f + p.k * 0.5f) else off.copy(alpha = 0.3f)
            drawCircle(col, p.r.dp.toPx(), Offset(x, p.y * size.height))
        }
    }
}
