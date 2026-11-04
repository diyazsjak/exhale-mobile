package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RecoveryTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-04T14:40:00Z")
    private fun t(s: String) = Instant.parse(s)
    private fun log(at: Instant, id: Long = 0) = SmokeLog(id, at, null, false)

    @Test fun next_isTheFirstMilestoneNotYetReached() {
        assertEquals("20 minutes", Milestones.next(Duration.ofMinutes(4))?.label)
        assertEquals("12 hours", Milestones.next(Duration.ofMinutes(161))?.label)
        assertEquals("12 hours", Milestones.next(Duration.ofMinutes(20))?.label)
        assertNull(Milestones.next(null))
        assertNull(Milestones.next(Duration.ofDays(365L * 16)))
        assertEquals(1, Milestones.reached(Duration.ofMinutes(161)))
        assertEquals(0, Milestones.reached(null))
    }

    @Test fun position_isLinearToTheFirstMilestoneThenLogBetweenMilestones() {
        assertEquals(0f, Milestones.position(Duration.ZERO), 0.001f)
        assertEquals(0.5f, Milestones.position(Duration.ofMinutes(10)), 0.001f)
        assertEquals(1f, Milestones.position(Duration.ofMinutes(20)), 0.001f)
        // Halfway between 20 min and 12 h on a log scale is 2 hours (20 × 6 = 120, 120 × 6 = 720).
        assertEquals(1.5f, Milestones.position(Duration.ofHours(2)), 0.001f)
        assertEquals(2f, Milestones.position(Duration.ofHours(12)), 0.001f)
        assertEquals(Milestones.all.size.toFloat(), Milestones.position(Duration.ofDays(365L * 20)), 0.001f)
        assertEquals(0f, Milestones.position(null), 0.001f)
    }

    @Test fun lastReached_remembersTheMostRecentGapThatGotThere() {
        val logs = listOf(
            t("2026-11-01T08:00:00Z"), // Sunday night gap from here: 13 hours, reaches 12 h
            t("2026-11-01T21:00:00Z"),
            t("2026-11-02T07:00:00Z"), // 10 hours: no
            t("2026-11-04T11:59:00Z"), // a 2-day gap before this one also reached 12 h, later
        )
        val r = Recovery.lastReached(logs, now)
        // 20 minutes: the running gap (11:59 → 14:40) is the most recent to reach it.
        assertEquals(t("2026-11-04T12:19:00Z"), r[0])
        // 12 hours: the 07:00 Monday → 11:59 Wednesday gap, at 19:00 Monday.
        assertEquals(t("2026-11-02T19:00:00Z"), r[1])
        // 2 weeks: never.
        assertNull(r[2])
    }

    @Test fun lastReached_staysAfterANewCigarette() {
        val logs = listOf(t("2026-11-03T20:00:00Z"), t("2026-11-04T14:36:00Z"))
        val r = Recovery.lastReached(logs, now)
        assertEquals(t("2026-11-03T20:20:00Z"), r[0])
        assertEquals(t("2026-11-04T08:00:00Z"), r[1])
    }

    @Test fun lastReached_ignoresTheTimeBeforeTheFirstCigarette() {
        val r = Recovery.lastReached(listOf(t("2026-11-04T14:30:00Z")), now)
        assertTrue(r.all { it == null })
        assertTrue(Recovery.lastReached(emptyList(), now).all { it == null })
    }

    @Test fun day_includesTheNightAndTheRunningGap() {
        val logs = listOf(
            t("2026-11-03T21:40:00Z"),
            t("2026-11-04T07:10:00Z"),
            t("2026-11-04T08:05:00Z"),
            t("2026-11-04T08:15:00Z"),
            t("2026-11-04T11:59:00Z"),
        )
        val d = Recovery.day(logs, now, zone)
        assertEquals(t("2026-11-04T00:00:00Z"), d.dayStart)
        assertEquals(t("2026-11-05T00:00:00Z"), d.dayEnd)
        assertEquals(4, d.logs.size)
        assertEquals(5, d.gaps.size)
        // The night counts in full, from yesterday's last one.
        assertEquals(t("2026-11-03T21:40:00Z"), d.gaps.first().start)
        assertEquals(Duration.ofMinutes(570), d.gaps.first().length)
        assertTrue(d.gaps.last().ongoing)
        assertEquals(now, d.gaps.last().end)
        assertFalse(d.gaps.first().ongoing)
        // 9h 30m, 55m, 3h 44m, 2h 41m reach 20 minutes; 10 minutes does not.
        assertEquals(4, d.atLeast(Duration.ofMinutes(20)))
        assertEquals(Duration.ofMinutes(570), d.longest?.length)
    }

    @Test fun day_withoutAnEarlierCigaretteStartsAtTheFirstOneToday() {
        val d = Recovery.day(listOf(t("2026-11-04T09:00:00Z"), t("2026-11-04T10:00:00Z")), now, zone)
        assertEquals(2, d.gaps.size)
        assertEquals(t("2026-11-04T09:00:00Z"), d.gaps.first().start)
    }

    @Test fun day_withNothingTodayIsOneRunningGap() {
        val d = Recovery.day(listOf(t("2026-11-03T22:00:00Z")), now, zone)
        assertEquals(0, d.logs.size)
        assertEquals(1, d.gaps.size)
        assertTrue(d.gaps.single().ongoing)
        assertTrue(Recovery.day(emptyList(), now, zone).gaps.isEmpty())
    }

    @Test fun fewer_countsTheLastSevenDaysAgainstTheBaseline() {
        // 10 a day before; 7 days in the window with 5 a day smoked → 35 fewer.
        val s = UserSettings(baselinePerDay = 10, planStart = LocalDate.parse("2026-10-01"))
        val logs = (0 until 35).map { log(now.minus(Duration.ofHours(4L * it + 1)), it.toLong()) }
        val r = Recovery.summary(s, logs, now, zone)
        assertEquals(35, r.fewer)
        assertFalse(r.fewerSinceStart)
    }

    @Test fun fewer_countsFromThePlanStartWhenItIsNewer() {
        val s = UserSettings(baselinePerDay = 10, planStart = LocalDate.parse("2026-11-02"))
        // Plan started 2 days 14h 40m ago: 26 at 10 a day, 6 smoked.
        val logs = (0 until 6).map { log(now.minus(Duration.ofHours(6L * it + 1)), it.toLong()) }
        val r = Recovery.summary(s, logs, now, zone)
        assertEquals(20, r.fewer)
        assertTrue(r.fewerSinceStart)
    }

    @Test fun fewer_isNullUnderADayIntoThePlan() {
        val s = UserSettings(baselinePerDay = 10, planStart = LocalDate.parse("2026-11-04"))
        assertNull(Recovery.summary(s, emptyList(), now, zone).fewer)
    }

    @Test fun summary_withNoLogsHasNothingReachedAndNoNext() {
        val r = Recovery.summary(UserSettings(planStart = LocalDate.parse("2026-10-01")), emptyList(), now, zone)
        assertNull(r.next)
        assertEquals(0, r.reached)
        assertTrue(r.lastReached.all { it == null })
        assertTrue(r.today.gaps.isEmpty())
    }
}
