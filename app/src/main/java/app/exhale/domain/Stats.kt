package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.DayOfWeek
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

    /** Average cigarettes per hour-of-day over [days] days ending at [now]. Size 24. */
    fun hourly(
        logs: List<SmokeLog>,
        now: Instant,
        zone: ZoneId,
        days: Int = 30,
        offsetDays: Int = 0,
        filter: DayFilter = DayFilter.ALL,
    ): FloatArray {
        val end = now.minus(Duration.ofDays(offsetDays.toLong()))
        val start = end.minus(Duration.ofDays(days.toLong()))
        val out = FloatArray(24)
        var dayCount = 0
        var d = start.atZone(zone).toLocalDate()
        val last = end.atZone(zone).toLocalDate()
        while (!d.isAfter(last)) { if (filter.accepts(d.dayOfWeek)) dayCount++; d = d.plusDays(1) }
        logs.filter { it.at >= start && it.at < end }.forEach {
            val z = it.at.atZone(zone)
            if (filter.accepts(z.dayOfWeek)) out[z.hour] += 1f
        }
        val div = dayCount.coerceAtLeast(1)
        for (i in out.indices) out[i] = out[i] / div
        return out
    }

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
