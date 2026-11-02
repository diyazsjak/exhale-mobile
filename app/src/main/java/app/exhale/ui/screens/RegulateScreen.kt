package app.exhale.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.exhale.domain.PlanMath
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.components.ExBadge
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.Fill
import app.exhale.ui.components.GradientRing
import app.exhale.ui.components.Overline
import app.exhale.ui.components.ScreenTitle
import app.exhale.ui.components.Stat
import app.exhale.ui.components.rememberHaptics
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.Duration
import java.time.format.DateTimeFormatter

@Composable
fun RegulateScreen(state: ExhaleState, onUrge: () -> Unit) {
    val c = Ex.colors
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

    ExScreen(
        header = {
            Overline("Regulate · week $week")
            Fill()
            ExBadge("${state.allowance} a day")
        },
    ) {
        ScreenTitle(if (ready) "Your next one\nis earned" else "Next one in")
        GradientRing(
            progress = sweep,
            colors = listOf(c.sky, c.oxy),
            modifier = Modifier.padding(horizontal = ExSpace.lg, vertical = ExSpace.xs),
            contentDescription = "Interval ${(sweep * 100).toInt()} percent earned",
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (ready) "Now" else Fmt.clock(state.remainingToNext), style = ExType.numberLarge, color = if (ready) c.oxyInk else c.ink)
                Text("${state.intervalMinutes} min between", style = ExType.caption, color = c.inkSoft)
            }
        }
        AllowanceSticks(used = state.todayCount, allowance = state.allowance)
        if (state.borrowedToday > 0) {
            Text(
                "${state.borrowedToday} borrowed from yesterday, so today's allowance is lower.",
                style = ExType.caption, color = c.sunInk, modifier = Modifier.fillMaxWidth(),
            )
        }
        ExButton(if (ready) "Wait a little longer" else "Ride it out", onUrge, Modifier.fillMaxWidth(), icon = ExIcons.Wave)
        PlanCard(state)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllowanceSticks(used: Int, allowance: Int) {
    val c = Ex.colors
    val total = maxOf(allowance, used)
    FlowRow(
        Modifier.fillMaxWidth().semantics { contentDescription = "$used of $allowance used today" },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0 until total) {
            val m = Modifier.size(width = 14.dp, height = 30.dp).clip(ExRadius.pill)
            when {
                i < minOf(used, allowance) -> Box(m.background(c.ember))
                i >= allowance -> Box(m.background(c.flare))
                i == used -> {
                    val dash = c.oxy
                    Canvas(m) {
                        drawRoundRect(
                            dash, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))),
                            cornerRadius = CornerRadius(size.width / 2),
                        )
                    }
                }
                else -> Box(m.background(c.surface))
            }
        }
    }
}

@Composable
private fun PlanCard(state: ExhaleState) {
    val c = Ex.colors
    val s = state.settings
    val thisWeek = PlanMath.baseAllowance(s, state.today)
    val nextWeekDate = s.planStart.plusDays(((PlanMath.weeksIntoPlan(s.planStart, state.today) + 1) * 7).toLong())
    val nextWeek = PlanMath.baseAllowance(s, nextWeekDate)
    ExCard {
        Overline("Plan · ${s.taper.label}")
        Row(Modifier.padding(vertical = ExSpace.xs)) {
            Stat("$thisWeek", "a day this week", Modifier.weight(1f))
            Stat("$nextWeek", "from ${nextWeekDate.format(DateTimeFormatter.ofPattern("MMM d"))}", Modifier.weight(1f), color = c.oxyInk)
        }
        Text(
            "Smoking early is fine. Anything over today's allowance is borrowed from tomorrow.",
            style = ExType.caption, color = c.inkSoft,
        )
    }
}
