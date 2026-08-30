package com.lifeos.email

import com.lifeos.core.MailboxSync
import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.MailKind
import com.lifeos.core.model.RawMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeMailboxSyncTest {
    @Test
    fun withoutAccountAsksUserToConnect() = runTest {
        val composite = CompositeMailboxSync(
            imap = unusedImap(),
            gmail = null,
            accounts = { emptyList() },
        )
        val error = composite.fetch(null).exceptionOrNull()
        assertEquals(CompositeMailboxSync.NO_ACCOUNT, error?.message)
    }

    @Test
    fun oauthGmailWithoutClientFailsInsteadOfSilentlyUsingImap() = runTest {
        val account = MailAccount(
            id = "1",
            kind = MailKind.GMAIL,
            address = "a@gmail.com",
            oauth = true,
        )
        val composite = CompositeMailboxSync(
            imap = unusedImap(),
            gmail = null,
            accounts = { listOf(account) },
        )
        assertTrue(composite.fetch(account).isFailure)
    }

    @Test
    fun gmailWithoutOauthUsesImapPreset() = runTest {
        var fetched: MailAccount? = null
        val imap = MailboxSync { account ->
            fetched = account
            Result.success(emptyList())
        }
        val account = MailAccount(id = "1", kind = MailKind.GMAIL, address = "a@gmail.com")
        val composite = CompositeMailboxSync(
            imap = imap,
            gmail = null,
            accounts = { listOf(account) },
        )
        composite.fetch(account).getOrThrow()
        assertEquals(ImapPresets.GMAIL_HOST, fetched?.host)
        assertEquals(ImapPresets.GMAIL_PORT, fetched?.port)
    }

    @Test
    fun fetchNullMergesMailboxAndContestSources() = runTest {
        val gmail = MailAccount(id = "1", kind = MailKind.GMAIL, address = "a@gmail.com")
        val cf = MailAccount(id = "2", kind = MailKind.CODEFORCES, address = "codeforces")
        val imap = MailboxSync { Result.success(listOf(msg("g1", "midterm tomorrow"))) }
        val contests = MailboxSync { Result.success(listOf(msg("cf_1", "Codeforces Round"))) }
        val composite = CompositeMailboxSync(
            imap = imap,
            gmail = null,
            accounts = { listOf(gmail, cf) },
            contests = contests,
        )
        val out = composite.fetch(null).getOrThrow()
        assertEquals(2, out.size)
        assertTrue(out.any { it.id == "g1" })
        assertTrue(out.any { it.id == "cf_1" })
    }

    private fun unusedImap(): MailboxSync = MailboxSync {
        Result.failure(IllegalStateException("imap should not run"))
    }

    private fun MailboxSync(block: suspend (MailAccount?) -> Result<List<RawMessage>>): MailboxSync =
        object : MailboxSync {
            override suspend fun fetch(account: MailAccount?): Result<List<RawMessage>> = block(account)
        }

    private fun msg(id: String, subject: String) = RawMessage(
        id = id,
        from = "test@example.com",
        subject = subject,
        body = subject,
        receivedAtEpochMs = 1L,
    )
}
