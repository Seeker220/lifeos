package com.lifeos.domain

import com.lifeos.core.Time
import com.lifeos.core.model.AppTimeout
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.Event
import com.lifeos.core.model.Goal
import com.lifeos.core.model.Hardness
import com.lifeos.core.model.ScheduleBlock
import com.lifeos.core.model.Todo
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionBuilderTest {

    @Test
    fun largeStateStaysUnderCapAndKeepsGoalsAndTimeouts() {
        val today = Time.todayIso()
        val goals = (1..8).map { i ->
            Goal(id = "g$i", title = "Goal $i", deadlineIso = Time.plusDaysIso(today, i.toLong()), hardness = Hardness.HARD)
        }
        val timeouts = (1..8).map { i ->
            AppTimeout(packageName = "com.example.app$i", limitMinutes = 10 + i, sourceGoalId = "g$i")
        }
        val tasks = (1..40).map { i ->
            Todo(
                id = "t$i",
                title = "Open task with a deliberately long title so truncation has something to drop $i",
                dueIso = Time.plusDaysIso(today, (i % 10).toLong()),
                estMinutes = 45,
                goalId = "g${(i % 8) + 1}",
            )
        }
        val events = (1..30).map { i ->
            Event(
                id = "e$i",
                title = "Event padding $i that should be dropped before any goal",
                startIso = Time.plusDaysIso(today, (i % 6).toLong()) + "T09:00",
            )
        }
        val blocks = (1..20).map { i ->
            ScheduleBlock(
                id = "b$i",
                title = "Block padding $i",
                startHhmm = "08:00",
                endHhmm = "09:00",
                daysOfWeek = listOf(1, 2, 3, 4, 5),
            )
        }
        val memory = (1..30).map { i -> "memory fact $i about something the model does not need under pressure" }
        val state = CanonicalLifeState(
            goals = goals,
            tasks = tasks,
            events = events,
            scheduleBlocks = blocks,
            appTimeouts = timeouts,
            memoryFacts = memory,
        )
        val projection = ProjectionBuilder().build(state)
        assertTrue(projection.charCount <= 4000)
        goals.forEach { goal ->
            assertTrue("missing goal ${goal.id}", projection.json.contains(goal.id))
            assertTrue("missing goal title ${goal.title}", projection.json.contains(goal.title))
        }
        timeouts.forEach { timeout ->
            assertTrue("missing timeout ${timeout.packageName}", projection.json.contains(timeout.packageName))
        }
        assertTrue(projection.json.contains("\"today\""))
        assertTrue(projection.json.contains("\"focus\""))
    }
}
