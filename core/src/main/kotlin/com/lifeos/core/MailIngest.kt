package com.lifeos.core

import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.EmailCandidate
import com.lifeos.core.model.MailMessage
import com.lifeos.core.model.RawMessage

/**
 * Folds a sync result into state. Shared so the Inbox screen and the agent's background
 * refresh cannot drift into two different dedupe rules.
 */
object MailIngest {
    const val KEEP_MESSAGES = 200

    fun merge(
        state: CanonicalLifeState,
        messages: List<RawMessage>,
        candidates: List<EmailCandidate>,
    ): CanonicalLifeState {
        val knownCandidates = state.emailCandidates.map { it.messageId }.toSet()
        val freshCandidates = candidates.filter { it.messageId !in knownCandidates }

        val knownMessages = state.mailMessages.map { it.id }.toSet()
        val freshMessages = messages
            .filter { it.id !in knownMessages }
            .map { raw ->
                MailMessage(
                    id = raw.id,
                    accountId = raw.accountId,
                    from = raw.from,
                    to = raw.to,
                    subject = raw.subject,
                    body = raw.body,
                    receivedAtEpochMs = raw.receivedAtEpochMs,
                )
            }

        if (freshCandidates.isEmpty() && freshMessages.isEmpty()) return state
        val merged = (state.mailMessages + freshMessages)
            .sortedByDescending { it.receivedAtEpochMs }
            .take(KEEP_MESSAGES)
        return state.copy(
            emailCandidates = state.emailCandidates + freshCandidates,
            mailMessages = merged,
        )
    }
}
