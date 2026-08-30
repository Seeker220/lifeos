package com.lifeos.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailAccountJsonTest {
    @Test
    fun serializedMailAccountHasNoPasswordField() {
        val json = Json.encodeToString(
            MailAccount.serializer(),
            MailAccount(
                id = "mail_1",
                kind = MailKind.GMAIL,
                address = "user@gmail.com",
                host = "imap.gmail.com",
                port = 993,
                username = "user@gmail.com",
                useSsl = true,
            ),
        )
        assertTrue(json.contains("user@gmail.com"))
        assertFalse(json.contains("password", ignoreCase = true))
        assertFalse(json.contains("secret", ignoreCase = true))
    }

    @Test
    fun missingNewFieldsStillDeserialize() {
        val raw = """{"id":"mail_old","kind":"IMAP","address":"a@uni.edu","host":"imap.uni.edu","port":993}"""
        val account = Json { ignoreUnknownKeys = true }.decodeFromString(MailAccount.serializer(), raw)
        assertTrue(account.username.isEmpty())
        assertTrue(account.useSsl)
    }
}
