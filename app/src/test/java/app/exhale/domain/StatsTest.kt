package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class StatsTest {
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private fun log(minAgo: Long, t: Trigger? = null) = SmokeLog(minAgo, now.minusSeconds(minAgo * 60), t, false)

    @Test fun heat_isHotRightAfterAndCoolAfterInterval() {
        assertEquals(1f, Stats.heat(now, now, 60), 0.001f)
        assertEquals(0.5f, Stats.heat(now, now.minusSeconds(30 * 60), 60), 0.001f)
        assertEquals(0f, Stats.heat(now, now.minusSeconds(120 * 60), 60), 0.001f)
        assertEquals(0f, Stats.heat(now, null, 60), 0.001f)
    }

    @Test fun countOn_countsOneCalendarDay() {
        val logs = listOf(log(60), log(11 * 60), log(13 * 60)) // 11:00 and 01:00 today, 23:00 yesterday
        assertEquals(2, Stats.countOn(logs, LocalDate.parse("2026-03-10"), ZoneOffset.UTC))
        assertEquals(1, Stats.countOn(logs, LocalDate.parse("2026-03-09"), ZoneOffset.UTC))
        // The same 23:00 UTC log is on the 10th two hours east.
        assertEquals(3, Stats.countOn(logs, LocalDate.parse("2026-03-10"), ZoneOffset.ofHours(2)))
    }

    private val today: LocalDate = LocalDate.parse("2026-03-10") // a Tuesday; now is 12:00 UTC
    private fun at(date: String, h: Int, t: Trigger? = null, id: Long = 0) =
        SmokeLog(id, LocalDate.parse(date).atTime(h, 30).toInstant(ZoneOffset.UTC), t, false)

    @Test fun hourly_averagesEachHourOverTheDaysItHasStarted() {
        // 11:xx today and yesterday; 15:xx yesterday only (today's 15:00 hasn't happened yet).
        val logs = listOf(at("2026-03-10", 11), at("2026-03-09", 11), at("2026-03-09", 11), at("2026-03-09", 15))
        val h = Stats.hourly(logs, Period.lastDays(today, 2), now, ZoneOffset.UTC, LocalDate.parse("2026-01-01"))
        assertEquals(1.5f, h[11], 0.001f)
        assertEquals(1f, h[15], 0.001f)
        assertEquals(2.5f, h.sum(), 0.001f)
    }

    @Test fun hourly_thirtyDaysIsThirtyDaysNotThirtyOne() {
        // 12 a day at 08:xx on each of the last 30 days, today included.
        val logs = (0L until 30L).flatMap { d -> List(12) { at(today.minusDays(d).toString(), 8) } }
        val h = Stats.hourly(logs, Period.lastDays(today, 30), now, ZoneOffset.UTC, LocalDate.parse("2026-01-01"))
        assertEquals(12f, h.sum(), 0.001f)
    }

    @Test fun hourly_newUserIsAveragedFromTheirStartOnly() {
        // Started 5 days ago (today included), 12 a day at 09:xx.
        val start = today.minusDays(4)
        val logs = (0L until 5L).flatMap { d -> List(12) { at(today.minusDays(d).toString(), 9) } }
        val h = Stats.hourly(logs, Period.lastDays(today, 30), now, ZoneOffset.UTC, start)
        assertEquals(12f, h.sum(), 0.001f)
    }

    @Test fun hourly_beforeTrackingStartedIsEmpty() {
        val h = Stats.hourly(emptyList(), Period.lastDays(today, 30, offsetDays = 30), now, ZoneOffset.UTC, today.minusDays(4))
        assertEquals(0f, h.sum(), 0.001f)
        assertEquals(0f, Stats.hourly(emptyList(), Period.lastDays(today, 30), now, ZoneOffset.UTC, null).sum(), 0.001f)
    }

    @Test fun hourly_ignoresLogsAfterNow() {
        val logs = listOf(at("2026-03-10", 11), at("2026-03-10", 18))
        val h = Stats.hourly(logs, Period.day(today), now, ZoneOffset.UTC, today)
        assertEquals(1f, h[11], 0.001f)
        assertEquals(0f, h[18], 0.001f)
    }

    @Test fun hourly_dayFilterAndSingleDay() {
        // Sat 7th and Sun 8th: 2 each at 10:xx. Mon 9th: 4 at 10:xx.
        val logs = List(2) { at("2026-03-07", 10) } + List(2) { at("2026-03-08", 10) } + List(4) { at("2026-03-09", 10) }
        val start = LocalDate.parse("2026-03-07")
        val weekends = Stats.hourly(logs, Period.lastDays(today, 30, filter = DayFilter.WEEKENDS), now, ZoneOffset.UTC, start)
        assertEquals(2f, weekends[10], 0.001f)
        val weekdays = Stats.hourly(logs, Period.lastDays(today, 30, filter = DayFilter.WEEKDAYS), now, ZoneOffset.UTC, start)
        assertEquals(2f, weekdays[10], 0.001f) // 4 on Monday over Mon + Tue (Tuesday 10:00 has started)
        val monday = Stats.hourly(logs, Period.day(LocalDate.parse("2026-03-09")), now, ZoneOffset.UTC, start)
        assertEquals(4f, monday[10], 0.001f)
    }

    @Test fun dataStart_isTheEarlierOfPlanStartAndFirstLog() {
        val logs = listOf(at("2026-03-01", 9))
        assertEquals(LocalDate.parse("2026-03-01"), Stats.dataStart(LocalDate.parse("2026-03-05"), logs, ZoneOffset.UTC))
        assertEquals(LocalDate.parse("2026-02-20"), Stats.dataStart(LocalDate.parse("2026-02-20"), logs, ZoneOffset.UTC))
        assertEquals(null, Stats.dataStart(null, emptyList(), ZoneOffset.UTC))
    }

    @Test fun triggers_reportCoverage() {
        val logs = listOf(log(1, Trigger.COFFEE), log(2, Trigger.COFFEE), log(3), log(4))
        val s = Stats.triggers(logs)
        assertEquals(2, s.tagged)
        assertEquals(4, s.total)
        assertEquals(listOf(Trigger.COFFEE to 2), s.counts)
    }

    @Test fun topTrigger_needsEnoughTaggedLogs() {
        assertNull(Stats.topTrigger(listOf(log(1, Trigger.COFFEE), log(2, Trigger.COFFEE), log(3), log(4))))
        val top = Stats.topTrigger(listOf(log(1, Trigger.COFFEE), log(2, Trigger.COFFEE), log(3, Trigger.STRESS)))!!
        assertEquals(Trigger.COFFEE, top.trigger)
        assertTrue(top.mostly)
        val split = Stats.topTrigger(listOf(log(1, Trigger.COFFEE), log(2, Trigger.STRESS), log(3, Trigger.DRINKS)))!!
        assertFalse(split.mostly)
    }

    @Test fun coveredDays_countsFilteredStartedDaysSinceTrackingBegan() {
        // Last 30 days with tracking from 5 days ago: 5 days, today included.
        assertEquals(5, Stats.coveredDays(Period.lastDays(today, 30), now, ZoneOffset.UTC, today.minusDays(4)))
        // Weekends only in the same span (Sat 7th, Sun 8th).
        assertEquals(2, Stats.coveredDays(Period.lastDays(today, 30, filter = DayFilter.WEEKENDS), now, ZoneOffset.UTC, today.minusDays(4)))
        // The 30 days before that: nothing tracked yet.
        assertEquals(0, Stats.coveredDays(Period.lastDays(today, 30, offsetDays = 30), now, ZoneOffset.UTC, today.minusDays(4)))
        assertEquals(30, Stats.coveredDays(Period.lastDays(today, 30), now, ZoneOffset.UTC, LocalDate.parse("2025-01-01")))
        assertEquals(0, Stats.coveredDays(Period.lastDays(today, 30), now, ZoneOffset.UTC, null))
        // Days still to come don't count.
        assertEquals(1, Stats.coveredDays(Period(today, today.plusDays(5)), now, ZoneOffset.UTC, today))
    }

    @Test fun peaks_busiestThenASecondFarEnoughAndBusyEnough() {
        val h = FloatArray(24)
        h[8] = 1.6f; h[9] = 1.5f; h[13] = 1.2f; h[19] = 1.1f
        assertEquals(listOf(8, 13), Stats.peaks(h)) // 9 is too close, 13 is 75% as busy
        h[13] = 1.0f
        assertEquals(listOf(8), Stats.peaks(h)) // 13 (62%) and 19 (69%) are not busy enough
        h[19] = 1.2f
        assertEquals(listOf(8, 19), Stats.peaks(h))
    }

    @Test fun peaks_wrapAroundMidnightAndEmpty() {
        val h = FloatArray(24)
        h[23] = 2f; h[1] = 1.9f
        assertEquals(listOf(23), Stats.peaks(h)) // 1 am is only 2 hours from 11 pm
        assertEquals(emptyList<Int>(), Stats.peaks(FloatArray(24)))
    }

    @Test fun longestGapBetween_clipsToTheWindow() {
        val from = now.minus(Duration.ofHours(10))
        val to = now
        // Anchor 12h ago, logs 6h and 1h ago: stretches 4h (clipped from 10h), 5h, 1h.
        val logs = listOf(log(12 * 60), log(6 * 60), log(60))
        assertEquals(Duration.ofHours(5), Stats.longestGapBetween(logs, from, to))
        assertEquals(Duration.ZERO, Stats.longestGapBetween(emptyList(), from, to))
        // Nothing inside but an earlier log: the whole window.
        assertEquals(Duration.ofHours(10), Stats.longestGapBetween(listOf(log(12 * 60)), from, to))
    }

    @Test fun period_containsOnlyItsFilteredDays() {
        val p = Period.lastDays(today, 7, filter = DayFilter.WEEKDAYS)
        assertTrue(p.contains(at("2026-03-09", 10).at, ZoneOffset.UTC))
        assertFalse(p.contains(at("2026-03-08", 10).at, ZoneOffset.UTC)) // Sunday
        assertFalse(p.contains(at("2026-03-03", 10).at, ZoneOffset.UTC)) // 8 days back
    }

    @Test fun longestGapBetween_includesTimeSinceLast() {
        val logs = listOf(log(300), log(240), log(30))
        assertEquals(Duration.ofMinutes(210), Stats.longestGapBetween(logs, now.minus(Duration.ofDays(1)), now))
    }

    @Test fun triggers_sortedDescending() {
        val logs = listOf(log(1, Trigger.STRESS), log(2, Trigger.COFFEE), log(3, Trigger.COFFEE), log(4))
        assertEquals(listOf(Trigger.COFFEE to 2, Trigger.STRESS to 1), Stats.triggers(logs).counts)
    }

    @Test fun dayFilter_splitsWeekdaysAndWeekends() {
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        DayOfWeek.entries.forEach { d ->
            assertTrue(DayFilter.ALL.accepts(d))
            assertEquals(d in weekend, DayFilter.WEEKENDS.accepts(d))
            assertEquals(d !in weekend, DayFilter.WEEKDAYS.accepts(d))
        }
    }
}
