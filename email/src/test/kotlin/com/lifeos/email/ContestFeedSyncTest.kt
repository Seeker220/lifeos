package com.lifeos.email

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContestFeedSyncTest {
    @Test
    fun parsesUpcomingCodeforcesRounds() {
        val now = 1_700_000_000L
        val json = """
            {"status":"OK","result":[
              {"id":1,"name":"Old Round","phase":"FINISHED","durationSeconds":7200,"startTimeSeconds":100},
              {"id":42,"name":"Codeforces Round 999 (Div. 2)","phase":"BEFORE","durationSeconds":7200,"startTimeSeconds":${now + 3600}}
            ]}
        """.trimIndent()
        val messages = ContestFeedSync.parseCodeforces(json, now)
        assertEquals(1, messages.size)
        assertEquals("cf_42", messages.single().id)
        assertTrue(messages.single().from.contains("codeforces"))
        assertTrue(messages.single().subject.contains("999"))
    }

    @Test
    fun parsesLeetcodeUpcoming() {
        val now = 1_700_000_000L
        val json = """
            {"data":{"upcomingContests":[
              {"title":"Weekly Contest 400","titleSlug":"weekly-contest-400","startTime":${now + 7200},"duration":5400}
            ]}}
        """.trimIndent()
        val messages = ContestFeedSync.parseLeetcode(json, now)
        assertEquals(1, messages.size)
        assertEquals("lc_weekly-contest-400", messages.single().id)
        assertTrue(messages.single().from.contains("leetcode"))
    }

    @Test
    fun leetcodeFallbackHasWeeklyAndBiweekly() {
        val messages = ContestFeedSync.fallbackLeetcode(1_700_000_000_000L)
        assertEquals(2, messages.size)
        assertTrue(messages.any { it.subject.contains("Weekly") })
        assertTrue(messages.any { it.subject.contains("Biweekly") })
    }
}
