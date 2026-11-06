package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Where the user stands today against the plan: the count, the allowance and when the next one is earned. */
data class TodayPlan(
    /** The settings and moment this plan was worked out for. */
    val settings: UserSettings,
    val now: Instant,
    val date: LocalDate,
    val count: Int,
    val allowance: Int,
    val intervalMinutes: Int,
    /** Cigarettes borrowed by yesterday's overage; today's allowance is already lowered by this. */
    val borrowed: Int,
    val last: SmokeLog?,
    val sinceLast: Duration?,
    val nextAllowedAt: Instant?,
    val heat: Float,
) {
    /** Time left until the next one is earned; null once it is (or before any log). */
    val remainingToNext: Duration?
        get() = nextAllowedAt?.let { Duration.between(now, it) }?.takeIf { !it.isNegative && !it.isZero }

    /** The next one is earned: something is logged and its interval has passed. */
    val earned: Boolean get() = last != null && remainingToNext == null

    /** How much of the gap to the next one is earned, 0..1 (Ember's countdown ring); full before any log. */
    val earnedFraction: Float
        get() = sinceLast?.let { (it.toMillis().toFloat() / Duration.ofMinutes(intervalMinutes.toLong()).toMillis()).coerceIn(0f, 1f) } ?: 1f

    /** Today's allowance is used up, so the next log goes over. */
    val overAllowance: Boolean get() = count >= allowance

    /** Cigarettes over today's allowance; 0 within it. On a lowered day not all of them are borrowed: see [borrowingFromTomorrow]. */
    val overToday: Int get() = (count - allowance).coerceAtLeast(0)

    /** Cigarettes left within today's allowance. */
    val leftToday: Int get() = (allowance - count).coerceAtLeast(0)

    /** Cigarettes past today's base allowance, which lower tomorrow (§4 Debt: measured against the base, never carried over). */
    val borrowingFromTomorrow: Int get() = PlanMath.debt(settings, date, count)

    /** Tomorrow's allowance if nothing more is logged today. */
    val tomorrowAllowance: Int get() = PlanMath.allowance(settings, date.plusDays(1), count)

    /** Today's allowance was lowered by yesterday and the Today popover hasn't been opened since. */
    val borrowNoticeDue: Boolean get() = borrowed > 0 && !settings.borrowNoticeSeenOn(date)

    /** The plan's base allowances today, tomorrow and next week (the Today popover). */
    val outlook: PlanOutlook get() = PlanMath.outlook(settings, date)

    companion object {
        /** [logs] must be oldest first. */
        fun of(s: UserSettings, logs: List<SmokeLog>, now: Instant, zone: ZoneId): TodayPlan {
            val today = now.atZone(zone).toLocalDate()
            val yesterday = today.minusDays(1)
            val yesterdayCount = Stats.countOn(logs, yesterday, zone)
            val allowance = PlanMath.allowance(s, today, yesterdayCount)
            val interval = PlanMath.intervalMinutes(allowance, s.wakingMinutes)
            val last = logs.lastOrNull()
            return TodayPlan(
                settings = s,
                now = now,
                date = today,
                count = Stats.countOn(logs, today, zone),
                allowance = allowance,
                intervalMinutes = interval,
                borrowed = PlanMath.debt(s, yesterday, yesterdayCount),
                last = last,
                sinceLast = last?.let { Duration.between(it.at, now) },
                nextAllowedAt = last?.at?.plus(Duration.ofMinutes(interval.toLong())),
                heat = Stats.heat(now, last?.at, interval),
            )
        }
    }
}
