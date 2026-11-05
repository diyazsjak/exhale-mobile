package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Where the user stands today against the plan: the count, the allowance and when the next one is earned. */
data class TodayPlan(
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
