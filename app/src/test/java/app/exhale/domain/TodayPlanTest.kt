package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TodayPlanTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-11-02")
    private val now: Instant = today.atTime(14, 0).toInstant(zone)
    private val settings = UserSettings(startAllowance = 10, taper = TaperType.HOLD, planStart = LocalDate.parse("2026-10-30"))
    private var id = 0L
    private fun log(date: LocalDate, hour: Int, minute: Int = 0, zone: ZoneId = this.zone) =
        SmokeLog(id++, date.atTime(hour, minute).atZone(zone).toInstant(), null, false)

    @Test fun noLogs_isFullyCoolWithNothingToWaitFor() {
        val p = TodayPlan.of(settings, emptyList(), now, zone)
        assertEquals(today, p.date)
        assertEquals(0, p.count)
        assertEquals(10, p.allowance)
        assertEquals(96, p.intervalMinutes) // 960 waking minutes / 10
        assertEquals(0, p.borrowed)
        assertNull(p.last)
        assertNull(p.sinceLast)
        assertNull(p.nextAllowedAt)
        assertEquals(0f, p.heat, 0f)
    }

    @Test fun lastLog_setsTheGapTheNextEarnedTimeAndHeat() {
        val last = log(today, 13)
        val p = TodayPlan.of(settings, listOf(last), now, zone)
        assertEquals(1, p.count)
        assertEquals(last, p.last)
        assertEquals(Duration.ofHours(1), p.sinceLast)
        assertEquals(last.at.plus(Duration.ofMinutes(96)), p.nextAllowedAt)
        assertEquals(1f - 60f / 96f, p.heat, 0.0001f)
    }

    @Test fun yesterdaysOverage_lowersTodayAndStretchesTheInterval() {
        val yesterday = today.minusDays(1)
        val logs = List(12) { log(yesterday, 8 + it) } // 2 over the 10 allowed
        val p = TodayPlan.of(settings, logs, now, zone)
        assertEquals(2, p.borrowed)
        assertEquals(8, p.allowance)
        assertEquals(120, p.intervalMinutes)
        assertEquals(0, p.count)
    }

    @Test fun overageBeforeThePlanStarted_isNotBorrowed() {
        val s = settings.copy(planStart = today)
        val p = TodayPlan.of(s, List(14) { log(today.minusDays(1), 8 + it % 12) }, now, zone)
        assertEquals(0, p.borrowed)
        assertEquals(10, p.allowance)
    }

    @Test fun days_followTheZone() {
        val tokyo = ZoneId.of("Asia/Tokyo") // +9: 23:30 on the 1st in UTC is 08:30 on the 2nd
        val logs = listOf(log(today.minusDays(1), 23, 30, ZoneOffset.UTC))
        assertEquals(1, TodayPlan.of(settings, logs, now, tokyo).count)
        assertEquals(0, TodayPlan.of(settings, logs, now, ZoneOffset.UTC).count)
    }

    @Test fun earned_onceTheIntervalHasPassed() {
        val waiting = TodayPlan.of(settings, listOf(log(today, 13)), now, zone) // 60 of 96 min
        assertFalse(waiting.earned)
        assertEquals(Duration.ofMinutes(36), waiting.remainingToNext)
        assertEquals(60f / 96f, waiting.earnedFraction, 0.0001f)
        val earned = TodayPlan.of(settings, listOf(log(today, 12)), now, zone) // 120 of 96 min
        assertTrue(earned.earned)
        assertNull(earned.remainingToNext)
        assertEquals(1f, earned.earnedFraction, 0f)
        val none = TodayPlan.of(settings, emptyList(), now, zone)
        assertFalse(none.earned)
        assertEquals(1f, none.earnedFraction, 0f)
    }

    @Test fun overAndLeft_areAgainstTodaysAllowance() {
        val within = TodayPlan.of(settings, List(7) { log(today, 6 + it) }, now, zone)
        assertFalse(within.overAllowance)
        assertEquals(3, within.leftToday)
        assertEquals(0, within.overToday)
        val over = TodayPlan.of(settings, List(12) { log(today, 1 + it) }, now, zone)
        assertTrue(over.overAllowance)
        assertEquals(0, over.leftToday)
        assertEquals(2, over.overToday)
        assertEquals(2, over.borrowingFromTomorrow)
        assertEquals(8, over.tomorrowAllowance)
    }

    @Test fun loweredDay_borrowsOnlyWhatGoesPastTheBase() {
        // Base 10; 12 yesterday lowers today to 8. Debt is measured against the base (§4).
        val yesterday = List(12) { log(today.minusDays(1), 8 + it) }
        val nine = TodayPlan.of(settings, yesterday + List(9) { log(today, 1 + it) }, now, zone)
        assertEquals(8, nine.allowance)
        assertEquals(1, nine.overToday)
        assertEquals(0, nine.borrowingFromTomorrow)
        assertEquals(10, nine.tomorrowAllowance)
        val twelve = TodayPlan.of(settings, yesterday + List(12) { log(today, 1 + it) }, now, zone)
        assertEquals(4, twelve.overToday)
        assertEquals(2, twelve.borrowingFromTomorrow)
        assertEquals(8, twelve.tomorrowAllowance)
        assertEquals(10, twelve.outlook.baseToday)
        assertEquals(10, twelve.outlook.baseTomorrow)
    }

    @Test fun borrowNotice_dueOnALoweredDayUntilSeen() {
        val logs = List(12) { log(today.minusDays(1), 8 + it) }
        assertTrue(TodayPlan.of(settings, logs, now, zone).borrowNoticeDue)
        assertFalse(TodayPlan.of(settings.withBorrowNoticeSeen(today), logs, now, zone).borrowNoticeDue)
        assertFalse(TodayPlan.of(settings, emptyList(), now, zone).borrowNoticeDue)
    }
}
