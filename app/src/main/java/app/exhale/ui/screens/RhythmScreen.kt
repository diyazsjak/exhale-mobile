package app.exhale.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.exhale.domain.DayFilter
import app.exhale.domain.Stats
import app.exhale.domain.Trigger
import app.exhale.ui.ExhaleState
import app.exhale.ui.components.Eyebrow
import app.exhale.ui.components.SegmentRow
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import java.time.Duration
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun RhythmScreen(state: ExhaleState) {
    val zone = ZoneId.systemDefault()
    var period by remember { mutableIntStateOf(0) }
    var filter by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }

    val offset = if (period == 0) 0 else 30
    val hours = remember(state.logs, period, filter, state.today) {
        Stats.hourly(state.logs, state.now, zone, 30, offset, DayFilter.entries[filter])
    }
    val windowStart = state.now.minus(Duration.ofDays((30 + offset).toLong()))
    val windowEnd = state.now.minus(Duration.ofDays(offset.toLong()))
    val windowLogs = state.logs.filter { it.at >= windowStart && it.at < windowEnd }
    val triggers = Stats.triggerCounts(windowLogs, windowStart)
    val avg = hours.sum()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Rhythm · 30 days", Modifier.weight(1f))
            Eyebrow("avg %.1f/day".format(avg))
        }
        Text("When you light up", style = ExType.title, color = Ex.Ink)
        SegmentRow(listOf("Last 30 days", "30 days before"), period, { period = it; selected = null })
        SegmentRow(DayFilter.entries.map { it.label }, filter, { filter = it; selected = null })

        SmokeClock(hours, selected) { selected = if (selected == it) null else it }

        val sel = selected
        if (sel != null) {
            val top = windowLogs.filter { it.at.atZone(zone).hour == sel && it.trigger != null }
                .groupingBy { it.trigger!! }.eachCount().maxByOrNull { it.value }?.key
            HourCard(sel, hours[sel], top)
        } else {
            Text("Tap a wedge to see that hour.", style = ExType.small, color = Ex.Mute)
        }

        Eyebrow("Triggers")
        if (triggers.isEmpty()) {
            Text("Tag cigarettes after logging to see what drives them.", style = ExType.small, color = Ex.Mute)
        }
        val max = triggers.maxOfOrNull { it.second } ?: 1
        triggers.take(6).forEach { (t, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.label, style = ExType.small, color = Ex.Ink, modifier = Modifier.width(88.dp))
                Box(Modifier.weight(1f).height(8.dp)) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(n / max.toFloat())
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(Ex.Ember, Ex.Flare)))
                    )
                }
                Text("$n", style = ExType.dataSmall, color = Ex.Mute, modifier = Modifier.width(40.dp).padding(start = 8.dp))
            }
        }
        val survived = state.urges.count { it.startedAt >= windowStart && it.startedAt < windowEnd && it.outcome.name == "SURVIVED" }
        Text("$survived craving waves ridden out in this period.", style = ExType.small, color = Ex.Oxy)
    }
}

@Composable
private fun HourCard(hour: Int, perDay: Float, top: Trigger?) {
    val label = hourLabel(hour)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Ex.Line, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Eyebrow("$label – ${hourLabel((hour + 1) % 24)}")
        Text("%.1f per day".format(perDay), style = ExType.data, color = Ex.Ink)
        if (top != null) {
            Text("Mostly ${top.label.lowercase()}.", style = ExType.body, color = Ex.Mute)
            Text("Plan: if ${top.label.lowercase()} around $label, then 10 slow breaths first.", style = ExType.small, color = Ex.Oxy)
        }
    }
}

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12 am"
    h < 12 -> "$h am"
    h == 12 -> "12 pm"
    else -> "${h - 12} pm"
}

/** 24-hour radial heatmap. Hour 0 at the top, clockwise. */
@Composable
private fun SmokeClock(hours: FloatArray, selected: Int?, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val max = (hours.maxOrNull() ?: 0f).coerceAtLeast(0.01f)
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = "24-hour heatmap. Busiest hour ${hourLabel(peak)}." }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val d = hypot(pos.x - c.x, pos.y - c.y)
                    if (d < minOf(size.width, size.height) * 0.2f) return@detectTapGestures
                    var a = atan2(pos.x - c.x, -(pos.y - c.y)) // 0 at top, clockwise
                    if (a < 0) a += (2 * PI).toFloat()
                    onSelect(((a / (2 * PI)) * 24).toInt().coerceIn(0, 23))
                }
            }
    ) {
        val c = center
        val r2 = size.minDimension / 2f - 4.dp.toPx()
        val r1 = r2 * 0.46f
        val gap = 0.012f
        for (i in 0 until 24) {
            val t = hours[i] / max
            val a0 = (i / 24f) * 2 * PI.toFloat() + gap
            val a1 = ((i + 1) / 24f) * 2 * PI.toFloat() - gap
            fun p(r: Float, a: Float) = Offset(c.x + sin(a) * r, c.y - cos(a) * r)
            val path = Path().apply {
                moveTo(p(r1, a0).x, p(r1, a0).y)
                var a = a0
                while (a < a1) { val q = p(r2, a); lineTo(q.x, q.y); a += 0.02f }
                lineTo(p(r2, a1).x, p(r2, a1).y)
                a = a1
                while (a > a0) { val q = p(r1, a); lineTo(q.x, q.y); a -= 0.02f }
                close()
            }
            val color = when {
                t > 0.75f -> Ex.Flare
                t > 0.15f -> Ex.Ember
                else -> Ex.Line
            }
            val alpha = if (t > 0.15f) 0.35f + t * 0.65f else 1f
            drawPath(path, color.copy(alpha = alpha))
            if (selected == i) drawPath(path, Ex.Ink, style = Stroke(2.dp.toPx()))
        }
        listOf(0 to "00", 6 to "06", 12 to "12", 18 to "18").forEach { (h, l) ->
            val a = h / 24f * 2 * PI.toFloat()
            val r = r1 * 0.72f
            val m = measurer.measure(l, ExType.dataSmall.copy(color = Ex.Mute))
            drawText(m, topLeft = Offset(c.x + sin(a) * r - m.size.width / 2f, c.y - cos(a) * r - m.size.height / 2f))
        }
    }
}
