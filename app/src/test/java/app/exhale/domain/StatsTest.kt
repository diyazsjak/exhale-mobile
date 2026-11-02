package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
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

    @Test fun notSmoked_comparesAgainstBaseline() {
        val since = now.minus(Duration.ofDays(2))
        assertEquals(10, Stats.notSmoked(10, since, now, 10))
        assertEquals(0, Stats.notSmoked(10, since, now, 50))
    }

    @Test fun money_usesPricePerCigarette() {
        assertEquals(500L, Stats.moneySavedMinor(10, 50.0))
    }

    @Test fun hourly_averagesPerDay() {
        val logs = listOf(log(60), log(61), log(60 * 24 + 60))
        val h = Stats.hourly(logs, now, ZoneOffset.UTC, days = 2)
        // 3 logs at 11:xx across a 3-calendar-day window.
        assertEquals(1f, h[10] + h[11], 0.001f)
    }

    @Test fun longestGap_includesTimeSinceLast() {
        val logs = listOf(log(300), log(240), log(30))
        assertEquals(Duration.ofMinutes(210), Stats.longestGap(logs, now, Duration.ofDays(1)))
    }

    @Test fun triggerCounts_sortedDescending() {
        val logs = listOf(log(1, Trigger.COFFEE), log(2, Trigger.STRESS), log(3, Trigger.COFFEE), log(4))
        val r = Stats.triggerCounts(logs, now.minusSeconds(3600))
        assertEquals(Trigger.COFFEE to 2, r.first())
        assertEquals(2, r.size)
    }
}
