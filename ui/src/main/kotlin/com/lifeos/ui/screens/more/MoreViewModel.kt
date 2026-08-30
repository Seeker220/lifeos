package com.lifeos.ui.screens.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.Persona
import com.lifeos.core.Personas
import com.lifeos.core.Ports
import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.CalendarMirrorItem
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.ChatTranscript
import com.lifeos.core.model.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

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
    val lifeBefore: LifeStateCounts = LifeStateCounts(),
    val lifeAfter: LifeStateCounts = LifeStateCounts(),
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
    val chatWindowK: Int = 12,
    val autoScheduleHighConfidence: Boolean = false,
    val dynamicColor: Boolean = false,
    val hasCalendar: Boolean = false,
    val calendarSyncEnabled: Boolean = false,
    val calendarId: Long? = null,
    val calendarName: String = "Local only",
    val calendarStatus: String? = null,
    val calendarHint: String = "",
    val isDebug: Boolean = false,
    val googleAccountPresent: Boolean = false,
)

private data class MoreExtras(
    val compactProof: CompactProof? = null,
    val calendarName: String = "Local only",
    val calendarStatus: String? = null,
    val calendarHint: String = "",
    val googleAccountPresent: Boolean = false,
)

class MoreViewModel(private val ports: Ports) : ViewModel() {
    private val permissions = MutableStateFlow(ports.system.permissions())
    private val extras = MutableStateFlow(MoreExtras())
    private val hasCalendar: Boolean = ports.calendar != null

    init {
        refreshCalendarCopy()
    }

    val uiState: StateFlow<MoreUiState> = combine(
        ports.lifeState.state,
        ports.chat.transcript,
        permissions,
        extras,
    ) { state, transcript, perms, extra ->
        buildUi(state, transcript, perms, extra)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        buildUi(
            ports.lifeState.state.value,
            ports.chat.transcript.value,
            permissions.value,
            extras.value,
        ),
    )

    fun refreshPermissions() {
        permissions.value = ports.system.permissions()
        refreshCalendarCopy()
    }

    fun compactChat() {
        viewModelScope.launch {
            val beforeLife = LifeStateCounts.from(ports.lifeState.state.value)
            val beforeChat = ports.chat.transcript.value.messages.size
            ports.compactor.ensureWindow()
            val afterLife = LifeStateCounts.from(ports.lifeState.state.value)
            val afterChat = ports.chat.transcript.value.messages.size
            extras.update {
                it.copy(
                    compactProof = CompactProof(
                        chatBefore = beforeChat,
                        chatAfter = afterChat,
                        lifeUnchanged = beforeLife == afterLife,
                        lifeBefore = beforeLife,
                        lifeAfter = afterLife,
                    ),
                )
            }
        }
    }

    fun setPersona(personaId: String) {
        viewModelScope.launch {
            ports.executor.execute(listOf(Action.SetPersona(personaId = personaId)), ActionOrigin.USER)
        }
    }

    fun setDynamicColor(on: Boolean) {
        viewModelScope.launch {
            ports.lifeState.mutate {
                it.copy(settings = it.settings.copy(dynamicColor = on))
            }
        }
    }

    fun setCalendarSyncEnabled(on: Boolean) {
        viewModelScope.launch {
            ports.lifeState.mutate {
                it.copy(settings = it.settings.copy(calendarSyncEnabled = on))
            }
        }
    }

    fun setChatWindowK(compact: Boolean) {
        viewModelScope.launch {
            val k = if (compact) 12 else 40
            ports.lifeState.mutate {
                it.copy(settings = it.settings.copy(chatWindowK = k))
            }
        }
    }

    fun setAutoScheduleHighConfidence(on: Boolean) {
        viewModelScope.launch {
            ports.lifeState.mutate {
                it.copy(settings = it.settings.copy(autoScheduleHighConfidence = on))
            }
        }
    }

    fun setDemoStrictTimeouts(on: Boolean) {
        viewModelScope.launch {
            ports.lifeState.mutate {
                it.copy(settings = it.settings.copy(demoStrictTimeouts = on))
            }
        }
    }

    fun syncCalendar() {
        val cal = ports.calendar ?: return
        viewModelScope.launch {
            val googleId = cal.ensureGoogleCalendar().getOrNull()
            val id = googleId ?: cal.ensureLifeOsCalendar().getOrElse { err ->
                extras.update { it.copy(calendarStatus = err.message ?: "Could not create calendar") }
                return@launch
            }
            ports.lifeState.mutate {
                it.copy(settings = it.settings.copy(calendarId = id, calendarSyncEnabled = true))
            }
            val items = mirrorItems(ports.lifeState.state.value)
            val written = cal.upsert(items).getOrElse { err ->
                extras.update { it.copy(calendarStatus = err.message ?: "Sync failed") }
                return@launch
            }
            refreshCalendarCopy()
            extras.update {
                it.copy(calendarStatus = "Synced $written events")
            }
        }
    }

    private fun refreshCalendarCopy() {
        val google = ports.calendar?.googleAccountPresent() == true
        extras.update {
            it.copy(
                googleAccountPresent = google,
                calendarName = if (google) "Google Calendar (device account)" else "Local only",
                calendarHint = if (google) {
                    "Events sync to the Google Calendar account on this phone."
                } else {
                    "No Google account on this device — events stay in a local LifeOS calendar."
                },
            )
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
            extras.value = MoreExtras()
            refreshCalendarCopy()
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
        extra: MoreExtras,
    ): MoreUiState = MoreUiState(
        xp = state.gamification.xp,
        streakDays = state.gamification.streakDays,
        lifeCounts = LifeStateCounts.from(state),
        chatMessages = transcript.messages.size,
        summaryLength = transcript.summary.length,
        compactProof = extra.compactProof,
        personas = Personas.ALL,
        personaId = state.personaId,
        memoryFacts = state.memoryFacts.asReversed(),
        permissions = perms.toRows(),
        demoStrictTimeouts = state.settings.demoStrictTimeouts,
        chatWindowK = state.settings.chatWindowK,
        autoScheduleHighConfidence = state.settings.autoScheduleHighConfidence,
        dynamicColor = state.settings.dynamicColor,
        hasCalendar = hasCalendar,
        calendarSyncEnabled = state.settings.calendarSyncEnabled,
        calendarId = state.settings.calendarId,
        calendarName = extra.calendarName,
        calendarStatus = extra.calendarStatus,
        calendarHint = extra.calendarHint,
        isDebug = ports.isDebug,
        googleAccountPresent = extra.googleAccountPresent,
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

internal fun CompactProof.lostLine(live: LifeStateCounts): String {
    val before = lifeBefore.takeIf { it != LifeStateCounts() } ?: live
    val after = lifeAfter.takeIf { it != LifeStateCounts() } ?: live
    val goalsLost = if (lifeUnchanged) 0 else (before.goals - after.goals).coerceAtLeast(0)
    val capsLost = if (lifeUnchanged) 0 else (before.timeouts - after.timeouts).coerceAtLeast(0)
    return "$goalsLost goals lost · $capsLost caps lost"
}

private fun PermissionStatus.toRows(): List<PermissionRowUi> = listOf(
    PermissionRowUi("Notifications", notifications),
    PermissionRowUi("Exact alarms", exactAlarms),
    PermissionRowUi("Usage access", usageAccess),
    PermissionRowUi("Overlay", overlay),
    PermissionRowUi("VPN", vpnConsented),
    PermissionRowUi("Full-screen intents", fullScreenIntent),
)

private fun mirrorItems(state: CanonicalLifeState): List<CalendarMirrorItem> {
    val zone = ZoneId.systemDefault()
    return state.events.mapNotNull { event ->
        val start = Time.parseIsoOrNull(event.startIso)
            ?.atZone(zone)
            ?.toInstant()
            ?.toEpochMilli()
            ?: return@mapNotNull null
        val end = Time.parseIsoOrNull(event.endIso)
            ?.atZone(zone)
            ?.toInstant()
            ?.toEpochMilli()
            ?: (start + 3_600_000L)
        CalendarMirrorItem(
            lifeOsId = event.id,
            title = event.title,
            startEpochMs = start,
            endEpochMs = end,
        )
    }
}
