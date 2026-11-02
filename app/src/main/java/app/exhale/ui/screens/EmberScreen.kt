package app.exhale.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.exhale.domain.Trigger
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.LoggedEvent
import app.exhale.ui.components.Eyebrow
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.TriggerRow
import app.exhale.ui.components.rememberHaptics
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOLD_MS = 1500

@Composable
fun EmberScreen(
    state: ExhaleState,
    logged: LoggedEvent?,
    onLog: () -> Unit,
    onUndo: (Long) -> Unit,
    onTag: (Long, Trigger) -> Unit,
    onDismissLogged: () -> Unit,
    onUrge: () -> Unit,
    onSettings: () -> Unit,
) {
    val heat by animateFloatAsState(state.heat, tween(1200), label = "heat")
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(lerp(Color(0xFF0F2A3A), Color(0xFF2A1230), heat), Ex.Void), radius = 1400f))
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Today")
                Spacer(Modifier.width(10.dp))
                Text("${state.todayCount} / ${state.allowance}", style = ExType.dataSmall, color = if (state.overAllowance) Ex.Flare else Ex.Ink)
                Spacer(Modifier.weight(1f))
                Text(
                    "SETTINGS", style = ExType.eyebrow, color = Ex.Mute,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSettings).padding(8.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(Fmt.gap(state.sinceLast), style = ExType.hero, color = Ex.Ink)
            Text(subline(state), style = ExType.body, color = Ex.Mute)

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                HoldOrb(heat = heat, onLog = onLog)
            }
            Eyebrow("Hold to light · 1.5 s", Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.size(16.dp))
            ExButton("I want one. Help me wait.", onUrge, Modifier.fillMaxWidth(), color = Ex.Flare, filled = false)
        }

        // Keep the last event while the toast animates out.
        var shown by remember { mutableStateOf(logged) }
        if (logged != null) shown = logged
        AnimatedVisibility(
            visible = logged != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            enter = slideInVertically(spring(dampingRatio = 0.6f)) { it * 2 },
            exit = slideOutVertically { it * 2 },
        ) {
            val ev = shown ?: return@AnimatedVisibility
            LaunchedEffect(ev.logId) { delay(8000); onDismissLogged() }
            LoggedToast(ev, onUndo = { onUndo(ev.logId) }, onTag = { onTag(ev.logId, it) })
        }
    }
}

private fun subline(s: ExhaleState): String = when {
    s.lastLog == null -> "No cigarettes logged yet. Hold the ember when you light one."
    s.remainingToNext == null -> "since your last one. Your next one is earned."
    else -> "since your last one. Next earned in ${Fmt.gap(s.remainingToNext)}."
}

@Composable
private fun LoggedToast(ev: LoggedEvent, onUndo: () -> Unit, onTag: (Trigger) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ex.Ink)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Logged. ${ev.countAfter} of ${ev.allowance} today.", style = ExType.bodyStrong, color = Ex.Void, modifier = Modifier.weight(1f))
            Text(
                "UNDO", style = ExType.eyebrow, color = Ex.Void,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onUndo).padding(8.dp),
            )
        }
        Text("What set it off? (optional)", style = ExType.small, color = Ex.Line)
        Box(Modifier.clip(RoundedCornerShape(50)).background(Ex.Void).padding(6.dp)) {
            TriggerRow(selected = null, onPick = onTag)
        }
    }
}

/**
 * The ember. Colour follows [heat]; press-and-hold for [HOLD_MS] to log.
 * Releasing early cancels. A TalkBack long-press action logs directly.
 */
@Composable
private fun HoldOrb(heat: Float, onLog: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val currentOnLog by rememberUpdatedState(onLog)
    val press by animateFloatAsState(if (progress.value > 0f) 0.92f else 1f, spring(dampingRatio = 0.45f), label = "press")

    val breathe = rememberInfiniteTransition(label = "breathe")
    val b by breathe.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse), label = "b")

    val core = lerp(Color.White, Ex.Glow, heat)
    val mid = lerp(Ex.Oxy, Ex.Sun, heat)
    val body = Ex.heatColor(heat)
    val edge = Ex.heatAccent(heat)

    Canvas(
        Modifier
            .size(260.dp)
            .semantics {
                contentDescription = "Ember. Press and hold to log a cigarette."
                onLongClick("Log cigarette") { currentOnLog(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    haptics.logging()
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        haptics.logged()
                        currentOnLog()
                        progress.animateTo(0f, tween(250))
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f && job.isActive) {
                        job.cancel()
                        haptics.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                })
            }
    ) {
        val r = size.minDimension / 2f
        val c = center
        val s = (1f + 0.06f * b) * press
        // Halo
        drawCircle(Brush.radialGradient(listOf(edge.copy(alpha = 0.45f), Color.Transparent), c, r), r, c)
        scale(s, c) {
            drawCircle(
                Brush.radialGradient(
                    0f to core,
                    0.14f to mid,
                    0.42f to body,
                    0.64f to edge,
                    0.72f to Color.Transparent,
                    center = Offset(c.x - r * 0.06f, c.y - r * 0.08f),
                    radius = r * 0.92f,
                ),
                r * 0.92f, c,
            )
        }
        // Hold ring
        if (progress.value > 0f) {
            val inset = 6.dp.toPx()
            drawArc(
                color = Ex.Ink,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
