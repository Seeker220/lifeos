package com.lifeos.email

import com.lifeos.core.MailboxSync
import com.lifeos.core.model.MailAccount
import com.lifeos.core.model.MailKind
import com.lifeos.core.model.RawMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ContestFeedSync(
    private val httpGet: (String) -> String = Companion::get,
    private val httpPost: (String, String) -> String = Companion::post,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : MailboxSync {
    override suspend fun fetch(account: MailAccount?): Result<List<RawMessage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (account?.kind) {
                    MailKind.CODEFORCES -> fetchCodeforces()
                    MailKind.LEETCODE -> fetchLeetcode()
                    else -> emptyList()
                }
            }
        }

    private fun fetchCodeforces(): List<RawMessage> {
        val body = httpGet(CODEFORCES_URL)
        return parseCodeforces(body, nowMs() / 1000)
    }

    private fun fetchLeetcode(): List<RawMessage> {
        val now = nowMs()
        return runCatching {
            val body = httpPost(LEETCODE_URL, LEETCODE_QUERY)
            parseLeetcode(body, now / 1000)
        }.getOrElse { fallbackLeetcode(now) }.ifEmpty { fallbackLeetcode(now) }
    }

    companion object {
        private const val CODEFORCES_URL = "https://codeforces.com/api/contest.list"
        private const val LEETCODE_URL = "https://leetcode.com/graphql"
        private const val LEETCODE_QUERY =
            """{"query":"{upcomingContests{title titleSlug startTime duration}}"}"""
        private val json = Json { ignoreUnknownKeys = true }
        private val iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.systemDefault())

        internal fun parseCodeforces(raw: String, nowSec: Long): List<RawMessage> {
            val parsed = json.decodeFromString(CfResponse.serializer(), raw)
            if (parsed.status != "OK") return emptyList()
            return parsed.result
                .filter { it.phase == "BEFORE" && (it.startTimeSeconds ?: 0L) > nowSec }
                .sortedBy { it.startTimeSeconds ?: Long.MAX_VALUE }
                .take(8)
                .map { contest ->
                    val start = (contest.startTimeSeconds ?: nowSec) * 1000
                    val hours = contest.durationSeconds / 3600.0
                    RawMessage(
                        id = "cf_${contest.id}",
                        from = "Codeforces <noreply@codeforces.com>",
                        subject = contest.name,
                        body = "Contest starts ${iso.format(Instant.ofEpochMilli(start))}. " +
                            "Duration ${"%.1f".format(hours)}h. https://codeforces.com/contest/${contest.id}",
                        receivedAtEpochMs = nowSec * 1000,
                    )
                }
        }

        internal fun parseLeetcode(raw: String, nowSec: Long): List<RawMessage> {
            val parsed = json.decodeFromString(LcEnvelope.serializer(), raw)
            return (parsed.data?.upcomingContests ?: emptyList())
                .filter { it.startTime > nowSec && it.title.isNotBlank() }
                .sortedBy { it.startTime }
                .take(6)
                .map { contest ->
                    val start = contest.startTime * 1000
                    val hours = if (contest.duration > 0) contest.duration / 3600.0 else 1.5
                    val slug = contest.titleSlug.ifBlank { contest.title.lowercase().replace(' ', '-') }
                    RawMessage(
                        id = "lc_$slug",
                        from = "LeetCode <noreply@leetcode.com>",
                        subject = contest.title,
                        body = "Contest starts ${iso.format(Instant.ofEpochMilli(start))}. " +
                            "Duration ${"%.1f".format(hours)}h. https://leetcode.com/contest/$slug",
                        receivedAtEpochMs = nowSec * 1000,
                    )
                }
        }

        internal fun fallbackLeetcode(nowMs: Long): List<RawMessage> {
            val now = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC)
            val weekly = nextOrSame(now, DayOfWeek.SUNDAY, 8, 0)
            val biweekly = nextOrSame(now, DayOfWeek.SATURDAY, 14, 30)
            return listOf(
                RawMessage(
                    id = "lc_weekly_${weekly.toLocalDate()}",
                    from = "LeetCode <noreply@leetcode.com>",
                    subject = "LeetCode Weekly Contest",
                    body = "Weekly contest starts ${iso.format(weekly.toInstant())}. Duration 1.5h. https://leetcode.com/contest/",
                    receivedAtEpochMs = nowMs,
                ),
                RawMessage(
                    id = "lc_biweekly_${biweekly.toLocalDate()}",
                    from = "LeetCode <noreply@leetcode.com>",
                    subject = "LeetCode Biweekly Contest",
                    body = "Biweekly contest starts ${iso.format(biweekly.toInstant())}. Duration 1.5h. https://leetcode.com/contest/",
                    receivedAtEpochMs = nowMs,
                ),
            )
        }

        private fun nextOrSame(now: ZonedDateTime, dow: DayOfWeek, hour: Int, minute: Int): ZonedDateTime {
            var at = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            var guard = 0
            while (at.dayOfWeek != dow || !at.isAfter(now)) {
                at = at.plusDays(1)
                if (++guard > 14) break
            }
            return at
        }

        private fun get(url: String): String = request("GET", url, null)

        private fun post(url: String, body: String): String = request("POST", url, body)

        private fun request(method: String, url: String, body: String?): String {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.requestMethod = method
            conn.setRequestProperty("User-Agent", "LifeOS/1.0")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            return (stream ?: error("HTTP ${conn.responseCode}")).bufferedReader().use { it.readText() }
                .also { conn.disconnect() }
        }
    }
}

@Serializable
internal data class CfResponse(
    val status: String = "",
    val result: List<CfContest> = emptyList(),
)

@Serializable
internal data class CfContest(
    val id: Int,
    val name: String = "",
    val phase: String = "",
    val durationSeconds: Long = 0,
    val startTimeSeconds: Long? = null,
)

@Serializable
internal data class LcEnvelope(val data: LcData? = null)

@Serializable
internal data class LcData(val upcomingContests: List<LcContest> = emptyList())

@Serializable
internal data class LcContest(
    val title: String = "",
    val titleSlug: String = "",
    val startTime: Long = 0,
    val duration: Long = 0,
)
