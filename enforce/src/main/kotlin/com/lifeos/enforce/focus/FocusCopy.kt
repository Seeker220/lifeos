package com.lifeos.enforce.focus

import com.lifeos.core.Time
import com.lifeos.core.model.EnforcementRules
import com.lifeos.core.model.FocusMode
import com.lifeos.core.model.FocusRules

internal object FocusCopy {
    fun deadlinePhrase(rules: EnforcementRules): String? {
        val label = rules.activeGoalLabel?.takeIf { it.isNotBlank() } ?: return null
        val deadline = rules.activeGoalDeadlineIso?.takeIf { it.isNotBlank() } ?: return null
        val days = Time.daysUntil(deadline) ?: return null
        return when {
            days < 0 -> "Overdue."
            days == 0 -> "Due today."
            days == 1 -> "One day left on $label."
            days == 2 -> "Two days left on $label."
            else -> "$days days left on $label."
        }
    }

    fun sourceLabel(rules: EnforcementRules): String? =
        rules.activeGoalLabel?.takeIf { it.isNotBlank() }?.let { "From: $it" }

    fun focusTitle(mode: FocusMode): String = when (mode) {
        FocusMode.BLACKLIST -> "Not now."
        FocusMode.WHITELIST -> "Focus mode."
    }

    fun focusSubtitle(
        mode: FocusMode,
        appLabel: String,
        allowedCount: Int,
        deadline: String?,
    ): String = when (mode) {
        FocusMode.BLACKLIST -> buildString {
            append(appLabel)
            append(" is blocked.")
            if (deadline != null) {
                append(' ')
                append(deadline)
            }
        }
        FocusMode.WHITELIST -> "Only $allowedCount apps are allowed right now."
    }

    fun timeoutTitle(limitMinutes: Int): String = "That's your $limitMinutes minutes."

    fun timeoutSubtitle(appLabel: String, deadline: String?): String = buildString {
        append(appLabel)
        append(" is done for today.")
        if (deadline != null) {
            append(' ')
            append(deadline)
        }
    }

    fun forFocus(appLabel: String, effective: FocusRules, rules: EnforcementRules): OverlayCopy {
        val deadline = deadlinePhrase(rules)
        return OverlayCopy(
            title = focusTitle(effective.mode),
            subtitle = focusSubtitle(effective.mode, appLabel, effective.packages.size, deadline),
            sourceLabel = sourceLabel(rules),
        )
    }

    fun forTimeout(appLabel: String, limitMinutes: Int, rules: EnforcementRules): OverlayCopy {
        return OverlayCopy(
            title = timeoutTitle(limitMinutes),
            subtitle = timeoutSubtitle(appLabel, deadlinePhrase(rules)),
            sourceLabel = sourceLabel(rules),
        )
    }
}

internal data class OverlayCopy(
    val title: String,
    val subtitle: String,
    val sourceLabel: String?,
)
