package app.exhale.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.exhale.domain.DayEntry
import app.exhale.domain.DayTimes
import app.exhale.domain.LogEdit
import app.exhale.domain.SmokeLog
import app.exhale.domain.Trigger
import app.exhale.ui.Fmt
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln

/** Width of the line the day list hangs on. */
private val RAIL = 20.dp

/** What one row of a [DayTimeline] is. */
private sealed interface TimelineRow {
    data class Previous(val log: SmokeLog) : TimelineRow
    data class Stretch(val length: Duration, val label: String?) : TimelineRow
    data class Smoke(val entry: DayEntry.Cigarette) : TimelineRow
    data class Wave(val entry: DayEntry.Wave) : TimelineRow
    data class Now(val since: Duration) : TimelineRow
}

/** The rows of [times], top to bottom: the last one before, then each entry after its gap, then now (today). */
private fun rowsOf(times: DayTimes): List<TimelineRow> {
    val rows = mutableListOf<TimelineRow>()
    var last: Instant? = times.previous?.at
    if (!times.isEmpty) times.previous?.let { rows += TimelineRow.Previous(it) }
    for (e in times.entries) {
        last?.let { rows += TimelineRow.Stretch(Duration.between(it, e.at), (e as? DayEntry.Cigarette)?.gapBefore?.let(Fmt::gap)) }
        rows += when (e) {
            is DayEntry.Cigarette -> TimelineRow.Smoke(e)
            is DayEntry.Wave -> TimelineRow.Wave(e)
        }
        last = e.at
    }
    val running = times.running
    if (running != null && last != null) {
        rows += TimelineRow.Stretch(Duration.between(last, running.end), null)
        rows += TimelineRow.Now(running.length)
    }
    return rows
}

/** The dot a row puts on the rail. */
private enum class Mark { Previous, Smoke, Over, Wave, Now }

/**
 * One day, cigarette by cigarette (Rhythm on a picked day). Each cigarette is an ember dot (flare
 * past the allowance) with its exact time, trigger and "3 of 8"; the oxygen line between dots is
 * the gap, longer for longer gaps and labelled "3h 27m". Waves ridden out sit on the line as oxygen
 * rings; today ends with "Now" and the running gap. The day before's last one opens the list, faint.
 * Tapping a cigarette calls [onEdit]. Shows nothing for an empty day: the caller says so.
 */
@Composable
fun DayTimeline(
    times: DayTimes,
    today: LocalDate,
    zone: ZoneId,
    h24: Boolean,
    onEdit: ((SmokeLog) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = Ex.colors
    val rows = remember(times) { rowsOf(times) }
    val timeWidth = if (h24) 44.dp else 68.dp
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, row ->
            val above = i > 0
            val below = i < rows.lastIndex
            when (row) {
                is TimelineRow.Previous -> MarkRow(
                    Mark.Previous, above, below, timeWidth,
                    time = Fmt.time(row.log.at, zone, h24),
                    label = previousLabel(row.log.at.atZone(zone).toLocalDate(), today),
                )
                is TimelineRow.Stretch -> StretchRow(row.length, row.label, timeWidth)
                is TimelineRow.Smoke -> {
                    val e = row.entry
                    val time = Fmt.time(e.at, zone, h24)
                    val label = e.log.trigger?.label ?: "No trigger"
                    val count = times.allowance?.let { "${e.number} of $it" } ?: "${e.number}"
                    MarkRow(
                        if (e.over) Mark.Over else Mark.Smoke, above, below, timeWidth,
                        time = time, timeColor = c.ink,
                        label = label, labelColor = if (e.log.trigger == null) c.inkFaint else c.ink,
                        end = count, endColor = if (e.over) c.flareInk else c.inkFaint,
                        onClick = onEdit?.let { { it(e.log) } },
                        description = "$time, $label, $count" + (e.gapBefore?.let { ", ${Fmt.gap(it)} after the one before" } ?: ""),
                    )
                }
                is TimelineRow.Wave -> {
                    val u = row.entry.urge
                    MarkRow(
                        Mark.Wave, above, below, timeWidth,
                        time = Fmt.time(u.startedAt, zone, h24), timeColor = c.oxyInk,
                        label = "Rode out a wave" + (u.trigger?.let { " · ${it.label}" } ?: ""), labelColor = c.oxyInk,
                        end = "${row.entry.length.toMinutes().coerceAtLeast(1)} min", endColor = c.inkFaint,
                    )
                }
                is TimelineRow.Now -> MarkRow(
                    Mark.Now, above, below, timeWidth,
                    time = "Now", timeColor = c.oxyInk,
                    label = "${Fmt.gap(row.since)} since the last one", labelColor = c.oxyInk,
                )
            }
        }
    }
}

/** "Yesterday's last one", "Saturday's last one" within the week, else "Last one before: Sun 12 Oct". */
private fun previousLabel(date: LocalDate, today: LocalDate): String {
    val name = Fmt.dayName(date, today, yesterday = true)
    return if (date.isBefore(today.minusDays(6))) "Last one before: $name" else "$name's last one"
}

/**
 * A row with a dot on the rail: time, label and an optional count at the end. Tappable with [onClick].
 * The day before's last one ([Mark.Previous]) is all faint and small, whatever colours are passed.
 */
@Composable
private fun MarkRow(
    mark: Mark,
    above: Boolean,
    below: Boolean,
    timeWidth: Dp,
    time: String,
    label: String,
    timeColor: Color = Color.Unspecified,
    labelColor: Color = Color.Unspecified,
    end: String? = null,
    endColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    description: String? = null,
) {
    val faintText = mark == Mark.Previous
    val faint = Ex.colors.inkFaint
    Row(
        Modifier
            .fillMaxWidth()
            .height(ExSize.touch)
            .clip(ExRadius.sm)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Change or delete", onClick = onClick) else Modifier)
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(mark, above, below)
        Text(
            time,
            style = if (faintText) ExType.caption else ExType.numberSmall,
            color = if (faintText) faint else timeColor,
            maxLines = 1, softWrap = false,
            // At least the width of an English time, so labels line up; a longer AM/PM word just pushes the label.
            modifier = Modifier.padding(start = ExSpace.sm, end = ExSpace.xs).widthIn(min = timeWidth),
        )
        Text(
            label, style = if (faintText) ExType.caption else ExType.bodySmall, color = if (faintText) faint else labelColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (end != null) Text(end, style = ExType.caption, color = endColor, modifier = Modifier.padding(start = ExSpace.xs, end = ExSpace.xxs))
    }
}

/** The line between two rows; its length grows with [length] (log scale), labelled "3h 27m" before a cigarette. */
@Composable
private fun StretchRow(length: Duration, label: String?, timeWidth: Dp) {
    val c = Ex.colors
    val minutes = length.toMinutes().coerceAtLeast(0)
    val h = (14 + 9 * ln(1.0 + minutes / 10.0)).coerceIn(18.0, 56.0).dp
    Row(Modifier.fillMaxWidth().height(h), verticalAlignment = Alignment.CenterVertically) {
        Rail(null, above = true, below = true)
        if (label != null) {
            Text(label, style = ExType.caption, color = c.oxyInk, modifier = Modifier.padding(start = ExSpace.sm).widthIn(min = timeWidth))
        }
    }
}

/** How a [Mark] is drawn: a dot of [radius], or with [ring] a ring on a card-coloured disc; [halo] behind it. */
private class MarkStyle(val color: Color, val radius: Dp, val ring: Dp? = null, val halo: Color? = null)

/** The rail cell of a row: the line above and below its centre, and its [mark] (none on a stretch). */
@Composable
private fun Rail(mark: Mark?, above: Boolean, below: Boolean) {
    val c = Ex.colors
    // Colours are read here, before the draw lambda, never from Ex.colors inside DrawScope.
    val line = c.oxy.copy(alpha = 0.55f)
    val card = c.surface
    val style = when (mark) {
        null -> null
        Mark.Smoke -> MarkStyle(c.ember, 5.5.dp)
        Mark.Over -> MarkStyle(c.flare, 5.5.dp)
        Mark.Wave -> MarkStyle(c.oxy, 4.5.dp, ring = 2.dp)
        Mark.Now -> MarkStyle(c.oxy, 5.dp, halo = c.tint(c.oxy))
        Mark.Previous -> MarkStyle(c.inkFaint, 3.75.dp, ring = 1.5.dp)
    }
    Box(
        Modifier.width(RAIL).fillMaxHeight().drawBehind {
            val at = Offset(size.width / 2, size.height / 2)
            val w = 3.dp.toPx()
            if (style == null) {
                drawLine(line, Offset(at.x, 0f), Offset(at.x, size.height), w, StrokeCap.Butt)
                return@drawBehind
            }
            if (above) drawLine(line, Offset(at.x, 0f), at, w, StrokeCap.Butt)
            if (below) drawLine(line, at, Offset(at.x, size.height), w, StrokeCap.Butt)
            style.halo?.let { drawCircle(it, 9.dp.toPx(), at) }
            val ring = style.ring
            if (ring == null) {
                drawCircle(style.color, style.radius.toPx(), at)
            } else {
                drawCircle(card, (style.radius + ring / 2).toPx(), at)
                drawCircle(style.color, style.radius.toPx(), at, style = Stroke(ring.toPx()))
            }
        },
    )
}

/**
 * Today in one line, the row under the app bar on Ember. A line from 6 am (or an earlier first one)
 * to midnight with an ember dot per cigarette (flare past the allowance), an oxygen stretch from the
 * last one to now and a ring at now; then "Last 11:40", or "Nothing yet". Tapping opens today on Rhythm.
 */
@Composable
fun DayLine(
    times: DayTimes,
    now: Instant,
    zone: ZoneId,
    h24: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ex.colors
    val track = c.line
    val ember = c.ember
    val flare = c.flare
    val oxy = c.oxy
    val bg = c.surface
    val start = times.lineStartMinute
    val span = (24 * 60 - start).toFloat()
    // Ember recomposes every second; today's cigarettes only change with a log.
    val cigarettes = remember(times.entries) { times.cigarettes }
    val dots = remember(cigarettes, zone) { cigarettes.map { DayTimes.minuteOf(it.at, zone) to it.over } }
    val labels = remember(cigarettes, zone, h24) { cigarettes.map { Fmt.time(it.at, zone, h24) } }
    val lastOnLine = dots.lastOrNull()?.first ?: start
    // Never before the last dot, even when the clocks go back and the minute of day drops.
    val nowMinute = DayTimes.minuteOf(now, zone).coerceAtLeast(lastOnLine)
    Row(
        modifier
            .fillMaxWidth()
            .height(ExSize.segment)
            .clip(ExRadius.pill)
            .background(bg)
            .clickable(role = Role.Button, onClickLabel = "Show today on Rhythm", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = when (labels.size) {
                    0 -> "Today: nothing logged"
                    1 -> "Today: 1, at ${labels[0]}"
                    else -> "Today: ${labels.size}, at ${labels.dropLast(1).joinToString(", ")} and ${labels.last()}"
                }
            }
            .padding(start = ExSpace.md, end = ExSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ExSpace.sm),
    ) {
        Canvas(Modifier.weight(1f).height(20.dp)) {
            val y = size.height / 2
            val r = 5.dp.toPx()
            fun x(m: Int) = r + (size.width - 2 * r) * ((m - start) / span).coerceIn(0f, 1f)
            drawLine(track, Offset(x(start), y), Offset(x(24 * 60), y), 2.dp.toPx(), StrokeCap.Round)
            if (times.running != null) drawLine(oxy, Offset(x(lastOnLine), y), Offset(x(nowMinute), y), 2.dp.toPx(), StrokeCap.Round)
            dots.forEach { (m, over) ->
                drawCircle(bg, 5.5.dp.toPx(), Offset(x(m), y))
                drawCircle(if (over) flare else ember, 4.dp.toPx(), Offset(x(m), y))
            }
            drawCircle(bg, r, Offset(x(nowMinute), y))
            drawCircle(oxy, 4.dp.toPx(), Offset(x(nowMinute), y), style = Stroke(2.dp.toPx()))
        }
        if (cigarettes.isEmpty()) {
            Text("Nothing yet", style = ExType.labelSmall, color = c.oxyInk)
        } else {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.inkSoft)) { append("Last ") }
                    append(labels.last())
                },
                style = ExType.numberSmall.copy(fontSize = ExType.labelSmall.fontSize), color = c.ink, maxLines = 1,
            )
        }
        Icon(ExIcons.Forward, null, Modifier.size(ExSize.iconSmall), tint = c.inkSoft)
    }
}

/** Height of one value on a [TimeWheel]; it shows three. */
private val WHEEL_SLOT = ExSize.row

/**
 * A time of day as two wheels, hour and minutes, like the iOS and Android spinners: the value in
 * the middle, its neighbours fading above (earlier) and below (later). Drag a wheel up for later,
 * down for earlier; tap the value below or above to step to it, and hold to keep going. Hours go
 * round the clock, minutes round the hour. [canSet] says whether a time can be chosen (a log can't
 * move into the future): one that can't is dimmed, and the wheel won't turn to it.
 */
@Composable
fun TimeWheel(minuteOfDay: Int, onChange: (Int) -> Unit, canSet: (Int) -> Boolean, h24: Boolean, modifier: Modifier = Modifier) {
    val c = Ex.colors
    val hour = minuteOfDay / 60
    val hourText = { m: Int -> (m / 60).let { h -> if (h24) "%02d".format(h) else "${if (h % 12 == 0) 12 else h % 12}" } }
    val half = LocalTime.of(hour, 0).format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    Row(
        modifier.fillMaxWidth().semantics { stateDescription = hourText(minuteOfDay) + ":%02d".format(minuteOfDay % 60) + if (h24) "" else " $half" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelColumn("hour", minuteOfDay, onChange, canSet, LogEdit::stepHour, hourText)
        Text(":", style = ExType.numberLarge, color = c.inkSoft, modifier = Modifier.padding(horizontal = ExSpace.xxs))
        WheelColumn("minute", minuteOfDay, onChange, canSet, LogEdit::stepMinute) { "%02d".format(it % 60) }
        if (!h24) Text(half, style = ExType.label, color = c.inkSoft, modifier = Modifier.padding(start = ExSpace.xs))
    }
}

/**
 * One wheel of a [TimeWheel]: values [step]ped from [minuteOfDay] by -2..2 by one [unit], drawn
 * [WHEEL_SLOT] apart and following the finger; past half a slot the wheel turns to the next value.
 */
@Composable
private fun WheelColumn(
    unit: String,
    minuteOfDay: Int,
    onChange: (Int) -> Unit,
    canSet: (Int) -> Boolean,
    step: (Int, Int) -> Int,
    label: (Int) -> String,
) {
    val c = Ex.colors
    val ink = c.ink
    val slot = with(LocalDensity.current) { WHEEL_SLOT.toPx() }
    // How far the values sit from rest, in px: negative while dragged up (towards later).
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(minuteOfDay)
    val change by rememberUpdatedState(onChange)
    val allowed by rememberUpdatedState(canSet)
    val later = step(minuteOfDay, 1)
    val earlier = step(minuteOfDay, -1)
    Box(
        Modifier
            .width(80.dp)
            .height(WHEEL_SLOT * 3)
            .clipToBounds()
            .pointerInput(step) {
                val settle = { scope.launch { offset.animateTo(0f, tween(160)) } }
                // The value the drag has turned to, so quick turns build on each other before the screen catches up.
                var m = current
                detectVerticalDragGestures(onDragStart = { m = current }, onDragEnd = { settle() }, onDragCancel = { settle() }) { pointer, dy ->
                    pointer.consume()
                    var o = offset.value + dy
                    val was = m
                    // Turn a value at a time while the drag has gone past half a slot; a value that
                    // can't be chosen holds the wheel short of it.
                    while (o <= -slot / 2 || o >= slot / 2) {
                        val dir = if (o < 0) 1 else -1
                        val next = step(m, dir)
                        if (!allowed(next)) { o = o.coerceIn(-slot * 0.3f, slot * 0.3f); break }
                        m = next
                        o += dir * slot
                    }
                    if (m != was) change(m)
                    scope.launch { offset.snapTo(o) }
                }
            },
    ) {
        for (k in -2..2) {
            val value = step(minuteOfDay, k)
            val dim = k != 0 && !canSet(value)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WHEEL_SLOT)
                    .offset { IntOffset(0, (slot * (k + 1) + offset.value).roundToInt()) }
                    .graphicsLayer {
                        val dist = (abs(slot * k + offset.value) / slot).coerceAtMost(2f)
                        val s = 1f - 0.18f * dist
                        scaleX = s
                        scaleY = s
                        alpha = (1f - 0.6f * dist).coerceAtLeast(0f) * if (dim) 0.35f else 1f
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(value), style = ExType.numberLarge, color = ink, textAlign = TextAlign.Center)
            }
        }
        RepeatTarget(canSet(earlier), "One $unit earlier", { onChange(earlier) }, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(WHEEL_SLOT))
        RepeatTarget(canSet(later), "One $unit later", { onChange(later) }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(WHEEL_SLOT))
    }
}

/**
 * Change one logged cigarette: its time (same day, never later than [now]) and its trigger (tap the
 * picked one again to clear it; "Your own" names a new one with [onAddTrigger]). Save hands back the
 * time and trigger, changed or not (the ViewModel skips a save that changes nothing). Delete hands
 * over to the caller, which asks first. Shown in [LogEditDialog].
 */
@Composable
fun LogEditor(
    log: SmokeLog,
    dayName: String,
    zone: ZoneId,
    now: Instant,
    h24: Boolean,
    triggers: List<Trigger>,
    onSave: (at: Instant, trigger: Trigger?) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onAddTrigger: ((name: String, then: (Trigger) -> Unit) -> Unit)? = null,
) {
    val c = Ex.colors
    var minute by remember(log.id) { mutableIntStateOf(DayTimes.minuteOf(log.at, zone)) }
    var trigger by remember(log.id) { mutableStateOf(log.trigger) }
    // A trigger hidden since keeps its chip here, so the log doesn't lose it by being opened.
    val chips = remember(triggers, log.trigger) { log.trigger?.takeIf { it !in triggers }?.let { triggers + it } ?: triggers }
    val at = LogEdit.moved(log.at, minute, zone, now)
    // On surface-high like the Logged card, so the trigger chips stand out from it.
    ExDialogCard(
        "$dayName, ${Fmt.time(log.at, zone, h24)}",
        listOf(
            DialogAction("Save", ButtonStyle.Primary) { onSave(at ?: log.at, trigger) },
            DialogAction("Delete this one", ButtonStyle.Ghost, onClick = onDelete),
        ),
        modifier, color = c.surfaceHigh,
    ) {
        Overline("Time", Modifier.padding(top = ExSpace.sm))
        TimeWheel(minute, { minute = it }, canSet = { LogEdit.moved(log.at, it, zone, now) != null }, h24 = h24)
        Overline("Trigger", Modifier.padding(top = ExSpace.sm))
        TriggerChips(
            chips, selected = trigger,
            onPick = { t -> trigger = if (trigger == t) null else t },
            onAdd = onAddTrigger?.let { add -> { name -> add(name) { trigger = it } } },
        )
    }
}

/** [LogEditor] as a modal. Tapping outside or Back closes it without saving. */
@Composable
fun LogEditDialog(
    log: SmokeLog,
    dayName: String,
    zone: ZoneId,
    now: Instant,
    h24: Boolean,
    triggers: List<Trigger>,
    onSave: (at: Instant, trigger: Trigger?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onAddTrigger: ((name: String, then: (Trigger) -> Unit) -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        // Scrolls on a short screen or with the keyboard up for "Your own".
        LogEditor(log, dayName, zone, now, h24, triggers, onSave, onDelete, Modifier.verticalScroll(rememberScrollState()), onAddTrigger)
    }
}
