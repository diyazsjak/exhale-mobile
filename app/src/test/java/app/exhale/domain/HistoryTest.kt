package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class HistoryTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-11-02")
    private val now: Instant = today.atTime(14, 0).toInstant(zone)
    private val settings = UserSettings(startAllowance = 10, taper = TaperType.HOLD, planStart = LocalDate.parse("2026-10-30"))
    private var id = 0L
    private fun logs(date: String, vararg hours: Int) = hours.map { SmokeLog(id++, LocalDate.parse(date).atTime(it, 0).toInstant(zone), null, false) }

    @Test fun months_newestFirstFromTrackingStart() {
        val logs = logs("2026-10-28", 9) + logs("2026-11-01", 9)
        val months = History.months(settings, logs, today, now, zone, LocalDate.parse("2026-10-28"))
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), months.map { it.month })
        val nov = months[0]
        assertEquals(30, nov.days.size)
        assertEquals(2, nov.tracked) // the 1st and today
        assertNull(nov.days[2]) // the 3rd is still to come
        val oct = months[1]
        assertNull(oct.days[26]) // the 27th is before tracking began
        assertEquals(4, oct.tracked) // 28th to 31st
    }

    @Test fun months_daysBeforeThePlanHaveNoAllowance() {
        val logs = logs("2026-10-28", 9, 10) + logs("2026-10-30", 9)
        val oct = History.months(settings, logs, today, now, zone, LocalDate.parse("2026-10-28"))[1]
        val before = oct.days[27]!! // 28th
        assertNull(before.allowance)
        assertFalse(before.over)
        assertEquals(10, oct.days[29]!!.allowance) // 30th, plan started
        assertEquals(2, oct.planned) // 30th and 31st
        assertEquals(2, oct.within)
    }

    @Test fun months_overAllowanceAndBorrowingShowUp() {
        // 12 on the 30th (2 over) lowers the 31st to 8; 9 on the 31st is then over too.
        val logs = logs("2026-10-30", *IntArray(12) { 8 + it }) + logs("2026-10-31", *IntArray(9) { 8 + it })
        val oct = History.months(settings, logs, today, now, zone, LocalDate.parse("2026-10-30"))[1]
        assertTrue(oct.days[29]!!.over)
        assertEquals(8, oct.days[30]!!.allowance)
        assertTrue(oct.days[30]!!.over)
        assertEquals(0, oct.within)
        assertEquals(10.5f, oct.average, 0.001f)
    }

    @Test fun months_longestGapStaysInsideTheMonth() {
        // Last October log at 20:00 on the 31st; November's first at 09:00 on the 1st.
        val logs = logs("2026-10-31", 8, 20) + logs("2026-11-01", 9, 10)
        val months = History.months(settings, logs, today, now, zone, LocalDate.parse("2026-10-31"))
        assertEquals(Duration.ofHours(12), months[1].longestGap) // 08:00 → 20:00
        // November: 1st 10:00 → now (2nd 14:00) is the longest stretch.
        assertEquals(Duration.ofHours(28), months[0].longestGap)
    }

    @Test fun months_emptyWithoutTracking() {
        assertEquals(emptyList<MonthTally>(), History.months(settings, emptyList(), today, now, zone, null))
    }
}
