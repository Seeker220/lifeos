package com.lifeos.email

import com.lifeos.core.LifeOsLog
import com.lifeos.core.MailboxSync
import com.lifeos.core.SecretsStore
import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.RawMessage
import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

class ImapMailboxSync(
    private val secrets: SecretsStore,
) : MailboxSync {
    override suspend fun fetch(account: MailAccount?): Result<List<RawMessage>> =
        withContext(Dispatchers.IO) {
            val acc = account ?: return@withContext Result.failure(
                IllegalArgumentException("No mail account"),
            )
            val resolved = ImapPresets.resolve(acc)
            val secret = secrets.getMailSecret(acc.id)
            runCatching {
                if (secret.isNullOrEmpty()) error(ImapErrors.MISSING_SECRET)
                if (resolved.host.isBlank() || resolved.username.isBlank()) {
                    error(ImapErrors.MISSING_HOST)
                }
                openInbox(resolved, secret)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { err ->
                    LifeOsLog.d("LifeOS/Mail", "imap ${resolved.host} failed: ${err::class.simpleName}")
                    Result.failure(
                        IllegalStateException(ImapErrors.userMessage(err, resolved, secret), err),
                    )
                },
            )
        }

    private fun openInbox(account: MailAccount, password: String): List<RawMessage> {
        val protocol = if (account.useSsl) "imaps" else "imap"
        val props = Properties().apply {
            put("mail.store.protocol", protocol)
            put("mail.$protocol.host", account.host)
            put("mail.$protocol.port", account.port.toString())
            put("mail.$protocol.ssl.enable", account.useSsl.toString())
            put("mail.$protocol.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
            put("mail.$protocol.timeout", CONNECT_TIMEOUT_MS.toString())
            put("mail.$protocol.writetimeout", CONNECT_TIMEOUT_MS.toString())
        }
        val session = Session.getInstance(props)
        var store: Store? = null
        var folder: Folder? = null
        try {
            store = session.getStore(protocol)
            store.connect(account.host, account.port, account.username, password)
            folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            val count = folder.messageCount
            if (count <= 0) return emptyList()
            val start = (count - FETCH_COUNT + 1).coerceAtLeast(1)
            val messages = folder.getMessages(start, count)
            val profile = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.CONTENT_INFO)
            }
            folder.fetch(messages, profile)
            return messages.reversed().map { ImapMessageMapper.toRaw(it, account.id) }
        } finally {
            runCatching { if (folder?.isOpen == true) folder.close(false) }
            runCatching { store?.close() }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val FETCH_COUNT = 50
    }
}
