package com.lifeos.ui.screens.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.LifeOsLog
import com.lifeos.core.Ports
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.CandidateStatus
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.EmailCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxUiState(
    val accountLabel: String = "Seed mailbox",
    val candidates: List<EmailCandidate> = emptyList(),
    val loading: Boolean = false,
    val snackbar: String? = null,
)

class InboxViewModel(val ports: Ports) : ViewModel() {
    private val loading = MutableStateFlow(false)
    private val snackbar = MutableStateFlow<String?>(null)

    val uiState: StateFlow<InboxUiState> = combine(
        ports.lifeState.state,
        loading,
        snackbar,
    ) { state, isLoading, bar ->
        InboxUiState(
            accountLabel = accountLabel(state),
            candidates = state.emailCandidates,
            loading = isLoading,
            snackbar = bar,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    fun sync() {
        viewModelScope.launch {
            loading.value = true
            try {
                val msgs = ports.mailbox.fetch(null).getOrElse { emptyList() }
                val incoming = ports.classifier.classify(msgs)
                ports.lifeState.mutate { state ->
                    val existing = state.emailCandidates.map { it.messageId }.toSet()
                    val fresh = incoming.filter { it.messageId !in existing }
                    state.copy(emailCandidates = state.emailCandidates + fresh)
                }
            } catch (t: Throwable) {
                LifeOsLog.d("LifeOS/Mail", "sync failed: ${t.message}")
            } finally {
                loading.value = false
            }
        }
    }

    fun promote(
        candidateId: String,
        titleOverride: String? = null,
        startIsoOverride: String? = null,
    ) {
        viewModelScope.launch {
            ports.executor.execute(
                listOf(
                    Action.PromoteEmail(
                        candidateId = candidateId,
                        titleOverride = titleOverride,
                        startIsoOverride = startIsoOverride,
                    ),
                ),
                ActionOrigin.USER,
            )
            snackbar.value = "Added to Today"
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
    }

    private fun accountLabel(state: CanonicalLifeState): String {
        val imap = state.mailAccounts.firstOrNull { it.address.isNotBlank() }
        return imap?.address ?: "Seed mailbox"
    }
}

fun EmailCandidate.isPendingDecision(): Boolean =
    status == CandidateStatus.PENDING && kind != com.lifeos.core.model.CandidateKind.NOISE

fun EmailCandidate.isPendingNoise(): Boolean =
    status == CandidateStatus.PENDING && kind == com.lifeos.core.model.CandidateKind.NOISE

fun EmailCandidate.isHandled(): Boolean =
    status == CandidateStatus.PROMOTED || status == CandidateStatus.DISMISSED
