package com.lifeos.agent

import com.lifeos.core.ActionExecutorPort
import com.lifeos.core.ChatStore
import com.lifeos.core.CompactorPort
import com.lifeos.core.LifeStateStore
import com.lifeos.core.LlmClient
import com.lifeos.core.ProjectionPort
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.AppliedChange
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.ChangeKind
import com.lifeos.core.model.ChatTranscript
import com.lifeos.core.model.ExecuteReport
import com.lifeos.core.model.LifeStateProjection
import com.lifeos.core.model.LlmRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryChatStore : ChatStore {
    private val _transcript = MutableStateFlow(ChatTranscript())
    override val transcript: StateFlow<ChatTranscript> = _transcript.asStateFlow()
    override suspend fun mutate(block: (ChatTranscript) -> ChatTranscript) {
        _transcript.value = block(_transcript.value)
    }
}

class InMemoryLifeStateStore(
    initial: CanonicalLifeState = CanonicalLifeState(),
) : LifeStateStore {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<CanonicalLifeState> = _state.asStateFlow()
    override suspend fun mutate(block: (CanonicalLifeState) -> CanonicalLifeState) {
        _state.value = block(_state.value)
    }
}

class RecordingExecutor : ActionExecutorPort {
    val calls = mutableListOf<Pair<List<Action>, ActionOrigin>>()
    var reapplyCount = 0
    override suspend fun reapplyEnforcement() {
        reapplyCount++
    }

    override suspend fun execute(actions: List<Action>, origin: ActionOrigin): ExecuteReport {
        calls += actions to origin
        val applied = actions.map { action ->
            AppliedChange(
                label = action::class.simpleName ?: "action",
                kind = ChangeKind.TASK,
                refId = (action as? Action.CreateGoal)?.id,
            )
        }
        return ExecuteReport(applied = applied)
    }
}

class NoOpCompactor : CompactorPort {
    var calls = 0
    override suspend fun ensureWindow() {
        calls++
    }
}

class FixedProjection(
    private val json: String = """{"today":"2026-08-30"}""",
) : ProjectionPort {
    override fun build(state: CanonicalLifeState): LifeStateProjection = LifeStateProjection(json)
}

class FakeLlmClient(
    private val result: Result<String>,
) : LlmClient {
    var calls = 0
    override suspend fun complete(req: LlmRequest): Result<String> {
        calls++
        return result
    }
}
