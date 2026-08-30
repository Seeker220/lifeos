package com.lifeos.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.DemoPackages
import com.lifeos.core.Ports
import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.AppTimeout
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.Habit
import com.lifeos.core.model.Hardness
import com.lifeos.core.model.Todo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoalCardUi(
    val id: String,
    val title: String,
    val dueLabel: String?,
    val hardness: Hardness,
    val riskPercent: Int,
    val capsLine: String?,
    val openCount: Int,
    val expanded: Boolean,
    val tasks: List<TaskRowUi>,
    val habits: List<HabitRowUi>,
    val canUndoExpansion: Boolean,
)

data class TaskRowUi(
    val id: String,
    val title: String,
    val done: Boolean,
    val overdue: Boolean,
    val subtitle: String?,
    val dueIso: String? = null,
)

data class HabitRowUi(
    val id: String,
    val title: String,
    val detail: String,
)

data class GoalsUiState(
    val goals: List<GoalCardUi>,
    val todos: List<TaskRowUi>,
    val empty: Boolean,
)

class GoalsViewModel(private val ports: Ports) : ViewModel() {
    private val expandedIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<GoalsUiState> = combine(
        ports.lifeState.state,
        expandedIds,
    ) { state, expanded -> buildUi(state, expanded) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            buildUi(ports.lifeState.state.value, expandedIds.value),
        )

    fun toggleGoal(id: String) {
        expandedIds.update { if (id in it) it - id else it + id }
    }

    fun completeTask(id: String) {
        viewModelScope.launch {
            ports.executor.execute(listOf(Action.CompleteTask(id = id)), ActionOrigin.USER)
        }
    }

    fun createTask(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            ports.executor.execute(listOf(Action.CreateTask(title = trimmed)), ActionOrigin.USER)
        }
    }

    fun revertExpansion(goalId: String) {
        viewModelScope.launch {
            ports.executor.execute(listOf(Action.RevertExpansion(goalId = goalId)), ActionOrigin.USER)
        }
    }

    private fun buildUi(state: CanonicalLifeState, expanded: Set<String>): GoalsUiState {
        val today = Time.todayIso()
        val goals = state.goals.filter { !it.archived }.map { goal ->
            val nestedTasks = state.tasks.filter { it.goalId == goal.id || it.sourceGoalId == goal.id }
            val nestedHabits = state.habits.filter { it.sourceGoalId == goal.id }
            val caps = state.appTimeouts.filter { it.sourceGoalId == goal.id }
            GoalCardUi(
                id = goal.id,
                title = goal.title,
                dueLabel = formatDue(goal.deadlineIso),
                hardness = goal.hardness,
                riskPercent = ports.risk.riskPercent(state, goal.id),
                capsLine = formatCapsLine(caps),
                openCount = nestedTasks.count { !it.done },
                expanded = goal.id in expanded,
                tasks = nestedTasks.map { it.toRow(today) },
                habits = nestedHabits.map { it.toRow() },
                canUndoExpansion = hasExpansion(state, goal.id),
            )
        }
        val todos = state.tasks
            .filter { task ->
                val unattached = task.goalId == null && task.sourceGoalId == null
                val overdueFromGoal = (task.goalId != null || task.sourceGoalId != null) &&
                    isOverdue(task.dueIso, today)
                unattached || overdueFromGoal
            }
            .map { it.toRow(today) }
            .sortedWith(
                compareByDescending<TaskRowUi> { it.overdue }
                    .thenBy { it.dueIso ?: "\uFFFF" }
                    .thenBy { it.title },
            )
        return GoalsUiState(
            goals = goals,
            todos = todos,
            empty = goals.isEmpty() && todos.isEmpty(),
        )
    }
}

private val dueDayFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private val dayNames = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val knownAppLabels = mapOf(
    DemoPackages.INSTAGRAM to "Instagram",
    DemoPackages.YOUTUBE to "YouTube",
    DemoPackages.CHROME to "Chrome",
    DemoPackages.DOCS to "Docs",
    DemoPackages.MAPS to "Maps",
    DemoPackages.SELF to "LifeOS",
)

private val aliasByPackage: Map<String, String> =
    DemoPackages.ALIASES.entries
        .groupBy({ it.value }, { it.key })
        .mapValues { (_, names) -> names.minBy { it.length } }

internal fun friendlyAppLabel(packageName: String): String {
    knownAppLabels[packageName]?.let { return it }
    val alias = aliasByPackage[packageName]
    if (alias != null) return alias.replaceFirstChar { it.uppercase(Locale.ENGLISH) }
    return packageName.substringAfterLast('.').replaceFirstChar { it.uppercase(Locale.ENGLISH) }
}

internal fun formatCapsLine(timeouts: List<AppTimeout>): String? {
    if (timeouts.isEmpty()) return null
    val body = timeouts.joinToString(" · ") { "${friendlyAppLabel(it.packageName)} ${it.limitMinutes}m" }
    return "Caps: $body"
}

internal fun formatDue(deadlineIso: String?): String? {
    if (deadlineIso.isNullOrBlank()) return null
    val date = Time.parseIsoOrNull(deadlineIso)?.toLocalDate()
        ?: runCatching { LocalDate.parse(deadlineIso.take(10)) }.getOrNull()
        ?: return "Due $deadlineIso"
    return "Due ${date.format(dueDayFmt)}"
}

internal fun isOverdue(dueIso: String?, todayIso: String): Boolean {
    if (dueIso.isNullOrBlank()) return false
    val datePart = dueIso.take(10)
    return if (dueIso.length > 10) {
        val dt = Time.parseIsoOrNull(dueIso) ?: return datePart < todayIso
        dt.isBefore(LocalDateTime.now())
    } else {
        datePart < todayIso
    }
}

private fun hasExpansion(state: CanonicalLifeState, goalId: String): Boolean =
    state.tasks.any { it.sourceGoalId == goalId } ||
        state.habits.any { it.sourceGoalId == goalId } ||
        state.events.any { it.sourceGoalId == goalId } ||
        state.scheduleBlocks.any { it.sourceGoalId == goalId } ||
        state.alarms.any { it.sourceGoalId == goalId } ||
        state.appTimeouts.any { it.sourceGoalId == goalId } ||
        state.focus.windows.any { it.sourceGoalId == goalId }

private fun Todo.toRow(todayIso: String): TaskRowUi {
    val overdue = !done && isOverdue(dueIso, todayIso)
    return TaskRowUi(
        id = id,
        title = title,
        done = done,
        overdue = overdue,
        subtitle = when {
            overdue -> "overdue"
            else -> formatDue(dueIso)
        },
        dueIso = dueIso,
    )
}

private fun Habit.toRow(): HabitRowUi {
    val days = if (daysOfWeek.size >= 7) {
        "Daily"
    } else {
        daysOfWeek.sorted().joinToString(", ") { dayNames.getOrElse(it) { it.toString() } }
    }
    return HabitRowUi(id = id, title = title, detail = "$days · $timeHhmm")
}
