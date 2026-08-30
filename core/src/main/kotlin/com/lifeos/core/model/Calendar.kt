package com.lifeos.core.model

data class CalendarPermissionStatus(
    val read: Boolean = false,
    val write: Boolean = false,
) {
    val granted: Boolean get() = read && write
}

data class CalendarMirrorItem(
    val lifeOsId: String,
    val title: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val allDay: Boolean = false,
    val notes: String = "",
)

data class ExternalEvent(
    val providerId: Long,
    val title: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val calendarName: String = "Google Calendar",
    val lifeOsId: String? = null,
)
