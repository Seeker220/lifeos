package com.lifeos.domain

import com.lifeos.core.model.AlarmSpec
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.Event
import com.lifeos.core.model.Habit
import com.lifeos.core.model.ScheduleBlock
import com.lifeos.core.model.TimelineKind
import com.lifeos.core.model.Todo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TimelineMergerTest {

    @Test
    fun forDateSortsByTimeWithUntimedLast() {
        val date = "2026-08-31"
        val dow = LocalDate.parse(date).dayOfWeek.value
        val zone = ZoneId.systemDefault()
        val alarmEpoch = LocalDate.parse(date).atTime(LocalTime.of(7, 0)).atZone(zone).toInstant().toEpochMilli()
        val state = CanonicalLifeState(
            alarms = listOf(
                AlarmSpec(id = "a1", label = "wake", timeHhmm = "07:00", triggerAtEpochMs = alarmEpoch),
            ),
            events = listOf(
                Event(id = "e1", title = "Standup", startIso = "${date}T09:15"),
            ),
            scheduleBlocks = listOf(
                ScheduleBlock(
                    id = "b1",
                    title = "Deep work",
                    startHhmm = "14:00",
                    endHhmm = "16:00",
                    daysOfWeek = listOf(dow),
                ),
            ),
            habits = listOf(
                Habit(id = "h1", title = "LeetCode", daysOfWeek = listOf(dow), timeHhmm = "19:00"),
            ),
            tasks = listOf(
                Todo(id = "t1", title = "Untimed due", dueIso = date),
                Todo(id = "t2", title = "Timed due", dueIso = "${date}T11:30"),
            ),
        )
        val items = TimelineMerger().forDate(state, date)
        val times = items.map { it.timeHhmm }
        assertEquals(listOf("07:00", "09:15", "11:30", "14:00", "19:00", "--:--"), times)
        assertEquals(TimelineKind.ALARM, items.first().kind)
        assertEquals("a1", items.first().refId)
        assertEquals("--:--", items.last().timeHhmm)
        assertEquals("t1", items.last().refId)
        assertTrue(items.dropLast(1).all { it.timeHhmm != "--:--" })
    }
}
