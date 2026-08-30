package com.lifeos.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CanonicalLifeState(
    val schemaVersion: Int = 1,
    val personaId: String = "strict",
    val memoryFacts: List<String> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val tasks: List<Todo> = emptyList(),
    val events: List<Event> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val scheduleBlocks: List<ScheduleBlock> = emptyList(),
    val alarms: List<AlarmSpec> = emptyList(),
    val appTimeouts: List<AppTimeout> = emptyList(),
    val focus: FocusRules = FocusRules(),
    val network: NetworkRules = NetworkRules(),
    val mailAccounts: List<MailAccount> = emptyList(),
    val emailCandidates: List<EmailCandidate> = emptyList(),
    val settings: Settings = Settings(),
    val gamification: Gamification = Gamification(),
)

@Serializable
data class ChatTranscript(
    val messages: List<ChatMessage> = emptyList(),
    val summary: String = "",
)
