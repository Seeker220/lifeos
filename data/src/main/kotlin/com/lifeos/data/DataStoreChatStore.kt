package com.lifeos.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lifeos.core.ChatStore
import com.lifeos.core.LifeOsLog
import com.lifeos.core.model.ChatTranscript
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DataStoreChatStore(
    private val context: Context,
    scope: CoroutineScope,
) : ChatStore {
    private val key = stringPreferencesKey("chat_v1")
    private val mutex = Mutex()
    private val _transcript = MutableStateFlow(ChatTranscript())
    override val transcript: StateFlow<ChatTranscript> = _transcript.asStateFlow()
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                runCatching {
                    val raw = context.lifeOsDataStore.data.first()[key]
                    if (!raw.isNullOrBlank()) {
                        _transcript.value =
                            LifeOsJson.instance.decodeFromString(ChatTranscript.serializer(), raw)
                    }
                }.onFailure {
                    LifeOsLog.d("LifeOS/Data", "chat decode failed: ${it.message}")
                    _transcript.value = ChatTranscript()
                }
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    override suspend fun awaitLoaded() {
        loaded.await()
    }

    override suspend fun mutate(block: (ChatTranscript) -> ChatTranscript) {
        // Writing before the first read would replace the saved transcript with an empty one.
        loaded.await()
        mutex.withLock {
            val next = block(_transcript.value)
            runCatching {
                val encoded = LifeOsJson.instance.encodeToString(ChatTranscript.serializer(), next)
                context.lifeOsDataStore.edit { it[key] = encoded }
            }.onFailure {
                LifeOsLog.d("LifeOS/Data", "chat write failed: ${it.message}")
            }
            _transcript.value = next
        }
    }
}
