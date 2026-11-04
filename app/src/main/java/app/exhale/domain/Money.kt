package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/**
 * Everything the Money screen shows, from one set of rules. Money kept over a stretch of time is
 * (baseline a day × days in it − cigarettes smoked in it) × price of one, floored at 0, counting
 * only time since the plan started.
 */
data class MoneySummary(
    /** Cigarettes not smoked since the plan started. */
    val notSmoked: Int,
    val keptMinor: Long,
    /** Kept in the last 7 / 30 days; null until the plan is older than that (it would equal the total). */
    val last7Minor: Long?,
    val last30Minor: Long?,
    /** The pace: kept in the last 7 days, or the average week so far before that. 0 under a day in. */
    val weeklyMinor: Long,
    /** True when [weeklyMinor] is the last 7 days rather than an average of a shorter start. */
    val paceFromLastWeek: Boolean,
    val perPack: Int,
    /** Whole packs not bought, and how far into the next one. */
    val packs: Int,
    val packRemainder: Int,
    val goalLeftMinor: Long,
    val goalProgress: Float,
    /** Weeks to the goal at [weeklyMinor]; 0 once reached, null when the pace is 0. */
    val weeksToGoal: Int?,
) {
    /** What [days] more at the current pace adds. */
    fun projectedMinor(days: Int): Long = weeklyMinor * days / 7
}

object Money {

    /** Cigarettes not smoked in [from, to) against [baseline] a day. */
    fun notSmokedBetween(baseline: Int, logs: List<SmokeLog>, from: Instant, to: Instant): Int =
        Stats.notSmoked(baseline, from, to, logs.count { it.at >= from && it.at < to })

    fun summary(s: UserSettings, logs: List<SmokeLog>, now: Instant, zone: ZoneId): MoneySummary {
        val start = s.planStart.atStartOfDay(zone).toInstant()
        val price = s.pricePerCigMinor
        fun minor(n: Int) = Stats.moneySavedMinor(n, price)
        val days = Duration.between(start, now).toMillis() / 86_400_000.0
        val notSmoked = notSmokedBetween(s.baselinePerDay, logs, start, now)
        val kept = minor(notSmoked)
        fun window(d: Long): Long? =
            if (days > d) minor(notSmokedBetween(s.baselinePerDay, logs, now.minus(Duration.ofDays(d)), now)) else null
        val last7 = window(7)
        val last30 = window(30)
        val weekly = last7 ?: if (days >= 1) (kept * 7 / days).toLong() else 0L
        val perPack = s.cigsPerPack.coerceAtLeast(1)
        val left = (s.goalMinor - kept).coerceAtLeast(0)
        return MoneySummary(
            notSmoked = notSmoked,
            keptMinor = kept,
            last7Minor = last7,
            last30Minor = last30,
            weeklyMinor = weekly,
            paceFromLastWeek = last7 != null,
            perPack = perPack,
            packs = notSmoked / perPack,
            packRemainder = notSmoked % perPack,
            goalLeftMinor = left,
            goalProgress = if (s.goalMinor > 0) (kept.toFloat() / s.goalMinor).coerceIn(0f, 1f) else 0f,
            weeksToGoal = when {
                left == 0L -> 0
                weekly > 0 -> ceil(left.toDouble() / weekly).toInt()
                else -> null
            },
        )
    }

    /** Longest goal name the app accepts. */
    const val GOAL_NAME_MAX = 40
}
