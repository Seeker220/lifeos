package com.lifeos.email

import com.lifeos.core.MailSender
import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.MailKind
import com.lifeos.core.model.OutgoingMail

/** Routes OAuth accounts to the API sender and everything else to SMTP. */
class CompositeMailSender(
    private val smtp: MailSender,
    private val gmail: MailSender? = null,
) : MailSender {
    override suspend fun send(account: MailAccount, mail: OutgoingMail): Result<Unit> =
        if (account.kind == MailKind.GMAIL && account.oauth) {
            gmail?.send(account, mail)
                ?: Result.failure(IllegalStateException("Google sign-in is not configured."))
        } else {
            smtp.send(account, mail)
        }
}
