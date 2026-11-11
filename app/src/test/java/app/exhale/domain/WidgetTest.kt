package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class WidgetTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-11-02")
    private val now: Instant = today.atTime(14, 0).toInstant(zone)
    private val midnight: Instant = today.plusDays(1).atStartOfDay().toInstant(zone)
    // 10 a day over 960 waking minutes: one every 96 minutes, so a ring step is 12 minutes.
    private val settings = UserSettings(onboarded = true, startAllowance = 10, taper = TaperType.HOLD, planStart = LocalDate.parse("2026-10-30"))
    private var id = 0L
    private fun log(at: Instant) = SmokeLog(id++, at, null, false)
    private fun model(logs: List<SmokeLog>, tap: WidgetTap = WidgetTap(), at: Instant = now) = Widget.model(settings, logs, tap, at, zone)

    // --- Taps ---

    @Test fun firstTap_armsForFourSeconds() {
        assertEquals(TapResult.Arm(now.plusSeconds(4)), Widget.onTap(WidgetTap(), now))
    }

    @Test fun secondTapInsideTheWindow_logs() {
        val armed = WidgetTap(armedUntil = now.plusSeconds(4))
        assertEquals(TapResult.Log, Widget.onTap(armed, now.plusSeconds(3)))
    }

    @Test fun secondTapAfterTheWindow_armsAgain() {
        val armed = WidgetTap(armedUntil = now.plusSeconds(4))
        assertEquals(TapResult.Arm(now.plusSeconds(9)), Widget.onTap(armed, now.plusSeconds(5)))
    }

    @Test fun tapWhileUndoShows_isIgnored_thenArmsOnceItIsGone() {
        val logged = WidgetTap(loggedId = 7, undoUntil = now.plusSeconds(8))
        assertEquals(TapResult.Ignore, Widget.onTap(logged, now.plusSeconds(2)))
        assertEquals(TapResult.Arm(now.plusSeconds(12)), Widget.onTap(logged, now.plusSeconds(8)))
    }

    // --- What it shows ---

    @Test fun notOnboarded_asksToSetUp() {
        val m = Widget.model(UserSettings(onboarded = false), emptyList(), WidgetTap(), now, zone)
        assertEquals(WidgetKind.SETUP, m.kind)
        assertEquals(midnight, m.refreshAt)
    }

    @Test fun nothingLogged_isAFullRingWithNothingToWaitFor() {
        val m = model(emptyList())
        assertEquals(WidgetKind.NOTHING_YET, m.kind)
        assertEquals(1f, m.ringFill, 0f)
        assertEquals(0f, m.heat, 0f)
        assertNull(m.nextAt)
        assertEquals(midnight, m.refreshAt)
    }

    @Test fun waiting_showsWhenTheNextOneIsEarned_andTheRingInWholeSteps() {
        val last = now.minus(Duration.ofMinutes(40)) // 40 of 96 minutes: 3 whole steps of 12
        val m = model(listOf(log(last)))
        assertEquals(WidgetKind.WAITING, m.kind)
        assertEquals(1, m.count)
        assertEquals(10, m.allowance)
        assertEquals(last.plus(Duration.ofMinutes(96)), m.nextAt)
        assertEquals(3f / 8f, m.ringFill, 0f)
        assertEquals(5f / 8f, m.heat, 0f)
        assertEquals(last.plus(Duration.ofMinutes(48)), m.refreshAt) // the next step
    }

    @Test fun earned_isAFullRingAndRedrawsAtMidnight() {
        val last = now.minus(Duration.ofMinutes(120))
        val m = model(listOf(log(last)))
        assertEquals(WidgetKind.EARNED, m.kind)
        assertEquals(1f, m.ringFill, 0f)
        assertEquals(last, m.lastAt)
        assertEquals(midnight, m.refreshAt)
    }

    @Test fun lastStep_redrawsWhenTheNextOneIsEarned() {
        val last = now.minus(Duration.ofMinutes(90)) // inside the 8th step (84–96)
        val m = model(listOf(log(last)))
        assertEquals(7f / 8f, m.ringFill, 0f)
        assertEquals(last.plus(Duration.ofMinutes(96)), m.refreshAt)
    }

    @Test fun overTheAllowance_countsWhatIsBorrowedFromTomorrow() {
        val logs = List(12) { log(today.atTime(7 + it, 0).toInstant(zone)) }
        val m = model(logs, at = today.atTime(19, 30).toInstant(zone))
        assertEquals(12, m.count)
        assertEquals(2, m.overToday)
        assertEquals(2, m.borrowingFromTomorrow)
    }

    @Test fun armed_showsUntilTheWindowCloses() {
        val tap = WidgetTap(armedUntil = now.plusSeconds(4))
        val logs = listOf(log(now.minus(Duration.ofMinutes(40))))
        val m = model(logs, tap)
        assertEquals(WidgetKind.ARMED, m.kind)
        assertEquals(now.plusSeconds(4), m.refreshAt)
        assertEquals(WidgetKind.WAITING, model(logs, tap, now.plusSeconds(4)).kind)
    }

    @Test fun logged_showsUndoForEightSeconds_thenTheNewWait() {
        val made = log(now)
        val tap = WidgetTap(loggedId = made.id, undoUntil = now.plusSeconds(8))
        val m = model(listOf(made), tap, now.plusSeconds(1))
        assertEquals(WidgetKind.LOGGED, m.kind)
        assertEquals(now, m.loggedAt)
        assertEquals(0f, m.ringFill, 0f)
        assertEquals(1f, m.heat, 0f)
        assertEquals(now.plusSeconds(8), m.refreshAt)
        val after = model(listOf(made), tap, now.plusSeconds(8))
        assertEquals(WidgetKind.WAITING, after.kind)
        assertNull(after.loggedAt)
    }

    @Test fun undo_isGoneOnceTheLogIs() {
        val tap = WidgetTap(loggedId = 99, undoUntil = now.plusSeconds(8)) // undone or erased in the app
        val m = model(listOf(log(now.minus(Duration.ofMinutes(40)))), tap)
        assertEquals(WidgetKind.WAITING, m.kind)
        assertNull(m.loggedAt)
    }

    @Test fun ringStep_roundsDown() {
        assertEquals(0f, Widget.ringStep(0.1f), 0f)
        assertEquals(0.125f, Widget.ringStep(0.2f), 0f)
        assertEquals(1f, Widget.ringStep(1f), 0f)
        assertEquals(1f, Widget.ringStep(1.5f), 0f)
    }
}
