package com.lifeos.agent

import com.lifeos.core.ActionExecutorPort
import com.lifeos.core.AgentPort
import com.lifeos.core.ChatStore
import com.lifeos.core.CompactorPort
import com.lifeos.core.Ids
import com.lifeos.core.LifeOsLog
import com.lifeos.core.LifeStateStore
import com.lifeos.core.LlmClient
import com.lifeos.core.Personas
import com.lifeos.core.ProjectionPort
import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.AgentTurnResult
import com.lifeos.core.model.ChatMessage
import com.lifeos.core.model.ChatRole
import com.lifeos.core.model.LlmRequest
import com.lifeos.core.model.TurnSource

class AgentController(
    private val chat: ChatStore,
    private val lifeState: LifeStateStore,
    private val executor: ActionExecutorPort,
    private val projection: ProjectionPort,
    private val compactor: CompactorPort,
    private val llm: LlmClient?,
    private val refreshInbox: suspend () -> Unit = {},
) : AgentPort {
    override suspend fun send(userText: String): AgentTurnResult {
        val started = Time.nowEpochMs()
        return try {
            sendInner(userText, started)
        } catch (t: Throwable) {
            LifeOsLog.d(TAG, "send failed ${t::class.simpleName} ms=${Time.nowEpochMs() - started}")
            AgentTurnResult(
                reply = "That one broke on my side and nothing was changed. Say it again.",
                source = TurnSource.OFFLINE_FALLBACK,
            )
        }
    }

    private suspend fun sendInner(userText: String, started: Long): AgentTurnResult {
        compactor.ensureWindow()
        chat.mutate { t ->
            t.copy(messages = t.messages + ChatMessage(
                id = Ids.new("m"),
                role = ChatRole.USER,
                text = userText,
                atEpochMs = Time.nowEpochMs(),
            ))
        }

        if (looksLikeInboxQuery(userText)) {
            runCatching { refreshInbox() }
        }
        val state = lifeState.state.value
        val proj = projection.build(state)
        val persona = Personas.byId(state.personaId)
        val system = SystemPromptBuilder.build(persona, proj, chat.transcript.value.summary)

        val parsed = if (llm != null) {
            llm.complete(LlmRequest(system, userText)).fold(
                onSuccess = { ActionParser.parse(it) },
                onFailure = {
                    LifeOsLog.d(TAG, "llm failed, using offline fallback: ${it.message}")
                    OfflineFallbacks.match(userText, state)
                },
            )
        } else {
            LifeOsLog.d(TAG, "no llm configured, using offline fallback")
            OfflineFallbacks.match(userText, state)
        }

        val report = executor.execute(parsed.actions, ActionOrigin.AGENT)
        val expansionGoalId = parsed.actions.filterIsInstance<Action.CreateGoal>().firstOrNull()?.id
        val chips = report.applied.map { it.label }
        val reply = resolveReply(parsed.reply, chips)

        chat.mutate { t ->
            t.copy(messages = t.messages + ChatMessage(
                id = Ids.new("m"),
                role = ChatRole.ASSISTANT,
                text = reply,
                atEpochMs = Time.nowEpochMs(),
                appliedChips = chips,
                expansionGoalId = expansionGoalId,
            ))
        }

        val skipped = parsed.skipped.size + report.skipped.size
        LifeOsLog.d(
            TAG,
            "turn source=${parsed.source} actions=${parsed.actions.size} " +
                "applied=${report.applied.size} skipped=$skipped ms=${Time.nowEpochMs() - started}",
        )
        return AgentTurnResult(
            reply = reply,
            actions = parsed.actions,
            report = report,
            source = parsed.source,
            expansionGoalId = expansionGoalId,
        )
    }

    private fun looksLikeInboxQuery(text: String): Boolean {
        val lower = text.lowercase()
        return INBOX_HINTS.any { lower.contains(it) }
    }

    private fun resolveReply(parsedReply: String, chips: List<String>): String {
        if (parsedReply.isNotBlank()) return parsedReply
        if (chips.isNotEmpty()) return "Done: " + chips.take(3).joinToString()
        return "Noted. It's on your list."
    }

    companion object {
        private const val TAG = "LifeOS/Agent"
        private val INBOX_HINTS = listOf(
            "email", "inbox", "mail", "exam", "deadline",
            "codeforces", "leetcode", "contest",
        )
    }
}
