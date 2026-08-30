package com.lifeos.ui.screens.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.Persona
import com.lifeos.core.Personas
import com.lifeos.core.Ports
import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.ChatTranscript
import com.lifeos.core.model.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LifeStateCounts(
    val goals: Int = 0,
    val tasks: Int = 0,
    val events: Int = 0,
    val habits: Int = 0,
    val blocks: Int = 0,
    val alarms: Int = 0,
    val timeouts: Int = 0,
    val memoryFacts: Int = 0,
) {
    companion object
}

data class PermissionRowUi(
    val title: String,
    val granted: Boolean,
)

data class CompactProof(
    val chatBefore: Int,
    val chatAfter: Int,
    val lifeUnchanged: Boolean,
)

data class MoreUiState(
    val xp: Int = 0,
    val streakDays: Int = 0,
    val lifeCounts: LifeStateCounts = LifeStateCounts(),
    val chatMessages: Int = 0,
    val summaryLength: Int = 0,
    val compactProof: CompactProof? = null,
    val personas: List<Persona> = Personas.ALL,
    val personaId: String = "strict",
    val memoryFacts: List<String> = emptyList(),
    val permissions: List<PermissionRowUi> = emptyList(),
    val demoStrictTimeouts: Boolean = false,
)

class MoreViewModel(private val ports: Ports) : ViewModel() {
    private val permissions = MutableStateFlow(ports.system.permissions())
    private val compactProof = MutableStateFlow<CompactProof?>(null)

    val uiState: StateFlow<MoreUiState> = combine(
        ports.lifeState.state,
        ports.chat.transcript,
        permissions,
        compactProof,
    ) { state, transcript, perms, proof ->
        buildUi(state, transcript, perms, proof)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        buildUi(
            ports.lifeState.state.value,
            ports.chat.transcript.value,
            permissions.value,
            compactProof.value,
        ),
    )

    fun refreshPermissions() {
        permissions.value = ports.system.permissions()
    }

    fun compactChat() {
        viewModelScope.launch {
            val beforeLife = LifeStateCounts.from(ports.lifeState.state.value)
            val beforeChat = ports.chat.transcript.value.messages.size
            ports.compactor.ensureWindow()
            val afterLife = LifeStateCounts.from(ports.lifeState.state.value)
            val afterChat = ports.chat.transcript.value.messages.size
            compactProof.value = CompactProof(
                chatBefore = beforeChat,
                chatAfter = afterChat,
                lifeUnchanged = beforeLife == afterLife,
            )
        }
    }

    fun setPersona(personaId: String) {
        viewModelScope.launch {
            ports.executor.execute(listOf(Action.SetPersona(personaId = personaId)), ActionOrigin.USER)
        }
    }

    fun setDemoStrictTimeouts(on: Boolean) {
        viewModelScope.launch {
            ports.lifeState.mutate {
                it.copy(settings = it.settings.copy(demoStrictTimeouts = on))
            }
        }
    }

    fun testAlarm() {
        viewModelScope.launch {
            ports.executor.execute(
                listOf(
                    Action.SetAlarm(
                        label = "demo",
                        timeHhmm = "",
                        triggerAtEpochMs = Time.nowEpochMs() + 60_000,
                        personaLine = "Time's up. Back to work.",
                    ),
                ),
                ActionOrigin.USER,
            )
        }
    }

    fun resetDemo() {
        viewModelScope.launch {
            ports.lifeState.mutate { CanonicalLifeState() }
            ports.chat.mutate { ChatTranscript() }
            compactProof.value = null
        }
    }

    fun reviewPermissions() {
        viewModelScope.launch {
            ports.lifeState.mutate {
                it.copy(settings = it.settings.copy(onboardingComplete = false))
            }
        }
    }

    private fun buildUi(
        state: CanonicalLifeState,
        transcript: ChatTranscript,
        perms: PermissionStatus,
        proof: CompactProof?,
    ): MoreUiState = MoreUiState(
        xp = state.gamification.xp,
        streakDays = state.gamification.streakDays,
        lifeCounts = LifeStateCounts.from(state),
        chatMessages = transcript.messages.size,
        summaryLength = transcript.summary.length,
        compactProof = proof,
        personas = Personas.ALL,
        personaId = state.personaId,
        memoryFacts = state.memoryFacts.asReversed(),
        permissions = perms.toRows(),
        demoStrictTimeouts = state.settings.demoStrictTimeouts,
    )
}

internal fun LifeStateCounts.Companion.from(state: CanonicalLifeState) = LifeStateCounts(
    goals = state.goals.size,
    tasks = state.tasks.size,
    events = state.events.size,
    habits = state.habits.size,
    blocks = state.scheduleBlocks.size,
    alarms = state.alarms.size,
    timeouts = state.appTimeouts.size,
    memoryFacts = state.memoryFacts.size,
)

private fun PermissionStatus.toRows(): List<PermissionRowUi> = listOf(
    PermissionRowUi("Notifications", notifications),
    PermissionRowUi("Exact alarms", exactAlarms),
    PermissionRowUi("Usage access", usageAccess),
    PermissionRowUi("Overlay", overlay),
    PermissionRowUi("VPN", vpnConsented),
    PermissionRowUi("Full-screen intents", fullScreenIntent),
)
