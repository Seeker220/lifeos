package com.lifeos.enforce.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager

object NotificationChannels {
    /** AccentVivid — status-bar / heads-up tint. Frozen hex, not a theme token. */
    val ACCENT: Int = 0xFF4C8DFF.toInt()

    /** S3 focus FGS. Id is frozen — do not rename. */
    const val FOCUS = "lifeos_focus"
    const val ALARM = "lifeos_alarm"
    const val VPN = "lifeos_vpn"
    const val NOTIF_FOCUS = 1001
    const val NOTIF_ALARM = 1002
    const val NOTIF_VPN = 1003

    fun ensureAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(FOCUS, "Focus", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                lightColor = ACCENT
            },
        )
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setLegacyStreamType(AudioManager.STREAM_ALARM)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(ALARM, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setBypassDnd(true)
                setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI, alarmAttrs)
                enableVibration(true)
                lightColor = ACCENT
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(VPN, "Network guard", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                lightColor = ACCENT
            },
        )
    }
}
