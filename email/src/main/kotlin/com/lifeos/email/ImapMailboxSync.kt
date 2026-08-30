package com.lifeos.email

import com.lifeos.core.MailboxSync
import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.RawMessage

class ImapMailboxSync : MailboxSync {
    override suspend fun fetch(account: MailAccount?): Result<List<RawMessage>> =
        Result.failure(NotImplementedError())
}
