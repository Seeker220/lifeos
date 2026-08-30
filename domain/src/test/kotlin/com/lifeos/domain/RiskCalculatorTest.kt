package com.lifeos.domain

import com.lifeos.core.Time
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.Goal
import com.lifeos.core.model.Hardness
import com.lifeos.core.model.Todo
import org.junit.Assert.assertEquals
import org.junit.Test

class RiskCalculatorTest {

    private val risk = RiskCalculator()

    @Test
    fun passedDeadlineWithOpenTasksIs100() {
        val state = CanonicalLifeState(
            goals = listOf(
                Goal(id = "g1", title = "Late", deadlineIso = Time.plusDaysIso(Time.todayIso(), -2), hardness = Hardness.HARD),
            ),
            tasks = listOf(
                Todo(id = "t1", title = "Still open", goalId = "g1", dueIso = Time.plusDaysIso(Time.todayIso(), -1), estMinutes = 60),
            ),
        )
        assertEquals(100, risk.riskPercent(state, "g1"))
        assertEquals(RiskBand.CRITICAL, risk.band(100))
    }

    @Test
    fun noOpenTasksIsZero() {
        val state = CanonicalLifeState(
            goals = listOf(
                Goal(id = "g1", title = "Done", deadlineIso = Time.plusDaysIso(Time.todayIso(), 5), hardness = Hardness.HARD),
            ),
            tasks = listOf(
                Todo(id = "t1", title = "Finished", goalId = "g1", done = true, completedAtIso = Time.nowIso()),
            ),
        )
        assertEquals(0, risk.riskPercent(state, "g1"))
        assertEquals(RiskBand.ON_TRACK, risk.band(0))
    }
}
