package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max

/** Derived numbers shown across screens. All pure functions. */
object Stats {

    /**
     * Heat of the "two temperatures" system: 1.0 = just smoked (ember), 0.0 = interval fully
     * earned (oxygen). Null last log means the user has never logged: fully cool.
     */
    fun heat(now: Instant, lastLog: Instant?, intervalMinutes: Int): Float {
        if (lastLog == null) return 0f
        val mins = Duration.between(lastLog, now).toMillis() / 60_000f
        val frac = mins / intervalMinutes.coerceAtLeast(1)
        return (1f - frac).coerceIn(0f, 1f)
    }

    fun countOn(logs: List<SmokeLog>, day: java.time.LocalDate, zone: ZoneId): Int =
        logs.count { it.at.atZone(zone).toLocalDate() == day }

    /** Cigarettes not smoked vs. the user's own baseline since the plan started. */
    fun notSmoked(baselinePerDay: Int, since: Instant, now: Instant, smokedSince: Int): Int {
        val days = Duration.between(since, now).toMillis() / 86_400_000.0
        if (days <= 0) return 0
        return max(0, (baselinePerDay * days).toInt() - smokedSince)
    }

    fun moneySavedMinor(notSmoked: Int, pricePerCigMinor: Double): Long =
        (notSmoked * pricePerCigMinor).toLong()

    /**
     * Average cigarettes per hour-of-day over [period], size 24. Each hour is averaged over the
     * days on which that hour has started by [now] and that are on or after [dataStart] (when the
     * user started tracking), so a new user's first days and the unfinished part of today don't
     * dilute the average. A one-day period gives that day's counts.
     */
    fun hourly(
        logs: List<SmokeLog>,
        period: Period,
        now: Instant,
        zone: ZoneId,
        dataStart: LocalDate?,
    ): FloatArray {
        val out = FloatArray(24)
        if (dataStart == null) return out
        val from = maxOf(period.first, dataStart)
        val days = IntArray(24)
        var d = from
        while (!d.isAfter(period.last)) {
            if (period.filter.accepts(d.dayOfWeek)) {
                for (h in 0 until 24) if (!d.atTime(h, 0).atZone(zone).toInstant().isAfter(now)) days[h]++
            }
            d = d.plusDays(1)
        }
        logs.forEach {
            if (!it.at.isAfter(now) && period.contains(it.at, zone) && !it.at.atZone(zone).toLocalDate().isBefore(from)) {
                out[it.at.atZone(zone).hour] += 1f
            }
        }
        for (i in out.indices) out[i] = if (days[i] == 0) 0f else out[i] / days[i]
        return out
    }

    /** When tracking began: the plan start, or the first log if that is earlier. Null with neither. */
    fun dataStart(planStart: LocalDate?, logs: List<SmokeLog>, zone: ZoneId): LocalDate? {
        val first = logs.minOfOrNull { it.at }?.atZone(zone)?.toLocalDate()
        return listOfNotNull(planStart, first).minOrNull()
    }

    /** Trigger counts plus how many logs were tagged at all, so a chart can say how much it covers. */
    fun triggers(logs: List<SmokeLog>): TriggerSummary =
        TriggerSummary(triggerCounts(logs, Instant.MIN), logs.count { it.trigger != null }, logs.size)

    /**
     * The most common trigger, only when at least [MIN_TAGGED] logs are tagged. [TopTrigger.mostly]
     * when it is more than half of them.
     */
    fun topTrigger(logs: List<SmokeLog>): TopTrigger? {
        val s = triggers(logs)
        if (s.tagged < MIN_TAGGED) return null
        val (t, n) = s.counts.first()
        return TopTrigger(t, n, s.tagged)
    }

    const val MIN_TAGGED = 3

    fun triggerCounts(logs: List<SmokeLog>, since: Instant): List<Pair<Trigger, Int>> =
        logs.filter { it.at >= since && it.trigger != null }
            .groupingBy { it.trigger!! }.eachCount()
            .toList().sortedByDescending { it.second }

    /**
     * Longest gap between consecutive logs, or last log → now, within the window.
     * The gap before the first log only counts when an earlier log exists to anchor it.
     */
    fun longestGap(logs: List<SmokeLog>, now: Instant, window: Duration): Duration {
        val since = now.minus(window)
        val inWindow = logs.map { it.at }.filter { it >= since }.sorted()
        val before = logs.map { it.at }.filter { it < since }.maxOrNull()
        val points = listOfNotNull(before) + inWindow + now
        var best = Duration.ZERO
        for (i in 1 until points.size) {
            val g = Duration.between(maxOf(points[i - 1], since), points[i])
            if (g > best) best = g
        }
        return best
    }
}

enum class DayFilter(val label: String) {
    ALL("All days"), WEEKDAYS("Weekdays"), WEEKENDS("Weekends");

    fun accepts(d: DayOfWeek): Boolean = when (this) {
        ALL -> true
        WEEKDAYS -> d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY
        WEEKENDS -> d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY
    }
}

/** Calendar days [first]..[last] (inclusive), keeping only the days [filter] accepts. */
data class Period(val first: LocalDate, val last: LocalDate, val filter: DayFilter = DayFilter.ALL) {
    fun contains(at: Instant, zone: ZoneId): Boolean {
        val d = at.atZone(zone).toLocalDate()
        return !d.isBefore(first) && !d.isAfter(last) && filter.accepts(d.dayOfWeek)
    }

    companion object {
        /** [days] calendar days ending [offsetDays] days before [today] (today included when 0). */
        fun lastDays(today: LocalDate, days: Int, offsetDays: Int = 0, filter: DayFilter = DayFilter.ALL): Period {
            val last = today.minusDays(offsetDays.toLong())
            return Period(last.minusDays((days - 1).toLong()), last, filter)
        }

        fun day(d: LocalDate) = Period(d, d)
    }
}

data class TriggerSummary(val counts: List<Pair<Trigger, Int>>, val tagged: Int, val total: Int)

data class TopTrigger(val trigger: Trigger, val count: Int, val tagged: Int) {
    val mostly: Boolean get() = count * 2 > tagged
}
