package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlanMathTest {
    private val start = LocalDate.of(2026, 1, 5)
    private fun s(taper: TaperType, allowance: Int = 12) =
        UserSettings(onboarded = true, startAllowance = allowance, taper = taper, planStart = start)

    @Test fun minusOne_dropsEachWeek() {
        val st = s(TaperType.MINUS_ONE_WEEKLY)
        assertEquals(12, PlanMath.baseAllowance(st, start))
        assertEquals(12, PlanMath.baseAllowance(st, start.plusDays(6)))
        assertEquals(11, PlanMath.baseAllowance(st, start.plusDays(7)))
        assertEquals(0, PlanMath.baseAllowance(st, start.plusDays(7 * 20)))
    }

    @Test fun firstWeeks_previewsEachTaper() {
        assertEquals(listOf(14, 13, 12, 11), PlanMath.firstWeeks(14, TaperType.MINUS_ONE_WEEKLY))
        assertEquals(listOf(20, 18, 17, 15), PlanMath.firstWeeks(20, TaperType.TEN_PERCENT_WEEKLY))
        assertEquals(listOf(14, 14, 14, 14), PlanMath.firstWeeks(14, TaperType.HOLD))
        assertEquals(listOf(1, 0), PlanMath.firstWeeks(1, TaperType.MINUS_ONE_WEEKLY, weeks = 2))
    }

    @Test fun beforePlanStart_usesStartAllowance() {
        assertEquals(12, PlanMath.baseAllowance(s(TaperType.MINUS_ONE_WEEKLY), start.minusDays(3)))
    }

    @Test fun tenPercent_movesAtLeastOnePerWeek() {
        val st = s(TaperType.TEN_PERCENT_WEEKLY, 20)
        assertEquals(20, PlanMath.baseAllowance(st, start))
        assertEquals(18, PlanMath.baseAllowance(st, start.plusDays(7)))
        val small = s(TaperType.TEN_PERCENT_WEEKLY, 5)
        assertEquals(4, PlanMath.baseAllowance(small, start.plusDays(7)))
    }

    @Test fun hold_neverChanges() {
        assertEquals(12, PlanMath.baseAllowance(s(TaperType.HOLD), start.plusDays(100)))
    }

    @Test fun overAllowance_isBorrowedFromNextDay() {
        val st = s(TaperType.HOLD)
        assertEquals(10, PlanMath.allowance(st, start.plusDays(2), smokedYesterday = 14))
        assertEquals(12, PlanMath.allowance(st, start.plusDays(2), smokedYesterday = 9))
        assertEquals(0, PlanMath.allowance(st, start.plusDays(2), smokedYesterday = 40))
    }

    @Test fun noDebtBeforePlanStart() {
        assertEquals(12, PlanMath.allowance(s(TaperType.HOLD), start, smokedYesterday = 30))
    }

    @Test fun interval_spreadsAllowanceOverWakingDay() {
        assertEquals(80, PlanMath.intervalMinutes(12, 960))
        assertEquals(960, PlanMath.intervalMinutes(0, 960))
    }
}
