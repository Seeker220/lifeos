package com.lifeos.app

import com.lifeos.agent.OfflineFallbacks
import com.lifeos.core.Ids
import com.lifeos.core.LifeOsLog
import com.lifeos.core.Ports
import com.lifeos.core.Time
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.ChatMessage
import com.lifeos.core.model.ChatRole
import com.lifeos.core.model.ChatTranscript

object DemoSeed {
    suspend fun seed(ports: Ports) {
        ports.lifeState.mutate { CanonicalLifeState() }
        ports.chat.mutate { ChatTranscript() }
        val expansion = OfflineFallbacks.match(
            "I want to crack a Google interview in 1 month",
            CanonicalLifeState(),
        )
        ports.executor.execute(expansion.actions, ActionOrigin.AGENT)
        ports.lifeState.mutate {
            it.copy(
                settings = it.settings.copy(
                    demoStrictTimeouts = true,
                    onboardingComplete = true,
                ),
            )
        }
    }

    /**
     * Reports what UsageStatsManager actually returns, so a device that silently denies
     * usage access can be told apart from one that simply has no screen time yet.
     */
    suspend fun usageReport(ports: Ports): String {
        val apps = ports.apps.launchableApps().map { it.packageName }
        val usage = ports.enforce.usageTodayMinutes(apps)
        val top = usage.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(8)
        val report = if (top.isEmpty()) {
            "usage access returned NOTHING for ${apps.size} apps"
        } else {
            "usage today: " + top.joinToString { "${it.key.substringAfterLast('.')}=${it.value}m" }
        }
        LifeOsLog.d("LifeOS/Usage", report)
        return report
    }

    suspend fun fillChat(ports: Ports) {
        val now = Time.nowEpochMs()
        val extras = (1..40).map { i ->
            ChatMessage(
                id = Ids.new("msg"),
                role = if (i % 2 == 1) ChatRole.USER else ChatRole.ASSISTANT,
                text = if (i % 2 == 1) "Note $i" else "Logged $i.",
                atEpochMs = now + i,
            )
        }
        ports.chat.mutate { current ->
            current.copy(messages = current.messages + extras)
        }
    }
}
