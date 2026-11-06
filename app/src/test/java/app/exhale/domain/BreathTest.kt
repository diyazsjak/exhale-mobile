package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BreathTest {
    @Test fun cycle_isFourInTwoHoldSixOut() {
        assertEquals(BreathPhase.IN, breathAt(0f).phase)
        assertEquals(4, breathAt(0f).secondsLeft)
        assertEquals(BreathPhase.HOLD, breathAt(4.5f).phase)
        assertEquals(BreathPhase.OUT, breathAt(6f).phase)
        assertEquals(6, breathAt(6f).secondsLeft)
        assertEquals(BreathPhase.IN, breathAt(12f).phase)
        assertEquals(0.5f, breathAt(14f).progress, 0.0001f)
        assertEquals(BreathPhase.IN, BreathPhase.OUT.next)
    }

    @Test fun fullness_risesHoldsAndFalls() {
        assertEquals(0f, breathAt(0f).fullness, 0.0001f)
        assertEquals(0.5f, breathAt(2f).fullness, 0.0001f)
        assertEquals(1f, breathAt(5f).fullness, 0.0001f)
        assertEquals(0.5f, breathAt(9f).fullness, 0.0001f)
    }
}
