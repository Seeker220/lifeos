package com.lifeos.enforce.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.lifeos.core.LifeOsLog
import com.lifeos.core.Time
import com.lifeos.enforce.R
import com.lifeos.enforce.notify.NotificationChannels

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISS) {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(NotificationChannels.NOTIF_ALARM)
            LifeOsLog.d("LifeOS/Alarm", "dismiss action")
            return
        }

        NotificationChannels.ensureAll(context)
        val pm = context.getSystemService(PowerManager::class.java)
        val wake = runCatching {
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lifeos:alarm")?.also {
                it.acquire(WAKE_MS)
            }
        }.getOrNull()
        try {
            handOff(context, intent)
        } finally {
            runCatching { if (wake?.isHeld == true) wake.release() }
        }
    }

    private fun handOff(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
            .ifBlank { context.getString(R.string.alarm_default_label) }
        val persona = intent.getStringExtra(EXTRA_PERSONA_LINE).orEmpty()
        val timeHhmm = intent.getStringExtra(EXTRA_TIME_HHMM).orEmpty()
            .ifBlank { Time.formatHhmm(Time.nowEpochMs()) }

        val activityIntent = Intent(context, AlarmActivity::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_LABEL, label)
            .putExtra(EXTRA_PERSONA_LINE, persona)
            .putExtra(EXTRA_TIME_HHMM, timeHhmm)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val activityPi = PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissPi = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode() xor DISMISS_XOR,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val nm = context.getSystemService(NotificationManager::class.java)
        val canFsi = if (Build.VERSION.SDK_INT >= 34) {
            nm?.canUseFullScreenIntent() == true
        } else {
            true
        }
        val canOverlay = Settings.canDrawOverlays(context)

        if (canFsi) {
            postAlarmNotification(context, label, persona, activityPi, dismissPi, fullScreen = true)
        } else {
            postAlarmNotification(context, label, persona, activityPi, dismissPi, fullScreen = false)
        }

        if (canOverlay) {
            runCatching { context.startActivity(activityIntent) }
                .onFailure { LifeOsLog.d("LifeOS/Alarm", "startActivity failed: ${it.message}") }
        }

        LifeOsLog.d(
            "LifeOS/Alarm",
            "fired id=$alarmId fsi=$canFsi overlay=$canOverlay path=${launchPath(canFsi, canOverlay)}",
        )
    }

    private fun postAlarmNotification(
        context: Context,
        label: String,
        persona: String,
        activityPi: PendingIntent,
        dismissPi: PendingIntent,
        fullScreen: Boolean,
    ) {
        val builder = NotificationCompat.Builder(context, NotificationChannels.ALARM)
            .setSmallIcon(R.drawable.alarm_ic_stat)
            .setContentTitle(label)
            .setContentText(persona.ifBlank { label })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(activityPi)
            .addAction(0, context.getString(R.string.alarm_dismiss), dismissPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (fullScreen) {
            builder.setFullScreenIntent(activityPi, true)
        }
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(NotificationChannels.NOTIF_ALARM, builder.build())
        }.onFailure {
            LifeOsLog.d("LifeOS/Alarm", "notify failed: ${it.message}")
        }
    }

    private fun launchPath(canFsi: Boolean, canOverlay: Boolean): String = when {
        canOverlay && !canFsi -> "startActivity"
        canFsi && canOverlay -> "fsi+startActivity"
        canFsi -> "fullScreenIntent"
        else -> "notification-only"
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_PERSONA_LINE = "extra_persona_line"
        const val EXTRA_TIME_HHMM = "extra_time_hhmm"
        const val ACTION_DISMISS = "com.lifeos.enforce.alarm.ACTION_DISMISS"
        private const val WAKE_MS = 10_000L
        private const val DISMISS_XOR = 0x4C1F0000
    }
}
