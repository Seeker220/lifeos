package com.lifeos.domain

import com.lifeos.core.AppCatalog
import com.lifeos.core.CalendarPort
import com.lifeos.core.ChatStore
import com.lifeos.core.DemoPackages
import com.lifeos.core.EnforceGateway
import com.lifeos.core.LifeStateStore
import com.lifeos.core.model.AlarmSpec
import com.lifeos.core.model.CalendarMirrorItem
import com.lifeos.core.model.CalendarPermissionStatus
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.ChatTranscript
import com.lifeos.core.model.EnforcementRules
import com.lifeos.core.model.ExternalEvent
import com.lifeos.core.model.FocusSession
import com.lifeos.core.model.InstalledApp
import com.lifeos.core.model.NetworkRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeLifeStateStore(
    initial: CanonicalLifeState = CanonicalLifeState(),
) : LifeStateStore {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<CanonicalLifeState> = _state

    var mutateCount: Int = 0
        private set

    override suspend fun mutate(block: (CanonicalLifeState) -> CanonicalLifeState) {
        mutex.withLock {
            mutateCount += 1
            _state.value = block(_state.value)
        }
    }
}

class FakeChatStore(
    initial: ChatTranscript = ChatTranscript(),
) : ChatStore {
    private val mutex = Mutex()
    private val _transcript = MutableStateFlow(initial)
    override val transcript: StateFlow<ChatTranscript> = _transcript

    override suspend fun mutate(block: (ChatTranscript) -> ChatTranscript) {
        mutex.withLock { _transcript.value = block(_transcript.value) }
    }
}

class FakeAppCatalog(
    private val installed: Set<String> = setOf(
        DemoPackages.YOUTUBE,
        DemoPackages.CHROME,
        DemoPackages.DOCS,
        DemoPackages.MAPS,
    ),
) : AppCatalog {
    override suspend fun launchableApps(): List<InstalledApp> =
        installed.map { InstalledApp(it, it.substringAfterLast('.')) }

    override suspend fun resolveOrSubstitute(nameOrPackage: String): String? {
        val raw = nameOrPackage.trim().lowercase()
        val pkg = DemoPackages.ALIASES[raw] ?: nameOrPackage.trim()
        if (pkg in installed) return pkg
        val sub = DemoPackages.SUBSTITUTES[pkg]
        if (sub != null && sub in installed) return sub
        return null
    }
}

class RecordingEnforceGateway(
    private val store: LifeStateStore? = null,
) : EnforceGateway {
    val calls = mutableListOf<String>()
    val scheduledAlarms = mutableListOf<AlarmSpec>()
    val cancelledAlarmIds = mutableListOf<String>()
    val startFocusSessions = mutableListOf<FocusSession>()
    val applyRulesCalls = mutableListOf<EnforcementRules>()
    var focusActiveAtStartFocus: Boolean? = null
        private set

    override fun startFocus(session: FocusSession) {
        focusActiveAtStartFocus = store?.state?.value?.focus?.active
        startFocusSessions += session
        calls += "startFocus"
    }

    override fun stopFocus() {
        calls += "stopFocus"
    }

    override fun applyRules(rules: EnforcementRules) {
        applyRulesCalls += rules
        calls += "applyRules"
    }

    override fun scheduleAlarm(spec: AlarmSpec) {
        scheduledAlarms += spec
        calls += "scheduleAlarm"
    }

    override fun cancelAlarm(alarmId: String) {
        cancelledAlarmIds += alarmId
        calls += "cancelAlarm"
    }

    override fun startNetworkGuard(rules: NetworkRules) {
        calls += "startNetworkGuard"
    }

    override fun stopNetworkGuard() {
        calls += "stopNetworkGuard"
    }

    override fun usageTodayMinutes(packages: List<String>): Map<String, Int> = emptyMap()

    override fun usageTodayAll(): Map<String, Int> = emptyMap()
}

class RecordingCalendarPort : CalendarPort {
    val upserts = mutableListOf<CalendarMirrorItem>()
    val deletes = mutableListOf<String>()
    var ensureCalls = 0
    var upsertCalls = 0
    var deleteCalls = 0

    override fun permissions() = CalendarPermissionStatus(read = true, write = true)

    override suspend fun ensureLifeOsCalendar(): Result<Long> {
        ensureCalls += 1
        return Result.success(1L)
    }

    override suspend fun upsert(items: List<CalendarMirrorItem>): Result<Int> {
        upsertCalls += 1
        upserts += items
        return Result.success(items.size)
    }

    override suspend fun delete(lifeOsIds: List<String>): Result<Int> {
        deleteCalls += 1
        deletes += lifeOsIds
        return Result.success(lifeOsIds.size)
    }

    override suspend fun readRange(startMs: Long, endMs: Long): Result<List<ExternalEvent>> =
        Result.success(emptyList())
}
