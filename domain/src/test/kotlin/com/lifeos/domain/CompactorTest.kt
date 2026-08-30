package com.lifeos.domain

import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.ChatMessage
import com.lifeos.core.model.ChatRole
import com.lifeos.core.model.ChatTranscript
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompactorTest {

    @Test
    fun ensureWindowKeepsTwelveMessagesAndLeavesLifeStateUntouched() = runTest {
        val life = FakeLifeStateStore(CanonicalLifeState(personaId = "strict"))
        val emissions = mutableListOf<CanonicalLifeState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            life.state.collect { emissions.add(it) }
        }
        assertEquals(1, emissions.size)

        val chat = FakeChatStore(
            ChatTranscript(
                messages = (1..40).map { i ->
                    ChatMessage(
                        id = "m$i",
                        role = if (i % 2 == 0) ChatRole.ASSISTANT else ChatRole.USER,
                        text = "message $i about the interview grind",
                        atEpochMs = i.toLong(),
                    )
                },
            ),
        )
        Compactor(chat, maxMessages = 12).ensureWindow()

        assertEquals(12, chat.transcript.value.messages.size)
        assertEquals((29..40).map { "m$it" }, chat.transcript.value.messages.map { it.id })
        assertTrue(chat.transcript.value.summary.isNotBlank())
        assertEquals(0, life.mutateCount)
        assertEquals(1, emissions.size)
        assertEquals(CanonicalLifeState(personaId = "strict"), life.state.value)
    }
}
