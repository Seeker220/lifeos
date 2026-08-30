package com.lifeos.email

import com.lifeos.core.MailboxSync
import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.MailKind
import com.lifeos.core.model.RawMessage

class CompositeMailboxSync(
    private val imap: MailboxSync,
    private val gmail: MailboxSync? = null,
    private val accounts: () -> List<MailAccount>,
    private val contests: MailboxSync = ContestFeedSync(),
) : MailboxSync {
    override suspend fun fetch(account: MailAccount?): Result<List<RawMessage>> {
        val real = accounts().filter { it.kind != MailKind.SEED }
        val targets = if (account != null && account.kind != MailKind.SEED) listOf(account) else real
        if (targets.isEmpty()) {
            return Result.failure(IllegalStateException(NO_ACCOUNT))
        }
        val collected = ArrayList<RawMessage>()
        var lastError: Throwable? = null
        for (target in targets) {
            fetchOne(target).fold(
                onSuccess = { collected += it },
                onFailure = { lastError = it },
            )
        }
        return when {
            collected.isNotEmpty() -> Result.success(collected)
            lastError != null -> Result.failure(lastError!!)
            else -> Result.success(emptyList())
        }
    }

    private suspend fun fetchOne(target: MailAccount): Result<List<RawMessage>> = when (target.kind) {
        MailKind.IMAP -> imap.fetch(ImapPresets.resolve(target))
        MailKind.GMAIL ->
            if (target.oauth) {
                gmail?.fetch(target)
                    ?: Result.failure(IllegalStateException("Google sign-in is not configured."))
            } else {
                imap.fetch(ImapPresets.resolve(target))
            }
        MailKind.CODEFORCES, MailKind.LEETCODE -> contests.fetch(target)
        MailKind.SEED -> Result.success(emptyList())
    }

    companion object {
        const val NO_ACCOUNT = "No mailbox connected. Add Gmail or IMAP first."
    }
}
