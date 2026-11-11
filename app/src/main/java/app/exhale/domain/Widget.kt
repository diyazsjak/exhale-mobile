package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor

/**
 * The home-screen widget's rules (SPEC §3.9): what it shows and what a tap does.
 *
 * A widget can't redraw every minute, so it shows clock times ("Next one at 3:13") rather than a
 * countdown, and its ring moves in [RING_STEPS] steps. [WidgetModel.refreshAt] says when the next
 * redraw is due. Launchers take the long-press on a widget, so logging is two taps instead of a hold:
 * the first arms it for [ARM_WINDOW], the second logs, and Undo shows for [UNDO_WINDOW]. The ends of
 * those windows are seconds away, so the widget redraws itself then (not through [WidgetModel.refreshAt]).
 */
object Widget {
    val ARM_WINDOW: Duration = Duration.ofSeconds(4)
    val UNDO_WINDOW: Duration = Duration.ofSeconds(8)

    /** The ring fills in this many steps between two cigarettes. */
    const val RING_STEPS = 8

    /** What a tap on the ring (2×2) or the ember button (4×1) does. */
    fun onTap(tap: WidgetTap, now: Instant): TapResult = when {
        tap.undoShown(now) -> TapResult.Ignore
        tap.armed(now) -> TapResult.Log
        else -> TapResult.Arm(now.plus(ARM_WINDOW))
    }

    /** [fraction] (0..1) rounded down to a ring step, so the ring never shows more than is earned. */
    fun ringStep(fraction: Float): Float = floor(fraction.coerceIn(0f, 1f) * RING_STEPS) / RING_STEPS

    /** [logs] oldest first. */
    fun model(s: UserSettings, logs: List<SmokeLog>, tap: WidgetTap, now: Instant, zone: ZoneId): WidgetModel {
        val tomorrow = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        if (!s.onboarded) {
            return WidgetModel(WidgetKind.SETUP, 0, 0, 0, 0, 1f, null, null, null, tomorrow)
        }
        val plan = TodayPlan.of(s, logs, now, zone)
        val armed = tap.armed(now)
        val undo = tap.undoShown(now)
        val kind = when {
            undo -> WidgetKind.LOGGED
            armed -> WidgetKind.ARMED
            plan.last == null -> WidgetKind.NOTHING_YET
            plan.earned -> WidgetKind.EARNED
            else -> WidgetKind.WAITING
        }
        return WidgetModel(
            kind = kind,
            count = plan.count,
            allowance = plan.allowance,
            overToday = plan.overToday,
            borrowingFromTomorrow = plan.borrowingFromTomorrow,
            ringFill = ringStep(plan.earnedFraction),
            nextAt = plan.nextAllowedAt,
            lastAt = plan.last?.at,
            loggedAt = if (undo) logs.lastOrNull { it.id == tap.loggedId }?.at else null,
            refreshAt = nextRefresh(plan, now, tomorrow),
        )
    }

    /**
     * The earliest moment the widget looks different: the next ring step, the moment the next one is
     * earned, or midnight (the count starts over).
     */
    private fun nextRefresh(plan: TodayPlan, now: Instant, midnight: Instant): Instant {
        val times = mutableListOf(midnight)
        val last = plan.last?.at
        val next = plan.nextAllowedAt
        if (last != null && next != null && next.isAfter(now)) {
            val step = Duration.between(last, next).dividedBy(RING_STEPS.toLong())
            if (!step.isZero) {
                val passed = Duration.between(last, now).toMillis() / step.toMillis()
                times += last.plus(step.multipliedBy(passed + 1))
            }
            times += next
        }
        return times.filter { it.isAfter(now) }.min()
    }
}

/** A widget's own tap state: when its arming and Undo windows close, and the log Undo removes. */
data class WidgetTap(
    val armedUntil: Instant? = null,
    val loggedId: Long? = null,
    val undoUntil: Instant? = null,
) {
    fun armed(now: Instant): Boolean = armedUntil?.isAfter(now) == true
    fun undoShown(now: Instant): Boolean = loggedId != null && undoUntil?.isAfter(now) == true
}

sealed interface TapResult {
    /** First tap: ask for a second one until [until]. */
    data class Arm(val until: Instant) : TapResult
    /** Second tap inside the window: log one now. */
    data object Log : TapResult
    /** Undo is showing: the ring waits until it's gone, so a stray tap can't log another. */
    data object Ignore : TapResult
}

enum class WidgetKind { SETUP, NOTHING_YET, WAITING, EARNED, ARMED, LOGGED }

data class WidgetModel(
    val kind: WidgetKind,
    val count: Int,
    val allowance: Int,
    /** Cigarettes over today's allowance (the count turns flare). */
    val overToday: Int,
    /** What goes past today's base and lowers tomorrow (§4 Debt). */
    val borrowingFromTomorrow: Int,
    /** The countdown ring, in [Widget.RING_STEPS] steps; 1 when earned or before any log. */
    val ringFill: Float,
    val nextAt: Instant?,
    val lastAt: Instant?,
    /** When the log that Undo would remove was made (only while Undo shows). */
    val loggedAt: Instant?,
    val refreshAt: Instant,
) {
    /** Heat for the ring's core, from the same steps: 1 just after a log, 0 once earned. */
    val heat: Float get() = 1f - ringFill
}
