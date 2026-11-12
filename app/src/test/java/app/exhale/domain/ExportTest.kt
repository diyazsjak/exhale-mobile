package app.exhale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExportTest {
    private val zone = ZoneId.of("Asia/Almaty") // UTC+5
    private fun at(text: String): Instant = Instant.parse(text)

    private fun rows(csv: String): List<String> {
        assertTrue("starts with a byte-order mark", csv.startsWith("﻿"))
        assertTrue("CRLF line ends", csv.endsWith("\r\n"))
        return csv.removePrefix("﻿").removeSuffix("\r\n").split("\r\n")
    }

    @Test fun nothingLogged_isJustTheHeader() {
        assertEquals(listOf("type,date,time,trigger,minutes,outcome,timestamp"), rows(Export.csv(emptyList(), emptyList(), zone)))
    }

    @Test fun cigarettesAndWaves_oneRowEach_oldestFirst_onThePhonesClock() {
        val logs = listOf(
            SmokeLog(2, at("2026-11-12T07:30:59.900Z"), null, false),
            SmokeLog(1, at("2026-11-11T19:05:00Z"), Trigger.COFFEE, false),
        )
        val urges = listOf(
            Urge(1, at("2026-11-12T03:00:00Z"), at("2026-11-12T03:04:10Z"), UrgeOutcome.SURVIVED, Trigger.STRESS),
            Urge(2, at("2026-11-12T08:00:00Z"), at("2026-11-12T08:01:40Z"), UrgeOutcome.SMOKED, null),
        )
        assertEquals(
            listOf(
                "type,date,time,trigger,minutes,outcome,timestamp",
                // 19:05 UTC is past midnight in Almaty: it lands on the 12th.
                "cigarette,2026-11-12,00:05,Coffee,,,2026-11-12T00:05:00+05:00",
                "craving wave,2026-11-12,08:00,Stress,4,rode it out,2026-11-12T08:00:00+05:00",
                "cigarette,2026-11-12,12:30,,,,2026-11-12T12:30:59+05:00",
                "craving wave,2026-11-12,13:00,,2,smoked,2026-11-12T13:00:00+05:00",
            ),
            rows(Export.csv(logs, urges, zone)),
        )
    }

    @Test fun usersOwnTriggers_areQuotedAndKeptAsText() {
        val logs = listOf(
            SmokeLog(1, at("2026-11-12T03:00:00Z"), Trigger("custom:a", "Work, again"), false),
            SmokeLog(2, at("2026-11-12T04:00:00Z"), Trigger("custom:b", "The \"break\""), false),
            SmokeLog(3, at("2026-11-12T05:00:00Z"), Trigger("custom:c", "=1+1"), false),
            SmokeLog(4, at("2026-11-12T06:00:00Z"), Trigger("custom:d", "Автобус"), false),
        )
        val triggers = rows(Export.csv(logs, emptyList(), zone)).drop(1).map { it.split(",")[3] }
        assertEquals(listOf("\"Work", "\"The \"\"break\"\"\"", "'=1+1", "Автобус"), triggers)
        assertEquals("\"Work, again\"", Export.cell("Work, again"))
        assertEquals("'-2", Export.cell("-2"))
        assertEquals("\"'@a,b\"", Export.cell("@a,b"))
        assertEquals("\"two\nlines\"", Export.cell("two\nlines"))
        assertEquals("", Export.cell(""))
        // An already guarded-looking name is guarded again, so taking the guard off gives back exactly what was written.
        assertEquals("''=1+1", Export.cell("'=1+1"))
        assertEquals("'hi", Export.cell("'hi"))
        listOf("=1+1", "'=1+1", "''=x", "'hi", "''", "'", "-", "plain").forEach { assertEquals(it, Export.unguard(Export.guard(it))) }
        // As 0.17.0 wrote them (only formula characters guarded): the same names come back.
        assertEquals("''note", Export.unguard("''note"))
        assertEquals("=1+1", Export.unguard("'=1+1"))
    }

    @Test fun fileName_isDated() {
        assertEquals("exhale-2026-11-12.csv", Export.fileName(LocalDate.parse("2026-11-12")))
    }

    @Test fun savedNote_countsWhatTheFileHolds() {
        assertEquals("Saved 412 cigarettes and 38 craving waves", Export.savedNote(Export.Counts(412, 38)))
        assertEquals("Saved 1 cigarette and 1 craving wave", Export.savedNote(Export.Counts(1, 1)))
        assertEquals("Saved 3 cigarettes", Export.savedNote(Export.Counts(3, 0)))
        assertEquals("Saved 2 craving waves", Export.savedNote(Export.Counts(0, 2)))
        assertEquals("Saved. Nothing logged yet, so it has only the headings.", Export.savedNote(Export.Counts(0, 0)))
    }
}
