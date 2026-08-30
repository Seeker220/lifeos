package com.lifeos.domain

import com.lifeos.core.ActionExecutorPort
import com.lifeos.core.AppCatalog
import com.lifeos.core.DemoPackages
import com.lifeos.core.EnforceGateway
import com.lifeos.core.Ids
import com.lifeos.core.LifeOsLog
import com.lifeos.core.LifeStateStore
import com.lifeos.core.Personas
import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.AlarmSpec
import com.lifeos.core.model.AppTimeout
import com.lifeos.core.model.AppliedChange
import com.lifeos.core.model.CandidateStatus
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.ChangeKind
import com.lifeos.core.model.EnforcementRules
import com.lifeos.core.model.EntitySource
import com.lifeos.core.model.Event
import com.lifeos.core.model.ExecuteReport
import com.lifeos.core.model.FocusMode
import com.lifeos.core.model.FocusSession
import com.lifeos.core.model.Goal
import com.lifeos.core.model.Habit
import com.lifeos.core.model.Hardness
import com.lifeos.core.model.NetworkMode
import com.lifeos.core.model.ScheduleBlock
import com.lifeos.core.model.SkippedAction
import com.lifeos.core.model.Todo
import java.time.format.DateTimeFormatter

class ActionExecutor(
    private val store: LifeStateStore,
    private val enforce: EnforceGateway,
    private val apps: AppCatalog,
) : ActionExecutorPort {

    override suspend fun execute(actions: List<Action>, origin: ActionOrigin): ExecuteReport {
        var working = store.state.value
        val applied = mutableListOf<AppliedChange>()
        val skipped = mutableListOf<SkippedAction>()
        val sideEffects = mutableListOf<() -> Unit>()
        var needsApplyRules = false

        for (action in actions) {
            runCatching {
                val step = applyAction(working, action, origin, sideEffects)
                working = step.state
                applied += step.applied
                step.skipped?.let { skipped += it }
                if (step.needsApplyRules) needsApplyRules = true
            }.onFailure { t ->
                skipped += SkippedAction(action.wireType(), t.message ?: "error")
            }
        }

        store.mutate { working }

        sideEffects.forEach { it.invoke() }

        if (needsApplyRules) {
            enforce.applyRules(buildRules(store.state.value))
        }

        return ExecuteReport(applied, skipped)
    }

    private data class Step(
        val state: CanonicalLifeState,
        val applied: List<AppliedChange> = emptyList(),
        val skipped: SkippedAction? = null,
        val needsApplyRules: Boolean = false,
    )

    private fun skip(state: CanonicalLifeState, type: String, reason: String) =
        Step(state, skipped = SkippedAction(type, reason))

    private fun ok(
        state: CanonicalLifeState,
        label: String,
        kind: ChangeKind,
        refId: String? = null,
        needsApplyRules: Boolean = false,
    ) = Step(state, listOf(AppliedChange(label, kind, refId)), needsApplyRules = needsApplyRules)

    private suspend fun applyAction(
        state: CanonicalLifeState,
        action: Action,
        origin: ActionOrigin,
        sideEffects: MutableList<() -> Unit>,
    ): Step = when (action) {
        is Action.CreateGoal -> createGoal(state, action)
        is Action.UpdateGoal -> updateGoal(state, action)
        is Action.ArchiveGoal -> archiveGoal(state, action)
        is Action.CreateTask -> createTask(state, action)
        is Action.CompleteTask -> completeTask(state, action)
        is Action.CreateEvent -> createEvent(state, action, origin)
        is Action.CreateHabit -> createHabit(state, action, sideEffects)
        is Action.CompleteHabitToday -> completeHabitToday(state, action)
        is Action.AddScheduleBlock -> addScheduleBlock(state, action)
        is Action.Remember -> remember(state, action)
        is Action.SetPersona -> setPersona(state, action)
        is Action.SetAlarm -> setAlarm(state, action, sideEffects)
        is Action.CancelAlarm -> cancelAlarm(state, action, sideEffects)
        is Action.SetAppTimeout -> setAppTimeout(state, action)
        is Action.ClearAppTimeout -> clearAppTimeout(state, action)
        is Action.FocusStart -> focusStart(state, action, sideEffects)
        is Action.FocusStop -> focusStop(state, sideEffects)
        is Action.FocusSetApps -> focusSetApps(state, action)
        is Action.SetFocusWindows -> setFocusWindows(state, action)
        is Action.NetworkSetMode -> networkSetMode(state, action, sideEffects)
        is Action.NetworkSetApps -> networkSetApps(state, action, sideEffects)
        is Action.PromoteEmail -> promoteEmail(state, action)
        is Action.DismissEmail -> dismissEmail(state, action)
        is Action.RevertExpansion -> revertExpansion(state, action, sideEffects)
        is Action.AwardXp -> awardXpAction(state, action)
    }

    private fun createGoal(state: CanonicalLifeState, action: Action.CreateGoal): Step {
        val title = action.title.trim()
        if (title.isEmpty()) return skip(state, "create_goal", "missing title")
        val deadline = parseDateOrNull(action.deadlineIso)
        val existing = state.goals.firstOrNull { it.title.trim().equals(title, ignoreCase = true) }
        val goal = if (existing != null) {
            existing.copy(
                title = title,
                deadlineIso = deadline,
                hardness = action.hardness,
                notes = action.notes,
                archived = false,
            )
        } else {
            Goal(
                id = action.id?.takeIf { it.isNotBlank() } ?: Ids.new("goal"),
                title = title,
                deadlineIso = deadline,
                hardness = action.hardness,
                createdAtIso = Time.nowIso(),
                notes = action.notes,
            )
        }
        val goals = if (existing != null) {
            state.goals.map { if (it.id == existing.id) goal else it }
        } else {
            state.goals + goal
        }
        return ok(state.copy(goals = goals), "Goal: $title", ChangeKind.GOAL, goal.id)
    }

    private fun updateGoal(state: CanonicalLifeState, action: Action.UpdateGoal): Step {
        val existing = state.goals.firstOrNull { it.id == action.id }
            ?: return skip(state, "update_goal", "unknown goal")
        val title = action.title?.trim()
        if (action.title != null && title.isNullOrEmpty()) return skip(state, "update_goal", "missing title")
        val deadline = when {
            action.deadlineIso == null -> existing.deadlineIso
            else -> parseDateOrNull(action.deadlineIso) ?: existing.deadlineIso
        }
        val updated = existing.copy(
            title = title ?: existing.title,
            deadlineIso = deadline,
            hardness = action.hardness ?: existing.hardness,
            notes = action.notes ?: existing.notes,
        )
        return ok(
            state.copy(goals = state.goals.map { if (it.id == updated.id) updated else it }),
            "Goal: ${updated.title}",
            ChangeKind.GOAL,
            updated.id,
        )
    }

    private fun archiveGoal(state: CanonicalLifeState, action: Action.ArchiveGoal): Step {
        val existing = state.goals.firstOrNull { it.id == action.id }
            ?: return skip(state, "archive_goal", "unknown goal")
        val updated = existing.copy(archived = true)
        return ok(
            state.copy(goals = state.goals.map { if (it.id == updated.id) updated else it }),
            "Archived: ${updated.title}",
            ChangeKind.GOAL,
            updated.id,
        )
    }

    private fun createTask(state: CanonicalLifeState, action: Action.CreateTask): Step {
        val title = action.title.trim()
        if (title.isEmpty()) return skip(state, "create_task", "missing title")
        val goalId = action.goalId?.takeIf { it.isNotBlank() } ?: action.sourceGoalId
        val task = Todo(
            id = action.id?.takeIf { it.isNotBlank() } ?: Ids.new("task"),
            title = title,
            goalId = goalId,
            dueIso = parseDateOrNull(action.dueIso),
            estMinutes = action.estMinutes,
            sourceGoalId = action.sourceGoalId,
        )
        return ok(state.copy(tasks = state.tasks + task), "Task: $title", ChangeKind.TASK, task.id)
    }

    private fun completeTask(state: CanonicalLifeState, action: Action.CompleteTask): Step {
        val task = matchTask(state, action.id, action.title)
            ?: return skip(state, "complete_task", "unknown task")
        if (task.done) return skip(state, "complete_task", "already done")
        val now = Time.nowIso()
        val updated = task.copy(done = true, completedAtIso = now)
        val xp = 10 + if (completedBeforeDue(now, task.dueIso)) 5 else 0
        val next = state.copy(tasks = state.tasks.map { if (it.id == updated.id) updated else it })
            .awardXp(xp)
        return ok(next, "Done: ${updated.title}", ChangeKind.TASK, updated.id)
    }

    private fun createEvent(state: CanonicalLifeState, action: Action.CreateEvent, origin: ActionOrigin): Step {
        val title = action.title.trim()
        if (title.isEmpty()) return skip(state, "create_event", "missing title")
        val start = parseDateOrNull(action.startIso)
            ?: return skip(state, "create_event", "unparseable date")
        val event = Event(
            id = action.id?.takeIf { it.isNotBlank() } ?: Ids.new("event"),
            title = title,
            startIso = start,
            endIso = parseDateOrNull(action.endIso),
            hardness = action.hardness,
            source = if (origin == ActionOrigin.EMAIL) EntitySource.EMAIL else EntitySource.AGENT,
            emailId = action.emailId,
            sourceGoalId = action.sourceGoalId,
        )
        return ok(state.copy(events = state.events + event), "Event: $title", ChangeKind.EVENT, event.id)
    }

    private fun createHabit(
        state: CanonicalLifeState,
        action: Action.CreateHabit,
        sideEffects: MutableList<() -> Unit>,
    ): Step {
        val title = action.title.trim()
        if (title.isEmpty()) return skip(state, "create_habit", "missing title")
        val days = action.daysOfWeek.filter { it in 1..7 }.distinct().sorted()
            .ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }
        val habit = Habit(
            id = action.id?.takeIf { it.isNotBlank() } ?: Ids.new("habit"),
            title = title,
            daysOfWeek = days,
            timeHhmm = action.timeHhmm,
            remindMinutesBefore = action.remindMinutesBefore,
            sourceGoalId = action.sourceGoalId,
        )
        var next = state.copy(habits = state.habits + habit)
        val remind = action.remindMinutesBefore
        if (remind != null) {
            val remindHhmm = minusMinutesHhmm(action.timeHhmm, remind)
            val spec = AlarmSpec(
                id = Ids.new("alarm"),
                label = "habit:${habit.id}",
                timeHhmm = remindHhmm,
                triggerAtEpochMs = Time.nextOccurrenceEpochMs(remindHhmm),
                sourceGoalId = action.sourceGoalId,
            )
            next = next.copy(alarms = next.alarms + spec)
            sideEffects += { enforce.scheduleAlarm(spec) }
        }
        return ok(next, "Habit: $title", ChangeKind.HABIT, habit.id)
    }

    private fun completeHabitToday(state: CanonicalLifeState, action: Action.CompleteHabitToday): Step {
        val habit = matchHabit(state, action.id, action.title)
            ?: return skip(state, "complete_habit_today", "unknown habit")
        val today = Time.todayIso()
        if (today in habit.completedDates) return skip(state, "complete_habit_today", "already done")
        val updated = habit.copy(completedDates = habit.completedDates + today)
        val next = state.copy(habits = state.habits.map { if (it.id == updated.id) updated else it })
            .awardXp(5)
        return ok(next, "Done: ${updated.title}", ChangeKind.HABIT, updated.id)
    }

    private fun addScheduleBlock(state: CanonicalLifeState, action: Action.AddScheduleBlock): Step {
        val title = action.title.trim()
        if (title.isEmpty()) return skip(state, "add_schedule_block", "missing title")
        val days = action.daysOfWeek.filter { it in 1..7 }.distinct().sorted()
        val dateIso = when {
            days.isEmpty() && action.dateIso == null -> Time.todayIso()
            else -> parseDateOrNull(action.dateIso)?.take(10)
        }
        val block = ScheduleBlock(
            id = action.id?.takeIf { it.isNotBlank() } ?: Ids.new("block"),
            title = title,
            startHhmm = action.startHhmm,
            endHhmm = action.endHhmm,
            kind = action.kind,
            daysOfWeek = days,
            dateIso = dateIso,
            sourceGoalId = action.sourceGoalId,
        )
        return ok(state.copy(scheduleBlocks = state.scheduleBlocks + block), "Block: $title", ChangeKind.BLOCK, block.id)
    }

    private fun remember(state: CanonicalLifeState, action: Action.Remember): Step {
        val fact = action.fact.trim()
        if (fact.isEmpty()) return skip(state, "remember", "missing title")
        val exists = state.memoryFacts.any { it.equals(fact, ignoreCase = true) }
        val facts = if (exists) state.memoryFacts else (state.memoryFacts + fact).takeLast(40)
        return ok(state.copy(memoryFacts = facts), "Remembered: $fact", ChangeKind.MEMORY)
    }

    private fun setPersona(state: CanonicalLifeState, action: Action.SetPersona): Step {
        val persona = Personas.byId(action.personaId)
        return ok(state.copy(personaId = persona.id), "Persona: ${persona.name}", ChangeKind.PERSONA)
    }

    private fun setAlarm(
        state: CanonicalLifeState,
        action: Action.SetAlarm,
        sideEffects: MutableList<() -> Unit>,
    ): Step {
        val id = action.id?.takeIf { it.isNotBlank() } ?: Ids.new("alarm")
        val label = action.label.trim().ifEmpty { action.timeHhmm }
        val spec = AlarmSpec(
            id = id,
            label = label,
            timeHhmm = action.timeHhmm,
            triggerAtEpochMs = action.triggerAtEpochMs ?: Time.nextOccurrenceEpochMs(action.timeHhmm),
            personaLine = action.personaLine,
            sourceGoalId = action.sourceGoalId,
        )
        val alarms = state.alarms.filter { it.id != id } + spec
        sideEffects += { enforce.scheduleAlarm(spec) }
        return ok(state.copy(alarms = alarms), "Alarm: $label", ChangeKind.ALARM, spec.id)
    }

    private fun cancelAlarm(
        state: CanonicalLifeState,
        action: Action.CancelAlarm,
        sideEffects: MutableList<() -> Unit>,
    ): Step {
        val matches = state.alarms.filter { alarm ->
            (action.id != null && alarm.id == action.id) ||
                (action.label != null && alarm.label.equals(action.label, ignoreCase = true))
        }
        if (matches.isEmpty()) return skip(state, "cancel_alarm", "unknown alarm")
        val removeIds = matches.map { it.id }.toSet()
        matches.forEach { spec -> sideEffects += { enforce.cancelAlarm(spec.id) } }
        return ok(
            state.copy(alarms = state.alarms.filter { it.id !in removeIds }),
            "Cancelled: ${matches.first().label}",
            ChangeKind.ALARM,
            matches.first().id,
        )
    }

    private suspend fun setAppTimeout(state: CanonicalLifeState, action: Action.SetAppTimeout): Step {
        if (action.limitMinutes <= 0) return skip(state, "set_app_timeout", "limitMinutes <= 0")
        val pkg = resolvePackage(action.packageName, dropAlwaysAllow = true)
            ?: return Step(state)
        // Substitution can collapse two requested apps onto one installed package, so a
        // later cap in the same expansion must never loosen the one already applied.
        val existing = state.appTimeouts.firstOrNull { it.packageName == pkg }
        if (existing != null &&
            existing.sourceGoalId != null &&
            existing.sourceGoalId == action.sourceGoalId &&
            existing.limitMinutes <= action.limitMinutes
        ) {
            return Step(state)
        }
        val timeout = AppTimeout(pkg, action.limitMinutes, action.sourceGoalId)
        val timeouts = state.appTimeouts.filter { it.packageName != pkg } + timeout
        return ok(
            state.copy(appTimeouts = timeouts),
            "Timeout: ${packageLabel(pkg)} ${action.limitMinutes}m",
            ChangeKind.TIMEOUT,
            pkg,
            needsApplyRules = true,
        )
    }

    private suspend fun clearAppTimeout(state: CanonicalLifeState, action: Action.ClearAppTimeout): Step {
        val pkg = action.packageName?.let { resolvePackage(it, dropAlwaysAllow = false) }
        if (pkg == null && action.sourceGoalId == null) {
            return skip(state, "clear_app_timeout", "missing package")
        }
        val remaining = state.appTimeouts.filterNot { timeout ->
            (pkg != null && timeout.packageName == pkg) ||
                (action.sourceGoalId != null && timeout.sourceGoalId == action.sourceGoalId)
        }
        return ok(
            state.copy(appTimeouts = remaining),
            "Cleared timeout",
            ChangeKind.TIMEOUT,
            pkg,
            needsApplyRules = true,
        )
    }

    private suspend fun focusStart(
        state: CanonicalLifeState,
        action: Action.FocusStart,
        sideEffects: MutableList<() -> Unit>,
    ): Step {
        val mode = action.mode ?: state.focus.mode
        val requested = action.packages
        val packages = if (requested != null) {
            resolvePackages(requested, dropAlwaysAllow = mode == FocusMode.BLACKLIST)
        } else {
            state.focus.packages
        }
        val now = Time.nowEpochMs()
        val ends = action.minutes?.let { now + it.toLong() * 60_000L }
        val next = state.copy(
            focus = state.focus.copy(
                active = true,
                mode = mode,
                packages = packages,
                startedAtEpochMs = now,
                endsAtEpochMs = ends,
            ),
        )
        sideEffects += { enforce.startFocus(FocusSession(mode, packages, ends)) }
        return ok(next, "Focus on", ChangeKind.FOCUS, needsApplyRules = true)
    }

    private fun focusStop(state: CanonicalLifeState, sideEffects: MutableList<() -> Unit>): Step {
        val started = state.focus.startedAtEpochMs
        val elapsed = if (started != null) Time.nowEpochMs() - started else 0L
        var next = state.copy(
            focus = state.focus.copy(
                active = false,
                startedAtEpochMs = null,
                endsAtEpochMs = null,
            ),
        )
        if (elapsed >= 10 * 60_000L) next = next.awardXp(15)
        sideEffects += { enforce.stopFocus() }
        return ok(next, "Focus off", ChangeKind.FOCUS, needsApplyRules = true)
    }

    private suspend fun focusSetApps(state: CanonicalLifeState, action: Action.FocusSetApps): Step {
        val packages = resolvePackages(action.packages, dropAlwaysAllow = action.mode == FocusMode.BLACKLIST)
        val next = state.copy(focus = state.focus.copy(mode = action.mode, packages = packages))
        return ok(next, "Focus apps", ChangeKind.FOCUS, needsApplyRules = true)
    }

    private suspend fun setFocusWindows(state: CanonicalLifeState, action: Action.SetFocusWindows): Step {
        val windows = action.windows.map { window ->
            window.copy(
                packages = resolvePackages(
                    window.packages,
                    dropAlwaysAllow = window.mode == FocusMode.BLACKLIST,
                ),
                sourceGoalId = window.sourceGoalId ?: action.sourceGoalId,
            )
        }
        val next = state.copy(focus = state.focus.copy(windows = windows))
        return ok(next, "Focus windows", ChangeKind.FOCUS, needsApplyRules = true)
    }

    private fun networkSetMode(
        state: CanonicalLifeState,
        action: Action.NetworkSetMode,
        sideEffects: MutableList<() -> Unit>,
    ): Step {
        val next = state.copy(network = state.network.copy(mode = action.mode))
        sideEffects += {
            if (action.mode == NetworkMode.OFF) enforce.stopNetworkGuard()
            else enforce.startNetworkGuard(store.state.value.network)
        }
        return ok(next, "Network: ${action.mode.name}", ChangeKind.NETWORK)
    }

    private suspend fun networkSetApps(
        state: CanonicalLifeState,
        action: Action.NetworkSetApps,
        sideEffects: MutableList<() -> Unit>,
    ): Step {
        val drop = state.network.mode == NetworkMode.BLACKLIST
        val packages = resolvePackages(action.packages, dropAlwaysAllow = drop)
        val next = state.copy(network = state.network.copy(packages = packages))
        if (next.network.mode != NetworkMode.OFF) {
            sideEffects += { enforce.startNetworkGuard(store.state.value.network) }
        }
        return ok(next, "Network apps", ChangeKind.NETWORK)
    }

    private fun promoteEmail(state: CanonicalLifeState, action: Action.PromoteEmail): Step {
        val candidate = state.emailCandidates.firstOrNull { it.id == action.candidateId }
            ?: return skip(state, "promote_email", "unknown candidate")
        val title = (action.titleOverride ?: candidate.proposedTitle).trim()
        if (title.isEmpty()) return skip(state, "promote_email", "missing title")
        val start = parseDateOrNull(action.startIsoOverride)
            ?: parseDateOrNull(candidate.proposedStartIso)
            ?: return skip(state, "promote_email", "unparseable date")
        val event = Event(
            id = Ids.new("event"),
            title = title,
            startIso = start,
            endIso = parseDateOrNull(candidate.proposedEndIso),
            source = EntitySource.EMAIL,
            emailId = candidate.messageId,
        )
        val candidates = state.emailCandidates.map {
            if (it.id == candidate.id) it.copy(status = CandidateStatus.PROMOTED) else it
        }
        return ok(
            state.copy(emailCandidates = candidates, events = state.events + event),
            "Scheduled: $title",
            ChangeKind.EMAIL,
            event.id,
        )
    }

    private fun dismissEmail(state: CanonicalLifeState, action: Action.DismissEmail): Step {
        val candidate = state.emailCandidates.firstOrNull { it.id == action.candidateId }
            ?: return skip(state, "dismiss_email", "unknown candidate")
        val candidates = state.emailCandidates.map {
            if (it.id == candidate.id) it.copy(status = CandidateStatus.DISMISSED) else it
        }
        return ok(state.copy(emailCandidates = candidates), "Dismissed: ${candidate.subject}", ChangeKind.EMAIL, candidate.id)
    }

    private fun revertExpansion(
        state: CanonicalLifeState,
        action: Action.RevertExpansion,
        sideEffects: MutableList<() -> Unit>,
    ): Step {
        val gid = action.goalId
        val removedAlarms = state.alarms.filter { it.sourceGoalId == gid }
        removedAlarms.forEach { spec -> sideEffects += { enforce.cancelAlarm(spec.id) } }
        val removedCount =
            state.tasks.count { it.sourceGoalId == gid } +
                state.habits.count { it.sourceGoalId == gid } +
                state.scheduleBlocks.count { it.sourceGoalId == gid } +
                removedAlarms.size +
                state.appTimeouts.count { it.sourceGoalId == gid } +
                state.focus.windows.count { it.sourceGoalId == gid }
        val next = state.copy(
            goals = state.goals.map { if (it.id == gid) it.copy(archived = true) else it },
            tasks = state.tasks.filterNot { it.sourceGoalId == gid },
            habits = state.habits.filterNot { it.sourceGoalId == gid },
            scheduleBlocks = state.scheduleBlocks.filterNot { it.sourceGoalId == gid },
            alarms = state.alarms.filterNot { it.sourceGoalId == gid },
            appTimeouts = state.appTimeouts.filterNot { it.sourceGoalId == gid },
            focus = state.focus.copy(windows = state.focus.windows.filterNot { it.sourceGoalId == gid }),
        )
        return ok(next, "Reverted $removedCount items", ChangeKind.REVERT, gid, needsApplyRules = true)
    }

    private fun awardXpAction(state: CanonicalLifeState, action: Action.AwardXp): Step {
        return ok(state.awardXp(action.amount), "XP: ${action.amount}", ChangeKind.XP)
    }

    private suspend fun resolvePackage(nameOrPackage: String, dropAlwaysAllow: Boolean): String? {
        val trimmed = nameOrPackage.trim()
        if (trimmed.isEmpty()) return null
        val aliased = DemoPackages.ALIASES[trimmed.lowercase()] ?: trimmed
        if (dropAlwaysAllow && aliased in DemoPackages.ALWAYS_ALLOW) return null
        val resolved = apps.resolveOrSubstitute(aliased)
        val stored = when {
            resolved == null -> aliased
            resolved != aliased -> {
                LifeOsLog.d("LifeOS/Exec", "substitute $aliased -> $resolved")
                resolved
            }
            else -> resolved
        }
        if (dropAlwaysAllow && stored in DemoPackages.ALWAYS_ALLOW) return null
        return stored
    }

    private suspend fun resolvePackages(names: List<String>, dropAlwaysAllow: Boolean): List<String> =
        names.mapNotNull { resolvePackage(it, dropAlwaysAllow) }.distinct()

    private fun matchTask(state: CanonicalLifeState, id: String?, title: String?): Todo? {
        if (id != null) state.tasks.firstOrNull { it.id == id }?.let { return it }
        val q = title?.trim().orEmpty()
        if (q.isEmpty()) return null
        return state.tasks.filter { it.title.contains(q, ignoreCase = true) }
            .minByOrNull { it.title.length }
    }

    private fun matchHabit(state: CanonicalLifeState, id: String?, title: String?): Habit? {
        if (id != null) state.habits.firstOrNull { it.id == id }?.let { return it }
        val q = title?.trim().orEmpty()
        if (q.isEmpty()) return null
        return state.habits.filter { it.title.contains(q, ignoreCase = true) }
            .minByOrNull { it.title.length }
    }

    private fun parseDateOrNull(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return if (Time.parseIsoOrNull(trimmed) != null) trimmed else null
    }

    private fun completedBeforeDue(completedIso: String, dueIso: String?): Boolean {
        val due = Time.parseIsoOrNull(dueIso) ?: return false
        val done = Time.parseIsoOrNull(completedIso) ?: return false
        return done.isBefore(due)
    }

    private fun CanonicalLifeState.awardXp(amount: Int): CanonicalLifeState {
        val today = Time.todayIso()
        val yesterday = Time.plusDaysIso(today, -1)
        val g = gamification
        val streak = when (g.lastActiveDateIso) {
            yesterday -> g.streakDays + 1
            today -> g.streakDays
            else -> 1
        }
        return copy(
            gamification = g.copy(
                xp = g.xp + amount,
                streakDays = streak,
                lastActiveDateIso = today,
            ),
        )
    }

    private fun buildRules(state: CanonicalLifeState): EnforcementRules {
        val nearest = state.goals
            .filter { !it.archived && it.hardness == Hardness.HARD }
            .mapNotNull { goal ->
                val deadline = Time.parseIsoOrNull(goal.deadlineIso) ?: return@mapNotNull null
                goal to deadline
            }
            .minByOrNull { it.second }
            ?.first
        return EnforcementRules(
            focus = state.focus,
            timeouts = state.appTimeouts,
            demoStrictTimeouts = state.settings.demoStrictTimeouts,
            activeGoalLabel = nearest?.title,
            activeGoalDeadlineIso = nearest?.deadlineIso,
        )
    }

    private fun Action.wireType(): String = when (this) {
        is Action.CreateGoal -> "create_goal"
        is Action.UpdateGoal -> "update_goal"
        is Action.ArchiveGoal -> "archive_goal"
        is Action.CreateTask -> "create_task"
        is Action.CompleteTask -> "complete_task"
        is Action.CreateEvent -> "create_event"
        is Action.CreateHabit -> "create_habit"
        is Action.CompleteHabitToday -> "complete_habit_today"
        is Action.AddScheduleBlock -> "add_schedule_block"
        is Action.Remember -> "remember"
        is Action.SetPersona -> "set_persona"
        is Action.SetAlarm -> "set_alarm"
        is Action.CancelAlarm -> "cancel_alarm"
        is Action.SetAppTimeout -> "set_app_timeout"
        is Action.ClearAppTimeout -> "clear_app_timeout"
        is Action.FocusStart -> "focus_start"
        is Action.FocusStop -> "focus_stop"
        is Action.FocusSetApps -> "focus_set_apps"
        is Action.SetFocusWindows -> "set_focus_windows"
        is Action.NetworkSetMode -> "network_set_mode"
        is Action.NetworkSetApps -> "network_set_apps"
        is Action.PromoteEmail -> "promote_email"
        is Action.DismissEmail -> "dismiss_email"
        is Action.RevertExpansion -> "revert_expansion"
        is Action.AwardXp -> "award_xp"
    }

    companion object {
        private val hhmmFmt = DateTimeFormatter.ofPattern("HH:mm")

        internal fun packageLabel(pkg: String): String = when (pkg) {
            DemoPackages.INSTAGRAM -> "Instagram"
            DemoPackages.YOUTUBE -> "YouTube"
            DemoPackages.CHROME -> "Chrome"
            DemoPackages.DOCS -> "Docs"
            DemoPackages.MAPS -> "Maps"
            else -> {
                val alias = DemoPackages.ALIASES.entries.firstOrNull { it.value == pkg }?.key
                alias?.split(' ')?.joinToString(" ") { part ->
                    part.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                } ?: pkg.substringAfterLast('.').replaceFirstChar { it.titlecase() }
            }
        }

        private fun minusMinutesHhmm(hhmm: String, minutes: Int): String {
            val parsed = Time.parseHhmm(hhmm) ?: return hhmm
            return parsed.minusMinutes(minutes.toLong()).format(hhmmFmt)
        }
    }
}
