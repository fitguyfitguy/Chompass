package com.fitguy.nofud.services.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed class SttEvent {
    data class Partial(val text: String) : SttEvent()
    data class Final(val text: String) : SttEvent()
    data class Error(val code: Int, val message: String) : SttEvent()
    object Ready : SttEvent()
    object EndOfSpeech : SttEvent()
}

/**
 * Wraps Android's [SpeechRecognizer] as a cold Flow. Emits live partials while
 * the user speaks, then a Final event on completion. Port of iOS native-iOS STT
 * one-tap flow.
 */
class NativeSpeechRecognizer(private val context: Context) {

    companion object {
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        fun isRecoverableSessionError(code: Int): Boolean =
            code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    code == SpeechRecognizer.ERROR_NO_MATCH ||
                    code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    code == ERROR_SERVER_DISCONNECTED

        fun isLanguageSupportError(code: Int): Boolean =
            code == ERROR_LANGUAGE_NOT_SUPPORTED || code == ERROR_LANGUAGE_UNAVAILABLE
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    fun listen(locale: String? = null, preferOffline: Boolean = true): Flow<SttEvent> = callbackFlow {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(SttEvent.Ready) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { trySend(SttEvent.EndOfSpeech) }
            override fun onError(error: Int) {
                trySend(SttEvent.Error(error, describeError(error)))
                close()
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                trySend(SttEvent.Final(list?.firstOrNull().orEmpty()))
                close()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                trySend(SttEvent.Partial(list?.firstOrNull().orEmpty()))
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        recognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (!locale.isNullOrBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)

        awaitClose {
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing microphone permission"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        ERROR_SERVER_DISCONNECTED -> "Speech service disconnected"
        ERROR_LANGUAGE_NOT_SUPPORTED -> "Speech language is not supported on this device"
        ERROR_LANGUAGE_UNAVAILABLE -> "Speech language is unavailable on this device"
        else -> "Speech error ($code)"
    }
}
