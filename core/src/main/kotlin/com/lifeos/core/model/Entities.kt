package com.lifeos.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Hardness { SOFT, HARD }

@Serializable
enum class EntitySource { USER, AGENT, EMAIL, SEED }

@Serializable
enum class BlockKind { STUDY, GYM, DEEP_WORK, OTHER }

@Serializable
enum class FocusMode { WHITELIST, BLACKLIST }

@Serializable
enum class NetworkMode { OFF, BLACKLIST, WHITELIST }

@Serializable
enum class ChatRole { USER, ASSISTANT, SYSTEM }

@Serializable
enum class MailKind { SEED, IMAP, GMAIL }

@Serializable
enum class CandidateKind { EXAM, DEADLINE, EVENT, NOISE }

@Serializable
enum class CandidateStatus { PENDING, PROMOTED, DISMISSED }

@Serializable
data class Goal(
    val id: String,
    val title: String,
    val deadlineIso: String? = null,
    val hardness: Hardness = Hardness.SOFT,
    val createdAtIso: String = "",
    val archived: Boolean = false,
    val notes: String = "",
)

@Serializable
data class Todo(
    val id: String,
    val title: String,
    val goalId: String? = null,
    val dueIso: String? = null,
    val estMinutes: Int = 30,
    val done: Boolean = false,
    val completedAtIso: String? = null,
    val sourceGoalId: String? = null,
)

@Serializable
data class Event(
    val id: String,
    val title: String,
    val startIso: String,
    val endIso: String? = null,
    val hardness: Hardness = Hardness.HARD,
    val source: EntitySource = EntitySource.USER,
    val emailId: String? = null,
    val sourceGoalId: String? = null,
)

/** daysOfWeek uses ISO numbering: 1 = Monday .. 7 = Sunday. */
@Serializable
data class Habit(
    val id: String,
    val title: String,
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val timeHhmm: String = "19:00",
    val remindMinutesBefore: Int? = null,
    val completedDates: List<String> = emptyList(),
    val sourceGoalId: String? = null,
)

/** Recurring when daysOfWeek is non-empty; one-off when dateIso is set. */
@Serializable
data class ScheduleBlock(
    val id: String,
    val title: String,
    val startHhmm: String,
    val endHhmm: String,
    val kind: BlockKind = BlockKind.OTHER,
    val daysOfWeek: List<Int> = emptyList(),
    val dateIso: String? = null,
    val sourceGoalId: String? = null,
)

/** triggerAtEpochMs null means "next occurrence of timeHhmm". */
@Serializable
data class AlarmSpec(
    val id: String,
    val label: String,
    val timeHhmm: String,
    val triggerAtEpochMs: Long? = null,
    val personaLine: String = "",
    val enabled: Boolean = true,
    val sourceGoalId: String? = null,
)

@Serializable
data class AppTimeout(
    val packageName: String,
    val limitMinutes: Int,
    val sourceGoalId: String? = null,
)

@Serializable
data class FocusWindow(
    val daysOfWeek: List<Int>,
    val startHhmm: String,
    val endHhmm: String,
    val mode: FocusMode,
    val packages: List<String>,
    val sourceGoalId: String? = null,
)

@Serializable
data class FocusRules(
    val active: Boolean = false,
    val mode: FocusMode = FocusMode.BLACKLIST,
    val packages: List<String> = emptyList(),
    val startedAtEpochMs: Long? = null,
    val endsAtEpochMs: Long? = null,
    val windows: List<FocusWindow> = emptyList(),
)

@Serializable
data class NetworkRules(
    val mode: NetworkMode = NetworkMode.OFF,
    val packages: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
)

@Serializable
data class Settings(
    val chatWindowK: Int = 12,
    val autoScheduleHighConfidence: Boolean = false,
    val demoStrictTimeouts: Boolean = false,
    val onboardingComplete: Boolean = false,
)

@Serializable
data class Gamification(
    val xp: Int = 0,
    val streakDays: Int = 0,
    val lastActiveDateIso: String? = null,
)

@Serializable
data class MailAccount(
    val id: String,
    val kind: MailKind = MailKind.SEED,
    val address: String = "",
    val host: String = "",
    val port: Int = 993,
)

@Serializable
data class RawMessage(
    val id: String,
    val from: String,
    val subject: String,
    val body: String,
    val receivedAtEpochMs: Long,
)

@Serializable
data class EmailCandidate(
    val id: String,
    val messageId: String,
    val from: String,
    val subject: String,
    val snippet: String,
    val confidence: Double,
    val kind: CandidateKind,
    val proposedTitle: String,
    val proposedStartIso: String? = null,
    val proposedEndIso: String? = null,
    val status: CandidateStatus = CandidateStatus.PENDING,
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val atEpochMs: Long,
    val appliedChips: List<String> = emptyList(),
    val expansionGoalId: String? = null,
)
