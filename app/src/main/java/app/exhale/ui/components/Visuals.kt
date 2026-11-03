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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Thick ring with a soft gradient sweep, like a check-in ring.
 * [progress] 0..1 fills clockwise from the top. [content] sits in the middle.
 */
@Composable
fun GradientRing(
    progress: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    stroke: Dp = 28.dp,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = Ex.colors.surface
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

/** Status of one day in [WeekStrip]. */
data class DayDot(val date: LocalDate, val count: Int?, val allowance: Int)

/**
 * S M T W T F S row of circles. Each past day shows its count, tinted oxygen when within
 * the allowance and ember when over. Without [onSelect], today gets an outline. With it, days up
 * to today are tappable and the [selected] one gets the outline; today keeps an ink letter.
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
            val over = d.count != null && d.count > d.allowance
            val accent = if (over) c.ember else c.oxy
            val past = !d.date.isAfter(today)
            val isSelected = d.date == selected
            val outlined = if (onSelect == null) d.date == today else isSelected
            val name = d.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Column(
                Modifier
                    .clip(ExRadius.md)
                    .then(
                        if (onSelect != null && past) Modifier
                            .clickable(role = Role.Button, onClickLabel = "Show $name") { onSelect(d.date) }
                            .semantics { this.selected = isSelected }
                        else Modifier
                    )
                    .padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = ExType.overline, color = if (d.date == today || d.date == selected) c.ink else c.inkFaint,
                )
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .clip(ExRadius.pill)
                        .background(if (d.count != null && d.count > 0) c.tint(accent) else c.surface)
                        .then(if (outlined) Modifier.border(1.5.dp, c.ink, ExRadius.pill) else Modifier)
                        .size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (d.count != null && past) {
                        Text("${d.count}", style = ExType.numberSmall, color = if (d.count > 0) c.inkOf(accent) else c.inkFaint)
                    }
                }
            }
        }
    }
}
