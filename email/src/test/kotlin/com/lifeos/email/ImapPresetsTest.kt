package com.lifeos.email

import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.MailKind
import jakarta.mail.AuthenticationFailedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImapPresetsTest {
    @Test
    fun gmailBlankHostGetsImapPreset() {
        val resolved = ImapPresets.resolve(
            MailAccount(id = "1", kind = MailKind.GMAIL, address = "a@gmail.com"),
        )
        assertEquals(ImapPresets.GMAIL_HOST, resolved.host)
        assertEquals(ImapPresets.GMAIL_PORT, resolved.port)
        assertEquals("a@gmail.com", resolved.username)
        assertTrue(resolved.useSsl)
    }

    @Test
    fun customHostIsKept() {
        val resolved = ImapPresets.resolve(
            MailAccount(
                id = "1",
                kind = MailKind.IMAP,
                address = "me@fastmail.com",
                host = "imap.fastmail.com",
                port = 993,
                username = "me@fastmail.com",
            ),
        )
        assertEquals("imap.fastmail.com", resolved.host)
        assertEquals("me@fastmail.com", resolved.username)
    }

    @Test
    fun gmailAuthFailureMentionsAppPasswordAndNeverEchoesSecret() {
        val account = MailAccount(id = "1", kind = MailKind.GMAIL, address = "a@gmail.com")
        val message = ImapErrors.userMessage(
            AuthenticationFailedException("LOGIN failed with super-secret"),
            account,
            secret = "super-secret",
        )
        assertTrue(message.contains("apppasswords"))
        assertFalse(message.contains("super-secret"))
    }

    @Test
    fun htmlBodyIsStrippedAndTruncated() {
        val html = "<p>Hello <b>world</b></p>"
        assertEquals("Hello world", ImapMessageMapper.stripTags(html))
        val long = "x".repeat(ImapMessageMapper.BODY_LIMIT + 20)
        assertEquals(ImapMessageMapper.BODY_LIMIT, ImapMessageMapper.truncateBody(long).length)
    }
}
