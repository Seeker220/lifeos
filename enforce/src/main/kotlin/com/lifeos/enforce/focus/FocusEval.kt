package com.lifeos.enforce.focus

import com.lifeos.core.DemoPackages
import com.lifeos.core.Time
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.EnforcementRules
import com.lifeos.core.model.FocusMode
import com.lifeos.core.model.FocusRules
import com.lifeos.core.model.FocusWindow
import com.lifeos.core.model.Goal
import com.lifeos.core.model.Hardness
import java.time.LocalTime

internal fun shouldRun(state: CanonicalLifeState, snapshot: EnforcementRules?): Boolean {
    if (state.focus.active || state.appTimeouts.isNotEmpty() || state.focus.windows.isNotEmpty()) {
        return true
    }
    val rules = snapshot ?: return false
    return rules.focus.active || rules.timeouts.isNotEmpty() || rules.focus.windows.isNotEmpty()
}

internal fun resolveRules(state: CanonicalLifeState, snapshot: EnforcementRules?): EnforcementRules {
    val focus = when {
        state.focus.active -> state.focus
        snapshot?.focus?.active == true -> snapshot.focus
        else -> state.focus.copy(
            windows = state.focus.windows.ifEmpty { snapshot?.focus?.windows.orEmpty() },
        )
    }
    val timeouts = state.appTimeouts.ifEmpty { snapshot?.timeouts.orEmpty() }
    val demoStrict = state.settings.demoStrictTimeouts || snapshot?.demoStrictTimeouts == true
    val goal = nearestHardGoal(state)
    return EnforcementRules(
        focus = focus,
        timeouts = timeouts,
        demoStrictTimeouts = demoStrict,
        activeGoalLabel = snapshot?.activeGoalLabel ?: goal?.title,
        activeGoalDeadlineIso = snapshot?.activeGoalDeadlineIso ?: goal?.deadlineIso,
    )
}

internal fun nearestHardGoal(state: CanonicalLifeState): Goal? =
    state.goals
        .asSequence()
        .filter { !it.archived && it.hardness == Hardness.HARD && !it.deadlineIso.isNullOrBlank() }
        .minByOrNull { Time.daysUntil(it.deadlineIso) ?: Int.MAX_VALUE }

internal fun effectiveFocus(focus: FocusRules, nowEpochMs: Long = Time.nowEpochMs()): FocusRules {
    val endsAt = focus.endsAtEpochMs
    val sessionLive = focus.active && (endsAt == null || nowEpochMs < endsAt)
    if (sessionLive) return focus
    val window = matchingWindow(focus.windows, nowEpochMs) ?: return focus.copy(active = false)
    return focus.copy(active = true, mode = window.mode, packages = window.packages)
}

internal fun matchingWindow(windows: List<FocusWindow>, nowEpochMs: Long): FocusWindow? {
    val dow = Time.isoDayOfWeek(Time.todayIso())
    val now = Time.parseHhmm(Time.formatHhmm(nowEpochMs)) ?: return null
    return windows.firstOrNull { w ->
        (w.daysOfWeek.isEmpty() || dow in w.daysOfWeek) && hhmmInRange(now, w.startHhmm, w.endHhmm)
    }
}

internal fun hhmmInRange(now: LocalTime, startHhmm: String, endHhmm: String): Boolean {
    val start = Time.parseHhmm(startHhmm) ?: return false
    val end = Time.parseHhmm(endHhmm) ?: return false
    return if (!start.isAfter(end)) {
        !now.isBefore(start) && now.isBefore(end)
    } else {
        !now.isBefore(start) || now.isBefore(end)
    }
}

internal fun violates(fg: String, focus: FocusRules): Boolean {
    if (fg in DemoPackages.ALWAYS_ALLOW) return false
    return when (focus.mode) {
        FocusMode.BLACKLIST -> focus.packages.any { packageMatches(it, fg) }
        FocusMode.WHITELIST -> focus.packages.none { packageMatches(it, fg) }
    }
}
