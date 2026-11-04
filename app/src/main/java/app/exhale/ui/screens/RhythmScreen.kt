package app.exhale.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
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
import app.exhale.domain.History
import app.exhale.domain.Stats
import app.exhale.domain.TopTrigger
import app.exhale.domain.TriggerSummary
import app.exhale.domain.Trigger
import app.exhale.domain.UrgeOutcome
import app.exhale.ui.ExhaleState
import app.exhale.ui.components.ActiveFilter
import app.exhale.ui.components.ButtonSize
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExLink
import app.exhale.ui.components.ExScreen
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
    // Reset whenever the shown day changes, whether from the week strip or from History.
    var selected by remember(day) { mutableStateOf(initialHour) }
    var lines by remember(day) { mutableStateOf(initialLines) }
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
    val peaks = remember(hours) { Stats.peaks(hours) }
    val unit = when (dayFilter) { DayFilter.ALL -> "a day"; DayFilter.WEEKDAYS -> "a weekday"; DayFilter.WEEKENDS -> "a weekend day" }
    val where = when (dayFilter) { DayFilter.ALL -> ""; DayFilter.WEEKDAYS -> " on weekdays"; DayFilter.WEEKENDS -> " at weekends" }
    val label = { h: Int -> hourLabel(h, h24) }

    // The average a day leads the comparison card (or the first-month card). Its label says what it
    // covers: the last 30 days, or since tracking began when that is newer, and the day filter.
    val sinceStart = dataStart != null && dataStart.isAfter(window.first)
    val average = Average(
        value = hours.sum(),
        unit = unit,
        over = "Average · " + when {
            sinceStart && dayFilter == DayFilter.ALL -> "since you started"
            sinceStart -> "${dayFilter.label.lowercase()} since you started"
            dayFilter == DayFilter.ALL -> "last 30 days"
            else -> "${dayFilter.label.lowercase()}, last 30 days"
        },
    )

    // The header ("Rhythm · Last 30 days", or "Rhythm" with a day picked) is in the shared app bar.
    ExScreen(underAppBar = true) {
        ScreenTitle("When you light up")

        Column(verticalArrangement = Arrangement.spacedBy(ExSpace.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last 7 days", style = ExType.caption, color = c.inkSoft)
                Text(
                    if (day == null) " · tap a day" else "",
                    style = ExType.caption, color = c.inkFaint, modifier = Modifier.weight(1f),
                )
                ExLink("All history", onHistory)
            }
            WeekStrip(state.week, state.today, selected = day, onSelect = { p ->
                onDay(if (day == p) null else p)
            })
        }

        AnimatedContent(day, transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) }, label = "filter") { d ->
            if (d != null) {
                val tally = remember(state.logs, state.settings, d) { History.tally(state.settings, History.countsByDay(state.logs, zone), d) }
                val name = dayName(d, state.today)
                ActiveFilter(
                    when {
                        tally.allowance == null -> "$name · ${tally.count}"
                        tally.over -> "$name · ${tally.count}, ${tally.count - tally.allowance} borrowed"
                        else -> "$name · ${tally.count} of ${tally.allowance}"
                    },
                    "Show last 30 days",
                    { onDay(null) },
                )
            } else {
                SegmentedControl(DayFilter.entries.map { it.label }, filter, { filter = it; selected = null })
            }
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

        // Keep the last comparison so the card still has something to show while it fades out.
        val lastBefore = remember { mutableStateOf<FloatArray?>(null) }
        if (beforeHours != null) lastBefore.value = beforeHours
        AnimatedVisibility(beforeHours != null, enter = fadeIn(tween(FADE_MS)) + expandVertically(tween(FADE_MS)), exit = fadeOut(tween(FADE_MS)) + shrinkVertically(tween(FADE_MS))) {
            val b = lastBefore.value ?: return@AnimatedVisibility
            ComparisonCard(
                average = average,
                hour = sel != null,
                now = if (sel != null) hours[sel] else hours.sum(),
                then = if (sel != null) b[sel] else b.sum(),
                unit = unit,
                lines = lines,
                onClick = { lines = !lines },
            )
        }
        if (beforeHours == null && day == null && covered > 0) {
            val tracked = dataStart?.let { ChronoUnit.DAYS.between(it, state.today) + 1 } ?: 0
            val left = dataStart?.let { ChronoUnit.DAYS.between(state.today, it.plusDays(36)) } ?: 0
            ExCard {
                AverageHead(average)
                Text(if (tracked <= 30) "Your first month." else "Almost enough to compare.", style = ExType.body, color = c.ink)
                Text(
                    if (dayFilter == DayFilter.ALL && left > 0) "In $left day${if (left == 1L) "" else "s"} you'll see how each hour compares with the 30 days before."
                    else "Once there's more history, you'll see how each hour compares with the 30 days before.",
                    style = ExType.caption, color = c.inkSoft,
                )
            }
        }

        AnimatedVisibility(day == null && covered >= MIN_DAYS && peaks.isNotEmpty(), enter = fadeIn(tween(FADE_MS)) + expandVertically(tween(FADE_MS)), exit = fadeOut(tween(FADE_MS)) + shrinkVertically(tween(FADE_MS))) {
            val h = peaks.firstOrNull() ?: return@AnimatedVisibility
            PlanCard(
                hour = label(h),
                top = Stats.topTrigger(windowLogs.filter { it.at.atZone(zone).hour == h }),
                idea = idea,
                onUrge = onUrge,
                onIdea = { idea++ },
            )
        }

        TriggersCard(triggers, pickedDay = day != null)
        WavesCard(
            survived = state.urges.count { window.contains(it.startedAt, zone) && it.outcome == UrgeOutcome.SURVIVED },
            span = if (day != null) "that day" else "$where in the last 30 days".trim(),
        )
    }
}

/** Trigger bars for what's shown, with how many logs were tagged at all. */
@Composable
private fun TriggersCard(triggers: TriggerSummary, pickedDay: Boolean) {
    val c = Ex.colors
    ExCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("Triggers", Modifier.weight(1f))
            if (triggers.tagged > 0) Text("${triggers.tagged} of ${triggers.total} tagged", style = ExType.caption, color = c.inkSoft)
        }
        if (triggers.tagged == 0) {
            Text(
                when {
                    triggers.total == 0 -> "Nothing logged here yet."
                    pickedDay -> "Nothing tagged that day."
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
}

/** Craving waves ridden out in [span] ("in the last 30 days"), or an invitation when there are none. */
@Composable
private fun WavesCard(survived: Int, span: String) {
    val c = Ex.colors
    if (survived > 0) {
        ExCard(color = c.tint(c.oxy)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ExSpace.md)) {
                Text("$survived", style = ExType.numberLarge, color = c.oxyInk)
                Text(
                    "craving wave${if (survived == 1) "" else "s"} ridden out $span.",
                    style = ExType.body, color = c.ink, modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        ExCard {
            Overline("Craving waves")
            Text("Ride one out with \u201cHelp me wait\u201d and it counts here.", style = ExType.body, color = c.ink)
        }
    }
}

/**
 * The [average] a day, then "1.4 fewer a day than the 30 days before (13.2)", or the same for one
 * [hour]. Tapping it lights up the thin lines on the clock that show the 30 days before.
 */
@Composable
private fun ComparisonCard(average: Average, hour: Boolean, now: Float, then: Float, unit: String, lines: Boolean, onClick: () -> Unit) {
    val c = Ex.colors
    val diff = then - now
    val same = abs(diff) < 0.05f
    val text = buildAnnotatedString {
        if (hour) append("This hour: ")
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = if (!same && diff > 0) c.oxyInk else c.ink)) {
            append(
                when {
                    same -> if (hour) "about the same" else "About the same"
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
        AverageHead(average)
        Column(Modifier.fillMaxWidth().animateContentSize(tween(FADE_MS))) {
            AnimatedContent(text, transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) }, label = "compare") {
                Text(it, style = ExType.body, color = c.ink)
            }
        }
        Row(Modifier.padding(top = ExSpace.xxs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
            Canvas(Modifier.size(28.dp)) {
                val u = size.width / 28f
                drawRect(bar, Offset(4 * u, 12 * u), Size(6 * u, 12 * u))
                drawRect(bar, Offset(16 * u, 16 * u), Size(6 * u, 8 * u))
                drawLine(line, Offset(2 * u, 8 * u), Offset(12 * u, 8 * u), 2 * u, StrokeCap.Round)
                drawLine(line, Offset(14 * u, 13 * u), Offset(24 * u, 13 * u), 2 * u, StrokeCap.Round)
            }
            Text(
                if (lines) "Lit up on the clock: each thin line is where that hour was. Tap to go back."
                else "Tap to see where each hour was on the clock.",
                style = ExType.caption, color = c.inkSoft, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The average a day for what is shown, and what it covers ("Average · last 30 days"). */
private data class Average(val value: Float, val unit: String, val over: String)

/** The average leading a card: its overline, then the number with its unit ("9.3 a day"). */
@Composable
private fun ColumnScope.AverageHead(a: Average) {
    val c = Ex.colors
    Overline(a.over)
    Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
        AnimatedContent(
            "%.1f".format(a.value),
            transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
            modifier = Modifier.alignByBaseline(),
            label = "average",
        ) { Text(it, style = ExType.numberLarge, color = c.ink) }
        Text(a.unit, style = ExType.body, color = c.inkSoft, modifier = Modifier.alignByBaseline())
    }
}

/** An if-then plan for the busiest [hour], with the trigger that comes with it when known. */
@Composable
private fun PlanCard(hour: String, top: TopTrigger?, idea: Int, onUrge: () -> Unit, onIdea: () -> Unit) {
    val c = Ex.colors
    val ideas = listOf(
        "then I ride one wave first.",
        "then I wait ten minutes, then decide.",
        "then I step outside without one first.",
        "then I have a glass of water first.",
    )
    ExCard {
        Overline("A plan for your busiest hour")
        Text(
            "${top?.let { ifAt(it.trigger, hour) } ?: "If it comes at $hour,"}\n${ideas[idea % ideas.size]}",
            style = ExType.headlineItalic, color = c.ink,
            modifier = Modifier.padding(vertical = ExSpace.xxs).semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
            ExButton("Help me wait", onUrge, style = ButtonStyle.Tonal, size = ButtonSize.Medium)
            ExButton("Another idea", onIdea, style = ButtonStyle.Ghost, size = ButtonSize.Medium)
        }
    }
}

/** The if-line of a plan for [t] at an hour. Null for [Trigger.OTHER], which says nothing useful. */
private fun ifAt(t: Trigger, hour: String): String? = when (t) {
    Trigger.COFFEE -> "If it's coffee at $hour,"
    Trigger.STRESS -> "If stress hits at $hour,"
    Trigger.AFTER_FOOD -> "If it's after a meal at $hour,"
    Trigger.DRINKS -> "If it's a drink at $hour,"
    Trigger.DRIVING -> "If I'm driving at $hour,"
    Trigger.BORED -> "If I'm bored at $hour,"
    Trigger.SOCIAL -> "If I'm with people at $hour,"
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
    // Bars and lines glide to new values when the filter, day or period changes.
    val shown = animatedValues(hours)
    val shownBefore = animatedValues(before ?: FloatArray(24))
    val hasBefore = before != null
    val lit by animateFloatAsState(if (lines) 1f else 0f, tween(FADE_MS), label = "lines")
    val curMax = (shown.maxOrNull() ?: 0f).coerceAtLeast(0.01f)
    val max = maxOf(curMax, if (hasBefore) shownBefore.max() else 0f)
    val track = c.surface
    val warm = c.ember
    val hot = c.flare
    val ink = c.ink
    val ghost = c.ink.copy(alpha = 0.6f + 0.4f * lit)
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
                val v = shown[i]
                if (v > 0.001f) {
                    val t = v / curMax
                    val fill = if (t > 0.75f) hot else warm.copy(alpha = 0.4f + 0.6f * t)
                    drawPath(sector(i, r1, radius(v)), fill.copy(alpha = fill.alpha * (1f - 0.8f * lit)))
                }
            }
            if (hasBefore) {
                for (i in 0 until 24) {
                    val v = shownBefore[i]
                    if (v <= 0.001f) continue
                    val r = radius(v)
                    val strong = lit > 0.5f || i == selected
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
        AnimatedContent(
            centre,
            transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
            contentAlignment = Alignment.Center,
            label = "centre",
        ) { ct ->
            Column(
                Modifier.width(r1dp * 2 * 0.86f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (ct.over.isNotEmpty()) Overline(ct.over)
                Text(ct.big, style = ExType.title, color = c.ink, textAlign = TextAlign.Center, maxLines = 1)
                Text(ct.small, style = ExType.caption, color = c.inkSoft, textAlign = TextAlign.Center)
                if (ct.extra.isNotEmpty()) {
                    Text(
                        ct.extra, style = ExType.caption, color = if (ct.extraFaint) c.inkFaint else c.inkSoft,
                        textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Inner radius of the clock ring as a share of the outer one. */
private const val INNER = 0.56f

/** Duration of Rhythm's fades and glides. */
private const val FADE_MS = 280

/** Duration of the clock bars and lines gliding to new values. */
private const val BAR_MS = 360

/**
 * [target], animated: when it changes, every value glides from where it is now to its new value.
 * The first value shows without animating.
 */
@Composable
private fun animatedValues(target: FloatArray): FloatArray {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (!target.contentEquals(to)) {
            val p = progress.value
            from = FloatArray(target.size) { from[it] + (to[it] - from[it]) * p }
            to = target
            progress.snapTo(0f)
            // Starts at full speed and only eases at the end, so bars never seem to hang before moving.
            progress.animateTo(1f, tween(BAR_MS, easing = LinearOutSlowInEasing))
        }
    }
    val p = progress.value
    return FloatArray(target.size) { from[it] + (to[it] - from[it]) * p }
}
