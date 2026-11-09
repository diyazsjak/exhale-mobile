package app.exhale.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.exhale.domain.DayAir
import app.exhale.domain.Milestone
import app.exhale.domain.Milestones
import app.exhale.domain.RecoverySummary
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.components.ExBadge
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.GradientRing
import app.exhale.ui.components.Overline
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExColors
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val x: Float, var y: Float, val v: Float, val r: Float, val k: Float)

/** Cards over the particle field let a little of it through. */
private const val CARD_ALPHA = 0.88f

private val ExColors.overParticles: Color get() = surface.copy(alpha = CARD_ALPHA)

/**
 * Air: what the body gets back after each cigarette, built so nothing resets. A sky of milestones
 * (lit once any gap has reached them; a dot with a trail for the current gap), the next milestone,
 * today's gaps, and the full timeline. Its overline is in the shared app bar, which stays with the hero
 * ([onHeld] reports how far, in px). While scrolling, the hero stays put
 * until the content after the sky reaches it, then scrolls away with it; the sky rises a little,
 * dims and drops its labels, and the rest of the screen scrolls over it. [initialScroll] (px) is
 * for screenshots.
 */
@Composable
fun AirScreen(
    state: ExhaleState,
    initialScroll: Int = 0,
    scroll: ScrollState = rememberScrollState(initialScroll),
    onHeld: (Float) -> Unit = {},
) {
    val c = Ex.colors
    val clear by animateFloatAsState(state.clearFraction, tween(1500), label = "clear")
    val h24 = DateFormat.is24HourFormat(LocalContext.current)
    val zone = ZoneId.systemDefault()
    val r = state.recovery
    val since = state.sinceLast
    val last = state.lastLog?.at
    val words = TimeWords(state.now, zone, h24)
    // How far the screen scrolls before the content after the sky reaches the header: the sky's
    // height plus the gap after it. Measured once from the layout, not on every frame.
    var skyHeight by remember { mutableIntStateOf(0) }
    // Tell the app bar how far the hero is held, so the bar stays and leaves with it.
    val density = LocalDensity.current
    LaunchedEffect(skyHeight) { onHeld(with(density) { pin(skyHeight) }) }

    ExScreen(
        background = { ParticleField(clear, Modifier.fillMaxSize()) },
        scrollState = scroll,
        underAppBar = true,
    ) {
        // The hero stays in place (it scrolls with the column, so it is moved back down by the
        // scroll) until the next card meets it, then goes up with it, under the app bar. Drawn over the rising sky.
        Column(
            Modifier
                .zIndex(1f)
                .graphicsLayer { translationY = min(scroll.value.toFloat(), pin(skyHeight)) },
            verticalArrangement = Arrangement.spacedBy(ExSpace.md),
        ) {
            Hero(since, r?.next)
        }
        RecoverySky(since, r?.lastReached.orEmpty(), r?.next, words, scroll, skyHeight, Modifier.onSizeChanged { skyHeight = it.height })
        if (since != null && last != null && r != null) {
            NextCard(since, last, r.next, words)
            if (r.today.gaps.isNotEmpty()) TodaysAirCard(r.today, state.now, words)
        }
        TimelineCard(since, last, r, words)
        val fewer = r?.fewer
        if (r != null && fewer != null && fewer > 0) {
            ExCard(color = c.tint(c.oxy).compositeOver(c.surface).copy(alpha = CARD_ALPHA)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ExSpace.md)) {
                    Text("$fewer", style = ExType.numberLarge, color = c.oxyInk)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "fewer than your old ${state.settings.baselinePerDay} a day ${if (r.fewerSinceStart) "since you started" else "in the last 7 days"}.",
                            style = ExType.body, color = c.ink,
                        )
                        Text("Cutting down clears the air too.", style = ExType.caption, color = c.inkSoft)
                    }
                }
            }
        }
    }
}

/** The time since the last cigarette and what the body is doing now; an invitation before any log. */
@Composable
private fun Hero(since: Duration?, next: Milestone?) {
    val c = Ex.colors
    Column(verticalArrangement = Arrangement.spacedBy(ExSpace.xxs)) {
        if (since == null) {
            Text("Nothing logged\nyet.", style = ExType.title, color = c.ink)
            Text(
                "After each cigarette, your body starts to recover within minutes. Log on the Ember tab and this map follows every gap.",
                style = ExType.body, color = c.inkSoft, modifier = Modifier.padding(top = ExSpace.xs),
            )
        } else {
            Text(Fmt.gap(since), style = ExType.display, color = c.oxyInk)
            Text("since your last one", style = ExType.titleItalic.copy(fontSize = 22.sp), color = c.inkSoft)
            Text(
                "Right now: " + (next?.underway ?: Milestones.ALL_REACHED).replaceFirstChar { it.lowercase() },
                style = ExType.body, color = c.ink, modifier = Modifier.padding(top = ExSpace.xs),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sky

/** The sky's drawing box; every point below is in these units and scaled to the width. */
private const val SKY_W = 350f
private const val SKY_H = 360f

/** The last cigarette, then one point per milestone (the last three are the long-road cluster). */
private val SKY_POINTS = listOf(
    Offset(14f, 344f), Offset(56f, 306f), Offset(130f, 250f), Offset(280f, 196f), Offset(200f, 120f),
    Offset(120f, 64f), Offset(276f, 44f), Offset(310f, 72f), Offset(332f, 28f),
)

/** Where each label sits: its top, and its left edge, or its right edge when [end]. */
private data class SkyLabel(val x: Float, val y: Float, val end: Boolean)

private val SKY_LABELS = listOf(
    SkyLabel(70f, 314f, false), SkyLabel(142f, 260f, false), SkyLabel(350f, 210f, true),
    SkyLabel(190f, 132f, true), SkyLabel(106f, 46f, true), SkyLabel(350f, 90f, true),
)

/** Milestones from this index on are drawn as one small cluster with one label, "The long road". */
private const val LONG_ROAD = 5

/*
 * How the sky collapses as the screen scrolls. `pin` is the scroll (px) at which the next card
 * reaches the held header: the sky's height plus the gap after it. While the header is held, the
 * sky rises only a little, so its top stays clear of the header; once the header has gone it rises
 * on to its place as a backdrop. It stays at full strength for the first half of `pin`, then dims.
 * Each label stays until the next card is about to slide over it.
 */
private const val SKY_LAG_HELD = 0.1f
private val SKY_LIFT_HELD = 24.dp
private const val SKY_LAG = 0.35f
private val SKY_LIFT = 120.dp
private const val SKY_DIM = 0.3f
private val SKY_DIM_AFTER = 120.dp
/** A label fades over this last stretch before the card reaches it. */
private val LABEL_CLEAR = 48.dp
/** About the height of a two-line sky label. */
private val LABEL_H = 36.dp

/** How far the sky has risen at scroll [s] (px). */
private fun Density.skyLift(s: Float, pin: Float): Float {
    val held = min(s * SKY_LAG_HELD, SKY_LIFT_HELD.toPx())
    val after = if (s > pin) min((s - pin) * SKY_LAG, (SKY_LIFT - SKY_LIFT_HELD).toPx()) else 0f
    return held + after
}

/** The sky's strength at scroll [s]: 1 until half of [pin], then down to [SKY_DIM]. */
private fun Density.skyStrength(s: Float, pin: Float): Float {
    val d = ((s - pin / 2f) / (pin / 2f + SKY_DIM_AFTER.toPx())).coerceIn(0f, 1f)
    return 1f - (1f - SKY_DIM) * d
}

/**
 * 1 → 0 as the card after the sky comes up to something in the sky whose bottom is [bottom] px
 * below the sky's top. [skyHeight] 0 means not measured yet: fully shown.
 */
private fun Density.clearOfCard(s: Float, skyHeight: Int, bottom: Float): Float {
    if (skyHeight == 0) return 1f
    val p = pin(skyHeight)
    val advance = s - skyLift(s, p)
    val touch = p - bottom
    val over = min(LABEL_CLEAR.toPx(), touch).coerceAtLeast(1f)
    return ((touch - advance) / over).coerceIn(0f, 1f)
}

/** The scroll (px) at which the card after the sky reaches the held header; never while unmeasured. */
private fun Density.pin(skyHeight: Int): Float = if (skyHeight == 0) Float.MAX_VALUE else skyHeight + ExSpace.md.toPx()

/**
 * The recovery sky. A dashed path runs from the last cigarette (an ember dot) through every
 * milestone. A milestone is lit once any gap has reached it and stays lit; the next one is a ring.
 * The current gap is a ringed dot with an oxygen trail behind it, placed by [Milestones.position].
 * Every point is labelled, so nothing needs a tap. Driven by [scroll] and the measured
 * [skyHeight]: see [skyLift], [skyStrength] and [clearOfCard].
 */
@Composable
private fun RecoverySky(
    since: Duration?,
    lastReached: List<Instant?>,
    next: Milestone?,
    words: TimeWords,
    scroll: ScrollState,
    skyHeight: Int,
    modifier: Modifier = Modifier,
) {
    val c = Ex.colors
    val lit = List(Milestones.all.size) { lastReached.getOrNull(it) != null }
    val nextIndex = next?.let { Milestones.all.indexOf(it) } ?: -1
    val target = if (since == null) -1f else Milestones.position(Duration.ofMinutes(since.toMinutes()))
    val pos by animateFloatAsState(target, tween(700), label = "gap")
    val description = buildString {
        append("Recovery map. ")
        val litLabels = Milestones.all.filterIndexed { i, _ -> lit[i] }.map { it.shortLabel }
        if (litLabels.isEmpty()) append("No milestone reached yet. ") else append("Reached at least once: ${litLabels.joinToString()}. ")
        if (since != null) {
            append("This gap: ${Fmt.gap(since)}")
            if (next != null) append(", on its way to ${next.label}.") else append(".")
        }
    }

    // Captured outside the Canvas so the lambda reads plain values (see CLAUDE.md).
    val path = c.inkFaint
    val oxy = c.oxy
    val halo = c.tint(c.oxy)
    val ember = c.ember
    val bg = c.bg

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = scroll.value.toFloat()
                // It scrolls with the column, so add back all but the part it should rise.
                translationY = s - skyLift(s, pin(skyHeight))
            }
            .clearAndSetSemantics { contentDescription = description },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(SKY_W / SKY_H)) {
            val unit = maxWidth / SKY_W
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = skyStrength(scroll.value.toFloat(), pin(skyHeight)) }) {
                val k = size.width / SKY_W
                val pts = SKY_POINTS.map { it * k }
                val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))
                drawPath(polyline(pts), path.copy(alpha = 0.7f), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash))
                if (pos >= 0f) {
                    val trail = trailTo(pts, pos)
                    drawPath(trail, oxy.copy(alpha = 0.18f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(trail, oxy, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                drawCircle(ember, 4.dp.toPx(), pts[0])
                Milestones.all.indices.forEach { i ->
                    val p = pts[i + 1]
                    val small = i >= LONG_ROAD
                    when {
                        lit[i] -> {
                            drawCircle(halo, (if (small) 9.dp else 13.dp).toPx(), p)
                            drawCircle(oxy, (if (small) 4.dp else 6.dp).toPx(), p)
                        }
                        i == nextIndex -> {
                            drawCircle(bg, 6.dp.toPx(), p)
                            drawCircle(oxy, 6.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                        }
                        else -> drawCircle(path, (if (small) 3.dp else 4.5.dp).toPx(), p)
                    }
                }
                if (pos >= 0f) gapDot(pointAt(pts, pos), oxy, halo, bg)
            }
            // Each label stays until the next card is about to slide over it.
            Box(Modifier.fillMaxSize()) {
                SKY_LABELS.forEachIndexed { i, l ->
                    val group = if (i == LONG_ROAD) LONG_ROAD until Milestones.all.size else i..i
                    val m = Milestones.all[group.first]
                    val isLit = group.all { lit[it] }
                    val first = when {
                        i == LONG_ROAD -> "5–15 years"
                        isLit -> "${m.shortLabel} · reached ${words.reached(lastReached[i]!!)}"
                        else -> m.shortLabel
                    }
                    val second = if (i == LONG_ROAD) "The long road" else m.short
                    val w = 200.dp
                    Column(
                        Modifier
                            .width(w)
                            .offset(x = unit * l.x - (if (l.end) w else 0.dp), y = unit * l.y)
                            .graphicsLayer { alpha = clearOfCard(scroll.value.toFloat(), skyHeight, (unit * l.y + LABEL_H).toPx()) },
                        horizontalAlignment = if (l.end) Alignment.End else Alignment.Start,
                    ) {
                        Text(first, style = ExType.labelSmall, color = if (isLit) c.oxyInk else c.inkSoft, textAlign = if (l.end) TextAlign.End else TextAlign.Start, maxLines = 1)
                        Text(second, style = ExType.caption, color = if (isLit) c.inkSoft else c.inkFaint, textAlign = if (l.end) TextAlign.End else TextAlign.Start, maxLines = 1)
                    }
                }
            }
        }
        Row(
            Modifier
                .padding(top = ExSpace.xs)
                .graphicsLayer { alpha = clearOfCard(scroll.value.toFloat(), skyHeight, skyHeight.toFloat()) },
            horizontalArrangement = Arrangement.spacedBy(ExSpace.md),
        ) {
            if (lit.any { it }) KeyItem("reached at least once") { drawCircle(halo, size.minDimension / 2f); drawCircle(oxy, size.minDimension / 4.5f) }
            if (since != null) KeyItem("this gap") { gapDot(center, oxy, halo, bg, scale = size.minDimension / 28.dp.toPx()) }
        }
    }
}

/** One entry of the sky's key: a small drawn glyph and what it means. */
@Composable
private fun KeyItem(label: String, glyph: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(14.dp), onDraw = glyph)
        Text(label, style = ExType.caption, color = Ex.colors.inkSoft)
    }
}

/** The current gap: a dot inside a ring, on a soft halo, so it never reads as a reached milestone. */
private fun DrawScope.gapDot(p: Offset, oxy: Color, halo: Color, bg: Color, scale: Float = 1f) {
    drawCircle(halo, 14.dp.toPx() * scale, p)
    drawCircle(bg, 9.dp.toPx() * scale, p)
    drawCircle(oxy, 9.dp.toPx() * scale, p, style = Stroke(2.dp.toPx() * scale))
    drawCircle(oxy, 4.dp.toPx() * scale, p)
}

private fun polyline(pts: List<Offset>) = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    pts.drop(1).forEach { lineTo(it.x, it.y) }
}

/** The point [pos] of the way along the path: whole numbers are the points themselves. */
private fun pointAt(pts: List<Offset>, pos: Float): Offset {
    val i = floor(pos).toInt().coerceIn(0, pts.size - 1)
    if (i >= pts.size - 1) return pts.last()
    return lerp(pts[i], pts[i + 1], pos - i)
}

private fun trailTo(pts: List<Offset>, pos: Float) = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    val i = floor(pos).toInt().coerceIn(0, pts.size - 1)
    for (j in 1..i) lineTo(pts[j].x, pts[j].y)
    val end = pointAt(pts, pos)
    lineTo(end.x, end.y)
}

// ---------------------------------------------------------------------------------------------
// Next milestone

/** The milestone this gap is heading for: a ring of the way there, what it is and when. */
@Composable
private fun NextCard(since: Duration, last: Instant, next: Milestone?, words: TimeWords) {
    val c = Ex.colors
    ExCard(color = c.overParticles) {
        if (next == null) {
            Text(Milestones.ALL_REACHED, style = ExType.body, color = c.ink)
            return@ExCard
        }
        val left = next.after.minus(since)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ExSpace.md)) {
            GradientRing(
                progress = since.toMillis().toFloat() / next.after.toMillis(),
                colors = listOf(c.sky, c.oxy),
                modifier = Modifier.size(92.dp),
                stroke = 9.dp,
                track = c.surfaceHigh,
                contentDescription = "${Fmt.roughly(left)} to go",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.roughly(left), style = ExType.numberSmall, color = c.ink, maxLines = 1)
                    Text("to go", style = ExType.caption, color = c.inkSoft)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ExSpace.xxs)) {
                Overline("Next · ${next.label}", color = c.oxyInk)
                Text(next.text, style = ExType.headline, color = c.ink)
                Text(words.at(last.plus(next.after)), style = ExType.caption, color = c.inkSoft)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Today's air

/** Height of a gap on the chart, 0..1: a log scale where 12 hours is the top line. */
private fun gapHeight(d: Duration): Float {
    val min = d.toMillis() / 60_000f
    return (ln(1f + min / 2f) / ln(1f + 360f)).coerceIn(0f, 1.06f)
}

/**
 * Today's gaps between cigarettes as oxygen blocks over a 24-hour line: the taller, the longer
 * (log scale, with dashed lines at 20 minutes and 12 hours). Ember ticks are today's cigarettes.
 * The running gap is solid, with a dashed outline of where it reaches 12 hours.
 */
@Composable
private fun TodaysAirCard(day: DayAir, now: Instant, words: TimeWords) {
    val c = Ex.colors
    val twenty = Milestones.all[0].after
    val twelve = Milestones.all[1].after
    val running = day.gaps.lastOrNull { it.ongoing }
    val n = day.atLeast(twenty)
    val longest = day.longest
    val summary = when {
        day.logs.isEmpty() && running != null -> "Nothing logged today. This gap has run ${Fmt.gap(running.length)}."
        n == 0 && running != null -> "Heart rate settles 20 minutes into a gap. This one gets there at ${words.clock(running.start.plus(twenty))}."
        else -> "$n gap${if (n == 1) "" else "s"} past 20 minutes today." + (longest?.let { " Longest: ${Fmt.gap(it.length)}." } ?: "")
    }
    val description = buildString {
        append("Today's gaps between cigarettes. ")
        append(summary)
        if (running != null && running.length < twelve) append(" This one reaches 12 hours at ${words.clock(running.start.plus(twelve))}.")
    }

    val measurer = rememberTextMeasurer()
    val axis = ExType.caption.copy(color = c.inkFaint)
    val oxy = c.oxy
    val halo = c.tint(c.oxy)
    val ember = c.ember
    val faint = c.inkFaint
    val ticks = if (words.h24) listOf("00", "06", "12", "18", "24") else listOf("12a", "6a", "12p", "6p", "12a")

    ExCard(color = c.overParticles) {
        Overline("Today’s air")
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(top = ExSpace.xs)
                .semantics { contentDescription = description }
        ) {
            val gutter = 44.dp.toPx()
            val top = 8.dp.toPx()
            val base = size.height - 22.dp.toPx()
            val plotW = size.width - gutter
            val span = Duration.between(day.dayStart, day.dayEnd).toMillis().toFloat()
            fun x(t: Instant) = gutter + plotW * (Duration.between(day.dayStart, t).toMillis() / span).coerceIn(0f, 1f)
            fun y(f: Float) = base - (base - top) * f
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))

            listOf(twenty to "20 min", twelve to "12 h").forEach { (d, label) ->
                val ly = y(gapHeight(d))
                drawLine(faint.copy(alpha = 0.5f), Offset(gutter, ly), Offset(size.width, ly), 1.dp.toPx(), pathEffect = dash)
                val m = measurer.measure(label, axis)
                drawText(m, topLeft = Offset(gutter - 8.dp.toPx() - m.size.width, ly - m.size.height / 2f))
            }
            drawLine(faint.copy(alpha = 0.35f), Offset(gutter, base), Offset(size.width, base), 1.dp.toPx())

            if (running != null && running.length < twelve) {
                val gx0 = x(maxOf(running.start, day.dayStart))
                val reach = running.start.plus(twelve)
                val gx1 = x(minOf(reach, day.dayEnd))
                val gy = y(gapHeight(twelve))
                val outline = Path().apply {
                    moveTo(gx0, base); lineTo(gx0, gy); lineTo(gx1, gy)
                    if (reach <= day.dayEnd) lineTo(gx1, base)
                }
                drawPath(outline, oxy.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
            }

            val gapPx = 1.5.dp.toPx()
            day.gaps.forEach { g ->
                val from = maxOf(g.start, day.dayStart)
                var x0 = x(from) + if (from == g.start) gapPx else 0f
                var x1 = x(g.end) - if (g.ongoing) 0f else gapPx
                if (x1 - x0 < gapPx) { val mid = (x0 + x1) / 2f; x0 = mid - gapPx / 2f; x1 = mid + gapPx / 2f }
                val yTop = y(gapHeight(g.length))
                val r = 4.dp.toPx()
                val block = Path().apply {
                    addRoundRect(RoundRect(Rect(x0, yTop, x1, base), topLeft = CornerRadius(r), topRight = CornerRadius(r)))
                }
                drawPath(block, if (g.ongoing) oxy else oxy.copy(alpha = 0.45f))
            }
            day.logs.forEach { t ->
                val tx = x(t)
                drawRoundRect(ember, Offset(tx - 1.5.dp.toPx(), base - 12.dp.toPx()), Size(3.dp.toPx(), 12.dp.toPx()), CornerRadius(1.5.dp.toPx()))
            }
            if (running != null) {
                val p = Offset(x(now), y(gapHeight(running.length)))
                drawCircle(halo, 8.dp.toPx(), p)
                drawCircle(oxy, 4.dp.toPx(), p)
            }
            ticks.forEachIndexed { i, t ->
                val m = measurer.measure(t, axis)
                val tx = gutter + plotW * i / 4f
                val left = (tx - m.size.width / 2f).coerceIn(gutter - m.size.width / 2f, size.width - m.size.width.toFloat())
                drawText(m, topLeft = Offset(left, size.height - m.size.height))
            }
        }
        Text(summary, style = ExType.body, color = c.ink)
    }
}

// ---------------------------------------------------------------------------------------------
// Timeline

private enum class Mark { ORIGIN, REACHED, HERE, NEXT, LATER }

/**
 * Every milestone in order, as a vertical line: the last cigarette, what this gap has reached,
 * "you are here", the next one, then the rest. A milestone reached in an earlier gap says so. The
 * 5, 10 and 15 years fold into "The long road" until this gap is close to them.
 */
@Composable
private fun TimelineCard(since: Duration?, last: Instant?, r: RecoverySummary?, words: TimeWords) {
    val c = Ex.colors
    val reached = r?.reached ?: 0
    val nextIndex = r?.next?.let { Milestones.all.indexOf(it) } ?: -1
    val foldable = nextIndex in 0 until LONG_ROAD || since == null
    var open by rememberSaveable { mutableStateOf(false) }
    val expanded = open || !foldable

    ExCard(color = c.overParticles) {
        Overline("Body recovery")
        Column(Modifier.padding(top = ExSpace.sm)) {
            if (since != null && last != null) {
                TimelineRow(Mark.ORIGIN, solidAbove = false, solidBelow = true) {
                    Text("Your last one · ${words.at(last).replaceFirstChar { it.lowercase() }}", style = ExType.caption, color = c.inkSoft)
                }
            }
            Milestones.all.forEachIndexed { i, m ->
                if (i == reached && since != null) {
                    TimelineRow(Mark.HERE, solidAbove = true, solidBelow = false) {
                        Overline("You are here · ${Fmt.gap(since)}", color = c.oxyInk)
                    }
                }
                if (i == LONG_ROAD && foldable) {
                    LongRoadToggle(expanded) { open = !open }
                }
                val row = @Composable {
                    val inGap = since != null && i < reached
                    val mark = when {
                        inGap -> Mark.REACHED
                        i == nextIndex -> Mark.NEXT
                        else -> Mark.LATER
                    }
                    TimelineRow(mark, solidAbove = inGap, solidBelow = inGap, end = i == Milestones.all.lastIndex && reached < Milestones.all.size) {
                        MilestoneText(m, mark, since, last, r?.lastReached?.getOrNull(i), words)
                    }
                }
                if (i >= LONG_ROAD && foldable) {
                    AnimatedVisibility(expanded, enter = fadeIn(tween(280)) + expandVertically(tween(280)), exit = fadeOut(tween(280)) + shrinkVertically(tween(280))) {
                        Column { row() }
                    }
                } else row()
            }
            if (since != null && reached == Milestones.all.size) {
                TimelineRow(Mark.HERE, solidAbove = true, solidBelow = false, end = true) {
                    Overline("You are here · ${Fmt.gap(since)}", color = c.oxyInk)
                }
            }
        }
        Text("Source: WHO, Health benefits of smoking cessation.", style = ExType.caption, color = c.inkFaint, modifier = Modifier.padding(top = ExSpace.xs))
    }
}

@Composable
private fun ColumnScope.MilestoneText(m: Milestone, mark: Mark, since: Duration?, last: Instant?, lastReached: Instant?, words: TimeWords) {
    val c = Ex.colors
    val soft = mark == Mark.LATER
    val head = when {
        mark == Mark.NEXT && since != null -> "${m.label} · in ${Fmt.roughly(m.after.minus(since))}"
        else -> m.label
    }
    Text(head, style = ExType.numberSmall, color = if (mark == Mark.REACHED) c.oxyInk else if (soft) c.inkSoft else c.ink)
    Text(m.text, style = ExType.body, color = if (soft) c.inkSoft else c.ink)
    val note = when {
        mark == Mark.REACHED && last != null -> words.reachedSentence(last.plus(m.after))
        else -> listOfNotNull(
            m.range?.let { "WHO: $it" },
            if (mark == Mark.LATER && since != null) "in ${Fmt.roughly(m.after.minus(since))}" else null,
        ).joinToString(" · ").ifEmpty { null }
    }
    if (note != null) Text(note, style = ExType.caption, color = c.inkFaint)
    if (mark != Mark.REACHED && lastReached != null) {
        ExBadge("You’ve been here · ${words.day(lastReached)}", Modifier.padding(top = ExSpace.xxs), accent = c.oxy)
    }
}

/** A row of the timeline: its mark on the line at the left, the line above and below it (none below at the [end]), then [content]. */
@Composable
private fun TimelineRow(mark: Mark, solidAbove: Boolean, solidBelow: Boolean, end: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = Ex.colors
    val oxy = c.oxy
    val halo = c.tint(c.oxy)
    val ember = c.ember
    val sky = c.sky
    val faint = c.inkFaint
    val bg = c.surface
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(36.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = 14.dp.toPx()
                    val my = 10.dp.toPx()
                    val w = 2.dp.toPx()
                    val dots = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
                    if (mark != Mark.ORIGIN) {
                        if (solidAbove) drawLine(oxy, Offset(x, 0f), Offset(x, my), w)
                        else drawLine(faint.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, my), 1.5.dp.toPx(), StrokeCap.Round, dots)
                    }
                    if (end) {
                        // Nothing below the last mark.
                    } else if (solidBelow) {
                        val brush = if (mark == Mark.ORIGIN) Brush.verticalGradient(listOf(ember, sky, oxy), my, size.height) else Brush.verticalGradient(listOf(oxy, oxy))
                        drawLine(brush, Offset(x, my), Offset(x, size.height), w)
                    } else {
                        drawLine(faint.copy(alpha = 0.6f), Offset(x, my), Offset(x, size.height), 1.5.dp.toPx(), StrokeCap.Round, dots)
                    }
                    val p = Offset(x, my)
                    when (mark) {
                        Mark.ORIGIN -> drawCircle(ember, 5.dp.toPx(), p)
                        Mark.REACHED -> {
                            drawCircle(oxy, 10.dp.toPx(), p)
                            val s = 10.dp.toPx() / 12f
                            val check = Path().apply {
                                moveTo(p.x - 5f * s, p.y + 0.5f * s); lineTo(p.x - 1.5f * s, p.y + 4f * s); lineTo(p.x + 5.5f * s, p.y - 4f * s)
                            }
                            drawPath(check, bg, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        Mark.HERE -> gapDot(p, oxy, halo, bg, scale = 0.9f)
                        Mark.NEXT -> {
                            drawCircle(bg, 8.dp.toPx(), p)
                            drawCircle(oxy, 8.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                        }
                        Mark.LATER -> {
                            drawCircle(bg, 5.dp.toPx(), p)
                            drawCircle(faint, 5.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                        }
                    }
                }
        )
        Column(Modifier.weight(1f).padding(bottom = ExSpace.lg), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

/** "The long road · 5, 10 and 15 years": opens and closes the last three milestones. */
@Composable
private fun LongRoadToggle(open: Boolean, onToggle: () -> Unit) {
    val c = Ex.colors
    val turn by animateFloatAsState(if (open) 90f else 0f, tween(280), label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, bottom = ExSpace.md)
            .clip(ExRadius.pill)
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide" else "Show") { onToggle() }
            .semantics { stateDescription = if (open) "Shown" else "Hidden" }
            .background(c.surfaceHigh)
            .heightIn(min = 48.dp)
            .padding(horizontal = ExSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("The long road · 5, 10 and 15 years", style = ExType.labelSmall, color = c.ink, modifier = Modifier.weight(1f))
        Icon(ExIcons.Forward, null, Modifier.size(18.dp).rotate(turn), tint = c.inkSoft)
    }
}

// ---------------------------------------------------------------------------------------------
// Words for times

/** How the Air screen says when something happens, relative to [now], following the 12/24-hour setting. */
private class TimeWords(val now: Instant, val zone: ZoneId, val h24: Boolean) {
    fun clock(t: Instant): String = Fmt.time(t, zone, h24)

    private fun daysFromToday(t: Instant): Long =
        ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), t.atZone(zone).toLocalDate())

    private fun date(t: Instant): String {
        val d = t.atZone(zone).toLocalDate()
        val sameYear = d.year == now.atZone(zone).year
        return d.format(DateTimeFormatter.ofPattern(if (sameYear) "d MMM" else "d MMM yyyy", Locale.getDefault()))
    }

    /** A moment in the past, short: "12:19", "yesterday", "Sunday", "3 Oct". */
    fun reached(t: Instant): String {
        val d = daysFromToday(t)
        return when {
            d == 0L -> clock(t)
            d == -1L -> "yesterday"
            d >= -6L -> t.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            else -> date(t)
        }
    }

    /** "Reached at 12:19" today, else "Reached yesterday", "Reached Sunday", "Reached 3 Oct". */
    fun reachedSentence(t: Instant): String = if (daysFromToday(t) == 0L) "Reached at ${clock(t)}" else "Reached ${reached(t)}"

    /** The day of a past moment: "today", "yesterday", "Sunday", "3 Oct". */
    fun day(t: Instant): String = if (daysFromToday(t) == 0L) "today" else reached(t)

    /** A moment either side of now: "Today at 23:59", "Tomorrow at 09:10", "Saturday at 10:00", "On 18 Nov". */
    fun at(t: Instant): String {
        val d = daysFromToday(t)
        return when {
            d == 0L -> "Today at ${clock(t)}"
            d == 1L -> "Tomorrow at ${clock(t)}"
            d == -1L -> "Yesterday at ${clock(t)}"
            d in 2L..6L || d in -6L..-2L -> "${t.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} at ${clock(t)}"
            else -> "On ${date(t)}"
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Particles

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
