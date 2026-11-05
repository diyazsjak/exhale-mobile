package app.exhale.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Derived numbers shown across screens. All pure functions. */
object Stats {

    private const val PEAK_APART = 3
    private const val PEAK_SHARE = 0.7f
    private const val MIN_TAGGED = 3

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

    /** Cigarettes logged on calendar day [day]. */
    fun countOn(logs: List<SmokeLog>, day: LocalDate, zone: ZoneId): Int =
        logs.count { it.at.atZone(zone).toLocalDate() == day }

    /** When tracking began: the plan start, or the first log if that is earlier. Null with neither. */
    fun dataStart(planStart: LocalDate?, logs: List<SmokeLog>, zone: ZoneId): LocalDate? {
        val first = logs.minOfOrNull { it.at }?.atZone(zone)?.toLocalDate()
        return listOfNotNull(planStart, first).minOrNull()
    }

    /**
     * Average cigarettes per hour-of-day over [period], size 24. Each hour is averaged over the
     * days on which that hour has started by [now] and that are on or after [dataStart] (when the
     * user started tracking), so a new user's first days and the unfinished part of today don't
     * dilute the average. A one-day period gives that day's counts.
     */
    fun hourly(logs: List<SmokeLog>, period: Period, now: Instant, zone: ZoneId, dataStart: LocalDate?): FloatArray {
        val out = FloatArray(24)
        if (dataStart == null) return out
        val days = IntArray(24)
        for (d in trackedDays(period, dataStart)) {
            for (h in 0 until 24) if (!d.atTime(h, 0).atZone(zone).toInstant().isAfter(now)) days[h]++
        }
        for (log in logs) {
            if (log.at.isAfter(now)) continue
            val local = log.at.atZone(zone)
            if (isTracked(local.toLocalDate(), period, dataStart)) out[local.hour] += 1f
        }
        for (h in out.indices) out[h] = if (days[h] == 0) 0f else out[h] / days[h]
        return out
    }

    /**
     * How many days of [period] count toward its averages: accepted by its filter, on or after
     * [dataStart], and already begun by [now].
     */
    fun coveredDays(period: Period, now: Instant, zone: ZoneId, dataStart: LocalDate?): Int {
        if (dataStart == null) return 0
        val today = now.atZone(zone).toLocalDate()
        return trackedDays(period, dataStart).takeWhile { !it.isAfter(today) }.count()
    }

    /** A day counts toward [period]'s averages when the period holds it and tracking had begun. */
    private fun isTracked(date: LocalDate, period: Period, dataStart: LocalDate): Boolean =
        !date.isBefore(dataStart) && period.contains(date)

    /** The days of [period] that count toward its averages, in order. */
    private fun trackedDays(period: Period, dataStart: LocalDate): Sequence<LocalDate> =
        generateSequence(maxOf(period.first, dataStart)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(period.last) }
            .filter { isTracked(it, period, dataStart) }

    /**
     * Up to two peak hours, busiest first: the busiest hour, plus the next busiest one that is at
     * least [PEAK_APART] hours away (round the clock) and at least [PEAK_SHARE] as busy. Empty when
     * nothing was smoked.
     */
    fun peaks(hours: FloatArray): List<Int> {
        val order = hours.indices.sortedWith(compareByDescending<Int> { hours[it] }.thenBy { it })
        val top = order.firstOrNull()?.takeIf { hours[it] > 0f } ?: return emptyList()
        val second = order.drop(1).firstOrNull { h ->
            val apart = minOf((h - top + 24) % 24, (top - h + 24) % 24)
            apart >= PEAK_APART && hours[h] >= hours[top] * PEAK_SHARE
        }
        return listOfNotNull(top, second)
    }

    /** Trigger counts, most common first, plus how many logs were tagged at all, so a chart can say how much it covers. */
    fun triggers(logs: List<SmokeLog>): TriggerSummary {
        val counts = logs.mapNotNull { it.trigger }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
        return TriggerSummary(counts, tagged = counts.sumOf { it.second }, total = logs.size)
    }

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

    /**
     * Longest stretch without a log inside [from, to): between consecutive logs, and from the last
     * log to [to]. The stretch before the first log only counts when an earlier log anchors it, and
     * it is clipped at [from].
     */
    fun longestGapBetween(logs: List<SmokeLog>, from: Instant, to: Instant): Duration {
        val times = logs.map { it.at }
        val inWindow = times.filter { it >= from && it < to }.sorted()
        val before = times.filter { it < from }.maxOrNull()
        if (inWindow.isEmpty() && before == null) return Duration.ZERO
        val points = listOfNotNull(before) + inWindow + to
        return points.zipWithNext { a, b -> Duration.between(maxOf(a, from), b) }.max().coerceAtLeast(Duration.ZERO)
    }
}

enum class DayFilter(val label: String) {
    ALL("All days"), WEEKDAYS("Weekdays"), WEEKENDS("Weekends");

    fun accepts(d: DayOfWeek): Boolean {
        val weekend = d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY
        return when (this) {
            ALL -> true
            WEEKDAYS -> !weekend
            WEEKENDS -> weekend
        }
    }
}

/** Calendar days [first]..[last] (inclusive), keeping only the days [filter] accepts. */
data class Period(val first: LocalDate, val last: LocalDate, val filter: DayFilter = DayFilter.ALL) {
    fun contains(date: LocalDate): Boolean =
        !date.isBefore(first) && !date.isAfter(last) && filter.accepts(date.dayOfWeek)

    fun contains(at: Instant, zone: ZoneId): Boolean = contains(at.atZone(zone).toLocalDate())

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
