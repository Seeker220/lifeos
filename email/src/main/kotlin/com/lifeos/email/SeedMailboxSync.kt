package com.lifeos.email

import com.lifeos.core.MailboxSync
import com.lifeos.core.Time
import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.RawMessage
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate

class SeedMailboxSync : MailboxSync {
    override suspend fun fetch(account: MailAccount?): Result<List<RawMessage>> = runCatching {
        delay(400)
        val substituted = applyRelativeDates(SeedMailbox.SEED_JSON)
        json.decodeFromString<List<RawMessage>>(substituted).map { msg ->
            msg.copy(receivedAtEpochMs = Time.nowEpochMs())
        }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        internal fun applyRelativeDates(raw: String): String {
            val today = Time.todayIso()
            val tokens = mapOf(
                "{{TODAY}}" to today,
                "{{PLUS_3}}" to Time.plusDaysIso(today, 3),
                "{{NEXT_FRI}}" to nextDowIso(DayOfWeek.FRIDAY),
                "{{NEXT_TUE}}" to nextDowIso(DayOfWeek.TUESDAY),
            )
            return tokens.entries.fold(raw) { acc, (token, value) -> acc.replace(token, value) }
        }

        private fun nextDowIso(dow: DayOfWeek): String {
            val today = LocalDate.parse(Time.todayIso())
            val delta = (dow.value - today.dayOfWeek.value + 7) % 7
            return Time.plusDaysIso(Time.todayIso(), delta.toLong())
        }
    }
}
