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

class RemindersTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-11T10:00:00Z")
    private val today = LocalDate.parse("2026-11-11")
    private fun t(s: String) = Instant.parse(s)
    private fun log(at: Instant) = SmokeLog(0, at, null, false)
    private val settings = UserSettings(onboarded = true, startAllowance = 10, taper = TaperType.MINUS_ONE_WEEKLY, planStart = today.minusDays(20))

    /** [days] days up to yesterday with cigarettes at 8:10 and 18:20, plus one at 13:05 every other day. */
    private fun habit(days: Int): List<SmokeLog> = (days downTo 1).flatMap { d ->
        val day = today.minusDays(d.toLong())
        listOfNotNull(
            day.atTime(8, 10), day.atTime(13, 5).takeIf { d % 2 == 0 }, day.atTime(18, 20),
        ).map { log(it.atZone(zone).toInstant()) }
    }

    private fun next(kind: ReminderKind, logs: List<SmokeLog>, after: Instant = now, s: UserSettings = settings) =
        Reminders.next(kind, s, logs, now, after, zone)

    // --- Busy hours

    @Test fun busyHours_waitForAWeekOfDays() {
        val young = settings.copy(planStart = today.minusDays(5))
        assertNull(Reminders.busyHours(young, habit(5), now, zone))
        assertNull(next(ReminderKind.BUSY_HOUR, habit(5), s = young))
        // 18:00 first: today's 8 am counts toward its average already, today's 6 pm not yet.
        assertEquals(listOf(18, 8), Reminders.busyHours(settings, habit(10), now, zone))
    }

    @Test fun busyHour_comes15MinutesBeforeTheNextPeak() {
        val r = next(ReminderKind.BUSY_HOUR, habit(10)) as Reminder.BusyHour
        // 10:00 now: 8 am has passed today, so 18:00 − 15 min.
        assertEquals(t("2026-11-11T17:45:00Z"), r.at)
        assertEquals(18, r.hour)
        // After that one, tomorrow's 8 am.
        assertEquals(t("2026-11-12T07:45:00Z"), next(ReminderKind.BUSY_HOUR, habit(10), after = r.at)?.at)
    }

    @Test fun busyHour_atMidnightFallsOnTheDayBefore() {
        val nights = (10 downTo 1).map { d -> log(today.minusDays(d.toLong()).atTime(0, 30).atZone(zone).toInstant()) }
        assertEquals(t("2026-11-11T23:45:00Z"), next(ReminderKind.BUSY_HOUR, nights)?.at)
    }

    @Test fun busyHour_noneWithNothingLogged() {
        assertEquals(emptyList<Int>(), Reminders.busyHours(settings, emptyList(), now, zone))
        assertNull(next(ReminderKind.BUSY_HOUR, emptyList()))
    }

    @Test fun busyHour_isSkippedRightAfterACigaretteOrAWave() {
        val r = Reminder.BusyHour(t("2026-11-11T17:45:00Z"), 18)
        val at = t("2026-11-11T17:46:00Z")
        assertTrue(Reminders.shouldPost(r, habit(10), emptyList(), at))
        assertFalse(Reminders.shouldPost(r, habit(10) + log(t("2026-11-11T17:20:00Z")), emptyList(), at))
        // 31 minutes before: long enough ago.
        assertTrue(Reminders.shouldPost(r, habit(10) + log(t("2026-11-11T17:15:00Z")), emptyList(), at))
        val wave = Urge(1, t("2026-11-11T17:30:00Z"), t("2026-11-11T17:34:00Z"), UrgeOutcome.SURVIVED, null)
        assertFalse(Reminders.shouldPost(r, habit(10), listOf(wave), at))
    }

    @Test fun shouldPost_onlyFromItsTimeUntilItIsTooLate() {
        val r = Reminder.BusyHour(t("2026-11-11T17:45:00Z"), 18)
        assertFalse(Reminders.shouldPost(r, emptyList(), emptyList(), t("2026-11-11T17:44:00Z")))
        assertTrue(Reminders.shouldPost(r, emptyList(), emptyList(), t("2026-11-11T17:59:00Z")))
        // Once the hour has begun, the nudge is no use.
        assertFalse(Reminders.shouldPost(r, emptyList(), emptyList(), t("2026-11-11T18:00:00Z")))
        val m = Reminder.MilestoneReached(t("2026-11-11T02:00:00Z"), Milestones.all[1])
        assertTrue(Reminders.shouldPost(m, emptyList(), emptyList(), t("2026-11-11T13:59:00Z")))
        assertFalse(Reminders.shouldPost(m, emptyList(), emptyList(), t("2026-11-11T14:00:00Z")))
    }

    // --- Milestones

    @Test fun milestone_isTheFirstTimeAGapReachesIt() {
        // Never 12 hours apart yet: 12 hours after the last one.
        val logs = listOf(log(t("2026-11-10T08:00:00Z")), log(t("2026-11-10T19:00:00Z")), log(t("2026-11-11T06:00:00Z")))
        val r = next(ReminderKind.MILESTONE, logs) as Reminder.MilestoneReached
        assertEquals("12 hours", r.milestone.label)
        assertEquals(t("2026-11-11T18:00:00Z"), r.at)
        assertEquals("12 hours · Carbon monoxide back to normal", r.title)
        // Then 2 weeks.
        assertEquals("2 weeks", (next(ReminderKind.MILESTONE, logs, after = r.at) as Reminder.MilestoneReached).milestone.label)
    }

    @Test fun milestone_reachedBeforeDoesNotComeAgain() {
        // 13 hours between the first two: 12 hours was reached, so the next is 2 weeks.
        val logs = listOf(log(t("2026-11-09T20:00:00Z")), log(t("2026-11-10T09:00:00Z")), log(t("2026-11-11T06:00:00Z")))
        val r = next(ReminderKind.MILESTONE, logs) as Reminder.MilestoneReached
        assertEquals("2 weeks", r.milestone.label)
        assertEquals(t("2026-11-25T06:00:00Z"), r.at)
    }

    @Test fun milestone_neverTwentyMinutesAndNoneWithoutLogs() {
        val r = next(ReminderKind.MILESTONE, listOf(log(t("2026-11-11T09:55:00Z"))))
        assertEquals("12 hours", (r as Reminder.MilestoneReached).milestone.label)
        assertNull(next(ReminderKind.MILESTONE, emptyList()))
    }

    // --- Plan weeks

    @Test fun planWeek_atMidnightWhenTheAllowanceDrops() {
        // Plan began 20 days ago (a Friday, 22 October): the next week starts 12 November.
        val r = next(ReminderKind.PLAN_WEEK, emptyList()) as Reminder.PlanWeek
        assertEquals(t("2026-11-12T00:00:00Z"), r.at)
        assertEquals(7, r.perDay)
        assertEquals("This week: 7 a day", r.title)
        assertEquals("One fewer than last week.", r.text)
        assertEquals(t("2026-11-19T00:00:00Z"), next(ReminderKind.PLAN_WEEK, emptyList(), after = r.at)?.at)
    }

    @Test fun planWeek_saysHowMuchItDroppedAndWhenItReachesZero() {
        val tenth = settings.copy(startAllowance = 30, taper = TaperType.TEN_PERCENT_WEEKLY)
        val r = next(ReminderKind.PLAN_WEEK, emptyList(), s = tenth) as Reminder.PlanWeek
        assertEquals("${r.before - r.perDay} fewer than last week.", r.text)
        assertTrue(r.before - r.perDay > 1)
        val last = settings.copy(startAllowance = 3)
        val zero = next(ReminderKind.PLAN_WEEK, emptyList(), s = last) as Reminder.PlanWeek
        assertEquals(0, zero.perDay)
        assertEquals("Your plan reaches zero. Urge Wave is here when a craving comes.", zero.text)
        // Once at zero, nothing more drops.
        assertNull(next(ReminderKind.PLAN_WEEK, emptyList(), after = zero.at, s = last))
    }

    @Test fun planWeek_neverOnHoldSteady() {
        assertNull(next(ReminderKind.PLAN_WEEK, emptyList(), s = settings.copy(taper = TaperType.HOLD)))
    }

    @Test fun planWeek_firstOneComesAWeekAfterAStartStillAhead() {
        val ahead = settings.copy(planStart = today.plusDays(3))
        assertEquals(t("2026-11-21T00:00:00Z"), next(ReminderKind.PLAN_WEEK, emptyList(), s = ahead)?.at)
    }

    // --- Everything

    @Test fun nothingBeforeOnboarding() {
        val fresh = settings.copy(onboarded = false)
        ReminderKind.entries.forEach { assertNull(next(it, habit(10), s = fresh)) }
    }

    @Test fun lateness_perKind() {
        assertEquals(Duration.ofMinutes(15), ReminderKind.BUSY_HOUR.late)
        assertEquals(Duration.ofHours(12), ReminderKind.MILESTONE.late)
        assertEquals(Duration.ofHours(24), ReminderKind.PLAN_WEEK.late)
    }

    @Test fun withReminder_turnsOneOnOrOff() {
        val on = UserSettings().withReminder(ReminderKind.MILESTONE, true)
        assertEquals(setOf(ReminderKind.MILESTONE), on.reminders)
        assertEquals(emptySet<ReminderKind>(), on.withReminder(ReminderKind.MILESTONE, false).reminders)
        assertEquals(emptySet<ReminderKind>(), UserSettings().reminders)
    }
}
