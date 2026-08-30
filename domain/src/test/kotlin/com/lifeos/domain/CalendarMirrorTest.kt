package com.lifeos.domain

import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.Settings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMirrorTest {

    @Test
    fun syncDisabledWritesNothing() = runTest {
        val store = FakeLifeStateStore()
        val cal = RecordingCalendarPort()
        val exec = ActionExecutor(store, RecordingEnforceGateway(), FakeAppCatalog(), calendar = cal)
        exec.execute(
            listOf(Action.CreateEvent(title = "Exam", startIso = "2026-09-01T10:00")),
            ActionOrigin.USER,
        )
        assertEquals(1, store.state.value.events.size)
        assertEquals(0, cal.ensureCalls)
        assertEquals(0, cal.upsertCalls)
        assertEquals(0, cal.deleteCalls)
    }

    @Test
    fun createEventUpsertsWhenEnabled() = runTest {
        val store = FakeLifeStateStore(CanonicalLifeState(settings = Settings(calendarSyncEnabled = true)))
        val cal = RecordingCalendarPort()
        val exec = ActionExecutor(store, RecordingEnforceGateway(), FakeAppCatalog(), calendar = cal)
        exec.execute(
            listOf(Action.CreateEvent(title = "Exam", startIso = "2026-09-01T10:00")),
            ActionOrigin.USER,
        )
        assertEquals(1, cal.ensureCalls)
        assertEquals(1, cal.upsertCalls)
        assertEquals("Exam", cal.upserts.single().title)
        assertEquals(store.state.value.events.single().id, cal.upserts.single().lifeOsId)
    }

    @Test
    fun revertDeletesMirroredBlocks() = runTest {
        val store = FakeLifeStateStore(CanonicalLifeState(settings = Settings(calendarSyncEnabled = true)))
        val cal = RecordingCalendarPort()
        val exec = ActionExecutor(store, RecordingEnforceGateway(), FakeAppCatalog(), calendar = cal)
        exec.execute(
            listOf(
                Action.CreateGoal(id = "g1", title = "G"),
                Action.AddScheduleBlock(
                    title = "Grind",
                    startHhmm = "19:00",
                    endHhmm = "21:00",
                    dateIso = Time.todayIso(),
                    sourceGoalId = "g1",
                ),
            ),
            ActionOrigin.AGENT,
        )
        val blockId = store.state.value.scheduleBlocks.single().id
        assertTrue(cal.upserts.any { it.lifeOsId == blockId })

        exec.execute(listOf(Action.RevertExpansion("g1")), ActionOrigin.USER)
        assertTrue(cal.deletes.contains(blockId))
    }
}
