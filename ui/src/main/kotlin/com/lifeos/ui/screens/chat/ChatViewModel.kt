package com.lifeos.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.LifeOsLog
import com.lifeos.core.Personas
import com.lifeos.core.Ports
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.AppliedChange
import com.lifeos.core.model.CandidateStatus
import com.lifeos.core.model.ChangeKind
import com.lifeos.core.model.ChatMessage
import com.lifeos.core.model.ChatRole
import com.lifeos.core.model.ExecuteReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val pendingEmailCount: Int = 0,
    val personaName: String = "Strict",
    val lastReport: ExecuteReport? = null,
    val lastExpansionGoalId: String? = null,
)

class ChatViewModel(private val ports: Ports) : ViewModel() {

    private val sending = MutableStateFlow(false)
    private val lastReport = MutableStateFlow<ExecuteReport?>(null)
    private val lastExpansionGoalId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ChatUiState> = combine(
        ports.chat.transcript,
        ports.lifeState.state,
        sending,
        lastReport,
        lastExpansionGoalId,
    ) { transcript, life, isSending, report, expansionId ->
        ChatUiState(
            messages = transcript.messages,
            sending = isSending,
            pendingEmailCount = life.emailCandidates.count { it.status == CandidateStatus.PENDING },
            personaName = Personas.byId(life.personaId).name,
            lastReport = report,
            lastExpansionGoalId = expansionId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = run {
            val life = ports.lifeState.state.value
            ChatUiState(
                messages = ports.chat.transcript.value.messages,
                pendingEmailCount = life.emailCandidates.count { it.status == CandidateStatus.PENDING },
                personaName = Personas.byId(life.personaId).name,
            )
        },
    )

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || sending.value) return
        sending.value = true
        viewModelScope.launch {
            try {
                runCatching { ports.agent.send(trimmed) }
                    .onSuccess { result ->
                        lastReport.value = result.report
                        lastExpansionGoalId.value = result.expansionGoalId
                    }
                    .onFailure { error ->
                        LifeOsLog.d("LifeOS/Agent", "send failed: ${error.message ?: "unknown"}")
                    }
            } finally {
                sending.value = false
            }
        }
    }

    fun undoExpansion(goalId: String) {
        if (goalId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                ports.executor.execute(
                    listOf(Action.RevertExpansion(goalId)),
                    ActionOrigin.USER,
                )
            }.onFailure { error ->
                LifeOsLog.d("LifeOS/Exec", "undo expansion failed: ${error.message ?: "unknown"}")
            }
        }
    }
}

internal fun ChatUiState.chipsFor(message: ChatMessage): List<AppliedChange> {
    val latestAssistantId = messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.id
    val report = lastReport
    if (message.id == latestAssistantId && report != null && report.applied.isNotEmpty()) {
        return report.applied
    }
    return message.appliedChips.map { label ->
        AppliedChange(label = label, kind = inferKindFromLabel(label), refId = null)
    }
}

internal fun ChatUiState.expansionIdFor(message: ChatMessage): String? {
    val latestAssistantId = messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.id
    return message.expansionGoalId
        ?: lastExpansionGoalId.takeIf { message.id == latestAssistantId }
}

internal fun inferKindFromLabel(label: String): ChangeKind {
    val t = label.trim()
    return when {
        t.startsWith("Goal:", ignoreCase = true) -> ChangeKind.GOAL
        t.startsWith("Timeout:", ignoreCase = true) -> ChangeKind.TIMEOUT
        t.startsWith("Done:", ignoreCase = true) -> ChangeKind.TASK
        t.startsWith("Task:", ignoreCase = true) -> ChangeKind.TASK
        t.startsWith("Todo:", ignoreCase = true) -> ChangeKind.TASK
        t.startsWith("Scheduled:", ignoreCase = true) -> ChangeKind.EVENT
        t.startsWith("Event:", ignoreCase = true) -> ChangeKind.EVENT
        t.startsWith("Habit:", ignoreCase = true) -> ChangeKind.HABIT
        t.startsWith("Block:", ignoreCase = true) -> ChangeKind.BLOCK
        t.startsWith("Schedule:", ignoreCase = true) -> ChangeKind.BLOCK
        t.startsWith("Alarm:", ignoreCase = true) -> ChangeKind.ALARM
        t.startsWith("Focus:", ignoreCase = true) -> ChangeKind.FOCUS
        t.startsWith("Network:", ignoreCase = true) -> ChangeKind.NETWORK
        t.startsWith("Persona:", ignoreCase = true) -> ChangeKind.PERSONA
        t.startsWith("XP:", ignoreCase = true) -> ChangeKind.XP
        t.startsWith("Email:", ignoreCase = true) -> ChangeKind.EMAIL
        t.startsWith("Reverted", ignoreCase = true) -> ChangeKind.REVERT
        t.startsWith("Remember", ignoreCase = true) -> ChangeKind.MEMORY
        t.startsWith("Memory:", ignoreCase = true) -> ChangeKind.MEMORY
        else -> ChangeKind.MEMORY
    }
}
