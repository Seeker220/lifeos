package com.lifeos.enforce.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifeos.core.LifeOsLog
import com.lifeos.enforce.EnforceHolder
import com.lifeos.enforce.focus.FocusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        // The store reads from disk asynchronously, so reading it inline right after a cold
        // start sees an empty state and would silently drop every saved alarm.
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val store = EnforceHolder.lifeState
                if (store == null) {
                    LifeOsLog.d("LifeOS/Alarm", "BootReceiver: lifeState null; skip")
                    return@launch
                }
                withTimeoutOrNull(LOAD_TIMEOUT_MS) { store.awaitLoaded() }
                val state = store.state.value
                val scheduler = EnforceHolder.alarms
                if (scheduler == null) {
                    LifeOsLog.d("LifeOS/Alarm", "BootReceiver: AlarmScheduler null; skip reschedule")
                } else {
                    scheduler.rescheduleAll(state.alarms)
                }
                if (state.appTimeouts.isNotEmpty() || state.focus.active) {
                    runCatching {
                        context.startForegroundService(Intent(context, FocusService::class.java))
                    }.onFailure {
                        LifeOsLog.d("LifeOS/Alarm", "BootReceiver FocusService: ${it.message}")
                    }
                }
                LifeOsLog.d(
                    "LifeOS/Alarm",
                    "BootReceiver $action alarms=${state.alarms.size} " +
                        "timeouts=${state.appTimeouts.size} focus=${state.focus.active}",
                )
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
