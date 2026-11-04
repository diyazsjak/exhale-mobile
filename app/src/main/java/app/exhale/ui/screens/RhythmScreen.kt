package app.exhale.ui.screens

import androidx.compose.foundation.Canvas
import java.util.Locale
import java.time.format.TextStyle
import java.time.LocalDate
import app.exhale.ui.components.WeekStrip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateFormat
import app.exhale.domain.DayFilter
import app.exhale.domain.Stats
import app.exhale.domain.Period
import app.exhale.domain.TopTrigger
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
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun RhythmScreen(state: ExhaleState, initialDay: LocalDate? = null) {
    val c = Ex.colors
    val zone = ZoneId.systemDefault()
    val h24 = DateFormat.is24HourFormat(LocalContext.current)
    var period by remember { mutableIntStateOf(0) }
    var filter by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    // A day picked in the week strip filters the whole screen to that day.
    var day by remember { mutableStateOf(initialDay) }

    val dataStart = remember(state.logs, state.settings.planStart) { Stats.dataStart(state.settings.planStart, state.logs, zone) }
    val dayFilter = DayFilter.entries[filter]
    val window = day?.let { Period.day(it) } ?: Period.lastDays(state.today, 30, if (period == 0) 0 else 30, dayFilter)
    val hours = remember(state.logs, window, state.now, dataStart) { Stats.hourly(state.logs, window, state.now, zone, dataStart) }
    val windowLogs = state.logs.filter { window.contains(it.at, zone) }
    val triggers = Stats.triggers(windowLogs)

    val d = day
    val dayName = d?.let { if (it == state.today) "Today" else it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    ExScreen(
        header = {
            Overline(
                when {
                    dayName != null -> "Rhythm · $dayName"
                    period == 0 -> "Rhythm · Last 30 days"
                    else -> "Rhythm · 30 days before"
                }
            )
            Fill()
            ExBadge(
                if (d != null) "${windowLogs.size} that day"
                else "avg %.1f a %s".format(hours.sum(), when (dayFilter) {
                    DayFilter.ALL -> "day"
                    DayFilter.WEEKDAYS -> "weekday"
                    DayFilter.WEEKENDS -> "weekend day"
                })
            )
        },
    ) {
        ScreenTitle("When you light up")
        if (state.week.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
                Row {
                    Text("This week", style = ExType.caption, color = c.inkSoft, modifier = Modifier.weight(1f))
                    val dot = state.week.firstOrNull { it.date == d }
                    val count = dot?.count ?: 0
                    Text(
                        when {
                            dot == null || dayName == null -> "Tap a day to see its hours"
                            count > dot.allowance -> "$dayName · $count, ${count - dot.allowance} borrowed"
                            else -> "$dayName · $count of ${dot.allowance}"
                        },
                        style = ExType.caption, color = if (d == null) c.inkFaint else c.ink,
                    )
                }
                WeekStrip(state.week, state.today, selected = day, onSelect = { p -> day = if (day == p) null else p; selected = null })
            }
        }
        SegmentedControl(listOf("Last 30 days", "30 days before"), if (day == null) period else -1, { period = it; selected = null; day = null })
        SegmentedControl(DayFilter.entries.map { it.label }, if (day == null) filter else -1, { filter = it; selected = null; day = null })
        SmokeClock(hours, selected, h24, Modifier.padding(vertical = ExSpace.xs)) { selected = if (selected == it) null else it }

        val sel = selected
        if (sel != null) {
            val top = Stats.topTrigger(windowLogs.filter { it.at.atZone(zone).hour == sel })
            HourCard(sel, hours[sel], top, h24, singleDay = day != null)
        } else {
            Text("Tap an hour on the clock to see it up close.", style = ExType.caption, color = c.inkSoft)
        }

        ExCard {
            Overline("Triggers")
            if (triggers.tagged == 0) {
                Text("Tag cigarettes after logging to see what drives them.", style = ExType.caption, color = c.inkSoft)
            }
            val max = triggers.counts.maxOfOrNull { it.second } ?: 1
            triggers.counts.take(6).forEach { (t, n) ->
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
            if (triggers.tagged > 0) {
                Text("${triggers.tagged} of ${triggers.total} tagged.", style = ExType.caption, color = c.inkSoft)
            }
        }
        val survived = state.urges.count { window.contains(it.startedAt, zone) && it.outcome == UrgeOutcome.SURVIVED }
        Text(
            when {
                survived == 0 -> "Ride out a craving with \"Help me wait\" and it counts here."
                d != null -> "$survived craving wave${if (survived == 1) "" else "s"} ridden out that day."
                else -> "$survived craving wave${if (survived == 1) "" else "s"} ridden out in this period."
            },
            style = ExType.body, color = if (survived == 0) c.inkSoft else c.oxyInk,
        )
    }
}

@Composable
private fun HourCard(hour: Int, perDay: Float, top: TopTrigger?, h24: Boolean, singleDay: Boolean = false) {
    val c = Ex.colors
    ExCard {
        Overline("${hourLabel(hour, h24)} – ${hourLabel((hour + 1) % 24, h24)}")
        val n = perDay.toInt()
        Text(
            when {
                perDay == 0f -> "None in this hour"
                singleDay -> "$n cigarette${if (n == 1) "" else "s"}"
                else -> "%.1f a day".format(perDay)
            },
            style = ExType.headline, color = c.ink,
        )
        if (top != null) {
            val name = top.trigger.label.lowercase()
            Text(
                "${if (top.mostly) "Mostly" else "Most often"} $name · ${top.count} of ${top.tagged} tagged.",
                style = ExType.body, color = c.inkSoft,
            )
        }
    }
}

/** "13:00" or "1 pm" hour label, following the phone's 12/24-hour setting. */
private fun hourLabel(h: Int, h24: Boolean): String = when {
    h24 -> "%02d:00".format(h)
    h == 0 -> "12 am"
    h < 12 -> "$h am"
    h == 12 -> "12 pm"
    else -> "${h - 12} pm"
}

/** 24-hour radial heatmap. Hour 0 at the top, clockwise. */
@Composable
private fun SmokeClock(hours: FloatArray, selected: Int?, h24: Boolean, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
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
                .semantics { contentDescription = if (hours.all { it == 0f }) "24-hour heatmap. Nothing logged in this period." else "24-hour heatmap. Busiest hour ${hourLabel(peak, h24)}, %.1f a day.".format(hours[peak]) }
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
                    // Any smoking at all shows; only an hour with none is empty.
                    hours[i] > 0f -> warm.copy(alpha = 0.3f + t * 0.7f)
                    else -> empty
                }
                drawPath(path, color)
                if (selected == i) drawPath(path, outline, style = Stroke(2.dp.toPx()))
            }
            (if (h24) listOf(0 to "00", 6 to "06", 12 to "12", 18 to "18") else listOf(0 to "12a", 6 to "6a", 12 to "12p", 18 to "6p")).forEach { (h, l) ->
                val a = h / 24f * 2 * PI.toFloat()
                val r = r1 * 0.72f
                val m = measurer.measure(l, labelStyle)
                drawText(m, topLeft = Offset(ctr.x + sin(a) * r - m.size.width / 2f, ctr.y - cos(a) * r - m.size.height / 2f))
            }
        }
    }
}
