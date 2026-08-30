package com.lifeos.email

import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.MailKind

object ImapPresets {
    const val GMAIL_HOST = "imap.gmail.com"
    const val GMAIL_PORT = 993

    fun resolve(account: MailAccount): MailAccount {
        val gmail = account.kind == MailKind.GMAIL
        val host = when {
            account.host.isNotBlank() -> account.host.trim()
            gmail -> GMAIL_HOST
            else -> account.host
        }
        val port = when {
            account.host.isNotBlank() -> account.port
            gmail -> GMAIL_PORT
            else -> account.port
        }
        val username = account.username.ifBlank { account.address }.trim()
        return account.copy(
            host = host,
            port = port,
            username = username,
            useSsl = if (gmail) true else account.useSsl,
        )
    }
}
