package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
