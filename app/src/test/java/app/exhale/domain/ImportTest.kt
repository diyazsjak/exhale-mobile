package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class ImportTest {
    private val zone = ZoneId.of("Asia/Almaty") // UTC+5
    private val now: Instant = Instant.parse("2026-11-12T10:00:00Z")
    private fun at(text: String): Instant = Instant.parse(text)
    private var keys = 0
    private val newKey = { "custom:new${keys++}" }

    private fun rows(text: String) = Import.parse(text, zone)!!
    private fun plan(text: String, logs: List<SmokeLog> = emptyList(), urges: List<Urge> = emptyList(), triggers: List<TriggerEntry> = Triggers.defaults) =
        Import.plan(rows(text), logs, urges, triggers, now, newKey)

    private val header = "type,date,time,trigger,minutes,outcome,timestamp\r\n"

    @Test fun roundTrip_whatWasExportedComesBack() {
        val bus = Trigger("custom:bus", "Bus stop, \"late\"")
        val triggers = Triggers.add(Triggers.defaults, bus.label, bus.key)!!.list
        val logs = listOf(
            SmokeLog(1, at("2026-11-11T19:05:00.250Z"), Trigger.COFFEE, false),
            SmokeLog(2, at("2026-11-12T07:30:59Z"), bus, false),
            SmokeLog(3, at("2026-11-12T08:00:00Z"), null, false),
        )
        val urges = listOf(Urge(1, at("2026-11-12T03:00:00Z"), at("2026-11-12T03:04:00Z"), UrgeOutcome.SMOKED, Trigger.STRESS))
        val csv = Export.csv(logs, urges, zone)

        // Into an empty Exhale with the same list: everything, to the second, with its triggers.
        val p = Import.plan(rows(csv), emptyList(), emptyList(), triggers, now, newKey)
        assertEquals(logs.map { it.at.epochSecond to it.trigger }, p.logs.map { it.at.epochSecond to it.trigger })
        assertEquals(urges.map { Triple(it.startedAt, it.endedAt, it.outcome) to it.trigger }, p.urges.map { Triple(it.startedAt, it.endedAt, it.outcome) to it.trigger })
        assertTrue(p.newTriggers.isEmpty())
        assertEquals(0, p.unreadable)

        // Into the Exhale it came from: nothing new.
        val again = Import.plan(rows(csv), logs, urges, triggers, now, newKey)
        assertFalse(again.adds)
        assertEquals(4, again.already)
    }

    @Test fun notAnExhaleFile_isNull() {
        assertNull(Import.parse("", zone))
        assertNull(Import.parse("name,amount\r\nbread,2\r\n", zone))
        assertNull(Import.parse("type,trigger\r\ncigarette,Coffee\r\n", zone)) // no moment
    }

    @Test fun headerOnly_isEmpty() {
        assertEquals(Import.Rows(emptyList(), emptyList(), 0), rows(header))
    }

    @Test fun lenientAboutWhatASpreadsheetDid() {
        // Columns reordered and renamed in case, an extra column, semicolons, LF, no timestamp, no BOM.
        val text = "Note;Time;TYPE;Date;Trigger\n" +
            "x;08:14;Cigarette;2026-11-12;'=1+1\n" +
            ";;;;\n" +
            "y;09:00;cigarette;2026-11-12;\n"
        val r = rows(text)
        assertEquals(
            listOf(Import.CigaretteRow(at("2026-11-12T03:14:00Z"), "=1+1"), Import.CigaretteRow(at("2026-11-12T04:00:00Z"), null)),
            r.cigarettes,
        )
        assertEquals(0, r.unreadable)
    }

    @Test fun timestampWinsOverDateAndTime() {
        val r = rows(header + "cigarette,2026-01-01,00:00,,,,2026-11-12T08:14:03+05:00\r\n")
        assertEquals(at("2026-11-12T03:14:03Z"), r.cigarettes.single().at)
    }

    @Test fun waves_lengthAndOutcome() {
        val r = rows(
            header +
                "craving wave,2026-11-12,08:00,,,,\r\n" + // no length or outcome: a whole wave, ridden out
                "craving wave,2026-11-12,09:00,,7,smoked,\r\n" +
                "craving wave,2026-11-12,10:00,,-3,,\r\n" + // unreadable length
                "craving wave,2026-11-12,11:00,,4,maybe,\r\n", // unreadable outcome
        )
        assertEquals(
            listOf(
                Import.WaveRow(at("2026-11-12T03:00:00Z"), at("2026-11-12T03:04:00Z"), UrgeOutcome.SURVIVED, null),
                Import.WaveRow(at("2026-11-12T04:00:00Z"), at("2026-11-12T04:07:00Z"), UrgeOutcome.SMOKED, null),
            ),
            r.waves,
        )
        assertEquals(2, r.unreadable)
    }

    @Test fun unreadableRows_areCountedAndLeftOut() {
        val r = rows(header + "cigar,2026-11-12,08:00,,,,\r\ncigarette,yesterday,08:00,,,,\r\ncigarette,2026-11-12,25:00,,,,\r\ncigarette,2026-11-12,08:00,,,,\r\n")
        assertEquals(1, r.cigarettes.size)
        assertEquals(3, r.unreadable)
    }

    @Test fun quotedCells_holdSeparatorsQuotesAndLineBreaks() {
        assertEquals(
            listOf(listOf("a,b", "say \"hi\"", "two\nlines", ""), listOf("x")),
            Import.table("\"a,b\",\"say \"\"hi\"\"\",\"two\nlines\",\r\nx"),
        )
    }

    @Test fun plan_skipsWhatsHere_whatsLater_andDuplicatesInTheFile() {
        val stored = listOf(SmokeLog(9, at("2026-11-12T03:14:03.700Z"), null, false))
        val p = plan(
            header +
                "cigarette,,,,,,2026-11-12T08:14:03+05:00\r\n" + // stored, to the second
                "cigarette,,,,,,2026-11-12T09:00:00+05:00\r\n" +
                "cigarette,,,,,,2026-11-12T09:00:00+05:00\r\n" + // twice in the file
                "cigarette,,,,,,2026-11-12T16:00:00+05:00\r\n" + // after now
                "craving wave,,,,4,,2026-11-12T14:58:00+05:00\r\n", // still running at now: ends at now
            logs = stored,
        )
        assertEquals(listOf(at("2026-11-12T04:00:00Z")), p.logs.map { it.at })
        assertEquals(listOf(at("2026-11-12T09:58:00Z") to now), p.urges.map { it.startedAt to it.endedAt })
        assertEquals(2, p.already)
        assertEquals(1, p.later)
        assertEquals(at("2026-11-12T04:00:00Z") to at("2026-11-12T09:58:00Z"), p.span)
        assertEquals(Export.Counts(1, 1), p.counts)
    }

    @Test fun plan_triggers_matchByName_hiddenStayHidden_newOnesJoin() {
        val list = Triggers.setHidden(Triggers.defaults, "DRIVING", true).filterNot { it.key == "BORED" }
        val p = plan(
            header +
                "cigarette,,,coffee,,,2026-11-12T08:00:00+05:00\r\n" + // a built-in, any case
                "cigarette,,,Driving,,,2026-11-12T08:10:00+05:00\r\n" + // hidden: stays hidden
                "cigarette,,,Bored,,,2026-11-12T08:20:00+05:00\r\n" + // deleted built-in: comes back
                "cigarette,,,  Bus   stop ,,,2026-11-12T08:30:00+05:00\r\n" + // new, tidied
                "craving wave,,,bus stop,4,,2026-11-12T08:40:00+05:00\r\n", // the same new one
            triggers = list,
        )
        val bus = p.newTriggers.last()
        assertEquals("Bus stop", bus.label)
        assertTrue(bus.key.startsWith(Triggers.CUSTOM_PREFIX))
        assertEquals(listOf(Trigger.COFFEE, Trigger.DRIVING, Trigger.BORED, bus), p.logs.map { it.trigger })
        assertEquals(bus, p.urges.single().trigger)
        assertEquals(listOf(Trigger.BORED, bus), p.newTriggers)
        assertTrue(p.triggers.first { it.key == "DRIVING" }.hidden)
        assertEquals(listOf("BORED", bus.key, "DRIVING"), p.triggers.takeLast(3).map { it.key })
    }

    @Test fun roundTrip_labelsThatLookLikeFormulasOrQuotes() {
        val labels = listOf("=1+1", "'=sum", "'quoted", "@home")
        val list = labels.foldIndexed(Triggers.defaults) { i, l, n -> Triggers.add(l, n, "custom:$i")!!.list }
        val logs = labels.mapIndexed { i, n -> SmokeLog(i.toLong(), at("2026-11-12T0${i}:00:00Z"), list.first { it.trigger.label == n }.trigger, false) }
        val p = Import.plan(rows(Export.csv(logs, emptyList(), zone)), emptyList(), emptyList(), list, now, newKey)
        assertEquals(labels, p.logs.map { it.trigger?.label })
    }

    @Test fun decode_utf8_elseTheRegionsOldCodePage() {
        assertEquals("\uFEFFАвтобус", Import.decode("\uFEFFАвтобус".toByteArray(Charsets.UTF_8), Locale("ru")))
        // Excel's plain "CSV" on Windows: the region's code page.
        assertEquals("Автобус", Import.decode("Автобус".toByteArray(charset("windows-1251")), Locale("kk")))
        assertEquals("Café", Import.decode("Café".toByteArray(charset("windows-1252")), Locale.UK))
        assertEquals("Łódź", Import.decode("Łódź".toByteArray(charset("windows-1250")), Locale("pl")))
    }

    @Test fun garbled_namesAreFlagged() {
        assertTrue(Import.garbled("Caf\uFFFD"))
        assertTrue(Import.garbled("???????"))
        assertFalse(Import.garbled("Why?"))
        assertFalse(Import.garbled("Автобус"))
    }

    private val today = LocalDate.parse("2026-11-12")
    private fun question(text: String, logs: List<SmokeLog> = emptyList()) =
        Import.question(plan(text, logs = logs), today, zone, Locale.UK)

    @Test fun question_add_namesTheDaysNewTriggersAndWhatsLeftOut() {
        val stored = listOf(SmokeLog(9, at("2026-11-12T03:00:00Z"), null, false))
        val q = question(
            header +
                "cigarette,,,Bus stop,,,2026-09-04T08:00:00+05:00\r\n" +
                "cigarette,,,,,,2026-11-12T08:00:00+05:00\r\n" + // stored
                "craving wave,,,,4,,2026-11-12T09:00:00+05:00\r\n" +
                "cigarette,,,,,,2026-11-12T20:00:00+05:00\r\n" + // later than now
                "cigar,,,,,,2026-11-12T08:00:00+05:00\r\n" +
                "cigar,,,,,,2026-11-12T08:00:00+05:00\r\n",
            logs = stored,
        )
        assertTrue(q.adds)
        assertEquals("Add 1 cigarette and 1 craving wave?", q.title)
        assertEquals(
            "From 4 September to 12 November. New for your triggers: Bus stop. 1 already in Exhale stays as it is. " +
                "1 later than now is left out. 2 lines couldn't be read and are left out.",
            q.text,
        )
    }

    @Test fun question_yearsShowOutsideThisYear_oneDaySaysOn_garbledNamesWarn() {
        val q = question(header + "cigarette,,,??????,,,2025-12-31T08:00:00+05:00\r\ncigarette,,,,,,2025-12-31T09:00:00+05:00\r\n")
        assertEquals(
            "On 31 December 2025. New for your triggers: ??????. Some names look garbled, like “??????”. " +
                "In Excel, save the file as CSV UTF-8 and import it again. Nothing already here changes.",
            q.text,
        )
    }

    @Test fun question_nothingNew() {
        val stored = listOf(SmokeLog(9, at("2026-11-12T03:00:00Z"), null, false))
        val again = question(header + "cigarette,,,,,,2026-11-12T08:00:00+05:00\r\n", logs = stored)
        assertFalse(again.adds)
        assertEquals("Nothing new in that file", again.title)
        assertEquals("What it has is already in Exhale.", again.text)
        assertEquals("It has no cigarettes or craving waves to add. 1 line couldn't be read and is left out.", question(header + "cigar,,,,,,\r\n").text)
    }

    @Test fun addedNote() {
        assertEquals("Added 2 cigarettes", Import.addedNote(Export.Counts(2, 0)))
        assertNull(Import.addedNote(Export.Counts(0, 0)))
    }

    @Test fun counted_and_savedNote() {
        assertEquals("2 cigarettes and 1 craving wave", Export.counted(Export.Counts(2, 1)))
        assertNull(Export.counted(Export.Counts(0, 0)))
    }
}
