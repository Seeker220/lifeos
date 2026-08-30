package com.lifeos.email

import com.lifeos.core.Time
import com.lifeos.core.model.CandidateKind
import com.lifeos.core.model.RawMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class EmailClassifierTest {
    private val classifier = EmailClassifier()

    @Test
    fun classifiesAllSevenSeedMessages() = runTest {
        val messages = seedMessages()
        val candidates = classifier.classify(messages)
        assertEquals(7, candidates.size)
        assertEquals(CandidateKind.EXAM, kindOf(candidates, "seed_os_midterm"))
        assertEquals(CandidateKind.DEADLINE, kindOf(candidates, "seed_dsa_due"))
        assertEquals(CandidateKind.EVENT, kindOf(candidates, "seed_google_drive"))
        assertEquals(CandidateKind.NOISE, kindOf(candidates, "seed_piazza"))
        assertEquals(CandidateKind.NOISE, kindOf(candidates, "seed_promo"))
        assertEquals(CandidateKind.DEADLINE, kindOf(candidates, "seed_lab_resub"))
        assertEquals(CandidateKind.NOISE, kindOf(candidates, "seed_github"))

        val midterm = candidates.first { it.messageId == "seed_os_midterm" }
        assertTrue(midterm.confidence >= 0.70)
        val drive = candidates.first { it.messageId == "seed_google_drive" }
        assertTrue(drive.confidence in 0.35..0.80)
        val lab = candidates.first { it.messageId == "seed_lab_resub" }
        assertTrue(lab.confidence in 0.35..0.70)
    }

    @Test
    fun extractedDatesAreRelativeNeverHardcoded() = runTest {
        val candidates = classifier.classify(seedMessages())
        val midterm = Time.parseIsoOrNull(
            candidates.first { it.messageId == "seed_os_midterm" }.proposedStartIso,
        )
        assertNotNull(midterm)
        assertEquals(DayOfWeek.FRIDAY, midterm!!.dayOfWeek)
        assertEquals(14, midterm.hour)
        assertFalse(midterm.isBefore(LocalDateTime.now().minusMinutes(1)))

        val dsa = Time.parseIsoOrNull(
            candidates.first { it.messageId == "seed_dsa_due" }.proposedStartIso,
        )
        assertNotNull(dsa)
        assertEquals(DayOfWeek.TUESDAY, dsa!!.dayOfWeek)
        assertEquals(23, dsa.hour)
        assertEquals(59, dsa.minute)

        val drive = candidates.first { it.messageId == "seed_google_drive" }.proposedStartIso
        assertNotNull(drive)
        assertTrue(drive!!.startsWith(Time.plusDaysIso(Time.todayIso(), 3)))
    }

    @Test
    fun unparseableBodyYieldsNoiseAndKeepsBatch() = runTest {
        val good = seedMessages().first()
        val bad = RawMessage(
            id = "broken",
            from = "??",
            subject = "",
            body = "\u0000".repeat(8),
            receivedAtEpochMs = 1L,
        )
        val out = classifier.classify(listOf(good, bad))
        assertEquals(2, out.size)
        assertEquals("broken", out[1].messageId)
        assertEquals(CandidateKind.NOISE, out[1].kind)
    }

    @Test
    fun proposedTitleStripsReplyPrefixAndCourseCode() {
        assertEquals(
            "Office hours",
            EmailClassifier.cleanTitle("Re: Fwd: Office hours CS3010"),
        )
    }

    @Test
    fun inNDaysAndTomorrowExtract() {
        val inDays = EmailClassifier.extractStartIso("registration closes in 3 days")
        assertNotNull(inDays)
        assertTrue(inDays!!.startsWith(Time.plusDaysIso(Time.todayIso(), 3)))
        val tomorrow = EmailClassifier.extractStartIso("due tomorrow")
        assertEquals(LocalDate.now().plusDays(1), Time.parseIsoOrNull(tomorrow)!!.toLocalDate())
    }

    @Test
    fun missingDateStaysNull() {
        assertNull(EmailClassifier.extractStartIso("hello there, no schedule"))
    }

    private fun kindOf(
        candidates: List<com.lifeos.core.model.EmailCandidate>,
        messageId: String,
    ) = candidates.first { it.messageId == messageId }.kind

    private fun seedMessages(): List<RawMessage> {
        val json = SeedMailboxSync.applyRelativeDates(SeedMailbox.SEED_JSON)
        return kotlinx.serialization.json.Json.decodeFromString<List<RawMessage>>(json)
    }
}
