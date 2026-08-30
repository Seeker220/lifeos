package com.lifeos.app

import android.app.Application
import android.util.Log
import com.lifeos.core.LifeOsLog
import com.lifeos.enforce.notify.NotificationChannels

class LifeOsApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        LifeOsLog.sink = { tag, msg -> Log.d(tag, msg) }
        NotificationChannels.ensureAll(this)
        container = AppContainer(this).also { it.publish() }
    }
}
