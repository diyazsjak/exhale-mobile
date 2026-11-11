package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The notifications Exhale can send. Each is off until the user turns it on (Settings → Reminders). */
enum class ReminderKind(
    /** How late a reminder may still be posted after its time, if the phone held the alarm back. */
    val late: Duration,
) {
    /** A nudge [Reminders.BUSY_LEAD] before one of the user's busiest hours. Too late once the hour has begun. */
    BUSY_HOUR(Reminders.BUSY_LEAD),

    /** A recovery milestone reached for the first time. Still true until the next cigarette. */
    MILESTONE(Duration.ofHours(12)),

    /** What's kept covers the savings goal, the first time. True all that day. */
    GOAL(Duration.ofHours(24)),

    /** A new plan week that lowers the allowance. Posted at midnight; true all that day. */
    PLAN_WEEK(Duration.ofHours(24)),
}

/**
 * One notification to post at [at]. The words are worked out here so they can be tested. They never
 * say "cigarette" or "smoke": a notification can show on a lock screen other people see.
 */
sealed interface Reminder {
    val kind: ReminderKind
    val at: Instant
    val title: String
    val text: String

    /** A nudge before busy [hour] (0–23), at [at] = that hour − [Reminders.BUSY_LEAD]. Opens Urge Wave. */
    data class BusyHour(override val at: Instant, val hour: Int) : Reminder {
        override val kind get() = ReminderKind.BUSY_HOUR
        override val title get() = "Your busy hour starts soon"
        override val text get() = "A craving peaks and fades in about 4 minutes. Ride it out here if one comes."
    }

    /** [milestone] reached for the first time, at [at]. Opens Air. */
    data class MilestoneReached(override val at: Instant, val milestone: Milestone) : Reminder {
        override val kind get() = ReminderKind.MILESTONE
        override val title get() = "${milestone.label} · ${milestone.text}"
        override val text get() = "The first time you've reached this one. Air shows what comes next."
    }

    /** What's kept covers the goal called [goalName], first at [at]. Opens Money. */
    data class GoalReached(override val at: Instant, val goalName: String) : Reminder {
        override val kind get() = ReminderKind.GOAL
        override val title get() = "You've kept enough for $goalName"
        override val text get() = "What you didn't spend since you started covers it. Go get it."
    }

    /** A plan week starting at [at] with [perDay] a day, down from [before]. Opens Ember. */
    data class PlanWeek(override val at: Instant, val perDay: Int, val before: Int) : Reminder {
        override val kind get() = ReminderKind.PLAN_WEEK
        override val title get() = "This week: $perDay a day"
        override val text get() = when {
            perDay == 0 -> "Your plan reaches zero. Urge Wave is here when a craving comes."
            before - perDay == 1 -> "One fewer than last week."
            else -> "${before - perDay} fewer than last week."
        }
    }
}

/**
 * When each reminder is due. Pure rules; the alarms and notifications live in `notify/`.
 *
 * - **Busy hours**: [BUSY_LEAD] before each of the user's peak hours ([Stats.peaks] of the last
 *   [Rhythm.WINDOW] days), so at most two a day. Only once those days cover [Rhythm.MIN_DAYS]. Skipped
 *   when a cigarette was logged or a craving wave started in the [BUSY_QUIET] before it.
 * - **Milestones**: each one from 12 hours on ([FIRST_MILESTONE]), the first time a gap reaches it.
 *   20 minutes comes after nearly every cigarette, so it never notifies.
 * - **Goal**: the first moment what's kept covers the savings goal ([Money.goalReachedAt]). Once it
 *   has, dipping under and back doesn't count again; a new, bigger goal does.
 * - **Plan weeks**: midnight on the first day of a plan week whose base allowance is lower than the
 *   day before. Never on Hold steady, and never again once the plan is at 0.
 */
object Reminders {
    /** How long before a busy hour its nudge comes. */
    val BUSY_LEAD: Duration = Duration.ofMinutes(15)

    /** A busy-hour nudge is skipped if something was logged or a wave started this soon before it. */
    val BUSY_QUIET: Duration = Duration.ofMinutes(30)

    /** Milestones before this one (20 minutes) don't notify. */
    const val FIRST_MILESTONE = 1

    /**
     * The first reminder of [kind] after [after], or null when none is coming (or the user hasn't
     * finished onboarding). [now] is when it's worked out: busy hours are averaged up to it. [logs]
     * are oldest first. Doesn't check whether [kind] is turned on.
     */
    fun next(kind: ReminderKind, s: UserSettings, logs: List<SmokeLog>, now: Instant, after: Instant, zone: ZoneId): Reminder? {
        if (!s.onboarded) return null
        return when (kind) {
            ReminderKind.BUSY_HOUR -> nextBusyHour(s, logs, now, after, zone)
            ReminderKind.MILESTONE -> nextMilestone(logs, after)
            ReminderKind.GOAL -> Money.goalReachedAt(s, logs, zone)?.takeIf { it.isAfter(after) }?.let { Reminder.GoalReached(it, s.goalName) }
            ReminderKind.PLAN_WEEK -> nextPlanWeek(s, after, zone)
        }
    }

    /**
     * What a run at [now] does for [kind], given the last reminder of it already [handled]: every
     * reminder that came due since (within its [ReminderKind.late], and after [handled]) is handled,
     * the ones that [shouldPost] are posted, and the next one is waited for. So a reminder the phone
     * held back still comes, and none comes twice.
     */
    fun due(kind: ReminderKind, s: UserSettings, logs: List<SmokeLog>, urges: List<Urge>, handled: Instant?, now: Instant, zone: ZoneId): ReminderRun {
        val post = mutableListOf<Reminder>()
        var last: Instant? = null
        var r = next(kind, s, logs, now, listOfNotNull(now.minus(kind.late), handled).max(), zone)
        while (r != null && !r.at.isAfter(now)) {
            if (shouldPost(r, logs, urges, now)) post += r
            last = r.at
            r = next(kind, s, logs, now, r.at, zone)
        }
        return ReminderRun(post, last, r)
    }

    /**
     * Whether [r], now due, should still be posted at [now]: not past its [ReminderKind.late], and for
     * a busy hour, nothing logged and no wave started in the [BUSY_QUIET] before.
     */
    fun shouldPost(r: Reminder, logs: List<SmokeLog>, urges: List<Urge>, now: Instant): Boolean {
        if (now.isBefore(r.at) || !now.isBefore(r.at.plus(r.kind.late))) return false
        if (r !is Reminder.BusyHour) return true
        val quietFrom = now.minus(BUSY_QUIET)
        return logs.none { it.at.isAfter(quietFrom) && !it.at.isAfter(now) } &&
            urges.none { it.startedAt.isAfter(quietFrom) && !it.startedAt.isAfter(now) }
    }

    /**
     * The user's busy hours (0–23, busiest first, at most two) from the last [Rhythm.WINDOW] days, or
     * null until those days cover [Rhythm.MIN_DAYS]. Empty when enough days are covered but nothing
     * was logged in them.
     */
    fun busyHours(s: UserSettings, logs: List<SmokeLog>, now: Instant, zone: ZoneId): List<Int>? {
        val today = now.atZone(zone).toLocalDate()
        val period = Period.lastDays(today, Rhythm.WINDOW)
        val dataStart = Stats.dataStart(s.planStart, logs, zone)
        if (Stats.coveredDays(period, now, zone, dataStart) < Rhythm.MIN_DAYS) return null
        return Stats.peaks(Stats.hourly(logs, period, now, zone, dataStart))
    }

    /** What Settings → Reminders says: the busy hours, and why any kind won't come for now. */
    fun outlook(s: UserSettings, logs: List<SmokeLog>, now: Instant, zone: ZoneId): ReminderOutlook {
        val hours = busyHours(s, logs, now, zone)
        val quiet = buildMap {
            when {
                hours == null -> put(ReminderKind.BUSY_HOUR, ReminderQuiet.NEEDS_A_WEEK)
                hours.isEmpty() -> put(ReminderKind.BUSY_HOUR, ReminderQuiet.NOTHING_LOGGED)
            }
            if (Money.goalReachedAt(s, logs, zone)?.isAfter(now) == false) put(ReminderKind.GOAL, ReminderQuiet.GOAL_REACHED)
            when {
                s.taper == TaperType.HOLD -> put(ReminderKind.PLAN_WEEK, ReminderQuiet.PLAN_HOLDS)
                PlanMath.baseAllowance(s, now.atZone(zone).toLocalDate()) == 0 -> put(ReminderKind.PLAN_WEEK, ReminderQuiet.PLAN_AT_ZERO)
            }
        }
        return ReminderOutlook(hours.orEmpty(), quiet)
    }

    private fun nextBusyHour(s: UserSettings, logs: List<SmokeLog>, now: Instant, after: Instant, zone: ZoneId): Reminder? {
        val hours = busyHours(s, logs, now, zone)?.takeIf { it.isNotEmpty() } ?: return null
        val day = after.atZone(zone).toLocalDate()
        // A nudge for midnight falls on the day before, so look a day further than tomorrow.
        return (0L..2L).flatMap { d -> hours.map { h -> Reminder.BusyHour(at(day.plusDays(d), h, zone).minus(BUSY_LEAD), h) } }
            .filter { it.at.isAfter(after) }
            .minByOrNull { it.at }
    }

    private fun nextMilestone(logs: List<SmokeLog>, after: Instant): Reminder? {
        val times = logs.map { it.at }
        val last = times.lastOrNull() ?: return null
        // Reached by a gap between two cigarettes: reached before, so not the first time.
        val reached = Recovery.lastReached(times, now = last)
        return Milestones.all.withIndex().drop(FIRST_MILESTONE)
            .filter { (i, _) -> reached[i] == null }
            .map { (_, m) -> Reminder.MilestoneReached(last.plus(m.after), m) }
            .firstOrNull { it.at.isAfter(after) }
    }

    /** A lower allowance only ever starts on a plan week's first day: the first one after [after]. */
    private fun nextPlanWeek(s: UserSettings, after: Instant, zone: ZoneId): Reminder? {
        if (s.taper == TaperType.HOLD) return null
        val o = PlanMath.outlook(s, after.atZone(zone).toLocalDate())
        if (o.baseNextWeek >= o.baseToday) return null
        return Reminder.PlanWeek(o.nextWeekStart.atStartOfDay(zone).toInstant(), o.baseNextWeek, o.baseToday)
    }

    private fun at(day: LocalDate, hour: Int, zone: ZoneId): Instant = day.atTime(hour, 0).atZone(zone).toInstant()
}

/** One [Reminders.due] run for a kind: what to [post], the newest reminder [handled] (posted or not), and the [next] to wait for. */
data class ReminderRun(val post: List<Reminder>, val handled: Instant?, val next: Reminder?)

/** Why a reminder kind won't come for now, for Settings → Reminders. */
enum class ReminderQuiet { NEEDS_A_WEEK, NOTHING_LOGGED, GOAL_REACHED, PLAN_HOLDS, PLAN_AT_ZERO }

/**
 * [Reminders.outlook]: the busy hours (empty until known) and, per kind, why none will come for now.
 * The default is before anything is worked out: no busy hours yet.
 */
data class ReminderOutlook(
    val busyHours: List<Int> = emptyList(),
    val quiet: Map<ReminderKind, ReminderQuiet> = mapOf(ReminderKind.BUSY_HOUR to ReminderQuiet.NEEDS_A_WEEK),
)
