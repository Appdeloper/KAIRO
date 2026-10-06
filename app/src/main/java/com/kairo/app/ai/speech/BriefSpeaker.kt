package com.kairo.app.ai.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Text-to-speech for the briefing. The activity calls speak() only while resumed and stop() on
 * pause, so KAIRO never talks from the background.
 */
class BriefSpeaker(context: Context) {
    private var ready = false
    private var pending: Pair<String, () -> Unit>? = null
    private var onDone: (() -> Unit)? = null

    private val _speaking = MutableStateFlow(false)

    /** True while TTS is actually talking, so the orb can show its "speaking" state. */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /** TTS callbacks arrive on a binder thread; UI state must be touched on main. */
    private val main = Handler(Looper.getMainLooper())

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) configure()
        pending?.let { (text, done) -> speak(text, done) }
        pending = null
    }

    private fun configure() {
        val indianEnglish = Locale.forLanguageTag("en-IN")
        val result = tts.setLanguage(indianEnglish)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault())
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post { _speaking.value = true }
            }

            override fun onDone(utteranceId: String?) {
                main.post {
                    _speaking.value = false
                    if (utteranceId == UTTERANCE_ID) onDone?.invoke()
                }
            }

            @Deprecated("Required override on older APIs")
            override fun onError(utteranceId: String?) {
                main.post { _speaking.value = false }
            }
        })
    }

    /** [done] runs only if the whole text was spoken, so an interrupted brief can be replayed. */
    fun speak(text: String, done: () -> Unit) {
        if (!ready) {
            pending = text to done
            return
        }
        onDone = done
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        pending = null
        onDone = null
        _speaking.value = false
        if (ready) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "kairo-brief"
    }
}
