package com.lifeos.app

import android.app.Application
import android.util.Log
import com.lifeos.core.LifeOsLog
import com.lifeos.enforce.notify.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LifeOsApplication : Application() {
    lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        LifeOsLog.sink = { tag, msg -> Log.d(tag, msg) }
        NotificationChannels.ensureAll(this)
        container = AppContainer(this).also { it.publish() }
        // Enforcement runs in services that die with the process, so a cold start has to
        // put the saved rules back in force instead of waiting for the next user action.
        scope.launch { runCatching { container.executor.reapplyEnforcement() } }
    }
}
