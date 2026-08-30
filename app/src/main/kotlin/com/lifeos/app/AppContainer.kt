package com.lifeos.app

import android.app.Application
import com.lifeos.agent.AgentController
import com.lifeos.agent.AzureFoundryClient
import com.lifeos.calendar.CalendarPortImpl
import com.lifeos.core.ActionExecutorPort
import com.lifeos.core.AgentPort
import com.lifeos.core.AppCatalog
import com.lifeos.core.CalendarPort
import com.lifeos.core.ChatStore
import com.lifeos.core.CompactorPort
import com.lifeos.core.EmailClassifierPort
import com.lifeos.core.EnforceGateway
import com.lifeos.core.LifeOsLog
import com.lifeos.core.LifeStateStore
import com.lifeos.core.MailIngest
import com.lifeos.core.MailSender
import com.lifeos.core.MailboxSync
import com.lifeos.core.Ports
import com.lifeos.core.ProjectionPort
import com.lifeos.core.RiskPort
import com.lifeos.core.SecretsStore
import com.lifeos.core.SystemAccess
import com.lifeos.core.TimelinePort
import com.lifeos.core.model.LlmConfig
import com.lifeos.core.model.MailKind
import com.lifeos.data.DataStoreChatStore
import com.lifeos.data.DataStoreLifeStateStore
import com.lifeos.data.EncryptedSecretsStore
import com.lifeos.domain.ActionExecutor
import com.lifeos.domain.Compactor
import com.lifeos.domain.ProjectionBuilder
import com.lifeos.domain.RiskCalculator
import com.lifeos.domain.TimelineMerger
import com.lifeos.email.CompositeMailSender
import com.lifeos.email.CompositeMailboxSync
import com.lifeos.email.EmailClassifier
import com.lifeos.email.ImapMailboxSync
import com.lifeos.email.SmtpMailSender
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
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

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

    val calendarPort = CalendarPortImpl(app)
    override val calendar: CalendarPort = calendarPort

    override val executor: ActionExecutorPort =
        ActionExecutor(lifeState, enforce, apps, calendar = calendarPort)
    // Cached so the agent's screen-time totals match the launchable-app list the Focus tab shows.
    private val launchablePackages = AtomicReference<Set<String>>(emptySet())

    override val projection: ProjectionPort = ProjectionBuilder(
        usageToday = {
            val all = enforce.usageTodayAll() - app.packageName
            val launchable = launchablePackages.get()
            if (launchable.isEmpty()) all else all.filterKeys { it in launchable }
        },
    )
    override val timeline: TimelinePort = TimelineMerger()
    override val risk: RiskPort = RiskCalculator()
    override val compactor: CompactorPort = Compactor(chat, maxMessages = 12)

    override val isDebug: Boolean = BuildConfig.DEBUG

    private val llmConfig = LlmConfig(
        BuildConfig.AZURE_LLM_ENDPOINT,
        BuildConfig.AZURE_LLM_DEPLOYMENT,
        BuildConfig.AZURE_LLM_API_KEY,
        BuildConfig.AZURE_LLM_API_VERSION,
    )
    override val secrets: SecretsStore = EncryptedSecretsStore(app, llmConfig)

    override val mailbox: MailboxSync = CompositeMailboxSync(
        imap = ImapMailboxSync(secrets),
        gmail = null,
        accounts = { lifeState.state.value.mailAccounts },
    )
    override val classifier: EmailClassifierPort = EmailClassifier()
    override val mailSender: MailSender = CompositeMailSender(smtp = SmtpMailSender(secrets))

    override val agent: AgentPort = AgentController(
        chat,
        lifeState,
        executor,
        projection,
        compactor,
        llm = if (llmConfig.usable) AzureFoundryClient(llmConfig) else null,
        refreshInbox = ::refreshInbox,
    )

    private suspend fun refreshInbox() {
        val accounts = lifeState.state.value.mailAccounts.filter { it.kind != MailKind.SEED }
        if (accounts.isEmpty()) return
        val msgs = mailbox.fetch(null).getOrElse { return }
        val incoming = classifier.classify(msgs)
        lifeState.mutate { state -> MailIngest.merge(state, msgs, incoming) }
        LifeOsLog.d("LifeOS/Mail", "agent refresh +${incoming.size} msgs")
    }

    fun publish() {
        EnforceHolder.lifeState = lifeState
        EnforceHolder.alarms = alarmScheduler
        EnforceHolder.focus = focusController
        EnforceHolder.network = networkGuard
        UiPorts.value = this
        LifeOsLog.d("LifeOS/Agent", "LLM configured: ${llmConfig.usable}")
        scope.launch {
            launchablePackages.set(apps.launchableApps().map { it.packageName }.toSet())
        }
    }
}
