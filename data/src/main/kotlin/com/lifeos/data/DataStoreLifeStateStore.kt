package com.lifeos.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lifeos.core.LifeOsLog
import com.lifeos.core.LifeStateStore
import com.lifeos.core.model.CanonicalLifeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DataStoreLifeStateStore(
    private val context: Context,
    scope: CoroutineScope,
) : LifeStateStore {
    private val key = stringPreferencesKey("life_state_v1")
    private val mutex = Mutex()
    private val _state = MutableStateFlow(CanonicalLifeState())
    override val state: StateFlow<CanonicalLifeState> = _state.asStateFlow()

    init {
        scope.launch {
            runCatching {
                val raw = context.lifeOsDataStore.data.first()[key]
                if (!raw.isNullOrBlank()) {
                    _state.value = LifeOsJson.instance.decodeFromString(CanonicalLifeState.serializer(), raw)
                }
            }.onFailure {
                LifeOsLog.d("LifeOS/Data", "life state decode failed: ${it.message}")
                _state.value = CanonicalLifeState()
            }
        }
    }

    override suspend fun mutate(block: (CanonicalLifeState) -> CanonicalLifeState) {
        mutex.withLock {
            val next = block(_state.value)
            runCatching {
                val encoded = LifeOsJson.instance.encodeToString(CanonicalLifeState.serializer(), next)
                context.lifeOsDataStore.edit { it[key] = encoded }
            }.onFailure {
                LifeOsLog.d("LifeOS/Data", "life state write failed: ${it.message}")
            }
            _state.value = next
        }
    }
}
