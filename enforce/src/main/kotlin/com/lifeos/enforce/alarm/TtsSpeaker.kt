package com.lifeos.enforce.alarm

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.lifeos.core.LifeOsLog
import java.util.ArrayDeque
import java.util.Locale

class TtsSpeaker(context: Context) {
    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private var ready = false
    private var failed = false
    private var engine: TextToSpeech? = null

    init {
        runCatching {
            engine = TextToSpeech(context.applicationContext) { status ->
                synchronized(lock) {
                    if (status != TextToSpeech.SUCCESS) {
                        failed = true
                        pending.clear()
                        LifeOsLog.d("LifeOS/Alarm", "TTS onInit failed status=$status")
                        return@TextToSpeech
                    }
                    val tts = engine ?: return@TextToSpeech
                    runCatching {
                        val result = tts.setLanguage(Locale.getDefault())
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            tts.setLanguage(Locale.US)
                        }
                        tts.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                    }.onFailure {
                        LifeOsLog.d("LifeOS/Alarm", "TTS configure failed: ${it.message}")
                    }
                    ready = true
                    while (pending.isNotEmpty()) {
                        speakInternal(pending.removeFirst())
                    }
                }
            }
        }.onFailure {
            failed = true
            LifeOsLog.d("LifeOS/Alarm", "TTS unavailable: ${it.message}")
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        synchronized(lock) {
            if (failed) return
            if (ready) speakInternal(text) else pending.addLast(text)
        }
    }

    fun stop() {
        synchronized(lock) {
            pending.clear()
            runCatching { engine?.stop() }
        }
    }

    fun shutdown() {
        synchronized(lock) {
            pending.clear()
            runCatching { engine?.stop() }
            runCatching { engine?.shutdown() }
            engine = null
            ready = false
        }
    }

    private fun speakInternal(text: String) {
        runCatching {
            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }.onFailure {
            LifeOsLog.d("LifeOS/Alarm", "TTS speak failed: ${it.message}")
        }
    }

    private companion object {
        const val UTTERANCE_ID = "lifeos-alarm"
    }
}
