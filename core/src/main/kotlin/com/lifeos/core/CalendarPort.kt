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
}
