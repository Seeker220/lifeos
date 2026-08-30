package com.lifeos.core.model

enum class ActionOrigin { AGENT, USER, EMAIL, SYSTEM }

enum class ChangeKind {
    GOAL, TASK, EVENT, HABIT, BLOCK, ALARM, TIMEOUT, FOCUS, NETWORK,
    MEMORY, PERSONA, XP, EMAIL, REVERT,
}

enum class TimelineKind { ALARM, EVENT, BLOCK, HABIT, TASK }

enum class TurnSource { LLM, OFFLINE_FALLBACK, ERROR }

enum class PermissionKind { NOTIFICATIONS, EXACT_ALARMS, USAGE_ACCESS, OVERLAY, VPN }

data class AppliedChange(
    val label: String,
    val kind: ChangeKind,
    val refId: String? = null,
)

data class SkippedAction(
    val type: String,
    val reason: String,
)

data class ExecuteReport(
    val applied: List<AppliedChange> = emptyList(),
    val skipped: List<SkippedAction> = emptyList(),
) {
    val isEmpty: Boolean get() = applied.isEmpty() && skipped.isEmpty()
}

data class LifeStateProjection(val json: String) {
    val charCount: Int get() = json.length
}

data class TimelineItem(
    val timeHhmm: String,
    val kind: TimelineKind,
    val title: String,
    val subtitle: String = "",
    val done: Boolean = false,
    val refId: String = "",
    val hard: Boolean = false,
)

data class FocusSession(
    val mode: FocusMode,
    val packages: List<String>,
    val endsAtEpochMs: Long? = null,
)

/** Everything :enforce needs in one immutable snapshot. */
data class EnforcementRules(
    val focus: FocusRules,
    val timeouts: List<AppTimeout>,
    val demoStrictTimeouts: Boolean = false,
    val activeGoalLabel: String? = null,
    val activeGoalDeadlineIso: String? = null,
)

data class PermissionStatus(
    val notifications: Boolean = false,
    val exactAlarms: Boolean = false,
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val vpnConsented: Boolean = false,
    val fullScreenIntent: Boolean = false,
) {
    val enforcementReady: Boolean get() = usageAccess && overlay
}

data class InstalledApp(
    val packageName: String,
    val label: String,
)

data class LlmConfig(
    val endpoint: String = "",
    val deployment: String = "",
    val apiKey: String = "",
    val apiVersion: String = "2024-10-21",
) {
    val usable: Boolean get() = endpoint.isNotBlank() && deployment.isNotBlank() && apiKey.isNotBlank()
}

data class LlmRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val maxTokens: Int = 1400,
    val temperature: Double = 0.4,
)

data class AgentTurnResult(
    val reply: String,
    val actions: List<Action> = emptyList(),
    val report: ExecuteReport = ExecuteReport(),
    val source: TurnSource = TurnSource.OFFLINE_FALLBACK,
    val expansionGoalId: String? = null,
)
