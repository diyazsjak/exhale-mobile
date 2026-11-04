package app.exhale.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Thick ring with a soft gradient sweep, like a check-in ring.
 * [progress] 0..1 fills clockwise from the top. [content] sits in the middle. [track] defaults to
 * `surface`; on a tinted card pass a tint of the accent instead.
 */
@Composable
fun GradientRing(
    progress: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    stroke: Dp = 28.dp,
    contentDescription: String? = null,
    track: Color? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = track ?: Ex.colors.surface
    Box(
        modifier.fillMaxWidth().aspectRatio(1f).then(
            if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val r = size.minDimension / 2f - s / 2f
            val tl = Offset(center.x - r, center.y - r)
            drawCircle(track, r, center, style = Stroke(s))
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) {
                // Start the arc one cap-radius after the gradient seam so the round start cap
                // takes the first colour instead of wrapping to the last one.
                val capDeg = Math.toDegrees((s / 2f / r).toDouble()).toFloat()
                val end = ((capDeg + 360f * p) / 360f).coerceAtMost(1f)
                rotate(-90f - capDeg, center) {
                    val stops = colors.mapIndexed { i, c -> (i / (colors.size - 1f)) * end to c }.toTypedArray()
                    drawArc(
                        Brush.sweepGradient(*stops, center = center),
                        startAngle = capDeg, sweepAngle = 360f * p, useCenter = false,
                        topLeft = tl, size = Size(r * 2, r * 2), style = Stroke(s, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

/**
 * Status of one day in [WeekStrip] and [MonthCalendar]. [count] is null for a day that is not
 * tracked (before tracking began, or still to come); [allowance] is null before the plan started,
 * which shows the count neutral instead of within or over.
 */
data class DayDot(val date: LocalDate, val count: Int?, val allowance: Int?) {
    val over: Boolean get() = count != null && allowance != null && count > allowance
}

/** A day's 40dp count circle, shared by the week strip and the calendar. */
@Composable
private fun DayCircle(d: DayDot, today: LocalDate, outlined: Boolean) {
    val c = Ex.colors
    val accent = if (d.over) c.ember else c.oxy
    val future = d.date.isAfter(today)
    val fill = when {
        d.count == null -> Color.Transparent
        d.count == 0 -> c.surface
        d.allowance == null -> c.surfaceHigh
        else -> c.tint(accent)
    }
    Box(
        Modifier
            .clip(ExRadius.pill)
            .background(fill)
            .then(
                when {
                    outlined -> Modifier.border(1.5.dp, c.ink, ExRadius.pill)
                    d.count == null && !future -> Modifier.border(1.dp, c.line, ExRadius.pill)
                    else -> Modifier
                }
            )
            .size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (d.count != null) {
            val ink = when {
                d.count == 0 -> c.inkFaint
                d.allowance == null -> c.ink
                else -> c.inkOf(accent)
            }
            Text("${d.count}", style = ExType.numberSmall, color = ink)
        }
    }
}

/** "Sunday, 1 November: 10 of 8, 2 borrowed" for screen readers. */
private fun dayDescription(d: DayDot, today: LocalDate): String {
    val name = if (d.date == today) "Today" else d.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    return when {
        d.count == null -> if (d.date.isAfter(today)) "$name, still to come" else "$name, before you started"
        d.allowance == null -> "$name, ${d.count}, before your plan"
        d.over -> "$name, ${d.count} of ${d.allowance}, ${d.count - d.allowance} borrowed"
        else -> "$name, ${d.count} of ${d.allowance}"
    }
}

/**
 * Row of seven day circles, each with its count, tinted oxygen within the allowance and ember
 * when over. Without [onSelect], today gets an outline. With it, tracked days are tappable and the
 * [selected] one gets the outline; today keeps an ink letter. Days with no count are not tappable.
 */
@Composable
fun WeekStrip(
    days: List<DayDot>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    selected: LocalDate? = null,
    onSelect: ((LocalDate) -> Unit)? = null,
) {
    val c = Ex.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            val isSelected = d.date == selected
            val outlined = if (onSelect == null) d.date == today else isSelected
            val tappable = onSelect != null && d.count != null && !d.date.isAfter(today)
            Column(
                Modifier
                    .clip(ExRadius.md)
                    .then(
                        if (tappable) Modifier.clickable(role = Role.Button, onClickLabel = "Show this day") { onSelect?.invoke(d.date) }
                        else Modifier
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = dayDescription(d, today)
                        if (onSelect != null) this.selected = isSelected
                    }
                    .padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = ExType.overline, color = if (d.date == today || isSelected) c.ink else c.inkFaint,
                )
                Box(Modifier.padding(top = 6.dp)) { DayCircle(d, today, outlined) }
            }
        }
    }
}

/**
 * One month as a calendar: weekday letters (first day per locale), then a date and a [DayCircle]
 * per day. [days] has one entry per day of [month]. Tracked days are tappable with [onSelect].
 * Future days show only their date, and weeks entirely in the future are not drawn.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    days: List<DayDot>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onSelect: ((LocalDate) -> Unit)? = null,
) {
    val c = Ex.colors
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val cells: List<DayDot?> = List(lead) { null } + days
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            (0L until 7L).forEach { i ->
                Text(
                    firstDay.plus(i).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = ExType.overline, color = c.inkFaint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        // Weeks that are still entirely to come are left out, so the current month stops at this week.
        cells.chunked(7).filter { w -> w.any { it != null && !it.date.isAfter(today) } }.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (d != null) {
                            val isToday = d.date == today
                            val tappable = onSelect != null && d.count != null
                            Column(
                                Modifier
                                    .clip(ExRadius.sm)
                                    .then(if (tappable) Modifier.clickable(role = Role.Button, onClickLabel = "Show this day") { onSelect?.invoke(d.date) } else Modifier)
                                    .semantics(mergeDescendants = true) { contentDescription = dayDescription(d, today) }
                                    .padding(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text("${d.date.dayOfMonth}", style = ExType.overline, color = if (isToday) c.ink else c.inkFaint)
                                DayCircle(d, today, outlined = isToday)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Colour key entry: a small dot in [color] and its meaning. */
@Composable
fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(ExRadius.pill).background(color))
        Text(label, style = ExType.caption, color = Ex.colors.inkSoft)
    }
}
