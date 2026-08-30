package com.lifeos.domain

import com.lifeos.core.ProjectionPort
import com.lifeos.core.Time
import com.lifeos.core.model.AppTimeout
import com.lifeos.core.model.CandidateStatus
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.Event
import com.lifeos.core.model.Goal
import com.lifeos.core.model.LifeStateProjection
import com.lifeos.core.model.ScheduleBlock
import com.lifeos.core.model.Todo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.temporal.ChronoUnit

class ProjectionBuilder : ProjectionPort {
    private val risk = RiskCalculator()

    override fun build(state: CanonicalLifeState): LifeStateProjection {
        val today = Time.todayIso()
        val now = Time.nowIso()
        val goals = state.goals.filterNot { it.archived }
        val timeouts = state.appTimeouts
        var memory = state.memoryFacts.takeLast(12).reversed()
        var blocks = state.scheduleBlocks.filter { block ->
            block.daysOfWeek.isNotEmpty() || inNext7Days(block.dateIso, today)
        }
        var events = state.events.filter { inNext7Days(it.startIso, today) }
            .sortedBy { it.startIso }
        var tasks = state.tasks.filter { !it.done }
            .sortedWith(compareBy(nullsLast()) { Time.parseIsoOrNull(it.dueIso) })
            .take(15)

        var json = emit(state, today, now, goals, timeouts, memory, blocks, events, tasks)
        while (json.length > CAP) {
            when {
                memory.isNotEmpty() -> memory = memory.dropLast(1)
                blocks.isNotEmpty() -> blocks = blocks.dropLast(1)
                events.isNotEmpty() -> events = events.dropLast(1)
                tasks.isNotEmpty() -> tasks = tasks.dropLast(1)
                else -> break
            }
            json = emit(state, today, now, goals, timeouts, memory, blocks, events, tasks)
        }
        return LifeStateProjection(json)
    }

    private fun emit(
        state: CanonicalLifeState,
        today: String,
        now: String,
        goals: List<Goal>,
        timeouts: List<AppTimeout>,
        memory: List<String>,
        blocks: List<ScheduleBlock>,
        events: List<Event>,
        tasks: List<Todo>,
    ): String = buildJsonObject {
        put("today", today)
        put("now", now)
        put("persona", state.personaId)
        put("goals", buildJsonArray {
            goals.forEach { goal ->
                add(buildJsonObject {
                    put("id", goal.id)
                    put("title", goal.title)
                    goal.deadlineIso?.let { put("deadlineIso", it) }
                    put("hardness", goal.hardness.name)
                    put("riskPercent", risk.riskPercent(state, goal.id))
                })
            }
        })
        put("openTasks", buildJsonArray {
            tasks.forEach { task ->
                add(buildJsonObject {
                    put("id", task.id)
                    put("title", task.title)
                    task.dueIso?.let { put("dueIso", it) }
                    put("estMinutes", task.estMinutes)
                    task.goalId?.let { put("goalId", it) }
                })
            }
        })
        put("eventsNext7Days", buildJsonArray {
            events.forEach { event ->
                add(buildJsonObject {
                    put("id", event.id)
                    put("title", event.title)
                    put("startIso", event.startIso)
                    event.endIso?.let { put("endIso", it) }
                })
            }
        })
        put("habits", buildJsonArray {
            state.habits.forEach { habit ->
                add(buildJsonObject {
                    put("id", habit.id)
                    put("title", habit.title)
                    put("daysOfWeek", JsonArray(habit.daysOfWeek.map { JsonPrimitive(it) }))
                    put("timeHhmm", habit.timeHhmm)
                    put("doneToday", today in habit.completedDates)
                })
            }
        })
        put("scheduleBlocks", buildJsonArray {
            blocks.forEach { block ->
                add(buildJsonObject {
                    put("id", block.id)
                    put("title", block.title)
                    put("startHhmm", block.startHhmm)
                    put("endHhmm", block.endHhmm)
                    put("kind", block.kind.name)
                    if (block.daysOfWeek.isNotEmpty()) {
                        put("daysOfWeek", JsonArray(block.daysOfWeek.map { JsonPrimitive(it) }))
                    }
                    block.dateIso?.let { put("dateIso", it) }
                })
            }
        })
        put("alarms", buildJsonArray {
            state.alarms.filter { it.enabled }.forEach { alarm ->
                add(buildJsonObject {
                    put("label", alarm.label)
                    put("timeHhmm", alarm.timeHhmm)
                })
            }
        })
        put("appTimeouts", buildJsonArray {
            timeouts.forEach { timeout ->
                add(buildJsonObject {
                    put("packageName", timeout.packageName)
                    put("limitMinutes", timeout.limitMinutes)
                    timeout.sourceGoalId?.let { put("sourceGoalId", it) }
                })
            }
        })
        put("focus", buildJsonObject {
            put("active", state.focus.active)
            put("mode", state.focus.mode.name)
            put("packages", JsonArray(state.focus.packages.map { JsonPrimitive(it) }))
            put("windowCount", state.focus.windows.size)
        })
        put("network", buildJsonObject {
            put("mode", state.network.mode.name)
            put("packages", JsonArray(state.network.packages.map { JsonPrimitive(it) }))
        })
        put("pendingEmailCount", state.emailCandidates.count { it.status == CandidateStatus.PENDING })
        put("memoryFacts", JsonArray(memory.map { JsonPrimitive(it) }))
        put("xp", state.gamification.xp)
        put("streakDays", state.gamification.streakDays)
    }.toString()

    private fun inNext7Days(iso: String?, today: String): Boolean {
        val date = Time.parseIsoOrNull(iso)?.toLocalDate() ?: return false
        val start = Time.parseIsoOrNull(today)?.toLocalDate() ?: return false
        val days = ChronoUnit.DAYS.between(start, date)
        return days in 0..6
    }

    companion object {
        private const val CAP = 4000
    }
}
