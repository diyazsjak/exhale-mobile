package app.exhale.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RhythmTest {
    private val zone = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.parse("2026-03-10")
    private val now: Instant = Instant.parse("2026-03-10T12:00:00Z")

    private fun log(date: LocalDate, h: Int, t: Trigger? = null, id: Long = 0) =
        SmokeLog(id, date.atTime(h, 30).toInstant(zone), t, false)

    private fun settings(start: LocalDate) = UserSettings(onboarded = true, startAllowance = 10, planStart = start)

    private fun view(start: LocalDate, logs: List<SmokeLog>, urges: List<Urge> = emptyList(), day: LocalDate? = null, filter: DayFilter = DayFilter.ALL) =
        Rhythm.view(settings(start), logs, urges, today, now, zone, Stats.dataStart(start, logs, zone), day, filter)

    @Test fun lastThirtyDays_matchesStats() {
        val start = today.minusDays(60)
        val logs = (1..50L).map { log(today.minusDays(it), (it % 24).toInt(), Trigger.COFFEE, it) }
        val v = view(start, logs)
        val period = Period.lastDays(today, 30)
        assertEquals(period, v.period)
        assertArrayEquals(Stats.hourly(logs, period, now, zone, start), v.hours, 0.0001f)
        assertNotNull(v.before)
        assertArrayEquals(Stats.hourly(logs, Period.lastDays(today, 30, 30), now, zone, start), v.before, 0.0001f)
        assertEquals(v.hours.sum(), v.average, 0.0001f)
        assertEquals(29, v.logs.size)
        assertEquals(Stats.peaks(v.hours), v.peaks)
        assertFalse(v.sinceStart)
        assertTrue(v.showPlan)
        assertNull(v.day)
    }

    @Test fun noComparison_untilTheDaysBeforeCoverAWeek() {
        // Tracking began 35 days ago: the 30 days before cover only 6 of them.
        val start = today.minusDays(35)
        val v = view(start, listOf(log(today.minusDays(1), 9)))
        assertNull(v.before)
        assertEquals(36, v.trackedDays)
        assertEquals(1, v.daysToCompare)
        assertNotNull(view(today.minusDays(36), listOf(log(today.minusDays(1), 9))).before)
    }

    @Test fun newUser_averageIsSinceStart_andNoPlanUnderAWeek() {
        val start = today.minusDays(3)
        val v = view(start, listOf(log(today.minusDays(1), 9)))
        assertTrue(v.sinceStart)
        assertEquals(4, v.trackedDays)
        assertEquals(33, v.daysToCompare)
        assertFalse(v.showPlan)
    }

    @Test fun pickedDay_isThatDayOnly() {
        val start = today.minusDays(60)
        val day = today.minusDays(2)
        val logs = listOf(log(day, 8, Trigger.COFFEE, 1), log(day, 8, Trigger.COFFEE, 2), log(day, 8, Trigger.COFFEE, 3), log(today.minusDays(3), 8))
        val v = view(start, logs, day = day)
        assertEquals(Period.day(day), v.period)
        assertEquals(3f, v.hours[8], 0.0001f)
        assertNull(v.before)
        assertEquals(3, v.day?.count)
        assertFalse(v.showPlan)
        // Triggers by hour are only read on the last 30 days.
        assertNull(v.topTrigger(8))
        assertEquals(Trigger.COFFEE, view(start, logs).topTrigger(8)?.trigger)
    }

    @Test fun wavesRidden_countsSurvivedUrgesInThePeriod() {
        val start = today.minusDays(60)
        fun urge(daysAgo: Long, outcome: UrgeOutcome): Urge {
            val t = today.minusDays(daysAgo).atTime(10, 0).toInstant(zone)
            return Urge(daysAgo, t, t.plusSeconds(240), outcome, null)
        }
        val urges = listOf(urge(1, UrgeOutcome.SURVIVED), urge(2, UrgeOutcome.SMOKED), urge(40, UrgeOutcome.SURVIVED))
        assertEquals(1, view(start, emptyList(), urges).wavesRidden)
        assertEquals(0, view(start, emptyList(), urges, day = today.minusDays(2)).wavesRidden)
    }

    @Test fun dayFilter_appliesToBothPeriods() {
        val v = view(today.minusDays(60), emptyList(), filter = DayFilter.WEEKENDS)
        assertEquals(DayFilter.WEEKENDS, v.period.filter)
    }
}
