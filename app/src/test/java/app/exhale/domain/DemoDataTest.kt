package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class DemoDataTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-11-02")
    private val endOfToday: Instant = today.plusDays(1).atStartOfDay(zone).toInstant()
    private fun demo(now: Instant = endOfToday, seed: Int = 42) = DemoData.generate(today, now, zone, random = Random(seed))

    @Test fun profile_isOnboardedAndTheSixtyDayPlanEndsToday() {
        val s = demo().settings
        assertTrue(s.onboarded)
        assertEquals(today.minusDays(59), s.planStart)
        assertEquals(18, s.baselinePerDay)
        assertEquals(16, s.startAllowance)
    }

    @Test fun profile_keepsTheUsersOwnPreferences() {
        val current = UserSettings(theme = ThemeMode.LIGHT, wakingMinutes = 14 * 60, taper = TaperType.HOLD)
        val s = DemoData.generate(today, endOfToday, zone, current).settings
        assertEquals(ThemeMode.LIGHT, s.theme)
        assertEquals(14 * 60, s.wakingMinutes)
        assertEquals(TaperType.MINUS_ONE_WEEKLY, s.taper) // the demo history follows this plan
        assertTrue(s.onboarded)
    }

    @Test fun history_staysInsideTheSixtyDaysAndNeverInTheFuture() {
        val now = today.atTime(14, 0).toInstant(zone)
        val d = demo(now)
        val first = today.minusDays(59).atStartOfDay(zone).toInstant()
        assertTrue(d.logs.isNotEmpty() && d.urges.isNotEmpty())
        assertTrue((d.logs.map { it.at } + d.urges.map { it.startedAt }).all { it >= first && it < now })
    }

    @Test fun logs_areOldestFirstAndTaperDownFromTheStart() {
        val d = demo()
        assertEquals(d.logs.sortedBy { it.at }, d.logs)
        val counts = History.countsByDay(d.logs, zone)
        assertTrue(counts.getValue(today.minusDays(59)) in 14..17) // 16, ±2
        assertTrue(counts.getValue(today) in 6..9) // 16 − 8 weeks, ±2
    }

    @Test fun urges_lastFourMinutesAndAreAboutStress() {
        val urges = demo().urges
        assertTrue(urges.all { Duration.between(it.startedAt, it.endedAt) == Duration.ofMinutes(4) && it.trigger == Trigger.STRESS })
        assertTrue(urges.any { it.outcome == UrgeOutcome.SURVIVED } && urges.any { it.outcome == UrgeOutcome.SMOKED })
    }

    @Test fun sameSeedGivesSameData_otherSeedsDiffer() {
        assertEquals(demo(), demo())
        assertNotEquals(demo().logs, demo(seed = 7).logs)
    }
}
