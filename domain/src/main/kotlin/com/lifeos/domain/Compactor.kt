package com.lifeos.domain

import com.lifeos.core.ChatStore
import com.lifeos.core.CompactorPort
import com.lifeos.core.model.ChatMessage

class Compactor(
    private val chat: ChatStore,
    private val maxMessages: Int = 12,
) : CompactorPort {
    override suspend fun ensureWindow() {
        val t = chat.transcript.value
        if (t.messages.size <= maxMessages) return
        val overflow = t.messages.dropLast(maxMessages)
        val kept = t.messages.takeLast(maxMessages)
        val summary = mergeSummary(t.summary, overflow)
        chat.mutate { it.copy(messages = kept, summary = summary) }
    }

    private fun mergeSummary(previous: String, overflow: List<ChatMessage>): String {
        val merged = buildString {
            if (previous.isNotBlank()) {
                append(previous)
                append('\n')
            }
            overflow.forEach { msg ->
                val role = msg.role.name.lowercase()
                append(role)
                append(": ")
                append(msg.text.take(100))
                append('\n')
            }
        }.trimEnd()
        return if (merged.length <= SUMMARY_CAP) merged else merged.takeLast(SUMMARY_CAP)
    }

    companion object {
        private const val SUMMARY_CAP = 1500
    }
}
