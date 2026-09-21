package com.example.seefix.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speech Recognition and Text-To-Speech manager for hands-free repair instructions.
 */
class SpeechManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "step_tts_id")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
