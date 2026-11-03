package app.exhale.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import app.exhale.domain.PlanMath
import app.exhale.ui.theme.ExRadius
import java.time.format.DateTimeFormatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.exhale.ui.components.rememberHaptics
import java.time.Duration
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
    onBorrowNoticeSeen: () -> Unit = {},
    initialTodayOpen: Boolean = false,
) {
    val c = Ex.colors
    val heat by animateFloatAsState(state.heat, tween(1200), label = "heat")
    val earned = state.lastLog != null && state.remainingToNext == null
    // Countdown ring around the ember: how much of the gap to the next one is earned.
    val ringTarget = state.sinceLast?.let {
        (it.toMillis().toFloat() / Duration.ofMinutes(state.intervalMinutes.toLong()).toMillis()).coerceIn(0f, 1f)
    } ?: 1f
    val ring by animateFloatAsState(ringTarget, tween(800), label = "ring")

    // Soft double tick when the next one becomes earned while the screen is open.
    val haptics = rememberHaptics()
    var wasEarned by remember { mutableStateOf(earned) }
    LaunchedEffect(earned) {
        if (earned && !wasEarned) haptics.earned()
        wasEarned = earned
    }

    // Today popover under the badge; the dot invites a tap on a day whose allowance was lowered.
    var todayOpen by remember { mutableStateOf(initialTodayOpen) }
    val borrowDot = state.borrowedToday > 0 && state.settings.borrowNoticeSeenDay != state.today.toEpochDay()
    BackHandler(enabled = todayOpen) { todayOpen = false }

    // The hint under the ember answers the gesture.
    var hint by remember { mutableStateOf(EmberHint.Idle) }
    LaunchedEffect(hint) {
        if (hint == EmberHint.TooShort || hint == EmberHint.Tapped) { delay(2000); hint = EmberHint.Idle }
    }
    LaunchedEffect(logged?.logId) {
        if (logged != null) { hint = EmberHint.Logged; delay(2600); hint = EmberHint.Idle }
    }

    Box(Modifier.fillMaxSize()) {
        ExScreen(
            scroll = false,
            header = {
                TodayBadge(state, showDot = borrowDot, expanded = todayOpen, onClick = {
                    todayOpen = !todayOpen
                    if (todayOpen && state.borrowedToday > 0) onBorrowNoticeSeen()
                })
                Fill()
                ExIconButton(ExIcons.Settings, "Settings", onSettings)
            },
        ) {
            // One number: time until the next one, then time since the last once it's earned.
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ExSpace.xxs),
            ) {
                val big = when {
                    state.lastLog == null -> "—"
                    earned -> Fmt.gap(state.sinceLast)
                    else -> Fmt.gap(state.remainingToNext)
                }
                Text(big, style = ExType.display.copy(fontSize = 64.sp), color = c.ink, textAlign = TextAlign.Center)
                val sub = when {
                    state.lastLog == null -> AnnotatedString("nothing logged yet")
                    earned -> buildAnnotatedString {
                        append("since your last one · ")
                        withStyle(SpanStyle(color = c.oxyInk)) { append("next one earned") }
                    }
                    else -> AnnotatedString("until your next one")
                }
                Text(sub, style = ExType.titleItalic.copy(fontSize = 21.sp), color = c.inkSoft, textAlign = TextAlign.Center)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmberOrb(
                    heat = heat,
                    onLog = onLog,
                    ring = ring,
                    nextLogIsOver = state.todayCount >= state.allowance,
                    onHoldStart = { hint = EmberHint.Holding; todayOpen = false },
                    onHoldEnd = { held -> hint = if (held < 0.08f) EmberHint.Tapped else EmberHint.TooShort },
                )
            }
            EmberHintLine(hint, state)
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
            LoggedCard(ev, state, onUndo = { onUndo(ev.logId) }, onTag = { onTag(ev.logId, it) })
        }

        // Tap anywhere outside the popover to close it.
        if (todayOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { todayOpen = false }
            )
        }
        AnimatedVisibility(
            visible = todayOpen,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = ExSpace.xs + ExSize.iconButton + ExSpace.xs)
                .padding(horizontal = ExSpace.sm),
            enter = fadeIn(tween(180)) + scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 0.92f, transformOrigin = TransformOrigin(0.12f, 0f)),
            exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.96f, transformOrigin = TransformOrigin(0.12f, 0f)),
        ) {
            TodayPopover(state)
        }
    }
}

/** What the hint under the ember is saying. */
private enum class EmberHint { Idle, Holding, Logged, TooShort, Tapped }

/** One line under the ember that reacts to the gesture; each change slides in from below. */
@Composable
private fun EmberHintLine(hint: EmberHint, state: ExhaleState) {
    val c = Ex.colors
    AnimatedContent(
        targetState = hint,
        transitionSpec = {
            (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(320))) togetherWith
                (slideOutVertically(tween(240)) { -it / 2 } + fadeOut(tween(200)))
        },
        modifier = Modifier.fillMaxWidth(),
        label = "ember-hint",
    ) { h ->
        val (text, color) = when (h) {
            EmberHint.Idle -> "Hold the ember to log" to c.inkFaint
            EmberHint.Holding -> "Keep holding…" to c.inkSoft
            EmberHint.Logged -> {
                val over = state.todayCount - state.allowance
                if (over > 0) "$over over · borrowed from tomorrow" to c.flareInk
                else "Logged · ${state.todayCount} of ${state.allowance} today" to c.emberInk
            }
            EmberHint.TooShort -> "Hold a little longer" to c.inkSoft
            EmberHint.Tapped -> "Hold the ember to log" to c.inkSoft
        }
        Text(
            text,
            style = if (h == EmberHint.Logged) ExType.labelSmall else ExType.body.copy(fontSize = ExType.labelSmall.fontSize),
            color = color, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun LoggedCard(ev: LoggedEvent, state: ExhaleState, onUndo: () -> Unit, onTag: (Trigger) -> Unit) {
    val c = Ex.colors
    ExCard(color = c.surfaceHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Logged", style = ExType.headline, color = c.ink)
                val over = state.todayCount - state.allowance
                Text(
                    if (over > 0) "${state.todayCount} of ${state.allowance} today · $over borrowed from tomorrow" else "${state.todayCount} of ${state.allowance} today",
                    style = ExType.caption, color = if (over > 0) c.flareInk else c.inkSoft,
                )
            }
            ExButton("Undo", onUndo, style = ButtonStyle.Ghost, size = app.exhale.ui.components.ButtonSize.Medium, icon = ExIcons.Undo)
        }
        Text("What set it off? Optional.", style = ExType.caption, color = c.inkSoft, modifier = Modifier.padding(top = ExSpace.xxs))
        TriggerChips(selected = null, onPick = onTag)
    }
}

/** Today's count. A couple of px larger than a plain badge, pink while over, with a dot on a day
 *  whose allowance was lowered by yesterday. Tapping opens the Today popover. */
@Composable
private fun TodayBadge(state: ExhaleState, showDot: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val c = Ex.colors
    val over = state.overAllowance && state.todayCount > state.allowance
    val bg by animateColorAsState(if (over) c.tint(c.flare) else c.surface, label = "badge-bg")
    val fg by animateColorAsState(if (over) c.flareInk else c.ink, label = "badge-fg")
    Box {
        Box(
            Modifier
                .clip(ExRadius.pill)
                .background(bg)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Close today" else "Show today", onClick = onClick)
                .semantics {
                    contentDescription = "${state.todayCount} of ${state.allowance} today" + (if (over) ", over" else "") + (if (showDot) ", new note" else "")
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text("${state.todayCount} of ${state.allowance} today", style = ExType.numberSmall.copy(fontSize = 14.sp), color = fg)
        }
        if (showDot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(12.dp)
                    .clip(ExRadius.pill)
                    .background(c.bg)
                    .padding(2.dp)
                    .clip(ExRadius.pill)
                    .background(c.sun)
            )
        }
    }
}

/** Popover under the badge: today's count, sticks, and why the number is what it is. */
@Composable
private fun TodayPopover(state: ExhaleState) {
    val c = Ex.colors
    val s = state.settings
    val base = PlanMath.baseAllowance(s, state.today)
    val over = state.todayCount - state.allowance
    val left = state.allowance - state.todayCount
    val tomorrowBase = PlanMath.baseAllowance(s, state.today.plusDays(1))
    val nextWeekDate = s.planStart.plusDays(((PlanMath.weeksIntoPlan(s.planStart, state.today) + 1) * 7).toLong())
    val nextWeek = PlanMath.baseAllowance(s, nextWeekDate)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 16.dp, ExRadius.lg, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(ExRadius.lg)
            .background(if (c.isDark) c.surfaceHigh else c.surface)
            .border(1.dp, c.line.copy(alpha = if (c.isDark) 1f else 0.6f), ExRadius.lg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(ExSpace.lg),
        verticalArrangement = Arrangement.spacedBy(ExSpace.sm),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
            Text("${state.todayCount} of ${state.allowance}", style = ExType.number, color = if (over > 0) c.flareInk else c.ink)
            Text("today", style = ExType.caption, color = c.inkSoft, modifier = Modifier.padding(bottom = 3.dp))
        }
        TodaySticks(used = state.todayCount, allowance = state.allowance)
        val line = buildAnnotatedString {
            when {
                over > 0 -> {
                    withStyle(SpanStyle(color = c.flareInk, fontWeight = FontWeight.SemiBold)) { append("$over over today. ") }
                    append("It's borrowed from tomorrow, so tomorrow starts at ${(tomorrowBase - over).coerceAtLeast(0)} instead of $tomorrowBase.")
                }
                left == 0 -> {
                    withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("That's all for today. ") }
                    append("Anything more is borrowed from tomorrow.")
                }
                else -> {
                    withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("$left left today. ") }
                    append("Going over isn't failing; anything over is borrowed from tomorrow.")
                }
            }
        }
        Text(line, style = ExType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = c.inkSoft)
        if (state.borrowedToday > 0) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.sunInk, fontWeight = FontWeight.SemiBold)) { append("Today is ${state.allowance} instead of $base: ") }
                    append("${state.borrowedToday} ${if (state.borrowedToday == 1) "was" else "were"} borrowed yesterday.")
                },
                style = ExType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = c.inkSoft,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Text(
            buildAnnotatedString {
                append("This week ")
                withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("$base a day") }
                append("  ·  From ${nextWeekDate.format(DateTimeFormatter.ofPattern("MMM d"))} ")
                withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("$nextWeek") }
            },
            style = ExType.caption, color = c.inkSoft,
        )
    }
}

/** Today as sticks: used (ember), the next one (dashed oxygen), over (flare), remaining. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaySticks(used: Int, allowance: Int) {
    val c = Ex.colors
    val empty = if (c.isDark) c.surface else c.surfaceHigh
    FlowRow(
        Modifier.fillMaxWidth().semantics { contentDescription = "$used of $allowance used today" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (i in 0 until maxOf(allowance, used)) {
            val m = Modifier.size(width = 13.dp, height = 28.dp).clip(ExRadius.pill)
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
                else -> Box(m.background(empty))
            }
        }
    }
}
