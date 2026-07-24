package app.chompass.models

import androidx.annotation.StringRes
import app.chompass.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SpeechProvider {
    @SerialName("Native (On-Device)") NATIVE,
    @SerialName("Gemini Audio") GEMINI,
    @SerialName("OpenAI Whisper") OPENAI,
    @SerialName("Groq (Whisper)") GROQ,
    @SerialName("Deepgram") DEEPGRAM,
    @SerialName("AssemblyAI") ASSEMBLY_AI;

    @get:StringRes
    val displayNameRes: Int get() = when (this) {
        NATIVE -> R.string.speech_provider_native
        GEMINI -> R.string.speech_provider_gemini
        OPENAI -> R.string.speech_provider_openai
        GROQ -> R.string.speech_provider_groq
        DEEPGRAM -> R.string.speech_provider_deepgram
        ASSEMBLY_AI -> R.string.speech_provider_assemblyai
    }

    val requiresApiKey: Boolean get() = this != NATIVE

    @get:StringRes
    val apiKeyPlaceholderRes: Int get() = when (this) {
        NATIVE -> R.string.speech_key_placeholder_native
        GEMINI -> R.string.speech_key_placeholder_gemini
        OPENAI -> R.string.speech_key_placeholder_openai
        GROQ -> R.string.speech_key_placeholder_groq
        DEEPGRAM -> R.string.speech_key_placeholder_deepgram
        ASSEMBLY_AI -> R.string.speech_key_placeholder_assemblyai
    }

    val defaultModel: String get() = when (this) {
        NATIVE -> ""
        GEMINI -> "gemini-3.6-flash"           // matches AI food-analysis default
        OPENAI -> "gpt-4o-mini-transcribe"     // same $/min as whisper-1, better accuracy
        GROQ -> "whisper-large-v3"
        DEEPGRAM -> "nova-3"
        ASSEMBLY_AI -> "universal"
    }

    @get:StringRes
    val descriptionRes: Int get() = when (this) {
        NATIVE -> R.string.speech_description_native
        GEMINI -> R.string.speech_description_gemini
        OPENAI -> R.string.speech_description_openai
        GROQ -> R.string.speech_description_groq
        DEEPGRAM -> R.string.speech_description_deepgram
        ASSEMBLY_AI -> R.string.speech_description_assemblyai
    }
}
