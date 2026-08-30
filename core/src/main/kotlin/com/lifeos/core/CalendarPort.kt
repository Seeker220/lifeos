package com.lifeos.core

import com.lifeos.core.model.CalendarMirrorItem
import com.lifeos.core.model.CalendarPermissionStatus
import com.lifeos.core.model.ExternalEvent

interface CalendarPort {
    fun permissions(): CalendarPermissionStatus
    suspend fun ensureLifeOsCalendar(): Result<Long>
    suspend fun upsert(items: List<CalendarMirrorItem>): Result<Int>
    suspend fun delete(lifeOsIds: List<String>): Result<Int>
    suspend fun readRange(startMs: Long, endMs: Long): Result<List<ExternalEvent>>

    /** VCALENDAR fallback when Google Calendar hides ACCOUNT_TYPE_LOCAL. */
    fun exportIcs(items: List<CalendarMirrorItem>): String = ""

    /** Primary `com.google` calendar on the device, or failure if none. */
    suspend fun ensureGoogleCalendar(): Result<Long> =
        Result.failure(IllegalStateException("No Google account calendar"))

    fun googleAccountPresent(): Boolean = false
}
