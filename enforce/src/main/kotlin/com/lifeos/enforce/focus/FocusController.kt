package com.lifeos.enforce.focus

import android.content.Context
import android.content.Intent
import com.lifeos.core.LifeOsLog
import com.lifeos.core.LifeStateStore
import com.lifeos.core.Time
import com.lifeos.core.model.EnforcementRules
import com.lifeos.core.model.FocusRules
import com.lifeos.core.model.FocusSession
import com.lifeos.enforce.EnforceHolder
import com.lifeos.enforce.usage.UsageStatsHelper

class FocusController(
    private val context: Context,
    private val store: LifeStateStore,
) {
    private val usage = UsageStatsHelper(context)

    fun start(session: FocusSession) {
        val state = store.state.value
        val goal = nearestHardGoal(state)
        EnforceHolder.rules = EnforcementRules(
            focus = FocusRules(
                active = true,
                mode = session.mode,
                packages = session.packages,
                startedAtEpochMs = Time.nowEpochMs(),
                endsAtEpochMs = session.endsAtEpochMs,
                windows = state.focus.windows,
            ),
            timeouts = state.appTimeouts,
            demoStrictTimeouts = state.settings.demoStrictTimeouts,
            activeGoalLabel = goal?.title,
            activeGoalDeadlineIso = goal?.deadlineIso,
        )
        LifeOsLog.d("LifeOS/Focus", "startFocus mode=${session.mode} pkgs=${session.packages.size}")
        startService()
    }

    fun stop() {
        val current = EnforceHolder.rules
        if (current != null) {
            EnforceHolder.rules = current.copy(focus = current.focus.copy(active = false))
        }
        val state = store.state.value
        val stillNeeded = shouldRun(state, EnforceHolder.rules)
        LifeOsLog.d("LifeOS/Focus", "stopFocus stillNeeded=$stillNeeded")
        if (!stillNeeded) {
            runCatching { context.stopService(Intent(context, FocusService::class.java)) }
        }
    }

    fun applyRules(rules: EnforcementRules) {
        EnforceHolder.rules = rules
        LifeOsLog.d(
            "LifeOS/Focus",
            "applyRules active=${rules.focus.active} timeouts=${rules.timeouts.size} windows=${rules.focus.windows.size}",
        )
        if (shouldRun(store.state.value, rules)) {
            startService()
        } else {
            runCatching { context.stopService(Intent(context, FocusService::class.java)) }
        }
    }

    fun usageTodayMinutes(packages: List<String>): Map<String, Int> =
        usage.usageTodayMinutes(packages)

    fun usageTodayAll(): Map<String, Int> = usage.usageTodayAll()

    private fun startService() {
        runCatching {
            context.startForegroundService(Intent(context, FocusService::class.java))
        }.onFailure {
            LifeOsLog.d("LifeOS/Focus", "startForegroundService failed: ${it.message}")
        }
    }
}
