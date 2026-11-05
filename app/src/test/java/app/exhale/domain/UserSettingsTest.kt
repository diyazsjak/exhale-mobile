package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UserSettingsTest {
    private val day = LocalDate.parse("2026-11-02")

    @Test fun withBorrowNoticeSeen_recordsTheDayAndChangesNothingElse() {
        val s = UserSettings(startAllowance = 9)
        val seen = s.withBorrowNoticeSeen(day)
        assertEquals(day.toEpochDay(), seen.borrowNoticeSeenDay)
        assertEquals(s.copy(borrowNoticeSeenDay = day.toEpochDay()), seen)
    }

    @Test fun borrowNoticeSeenOn_isTrueOnlyForTheDayItWasOpened() {
        val seen = UserSettings().withBorrowNoticeSeen(day)
        assertTrue(seen.borrowNoticeSeenOn(day))
        assertFalse(seen.borrowNoticeSeenOn(day.plusDays(1)))
        assertFalse(UserSettings().borrowNoticeSeenOn(day))
    }

    @Test fun withBorrowNoticeSeen_movesOnToTheNextDay() {
        val seen = UserSettings().withBorrowNoticeSeen(day)
        assertEquals(day.plusDays(1).toEpochDay(), seen.withBorrowNoticeSeen(day.plusDays(1)).borrowNoticeSeenDay)
    }
}
