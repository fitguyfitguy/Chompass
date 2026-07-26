package app.chompass.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.chompass.models.AIProvider
import app.chompass.models.HeuristicServingUnitSettings
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.models.SpeechLanguage
import app.chompass.models.SpeechProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val CUSTOM_BASE_URL_PREFIX = "customBaseURL_"

// -- AI Provider selection --------------------------------------------
internal val PreferencesStore.selectedAIProviderImpl: Flow<AIProvider> get() = dataStore.data.map {
        val raw = it[Keys.SELECTED_AI_PROVIDER]
        AIProvider.values().firstOrNull { p -> p.name == raw } ?: AIProvider.GEMINI
    }
internal suspend fun PreferencesStore.setSelectedAIProviderImpl(p: AIProvider) {
        dataStore.edit { it[Keys.SELECTED_AI_PROVIDER] = p.name }
    }

internal val PreferencesStore.selectedAIModelImpl: Flow<String?> get() = dataStore.data.map { it[Keys.SELECTED_AI_MODEL] }
internal suspend fun PreferencesStore.setSelectedAIModelImpl(model: String) {
        dataStore.edit { it[Keys.SELECTED_AI_MODEL] = AIProvider.normalizeModelId(model) }
    }

internal fun PreferencesStore.customBaseUrlImpl(provider: AIProvider): Flow<String?> = dataStore.data.map {
        it[stringPreferencesKey(CUSTOM_BASE_URL_PREFIX + provider.name)]
    }

internal suspend fun PreferencesStore.setCustomBaseUrlImpl(provider: AIProvider, url: String?) {
        val key = stringPreferencesKey(CUSTOM_BASE_URL_PREFIX + provider.name)
        dataStore.edit {
            if (url.isNullOrEmpty()) it.remove(key) else it[key] = url
        }
    }

    /** AI output-token cap sent with every request. Default 1024; raise it for local
     *  models whose replies get truncated. */
internal val PreferencesStore.maxResponseTokensImpl: Flow<Int> get() = dataStore.data.map {
        clampMaxResponseTokens(it[Keys.MAX_RESPONSE_TOKENS] ?: 1024)
    }
internal suspend fun PreferencesStore.setMaxResponseTokensImpl(v: Int) {
        dataStore.edit { it[Keys.MAX_RESPONSE_TOKENS] = clampMaxResponseTokens(v) }
    }

/** Read timeout for cloud AI HTTP calls (vision / chat). Default 60s; raise for slow local GPUs. */
internal val PreferencesStore.aiReadTimeoutSecondsImpl: Flow<Int> get() = dataStore.data.map {
        clampAiReadTimeoutSeconds(it[Keys.AI_READ_TIMEOUT_SECONDS] ?: DEFAULT_AI_READ_TIMEOUT_SECONDS)
    }
internal suspend fun PreferencesStore.setAiReadTimeoutSecondsImpl(v: Int) {
        dataStore.edit { it[Keys.AI_READ_TIMEOUT_SECONDS] = clampAiReadTimeoutSeconds(v) }
    }

internal const val MIN_MAX_RESPONSE_TOKENS = 256
internal const val MAX_MAX_RESPONSE_TOKENS = 8192
internal const val DEFAULT_AI_READ_TIMEOUT_SECONDS = 180
internal const val MIN_AI_READ_TIMEOUT_SECONDS = 30
internal const val MAX_AI_READ_TIMEOUT_SECONDS = 600

internal fun clampMaxResponseTokens(v: Int): Int =
    v.coerceIn(MIN_MAX_RESPONSE_TOKENS, MAX_MAX_RESPONSE_TOKENS)

internal fun clampAiReadTimeoutSeconds(v: Int): Int =
    v.coerceIn(MIN_AI_READ_TIMEOUT_SECONDS, MAX_AI_READ_TIMEOUT_SECONDS)

    // -- Serving unit inference --------------------------------------------
internal val PreferencesStore.servingUnitInferenceModeImpl: Flow<ServingUnitInferenceMode> get() = dataStore.data.map { prefs ->
        ServingUnitInferenceMode.fromStorage(prefs[Keys.SERVING_UNIT_INFERENCE_MODE])
    }
internal suspend fun PreferencesStore.setServingUnitInferenceModeImpl(mode: ServingUnitInferenceMode) {
        dataStore.edit { it[Keys.SERVING_UNIT_INFERENCE_MODE] = mode.storageKey }
    }

internal val PreferencesStore.heuristicServingUnitSettingsImpl: Flow<HeuristicServingUnitSettings>
    get() = objectPref(Keys.HEURISTIC_SERVING_UNIT_SETTINGS, HeuristicServingUnitSettings.serializer())
        .map { it ?: HeuristicServingUnitSettings.Default }

internal suspend fun PreferencesStore.setHeuristicServingUnitSettingsImpl(settings: HeuristicServingUnitSettings) =
    setObjectPref(Keys.HEURISTIC_SERVING_UNIT_SETTINGS, HeuristicServingUnitSettings.serializer(), settings)

    // -- Custom AI Instructions ------------------------------------------
    /** Free-form text appended to every AI request. Empty = disabled. */
internal val PreferencesStore.userContextImpl: Flow<String> get() = dataStore.data.map { it[Keys.USER_CONTEXT].orEmpty() }
internal suspend fun PreferencesStore.setUserContextImpl(value: String) {
        val trimmed = value.trim()
        dataStore.edit {
            if (trimmed.isEmpty()) it.remove(Keys.USER_CONTEXT) else it[Keys.USER_CONTEXT] = trimmed
        }
    }

    // -- Fallback AI Provider --------------------------------------------
internal val PreferencesStore.fallbackEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.FALLBACK_ENABLED] ?: true }
internal suspend fun PreferencesStore.setFallbackEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.FALLBACK_ENABLED] = v } }

internal val PreferencesStore.selectedFallbackProviderImpl: Flow<AIProvider> get() = dataStore.data.map {
        val raw = it[Keys.FALLBACK_PROVIDER]
        AIProvider.values().firstOrNull { p -> p.name == raw } ?: AIProvider.GEMINI
    }
internal suspend fun PreferencesStore.setSelectedFallbackProviderImpl(p: AIProvider) {
        dataStore.edit { it[Keys.FALLBACK_PROVIDER] = p.name }
    }

internal val PreferencesStore.selectedFallbackModelImpl: Flow<String?> get() = dataStore.data.map { it[Keys.FALLBACK_MODEL] }
internal suspend fun PreferencesStore.setSelectedFallbackModelImpl(model: String) {
        dataStore.edit { it[Keys.FALLBACK_MODEL] = AIProvider.normalizeModelId(model) }
    }

    /** When true, Gemini generateContent requests include the google_search grounding tool. */
internal val PreferencesStore.geminiGoogleSearchEnabledImpl: Flow<Boolean> get() = dataStore.data.map {
        it[Keys.GEMINI_GOOGLE_SEARCH_ENABLED] ?: false
    }
internal suspend fun PreferencesStore.setGeminiGoogleSearchEnabledImpl(v: Boolean) {
        dataStore.edit { it[Keys.GEMINI_GOOGLE_SEARCH_ENABLED] = v }
    }

    /** Beta: after a photo entry, offer a portion-size chip (small/regular/large/restaurant)
     *  and re-analyze with the answer when the estimate looks portion-uncertain. Default off;
     *  see docs/UNCERTAINTY_DRIVEN_ENTRY.md bet 1. */
internal val PreferencesStore.portionClarifyEnabledImpl: Flow<Boolean> get() = dataStore.data.map {
        it[Keys.PORTION_CLARIFY_ENABLED] ?: false
    }
internal suspend fun PreferencesStore.setPortionClarifyEnabledImpl(v: Boolean) {
        dataStore.edit { it[Keys.PORTION_CLARIFY_ENABLED] = v }
    }

    // -- Speech Provider selection ---------------------------------------
internal val PreferencesStore.selectedSpeechProviderImpl: Flow<SpeechProvider> get() = dataStore.data.map {
        val raw = it[Keys.SELECTED_SPEECH_PROVIDER]
        SpeechProvider.values().firstOrNull { p -> p.name == raw } ?: SpeechProvider.NATIVE
    }
internal suspend fun PreferencesStore.setSelectedSpeechProviderImpl(p: SpeechProvider) {
        dataStore.edit { it[Keys.SELECTED_SPEECH_PROVIDER] = p.name }
    }

internal fun PreferencesStore.selectedSpeechLanguageImpl(provider: SpeechProvider): Flow<SpeechLanguage> = dataStore.data.map {
        val raw = it[Keys.selectedSpeechLanguage(provider)]
        SpeechLanguage.values().firstOrNull { language -> language.name == raw }
            ?: SpeechLanguage.defaultFor(provider)
    }

internal suspend fun PreferencesStore.setSelectedSpeechLanguageImpl(provider: SpeechProvider, language: SpeechLanguage) {
        dataStore.edit { it[Keys.selectedSpeechLanguage(provider)] = language.name }
    }

    // -- On-device LLM ------------------------------------------------------
    /** Catalog version string of the currently-downloaded model file, or null if none is downloaded. */
internal val PreferencesStore.onDeviceModelDownloadedVersionImpl: Flow<String?> get() = dataStore.data.map { it[Keys.ON_DEVICE_MODEL_DOWNLOADED_VERSION] }
internal suspend fun PreferencesStore.setOnDeviceModelDownloadedVersionImpl(version: String?) {
        dataStore.edit {
            if (version.isNullOrEmpty()) it.remove(Keys.ON_DEVICE_MODEL_DOWNLOADED_VERSION) else it[Keys.ON_DEVICE_MODEL_DOWNLOADED_VERSION] = version
        }
    }

internal val PreferencesStore.onDeviceDownloadOverWifiOnlyImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.ON_DEVICE_DOWNLOAD_OVER_WIFI_ONLY] ?: true }
internal suspend fun PreferencesStore.setOnDeviceDownloadOverWifiOnlyImpl(v: Boolean) { dataStore.edit { it[Keys.ON_DEVICE_DOWNLOAD_OVER_WIFI_ONLY] = v } }

    /** Rollout gate: whether ON_DEVICE appears as a selectable provider. Default on since 1.14.0. */
internal val PreferencesStore.onDeviceFeatureVisibleImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.ON_DEVICE_FEATURE_VISIBLE] ?: true }
internal suspend fun PreferencesStore.setOnDeviceFeatureVisibleImpl(v: Boolean) { dataStore.edit { it[Keys.ON_DEVICE_FEATURE_VISIBLE] = v } }
