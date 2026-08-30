package com.lifeos.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object Time {
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val hhmmFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun nowEpochMs(): Long = System.currentTimeMillis()

    fun todayIso(zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.now(zone).format(dateFmt)

    fun nowIso(zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDateTime.now(zone).format(dateTimeFmt)

    fun parseIsoOrNull(s: String?): LocalDateTime? {
        if (s.isNullOrBlank()) return null
        val trimmed = s.trim()
        return try {
            LocalDateTime.parse(trimmed, dateTimeFmt)
        } catch (_: DateTimeParseException) {
            try {
                LocalDate.parse(trimmed, dateFmt).atStartOfDay()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(trimmed)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    fun nextOccurrenceEpochMs(
        timeHhmm: String,
        fromEpochMs: Long = nowEpochMs(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val time = parseHhmm(timeHhmm) ?: LocalTime.of(7, 0)
        val from = Instant.ofEpochMilli(fromEpochMs).atZone(zone)
        var candidate = from.toLocalDate().atTime(time)
        if (!candidate.atZone(zone).toInstant().isAfter(from.toInstant())) {
            candidate = candidate.plusDays(1)
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    fun isoDayOfWeek(dateIso: String): Int =
        parseIsoOrNull(dateIso)?.toLocalDate()?.dayOfWeek?.value ?: LocalDate.now().dayOfWeek.value

    fun plusDaysIso(dateIso: String, days: Long): String {
        val date = parseIsoOrNull(dateIso)?.toLocalDate() ?: LocalDate.now()
        return date.plusDays(days).format(dateFmt)
    }

    fun startOfTodayEpochMs(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    fun formatHhmm(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime().format(hhmmFmt)

    fun parseHhmm(value: String): LocalTime? = try {
        LocalTime.parse(value.trim(), hhmmFmt)
    } catch (_: DateTimeParseException) {
        null
    }

    fun daysUntil(deadlineIso: String?, zone: ZoneId = ZoneId.systemDefault()): Int? {
        val deadline = parseIsoOrNull(deadlineIso)?.toLocalDate() ?: return null
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(zone), deadline).toInt()
    }
}
