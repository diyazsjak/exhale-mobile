package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A stretch without a cigarette, from [start] to [end]. [ongoing] when it is the one still running (it ends now). */
data class Gap(val start: Instant, val end: Instant, val ongoing: Boolean) {
    val length: Duration get() = Duration.between(start, end)
}

/**
 * Today's gaps for the Air chart. [gaps] are the ones that end today or are still running, oldest
 * first; the first may have started yesterday (a night). [logs] are today's cigarettes.
 */
data class DayAir(
    val dayStart: Instant,
    val dayEnd: Instant,
    val gaps: List<Gap>,
    val logs: List<Instant>,
) {
    /** Gaps today that lasted at least [d], the running one included. */
    fun atLeast(d: Duration): Int = gaps.count { it.length >= d }

    val longest: Gap? get() = gaps.maxByOrNull { it.length }
}

/** Everything the Air screen shows beyond the time since the last cigarette. */
data class RecoverySummary(
    /** The milestone the current gap is heading for; null with no logs or once all are reached. */
    val next: Milestone?,
    /** How many milestones the current gap has reached. */
    val reached: Int,
    /**
     * Per milestone (same order as [Milestones.all]): the most recent moment it was reached, in
     * this gap or an earlier one. Null if no gap has reached it yet. Once reached it never goes back
     * to null, whatever happens next.
     */
    val lastReached: List<Instant?>,
    val today: DayAir,
    /** Cigarettes fewer than the baseline in [fewerFrom]..now (the last 7 days, or since the plan start if newer). Null under a day in. */
    val fewer: Int?,
    /** True when [fewer] counts from the plan start rather than 7 days back. */
    val fewerSinceStart: Boolean,
)

/**
 * Recovery after each cigarette, built so that nothing resets: a gap counts from its last
 * cigarette, and once any gap has reached a milestone that is remembered.
 */
object Recovery {

    fun summary(s: UserSettings, logs: List<SmokeLog>, now: Instant, zone: ZoneId): RecoverySummary {
        val times = logs.map { it.at }.filter { !it.isAfter(now) }.sorted()
        val since = times.lastOrNull()?.let { Duration.between(it, now) }
        val start = s.planStart.atStartOfDay(zone).toInstant()
        val weekAgo = now.minus(Duration.ofDays(7))
        val from = maxOf(start, weekAgo)
        val fewer = if (Duration.between(from, now) >= Duration.ofDays(1)) Money.notSmokedBetween(s.baselinePerDay, logs, from, now) else null
        return RecoverySummary(
            next = Milestones.next(since),
            reached = Milestones.reached(since),
            lastReached = lastReached(times, now),
            today = day(times, now, zone),
            fewer = fewer,
            fewerSinceStart = from != weekAgo,
        )
    }

    /**
     * Per milestone, the most recent moment a gap reached it: a gap from one cigarette to the next
     * (or to [now] for the last) reaches a milestone at its start + the milestone's time. The stretch
     * before the first cigarette has no start, so it counts for nothing.
     */
    fun lastReached(sortedLogs: List<Instant>, now: Instant): List<Instant?> {
        val out = MutableList<Instant?>(Milestones.all.size) { null }
        for (i in sortedLogs.indices) {
            val a = sortedLogs[i]
            val b = sortedLogs.getOrNull(i + 1) ?: now
            val gap = Duration.between(a, b)
            Milestones.all.forEachIndexed { k, m -> if (gap >= m.after) out[k] = a.plus(m.after) }
        }
        return out
    }

    /**
     * Today's gaps: between today's cigarettes, the running one up to [now], and the one that ended
     * with today's first cigarette (a night) when an earlier cigarette starts it. With no cigarette
     * today, the running gap is the only one.
     */
    fun day(sortedLogs: List<Instant>, now: Instant, zone: ZoneId): DayAir {
        val date = now.atZone(zone).toLocalDate()
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val today = sortedLogs.filter { it >= dayStart && !it.isAfter(now) }
        val before = sortedLogs.lastOrNull { it < dayStart }
        val starts = listOfNotNull(before) + today
        val gaps = starts.mapIndexed { i, a ->
            val b = starts.getOrNull(i + 1)
            Gap(a, b ?: now, ongoing = b == null)
        }
        return DayAir(dayStart, dayEnd, gaps, today)
    }
}
