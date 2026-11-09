package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class DayTimesTest {
    private val zone = ZoneOffset.UTC
    private val sunday: LocalDate = LocalDate.parse("2026-11-08")
    private val monday: LocalDate = sunday.plusDays(1)
    private val now: Instant = monday.atTime(14, 21).toInstant(zone)

    private var nextId = 0L
    private fun log(day: LocalDate, h: Int, m: Int, t: Trigger? = null) = SmokeLog(nextId++, day.atTime(h, m).toInstant(zone), t, false)
    private fun urge(day: LocalDate, h: Int, m: Int, outcome: UrgeOutcome = UrgeOutcome.SURVIVED): Urge {
        val at = day.atTime(h, m).toInstant(zone)
        return Urge(nextId++, at, at.plusSeconds(240), outcome, Trigger.STRESS)
    }

    private val saturdayLast = log(sunday.minusDays(1), 23, 20)
    private val sundayLogs = listOf(
        log(sunday, 9, 38, Trigger.COFFEE), log(sunday, 13, 5), log(sunday, 13, 52),
        log(sunday, 18, 44), log(sunday, 20, 57), log(sunday, 22, 31),
    )

    @Test fun pastDay_listsCigarettesInOrder_withGapsAndNumbers() {
        val t = DayTimes.of(listOf(saturdayLast) + sundayLogs, emptyList(), sunday, zone, allowance = 8, now = now)
        assertEquals(6, t.count)
        assertEquals(saturdayLast, t.previous)
        assertEquals(sundayLogs, t.cigarettes.map { it.log })
        assertEquals((1..6).toList(), t.cigarettes.map { it.number })
        // The first gap counts from the day before's last one.
        assertEquals(Duration.ofMinutes(10 * 60 + 18), t.cigarettes[0].gapBefore)
        assertEquals(Duration.ofMinutes(47), t.cigarettes[2].gapBefore)
        assertTrue(t.cigarettes.none { it.over })
        assertNull(t.running)
    }

    @Test fun pastTheAllowance_isOver() {
        val t = DayTimes.of(sundayLogs, emptyList(), sunday, zone, allowance = 4, now = now)
        assertEquals(listOf(false, false, false, false, true, true), t.cigarettes.map { it.over })
    }

    @Test fun beforeThePlanStart_nothingIsOver() {
        val t = DayTimes.of(sundayLogs, emptyList(), sunday, zone, allowance = null, now = now)
        assertTrue(t.cigarettes.none { it.over })
    }

    @Test fun firstLogEver_hasNoGapBefore() {
        val t = DayTimes.of(sundayLogs, emptyList(), sunday, zone, allowance = 8, now = now)
        assertNull(t.previous)
        assertNull(t.cigarettes[0].gapBefore)
    }

    @Test fun onlyWavesRiddenOut_show_inTimeOrder() {
        val ridden = urge(sunday, 16, 12)
        val smoked = urge(sunday, 17, 0, UrgeOutcome.SMOKED)
        val otherDay = urge(monday, 9, 0)
        val t = DayTimes.of(sundayLogs, listOf(otherDay, smoked, ridden), sunday, zone, allowance = 8, now = now)
        val entries = t.entries
        assertEquals(7, entries.size)
        assertEquals(DayEntry.Wave(ridden), entries[3])
        assertEquals(Duration.ofMinutes(4), (entries[3] as DayEntry.Wave).length)
        // A wave doesn't count as a cigarette or split the gap.
        assertEquals(Duration.ofMinutes(4 * 60 + 52), (entries[4] as DayEntry.Cigarette).gapBefore)
    }

    @Test fun longestGap_isBetweenCigarettes_notOvernight() {
        val t = DayTimes.of(listOf(saturdayLast) + sundayLogs, emptyList(), sunday, zone, allowance = 8, now = now)
        assertEquals(Gap(sundayLogs[2].at, sundayLogs[3].at, ongoing = false), t.longestGap)
        assertEquals(Duration.ofMinutes(4 * 60 + 52), t.longestGap!!.length)
    }

    @Test fun longestGap_needsTwoMoments() {
        val one = DayTimes.of(listOf(log(sunday, 9, 0)), emptyList(), sunday, zone, allowance = 8, now = now)
        assertNull(one.longestGap)
    }

    @Test fun today_hasTheRunningGap_andLeavesOutTheFuture() {
        val today = listOf(log(monday, 7, 57), log(monday, 11, 40))
        val later = log(monday, 18, 0)
        val t = DayTimes.of(sundayLogs + today + later, emptyList(), monday, zone, allowance = 10, now = now)
        assertEquals(2, t.count)
        assertEquals(Gap(today[1].at, now, ongoing = true), t.running)
        // 7:57 to 11:40 (3h 43m) is longer than the running 2h 41m.
        assertEquals(Gap(today[0].at, today[1].at, ongoing = false), t.longestGap)
    }

    @Test fun today_longestGap_canBeTheRunningOne() {
        val today = listOf(log(monday, 7, 0), log(monday, 8, 0))
        val t = DayTimes.of(today, emptyList(), monday, zone, allowance = 10, now = now)
        assertEquals(Gap(today[1].at, now, ongoing = true), t.longestGap)
    }

    @Test fun today_withNothingYet_runsFromTheLastOneBefore() {
        val t = DayTimes.of(sundayLogs, emptyList(), monday, zone, allowance = 10, now = now)
        assertEquals(0, t.count)
        assertFalse(t.isEmpty)
        assertEquals(Gap(sundayLogs.last().at, now, ongoing = true), t.running)
        assertNull(t.longestGap)
    }

    @Test fun aDayWithNothing_isEmpty() {
        assertTrue(DayTimes.of(emptyList(), emptyList(), monday, zone, allowance = 10, now = now).isEmpty)
        // A past day with no logs has no running gap, whatever came before.
        assertTrue(DayTimes.of(listOf(saturdayLast), emptyList(), sunday, zone, allowance = 8, now = now).isEmpty)
        assertFalse(DayTimes.of(sundayLogs, emptyList(), sunday, zone, allowance = 8, now = now).isEmpty)
    }

    @Test fun pastDay_ignoresLaterDays_andTheFuture() {
        val later = listOf(log(monday, 7, 0), log(monday, 20, 0))
        val t = DayTimes.of(listOf(saturdayLast) + sundayLogs + later, emptyList(), sunday, zone, allowance = 8, now = now)
        assertEquals(sundayLogs, t.cigarettes.map { it.log })
        assertEquals(saturdayLast, t.previous)
    }

    @Test fun lineStart_isSixAm_orTheHourOfAnEarlierFirstOne() {
        assertEquals(360, DayTimes.lineStart(null))
        assertEquals(360, DayTimes.lineStart(LocalTime.of(7, 57)))
        assertEquals(300, DayTimes.lineStart(LocalTime.of(5, 40)))
        assertEquals(0, DayTimes.lineStart(LocalTime.of(0, 15)))
        val t = DayTimes.of(listOf(log(monday, 5, 40)), emptyList(), monday, zone, allowance = 10, now = now)
        assertEquals(300, t.lineStartMinute)
    }

    @Test fun stepHour_wrapsRoundTheClock_keepingMinutes() {
        assertEquals(14 * 60 + 5, LogEdit.stepHour(13 * 60 + 5, 1))
        assertEquals(5, LogEdit.stepHour(23 * 60 + 5, 1))
        assertEquals(23 * 60 + 5, LogEdit.stepHour(5, -1))
    }

    @Test fun stepMinute_wrapsRoundTheHour_keepingTheHour() {
        assertEquals(13 * 60 + 6, LogEdit.stepMinute(13 * 60 + 5, 1))
        assertEquals(13 * 60, LogEdit.stepMinute(13 * 60 + 59, 1))
        assertEquals(13 * 60 + 59, LogEdit.stepMinute(13 * 60, -1))
    }

    @Test fun moved_staysOnItsDay_atTheStartOfTheMinute() {
        val original = sunday.atTime(13, 5, 22).toInstant(zone)
        assertEquals(sunday.atTime(12, 40).toInstant(zone), LogEdit.moved(original, 12 * 60 + 40, zone, now))
        assertEquals(sunday.atTime(0, 10).toInstant(zone), LogEdit.moved(original, 10, zone, now))
    }

    @Test fun moved_toTheSameMinute_keepsTheSeconds() {
        val original = sunday.atTime(13, 5, 22).toInstant(zone)
        assertEquals(original, LogEdit.moved(original, 13 * 60 + 5, zone, now))
    }

    @Test fun moved_neverIntoTheFuture() {
        val original = monday.atTime(11, 40).toInstant(zone)
        assertEquals(now, LogEdit.moved(original, 14 * 60 + 21, zone, now))
        assertNull(LogEdit.moved(original, 14 * 60 + 22, zone, now))
        assertFalse(LogEdit.moved(original, 13 * 60, zone, now) == null)
    }

    @Test fun rhythm_pickedDay_carriesItsTimes() {
        val s = UserSettings(onboarded = true, startAllowance = 10, planStart = sunday.minusDays(10))
        val logs = listOf(saturdayLast) + sundayLogs
        val v = Rhythm.view(s, logs, emptyList(), monday, now, zone, Stats.dataStart(s.planStart, logs, zone), sunday, DayFilter.ALL)
        val times = v.times!!
        assertEquals(sunday, times.day)
        assertEquals(v.day!!.allowance, times.allowance)
        assertEquals(6, times.count)
        assertNull(Rhythm.view(s, logs, emptyList(), monday, now, zone, sunday, null, DayFilter.ALL).times)
    }
}
