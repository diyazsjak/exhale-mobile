package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TriggersTest {
    private val base = Triggers.defaults
    private fun labels(list: List<TriggerEntry>) = list.map { (if (it.hidden) "-" else "") + it.trigger.label }
    private fun custom(name: String) = Trigger(Triggers.CUSTOM_PREFIX + name, name)
    private fun shownCount(n: Int) = (1..n).map { custom("T$it") }

    @Test fun forName_findsShownOrHiddenWithoutChangingThem_elseAdds() {
        val list = Triggers.delete(Triggers.setHidden(base, "DRIVING", true), "BORED", emptyMap())
        assertEquals(TriggerAdded(list, Trigger.COFFEE), Triggers.forName(list, " coffee ", "k"))
        assertEquals(TriggerAdded(list, Trigger.DRIVING), Triggers.forName(list, "Driving", "k")) // stays hidden
        assertEquals(Trigger.BORED, Triggers.forName(list, "Bored", "k")!!.trigger) // a deleted built-in comes back
        val own = Triggers.forName(list, "Bus  stop", "k")!!
        assertEquals(Trigger("k", "Bus stop"), own.trigger)
        assertEquals("Bus stop", labels(own.list)[labels(own.list).indexOf("-Driving") - 1])
        assertNull(Triggers.forName(list, "   ", "k"))
    }

    @Test fun defaults_areTheBuiltInsShown() {
        assertEquals(BuiltInTrigger.entries.map { it.label }, labels(base))
        assertEquals(Trigger.BUILT_IN, Triggers.shown(base))
    }

    @Test fun clean_trimsCollapsesAndCaps() {
        assertEquals("Phone calls", Triggers.clean("  Phone    calls "))
        assertNull(Triggers.clean("   "))
        assertEquals(20, Triggers.clean("x".repeat(30))!!.length)
        // Cutting at 20 never leaves a trailing space.
        assertEquals("aaaaaaaaaaaaaaaaaaa", Triggers.clean("aaaaaaaaaaaaaaaaaaa bbb"))
    }

    @Test fun add_newGoesAfterShownBeforeHidden() {
        val list = Triggers.setHidden(base, "DRIVING", true)
        val r = Triggers.add(list, " Phone  calls", "custom:1")!!
        assertEquals(Trigger("custom:1", "Phone calls"), r.trigger)
        assertEquals(listOf("Coffee", "Stress", "After food", "Drinks", "Bored", "Social", "Other", "Phone calls", "-Driving"), labels(r.list))
    }

    @Test fun add_blankIsNull() {
        assertNull(Triggers.add(base, "  ", "custom:1"))
    }

    @Test fun add_existingNameMeansThatTrigger_ignoringCaseAndSpaces() {
        val r = Triggers.add(base, "afterFOOD", "custom:1")!!
        assertEquals(Trigger.AFTER_FOOD, r.trigger)
        assertSame(base, r.list)
    }

    @Test fun add_hiddenNameShowsItAgain() {
        val list = Triggers.setHidden(base, "DRIVING", true)
        val r = Triggers.add(list, "driving", "custom:1")!!
        assertEquals(Trigger.DRIVING, r.trigger)
        assertEquals(base.size, r.list.size)
        assertFalse(r.list.first { it.key == "DRIVING" }.hidden)
    }

    @Test fun add_deletedBuiltInComesBackAsBuiltIn() {
        val list = Triggers.delete(base, "COFFEE", emptyMap())
        val r = Triggers.add(list, "coffee", "custom:1")!!
        assertEquals(Trigger.COFFEE, r.trigger)
        assertEquals("Coffee", labels(r.list).last())
    }

    @Test fun check_blankTakenHiddenOk() {
        val list = Triggers.setHidden(base, "DRIVING", true)
        assertEquals(NameCheck.Blank, Triggers.check(list, "   "))
        assertEquals(NameCheck.Taken(list.first { it.key == "COFFEE" }), Triggers.check(list, " coffee"))
        assertEquals(NameCheck.Hidden(list.last()), Triggers.check(list, "DRIVING"))
        assertEquals(NameCheck.Ok("Bus stop"), Triggers.check(list, "Bus   stop "))
        // Its own name doesn't count as taken when renaming it.
        assertEquals(NameCheck.Ok("coffee"), Triggers.check(list, "coffee", except = "COFFEE"))
    }

    @Test fun ideas_leaveOutNamesTheListHas() {
        assertEquals(Triggers.IDEAS, Triggers.ideas(base))
        val list = Triggers.add(base, "phone calls", "custom:1")!!.list
        assertEquals(Triggers.IDEAS - "Phone calls", Triggers.ideas(list))
    }

    @Test fun rename_onlyOwnAndUntaken() {
        val list = Triggers.add(base, "Phone calls", "custom:1")!!.list
        assertEquals("Calls with mum", Triggers.rename(list, "custom:1", "Calls with mum")!!.last().trigger.label)
        // Case changes of its own name are fine.
        assertEquals("phone calls", Triggers.rename(list, "custom:1", "phone calls")!!.last().trigger.label)
        assertNull(Triggers.rename(list, "custom:1", "Coffee"))
        assertNull(Triggers.rename(list, "custom:1", " "))
        assertNull(Triggers.rename(list, "COFFEE", "Espresso"))
        assertNull(Triggers.rename(list, "custom:404", "Anything"))
    }

    @Test fun setHidden_movesToEnd_andShowsAgainAfterShown() {
        val hidden = Triggers.setHidden(base, "COFFEE", true)
        assertEquals("-Coffee", labels(hidden).last())
        val two = Triggers.setHidden(hidden, "STRESS", true)
        val back = Triggers.setHidden(two, "COFFEE", false)
        assertEquals(listOf("After food", "Drinks", "Driving", "Bored", "Social", "Other", "Coffee", "-Stress"), labels(back))
        assertSame(base, Triggers.setHidden(base, "COFFEE", false))
    }

    @Test fun move_reordersShownAndKeepsHiddenLast() {
        val list = Triggers.setHidden(base, "OTHER", true)
        val moved = Triggers.move(list, from = 6, to = 0)
        assertEquals(listOf("Social", "Coffee", "Stress", "After food", "Drinks", "Driving", "Bored", "-Other"), labels(moved))
        // Out of range targets clamp; a bad source changes nothing.
        assertEquals("Coffee", labels(Triggers.move(list, 0, 99))[6])
        assertSame(list, Triggers.move(list, 7, 0))
    }

    @Test fun uses_countLogsAndCravings() {
        val at = Instant.parse("2026-11-02T10:00:00Z")
        val mine = custom("Bus stop")
        val logs = listOf(SmokeLog(1, at, Trigger.COFFEE, false), SmokeLog(2, at, mine, false), SmokeLog(3, at, null, false))
        val urges = listOf(Urge(1, at, at, UrgeOutcome.SURVIVED, Trigger.COFFEE))
        assertEquals(mapOf("COFFEE" to 2, mine.key to 1), Triggers.uses(logs, urges))
    }

    @Test fun delete_onlyWhenUnused() {
        val uses = mapOf("COFFEE" to 3)
        assertFalse(Triggers.canDelete("COFFEE", uses))
        assertSame(base, Triggers.delete(base, "COFFEE", uses))
        assertTrue(Triggers.canDelete("STRESS", uses))
        assertEquals(base.size - 1, Triggers.delete(base, "STRESS", uses).size)
    }

    @Test fun chipRow_ninePlusMore_butTenFitsWhole() {
        val ten = shownCount(10)
        assertEquals(ChipRow(ten, 0), Triggers.chipRow(ten, null, expanded = false))
        val twelve = shownCount(12)
        val row = Triggers.chipRow(twelve, null, expanded = false)
        assertEquals(9, row.visible.size)
        assertEquals(3, row.more)
        assertEquals(ChipRow(twelve, 0), Triggers.chipRow(twelve, null, expanded = true))
    }

    @Test fun chipRow_neverHidesThePickedChip() {
        val twelve = shownCount(12)
        assertEquals(0, Triggers.chipRow(twelve, twelve[10], expanded = false).more)
        assertEquals(3, Triggers.chipRow(twelve, twelve[2], expanded = false).more)
    }

    @Test fun resolve_listThenBuiltInThenNull() {
        val mine = custom("Bus stop")
        val renamed = mapOf(mine.key to mine)
        assertEquals(mine, Triggers.resolve(mine.key, renamed))
        // A built-in tag still reads even if the built-in left the list.
        assertEquals(Trigger.COFFEE, Triggers.resolve("COFFEE", emptyMap()))
        assertNull(Triggers.resolve("custom:gone", renamed))
        assertNull(Triggers.resolve(null, renamed))
    }

    @Test fun inSentence_lowersBuiltInsOnly() {
        assertEquals("after food", Trigger.AFTER_FOOD.inSentence)
        assertEquals("Mum's calls", custom("Mum's calls").inSentence)
    }
}
