package com.lifeos.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.Ports
import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.ExternalEvent
import com.lifeos.core.model.TimelineItem
import com.lifeos.core.model.TimelineKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayItem(
    val item: TimelineItem,
    val external: Boolean = false,
    val sourceGoalLabel: String? = null,
    val startMinutes: Int? = null,
    val endMinutes: Int? = null,
) {
    val key: String get() = "${item.kind}-${item.refId}"
    val completable: Boolean
        get() = !external && (item.kind == TimelineKind.HABIT || item.kind == TimelineKind.TASK)
    val timed: Boolean get() = Time.parseHhmm(item.timeHhmm) != null
}

data class TimelineGroup(
    val title: String,
    val items: List<TodayItem>,
)

data class WeekDayUi(
    val dateIso: String,
    val weekdayInitial: String,
    val dayNumber: Int,
    val isToday: Boolean,
    val selected: Boolean,
    val hasItems: Boolean,
)

data class TodayUiState(
    val selectedDate: String,
    val dateTitle: String,
    val isToday: Boolean,
    val focusActive: Boolean,
    val groups: List<TimelineGroup>,
    val weekDays: List<WeekDayUi>,
    val empty: Boolean,
)

class TodayViewModel(private val ports: Ports) : ViewModel() {
    private val selectedDate = MutableStateFlow(Time.todayIso())
    private val externalEvents = MutableStateFlow<List<ExternalEvent>>(emptyList())

    val uiState: StateFlow<TodayUiState> = combine(
        ports.lifeState.state,
        selectedDate,
        externalEvents,
    ) { state, date, external -> buildUi(state, date, external) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            buildUi(ports.lifeState.state.value, selectedDate.value, externalEvents.value),
        )

    init {
        viewModelScope.launch {
            selectedDate.collect { date -> refreshExternal(date) }
        }
    }

    fun shiftDate(days: Long) {
        selectedDate.update { Time.plusDaysIso(it, days) }
    }

    fun selectDate(dateIso: String) {
        selectedDate.value = dateIso
    }

    fun completeItem(item: TimelineItem) {
        val action = when (item.kind) {
            TimelineKind.TASK -> Action.CompleteTask(id = item.refId)
            TimelineKind.HABIT -> Action.CompleteHabitToday(id = item.refId)
            else -> return
        }
        viewModelScope.launch {
            ports.executor.execute(listOf(action), ActionOrigin.USER)
        }
    }

    fun startFocus() {
        viewModelScope.launch {
            ports.executor.execute(
                listOf(Action.FocusStart(mode = null, packages = null, minutes = 50)),
                ActionOrigin.USER,
            )
        }
    }

    fun stopFocus() {
        viewModelScope.launch {
            ports.executor.execute(listOf(Action.FocusStop), ActionOrigin.USER)
        }
    }

    private suspend fun refreshExternal(date: String) {
        val events = ports.calendar?.let { calendar ->
            val start = startOfDayMs(date)
            val end = startOfDayMs(Time.plusDaysIso(date, 1))
            calendar.readRange(start, end).getOrElse { emptyList() }
        }.orEmpty()
        externalEvents.value = events
    }

    private fun buildUi(
        state: CanonicalLifeState,
        date: String,
        external: List<ExternalEvent>,
    ): TodayUiState {
        val lifeItems = ports.timeline.forDate(state, date).map { item ->
            val (start, end) = resolveTimes(state, item)
            TodayItem(
                item = item,
                sourceGoalLabel = sourceGoalLabel(state, item),
                startMinutes = start,
                endMinutes = end,
            )
        }
        val calendarItems = external
            .filter { it.lifeOsId == null }
            .map { it.toTodayItem() }
        val merged = (lifeItems + calendarItems).sortedWith(
            compareBy({ !it.timed }, { it.item.timeHhmm }),
        )
        val groups = groupTodayItems(merged)
        return TodayUiState(
            selectedDate = date,
            dateTitle = formatDateTitle(date),
            isToday = date == Time.todayIso(),
            focusActive = state.focus.active,
            groups = groups,
            weekDays = weekDays(state, date, external),
            empty = merged.isEmpty(),
        )
    }

    private fun weekDays(
        state: CanonicalLifeState,
        selected: String,
        external: List<ExternalEvent>,
    ): List<WeekDayUi> {
        val today = Time.todayIso()
        val selectedDate = parseDate(selected)
        val monday = selectedDate.with(DayOfWeek.MONDAY)
        return (0L..6L).map { offset ->
            val day = monday.plusDays(offset)
            val iso = day.toString()
            val hasLife = ports.timeline.forDate(state, iso).isNotEmpty()
            val hasCal = iso == selected && external.any { it.lifeOsId == null }
            WeekDayUi(
                dateIso = iso,
                weekdayInitial = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                dayNumber = day.dayOfMonth,
                isToday = iso == today,
                selected = iso == selected,
                hasItems = hasLife || hasCal,
            )
        }
    }
}

private val dateTitleFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH)

internal fun formatDateTitle(dateIso: String): String =
    runCatching { LocalDate.parse(dateIso.take(10)).format(dateTitleFmt) }.getOrDefault(dateIso)

internal fun groupTimeline(items: List<TimelineItem>): List<TimelineGroup> =
    groupTodayItems(items.map { TodayItem(item = it) })

internal fun groupTodayItems(items: List<TodayItem>): List<TimelineGroup> {
    val buckets = linkedMapOf(
        "Morning" to mutableListOf<TodayItem>(),
        "Afternoon" to mutableListOf(),
        "Evening" to mutableListOf(),
        "Anytime" to mutableListOf(),
    )
    items.forEach { item ->
        buckets.getValue(dayPartLabel(item.item)).add(item)
    }
    return buckets.mapNotNull { (title, rows) ->
        if (rows.isEmpty()) null else TimelineGroup(title, rows)
    }
}

internal fun dayPartLabel(item: TimelineItem): String {
    val raw = item.timeHhmm.trim()
    if (raw.isEmpty()) return "Anytime"
    val hour = raw.substringBefore(":").toIntOrNull() ?: return "Anytime"
    return when {
        hour < 12 -> "Morning"
        hour < 17 -> "Afternoon"
        else -> "Evening"
    }
}

internal fun startOfDayMs(dateIso: String, zone: ZoneId = ZoneId.systemDefault()): Long =
    parseDate(dateIso).atStartOfDay(zone).toInstant().toEpochMilli()

internal fun minutesOfDay(hhmm: String): Int? {
    val time = Time.parseHhmm(hhmm) ?: return null
    return time.hour * 60 + time.minute
}

internal fun minutesOfEpoch(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
    val time = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
    return time.hour * 60 + time.minute
}

internal fun nowMinutes(): Int = minutesOfEpoch(Time.nowEpochMs())

internal fun formatCountdown(deltaMinutes: Int, ending: Boolean): String {
    val abs = kotlin.math.abs(deltaMinutes)
    val hours = abs / 60
    val mins = abs % 60
    val body = when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
    return if (ending) "ends in $body" else "starts in $body"
}

internal fun elapsedFraction(startMinutes: Int?, endMinutes: Int?, now: Int): Float {
    if (startMinutes == null || endMinutes == null || endMinutes <= startMinutes) return 0f
    if (now <= startMinutes) return 0f
    if (now >= endMinutes) return 1f
    return (now - startMinutes).toFloat() / (endMinutes - startMinutes).toFloat()
}

internal fun resolveHero(items: List<TodayItem>, isToday: Boolean, now: Int = nowMinutes()): TodayItem? {
    val timed = items.filter { it.timed && it.startMinutes != null && !it.item.done }
        .sortedBy { it.startMinutes }
    if (!isToday) return timed.firstOrNull() ?: items.firstOrNull()
    val current = timed.firstOrNull { item ->
        val start = item.startMinutes ?: return@firstOrNull false
        val end = item.endMinutes ?: (start + 30)
        now in start until end
    }
    if (current != null) return current
    return timed.firstOrNull { (it.startMinutes ?: Int.MAX_VALUE) > now }
}

private fun parseDate(dateIso: String): LocalDate =
    runCatching { LocalDate.parse(dateIso.take(10)) }.getOrElse { LocalDate.now() }

private fun sourceGoalLabel(state: CanonicalLifeState, item: TimelineItem): String? {
    val goalId = when (item.kind) {
        TimelineKind.TASK -> state.tasks.find { it.id == item.refId }?.let { it.sourceGoalId ?: it.goalId }
        TimelineKind.HABIT -> state.habits.find { it.id == item.refId }?.sourceGoalId
        TimelineKind.BLOCK -> state.scheduleBlocks.find { it.id == item.refId }?.sourceGoalId
        TimelineKind.EVENT -> state.events.find { it.id == item.refId }?.sourceGoalId
        TimelineKind.ALARM -> state.alarms.find { it.id == item.refId }?.sourceGoalId
    }
    return goalId?.let { id -> state.goals.find { it.id == id }?.title }?.takeIf { it.isNotBlank() }
}

private fun resolveTimes(state: CanonicalLifeState, item: TimelineItem): Pair<Int?, Int?> {
    val start = minutesOfDay(item.timeHhmm)
    return when (item.kind) {
        TimelineKind.BLOCK -> {
            val block = state.scheduleBlocks.find { it.id == item.refId }
            minutesOfDay(block?.startHhmm ?: item.timeHhmm) to minutesOfDay(block?.endHhmm.orEmpty())
        }
        TimelineKind.EVENT -> {
            val event = state.events.find { it.id == item.refId }
            val eventStart = event?.startIso?.let { isoToMinutes(it) } ?: start
            val eventEnd = event?.endIso?.let { isoToMinutes(it) } ?: eventStart?.plus(60)
            eventStart to eventEnd
        }
        else -> start to start?.plus(30)
    }
}

private fun isoToMinutes(iso: String): Int? {
    val dt = Time.parseIsoOrNull(iso) ?: return null
    return dt.hour * 60 + dt.minute
}

private fun ExternalEvent.toTodayItem(): TodayItem {
    val start = minutesOfEpoch(startEpochMs)
    val end = minutesOfEpoch(endEpochMs).takeIf { it > start } ?: (start + 60)
    return TodayItem(
        item = TimelineItem(
            timeHhmm = Time.formatHhmm(startEpochMs),
            kind = TimelineKind.EVENT,
            title = title,
            subtitle = calendarName.ifBlank { "Google Calendar" },
            refId = "cal-$providerId",
        ),
        external = true,
        startMinutes = start,
        endMinutes = end,
    )
}
