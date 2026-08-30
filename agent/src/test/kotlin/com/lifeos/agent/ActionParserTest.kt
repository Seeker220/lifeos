package com.lifeos.agent

import com.lifeos.core.model.Action
import com.lifeos.core.model.BlockKind
import com.lifeos.core.model.FocusMode
import com.lifeos.core.model.NetworkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionParserTest {
    @Test
    fun parseResolvesEnumSynonyms() {
        val raw = """{"reply":"ok","actions":[
            {"type":"set_focus_windows","windows":[
              {"daysOfWeek":[1],"startHhmm":"19:00","endHhmm":"21:00","mode":"block","packages":["a"]},
              {"daysOfWeek":[2],"startHhmm":"19:00","endHhmm":"21:00","mode":"allow","packages":["b"]}]},
            {"type":"add_schedule_block","title":"lift","startHhmm":"07:00","endHhmm":"08:00","kind":"workout"},
            {"type":"network_set_mode","mode":"none"}]}"""
        val turn = ActionParser.parse(raw)
        val windows = (turn.actions[0] as Action.SetFocusWindows).windows
        assertEquals(FocusMode.BLACKLIST, windows[0].mode)
        assertEquals(FocusMode.WHITELIST, windows[1].mode)
        assertEquals(BlockKind.GYM, (turn.actions[1] as Action.AddScheduleBlock).kind)
        assertEquals(NetworkMode.OFF, (turn.actions[2] as Action.NetworkSetMode).mode)
    }

    @Test
    fun parseStripsFenceLeadingProseAndTrailingSentence() {
        val raw = """
            Sure, here is the plan:
            ```json
            {"reply":"On it.","actions":[{"type":"create_task","title":"graphs"}]}
            ```
            Hope that helps.
        """.trimIndent()
        val turn = ActionParser.parse(raw)
        assertEquals("On it.", turn.reply)
        assertEquals(1, turn.actions.size)
        assertEquals("graphs", (turn.actions.single() as Action.CreateTask).title)
        assertTrue(turn.skipped.isEmpty())
    }

    @Test
    fun parseKeepsKnownActionsWhenOneTypeIsUnknown() {
        val raw = """
            {"reply":"ok","actions":[
              {"type":"create_task","title":"keep me"},
              {"type":"explode_moon","foo":1},
              {"type":"remember","fact":"also keep"}
            ]}
        """.trimIndent()
        val turn = ActionParser.parse(raw)
        assertEquals(2, turn.actions.size)
        assertEquals(1, turn.skipped.size)
        assertEquals("explode_moon", turn.skipped.single().type)
        assertTrue(turn.actions[0] is Action.CreateTask)
        assertTrue(turn.actions[1] is Action.Remember)
    }

    @Test
    fun parseInvalidJsonReturnsProseAndZeroActions() {
        val turn = ActionParser.parse("this is not json at all {oops")
        assertTrue(turn.reply.isNotBlank())
        assertTrue(turn.actions.isEmpty())
        assertEquals("parse", turn.skipped.single().type)
    }

    @Test
    fun parseCoercesStringDaysAndLimitMinutes() {
        val raw = """
            {"reply":"x","actions":[
              {"type":"create_habit","title":"h","daysOfWeek":"1"},
              {"type":"set_app_timeout","packageName":"com.instagram.android","limitMinutes":"30"}
            ]}
        """.trimIndent()
        val turn = ActionParser.parse(raw)
        val habit = turn.actions[0] as Action.CreateHabit
        assertEquals(listOf(1), habit.daysOfWeek)
        val timeout = turn.actions[1] as Action.SetAppTimeout
        assertEquals(30, timeout.limitMinutes)
    }
}
