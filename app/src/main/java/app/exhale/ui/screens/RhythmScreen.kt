package app.exhale.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.exhale.domain.DayFilter
import app.exhale.domain.Period
import app.exhale.domain.PlanMath
import app.exhale.domain.Stats
import app.exhale.domain.TopTrigger
import app.exhale.domain.Trigger
import app.exhale.domain.UrgeOutcome
import app.exhale.ui.ExhaleState
import app.exhale.ui.components.ActiveFilter
import app.exhale.ui.components.ButtonSize
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.ExBadge
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExLink
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.Fill
import app.exhale.ui.components.Overline
import app.exhale.ui.components.ScreenTitle
import app.exhale.ui.components.SegmentedControl
import app.exhale.ui.components.WeekStrip
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Days of history (in the period and the one before) needed before averages and comparisons show. */
private const val MIN_DAYS = 7

/**
 * Rhythm: when the user smokes over the last 30 days, or on one picked [day] (hoisted so History
 * can open a day here). Tapping the comparison card lights up the thin "30 days before" lines on
 * the clock; picking an hour narrows the card to that hour.
 */
@Composable
fun RhythmScreen(
    state: ExhaleState,
    day: LocalDate? = null,
    onDay: (LocalDate?) -> Unit = {},
    onHistory: () -> Unit = {},
    onUrge: () -> Unit = {},
    initialHour: Int? = null,
    initialLines: Boolean = false,
) {
    val c = Ex.colors
    val zone = ZoneId.systemDefault()
    val h24 = DateFormat.is24HourFormat(LocalContext.current)
    var filter by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(initialHour) }
    var lines by remember { mutableStateOf(initialLines) }
    var idea by remember { mutableIntStateOf(0) }

    val dataStart = state.dataStart
    val dayFilter = DayFilter.entries[filter]
    val minute = state.now.epochSecond / 60
    val window = day?.let { Period.day(it) } ?: Period.lastDays(state.today, 30, 0, dayFilter)
    val before = Period.lastDays(state.today, 30, 30, dayFilter)
    val hours = remember(state.logs, window, minute, dataStart) { Stats.hourly(state.logs, window, state.now, zone, dataStart) }
    val covered = Stats.coveredDays(window, state.now, zone, dataStart)
    val compare = day == null && Stats.coveredDays(before, state.now, zone, dataStart) >= MIN_DAYS
    val beforeHours = remember(state.logs, before, dataStart, compare) { if (compare) Stats.hourly(state.logs, before, state.now, zone, dataStart) else null }
    val windowLogs = remember(state.logs, window) { state.logs.filter { window.contains(it.at, zone) } }
    val triggers = remember(windowLogs) { Stats.triggers(windowLogs) }
    val peaks = Stats.peaks(hours)
    val unit = when (dayFilter) { DayFilter.ALL -> "a day"; DayFilter.WEEKDAYS -> "a weekday"; DayFilter.WEEKENDS -> "a weekend day" }
    val where = when (dayFilter) { DayFilter.ALL -> ""; DayFilter.WEEKDAYS -> " on weekdays"; DayFilter.WEEKENDS -> " at weekends" }
    val dayName = day?.let { dayName(it, state.today) }
    val label = { h: Int -> hourLabel(h, h24) }

    val insight = when {
        day != null -> when {
            windowLogs.isEmpty() || peaks.isEmpty() -> if (day == state.today) "Nothing logged today." else "Nothing logged on $dayName."
            day == state.today -> "Today, busiest around ${label(peaks[0])}."
            else -> "On $dayName, busiest around ${label(peaks[0])}."
        }
        covered == 0 -> "Log a few and your rhythm shows up here."
        covered < MIN_DAYS -> "$covered day${if (covered == 1) "" else "s"} in. Your rhythm gets clearer after a week or two."
        peaks.isEmpty() -> "Nothing logged$where in the last 30 days."
        else -> "Busiest around ${peaks.joinToString(" and ") { label(it) }}." +
            (Stats.topTrigger(windowLogs)?.let { words(it.trigger)?.most?.let { s -> " $s" } } ?: "")
    }

    ExScreen(
        header = {
            Overline(if (dayName != null) "Rhythm · $dayName" else "Rhythm · Last 30 days")
            Fill()
            ExBadge(if (day != null) "${windowLogs.size} that day" else "avg %.1f %s".format(hours.sum(), unit))
        },
    ) {
        ScreenTitle("When you light up", subtitle = insight)

        Column(verticalArrangement = Arrangement.spacedBy(ExSpace.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last 7 days", style = ExType.caption, color = c.inkSoft)
                Text(
                    if (day != null && state.week.any { it.date == day }) " · tap again to go back" else " · tap a day",
                    style = ExType.caption, color = c.inkFaint, modifier = Modifier.weight(1f),
                )
                ExLink("All history", onHistory)
            }
            WeekStrip(state.week, state.today, selected = day, onSelect = { p ->
                onDay(if (day == p) null else p); selected = null; lines = false
            })
        }

        if (day != null) {
            val count = windowLogs.size
            val allowance = if (day.isBefore(state.settings.planStart)) null
            else PlanMath.allowance(state.settings, day, Stats.countOn(state.logs, day.minusDays(1), zone))
            ActiveFilter(
                when {
                    allowance == null -> "$dayName · $count"
                    count > allowance -> "$dayName · $count, ${count - allowance} borrowed"
                    else -> "$dayName · $count of $allowance"
                },
                "Back to 30 days",
                { onDay(null); selected = null },
            )
        } else {
            SegmentedControl(DayFilter.entries.map { it.label }, filter, { filter = it; selected = null })
        }

        val sel = selected
        val centre = when {
            sel != null -> {
                val v = hours[sel]
                val top = if (day == null) Stats.topTrigger(windowLogs.filter { it.at.atZone(zone).hour == sel }) else null
                Centre(
                    over = hourRange(sel, h24),
                    big = if (v == 0f) "None" else if (day != null) "${v.toInt()}" else "%.1f".format(v),
                    small = when {
                        v == 0f -> "in this hour"
                        day != null -> if (v.toInt() == 1) "cigarette" else "cigarettes"
                        else -> unit
                    },
                    extra = top?.let { "${if (it.mostly) "Mostly" else "Most often"} ${it.trigger.label.lowercase()} · ${it.count} of ${it.tagged} tagged" } ?: "",
                    extraFaint = false,
                )
            }
            peaks.isNotEmpty() -> {
                val v = hours[peaks[0]]
                Centre(
                    over = "Busiest", big = label(peaks[0]),
                    small = if (day != null) "${v.toInt()} cigarette${if (v.toInt() == 1) "" else "s"}" else "%.1f %s".format(v, unit),
                    extra = "Tap an hour", extraFaint = true,
                )
            }
            else -> Centre(over = "", big = "—", small = "nothing logged", extra = "", extraFaint = true)
        }
        SmokeClock(
            hours = hours,
            before = beforeHours,
            selected = sel,
            lines = lines,
            h24 = h24,
            centre = centre,
            description = buildString {
                append("Smoke clock. ")
                if (peaks.isEmpty()) append("Nothing logged.") else append("Busiest around ${label(peaks[0])}. ")
                if (sel != null) append("Showing ${hourRange(sel, h24)}: ${centre.big} ${centre.small}.")
            },
            onTap = { h -> selected = if (h == null || h == selected) null else h },
            modifier = Modifier.padding(vertical = ExSpace.xxs),
        )

        if (beforeHours != null) {
            ComparisonCard(
                hour = sel?.let { hourRange(it, h24) },
                now = if (sel != null) hours[sel] else hours.sum(),
                then = if (sel != null) beforeHours[sel] else beforeHours.sum(),
                unit = unit,
                lines = lines,
                onClick = { lines = !lines },
            )
        } else if (day == null && covered > 0) {
            val tracked = dataStart?.let { ChronoUnit.DAYS.between(it, state.today) + 1 } ?: 0
            val left = dataStart?.let { ChronoUnit.DAYS.between(state.today, it.plusDays(36)) } ?: 0
            ExCard {
                Text(if (tracked <= 30) "Your first month." else "Almost enough to compare.", style = ExType.body, color = c.ink)
                Text(
                    if (dayFilter == DayFilter.ALL && left > 0) "In $left day${if (left == 1L) "" else "s"} you'll see how each hour compares with the 30 days before."
                    else "Once there's more history, you'll see how each hour compares with the 30 days before.",
                    style = ExType.caption, color = c.inkSoft,
                )
            }
        }

        if (day == null && covered >= MIN_DAYS && peaks.isNotEmpty()) {
            val h = peaks[0]
            PlanCard(
                hour = label(h),
                top = Stats.topTrigger(windowLogs.filter { it.at.atZone(zone).hour == h }),
                idea = idea,
                onUrge = onUrge,
                onIdea = { idea++ },
            )
        }

        ExCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Triggers", Modifier.weight(1f))
                if (triggers.tagged > 0) Text("${triggers.tagged} of ${triggers.total} tagged", style = ExType.caption, color = c.inkSoft)
            }
            if (triggers.tagged == 0) {
                Text(
                    when {
                        triggers.total == 0 -> "Nothing logged here yet."
                        day != null -> "Nothing tagged that day."
                        else -> "Tag cigarettes after logging to see what drives them."
                    },
                    style = ExType.caption, color = c.inkSoft,
                )
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
        }

        val survived = state.urges.count { window.contains(it.startedAt, zone) && it.outcome == UrgeOutcome.SURVIVED }
        if (survived > 0) {
            ExCard(color = c.tint(c.oxy)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ExSpace.md)) {
                    Text("$survived", style = ExType.numberLarge, color = c.oxyInk)
                    Text(
                        "craving wave${if (survived == 1) "" else "s"} ridden out ${if (day != null) "that day" else "$where in the last 30 days".trim()}.",
                        style = ExType.body, color = c.ink, modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            ExCard {
                Overline("Craving waves")
                Text("Ride one out with “Help me wait” and it counts here.", style = ExType.body, color = c.ink)
            }
        }
    }
}

/**
 * "1.4 fewer a day than the 30 days before (13.2)", or the same for one [hour]. Tapping it lights
 * up the thin lines on the clock that show the 30 days before.
 */
@Composable
private fun ComparisonCard(hour: String?, now: Float, then: Float, unit: String, lines: Boolean, onClick: () -> Unit) {
    val c = Ex.colors
    val diff = then - now
    val same = abs(diff) < 0.05f
    val text = buildAnnotatedString {
        if (hour != null) append("$hour: ")
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = if (!same && diff > 0) c.oxyInk else c.ink)) {
            append(
                when {
                    same -> if (hour != null) "about the same" else "About the same"
                    diff > 0 -> "%.1f fewer %s".format(diff, unit)
                    else -> "%.1f more %s".format(-diff, unit)
                }
            )
        }
        append(if (same) " as the 30 days before (%.1f).".format(then) else " than the 30 days before (%.1f).".format(then))
    }
    val bar = c.ember.copy(alpha = 0.55f)
    val line = c.ink.copy(alpha = 0.6f)
    ExCard(
        onClick = onClick,
        outlined = lines,
        modifier = Modifier.semantics { stateDescription = if (lines) "Lines shown on the clock" else "Lines not highlighted" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Canvas(Modifier.size(28.dp)) {
                val u = size.width / 28f
                drawRect(bar, Offset(4 * u, 12 * u), Size(6 * u, 12 * u))
                drawRect(bar, Offset(16 * u, 16 * u), Size(6 * u, 8 * u))
                drawLine(line, Offset(2 * u, 8 * u), Offset(12 * u, 8 * u), 2 * u, StrokeCap.Round)
                drawLine(line, Offset(14 * u, 13 * u), Offset(24 * u, 13 * u), 2 * u, StrokeCap.Round)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text, style = ExType.body, color = c.ink)
                Text(
                    if (lines) "Lit up on the clock: each thin line is where that hour was. Tap to go back."
                    else "Tap to see where each hour was on the clock.",
                    style = ExType.caption, color = c.inkSoft,
                )
            }
        }
    }
}

/** An if-then plan for the busiest [hour], with the trigger that comes with it when known. */
@Composable
private fun PlanCard(hour: String, top: TopTrigger?, idea: Int, onUrge: () -> Unit, onIdea: () -> Unit) {
    val c = Ex.colors
    val w = top?.let { words(it.trigger) }
    val ideas = listOf(
        "then I ride one wave first.",
        "then I wait ten minutes, then decide.",
        "then I step outside without one first.",
        "then I have a glass of water first.",
    )
    ExCard {
        Overline("A plan for $hour")
        Text(
            when {
                w == null || top == null -> "$hour is your busiest hour."
                top.mostly -> "${w.noun} comes with most of your $hour ones."
                else -> "${w.noun} comes up most around $hour."
            },
            style = ExType.caption, color = c.inkSoft,
        )
        Text(
            "${w?.ifAt?.invoke(hour) ?: "If it comes at $hour,"}\n${ideas[idea % ideas.size]}",
            style = ExType.headlineItalic, color = c.ink,
            modifier = Modifier.padding(vertical = ExSpace.xxs).semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
            ExButton("Help me wait", onUrge, style = ButtonStyle.Tonal, size = ButtonSize.Medium)
            ExButton("Another idea", onIdea, style = ButtonStyle.Ghost, size = ButtonSize.Medium)
        }
    }
}

/** How each trigger reads in a sentence. Null for [Trigger.OTHER], which says nothing useful. */
private class TriggerWords(val most: String, val noun: String, val ifAt: (String) -> String)

private fun words(t: Trigger): TriggerWords? = when (t) {
    Trigger.COFFEE -> TriggerWords("Coffee comes up most.", "Coffee") { "If it's coffee at $it," }
    Trigger.STRESS -> TriggerWords("Stress comes up most.", "Stress") { "If stress hits at $it," }
    Trigger.AFTER_FOOD -> TriggerWords("Meals come up most.", "A meal") { "If it's after a meal at $it," }
    Trigger.DRINKS -> TriggerWords("Drinks come up most.", "A drink") { "If it's a drink at $it," }
    Trigger.DRIVING -> TriggerWords("Driving comes up most.", "Driving") { "If I'm driving at $it," }
    Trigger.BORED -> TriggerWords("Boredom comes up most.", "Boredom") { "If I'm bored at $it," }
    Trigger.SOCIAL -> TriggerWords("Company comes up most.", "Company") { "If I'm with people at $it," }
    Trigger.OTHER -> null
}

/** "Today", a weekday name within the last week, else "Sun 12 Oct". */
private fun dayName(d: LocalDate, today: LocalDate): String = when {
    d == today -> "Today"
    !d.isBefore(today.minusDays(6)) -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}

/** "13:00" or "1 pm", following the phone's 12/24-hour setting. */
private fun hourLabel(h: Int, h24: Boolean): String = when {
    h24 -> "%02d:00".format(h)
    h == 0 -> "12 am"
    h < 12 -> "$h am"
    h == 12 -> "12 pm"
    else -> "${h - 12} pm"
}

/** "8–9 am", "11 am–12 pm", "08:00–09:00". */
private fun hourRange(h: Int, h24: Boolean): String {
    val a = hourLabel(h, h24)
    val b = hourLabel((h + 1) % 24, h24)
    if (h24) return "$a–$b"
    val (an, ap) = a.split(" ")
    val (bn, bp) = b.split(" ")
    return if (ap == bp) "$an–$bn $bp" else "$a–$b"
}

private data class Centre(val over: String, val big: String, val small: String, val extra: String, val extraFaint: Boolean)

/**
 * 24-hour radial chart ("Smoke Clock"). Hour 0 at the top, clockwise. Each hour is a bar whose
 * length (and warmth) is its average; [before] adds a thin line per hour where it was in the 30
 * days before, lit up when [lines]. The centre shows [centre]: the busiest hour, or the picked one.
 * Tap an hour to pick it, the centre to clear. Screen readers step through hours with actions.
 */
@Composable
private fun SmokeClock(
    hours: FloatArray,
    before: FloatArray?,
    selected: Int?,
    lines: Boolean,
    h24: Boolean,
    centre: Centre,
    description: String,
    onTap: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ex.colors
    val measurer = rememberTextMeasurer()
    val curMax = (hours.maxOrNull() ?: 0f).coerceAtLeast(0.01f)
    val max = maxOf(curMax, before?.maxOrNull() ?: 0f)
    val track = c.surface
    val warm = c.ember
    val hot = c.flare
    val ink = c.ink
    val ghost = if (lines) c.ink else c.ink.copy(alpha = 0.6f)
    val labelStyle = ExType.caption.copy(color = c.inkSoft)
    val ticks = if (h24) listOf("00", "06", "12", "18") else listOf("12a", "6a", "12p", "6p")
    val tap by rememberUpdatedState(onTap)
    val sel by rememberUpdatedState(selected)

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        val labelPad = 20.dp
        val r2dp = maxWidth / 2 - labelPad
        val r1dp = r2dp * INNER
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    customActions = listOf(
                        CustomAccessibilityAction("Next hour") { tap(((sel ?: -1) + 1) % 24); true },
                        CustomAccessibilityAction("Previous hour") { tap(((sel ?: 0) + 23) % 24); true },
                    ) + if (selected != null) listOf(CustomAccessibilityAction("Clear hour") { tap(null); true }) else emptyList()
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r2 = minOf(size.width, size.height) / 2f - labelPad.toPx()
                        val dist = hypot(pos.x - cx, pos.y - cy)
                        when {
                            dist < r2 * INNER -> tap(null)
                            dist <= r2 + 8.dp.toPx() -> {
                                var a = atan2(pos.x - cx, -(pos.y - cy))
                                if (a < 0) a += (2 * PI).toFloat()
                                tap(((a / (2 * PI)) * 24).toInt().coerceIn(0, 23))
                            }
                        }
                    }
                }
        ) {
            val ctr = center
            val r2 = size.minDimension / 2f - labelPad.toPx()
            val r1 = r2 * INNER
            fun radius(v: Float) = r1 + (r2 - r1) * (0.12f + 0.88f * v / max)
            val gap = 1.2f
            fun start(i: Int) = i * 15f - 90f + gap
            val sweep = 15f - 2 * gap
            fun sector(i: Int, inner: Float, outer: Float) = Path().apply {
                arcTo(Rect(ctr, outer), start(i), sweep, forceMoveTo = true)
                arcTo(Rect(ctr, inner), start(i) + sweep, -sweep, forceMoveTo = false)
                close()
            }
            for (i in 0 until 24) {
                drawPath(sector(i, r1, r2), track)
                val v = hours[i]
                if (v > 0f) {
                    val t = v / curMax
                    val fill = if (t > 0.75f) hot else warm.copy(alpha = 0.4f + 0.6f * t)
                    drawPath(sector(i, r1, radius(v)), if (lines) fill.copy(alpha = 0.2f) else fill)
                }
            }
            if (before != null) {
                for (i in 0 until 24) {
                    val v = before[i]
                    if (v <= 0f) continue
                    val r = radius(v)
                    val strong = lines || i == selected
                    drawArc(
                        if (i == selected) ink else ghost,
                        startAngle = start(i) + 1f, sweepAngle = sweep - 2f, useCenter = false,
                        topLeft = Offset(ctr.x - r, ctr.y - r), size = Size(r * 2, r * 2),
                        style = Stroke((if (strong) 3.dp else 2.dp).toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            if (selected != null) drawPath(sector(selected, r1, r2), ink, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            ticks.forEachIndexed { k, l ->
                val a = k * (PI / 2).toFloat()
                val r = r2 + labelPad.toPx() * 0.55f
                val m = measurer.measure(l, labelStyle)
                drawText(m, topLeft = Offset(ctr.x + sin(a) * r - m.size.width / 2f, ctr.y - cos(a) * r - m.size.height / 2f))
            }
        }
        Column(
            Modifier.width(r1dp * 2 * 0.86f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (centre.over.isNotEmpty()) Overline(centre.over)
            Text(centre.big, style = ExType.title, color = c.ink, textAlign = TextAlign.Center, maxLines = 1)
            Text(centre.small, style = ExType.caption, color = c.inkSoft, textAlign = TextAlign.Center)
            if (centre.extra.isNotEmpty()) {
                Text(
                    centre.extra, style = ExType.caption, color = if (centre.extraFaint) c.inkFaint else c.inkSoft,
                    textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Inner radius of the clock ring as a share of the outer one. */
private const val INNER = 0.56f
