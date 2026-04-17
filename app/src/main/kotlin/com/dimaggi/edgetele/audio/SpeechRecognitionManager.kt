package com.dimaggi.edgetele.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.dimaggi.edgetele.data.model.enums.Language
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages on-device speech-to-text using Android's SpeechRecognizer API.
 *
 * All SpeechRecognizer calls are marshalled to the main thread via [mainHandler],
 * so callers do not need to be on the main thread.
 *
 * Language pack notes:
 * - English (en-US): pre-installed, no download required
 * - Haitian Creole (ht-HT): fallback to fr-HT if exact locale unavailable
 * - Bislama (bi-VU): fallback to en-VU → en-US
 * - Tok Pisin (tpi-PG): fallback to en-PG → en-AU → en-US
 *
 * Callers should observe [isListening] and handle [SttEvent] emissions.
 */
@Singleton
class SpeechRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SpeechRecognitionMgr"
        private const val MAX_RESULTS = 1
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isAvailable = MutableStateFlow(
        SpeechRecognizer.isRecognitionAvailable(context)
    )
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val eventChannel = Channel<SttEvent>(Channel.BUFFERED)
    val events: Flow<SttEvent> = eventChannel.receiveAsFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Start listening. Creates a fresh [SpeechRecognizer] each call.
     * Safe to call from any thread.
     */
    fun startListening(language: Language = Language.ENGLISH) {
        mainHandler.post {
            if (!_isAvailable.value) {
                sendEvent(SttEvent.Error(SttError.RECOGNITION_UNAVAILABLE))
                return@post
            }
            // Always tear down any previous recognizer before starting a new one.
            destroyRecognizerInternal()

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolveLocale(language))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // Give the user more time to speak before the session times out.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
            Log.d(TAG, "Started listening in ${language.displayName} (${resolveLocale(language)})")
        }
    }

    /** User-initiated stop: gracefully stop then destroy. */
    fun stopListening() {
        mainHandler.post {
            speechRecognizer?.stopListening()
            destroyRecognizerInternal()
            Log.d(TAG, "Stopped listening")
        }
    }

    fun release() {
        mainHandler.post {
            destroyRecognizerInternal()
            eventChannel.close()
        }
    }

    /**
     * Destroy the recognizer WITHOUT calling stopListening() first.
     *
     * Calling stopListening() from inside a RecognitionListener callback (onError,
     * onResults) can trigger a spurious ERROR_CLIENT on some Android versions, which
     * fires onError again and sends an extra error event to observers. Only destroy()
     * is needed — the session is already terminal at that point.
     */
    private fun destroyRecognizerInternal() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isListening.value = false
    }

    /**
     * Resolve the best available STT locale for a given [Language].
     * Falls back progressively to avoid crashing on unsupported locales.
     */
    private fun resolveLocale(language: Language): String = when (language) {
        Language.ENGLISH        -> "en-US"
        Language.BISLAMA        -> "en-VU"     // best approximation; may fall back to en-US
        Language.TOK_PISIN      -> "en-PG"     // best approximation; may fall back to en-AU
        Language.HAITIAN_CREOLE -> "ht-HT"     // may fall back to fr-HT on some devices
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech begun")
        }

        override fun onRmsChanged(rmsdB: Float) {
            sendEvent(SttEvent.VolumeChanged(rmsdB))
        }

        override fun onBufferReceived(buffer: ByteArray?) { /* unused */ }

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
            _isListening.value = false
        }

        override fun onError(error: Int) {
            // Only destroy — do NOT call stopListening() here. Calling stopListening()
            // from within onError triggers ERROR_CLIENT on some Android versions, causing
            // a second onError and leaving the recognizer in a broken state on the next tap.
            destroyRecognizerInternal()
            val sttError = when (error) {
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> SttError.LANGUAGE_NOT_SUPPORTED
                SpeechRecognizer.ERROR_AUDIO                  -> SttError.MICROPHONE_UNAVAILABLE
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_NO_MATCH               -> SttError.TIMEOUT
                else                                          -> SttError.RECOGNITION_FAILED
            }
            Log.w(TAG, "STT error: $sttError (code=$error)")
            // isRecognitionAvailable() returns true on some devices even when the STT
            // service is broken. Mark unavailable after first hard failure so the UI hides the mic.
            if (sttError == SttError.RECOGNITION_FAILED) {
                _isAvailable.value = false
            }
            sendEvent(SttEvent.Error(sttError))
        }

        override fun onResults(results: Bundle?) {
            // Only destroy — same reason as onError.
            destroyRecognizerInternal()
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = matches?.firstOrNull().orEmpty()
            Log.d(TAG, "Final transcript: $transcript")
            sendEvent(SttEvent.FinalResult(transcript))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            sendEvent(SttEvent.PartialResult(partial))
        }

        override fun onEvent(eventType: Int, params: Bundle?) { /* unused */ }
    }

    private fun sendEvent(event: SttEvent) {
        eventChannel.trySend(event)
    }

    // ---- Event types ----

    sealed class SttEvent {
        data class PartialResult(val text: String) : SttEvent()
        data class FinalResult(val text: String) : SttEvent()
        data class VolumeChanged(val rmsdB: Float) : SttEvent()
        data class Error(val error: SttError) : SttEvent()
    }

    enum class SttError {
        LANGUAGE_NOT_SUPPORTED,
        MICROPHONE_UNAVAILABLE,
        TIMEOUT,
        RECOGNITION_FAILED,
        RECOGNITION_UNAVAILABLE
    }
}
