package com.lifeos.agent

import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.TurnSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFallbacksTest {
    private val state = CanonicalLifeState()

    @Test
    fun googleInterviewExpandsToAtLeastTenActions() {
        val turn = OfflineFallbacks.match("I want to crack a Google interview in 1 month", state)
        assertEquals(TurnSource.OFFLINE_FALLBACK, turn.source)
        assertTrue(turn.actions.size >= 10)
        assertEquals(2, turn.actions.filterIsInstance<Action.SetAppTimeout>().size)
        assertTrue(turn.actions.any { it is Action.AddScheduleBlock })
    }

    @Test
    fun googleInterviewSharesSourceGoalId() {
        val turn = OfflineFallbacks.match("I want to crack a Google interview in 1 month", state)
        val goal = turn.actions.filterIsInstance<Action.CreateGoal>().single()
        val shared = goal.id
        assertEquals("g_google", shared)
        for (action in turn.actions) {
            val sid = sourceGoalIdOf(action)
            if (sid != null) assertEquals(shared, sid)
        }
        assertTrue(turn.actions.count { sourceGoalIdOf(it) != null } >= 8)
    }

    @Test
    fun googleInterviewDeadlinesAreRelativeToToday() {
        val turn = OfflineFallbacks.match("I want to crack a Google interview in 1 month", state)
        val goal = turn.actions.filterIsInstance<Action.CreateGoal>().single()
        assertEquals(Time.plusDaysIso(Time.todayIso(), 30), goal.deadlineIso)
        val tasks = turn.actions.filterIsInstance<Action.CreateTask>()
        assertEquals(Time.plusDaysIso(Time.todayIso(), 3), tasks[0].dueIso)
        assertEquals(Time.plusDaysIso(Time.todayIso(), 7), tasks[1].dueIso)
    }

    @Test
    fun greetingCreatesNothing() {
        for (text in listOf("Hi", "hello there", "asdfgh", "what should I do?")) {
            val turn = OfflineFallbacks.match(text, state)
            assertEquals("no actions for: $text", 0, turn.actions.size)
            assertTrue(turn.reply.isNotBlank())
        }
    }

    @Test
    fun actionableTextCreatesOneTask() {
        val turn = OfflineFallbacks.match("buy groceries tonight", state)
        assertEquals(1, turn.actions.size)
        val task = turn.actions.single() as Action.CreateTask
        assertEquals("buy groceries tonight", task.title)
        assertEquals(TurnSource.OFFLINE_FALLBACK, turn.source)
    }

    private fun sourceGoalIdOf(action: Action): String? = when (action) {
        is Action.CreateTask -> action.sourceGoalId
        is Action.CreateEvent -> action.sourceGoalId
        is Action.CreateHabit -> action.sourceGoalId
        is Action.AddScheduleBlock -> action.sourceGoalId
        is Action.SetAlarm -> action.sourceGoalId
        is Action.SetAppTimeout -> action.sourceGoalId
        is Action.ClearAppTimeout -> action.sourceGoalId
        is Action.SetFocusWindows -> action.sourceGoalId
        else -> null
    }
}
