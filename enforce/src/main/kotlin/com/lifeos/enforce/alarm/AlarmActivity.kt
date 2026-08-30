package com.lifeos.enforce.alarm

import android.app.Activity
import android.app.NotificationManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.lifeos.core.Ids
import com.lifeos.core.LifeOsLog
import com.lifeos.core.Time
import com.lifeos.core.model.AlarmSpec
import com.lifeos.enforce.R
import com.lifeos.enforce.notify.NotificationChannels

class AlarmActivity : Activity() {
    private var tts: TtsSpeaker? = null
    private var vibrator: Vibrator? = null
    private var cleaned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationChannels.ensureAll(this)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alarm)

        val alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID).orEmpty()
        val label = intent.getStringExtra(AlarmReceiver.EXTRA_LABEL).orEmpty()
            .ifBlank { getString(R.string.alarm_default_label) }
        val persona = intent.getStringExtra(AlarmReceiver.EXTRA_PERSONA_LINE).orEmpty()
        val timeHhmm = intent.getStringExtra(AlarmReceiver.EXTRA_TIME_HHMM).orEmpty()
            .ifBlank { Time.formatHhmm(Time.nowEpochMs()) }

        findViewById<TextView>(R.id.alarm_time).text = timeHhmm
        findViewById<TextView>(R.id.alarm_label).text = label
        findViewById<TextView>(R.id.alarm_persona).text = persona

        findViewById<Button>(R.id.alarm_dismiss).setOnClickListener { dismiss() }
        findViewById<Button>(R.id.alarm_snooze).setOnClickListener { snooze(alarmId, label, persona) }

        startVibration()
        tts = TtsSpeaker(this).also { speaker ->
            if (persona.isNotBlank()) speaker.speak(persona)
        }
        LifeOsLog.d("LifeOS/Alarm", "AlarmActivity id=$alarmId label=$label")
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun dismiss() {
        teardown()
        finish()
    }

    private fun snooze(alarmId: String, label: String, persona: String) {
        val triggerAt = Time.nowEpochMs() + SNOOZE_MS
        val spec = AlarmSpec(
            id = Ids.new("snooze"),
            label = label,
            timeHhmm = Time.formatHhmm(triggerAt),
            triggerAtEpochMs = triggerAt,
            personaLine = persona,
            enabled = true,
        )
        runCatching { AlarmScheduler(this).schedule(spec) }
            .onFailure { LifeOsLog.d("LifeOS/Alarm", "snooze schedule failed: ${it.message}") }
        LifeOsLog.d("LifeOS/Alarm", "snooze from=$alarmId to=${spec.id} at=$triggerAt")
        teardown()
        finish()
    }

    private fun startVibration() {
        runCatching {
            val vm = getSystemService(VibratorManager::class.java)
            val v = vm?.defaultVibrator ?: return
            vibrator = v
            v.vibrate(VibrationEffect.createWaveform(VIBRATE_TIMINGS, 0))
        }.onFailure {
            LifeOsLog.d("LifeOS/Alarm", "vibrate failed: ${it.message}")
        }
    }

    private fun teardown() {
        if (cleaned) return
        cleaned = true
        tts?.stop()
        tts?.shutdown()
        tts = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        getSystemService(NotificationManager::class.java)?.cancel(NotificationChannels.NOTIF_ALARM)
    }

    private companion object {
        const val SNOOZE_MS = 5 * 60 * 1000L
        val VIBRATE_TIMINGS = longArrayOf(0, 500, 250, 500, 250, 500)
    }
}
