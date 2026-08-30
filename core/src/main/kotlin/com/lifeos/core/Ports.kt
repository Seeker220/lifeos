package com.lifeos.core

import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.AgentTurnResult
import com.lifeos.core.model.AlarmSpec
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.ChatTranscript
import com.lifeos.core.model.EmailCandidate
import com.lifeos.core.model.EnforcementRules
import com.lifeos.core.model.ExecuteReport
import com.lifeos.core.model.FocusSession
import com.lifeos.core.model.InstalledApp
import com.lifeos.core.model.LifeStateProjection
import com.lifeos.core.model.LlmConfig
import com.lifeos.core.model.LlmRequest
import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.NetworkRules
import com.lifeos.core.model.PermissionStatus
import com.lifeos.core.model.RawMessage
import com.lifeos.core.model.TimelineItem
import kotlinx.coroutines.flow.StateFlow

interface LifeStateStore {
    val state: StateFlow<CanonicalLifeState>
    suspend fun mutate(block: (CanonicalLifeState) -> CanonicalLifeState)

    /** Suspends until persisted data has been read. In-memory stores are ready immediately. */
    suspend fun awaitLoaded() {}
}

interface ChatStore {
    val transcript: StateFlow<ChatTranscript>
    suspend fun mutate(block: (ChatTranscript) -> ChatTranscript)

    /** Suspends until persisted data has been read. In-memory stores are ready immediately. */
    suspend fun awaitLoaded() {}
}

interface SecretsStore {
    fun llmConfig(): LlmConfig?
}

interface ActionExecutorPort {
    suspend fun execute(actions: List<Action>, origin: ActionOrigin): ExecuteReport

    /**
     * Pushes the persisted state back onto the OS. Enforcement lives in services that die
     * with the process, so it must be re-established on every cold start.
     */
    suspend fun reapplyEnforcement()
}

interface ProjectionPort {
    fun build(state: CanonicalLifeState): LifeStateProjection
}

interface TimelinePort {
    fun forDate(state: CanonicalLifeState, dateIso: String): List<TimelineItem>
}

interface RiskPort {
    fun riskPercent(state: CanonicalLifeState, goalId: String): Int
}

interface CompactorPort {
    suspend fun ensureWindow()
}

interface AgentPort {
    suspend fun send(userText: String): AgentTurnResult
}

interface LlmClient {
    suspend fun complete(req: LlmRequest): Result<String>
}

interface EnforceGateway {
    fun startFocus(session: FocusSession)
    fun stopFocus()
    fun applyRules(rules: EnforcementRules)
    fun scheduleAlarm(spec: AlarmSpec)
    fun cancelAlarm(alarmId: String)
    fun startNetworkGuard(rules: NetworkRules)
    fun stopNetworkGuard()
    fun usageTodayMinutes(packages: List<String>): Map<String, Int>
}

interface SystemAccess {
    fun permissions(): PermissionStatus
}

interface AppCatalog {
    suspend fun launchableApps(): List<InstalledApp>
    suspend fun resolveOrSubstitute(nameOrPackage: String): String?
}

interface MailboxSync {
    suspend fun fetch(account: MailAccount?): Result<List<RawMessage>>
}

interface EmailClassifierPort {
    suspend fun classify(messages: List<RawMessage>): List<EmailCandidate>
}

interface Ports {
    val lifeState: LifeStateStore
    val chat: ChatStore
    val executor: ActionExecutorPort
    val agent: AgentPort
    val projection: ProjectionPort
    val timeline: TimelinePort
    val risk: RiskPort
    val compactor: CompactorPort
    val enforce: EnforceGateway
    val system: SystemAccess
    val apps: AppCatalog
    val mailbox: MailboxSync
    val classifier: EmailClassifierPort

    /** Null until :calendar is wired. UI must guard. */
    val calendar: CalendarPort? get() = null
}
