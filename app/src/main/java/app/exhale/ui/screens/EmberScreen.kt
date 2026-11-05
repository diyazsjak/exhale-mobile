package app.exhale.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.domain.Trigger
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.LoggedEvent
import app.exhale.ui.components.ButtonSize
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.TriggerChips
import app.exhale.ui.components.rememberHaptics
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

@Composable
fun EmberScreen(
    state: ExhaleState,
    logged: LoggedEvent?,
    onLog: () -> Unit,
    onUndo: (Long) -> Unit,
    onTag: (Long, Trigger) -> Unit,
    onDismissLogged: () -> Unit,
    onUrge: () -> Unit,
    todayOpen: Boolean = false,
    onTodayOpen: (Boolean) -> Unit = {},
) {
    val c = Ex.colors
    val heat by animateFloatAsState(state.heat, tween(1200), label = "heat")
    val earned = state.earned
    // Countdown ring around the ember: how much of the gap to the next one is earned.
    val ring by animateFloatAsState(state.earnedFraction, tween(800), label = "ring")

    // Soft double tick when the next one becomes earned while the screen is open.
    val haptics = rememberHaptics()
    var wasEarned by remember { mutableStateOf(earned) }
    LaunchedEffect(earned) {
        if (earned && !wasEarned) haptics.earned()
        wasEarned = earned
    }

    // Today popover under the badge, which lives in the shared app bar (see TodayBadge); the state is hoisted.
    BackHandler(enabled = todayOpen) { onTodayOpen(false) }

    // The hint under the ember answers the gesture.
    var hint by remember { mutableStateOf(EmberHint.Idle) }
    LaunchedEffect(hint) {
        if (hint == EmberHint.TooShort || hint == EmberHint.Tapped) { delay(2000); hint = EmberHint.Idle }
    }
    // After a log the hint simply returns to rest; the Logged card says what happened.
    LaunchedEffect(logged?.logId) {
        if (logged != null) hint = EmberHint.Idle
    }

    Box(Modifier.fillMaxSize()) {
        ExScreen(
            scroll = false,
            underAppBar = true,
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
                    nextLogIsOver = state.overAllowance,
                    onHoldStart = { hint = EmberHint.Holding; onTodayOpen(false) },
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
            // Unhurried: it arrives after the log's burst has played out.
            enter = slideInVertically(tween(650, delayMillis = 250, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(500, delayMillis = 250)),
            exit = slideOutVertically(tween(450, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(350)),
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
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTodayOpen(false) }
            )
        }
        AnimatedVisibility(
            visible = todayOpen,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = ExSpace.xs + ExSize.iconButton + ExSpace.xs)
                .padding(horizontal = ExSpace.sm),
            // Quick: it answers a tap.
            enter = fadeIn(tween(100)) + scaleIn(tween(170, easing = FastOutSlowInEasing), initialScale = 0.94f, transformOrigin = TransformOrigin(0.12f, 0f)),
            exit = fadeOut(tween(100)) + scaleOut(tween(130), targetScale = 0.97f, transformOrigin = TransformOrigin(0.12f, 0f)),
        ) {
            TodayPopover(state)
        }
    }
}

/** What the hint under the ember is saying. */
private enum class EmberHint { Idle, Holding, TooShort, Tapped }

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
            EmberHint.TooShort -> "Hold a little longer" to c.inkSoft
            EmberHint.Tapped -> "Hold the ember to log" to c.inkSoft
        }
        Text(
            text,
            style = ExType.body.copy(fontSize = ExType.labelSmall.fontSize),
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
                val borrowing = state.borrowingFromTomorrow
                Text(
                    "${state.todayCount} of ${state.allowance} today" + if (borrowing > 0) " · $borrowing borrowed from tomorrow" else "",
                    style = ExType.caption, color = if (state.overToday > 0) c.flareInk else c.inkSoft,
                )
            }
            ExButton("Undo", onUndo, style = ButtonStyle.Ghost, size = ButtonSize.Medium, icon = ExIcons.Undo)
        }
        Text("What set it off? Optional.", style = ExType.caption, color = c.inkSoft, modifier = Modifier.padding(top = ExSpace.xxs))
        TriggerChips(selected = null, onPick = onTag)
    }
}

/**
 * Today's count, the Ember tab's leading in the app bar. A couple of px larger than a plain badge,
 * pink while over, with a dot on a day whose allowance was lowered by yesterday
 * ([ExhaleState.borrowNoticeDue]). Tapping opens the Today popover.
 */
@Composable
internal fun TodayBadge(state: ExhaleState, expanded: Boolean, onClick: () -> Unit) {
    val c = Ex.colors
    val showDot = state.borrowNoticeDue
    val over = state.overToday > 0
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
    val over = state.overToday
    val left = state.leftToday
    val plan = state.outlook
    val borrowing = state.borrowingFromTomorrow
    val tomorrow = state.tomorrowAllowance
    val lowered = state.allowance < plan.baseToday
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
                    when {
                        borrowing == 0 -> append("You're still within your usual ${plan.baseToday}, so nothing is borrowed: tomorrow starts at $tomorrow.")
                        borrowing < over -> append(
                            "$borrowing of them ${if (borrowing == 1) "is" else "are"} past your usual ${plan.baseToday} and borrowed from tomorrow, " +
                                "so tomorrow starts at $tomorrow instead of ${plan.baseTomorrow}."
                        )
                        else -> append("It's borrowed from tomorrow, so tomorrow starts at $tomorrow instead of ${plan.baseTomorrow}.")
                    }
                }
                left == 0 -> {
                    withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("That's all for today. ") }
                    append(if (lowered) "Anything past your usual ${plan.baseToday} is borrowed from tomorrow." else "Anything more is borrowed from tomorrow.")
                }
                else -> {
                    withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("$left left today. ") }
                    append("Going over isn't failing; anything ${if (lowered) "past your usual ${plan.baseToday}" else "over"} is borrowed from tomorrow.")
                }
            }
        }
        Text(line, style = ExType.bodySmall, color = c.inkSoft)
        if (state.borrowedToday > 0) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.sunInk, fontWeight = FontWeight.SemiBold)) { append("Today is ${state.allowance} instead of ${plan.baseToday}: ") }
                    append("${state.borrowedToday} ${if (state.borrowedToday == 1) "was" else "were"} borrowed yesterday.")
                },
                style = ExType.bodySmall, color = c.inkSoft,
            )
        }
        Box(Modifier.fillMaxWidth().height(ExSize.hairline).background(c.line))
        Text(
            buildAnnotatedString {
                append("This week ")
                withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("${plan.baseToday} a day") }
                append("  ·  From ${plan.nextWeekStart.format(DateTimeFormatter.ofPattern("MMM d"))} ")
                withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("${plan.baseNextWeek}") }
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
