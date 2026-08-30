package com.lifeos.agent

import com.lifeos.core.model.ActionOrigin
import com.lifeos.core.model.ChatRole
import com.lifeos.core.model.TurnSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentControllerTest {
    @Test
    fun sendWithNullLlmAppendsTwoMessagesAndExecutesOnce() = runTest {
        val chat = InMemoryChatStore()
        val executor = RecordingExecutor()
        val controller = AgentController(
            chat = chat,
            lifeState = InMemoryLifeStateStore(),
            executor = executor,
            projection = FixedProjection(),
            compactor = NoOpCompactor(),
            llm = null,
        )
        val result = controller.send("asdfgh")
        assertEquals(2, chat.transcript.value.messages.size)
        assertEquals(ChatRole.USER, chat.transcript.value.messages[0].role)
        assertEquals(ChatRole.ASSISTANT, chat.transcript.value.messages[1].role)
        assertEquals(1, executor.calls.size)
        assertEquals(ActionOrigin.AGENT, executor.calls.single().second)
        assertEquals(TurnSource.OFFLINE_FALLBACK, result.source)
        assertTrue(result.reply.isNotBlank())
    }

    @Test
    fun sendWithFailingLlmUsesOfflineFallback() = runTest {
        val chat = InMemoryChatStore()
        val controller = AgentController(
            chat = chat,
            lifeState = InMemoryLifeStateStore(),
            executor = RecordingExecutor(),
            projection = FixedProjection(),
            compactor = NoOpCompactor(),
            llm = FakeLlmClient(Result.failure(RuntimeException("network down"))),
        )
        val result = controller.send("hello there")
        assertEquals(TurnSource.OFFLINE_FALLBACK, result.source)
        assertTrue(result.reply.isNotBlank())
        assertEquals(2, chat.transcript.value.messages.size)
    }
}
