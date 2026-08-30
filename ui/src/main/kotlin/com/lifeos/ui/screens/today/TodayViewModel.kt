package com.lifeos.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.Ports
import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.TimelineItem
import com.lifeos.core.model.TimelineKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimelineGroup(
    val title: String,
    val items: List<TimelineItem>,
)

data class TodayUiState(
    val selectedDate: String,
    val dateTitle: String,
    val isToday: Boolean,
    val focusActive: Boolean,
    val groups: List<TimelineGroup>,
    val empty: Boolean,
)

class TodayViewModel(private val ports: Ports) : ViewModel() {
    private val selectedDate = MutableStateFlow(Time.todayIso())

    val uiState: StateFlow<TodayUiState> = combine(
        ports.lifeState.state,
        selectedDate,
    ) { state, date -> buildUi(state, date) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            buildUi(ports.lifeState.state.value, selectedDate.value),
        )

    fun shiftDate(days: Long) {
        selectedDate.update { Time.plusDaysIso(it, days) }
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

    private fun buildUi(state: CanonicalLifeState, date: String): TodayUiState {
        val items = ports.timeline.forDate(state, date)
        val groups = groupTimeline(items)
        return TodayUiState(
            selectedDate = date,
            dateTitle = formatDateTitle(date),
            isToday = date == Time.todayIso(),
            focusActive = state.focus.active,
            groups = groups,
            empty = items.isEmpty(),
        )
    }
}

private val dateTitleFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH)

internal fun formatDateTitle(dateIso: String): String =
    runCatching { LocalDate.parse(dateIso.take(10)).format(dateTitleFmt) }.getOrDefault(dateIso)

internal fun groupTimeline(items: List<TimelineItem>): List<TimelineGroup> {
    val buckets = linkedMapOf(
        "Morning" to mutableListOf<TimelineItem>(),
        "Afternoon" to mutableListOf(),
        "Evening" to mutableListOf(),
        "Anytime" to mutableListOf(),
    )
    items.forEach { item ->
        buckets.getValue(dayPartLabel(item)).add(item)
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
