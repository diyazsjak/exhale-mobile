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
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.domain.Trigger
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.LoggedEvent
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.ExBadge
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExIconButton
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.Fill
import app.exhale.ui.components.TriggerChips
import app.exhale.ui.components.WeekStrip
import app.exhale.ui.components.rememberHaptics
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val c = Ex.colors
    val heat by animateFloatAsState(state.heat, tween(1200), label = "heat")
    Box(Modifier.fillMaxSize()) {
        ExScreen(
            scroll = false,
            header = {
                ExBadge("${state.todayCount} of ${state.allowance} today", accent = if (state.overAllowance) c.flare else null)
                Fill()
                ExIconButton(ExIcons.Settings, "Settings", onSettings)
            },
        ) {
            if (state.week.isNotEmpty()) WeekStrip(state.week, state.today)
            Column(
                Modifier.fillMaxWidth().padding(top = ExSpace.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ExSpace.xxs),
            ) {
                Text(Fmt.gap(state.sinceLast), style = ExType.display.copy(fontSize = 64.sp), color = c.ink, textAlign = TextAlign.Center)
                Text(
                    if (state.lastLog == null) "nothing logged yet" else "since your last one",
                    style = ExType.titleItalic.copy(fontSize = 22.sp), color = c.inkSoft,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmberOrb(heat = heat, onLog = onLog)
            }
            Text(nextLine(state), style = ExType.caption, color = c.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            ExButton("I want one. Help me wait.", onUrge, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = ExIcons.Wave)
        }

        // Keep the last event while the card animates out.
        var shown by remember { mutableStateOf(logged) }
        if (logged != null) shown = logged
        AnimatedVisibility(
            visible = logged != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = ExSpace.sm)
                .padding(bottom = ExSize.tabBar + ExSpace.lg),
            enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 400f)) { it },
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val ev = shown ?: return@AnimatedVisibility
            LaunchedEffect(ev.logId) { delay(8000); onDismissLogged() }
            LoggedCard(ev, onUndo = { onUndo(ev.logId) }, onTag = { onTag(ev.logId, it) })
        }
    }
}

private fun nextLine(s: ExhaleState): String = when {
    s.lastLog == null -> "Hold the ember for a moment when you light one."
    s.remainingToNext == null -> "Your next one is earned. Hold the ember to log."
    else -> "Next one earned in ${Fmt.gap(s.remainingToNext)}. Hold the ember to log."
}

@Composable
private fun LoggedCard(ev: LoggedEvent, onUndo: () -> Unit, onTag: (Trigger) -> Unit) {
    val c = Ex.colors
    ExCard(color = c.surfaceHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Logged", style = ExType.headline, color = c.ink)
                Text("${ev.countAfter} of ${ev.allowance} today", style = ExType.caption, color = c.inkSoft)
            }
            ExButton("Undo", onUndo, style = ButtonStyle.Ghost, size = app.exhale.ui.components.ButtonSize.Medium, icon = ExIcons.Undo)
        }
        Text("What set it off? Optional.", style = ExType.caption, color = c.inkSoft, modifier = Modifier.padding(top = ExSpace.xxs))
        TriggerChips(selected = null, onPick = onTag)
    }
}
