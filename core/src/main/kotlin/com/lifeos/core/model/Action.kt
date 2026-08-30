package com.lifeos.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Action {
    @Serializable
    @SerialName("create_goal")
    data class CreateGoal(
        val id: String? = null,
        val title: String,
        val deadlineIso: String? = null,
        val hardness: Hardness = Hardness.SOFT,
        val notes: String = "",
    ) : Action

    @Serializable
    @SerialName("update_goal")
    data class UpdateGoal(
        val id: String,
        val title: String? = null,
        val deadlineIso: String? = null,
        val hardness: Hardness? = null,
        val notes: String? = null,
    ) : Action

    @Serializable
    @SerialName("archive_goal")
    data class ArchiveGoal(val id: String) : Action

    @Serializable
    @SerialName("create_task")
    data class CreateTask(
        val id: String? = null,
        val title: String,
        val goalId: String? = null,
        val dueIso: String? = null,
        val estMinutes: Int = 30,
        val sourceGoalId: String? = null,
    ) : Action

    @Serializable
    @SerialName("complete_task")
    data class CompleteTask(
        val id: String? = null,
        val title: String? = null,
    ) : Action

    @Serializable
    @SerialName("create_event")
    data class CreateEvent(
        val id: String? = null,
        val title: String,
        val startIso: String,
        val endIso: String? = null,
        val hardness: Hardness = Hardness.HARD,
        val emailId: String? = null,
        val sourceGoalId: String? = null,
    ) : Action

    @Serializable
    @SerialName("create_habit")
    data class CreateHabit(
        val id: String? = null,
        val title: String,
        val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
        val timeHhmm: String = "19:00",
        val remindMinutesBefore: Int? = null,
        val sourceGoalId: String? = null,
    ) : Action

    @Serializable
    @SerialName("complete_habit_today")
    data class CompleteHabitToday(
        val id: String? = null,
        val title: String? = null,
    ) : Action

    @Serializable
    @SerialName("add_schedule_block")
    data class AddScheduleBlock(
        val id: String? = null,
        val title: String,
        val startHhmm: String,
        val endHhmm: String,
        val kind: BlockKind = BlockKind.OTHER,
        val daysOfWeek: List<Int> = emptyList(),
        val dateIso: String? = null,
        val sourceGoalId: String? = null,
    ) : Action

    @Serializable
    @SerialName("remember")
    data class Remember(val fact: String) : Action

    @Serializable
    @SerialName("set_persona")
    data class SetPersona(val personaId: String) : Action

    @Serializable
    @SerialName("set_alarm")
    data class SetAlarm(
        val id: String? = null,
        val label: String = "",
        val timeHhmm: String,
        val personaLine: String = "",
        val triggerAtEpochMs: Long? = null,
        val sourceGoalId: String? = null,
    ) : Action

    @Serializable
    @SerialName("cancel_alarm")
    data class CancelAlarm(
        val id: String? = null,
        val label: String? = null,
    ) : Action

    @Serializable
    @SerialName("set_app_timeout")
    data class SetAppTimeout(
        val packageName: String,
        val limitMinutes: Int,
        val sourceGoalId: String? = null,
    ) : Action

    @Serializable
    @SerialName("clear_app_timeout")
    data class ClearAppTimeout(
        val packageName: String? = null,
        val sourceGoalId: String? = null,
    ) : Action

    @Serializable
    @SerialName("focus_start")
    data class FocusStart(
        val mode: FocusMode? = null,
        val packages: List<String>? = null,
        val minutes: Int? = null,
    ) : Action

    @Serializable
    @SerialName("focus_stop")
    data object FocusStop : Action

    @Serializable
    @SerialName("focus_set_apps")
    data class FocusSetApps(
        val mode: FocusMode,
        val packages: List<String>,
    ) : Action

    @Serializable
    @SerialName("set_focus_windows")
    data class SetFocusWindows(
        val windows: List<FocusWindow>,
        val sourceGoalId: String? = null,
    ) : Action

    @Serializable
    @SerialName("network_set_mode")
    data class NetworkSetMode(val mode: NetworkMode) : Action

    @Serializable
    @SerialName("network_set_apps")
    data class NetworkSetApps(val packages: List<String>) : Action

    @Serializable
    @SerialName("promote_email")
    data class PromoteEmail(
        val candidateId: String,
        val titleOverride: String? = null,
        val startIsoOverride: String? = null,
    ) : Action

    @Serializable
    @SerialName("dismiss_email")
    data class DismissEmail(val candidateId: String) : Action

    @Serializable
    @SerialName("revert_expansion")
    data class RevertExpansion(val goalId: String) : Action

    @Serializable
    @SerialName("award_xp")
    data class AwardXp(
        val amount: Int,
        val reason: String = "",
    ) : Action
}
