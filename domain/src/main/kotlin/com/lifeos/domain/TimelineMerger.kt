package com.lifeos.domain

import com.lifeos.core.Time
import com.lifeos.core.TimelinePort
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.Hardness
import com.lifeos.core.model.TimelineItem
import com.lifeos.core.model.TimelineKind
import java.time.Instant
import java.time.ZoneId

class TimelineMerger : TimelinePort {
    override fun forDate(state: CanonicalLifeState, dateIso: String): List<TimelineItem> {
        val dow = Time.isoDayOfWeek(dateIso)
        val today = Time.todayIso()
        val items = mutableListOf<TimelineItem>()

        state.alarms.filter { it.enabled && alarmOccursOn(it.triggerAtEpochMs, it.timeHhmm, dateIso) }.forEach { alarm ->
            items += TimelineItem(
                timeHhmm = alarm.timeHhmm.ifBlank { UNTIMED },
                kind = TimelineKind.ALARM,
                title = alarm.label.ifBlank { alarm.timeHhmm },
                subtitle = alarm.label,
                refId = alarm.id,
            )
        }

        state.events.filter { dateOf(it.startIso) == dateIso }.forEach { event ->
            items += TimelineItem(
                timeHhmm = timeOf(event.startIso),
                kind = TimelineKind.EVENT,
                title = event.title,
                refId = event.id,
                hard = event.hardness == Hardness.HARD,
            )
        }

        state.scheduleBlocks.filter { block ->
            (block.daysOfWeek.isNotEmpty() && dow in block.daysOfWeek) || block.dateIso == dateIso
        }.forEach { block ->
            items += TimelineItem(
                timeHhmm = block.startHhmm.ifBlank { UNTIMED },
                kind = TimelineKind.BLOCK,
                title = block.title,
                subtitle = "${block.startHhmm}–${block.endHhmm}",
                refId = block.id,
            )
        }

        state.habits.filter { habit ->
            val days = habit.daysOfWeek.ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }
            dow in days
        }.forEach { habit ->
            items += TimelineItem(
                timeHhmm = habit.timeHhmm.ifBlank { UNTIMED },
                kind = TimelineKind.HABIT,
                title = habit.title,
                done = dateIso in habit.completedDates,
                refId = habit.id,
            )
        }

        state.tasks.filter { !it.done }.forEach { task ->
            val dueDate = dateOf(task.dueIso)
            val overdue = dateIso == today && dueDate != null && dueDate < today
            val dueToday = dueDate == dateIso
            if (!dueToday && !overdue) return@forEach
            items += TimelineItem(
                timeHhmm = timeOf(task.dueIso),
                kind = TimelineKind.TASK,
                title = task.title,
                subtitle = if (overdue) "overdue" else "",
                refId = task.id,
            )
        }

        return items.sortedWith(compareBy({ it.timeHhmm == UNTIMED }, { it.timeHhmm }))
    }

    fun todayFor(state: CanonicalLifeState): List<TimelineItem> = forDate(state, Time.todayIso())

    private fun alarmOccursOn(triggerAtEpochMs: Long?, timeHhmm: String, dateIso: String): Boolean {
        if (triggerAtEpochMs != null) {
            val date = Instant.ofEpochMilli(triggerAtEpochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
            return date == dateIso
        }
        return Time.parseHhmm(timeHhmm) != null
    }

    private fun dateOf(iso: String?): String? =
        Time.parseIsoOrNull(iso)?.toLocalDate()?.toString()

    private fun timeOf(iso: String?): String {
        val dt = Time.parseIsoOrNull(iso) ?: return UNTIMED
        val time = dt.toLocalTime()
        return if (time.hour == 0 && time.minute == 0 && !iso.orEmpty().contains('T')) {
            UNTIMED
        } else {
            Time.formatHhmm(dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }

    companion object {
        private const val UNTIMED = "--:--"
    }
}
