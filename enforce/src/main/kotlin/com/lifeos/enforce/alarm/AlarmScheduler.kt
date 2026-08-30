package com.lifeos.enforce.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lifeos.core.LifeOsLog
import com.lifeos.core.Time
import com.lifeos.core.model.AlarmSpec
import java.util.concurrent.ConcurrentHashMap

class AlarmScheduler(private val context: Context) {
    private val scheduledIds = ConcurrentHashMap.newKeySet<String>()

    fun schedule(spec: AlarmSpec) {
        if (!spec.enabled) {
            LifeOsLog.d("LifeOS/Alarm", "skip disabled ${spec.id}")
            return
        }
        val am = context.getSystemService(AlarmManager::class.java)
        if (am == null) {
            LifeOsLog.d("LifeOS/Alarm", "AlarmManager missing; skip ${spec.id}")
            return
        }
        if (!am.canScheduleExactAlarms()) {
            LifeOsLog.d("LifeOS/Alarm", "exact alarms not granted; skip schedule ${spec.id}")
            return
        }
        val triggerAt = spec.triggerAtEpochMs ?: Time.nextOccurrenceEpochMs(spec.timeHhmm)
        val now = Time.nowEpochMs()
        if (triggerAt < now - PAST_GRACE_MS) {
            LifeOsLog.d("LifeOS/Alarm", "skip past alarm ${spec.id} trigger=$triggerAt now=$now")
            return
        }
        val show = PendingIntent.getActivity(
            context,
            0,
            mainActivityIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fire = PendingIntent.getBroadcast(
            context,
            spec.id.hashCode(),
            fireIntent(spec, triggerAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fire)
            scheduledIds.add(spec.id)
            LifeOsLog.d("LifeOS/Alarm", "scheduled ${spec.id} at $triggerAt label=${spec.label}")
        }.onFailure {
            LifeOsLog.d("LifeOS/Alarm", "setAlarmClock failed ${spec.id}: ${it.message}")
        }
    }

    fun cancel(alarmId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val fire = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { am.cancel(fire) }
        runCatching { fire.cancel() }
        scheduledIds.remove(alarmId)
        LifeOsLog.d("LifeOS/Alarm", "cancel $alarmId")
    }

    fun rescheduleAll(alarms: List<AlarmSpec>) {
        scheduledIds.toList().forEach { cancel(it) }
        alarms.forEach { schedule(it) }
        LifeOsLog.d("LifeOS/Alarm", "rescheduleAll n=${alarms.size}")
    }

    private fun fireIntent(spec: AlarmSpec, triggerAt: Long): Intent =
        Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, spec.id)
            .putExtra(AlarmReceiver.EXTRA_LABEL, spec.label)
            .putExtra(AlarmReceiver.EXTRA_PERSONA_LINE, spec.personaLine)
            .putExtra(
                AlarmReceiver.EXTRA_TIME_HHMM,
                spec.timeHhmm.ifBlank { Time.formatHhmm(triggerAt) },
            )

    private fun mainActivityIntent(): Intent =
        Intent().setClassName(context.packageName, "${context.packageName}.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val PAST_GRACE_MS = 60_000L
    }
}
