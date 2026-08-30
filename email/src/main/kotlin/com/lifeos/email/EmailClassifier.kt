package com.lifeos.email

import com.lifeos.core.EmailClassifierPort
import com.lifeos.core.Ids
import com.lifeos.core.Time
import com.lifeos.core.model.CandidateKind
import com.lifeos.core.model.EmailCandidate
import com.lifeos.core.model.RawMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class EmailClassifier : EmailClassifierPort {
    override suspend fun classify(messages: List<RawMessage>): List<EmailCandidate> =
        messages.map { message ->
            runCatching { classifyOne(message) }.getOrElse {
                noiseFallback(message)
            }
        }

    private fun classifyOne(message: RawMessage): EmailCandidate {
        val hay = "${message.from} ${message.subject} ${message.body}".lowercase(Locale.US)
        val (kind, confidence) = score(hay, message.from)
        val startIso = extractStartIso("${message.subject} ${message.body}")
        return EmailCandidate(
            id = Ids.new("em"),
            messageId = message.id,
            from = message.from,
            subject = message.subject,
            snippet = message.body.trim().take(160),
            confidence = confidence,
            kind = kind,
            proposedTitle = cleanTitle(message.subject),
            proposedStartIso = startIso,
        )
    }

    private fun noiseFallback(message: RawMessage): EmailCandidate =
        EmailCandidate(
            id = Ids.new("em"),
            messageId = message.id,
            from = message.from,
            subject = message.subject,
            snippet = message.body.trim().take(160),
            confidence = 0.0,
            kind = CandidateKind.NOISE,
            proposedTitle = cleanTitle(message.subject),
        )

    companion object {
        private val examRe = Regex("""\b(exam|midterm|quiz|viva|test)\b""", RegexOption.IGNORE_CASE)
        private val deadlineRe =
            Regex("""\b(due|deadline|submit|submission|resubmission)\b""", RegexOption.IGNORE_CASE)
        private val eventRe =
            Regex("""\b(registration|drive|talk|seminar|workshop|closes)\b""", RegexOption.IGNORE_CASE)
        private val penaltyRe =
            Regex("""promotions|unsubscribe|% off|noreply@\S*store""", RegexOption.IGNORE_CASE)
        private val isoDateRe = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")
        private val dayMonthRe = Regex("""\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""")
        private val weekdayTimeRe = Regex(
            """\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\s+(\d{1,2}:\d{2})\s*(am|pm)?""",
            RegexOption.IGNORE_CASE,
        )
        private val weekdayRe = Regex(
            """\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val inDaysRe = Regex("""\bin\s+(\d+)\s+days?\b""", RegexOption.IGNORE_CASE)
        private val tomorrowRe = Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE)
        private val tonightRe = Regex("""\btonight\b""", RegexOption.IGNORE_CASE)
        private val ordinalDayRe = Regex("""\b(?:by\s+)?(\d{1,2})(?:st|nd|rd|th)\b""", RegexOption.IGNORE_CASE)
        private val replyPrefixRe = Regex("""^(re|fwd|fw):\s*""", RegexOption.IGNORE_CASE)
        private val courseCodeRe = Regex("""\s+[A-Z]{2,}\s*-?\s*\d{3,4}\s*$""")
        private val isoOut = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        internal fun score(hay: String, from: String): Pair<CandidateKind, Double> {
            var exam = if (examRe.containsMatchIn(hay)) 0.50 else 0.0
            var deadline = if (deadlineRe.containsMatchIn(hay)) 0.45 else 0.0
            var event = if (eventRe.containsMatchIn(hay)) 0.35 else 0.0

            val sender = from.lowercase(Locale.US)
            val domain = sender.substringAfter('@', sender)
            if (domain.endsWith(".edu") || domain.contains(".edu")) {
                exam += 0.25
                deadline += 0.15
                event += 0.15
            }
            if (domain.contains("classroom.google.com")) {
                deadline += 0.25
                exam += 0.10
            }
            if (sender.contains("placement") || domain.contains("placement")) {
                event += 0.15
            }
            if (domain.contains("codeforces") || domain.contains("leetcode") ||
                hay.contains("codeforces") || hay.contains("leetcode")
            ) {
                event += 0.45
                deadline += 0.15
            }
            if (hay.contains("contest") || hay.contains("round (div")) {
                event += 0.15
            }

            val penalty = if (penaltyRe.containsMatchIn(hay) || penaltyRe.containsMatchIn(sender)) 0.55 else 0.0
            val ranked = listOf(
                CandidateKind.EXAM to exam,
                CandidateKind.DEADLINE to deadline,
                CandidateKind.EVENT to event,
            ).maxBy { it.second }
            val confidence = (ranked.second - penalty).coerceIn(0.0, 1.0)
            val kind = if (confidence < 0.35) CandidateKind.NOISE else ranked.first
            return kind to confidence
        }

        internal fun extractStartIso(
            text: String,
            now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault()),
        ): String? {
            isoDateRe.find(text)?.groupValues?.get(1)?.let { iso ->
                val date = Time.parseIsoOrNull(iso)?.toLocalDate() ?: LocalDate.parse(iso)
                val time = nearbyTime(text) ?: LocalTime.of(9, 0)
                return date.atTime(time).format(isoOut)
            }
            dayMonthRe.find(text)?.let { match ->
                val day = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                if (day in 1..31 && month in 1..12) {
                    val yearPart = match.groupValues.getOrNull(3).orEmpty()
                    val year = when {
                        yearPart.length == 4 -> yearPart.toInt()
                        yearPart.length == 2 -> 2000 + yearPart.toInt()
                        else -> now.year
                    }
                    var date = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                    if (date != null && date.isBefore(now.toLocalDate()) && yearPart.isEmpty()) {
                        date = date.plusYears(1)
                    }
                    if (date != null) {
                        val time = nearbyTime(text) ?: LocalTime.of(9, 0)
                        return date.atTime(time).format(isoOut)
                    }
                }
            }
            weekdayTimeRe.find(text)?.let { match ->
                val dow = parseWeekday(match.groupValues[1]) ?: return@let
                val time = parseClock(match.groupValues[2], match.groupValues.getOrNull(3)) ?: return@let
                return nextWeekday(dow, time, now).format(isoOut)
            }
            weekdayRe.find(text)?.let { match ->
                val dow = parseWeekday(match.groupValues[1]) ?: return@let
                return nextWeekday(dow, LocalTime.of(9, 0), now).format(isoOut)
            }
            inDaysRe.find(text)?.groupValues?.get(1)?.toLongOrNull()?.let { days ->
                return Time.parseIsoOrNull(Time.plusDaysIso(Time.todayIso(), days))
                    ?.toLocalDate()
                    ?.atTime(9, 0)
                    ?.format(isoOut)
            }
            if (tomorrowRe.containsMatchIn(text)) {
                return now.toLocalDate().plusDays(1).atTime(9, 0).format(isoOut)
            }
            if (tonightRe.containsMatchIn(text)) {
                var at = now.toLocalDate().atTime(21, 0)
                if (!at.isAfter(now)) at = at.plusDays(1)
                return at.format(isoOut)
            }
            ordinalDayRe.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { day ->
                if (day in 1..31) {
                    var date = now.toLocalDate()
                    if (date.dayOfMonth >= day) date = date.plusMonths(1)
                    val resolved = runCatching { date.withDayOfMonth(day) }.getOrNull()
                    if (resolved != null) return resolved.atTime(9, 0).format(isoOut)
                }
            }
            return null
        }

        internal fun cleanTitle(subject: String): String {
            var title = subject.trim()
            while (replyPrefixRe.containsMatchIn(title)) {
                title = title.replaceFirst(replyPrefixRe, "")
            }
            title = title.replace(courseCodeRe, "")
            return title.trim().ifBlank { subject.trim() }
        }

        private fun nearbyTime(text: String): LocalTime? {
            val match = Regex("""\b(\d{1,2}:\d{2})\s*(am|pm)?""", RegexOption.IGNORE_CASE).find(text)
                ?: return null
            return parseClock(match.groupValues[1], match.groupValues.getOrNull(2))
        }

        private fun parseClock(hhmm: String, ampm: String?): LocalTime? {
            val parts = hhmm.split(':')
            if (parts.size < 2) return null
            var hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (minute !in 0..59) return null
            val mer = ampm?.lowercase(Locale.US)
            if (mer == "pm" && hour in 1..11) hour += 12
            if (mer == "am" && hour == 12) hour = 0
            if (hour !in 0..23) return null
            return LocalTime.of(hour, minute)
        }

        private fun parseWeekday(raw: String): DayOfWeek? = when (raw.lowercase(Locale.US)) {
            "monday", "mon" -> DayOfWeek.MONDAY
            "tuesday", "tue", "tues" -> DayOfWeek.TUESDAY
            "wednesday", "wed" -> DayOfWeek.WEDNESDAY
            "thursday", "thu", "thur", "thurs" -> DayOfWeek.THURSDAY
            "friday", "fri" -> DayOfWeek.FRIDAY
            "saturday", "sat" -> DayOfWeek.SATURDAY
            "sunday", "sun" -> DayOfWeek.SUNDAY
            else -> null
        }

        private fun nextWeekday(dow: DayOfWeek, time: LocalTime, now: LocalDateTime): LocalDateTime {
            var date = now.toLocalDate()
            var candidate = date.atTime(time)
            var guard = 0
            while (candidate.dayOfWeek != dow || !candidate.isAfter(now)) {
                date = date.plusDays(1)
                candidate = date.atTime(time)
                if (++guard > 14) break
            }
            return candidate
        }
    }
}
