package com.lifeos.email

import com.lifeos.core.Time
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedMailboxSyncTest {
    @Test
    fun fetchSubstitutesRelativeDatesAndReturnsSevenMessages() = runTest {
        val result = SeedMailboxSync().fetch(null)
        assertTrue(result.isSuccess)
        val messages = result.getOrThrow()
        assertEquals(7, messages.size)
        assertFalse(messages.any { "{{" in it.subject || "{{" in it.body })
        val drive = messages.first { it.id == "seed_google_drive" }
        assertTrue(drive.body.contains(Time.plusDaysIso(Time.todayIso(), 3)))
        val now = Time.nowEpochMs()
        assertTrue(messages.all { it.receivedAtEpochMs in (now - 5_000)..(now + 5_000) })
    }

    @Test
    fun seedJsonHasNoHardcodedCalendarYear() {
        assertFalse(Regex("""20\d{2}-\d{2}-\d{2}""").containsMatchIn(SeedMailbox.SEED_JSON))
    }
}
