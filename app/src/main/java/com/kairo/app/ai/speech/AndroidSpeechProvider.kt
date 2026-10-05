package com.kairo.app.ai.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android's built-in SpeechRecognizer. Create, use and release on the main thread (a platform
 * requirement), from the activity that shows the mic button.
 */
class AndroidSpeechProvider(context: Context) : SpeechProvider {

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null

    private val _results = MutableSharedFlow<Transcript>(extraBufferCapacity = BUFFER)
    private val _errors = MutableSharedFlow<SpeechError>(extraBufferCapacity = BUFFER)
    private val _level = MutableStateFlow(0f)
    private val _listening = MutableStateFlow(false)

    override val results: Flow<Transcript> = _results.asSharedFlow()
    override val errors: Flow<SpeechError> = _errors.asSharedFlow()
    override val level: StateFlow<Float> = _level.asStateFlow()
    override val listening: StateFlow<Boolean> = _listening.asStateFlow()
    override val isAvailable: Boolean get() = recognizer != null

    init {
        recognizer?.setRecognitionListener(Listener())
    }

    override fun start() {
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // en-IN copes with Hinglish in Roman script far better than en-US.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        _listening.value = true
        r.startListening(intent)
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
        _listening.value = false
        _level.value = 0f
    }

    override fun release() {
        recognizer?.destroy()
        _listening.value = false
    }

    private inner class Listener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // Typical RMS runs from about -2 dB (silence) to 10 dB (speaking close to the mic).
            _level.value = ((rmsdB - RMS_FLOOR) / RMS_RANGE).coerceIn(0f, 1f)
        }

        override fun onEndOfSpeech() {
            _level.value = 0f
        }

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { _results.tryEmit(Transcript(it, isFinal = false)) }
        }

        override fun onResults(results: Bundle?) {
            _listening.value = false
            _level.value = 0f
            val text = firstResult(results)
            if (text == null) _errors.tryEmit(SpeechError.NO_MATCH) else _results.tryEmit(Transcript(text, isFinal = true))
        }

        override fun onError(error: Int) {
            _listening.value = false
            _level.value = 0f
            _errors.tryEmit(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.NO_MATCH
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.NO_PERMISSION
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechError.BUSY
                    else -> SpeechError.OTHER
                },
            )
        }

        private fun firstResult(bundle: Bundle?): String? =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val LANGUAGE = "en-IN"
        const val BUFFER = 16
        const val RMS_FLOOR = -2f
        const val RMS_RANGE = 12f
    }
}
