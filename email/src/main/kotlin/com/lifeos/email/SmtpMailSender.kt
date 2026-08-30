package com.lifeos.email

import com.lifeos.core.LifeOsLog
import com.lifeos.core.MailSender
import com.lifeos.core.SecretsStore
import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.OutgoingMail
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

class SmtpMailSender(
    private val secrets: SecretsStore,
) : MailSender {

    override suspend fun send(account: MailAccount, mail: OutgoingMail): Result<Unit> =
        withContext(Dispatchers.IO) {
            val resolved = SmtpPresets.resolve(account)
            val secret = secrets.getMailSecret(account.id)
            runCatching {
                if (secret.isNullOrEmpty()) error(ImapErrors.MISSING_SECRET)
                if (resolved.smtpHost.isBlank()) error("No outgoing server for this account.")
                if (mail.to.isBlank()) error("Recipient is required.")
                deliver(resolved, secret, mail)
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { err ->
                    LifeOsLog.d("LifeOS/Mail", "smtp ${resolved.smtpHost} failed: ${err::class.simpleName}")
                    Result.failure(
                        IllegalStateException(ImapErrors.userMessage(err, resolved, secret), err),
                    )
                },
            )
        }

    private fun deliver(account: MailAccount, password: String, mail: OutgoingMail) {
        val props = Properties().apply {
            put("mail.transport.protocol", "smtp")
            put("mail.smtp.host", account.smtpHost)
            put("mail.smtp.port", account.smtpPort.toString())
            put("mail.smtp.auth", "true")
            // 465 is implicit TLS; everything else negotiates STARTTLS after connecting.
            if (account.smtpPort == IMPLICIT_TLS_PORT) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            put("mail.smtp.connectiontimeout", TIMEOUT_MS.toString())
            put("mail.smtp.timeout", TIMEOUT_MS.toString())
            put("mail.smtp.writetimeout", TIMEOUT_MS.toString())
        }
        val username = account.username.ifBlank { account.address }
        val session = Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(username, password)
            },
        )
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(account.address.ifBlank { username }))
            setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(mail.to, false))
            subject = mail.subject.ifBlank { "(no subject)" }
            setText(mail.body, "UTF-8")
            sentDate = java.util.Date()
        }
        Transport.send(message)
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val IMPLICIT_TLS_PORT = 465
    }
}
