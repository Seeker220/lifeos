package com.lifeos.app

import android.app.Application
import com.lifeos.agent.AgentController
import com.lifeos.agent.AzureFoundryClient
import com.lifeos.core.ActionExecutorPort
import com.lifeos.core.AgentPort
import com.lifeos.core.AppCatalog
import com.lifeos.core.ChatStore
import com.lifeos.core.CompactorPort
import com.lifeos.core.EmailClassifierPort
import com.lifeos.core.EnforceGateway
import com.lifeos.core.LifeOsLog
import com.lifeos.core.LifeStateStore
import com.lifeos.core.MailboxSync
import com.lifeos.core.Ports
import com.lifeos.core.ProjectionPort
import com.lifeos.core.RiskPort
import com.lifeos.core.SystemAccess
import com.lifeos.core.TimelinePort
import com.lifeos.core.model.LlmConfig
import com.lifeos.data.DataStoreChatStore
import com.lifeos.data.DataStoreLifeStateStore
import com.lifeos.domain.ActionExecutor
import com.lifeos.domain.Compactor
import com.lifeos.domain.ProjectionBuilder
import com.lifeos.domain.RiskCalculator
import com.lifeos.domain.TimelineMerger
import com.lifeos.email.EmailClassifier
import com.lifeos.email.SeedMailboxSync
import com.lifeos.enforce.EnforceGatewayImpl
import com.lifeos.enforce.EnforceHolder
import com.lifeos.enforce.alarm.AlarmScheduler
import com.lifeos.enforce.focus.FocusController
import com.lifeos.enforce.system.AppCatalogImpl
import com.lifeos.enforce.system.SystemAccessImpl
import com.lifeos.enforce.vpn.NetworkGuardController
import com.lifeos.ui.UiPorts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(app: Application) : Ports {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val lifeState: LifeStateStore = DataStoreLifeStateStore(app, scope)
    override val chat: ChatStore = DataStoreChatStore(app, scope)

    override val system: SystemAccess = SystemAccessImpl(app)
    override val apps: AppCatalog = AppCatalogImpl(app)

    private val focusController = FocusController(app, lifeState)
    private val alarmScheduler = AlarmScheduler(app)
    private val networkGuard = NetworkGuardController(app)
    override val enforce: EnforceGateway =
        EnforceGatewayImpl(focusController, alarmScheduler, networkGuard)

    override val executor: ActionExecutorPort = ActionExecutor(lifeState, enforce, apps)
    override val projection: ProjectionPort = ProjectionBuilder()
    override val timeline: TimelinePort = TimelineMerger()
    override val risk: RiskPort = RiskCalculator()
    override val compactor: CompactorPort = Compactor(chat, maxMessages = 12)

    private val llmConfig = LlmConfig(
        BuildConfig.AZURE_LLM_ENDPOINT,
        BuildConfig.AZURE_LLM_DEPLOYMENT,
        BuildConfig.AZURE_LLM_API_KEY,
        BuildConfig.AZURE_LLM_API_VERSION,
    )
    override val agent: AgentPort = AgentController(
        chat,
        lifeState,
        executor,
        projection,
        compactor,
        llm = if (llmConfig.usable) AzureFoundryClient(llmConfig) else null,
    )

    override val mailbox: MailboxSync = SeedMailboxSync()
    override val classifier: EmailClassifierPort = EmailClassifier()

    fun publish() {
        EnforceHolder.lifeState = lifeState
        EnforceHolder.alarms = alarmScheduler
        EnforceHolder.focus = focusController
        EnforceHolder.network = networkGuard
        UiPorts.value = this
        LifeOsLog.d("LifeOS/Agent", "LLM configured: ${llmConfig.usable}")
    }
}
