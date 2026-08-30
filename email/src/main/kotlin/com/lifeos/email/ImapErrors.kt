package com.lifeos.email

import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.MailKind
import jakarta.mail.AuthenticationFailedException

object ImapErrors {
    const val GMAIL_APP_PASSWORD =
        "App password rejected. Create one at myaccount.google.com/apppasswords"
    const val GENERIC_AUTH = "Sign-in rejected. Check username, password, and IMAP settings."
    const val MISSING_SECRET = "No password stored for this account. Reconnect."
    const val MISSING_HOST = "Host and username are required."

    fun userMessage(error: Throwable, account: MailAccount?, secret: String? = null): String {
        val gmail = account?.kind == MailKind.GMAIL ||
            account?.host?.contains("gmail", ignoreCase = true) == true
        val raw = when {
            isAuthFailure(error) && gmail -> GMAIL_APP_PASSWORD
            isAuthFailure(error) -> GENERIC_AUTH
            else -> error.message?.takeIf { it.isNotBlank() } ?: "Could not reach the mail server."
        }
        return sanitize(raw, secret)
    }

    fun isAuthFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is AuthenticationFailedException) return true
            val msg = current.message.orEmpty()
            if (
                msg.contains("AUTHENTICATION", ignoreCase = true) ||
                msg.contains("Invalid credentials", ignoreCase = true) ||
                msg.contains("LOGIN failed", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun sanitize(message: String, secret: String?): String {
        if (secret.isNullOrEmpty()) return message
        return message.replace(secret, "••••")
    }
}
