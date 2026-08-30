package com.lifeos.domain

import com.lifeos.core.DemoPackages
import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.BlockKind
import com.lifeos.core.model.FocusMode
import com.lifeos.core.model.FocusWindow
import com.lifeos.core.model.Hardness
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExecutorTest {

    @Test
    fun createGoalTwiceSameTitleYieldsOneGoal() = runTest {
        val store = FakeLifeStateStore()
        val exec = ActionExecutor(store, RecordingEnforceGateway(), FakeAppCatalog())
        exec.execute(
            listOf(
                Action.CreateGoal(title = "Crack Google interview", hardness = Hardness.HARD),
                Action.CreateGoal(title = "crack google interview", deadlineIso = Time.plusDaysIso(Time.todayIso(), 30)),
            ),
            ActionOrigin.AGENT,
        )
        assertEquals(1, store.state.value.goals.size)
        assertTrue(store.state.value.goals.single().title.equals("Crack Google interview", ignoreCase = true))
        assertEquals(Time.plusDaysIso(Time.todayIso(), 30), store.state.value.goals.single().deadlineIso)
    }

    @Test
    fun interviewExpansionIsOneMutateAndPropagatesSourceGoalId() = runTest {
        val store = FakeLifeStateStore()
        val enforce = RecordingEnforceGateway(store)
        val exec = ActionExecutor(store, enforce, FakeAppCatalog())
        val report = exec.execute(interviewExpansion(), ActionOrigin.AGENT)
        assertEquals(1, store.mutateCount)
        assertTrue(report.skipped.isEmpty())

        val state = store.state.value
        assertTrue(state.tasks.isNotEmpty())
        assertTrue(state.habits.isNotEmpty())
        assertTrue(state.scheduleBlocks.isNotEmpty())
        assertTrue(state.alarms.isNotEmpty())
        assertTrue(state.appTimeouts.isNotEmpty())
        assertTrue(state.focus.windows.isNotEmpty())

        state.tasks.forEach { assertEquals(GOAL_ID, it.sourceGoalId) }
        state.habits.forEach { assertEquals(GOAL_ID, it.sourceGoalId) }
        state.scheduleBlocks.forEach { assertEquals(GOAL_ID, it.sourceGoalId) }
        state.alarms.forEach { assertEquals(GOAL_ID, it.sourceGoalId) }
        state.appTimeouts.forEach { assertEquals(GOAL_ID, it.sourceGoalId) }
        state.focus.windows.forEach { assertEquals(GOAL_ID, it.sourceGoalId) }
    }

    @Test
    fun revertExpansionRemovesChildrenAndCancelsAlarms() = runTest {
        val store = FakeLifeStateStore()
        val enforce = RecordingEnforceGateway(store)
        val exec = ActionExecutor(store, enforce, FakeAppCatalog())
        exec.execute(interviewExpansion(), ActionOrigin.AGENT)
        val alarmIds = store.state.value.alarms.map { it.id }
        assertTrue(alarmIds.isNotEmpty())

        enforce.cancelledAlarmIds.clear()
        exec.execute(listOf(Action.RevertExpansion(GOAL_ID)), ActionOrigin.USER)

        val state = store.state.value
        assertTrue(state.tasks.none { it.sourceGoalId == GOAL_ID })
        assertTrue(state.habits.none { it.sourceGoalId == GOAL_ID })
        assertTrue(state.scheduleBlocks.none { it.sourceGoalId == GOAL_ID })
        assertTrue(state.alarms.none { it.sourceGoalId == GOAL_ID })
        assertTrue(state.appTimeouts.none { it.sourceGoalId == GOAL_ID })
        assertTrue(state.focus.windows.none { it.sourceGoalId == GOAL_ID })
        assertTrue(state.goals.first { it.id == GOAL_ID }.archived)
        assertEquals(alarmIds.toSet(), enforce.cancelledAlarmIds.toSet())
        assertEquals(alarmIds.size, enforce.cancelledAlarmIds.size)
    }

    @Test
    fun badActionIsSkippedWhileSiblingsApply() = runTest {
        val store = FakeLifeStateStore()
        val exec = ActionExecutor(store, RecordingEnforceGateway(), FakeAppCatalog())
        val report = exec.execute(
            listOf(
                Action.CreateGoal(title = "", hardness = Hardness.HARD),
                Action.CreateTask(title = "Sibling task", sourceGoalId = "g1"),
            ),
            ActionOrigin.AGENT,
        )
        assertEquals(1, report.skipped.size)
        assertEquals("create_goal", report.skipped.single().type)
        assertEquals("missing title", report.skipped.single().reason)
        assertEquals(1, report.applied.size)
        assertEquals(1, store.state.value.tasks.size)
        assertTrue(store.state.value.goals.isEmpty())
    }

    @Test
    fun instagramAliasStoresSubstitute() = runTest {
        val store = FakeLifeStateStore()
        val exec = ActionExecutor(store, RecordingEnforceGateway(), FakeAppCatalog())
        exec.execute(
            listOf(Action.SetAppTimeout(packageName = "instagram", limitMinutes = 30)),
            ActionOrigin.AGENT,
        )
        val stored = store.state.value.appTimeouts.single()
        assertEquals(DemoPackages.YOUTUBE, stored.packageName)
        assertEquals(30, stored.limitMinutes)
        assertFalse(store.state.value.appTimeouts.any { it.packageName == DemoPackages.INSTAGRAM })
    }

    @Test
    fun systemUiTimeoutIsDropped() = runTest {
        val store = FakeLifeStateStore()
        val exec = ActionExecutor(store, RecordingEnforceGateway(), FakeAppCatalog())
        exec.execute(
            listOf(Action.SetAppTimeout(packageName = "com.android.systemui", limitMinutes = 15)),
            ActionOrigin.AGENT,
        )
        assertTrue(store.state.value.appTimeouts.isEmpty())
        assertTrue(store.state.value.appTimeouts.none { it.packageName in DemoPackages.ALWAYS_ALLOW })
    }

    @Test
    fun focusStartFiresAfterStateWrite() = runTest {
        val store = FakeLifeStateStore()
        val enforce = RecordingEnforceGateway(store)
        val exec = ActionExecutor(store, enforce, FakeAppCatalog())
        exec.execute(
            listOf(
                Action.FocusStart(
                    mode = FocusMode.BLACKLIST,
                    packages = listOf(DemoPackages.YOUTUBE),
                    minutes = 25,
                ),
            ),
            ActionOrigin.USER,
        )
        assertEquals(true, enforce.focusActiveAtStartFocus)
        assertTrue(store.state.value.focus.active)
        assertTrue(enforce.calls.indexOf("startFocus") < enforce.calls.indexOf("applyRules"))
    }

    private fun interviewExpansion(): List<Action> {
        val today = Time.todayIso()
        return listOf(
            Action.CreateGoal(
                id = GOAL_ID,
                title = "Crack Google interview",
                deadlineIso = Time.plusDaysIso(today, 30),
                hardness = Hardness.HARD,
            ),
            Action.CreateHabit(
                title = "LeetCode daily",
                daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                timeHhmm = "19:00",
                sourceGoalId = GOAL_ID,
            ),
            Action.CreateHabit(
                title = "Mock interview",
                daysOfWeek = listOf(6),
                timeHhmm = "10:00",
                sourceGoalId = GOAL_ID,
            ),
            Action.AddScheduleBlock(
                title = "Interview grind",
                startHhmm = "19:00",
                endHhmm = "21:00",
                kind = BlockKind.STUDY,
                daysOfWeek = listOf(1, 2, 3, 4, 5),
                sourceGoalId = GOAL_ID,
            ),
            Action.AddScheduleBlock(
                title = "Weekend mock block",
                startHhmm = "10:00",
                endHhmm = "12:00",
                kind = BlockKind.STUDY,
                daysOfWeek = listOf(6),
                sourceGoalId = GOAL_ID,
            ),
            Action.CreateTask(
                title = "Graphs and trees set",
                dueIso = Time.plusDaysIso(today, 3),
                estMinutes = 120,
                sourceGoalId = GOAL_ID,
            ),
            Action.CreateTask(
                title = "System design notes: caching, sharding",
                dueIso = Time.plusDaysIso(today, 7),
                estMinutes = 90,
                sourceGoalId = GOAL_ID,
            ),
            Action.SetAppTimeout(packageName = DemoPackages.INSTAGRAM, limitMinutes = 30, sourceGoalId = GOAL_ID),
            Action.SetAppTimeout(packageName = DemoPackages.YOUTUBE, limitMinutes = 45, sourceGoalId = GOAL_ID),
            Action.SetFocusWindows(
                windows = listOf(
                    FocusWindow(
                        daysOfWeek = listOf(1, 2, 3, 4, 5),
                        startHhmm = "19:00",
                        endHhmm = "21:00",
                        mode = FocusMode.BLACKLIST,
                        packages = listOf(DemoPackages.INSTAGRAM, DemoPackages.YOUTUBE),
                    ),
                ),
                sourceGoalId = GOAL_ID,
            ),
            Action.SetAlarm(
                label = "bedtime-check",
                timeHhmm = "22:30",
                personaLine = "LeetCode done? Don't lie to me.",
                sourceGoalId = GOAL_ID,
            ),
            Action.Remember(fact = "Google interview in one month; strict on social app timeouts"),
        )
    }

    companion object {
        private const val GOAL_ID = "g_google"
    }
}
