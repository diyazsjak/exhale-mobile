package app.exhale.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import app.exhale.ui.components.Haptics
import app.exhale.ui.components.rememberHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the home screen must be held to log. */
const val HOLD_MS = 1500

/** Haptic ticks during a hold, rising in strength. */
private const val CHARGE_TICKS = 12

/** The log's burst animation. */
private const val BURST_MS = 900

/** Time for a full hold to drain back to zero after release. */
private const val RELEASE_MS = 450

/** A release before this much of the hold counts as a tap. */
internal const val TAP_THRESHOLD = 0.08f

/**
 * Press and hold to log, shared by both home screens (Ember and Glow) so they feel the same:
 * 12 `charge()` ticks rising over [HOLD_MS], then a `logged()` thud (`loggedOver()` when the log
 * goes over today's allowance). Releasing early cancels and drains the hold.
 *
 * [hold] runs 0..1 while pressed, [burst] 0..1 once after a log, [wobble] 0..1 after a quick tap.
 */
@Stable
class HoldToLog internal constructor(private val scope: CoroutineScope, private val haptics: Haptics) {
    val hold = Animatable(0f)
    val burst = Animatable(0f)
    val wobble = Animatable(1f)

    /** Whether the last log went over today's allowance (pink burst). */
    var burstOver by mutableStateOf(false)
        private set

    /** Where the last press landed, in the held element's coordinates. Null before any press (or from TalkBack or a keyboard). */
    var pressAt by mutableStateOf<Offset?>(null)
        private set

    internal var onLog: () -> Unit = {}
    internal var nextLogIsOver = false
    internal var onHoldStart: () -> Unit = {}
    internal var onHoldEnd: (Float) -> Unit = {}

    private fun release() = scope.launch {
        hold.animateTo(0f, tween((RELEASE_MS * hold.value).toInt().coerceAtLeast(1), easing = LinearEasing))
    }

    /** TalkBack's long-press action: logs straight away. */
    internal fun logNow() {
        haptics.logged()
        onLog()
    }

    internal suspend fun press(gesture: PressGestureScope, at: Offset) {
        pressAt = at
        onHoldStart()
        var committed = false
        val job = scope.launch {
            val ticks = launch {
                repeat(CHARGE_TICKS) { k ->
                    delay(HOLD_MS.toLong() / CHARGE_TICKS)
                    haptics.charge((k + 1f) / CHARGE_TICKS)
                }
            }
            hold.animateTo(1f, tween((HOLD_MS * (1f - hold.value)).toInt().coerceAtLeast(1), easing = LinearEasing))
            ticks.cancel()
            committed = true
            burstOver = nextLogIsOver
            if (burstOver) haptics.loggedOver() else haptics.logged()
            onLog()
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
                burst.snapTo(0f)
            }
            release()
        }
        gesture.tryAwaitRelease()
        if (!committed) {
            val held = hold.value
            job.cancel()
            haptics.cancel()
            release()
            if (held < TAP_THRESHOLD) scope.launch { wobble.snapTo(0f); wobble.animateTo(1f, tween(600, easing = LinearEasing)) }
            onHoldEnd(held)
        }
    }
}

/**
 * [onHoldStart] fires on press; [onHoldEnd] on an early release with how far the hold got (0..1),
 * so the screen can react ("Hold a little longer"). [nextLogIsOver]: the next log goes over today's
 * allowance.
 */
@Composable
fun rememberHoldToLog(
    onLog: () -> Unit,
    nextLogIsOver: Boolean = false,
    onHoldStart: () -> Unit = {},
    onHoldEnd: (held: Float) -> Unit = {},
): HoldToLog {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val h = remember(haptics, scope) { HoldToLog(scope, haptics) }
    SideEffect {
        h.onLog = onLog
        h.nextLogIsOver = nextLogIsOver
        h.onHoldStart = onHoldStart
        h.onHoldEnd = onHoldEnd
    }
    return h
}

/** Makes this element the place to press and hold, with [description] and a long-press action for TalkBack. */
fun Modifier.holdToLog(h: HoldToLog, description: String): Modifier = this
    .semantics {
        contentDescription = description
        onLongClick("Log cigarette") { h.logNow(); true }
    }
    .pointerInput(h) { detectTapGestures(onPress = { at -> h.press(this, at) }) }
