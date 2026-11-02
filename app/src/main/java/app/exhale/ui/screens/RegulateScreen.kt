package app.exhale.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.domain.PlanMath
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.components.Eyebrow
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.rememberHaptics
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import java.time.Duration
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegulateScreen(state: ExhaleState, onUrge: () -> Unit) {
    val s = state.settings
    val week = PlanMath.weeksIntoPlan(s.planStart, state.today) + 1
    val ready = state.remainingToNext == null
    val haptics = rememberHaptics()
    var wasReady by remember { mutableStateOf(ready) }
    LaunchedEffect(ready) {
        if (ready && !wasReady) haptics.earned()
        wasReady = ready
    }

    val elapsedFrac = state.sinceLast?.let {
        (it.toMillis().toFloat() / Duration.ofMinutes(state.intervalMinutes.toLong()).toMillis()).coerceIn(0f, 1f)
    } ?: 1f
    val sweep by animateFloatAsState(elapsedFrac, tween(800), label = "sweep")

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF121A3A), Ex.Void)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Regulate · week $week", Modifier.weight(1f))
            Eyebrow("${state.allowance}/day")
        }

        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize(0.92f).semantics { contentDescription = "Interval ring, ${(sweep * 100).toInt()} percent earned" }) {
                val stroke = 16.dp.toPx()
                val r = size.minDimension / 2f - stroke
                val tl = Offset(center.x - r, center.y - r)
                drawCircle(Ex.Line, r, center, style = Stroke(stroke))
                drawArc(
                    Brush.sweepGradient(listOf(Ex.Sky, Ex.Oxy, Ex.Sky), center),
                    -90f, 360f * sweep, false, tl, Size(r * 2, r * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                for (i in 0 until 60) {
                    val a = Math.toRadians(i * 6.0 - 90)
                    val r1 = r - stroke - if (i % 5 == 0) 12.dp.toPx() else 6.dp.toPx()
                    val r2 = r - stroke - 2.dp.toPx()
                    drawLine(
                        Ex.Mute.copy(alpha = if (i % 5 == 0) 0.6f else 0.25f),
                        Offset(center.x + (cos(a) * r1).toFloat(), center.y + (sin(a) * r1).toFloat()),
                        Offset(center.x + (cos(a) * r2).toFloat(), center.y + (sin(a) * r2).toFloat()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow(if (ready) "Earned" else "Next in")
                Text(if (ready) "Now" else Fmt.clock(state.remainingToNext), style = ExType.data.copy(fontSize = 34.sp), color = if (ready) Ex.Oxy else Ex.Ink)
                Text("interval ${state.intervalMinutes} min", style = ExType.small, color = Ex.Mute)
            }
        }

        AllowanceSticks(used = state.todayCount, allowance = state.allowance)
        if (state.borrowedToday > 0) {
            Text(
                "${state.borrowedToday} borrowed from yesterday, so today's allowance is lower.",
                style = ExType.small, color = Ex.Sun,
            )
        }

        ExButton(if (ready) "Wait longer anyway" else "Ride it out", onUrge, Modifier.fillMaxWidth())

        PlanCard(state)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllowanceSticks(used: Int, allowance: Int) {
    val total = maxOf(allowance, used)
    FlowRow(
        Modifier.fillMaxWidth().semantics { contentDescription = "$used of $allowance used today" },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0 until total) {
            val shape = RoundedCornerShape(5.dp)
            val m = Modifier.size(width = 16.dp, height = 28.dp).clip(shape)
            when {
                i < minOf(used, allowance) -> Box(m.background(Brush.verticalGradient(listOf(Ex.Sun, Ex.Ember))))
                i >= allowance -> Box(m.background(Ex.Flare))
                i == used -> Canvas(m) {
                    drawRoundRect(
                        Ex.Oxy, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                    )
                }
                else -> Box(m.background(Ex.Line))
            }
        }
    }
}

@Composable
private fun PlanCard(state: ExhaleState) {
    val s = state.settings
    val thisWeek = PlanMath.baseAllowance(s, state.today)
    val nextWeekDate = s.planStart.plusDays(((PlanMath.weeksIntoPlan(s.planStart, state.today) + 1) * 7).toLong())
    val nextWeek = PlanMath.baseAllowance(s, nextWeekDate)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Ex.Line, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow("Plan · ${s.taper.label}")
        Row {
            Column(Modifier.weight(1f)) {
                Text("$thisWeek", style = ExType.data, color = Ex.Ink)
                Text("per day this week", style = ExType.small, color = Ex.Mute)
            }
            Column(Modifier.weight(1f)) {
                Text("$nextWeek", style = ExType.data, color = Ex.Oxy)
                Text("from ${nextWeekDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))}", style = ExType.small, color = Ex.Mute)
            }
        }
        Spacer(Modifier.size(2.dp))
        Text(
            "Smoking early is allowed. Anything over today's allowance is borrowed from tomorrow.",
            style = ExType.small, color = Ex.Mute,
        )
    }
}
