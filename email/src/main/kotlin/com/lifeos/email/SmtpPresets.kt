package com.lifeos.email

import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.MailKind

object SmtpPresets {
    const val GMAIL_SMTP_HOST = "smtp.gmail.com"
    const val STARTTLS_PORT = 587

    /**
     * Fills in the outgoing server. Users supply IMAP details when connecting, so the SMTP
     * host is guessed from the incoming one — mail providers almost always mirror the name.
     */
    fun resolve(account: MailAccount): MailAccount {
        if (account.smtpHost.isNotBlank()) return account
        val incoming = ImapPresets.resolve(account)
        val host = when {
            account.kind == MailKind.GMAIL -> GMAIL_SMTP_HOST
            incoming.host.isBlank() -> ""
            else -> guessFromImapHost(incoming.host)
        }
        return incoming.copy(smtpHost = host, smtpPort = STARTTLS_PORT)
    }

    private fun guessFromImapHost(imapHost: String): String {
        val host = imapHost.trim().lowercase()
        return when {
            host.startsWith("imap.") -> "smtp." + host.removePrefix("imap.")
            host.startsWith("mail.") -> host
            else -> host
        }
    }
}
