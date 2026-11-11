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

    /**
     * Cigarettes not smoked in [from, to) against [baseline] a day, counting fractional days and
     * floored at 0.
     */
    fun notSmokedBetween(baseline: Int, logs: List<SmokeLog>, from: Instant, to: Instant): Int {
        val days = daysBetween(from, to)
        if (days <= 0) return 0
        val smoked = logs.count { it.at >= from && it.at < to }
        return ((baseline * days).toInt() - smoked).coerceAtLeast(0)
    }

    fun summary(s: UserSettings, logs: List<SmokeLog>, now: Instant, zone: ZoneId): MoneySummary {
        val start = s.planStart.atStartOfDay(zone).toInstant()
        fun notSmokedSince(from: Instant) = notSmokedBetween(s.baselinePerDay, logs, from, now)
        fun minor(notSmoked: Int) = worth(notSmoked.toLong(), s.pricePerCigMinor)
        val days = daysBetween(start, now)
        val notSmoked = notSmokedSince(start)
        val kept = minor(notSmoked)
        fun window(d: Long): Long? = if (days > d) minor(notSmokedSince(now.minus(Duration.ofDays(d)))) else null
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

    /**
     * The first moment what's kept since the plan start covers the goal ([MoneySummary.keptMinor] ≥
     * [UserSettings.goalMinor]), counting [logs] as they are and none after the last one. In the past
     * once reached, even if it has dipped under since; null with no goal, no price or a baseline of 0.
     * Between two logs, what's kept grows by one cigarette every 1/baseline of a day.
     */
    fun goalReachedAt(s: UserSettings, logs: List<SmokeLog>, zone: ZoneId): Instant? {
        val price = s.pricePerCigMinor
        if (s.goalMinor <= 0 || price <= 0.0 || s.baselinePerDay <= 0) return null
        // The fewest cigarettes not smoked whose price covers the goal, rounded as the summary rounds.
        var need = ceil(s.goalMinor / price).toLong()
        while (need > 0 && worth(need - 1, price) >= s.goalMinor) need--
        while (worth(need, price) < s.goalMinor) need++
        val start = s.planStart.atStartOfDay(zone).toInstant()
        val smoked = logs.map { it.at }.filter { it >= start }.sorted()
        // With j smoked so far, `need` not smoked is reached at start + (need + j) / baseline days. The
        // first j whose moment comes before the (j + 1)th cigarette is the answer.
        for (j in 0..smoked.size) {
            // A millisecond over, so rounding can't leave it a hair short of a whole cigarette.
            val at = start.plusMillis(ceil((need + j) * DAY_MS / s.baselinePerDay).toLong() + 1)
            if (j == smoked.size || !at.isAfter(smoked[j])) return at
        }
        return null
    }

    /**
     * Money kept over the plan's first [days] if exactly the allowance is smoked each day: the
     * estimate Onboarding shows before anything is logged.
     */
    fun keptFollowingPlan(s: UserSettings, days: Int): Long =
        (0 until days).sumOf { d ->
            (s.baselinePerDay - PlanMath.baseAllowance(s, s.planStart.plusDays(d.toLong()))).coerceAtLeast(0) * s.pricePerCigMinor
        }.toLong()

    private const val DAY_MS = 86_400_000.0

    /** What [notSmoked] cigarettes at [pricePerCig] come to, in minor units, rounded down. */
    private fun worth(notSmoked: Long, pricePerCig: Double): Long = (notSmoked * pricePerCig).toLong()

    private fun daysBetween(from: Instant, to: Instant): Double = Duration.between(from, to).toMillis() / DAY_MS
}
