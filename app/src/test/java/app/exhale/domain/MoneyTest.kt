package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class MoneyTest {
    private val zone = ZoneOffset.UTC
    private val start = LocalDate.parse("2026-10-01")
    // 18 a day before; 20 for 10.00 → 50 cents each; goal 599.00.
    private val settings = UserSettings(baselinePerDay = 18, cigsPerPack = 20, packPriceMinor = 1000, planStart = start, goalMinor = 59900)
    private var id = 0L
    /** [n] logs at noon on each of [days] days from the start. */
    private fun daily(n: Int, days: Int) = (0 until days).flatMap { d -> List(n) { SmokeLog(id++, start.plusDays(d.toLong()).atTime(12, 0).toInstant(zone), null, false) } }
    private fun at(day: Int): Instant = start.plusDays(day.toLong()).atStartOfDay(zone).toInstant()

    @Test fun summary_keptSinceStartAndRecentWindows() {
        // 40 full days at 10 a day: 8 a day not smoked.
        val m = Money.summary(settings, daily(10, 40), at(40), zone)
        assertEquals(320, m.notSmoked)
        assertEquals(16000L, m.keptMinor) // 320 × 0.50
        assertEquals(2800L, m.last7Minor) // 56 × 0.50
        assertEquals(12000L, m.last30Minor) // 240 × 0.50
        assertEquals(2800L, m.weeklyMinor)
        assertTrue(m.paceFromLastWeek)
    }

    @Test fun summary_windowsWaitUntilThePlanIsOlder() {
        val m = Money.summary(settings, daily(10, 5), at(5), zone)
        assertNull(m.last7Minor)
        assertNull(m.last30Minor)
        // Pace before a week: the average so far, 40 in 5 days → 56 a week → 28.00.
        assertEquals(2800L, m.weeklyMinor)
        assertFalse(m.paceFromLastWeek)
        assertEquals(0L, Money.summary(settings, emptyList(), at(0).plusSeconds(3600), zone).weeklyMinor)
    }

    @Test fun summary_packsAndGoal() {
        val m = Money.summary(settings, daily(10, 40), at(40), zone)
        assertEquals(16, m.packs) // 320 / 20
        assertEquals(0, m.packRemainder)
        assertEquals(43900L, m.goalLeftMinor) // 599 − 160
        assertEquals(16000f / 59900f, m.goalProgress, 0.0001f)
        assertEquals(16, m.weeksToGoal) // 439 / 28 = 15.7 → 16
        assertEquals(12000L, m.projectedMinor(30)) // 28.00 a week × 30 / 7 = 120.00
        assertEquals(146000L, m.projectedMinor(365)) // a year
    }

    @Test fun notSmokedBetween_countsFractionalDaysInAHalfOpenWindow() {
        val from = at(0)
        // 10 a day over a day and a half is 15, minus the 2 logs inside; the one at the end is outside it.
        val logs = listOf(SmokeLog(1, from, null, false), SmokeLog(2, from.plusSeconds(3600), null, false), SmokeLog(3, at(1).plusSeconds(43_200), null, false))
        assertEquals(13, Money.notSmokedBetween(10, logs, from, at(1).plusSeconds(43_200)))
        assertEquals(0, Money.notSmokedBetween(10, logs, from, from.plusSeconds(3600))) // floored at 0
        assertEquals(0, Money.notSmokedBetween(10, emptyList(), at(1), at(0))) // an empty window
    }

    @Test fun summary_flooredAndReached() {
        // Smoking above the old amount keeps nothing, and the pace is 0: no estimate.
        val over = Money.summary(settings, daily(20, 10), at(10), zone)
        assertEquals(0L, over.keptMinor)
        assertEquals(0L, over.weeklyMinor)
        assertNull(over.weeksToGoal)
        val reached = Money.summary(settings.copy(goalMinor = 5000), daily(10, 40), at(40), zone)
        assertEquals(0L, reached.goalLeftMinor)
        assertEquals(1f, reached.goalProgress, 0f)
        assertEquals(0, reached.weeksToGoal)
    }

    @Test fun keptFollowingPlan_countsTheAllowanceAgainstTheBaseline() {
        // 18 before, starting at 14 and dropping one a week: 4, 5, 6 and 7 fewer a day over four weeks.
        val s = settings.copy(startAllowance = 14, taper = TaperType.MINUS_ONE_WEEKLY)
        assertEquals(7L * (4 + 5 + 6 + 7) * 50, Money.keptFollowingPlan(s, days = 28))
        assertEquals(4L * 50, Money.keptFollowingPlan(s, days = 1))
        // A start above the baseline keeps nothing, never less.
        assertEquals(0L, Money.keptFollowingPlan(s.copy(startAllowance = 30, taper = TaperType.HOLD), days = 28))
    }

    private fun kept(logs: List<SmokeLog>, t: Instant) = Money.summary(settings, logs, t, zone).keptMinor

    @Test fun goalReachedAt_isTheFirstMomentKeptCoversTheGoal() {
        // 599.00 at 0.50 each is 1,198 not smoked: 66.6 days at 18 a day with none smoked.
        val none = Money.goalReachedAt(settings, emptyList(), zone)!!
        assertTrue(kept(emptyList(), none) >= 59900)
        assertTrue(kept(emptyList(), none.minusSeconds(60)) < 59900)
        assertEquals(at(66), none.truncatedTo(ChronoUnit.DAYS))
        // At 10 a day for 40 days, then none: later, and still the first moment.
        val logs = daily(10, 40)
        val some = Money.goalReachedAt(settings, logs, zone)!!
        assertTrue(some.isAfter(none))
        assertTrue(kept(logs, some) >= 59900)
        assertTrue(kept(logs, some.minusSeconds(60)) < 59900)
    }

    @Test fun goalReachedAt_aLogBeforeMovesItLaterOneAfterDoesNot() {
        val reached = Money.goalReachedAt(settings, emptyList(), zone)!!
        // One before: a cigarette's worth later, 1/18 of a day.
        val before = listOf(SmokeLog(1, at(10), null, false))
        assertEquals(reached.plusMillis(86_400_000L / 18), Money.goalReachedAt(settings, before, zone))
        // One just after: kept dips under for a while, but it was reached.
        val after = listOf(SmokeLog(1, reached.plusSeconds(60), null, false))
        assertEquals(reached, Money.goalReachedAt(settings, after, zone))
        assertTrue(kept(after, reached.plusSeconds(120)) < 59900)
    }

    @Test fun goalReachedAt_noneWithoutAGoalOrABaseline() {
        assertNull(Money.goalReachedAt(settings.copy(goalMinor = 0), emptyList(), zone))
        assertNull(Money.goalReachedAt(settings.copy(baselinePerDay = 0), emptyList(), zone))
    }
}
