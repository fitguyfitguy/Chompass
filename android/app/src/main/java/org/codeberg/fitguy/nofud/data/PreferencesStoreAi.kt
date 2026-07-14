package org.codeberg.fitguy.nofud.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import org.codeberg.fitguy.nofud.models.AIProvider
import org.codeberg.fitguy.nofud.models.HeuristicServingUnitSettings
import org.codeberg.fitguy.nofud.models.ServingUnitInferenceMode
import org.codeberg.fitguy.nofud.models.SpeechLanguage
import org.codeberg.fitguy.nofud.models.SpeechProvider
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
internal val PreferencesStore.maxResponseTokensImpl: Flow<Int> get() = dataStore.data.map { it[Keys.MAX_RESPONSE_TOKENS] ?: 1024 }
internal suspend fun PreferencesStore.setMaxResponseTokensImpl(v: Int) { dataStore.edit { it[Keys.MAX_RESPONSE_TOKENS] = v.coerceAtLeast(1) } }

    // -- Serving unit inference --------------------------------------------
internal val PreferencesStore.servingUnitInferenceModeImpl: Flow<ServingUnitInferenceMode> get() = dataStore.data.map { prefs ->
        ServingUnitInferenceMode.fromStorage(prefs[Keys.SERVING_UNIT_INFERENCE_MODE])
    }
internal suspend fun PreferencesStore.setServingUnitInferenceModeImpl(mode: ServingUnitInferenceMode) {
        dataStore.edit { it[Keys.SERVING_UNIT_INFERENCE_MODE] = mode.storageKey }
    }

internal val PreferencesStore.heuristicServingUnitSettingsImpl: Flow<HeuristicServingUnitSettings> get() = dataStore.data.map { prefs ->
        prefs[Keys.HEURISTIC_SERVING_UNIT_SETTINGS]?.let {
            runCatching { json.decodeFromString<HeuristicServingUnitSettings>(it) }.getOrNull()
        } ?: HeuristicServingUnitSettings.Default
    }
internal suspend fun PreferencesStore.setHeuristicServingUnitSettingsImpl(settings: HeuristicServingUnitSettings) {
        dataStore.edit {
            it[Keys.HEURISTIC_SERVING_UNIT_SETTINGS] =
                json.encodeToString(HeuristicServingUnitSettings.serializer(), settings)
        }
    }

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
internal val PreferencesStore.fallbackEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.FALLBACK_ENABLED] ?: false }
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

    
