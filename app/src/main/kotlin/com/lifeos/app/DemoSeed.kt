package com.lifeos.app

import com.lifeos.agent.OfflineFallbacks
import com.lifeos.core.Ids
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
