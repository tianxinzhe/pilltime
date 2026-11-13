package com.medtimer.app.ui.reminder

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 提醒页语音播报：初始化失败、语言不支持或引擎异常时全部静默，不影响界面。
 * 进入页面时播报一次，页面销毁时必须调用 [release]。
 */
class ReminderSpeaker(context: Context) {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var pendingLocale: Locale = Locale.getDefault()

    init {
        try {
            engine = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    val text = pendingText
                    pendingText = null
                    if (text != null) speakNow(text, pendingLocale)
                }
            }
        } catch (_: Exception) {
            engine = null
        }
    }

    /** 播报文本；引擎尚未就绪时暂存，初始化成功后补播。 */
    fun speak(text: String, locale: Locale = Locale.getDefault()) {
        if (text.isBlank()) return
        pendingLocale = locale
        if (!ready) {
            pendingText = text
            return
        }
        speakNow(text, locale)
    }

    private fun speakNow(text: String, locale: Locale) {
        val tts = engine ?: return
        val languageResult = try {
            tts.setLanguage(locale)
        } catch (_: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        // 语言缺失或不支持时静默，不打断界面
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return
        }
        try {
            // 语速略慢，老人听得清
            tts.setSpeechRate(SPEECH_RATE)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        } catch (_: Exception) {
            // 引擎异常不影响界面
        }
    }

    /** 停止并释放引擎；重复调用安全。 */
    fun release() {
        val tts = engine ?: return
        engine = null
        ready = false
        pendingText = null
        try {
            tts.stop()
        } catch (_: Exception) {
            // 释放阶段忽略异常
        }
        try {
            tts.shutdown()
        } catch (_: Exception) {
            // 释放阶段忽略异常
        }
    }

    private companion object {
        const val SPEECH_RATE = 0.9f
        const val UTTERANCE_ID = "medtimer_reminder"
    }
}