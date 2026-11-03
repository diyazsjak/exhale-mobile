package app.exhale.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import app.exhale.domain.UrgeOutcome
import app.exhale.ui.ExhaleState
import app.exhale.ui.components.ExBadge
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.Fill
import app.exhale.ui.components.Overline
import app.exhale.ui.components.ScreenTitle
import app.exhale.ui.components.SegmentedControl
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSpace
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
    val c = Ex.colors
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

    ExScreen(
        header = {
            Overline("Rhythm · 30 days")
            Fill()
            ExBadge("avg %.1f a day".format(hours.sum()))
        },
    ) {
        ScreenTitle("When you light up")
        SegmentedControl(listOf("Last 30 days", "30 days before"), period, { period = it; selected = null })
        SegmentedControl(DayFilter.entries.map { it.label }, filter, { filter = it; selected = null })
        SmokeClock(hours, selected, Modifier.padding(vertical = ExSpace.xs)) { selected = if (selected == it) null else it }

        val sel = selected
        if (sel != null) {
            val top = windowLogs.filter { it.at.atZone(zone).hour == sel && it.trigger != null }
                .groupingBy { it.trigger!! }.eachCount().maxByOrNull { it.value }?.key
            HourCard(sel, hours[sel], top)
        } else {
            Text("Tap an hour on the clock to see it up close.", style = ExType.caption, color = c.inkSoft)
        }

        ExCard {
            Overline("Triggers")
            if (triggers.isEmpty()) {
                Text("Tag cigarettes after logging to see what drives them.", style = ExType.caption, color = c.inkSoft)
            }
            val max = triggers.maxOfOrNull { it.second } ?: 1
            triggers.take(6).forEach { (t, n) ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.label, style = ExType.labelSmall, color = c.ink, modifier = Modifier.width(92.dp))
                    Box(Modifier.weight(1f).height(10.dp).clip(ExRadius.pill).background(c.surfaceHigh)) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(n / max.toFloat())
                                .clip(ExRadius.pill)
                                .background(Brush.horizontalGradient(listOf(c.ember, c.flare)))
                        )
                    }
                    Text("$n", style = ExType.numberSmall, color = c.inkSoft, modifier = Modifier.width(44.dp).padding(start = 10.dp))
                }
            }
        }
        val survived = state.urges.count { it.startedAt >= windowStart && it.startedAt < windowEnd && it.outcome == UrgeOutcome.SURVIVED }
        Text("$survived craving waves ridden out in this period.", style = ExType.body, color = c.oxyInk)
    }
}

@Composable
private fun HourCard(hour: Int, perDay: Float, top: Trigger?) {
    val c = Ex.colors
    val label = hourLabel(hour)
    ExCard {
        Overline("$label – ${hourLabel((hour + 1) % 24)}")
        Text("%.1f a day".format(perDay), style = ExType.headline, color = c.ink)
        if (top != null) {
            Text("Mostly ${top.label.lowercase()}.", style = ExType.body, color = c.inkSoft)
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
private fun SmokeClock(hours: FloatArray, selected: Int?, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val c = Ex.colors
    val measurer = rememberTextMeasurer()
    val max = (hours.maxOrNull() ?: 0f).coerceAtLeast(0.01f)
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
    val empty = c.surface
    val warm = c.ember
    val hot = c.flare
    val outline = c.ink
    val labelStyle = ExType.numberSmall.copy(color = c.inkSoft)
    androidx.compose.foundation.layout.Box(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = "24-hour heatmap. Busiest hour ${hourLabel(peak)}." }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        if (hypot(pos.x - cx, pos.y - cy) < minOf(size.width, size.height) * 0.2f) return@detectTapGestures
                        var a = atan2(pos.x - cx, -(pos.y - cy))
                        if (a < 0) a += (2 * PI).toFloat()
                        onSelect(((a / (2 * PI)) * 24).toInt().coerceIn(0, 23))
                    }
                }
        ) {
            val ctr = center
            val r2 = size.minDimension / 2f - 4.dp.toPx()
            val r1 = r2 * 0.5f
            val gap = 0.02f
            for (i in 0 until 24) {
                val t = hours[i] / max
                val a0 = (i / 24f) * 2 * PI.toFloat() + gap
                val a1 = ((i + 1) / 24f) * 2 * PI.toFloat() - gap
                fun p(r: Float, a: Float) = Offset(ctr.x + sin(a) * r, ctr.y - cos(a) * r)
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
                    t > 0.75f -> hot
                    t > 0.15f -> warm.copy(alpha = 0.3f + t * 0.7f)
                    else -> empty
                }
                drawPath(path, color)
                if (selected == i) drawPath(path, outline, style = Stroke(2.dp.toPx()))
            }
            listOf(0 to "00", 6 to "06", 12 to "12", 18 to "18").forEach { (h, l) ->
                val a = h / 24f * 2 * PI.toFloat()
                val r = r1 * 0.72f
                val m = measurer.measure(l, labelStyle)
                drawText(m, topLeft = Offset(ctr.x + sin(a) * r - m.size.width / 2f, ctr.y - cos(a) * r - m.size.height / 2f))
            }
        }
    }
}
