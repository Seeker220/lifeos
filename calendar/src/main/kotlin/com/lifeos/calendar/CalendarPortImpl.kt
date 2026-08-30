package com.lifeos.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.lifeos.core.CalendarPort
import com.lifeos.core.LifeOsLog
import com.lifeos.core.model.CalendarMirrorItem
import com.lifeos.core.model.CalendarPermissionStatus
import com.lifeos.core.model.ExternalEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

class CalendarPortImpl(private val context: Context) : CalendarPort {

    @Volatile
    private var cachedCalendarId: Long? = null

    override fun permissions(): CalendarPermissionStatus {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        return CalendarPermissionStatus(read = read, write = write)
    }

    override suspend fun ensureLifeOsCalendar(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching { findOrCreateCalendar() }.onFailure { t ->
            LifeOsLog.d(TAG, "ensureLifeOsCalendar: ${t.message ?: t::class.simpleName}")
        }
    }

    override suspend fun ensureGoogleCalendar(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            requireRead()
            queryGooglePrimaryId() ?: error("No Google account calendar on this device")
        }.onFailure { t ->
            LifeOsLog.d(TAG, "ensureGoogleCalendar: ${t.message ?: t::class.simpleName}")
        }
    }

    override fun googleAccountPresent(): Boolean {
        if (!permissions().read) return false
        return runCatching { queryGooglePrimaryId() != null }.getOrDefault(false)
    }

    override suspend fun upsert(items: List<CalendarMirrorItem>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (items.isEmpty()) return@runCatching 0
            requireWrite()
            val target = resolveWriteTarget()
            var written = 0
            for (item in items) {
                if (item.lifeOsId.isBlank()) continue
                runCatching { upsertOne(target, item) }
                    .onSuccess { written += 1 }
                    .onFailure { t ->
                        LifeOsLog.d(TAG, "upsert ${item.lifeOsId}: ${t.message ?: t::class.simpleName}")
                    }
            }
            written
        }.onFailure { t ->
            LifeOsLog.d(TAG, "upsert: ${t.message ?: t::class.simpleName}")
        }
    }

    override suspend fun delete(lifeOsIds: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (lifeOsIds.isEmpty()) return@runCatching 0
            requireWrite()
            val resolver = context.contentResolver
            var removed = 0
            for (id in lifeOsIds.distinct().filter { it.isNotBlank() }) {
                val local = resolver.delete(
                    asSyncAdapter(CalendarContract.Events.CONTENT_URI),
                    "${CalendarContract.Events.SYNC_DATA1}=?",
                    arrayOf(id),
                )
                val google = resolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "${CalendarContract.Events.CUSTOM_APP_URI}=? AND ${CalendarContract.Events.DELETED}=0",
                    arrayOf(lifeOsAppUri(id)),
                )
                removed += local + google
            }
            removed
        }.onFailure { t ->
            LifeOsLog.d(TAG, "delete: ${t.message ?: t::class.simpleName}")
        }
    }

    override suspend fun readRange(startMs: Long, endMs: Long): Result<List<ExternalEvent>> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireRead()
                queryInstances(startMs, endMs)
            }.onFailure { t ->
                LifeOsLog.d(TAG, "readRange: ${t.message ?: t::class.simpleName}")
            }
        }

    /**
     * VCALENDAR fallback if a calendar app hides ACCOUNT_TYPE_LOCAL calendars.
     * Share via FileProvider from the app module if needed.
     */
    override fun exportIcs(items: List<CalendarMirrorItem>): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//LifeOS//Calendar//EN")
        appendLine("CALSCALE:GREGORIAN")
        for (item in items) {
            if (item.lifeOsId.isBlank()) continue
            appendLine("BEGIN:VEVENT")
            appendLine("UID:${item.lifeOsId}@lifeos")
            appendLine("DTSTAMP:${formatIcsUtc(System.currentTimeMillis())}")
            if (item.allDay) {
                appendLine("DTSTART;VALUE=DATE:${formatIcsDate(item.startEpochMs)}")
                appendLine("DTEND;VALUE=DATE:${formatIcsDate(item.endEpochMs)}")
            } else {
                appendLine("DTSTART:${formatIcsUtc(item.startEpochMs)}")
                appendLine("DTEND:${formatIcsUtc(item.endEpochMs)}")
            }
            appendLine("SUMMARY:${escapeIcs(item.title)}")
            if (item.notes.isNotBlank()) appendLine("DESCRIPTION:${escapeIcs(item.notes)}")
            appendLine("END:VEVENT")
        }
        appendLine("END:VCALENDAR")
    }

    private fun resolveWriteTarget(): WriteTarget {
        queryGooglePrimaryId()?.let { id ->
            cachedCalendarId = id
            return WriteTarget(calendarId = id, google = true)
        }
        return WriteTarget(calendarId = findOrCreateCalendar(), google = false)
    }

    private fun queryGooglePrimaryId(): Long? {
        val resolver = context.contentResolver
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE}=? AND ${CalendarContract.Calendars.IS_PRIMARY}=1",
            arrayOf(GOOGLE_ACCOUNT_TYPE),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                if (id > 0L) return id
            }
        }
        return resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE}=? AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
            arrayOf(GOOGLE_ACCOUNT_TYPE, CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0).takeIf { it > 0L } else null
        }
    }

    private fun findOrCreateCalendar(): Long {
        requireWrite()
        cachedCalendarId?.let { id ->
            if (calendarExists(id) && !isGoogleCalendar(id)) return id
        }
        queryLifeOsCalendarId()?.let { id ->
            cachedCalendarId = id
            return id
        }
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            put(CalendarContract.Calendars.CAN_ORGANIZER_RESPOND, 0)
            put(CalendarContract.Calendars.ALLOWED_REMINDERS, "0,1")
            put(CalendarContract.Calendars.ALLOWED_AVAILABILITY, "0,1,2")
            put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES, "0,1,2")
        }
        val uri = context.contentResolver.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), values)
            ?: error("calendar insert returned null")
        val id = ContentUris.parseId(uri)
        if (id <= 0L) error("calendar insert returned invalid id")
        cachedCalendarId = id
        LifeOsLog.d(TAG, "created LifeOS calendar id=$id")
        return id
    }

    private fun queryLifeOsCalendarId(): Long? {
        val resolver = context.contentResolver
        return resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun calendarExists(id: Long): Boolean {
        return context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
            arrayOf(CalendarContract.Calendars._ID),
            null,
            null,
            null,
        )?.use { it.moveToFirst() } == true
    }

    private fun upsertOne(target: WriteTarget, item: CalendarMirrorItem) {
        val existingId = queryEventId(target.calendarId, item.lifeOsId, target.google)
        val start = item.startEpochMs
        val end = when {
            item.endEpochMs > item.startEpochMs -> item.endEpochMs
            item.allDay -> item.startEpochMs + 86_400_000L
            else -> item.startEpochMs + 3_600_000L
        }
        val tz = if (item.allDay) "UTC" else TimeZone.getDefault().id
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, target.calendarId)
            put(CalendarContract.Events.TITLE, item.title)
            put(CalendarContract.Events.DESCRIPTION, item.notes)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz)
            put(CalendarContract.Events.ALL_DAY, if (item.allDay) 1 else 0)
            if (target.google) {
                put(CalendarContract.Events.CUSTOM_APP_PACKAGE, APP_PACKAGE)
                put(CalendarContract.Events.CUSTOM_APP_URI, lifeOsAppUri(item.lifeOsId))
            } else {
                put(CalendarContract.Events.SYNC_DATA1, item.lifeOsId)
                put(CalendarContract.Events.DIRTY, 0)
            }
        }
        val resolver = context.contentResolver
        if (existingId != null) {
            val base = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
            val uri = if (target.google) base else asSyncAdapter(base)
            resolver.update(uri, values, null, null)
        } else {
            val dest = if (target.google) {
                CalendarContract.Events.CONTENT_URI
            } else {
                asSyncAdapter(CalendarContract.Events.CONTENT_URI)
            }
            val uri = resolver.insert(dest, values) ?: error("event insert returned null")
            if (ContentUris.parseId(uri) <= 0L) error("event insert returned invalid id")
        }
    }

    private fun queryEventId(calendarId: Long, lifeOsId: String, google: Boolean): Long? {
        val selection: String
        val args: Array<String>
        if (google) {
            selection = "${CalendarContract.Events.CUSTOM_APP_URI}=? AND " +
                "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DELETED}=0"
            args = arrayOf(lifeOsAppUri(lifeOsId), calendarId.toString())
        } else {
            selection = "${CalendarContract.Events.SYNC_DATA1}=? AND " +
                "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DELETED}=0"
            args = arrayOf(lifeOsId, calendarId.toString())
        }
        return context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            args,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun isGoogleCalendar(id: Long): Boolean {
        return context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
            arrayOf(CalendarContract.Calendars.ACCOUNT_TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == GOOGLE_ACCOUNT_TYPE
        } == true
    }

    private fun queryInstances(startMs: Long, endMs: Long): List<ExternalEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, startMs)
            ContentUris.appendId(this, endMs)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val events = mutableListOf<ExternalEvent>()
        val eventIds = mutableListOf<Long>()
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val idIdx = cursor.columnIndex(CalendarContract.Instances.EVENT_ID) ?: return@use
            val titleIdx = cursor.columnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.columnIndex(CalendarContract.Instances.BEGIN) ?: return@use
            val endIdx = cursor.columnIndex(CalendarContract.Instances.END)
            val nameIdx = cursor.columnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val eventId = cursor.getLong(idIdx)
                eventIds += eventId
                events += ExternalEvent(
                    providerId = eventId,
                    title = titleIdx?.let { cursor.stringOrEmpty(it) }.orEmpty(),
                    startEpochMs = cursor.getLong(beginIdx),
                    endEpochMs = endIdx?.let { cursor.getLong(it) } ?: 0L,
                    calendarName = nameIdx?.let { cursor.stringOrEmpty(it) }?.ifBlank { null }
                        ?: "Google Calendar",
                )
            }
        }
        if (eventIds.isEmpty()) return events
        val lifeOsByEvent = queryLifeOsIds(eventIds.distinct())
        return events.map { event ->
            val lifeOsId = lifeOsByEvent[event.providerId]
            if (lifeOsId.isNullOrBlank()) event else event.copy(lifeOsId = lifeOsId)
        }
    }

    private fun queryLifeOsIds(eventIds: List<Long>): Map<Long, String> {
        if (eventIds.isEmpty()) return emptyMap()
        val out = mutableMapOf<Long, String>()
        eventIds.chunked(80).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.SYNC_DATA1,
                    CalendarContract.Events.CUSTOM_APP_URI,
                ),
                "${CalendarContract.Events._ID} IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray(),
                null,
            )?.use { cursor ->
                val idIdx = cursor.columnIndex(CalendarContract.Events._ID) ?: return@use
                val syncIdx = cursor.columnIndex(CalendarContract.Events.SYNC_DATA1)
                val uriIdx = cursor.columnIndex(CalendarContract.Events.CUSTOM_APP_URI)
                while (cursor.moveToNext()) {
                    val sync = syncIdx?.let { cursor.stringOrEmpty(it) }.orEmpty()
                    val appUri = uriIdx?.let { cursor.stringOrEmpty(it) }.orEmpty()
                    val lifeOsId = when {
                        sync.isNotBlank() -> sync
                        appUri.startsWith(APP_URI_PREFIX) -> appUri.removePrefix(APP_URI_PREFIX)
                        else -> ""
                    }
                    if (lifeOsId.isNotBlank()) out[cursor.getLong(idIdx)] = lifeOsId
                }
            }
        }
        return out
    }

    private fun requireRead() {
        if (!permissions().read) error("READ_CALENDAR not granted")
    }

    private fun requireWrite() {
        if (!permissions().write) error("WRITE_CALENDAR not granted")
    }

    private fun asSyncAdapter(base: Uri): Uri =
        base.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

    private fun lifeOsAppUri(lifeOsId: String): String = "$APP_URI_PREFIX$lifeOsId"

    private fun Cursor.columnIndex(name: String): Int? =
        getColumnIndex(name).takeIf { it >= 0 }

    private fun Cursor.stringOrEmpty(index: Int): String =
        if (index < 0 || isNull(index)) "" else getString(index).orEmpty()

    private data class WriteTarget(val calendarId: Long, val google: Boolean)

    private companion object {
        const val TAG = "LifeOS/Cal"
        const val ACCOUNT_NAME = "LifeOS"
        const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val CALENDAR_NAME = "LifeOS"
        const val CALENDAR_COLOR = 0xFF4C8DFF.toInt()
        const val APP_PACKAGE = "com.lifeos.app"
        const val APP_URI_PREFIX = "lifeos://event/"

        private val icsUtc = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(java.time.ZoneOffset.UTC)
        private val icsDate = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(java.time.ZoneOffset.UTC)

        fun formatIcsUtc(epochMs: Long): String =
            icsUtc.format(java.time.Instant.ofEpochMilli(epochMs))

        fun formatIcsDate(epochMs: Long): String =
            icsDate.format(java.time.Instant.ofEpochMilli(epochMs))

        fun escapeIcs(raw: String): String =
            raw.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
    }
}
