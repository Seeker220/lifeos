package com.lifeos.ui.screens.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.Ids
import com.lifeos.core.LifeOsLog
import com.lifeos.core.MailIngest
import com.lifeos.core.Ports
import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.CalendarMirrorItem
import com.lifeos.core.model.CandidateStatus
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.EmailCandidate
import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.MailKind
import com.lifeos.core.model.MailMessage
import com.lifeos.core.model.OutgoingMail
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxUiState(
    val accountLabel: String = "No sources",
    val connectedKinds: Set<MailKind> = emptySet(),
    val hasRealAccount: Boolean = false,
    val canSend: Boolean = false,
    val isDebug: Boolean = false,
    val candidates: List<EmailCandidate> = emptyList(),
    val messages: List<MailMessage> = emptyList(),
    val loading: Boolean = false,
    val sending: Boolean = false,
    val snackbar: String? = null,
    val snackbarAction: String? = null,
)

class InboxViewModel(val ports: Ports) : ViewModel() {
    private val loading = MutableStateFlow(false)
    private val sending = MutableStateFlow(false)
    private val snackbar = MutableStateFlow<String?>(null)
    private val snackbarAction = MutableStateFlow<String?>(null)

    private data class Transient(
        val loading: Boolean,
        val sending: Boolean,
        val snackbar: String?,
        val snackbarAction: String?,
    )

    val uiState: StateFlow<InboxUiState> = combine(
        ports.lifeState.state,
        combine(loading, sending, snackbar, snackbarAction, ::Transient),
    ) { state, transient ->
        val real = realAccounts(state)
        InboxUiState(
            accountLabel = accountLabel(state),
            connectedKinds = real.map { it.kind }.toSet(),
            hasRealAccount = real.isNotEmpty(),
            canSend = real.any { it.kind == MailKind.GMAIL || it.kind == MailKind.IMAP },
            isDebug = ports.isDebug,
            candidates = state.emailCandidates,
            messages = state.mailMessages,
            loading = transient.loading,
            sending = transient.sending,
            snackbar = transient.snackbar,
            snackbarAction = transient.snackbarAction,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState(isDebug = ports.isDebug))

    fun sync() {
        viewModelScope.launch {
            loading.value = true
            try {
                val result = ports.mailbox.fetch(null)
                val msgs = result.getOrElse { err ->
                    showMessage(err.message ?: "Sync failed")
                    LifeOsLog.d("LifeOS/Mail", "sync failed: ${err.message}")
                    return@launch
                }
                val incoming = ports.classifier.classify(msgs)
                ports.lifeState.mutate { state -> MailIngest.merge(state, msgs, incoming) }
            } catch (t: Throwable) {
                showMessage(t.message ?: "Sync failed")
                LifeOsLog.d("LifeOS/Mail", "sync failed: ${t.message}")
            } finally {
                loading.value = false
            }
        }
    }

    fun send(to: String, subject: String, body: String, replyToId: String? = null) {
        viewModelScope.launch {
            val account = realAccounts(ports.lifeState.state.value).firstOrNull {
                it.kind == MailKind.GMAIL || it.kind == MailKind.IMAP
            }
            if (account == null) {
                showMessage("Connect Gmail or IMAP before sending.")
                return@launch
            }
            if (to.isBlank()) {
                showMessage("Recipient is required.")
                return@launch
            }
            sending.value = true
            val mail = OutgoingMail(
                to = to.trim(),
                subject = subject.trim(),
                body = body,
                inReplyToId = replyToId,
            )
            ports.mailSender.send(account, mail).fold(
                onSuccess = {
                    ports.lifeState.mutate { state ->
                        state.copy(
                            sentMail = (
                                state.sentMail + MailMessage(
                                    id = Ids.new("sent"),
                                    accountId = account.id,
                                    from = account.address,
                                    to = mail.to,
                                    subject = mail.subject,
                                    body = mail.body,
                                    receivedAtEpochMs = Time.nowEpochMs(),
                                    read = true,
                                )
                                ).takeLast(50),
                        )
                    }
                    showMessage("Sent to ${mail.to}")
                },
                onFailure = { err ->
                    showMessage(err.message ?: "Send failed")
                    LifeOsLog.d("LifeOS/Mail", "send failed: ${err.message}")
                },
            )
            sending.value = false
        }
    }

    fun markRead(messageId: String) {
        viewModelScope.launch {
            ports.lifeState.mutate { state ->
                state.copy(
                    mailMessages = state.mailMessages.map {
                        if (it.id == messageId) it.copy(read = true) else it
                    },
                )
            }
        }
    }

    fun connect(
        kind: MailKind,
        address: String,
        password: String,
        host: String = "",
        port: Int = 993,
        username: String = "",
        smtpHost: String = "",
        smtpPort: Int = 587,
    ) {
        viewModelScope.launch {
            val email = address.trim()
            val secret = password
            val resolvedHost = if (kind == MailKind.GMAIL && host.isBlank()) "imap.gmail.com" else host.trim()
            val resolvedPort = if (kind == MailKind.GMAIL && host.isBlank()) 993 else port
            val user = username.trim().ifBlank { email }
            if (email.isBlank() || secret.isBlank()) {
                showMessage("Email and app password are required.")
                return@launch
            }
            if (kind == MailKind.IMAP && resolvedHost.isBlank()) {
                showMessage("IMAP host is required.")
                return@launch
            }
            val retiring = ports.lifeState.state.value.mailAccounts.filter { it.kind == kind }
            retiring.forEach { ports.secrets.deleteMailSecret(it.id) }
            val id = Ids.new("mail")
            ports.secrets.putMailSecret(id, secret)
            ports.lifeState.mutate { state ->
                val keep = state.mailAccounts.filter { it.kind != MailKind.SEED && it.kind != kind }
                state.copy(
                    mailAccounts = keep + MailAccount(
                        id = id,
                        kind = kind,
                        address = email,
                        host = resolvedHost,
                        port = resolvedPort,
                        username = user,
                        useSsl = true,
                        smtpHost = smtpHost.trim(),
                        smtpPort = smtpPort,
                    ),
                )
            }
            sync()
        }
    }

    fun addSource(kind: MailKind) {
        if (kind != MailKind.CODEFORCES && kind != MailKind.LEETCODE) return
        viewModelScope.launch {
            val already = ports.lifeState.state.value.mailAccounts.any { it.kind == kind }
            if (!already) {
                ports.lifeState.mutate { state ->
                    val keep = state.mailAccounts.filter { it.kind != MailKind.SEED }
                    state.copy(
                        mailAccounts = keep + MailAccount(
                            id = Ids.new("mail"),
                            kind = kind,
                            address = kind.name.lowercase(),
                        ),
                    )
                }
            }
            sync()
        }
    }

    fun disconnect(kind: MailKind) {
        viewModelScope.launch {
            val dying = ports.lifeState.state.value.mailAccounts.filter { it.kind == kind }
            dying.forEach { ports.secrets.deleteMailSecret(it.id) }
            ports.lifeState.mutate { state ->
                state.copy(mailAccounts = state.mailAccounts.filter { it.kind != kind && it.kind != MailKind.SEED })
            }
        }
    }

    fun promote(
        candidateId: String,
        titleOverride: String? = null,
        startIsoOverride: String? = null,
    ) {
        viewModelScope.launch {
            val report = ports.executor.execute(
                listOf(
                    Action.PromoteEmail(
                        candidateId = candidateId,
                        titleOverride = titleOverride,
                        startIsoOverride = startIsoOverride,
                    ),
                ),
                ActionOrigin.USER,
            )
            maybeMirrorCalendar(report.applied.firstOrNull()?.refId)
            showMessage("Added to Today", action = "View")
        }
    }

    private suspend fun maybeMirrorCalendar(eventId: String?) {
        val calendar = ports.calendar ?: return
        val snapshot = ports.lifeState.state.value
        if (!snapshot.settings.calendarSyncEnabled) return
        val event = eventId?.let { id -> snapshot.events.firstOrNull { it.id == id } } ?: return
        val start = Time.parseIsoOrNull(event.startIso) ?: return
        val zone = ZoneId.systemDefault()
        val startMs = start.atZone(zone).toInstant().toEpochMilli()
        val endMs = Time.parseIsoOrNull(event.endIso)
            ?.atZone(zone)
            ?.toInstant()
            ?.toEpochMilli()
            ?: startMs + 3_600_000L
        runCatching {
            calendar.upsert(
                listOf(
                    CalendarMirrorItem(
                        lifeOsId = event.id,
                        title = event.title,
                        startEpochMs = startMs,
                        endEpochMs = endMs,
                        notes = event.emailId.orEmpty(),
                    ),
                ),
            )
        }
    }

    fun dismiss(candidateId: String) {
        viewModelScope.launch {
            ports.executor.execute(
                listOf(Action.DismissEmail(candidateId)),
                ActionOrigin.USER,
            )
        }
    }

    fun consumeSnackbar() {
        snackbar.value = null
        snackbarAction.value = null
    }

    private fun showMessage(message: String, action: String? = null) {
        snackbar.value = message
        snackbarAction.value = action
    }

    private fun realAccounts(state: CanonicalLifeState): List<MailAccount> =
        state.mailAccounts.filter { it.kind != MailKind.SEED }

    private fun accountLabel(state: CanonicalLifeState): String {
        val real = realAccounts(state)
        if (real.isEmpty()) return if (ports.isDebug) "Seed mailbox" else "No sources"
        return real.joinToString(" · ") { account ->
            when (account.kind) {
                MailKind.GMAIL, MailKind.IMAP -> account.address.ifBlank { account.kind.name }
                MailKind.CODEFORCES -> "Codeforces"
                MailKind.LEETCODE -> "LeetCode"
                MailKind.SEED -> "Seed"
            }
        }
    }
}

fun EmailCandidate.isPendingDecision(): Boolean =
    status == CandidateStatus.PENDING && kind != com.lifeos.core.model.CandidateKind.NOISE

fun EmailCandidate.isPendingNoise(): Boolean =
    status == CandidateStatus.PENDING && kind == com.lifeos.core.model.CandidateKind.NOISE

fun EmailCandidate.isHandled(): Boolean =
    status == CandidateStatus.PROMOTED || status == CandidateStatus.DISMISSED
