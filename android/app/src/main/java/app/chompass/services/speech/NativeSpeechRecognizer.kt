package app.chompass.services.speech

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import app.chompass.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

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
        private const val TAG = "ChompassSpeech"
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        /** Assist / voice-interaction services are not bindable by normal apps. */
        private const val PERM_VOICE_INTERACTION = "android.permission.BIND_VOICE_INTERACTION"

        /**
         * Assist / incomplete "RecognitionService" stubs that advertise the intent but
         * fail for third-party [SpeechRecognizer] clients (ERROR_CLIENT).
         */
        private val DENYLISTED_PACKAGES = setOf(
            "com.anthropic.claude",
            "io.homeassistant.companion.android.minimal",
            "io.homeassistant.companion.android",
        )

        /**
         * Well-known RecognitionService components. Used when the system default
         * is unset or Assist apps pollute the query results. Packages installed only
         * in another profile (e.g. Private Space) are skipped.
         */
        private val KNOWN_SERVICES = listOf(
            ComponentName(
                "dev.soupslurpr.transcribro",
                "dev.soupslurpr.transcribro.recognitionservice.MainRecognitionService",
            ),
            ComponentName(
                "com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService",
            ),
            ComponentName(
                "com.google.android.tts",
                "com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService",
            ),
        )

        fun isRecoverableSessionError(code: Int): Boolean =
            code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    code == SpeechRecognizer.ERROR_NO_MATCH ||
                    code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    code == ERROR_SERVER_DISCONNECTED

        fun isLanguageSupportError(code: Int): Boolean =
            code == ERROR_LANGUAGE_NOT_SUPPORTED || code == ERROR_LANGUAGE_UNAVAILABLE

        /**
         * Offline prefer / missing on-device model often surfaces as [SpeechRecognizer.ERROR_CLIENT]
         * rather than a language-unavailable code. Callers should fall offline → online on these.
         */
        fun shouldFallbackRecognitionMode(code: Int, preferOffline: Boolean): Boolean =
            isLanguageSupportError(code) ||
                    (preferOffline && code == SpeechRecognizer.ERROR_CLIENT)

        private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, 0)
                }
                true
            }.getOrDefault(false)

        private fun queryRecognitionServices(pm: PackageManager): List<ResolveInfo> {
            val intent = Intent(RecognitionService.SERVICE_INTERFACE)
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentServices(intent, 0)
                }
            }.getOrDefault(emptyList())
        }

        private fun scoreService(info: ResolveInfo): Int {
            val service = info.serviceInfo ?: return Int.MIN_VALUE
            val pkg = service.packageName
            val perm = service.permission
            if (pkg in DENYLISTED_PACKAGES) return Int.MIN_VALUE
            // Voice-interaction Assist services cannot be bound by third-party apps.
            if (perm == PERM_VOICE_INTERACTION) return Int.MIN_VALUE

            var s = 10
            when {
                // FOSS on-device STT (GrapheneOS-friendly). Prefer over Assist stubs.
                pkg == "dev.soupslurpr.transcribro" -> s += 120
                pkg == "com.google.android.googlequicksearchbox" -> s += 100
                pkg == "com.google.android.tts" -> s += 80
                pkg.startsWith("com.google.") -> s += 60
                // Typical real STT engines: no binder permission, or RECORD_AUDIO.
                perm.isNullOrBlank() || perm == Manifest.permission.RECORD_AUDIO -> s += 40
                else -> s += 5
            }
            return s
        }

        private fun parseSecureRecognitionService(context: Context): ComponentName? {
            val raw = runCatching {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    "voice_recognition_service",
                )
            }.getOrNull()?.trim().orEmpty()
            if (raw.isEmpty()) return null
            return runCatching { ComponentName.unflattenFromString(raw) }.getOrNull()
                ?.takeIf { isPackageInstalled(context.packageManager, it.packageName) }
                ?.takeIf { it.packageName !in DENYLISTED_PACKAGES }
        }

        /**
         * Pick a recognition service that third-party apps can bind to.
         *
         * [SpeechRecognizer.createSpeechRecognizer] without a component uses the
         * *default* RecognitionService. On GrapheneOS that setting is often empty
         * or points at Assist stubs (Home Assistant / Claude) that fail with
         * ERROR_CLIENT. Prefer Settings → Voice input, then real STT engines
         * (Transcribro, Google if present), never Assist-only packages.
         */
        internal fun resolvePreferredRecognitionService(context: Context): ComponentName? {
            val pm = context.packageManager

            parseSecureRecognitionService(context)?.let { preferred ->
                Log.i(TAG, "Using Settings voice_recognition_service $preferred")
                return preferred
            }

            val queried = queryRecognitionServices(pm)
            Log.i(TAG, "queryIntentServices(RecognitionService) count=${queried.size}")
            queried.forEach { info ->
                val s = info.serviceInfo
                Log.i(
                    TAG,
                    "  candidate ${s?.packageName}/${s?.name} perm=${s?.permission} score=${scoreService(info)}"
                )
            }

            val fromQuery = queried
                .maxByOrNull(::scoreService)
                ?.takeIf { scoreService(it) > 0 }
                ?.serviceInfo
                ?.let { ComponentName(it.packageName, it.name) }
            if (fromQuery != null) {
                Log.i(TAG, "Using queried recognition service $fromQuery")
                return fromQuery
            }

            for (component in KNOWN_SERVICES) {
                if (!isPackageInstalled(pm, component.packageName)) {
                    Log.i(TAG, "Known service not installed in this profile: $component")
                    continue
                }
                if (component.packageName in DENYLISTED_PACKAGES) continue
                Log.i(TAG, "Using known recognition service $component")
                return component
            }
            return null
        }
    }

    fun isAvailable(): Boolean = hasUsableRecognitionService()

    /** True when this profile has a bindable RecognitionService (not Assist-only stubs). */
    fun hasUsableRecognitionService(): Boolean =
        resolvePreferredRecognitionService(context) != null

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    fun listen(locale: String? = null, preferOffline: Boolean = true): Flow<SttEvent> = callbackFlow {
        // SpeechRecognizer create/start/destroy must run on the main thread.
        val mainHandler = Handler(Looper.getMainLooper())
        val component = resolvePreferredRecognitionService(context)
        if (component == null) {
            trySend(
                SttEvent.Error(
                    SpeechRecognizer.ERROR_CLIENT,
                    // Message is replaced by localized UI string when possible.
                    "NO_USABLE_RECOGNITION_SERVICE"
                )
            )
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context, component)

        var closed = false
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(SttEvent.Ready) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { trySend(SttEvent.EndOfSpeech) }
            override fun onError(error: Int) {
                Log.w(TAG, "Recognition error $error (${describeError(error)}) component=$component")
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

        // Bind is async; starting in the same stack frame often races to
        // "not connected to the recognition service" (ERROR_CLIENT) on API 34+.
        val start = Runnable {
            if (closed) return@Runnable
            runCatching { recognizer.startListening(intent) }
                .onFailure { e ->
                    Log.e(TAG, "startListening failed", e)
                    trySend(
                        SttEvent.Error(
                            SpeechRecognizer.ERROR_CLIENT,
                            e.localizedMessage ?: context.getString(R.string.speech_error_start_failed)
                        )
                    )
                    close()
                }
        }
        mainHandler.post(start)

        awaitClose {
            closed = true
            val teardown = Runnable {
                runCatching { recognizer.stopListening() }
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                teardown.run()
            } else {
                mainHandler.post(teardown)
            }
        }
    }.flowOn(Dispatchers.Main.immediate)

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.speech_error_audio_capture_failed)
        SpeechRecognizer.ERROR_CLIENT -> context.getString(R.string.speech_error_start_failed)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.speech_error_missing_permission)
        SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.speech_error_network)
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.speech_error_network_timeout)
        SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.speech_error_no_match)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> context.getString(R.string.speech_error_recognizer_busy)
        SpeechRecognizer.ERROR_SERVER -> context.getString(R.string.speech_error_server)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.speech_error_no_input)
        ERROR_SERVER_DISCONNECTED -> context.getString(R.string.speech_error_server_disconnected)
        ERROR_LANGUAGE_NOT_SUPPORTED -> context.getString(R.string.speech_error_language_not_supported)
        ERROR_LANGUAGE_UNAVAILABLE -> context.getString(R.string.speech_error_language_unavailable)
        else -> context.getString(R.string.speech_error_unknown_format, code)
    }
}
