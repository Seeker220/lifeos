package com.lifeos.email

import com.lifeos.core.model.RawMessage
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part

object ImapMessageMapper {
    const val BODY_LIMIT = 8 * 1024

    fun toRaw(message: Message, accountId: String = ""): RawMessage {
        val from = message.from?.firstOrNull()?.toString().orEmpty()
        val subject = message.subject.orEmpty()
        val body = extractText(message).take(BODY_LIMIT)
        val received = message.receivedDate?.time ?: message.sentDate?.time ?: 0L
        val headerId = runCatching { message.getHeader("Message-ID")?.firstOrNull() }.getOrNull()
        val id = headerId?.ifBlank { null } ?: "imap_${received}_${subject.hashCode()}"
        val to = runCatching {
            message.getRecipients(Message.RecipientType.TO)?.joinToString(", ").orEmpty()
        }.getOrDefault("")
        return RawMessage(
            id = id,
            from = from,
            subject = subject,
            body = body,
            receivedAtEpochMs = received,
            to = to,
            accountId = accountId,
        )
    }

    fun truncateBody(body: String): String = body.take(BODY_LIMIT)

    fun extractText(part: Part): String {
        val content = runCatching { part.content }.getOrNull() ?: return ""
        return when {
            part.isMimeType("text/plain") -> content.toString()
            part.isMimeType("text/html") -> stripTags(content.toString())
            content is Multipart -> {
                val texts = (0 until content.count).map { idx ->
                    runCatching { extractText(content.getBodyPart(idx)) }.getOrDefault("")
                }
                texts.firstOrNull { it.isNotBlank() }.orEmpty()
            }
            else -> ""
        }
    }

    fun stripTags(html: String): String =
        html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
}
