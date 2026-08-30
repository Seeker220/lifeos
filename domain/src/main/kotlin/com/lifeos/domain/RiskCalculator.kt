package com.lifeos.domain

import com.lifeos.core.RiskPort
import com.lifeos.core.Time
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.Hardness
import kotlin.math.max
import kotlin.math.roundToInt

enum class RiskBand { ON_TRACK, AT_RISK, CRITICAL }

class RiskCalculator : RiskPort {
    override fun riskPercent(state: CanonicalLifeState, goalId: String): Int {
        val goal = state.goals.firstOrNull { it.id == goalId } ?: return 0
        if (goal.deadlineIso.isNullOrBlank() || Time.parseIsoOrNull(goal.deadlineIso) == null) return 0

        val open = state.tasks.filter { !it.done && belongsTo(it.goalId, it.sourceGoalId, goal.id) }
        if (open.isEmpty()) return 0

        val daysUntil = Time.daysUntil(goal.deadlineIso)
        val raw = if (daysUntil != null && daysUntil < 0) {
            100
        } else {
            val remainingMin = open.sumOf { it.estMinutes }
            val availableMin = (daysUntil ?: 0) * FOCUS_MINUTES_PER_DAY
            val today = Time.todayIso()
            val cutoff = Time.plusDaysIso(today, -7)
            val completedInLast7Days = state.tasks.count { task ->
                task.done &&
                    belongsTo(task.goalId, task.sourceGoalId, goal.id) &&
                    (dateOf(task.completedAtIso) ?: "") >= cutoff
            }
            val overdueOpenTasks = open.count { task ->
                val due = dateOf(task.dueIso)
                due != null && due < today
            }
            val pressure = remainingMin.toDouble() / max(1, availableMin)
            val completion7d = completedInLast7Days.toDouble() /
                max(1, completedInLast7Days + overdueOpenTasks)
            (100.0 * (0.70 * pressure + 0.30 * (1.0 - completion7d))).roundToInt()
        }

        val scaled = if (goal.hardness == Hardness.SOFT) (raw * 0.6).roundToInt() else raw
        return scaled.coerceIn(0, 100)
    }

    fun band(percent: Int): RiskBand = when {
        percent < 40 -> RiskBand.ON_TRACK
        percent < 70 -> RiskBand.AT_RISK
        else -> RiskBand.CRITICAL
    }

    private fun belongsTo(goalId: String?, sourceGoalId: String?, id: String): Boolean =
        goalId == id || sourceGoalId == id

    private fun dateOf(iso: String?): String? =
        Time.parseIsoOrNull(iso)?.toLocalDate()?.toString()

    companion object {
        const val FOCUS_MINUTES_PER_DAY = 240
    }
}
