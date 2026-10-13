package app.chompass.data

import androidx.datastore.preferences.core.edit

import androidx.annotation.StringRes
import app.chompass.R
import app.chompass.models.AiProfile
import app.chompass.models.AIProvider
import app.chompass.models.HeuristicServingUnitSettings
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.models.SpeechLanguage
import app.chompass.models.SpeechProvider
import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// -- Master AI-features switch (Codeberg #20 phase 2) -------------------
/** When false, no data is sent to any LLM provider: AI entry points are hidden
 *  and [app.chompass.services.ai.FoodAnalysisService] / ChatService throw
 *  AiError.Disabled. Default ON. */
internal val PreferencesStore.aiFeaturesEnabledImpl: Flow<Boolean> get() = dataStore.data.map {
        it[Keys.AI_FEATURES_ENABLED] ?: true
    }
internal suspend fun PreferencesStore.setAiFeaturesEnabledImpl(v: Boolean) {
        dataStore.edit { it[Keys.AI_FEATURES_ENABLED] = v }
    }

// -- AI Provider selection --------------------------------------------

/**
 * Release cleartext opt-in (issue #8 follow-up, design doc D2 Option B):
 * [app.chompass.services.ai.AiHttp.assertCleartextAllowed] rejects http:// URLs for
 * non-loopback hosts unless this is on. Default OFF — https stays the default path.
 */
internal val PreferencesStore.allowInsecureHttpImpl: Flow<Boolean> get() = dataStore.data.map {
        it[Keys.ALLOW_INSECURE_HTTP] ?: false
    }
internal suspend fun PreferencesStore.setAllowInsecureHttpImpl(v: Boolean) {
        dataStore.edit { it[Keys.ALLOW_INSECURE_HTTP] = v }
    }

// -- AI profiles ---------------------------------------------------------

private val aiProfileListSerializer = ListSerializer(AiProfile.serializer())

/**
 * Decodes the persisted profile list. Lenient like [PreferencesStore.json]:
 * unknown fields from a newer build are ignored, and an unreadable blob
 * degrades to an empty list (settings UI offers Add profile; no crash loop).
 */
internal fun decodeAiProfilesJson(json: Json, raw: String?): List<AiProfile> {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return emptyList()
    return try {
        json.decodeFromString(aiProfileListSerializer, text)
    } catch (_: IllegalArgumentException) {
        // SerializationException (unknown enum/shape) extends IllegalArgumentException.
        emptyList()
    }
}

internal fun PreferencesStore.decodeAiProfiles(raw: String?): List<AiProfile> =
    decodeAiProfilesJson(json, raw)

internal val PreferencesStore.aiProfilesImpl: Flow<List<AiProfile>> get() = dataStore.data.map {
        decodeAiProfiles(it[Keys.AI_PROFILES_JSON])
    }

/** Read-modify-write of the profile list inside ONE dataStore.edit. */
internal suspend fun PreferencesStore.mutateProfilesImpl(block: (MutableList<AiProfile>) -> Unit) {
    dataStore.edit { prefs ->
        val list = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]).toMutableList()
        block(list)
        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(aiProfileListSerializer, list)
    }
}

/**
 * Appends [profile] and makes it active inside ONE `dataStore.edit`, so a UI
 * that selects a provider right after "Add profile" can never target the
 * previously active row (the two-edit add-then-activate left a window where
 * `editActiveProfile` rewrote the old profile). Leaves the fallback id and
 * every other profile untouched.
 */
internal suspend fun PreferencesStore.addAndActivateProfileImpl(profile: AiProfile) {
    dataStore.edit { prefs ->
        val list = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]).toMutableList()
        list += profile
        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(aiProfileListSerializer, list)
        prefs[Keys.ACTIVE_AI_PROFILE_ID] = profile.id.toString()
    }
}

internal val PreferencesStore.activeAiProfileIdImpl: Flow<String?> get() = dataStore.data.map { it[Keys.ACTIVE_AI_PROFILE_ID] }
internal suspend fun PreferencesStore.setActiveAiProfileIdImpl(id: String) {
    dataStore.edit { it[Keys.ACTIVE_AI_PROFILE_ID] = id }
}

/**
 * The profile driving every AI feature: the [Keys.ACTIVE_AI_PROFILE_ID] match,
 * else the first list entry (so a stale/missing id still resolves to
 * something configurable), else null (pre-migration / empty store).
 */
internal val PreferencesStore.activeAiProfileImpl: Flow<AiProfile?> get() = dataStore.data.map { prefs ->
        val profiles = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON])
        profiles.firstOrNull { it.id.toString() == prefs[Keys.ACTIVE_AI_PROFILE_ID] }
            ?: profiles.firstOrNull()
    }

/** Fallback slot; null/blank id or unknown id = no fallback profile. */
internal val PreferencesStore.fallbackAiProfileImpl: Flow<AiProfile?> get() = dataStore.data.map { prefs ->
        val id = prefs[Keys.FALLBACK_AI_PROFILE_ID]?.takeIf { it.isNotBlank() } ?: return@map null
        decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]).firstOrNull { it.id.toString() == id }
    }

internal suspend fun PreferencesStore.setFallbackAiProfileIdImpl(id: String?) {
    dataStore.edit {
        if (id.isNullOrBlank()) it.remove(Keys.FALLBACK_AI_PROFILE_ID) else it[Keys.FALLBACK_AI_PROFILE_ID] = id
    }
}

/**
 * Single-edit transform of the active profile (same resolution as
 * [activeAiProfileImpl]: explicit id, else first entry). No-op when the store
 * has no profiles and [createIfMissing] is false; otherwise a bare default
 * profile is created first.
 */
internal suspend fun PreferencesStore.editActiveProfileImpl(
    createIfMissing: Boolean = true,
    transform: (AiProfile) -> AiProfile,
) {
    dataStore.edit { prefs ->
        val list = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]).toMutableList()
        if (list.isEmpty()) {
            if (!createIfMissing) return@edit
            list += AiProfile(
                id = UUID.randomUUID(),
                name = AIProvider.GEMINI.name,
                provider = AIProvider.GEMINI,
                createdAt = Instant.now(),
            )
        }
        val activeId = prefs[Keys.ACTIVE_AI_PROFILE_ID]
        val idx = list.indexOfFirst { it.id.toString() == activeId }.takeIf { it >= 0 } ?: 0
        list[idx] = transform(list[idx])
        prefs[Keys.ACTIVE_AI_PROFILE_ID] = list[idx].id.toString()
        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(aiProfileListSerializer, list)
    }
}

/**
 * Retargets the active profile. Same provider is a no-op (key, base URL,
 * vision model, and model stay). A real switch clears base URL and vision
 * model — they belong to the old host — and deletes that profile's key.
 * Returns false when there is no profile or the provider did not change.
 */
internal suspend fun PreferencesStore.switchActiveProfileProviderImpl(
    keyStore: KeyStore,
    provider: AIProvider,
    model: String,
): Boolean {
    val active = activeAiProfile.first() ?: return false
    if (active.provider == provider) return false
    editActiveProfileImpl {
        it.copy(provider = provider, model = model, baseUrl = null, visionModel = null)
    }
    keyStore.setProfileApiKey(active.id, null)
    return true
}

/**
 * Legacy single-slot setters, kept only for not-yet-migrated call sites
 * (settings/onboarding UI, debug goal-matrix harness, JVM tests). They now
 * address the two profile slots — ACTIVE (primary) and FALLBACK — because the
 * per-provider DataStore keys are gone after [ensureAiProfilesMigratedImpl].
 *
 * Targeting rules:
 * - Provider-switch ([setSelectedAIProviderCompatImpl]) copies the active
 *   profile in place (provider + default model; url/vision survive like the
 *   old per-provider slots) — the historical clobber was model-only.
 * - Per-provider writes ([setCustomBaseUrlCompatImpl]/[setVisionModelCompatImpl])
 *   hit the active profile when it matches, else the first non-fallback
 *   profile with that provider, else create (unactivated) — so "configure
 *   provider P's endpoint, then switch to P" keeps working.
 * - Fallback setters replace/update the single fallback slot profile.
 */
internal suspend fun PreferencesStore.setSelectedAIProviderCompatImpl(p: AIProvider) {
    editActiveProfileImpl { it.copy(provider = p, model = p.defaultModel) }
}

internal suspend fun PreferencesStore.setSelectedAIModelCompatImpl(model: String) {
    editActiveProfileImpl { it.copy(model = AIProvider.normalizeModelId(model)) }
}

/** First non-fallback profile with [provider], preferring the active one; -1 when absent. */
private fun pickProfileSlot(
    profiles: List<AiProfile>,
    fallbackId: String?,
    provider: AIProvider,
): Int {
    return profiles.indexOfFirst { it.provider == provider && it.id.toString() != fallbackId }
        .takeIf { it >= 0 }
        ?: profiles.indexOfFirst { it.provider == provider }
}

/**
 * Locates (or creates, when [createIfMissing] and no candidate exists) the
 * profile slot that per-provider legacy writes address, INSIDE one edit.
 */
private fun MutableList<AiProfile>.slotIndexFor(
    prefs: androidx.datastore.preferences.core.Preferences,
    provider: AIProvider,
    createIfMissing: Boolean,
): Int {
    val idx = pickProfileSlot(this, prefs[Keys.FALLBACK_AI_PROFILE_ID], provider)
    if (idx >= 0 || !createIfMissing) return idx
    this += AiProfile(
        id = UUID.randomUUID(),
        name = provider.name,
        provider = provider,
        createdAt = Instant.now(),
    )
    return size - 1
}

internal suspend fun PreferencesStore.setCustomBaseUrlCompatImpl(provider: AIProvider, url: String?) {
    dataStore.edit { prefs ->
        val list = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]).toMutableList()
        val idx = list.slotIndexFor(prefs, provider, createIfMissing = !url.isNullOrEmpty())
        if (idx < 0) return@edit
        list[idx] = list[idx].copy(baseUrl = url?.takeIf { it.isNotEmpty() })
        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(aiProfileListSerializer, list)
    }
}

internal suspend fun PreferencesStore.setVisionModelCompatImpl(provider: AIProvider, model: String?) {
    dataStore.edit { prefs ->
        val list = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]).toMutableList()
        val normalized = model?.takeIf { it.isNotBlank() }?.let(AIProvider::normalizeModelId)
        val idx = list.slotIndexFor(prefs, provider, createIfMissing = normalized != null)
        if (idx < 0) return@edit
        list[idx] = list[idx].copy(visionModel = normalized)
        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(aiProfileListSerializer, list)
    }
}

/** (Re)creates the single fallback-slot profile: provider switch resets model/url. */
internal suspend fun PreferencesStore.setSelectedFallbackProviderCompatImpl(p: AIProvider) {
    dataStore.edit { prefs ->
        val list = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]).toMutableList()
        val existingId = prefs[Keys.FALLBACK_AI_PROFILE_ID]
        val idx = list.indexOfFirst { it.id.toString() == existingId }
        val replaced = if (idx >= 0) {
            list[idx].copy(provider = p, model = null, baseUrl = null, visionModel = null)
        } else {
            AiProfile(
                id = UUID.randomUUID(),
                name = p.name,
                provider = p,
                createdAt = Instant.now(),
            )
        }
        if (idx >= 0) list[idx] = replaced else list += replaced
        prefs[Keys.FALLBACK_AI_PROFILE_ID] = replaced.id.toString()
        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(aiProfileListSerializer, list)
    }
}

internal suspend fun PreferencesStore.setSelectedFallbackModelCompatImpl(model: String) {
    dataStore.edit { prefs ->
        val list = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]).toMutableList()
        val existingId = prefs[Keys.FALLBACK_AI_PROFILE_ID]
        val idx = list.indexOfFirst { it.id.toString() == existingId }
        val fallback = if (idx >= 0) {
            list[idx].copy(model = AIProvider.normalizeModelId(model))
        } else {
            AiProfile(
                id = UUID.randomUUID(),
                name = AIProvider.GEMINI.name,
                provider = AIProvider.GEMINI,
                model = AIProvider.normalizeModelId(model),
                createdAt = Instant.now(),
            )
        }
        if (idx >= 0) list[idx] = fallback else list += fallback
        prefs[Keys.FALLBACK_AI_PROFILE_ID] = fallback.id.toString()
        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(aiProfileListSerializer, list)
    }
}

internal suspend fun PreferencesStore.setFallbackCustomBaseUrlCompatImpl(provider: AIProvider, url: String?) {
    dataStore.edit { prefs ->
        val list = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON]).toMutableList()
        val existingId = prefs[Keys.FALLBACK_AI_PROFILE_ID]
        val idx = list.indexOfFirst { it.id.toString() == existingId }
        if (idx < 0 || list[idx].provider != provider) return@edit
        list[idx] = list[idx].copy(baseUrl = url?.takeIf { it.isNotEmpty() })
        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(aiProfileListSerializer, list)
    }
}

/**
 * One-shot legacy single-slot → named-profile migration. Guard: any existing
 * `aiProfilesJson` means the profile world is already populated (double
 * invocation safe). The legacy provider/model/vision/url keys AND the legacy
 * per-provider keychain entries are deleted in the same pass — no downgrade
 * path (single F-Droid channel).
 *
 * Per qualifying provider (selected, fallback, has key, or has custom URL)
 * one profile is created; the fallback provider additionally gets a dedicated
 * `"<name> fallback"` profile from its fallback-slot fields and is skipped in
 * the primary sweep (unless it is ALSO the selected provider, whose primary
 * config must survive). Fresh install (nothing qualifies) → one default
 * Gemini profile so `activeAiProfile` never resolves null.
 */
internal suspend fun PreferencesStore.ensureAiProfilesMigratedImpl(keyStore: KeyStore, context: Context) {
    var fallbackProfileId: String? = null
    dataStore.edit { prefs ->
        if (prefs[Keys.AI_PROFILES_JSON] != null) return@edit

        val legacyProvider = AIProvider.values().firstOrNull { it.name == prefs[Keys.SELECTED_AI_PROVIDER] }
            ?: AIProvider.GEMINI
        val legacyModel = prefs[Keys.SELECTED_AI_MODEL]
        val legacyFallback = AIProvider.values().firstOrNull { it.name == prefs[Keys.FALLBACK_PROVIDER] }

        val usedNames = mutableSetOf<String>()
        fun uniqueName(base: String): String {
            var candidate = base
            var n = 2
            while (!usedNames.add(candidate)) {
                candidate = "$base $n"
                n++
            }
            return candidate
        }

        val profiles = mutableListOf<AiProfile>()
        val activeIds = mutableMapOf<AIProvider, String>()
        for (p in AIProvider.values()) {
            if (p == legacyFallback && p != legacyProvider) continue
            val baseUrl = prefs[Keys.customBaseUrl(p)]?.takeIf { it.isNotBlank() }
            val visionModel = prefs[Keys.visionModel(p)]?.takeIf { it.isNotBlank() }
            val key = keyStore.apiKey(p)
            if (p != legacyProvider && baseUrl == null && key == null) continue
            val profile = AiProfile(
                id = UUID.randomUUID(),
                name = uniqueName(context.getString(p.displayNameRes)),
                provider = p,
                model = if (p == legacyProvider) legacyModel else null,
                baseUrl = baseUrl,
                visionModel = visionModel,
                createdAt = Instant.now(),
            )
            profiles += profile
            activeIds[p] = profile.id.toString()
            keyStore.setProfileApiKey(profile.id, key)
        }

        var activeId = activeIds[legacyProvider]
        if (profiles.isEmpty()) {
            val def = AiProfile(
                id = UUID.randomUUID(),
                name = uniqueName(context.getString(AIProvider.GEMINI.displayNameRes)),
                provider = AIProvider.GEMINI,
                createdAt = Instant.now(),
            )
            profiles += def
            activeId = def.id.toString()
        }

        if (legacyFallback != null) {
            val fallback = AiProfile(
                id = UUID.randomUUID(),
                name = uniqueName(context.getString(legacyFallback.displayNameRes) + " fallback"),
                provider = legacyFallback,
                model = prefs[Keys.FALLBACK_MODEL],
                baseUrl = prefs[Keys.fallbackCustomBaseUrl(legacyFallback)]?.takeIf { it.isNotBlank() },
                createdAt = Instant.now(),
            )
            profiles += fallback
            fallbackProfileId = fallback.id.toString()
            keyStore.setProfileApiKey(fallback.id, keyStore.fallbackApiKey(legacyFallback))
        }

        prefs[Keys.AI_PROFILES_JSON] = json.encodeToString(aiProfileListSerializer, profiles)
        if (activeId != null) prefs[Keys.ACTIVE_AI_PROFILE_ID] = activeId
        if (fallbackProfileId != null) prefs[Keys.FALLBACK_AI_PROFILE_ID] = fallbackProfileId

        prefs.remove(Keys.SELECTED_AI_PROVIDER)
        prefs.remove(Keys.SELECTED_AI_MODEL)
        prefs.remove(Keys.FALLBACK_PROVIDER)
        prefs.remove(Keys.FALLBACK_MODEL)
        for (p in AIProvider.values()) {
            prefs.remove(Keys.visionModel(p))
            prefs.remove(Keys.customBaseUrl(p))
            prefs.remove(Keys.fallbackCustomBaseUrl(p))
        }
    }
    keyStore.clearLegacyAiKeys()
}

/** Compat reader: active-or-first non-fallback profile base URL for [provider]. */
internal fun PreferencesStore.customBaseUrlImpl(provider: AIProvider): Flow<String?> = combine(
    aiProfilesImpl, dataStore.data.map { it[Keys.FALLBACK_AI_PROFILE_ID] },
) { profiles, fallbackId ->
    profiles.getOrNull(pickProfileSlot(profiles, fallbackId, provider))?.baseUrl
}

/** Compat reader: active-or-first non-fallback profile vision slot for [provider]. */
internal fun PreferencesStore.visionModelImpl(provider: AIProvider): Flow<String?> = combine(
    aiProfilesImpl, dataStore.data.map { it[Keys.FALLBACK_AI_PROFILE_ID] },
) { profiles, fallbackId ->
    profiles.getOrNull(pickProfileSlot(profiles, fallbackId, provider))?.visionModel
}

    /** AI output-token cap sent with every request. Default 4096; set a lower
     *  cap for local models with small context windows. */
internal val PreferencesStore.maxResponseTokensImpl: Flow<Int> get() = dataStore.data.map {
        clampMaxResponseTokens(it[Keys.MAX_RESPONSE_TOKENS] ?: DEFAULT_MAX_RESPONSE_TOKENS)
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

/** Reasoning-effort control for OpenRouter reasoning-capable models (upstream #194)
 *  and selfhosted OpenAI-compatible hosts (#97). AUTO keeps the app's historical
 *  behavior: `exclude: true` always with `effort: "low"` on compact retries only
 *  (OpenRouter), and no reasoning field at all (Custom/Ollama). DISABLED asks for
 *  no thinking: `reasoning_effort: "none"` on Custom/Ollama, exclude-only on
 *  OpenRouter. Explicit efforts are sent with every request. */
enum class OpenRouterReasoningEffort(val storageKey: String, val requestValue: String?) {
    AUTO("auto", null),
    LOW("low", "low"),
    MEDIUM("medium", "medium"),
    HIGH("high", "high"),
    DISABLED("disabled", "none");

    @get:StringRes
    val displayNameRes: Int get() = when (this) {
        AUTO -> R.string.ai_reasoning_effort_auto
        LOW -> R.string.ai_reasoning_effort_low
        MEDIUM -> R.string.ai_reasoning_effort_medium
        HIGH -> R.string.ai_reasoning_effort_high
        DISABLED -> R.string.ai_reasoning_effort_disabled
    }

    companion object {
        fun fromStorage(raw: String?): OpenRouterReasoningEffort =
            entries.firstOrNull { it.storageKey == raw } ?: AUTO
    }
}

internal val PreferencesStore.openRouterReasoningEffortImpl: Flow<OpenRouterReasoningEffort> get() =
    dataStore.data.map { OpenRouterReasoningEffort.fromStorage(it[Keys.OPENROUTER_REASONING_EFFORT]) }

internal suspend fun PreferencesStore.setOpenRouterReasoningEffortImpl(e: OpenRouterReasoningEffort) {
    dataStore.edit { it[Keys.OPENROUTER_REASONING_EFFORT] = e.storageKey }
}

/** Manual override for the goal-recalc prompt tier (#123): local model names
 *  can't be classified by isSmallCloudModel, so users on Ollama/Custom
 *  endpoints can force the compact Safe prompt. AUTO keeps the model-name
 *  rules (on-device and small cloud models get Safe, everything else Smart). */
enum class GoalRecalcTierOverride(val storageKey: String) {
    AUTO("auto"), SAFE("safe"), SMART("smart");

    @get:StringRes
    val displayNameRes: Int get() = when (this) {
        AUTO -> R.string.goal_tier_auto
        SAFE -> R.string.goal_tier_safe
        SMART -> R.string.goal_tier_smart
    }

    companion object {
        fun fromStorage(raw: String?): GoalRecalcTierOverride =
            entries.firstOrNull { it.storageKey == raw } ?: AUTO
    }
}

internal val PreferencesStore.goalRecalcTierImpl: Flow<GoalRecalcTierOverride> get() =
    dataStore.data.map { GoalRecalcTierOverride.fromStorage(it[Keys.GOAL_RECALC_TIER]) }

internal suspend fun PreferencesStore.setGoalRecalcTierImpl(v: GoalRecalcTierOverride) {
    dataStore.edit { it[Keys.GOAL_RECALC_TIER] = v.storageKey }
}

internal const val MIN_MAX_RESPONSE_TOKENS = 256
internal const val MAX_MAX_RESPONSE_TOKENS = 16384
internal const val DEFAULT_MAX_RESPONSE_TOKENS = 4096
internal const val DEFAULT_AI_READ_TIMEOUT_SECONDS = 180
internal const val MIN_AI_READ_TIMEOUT_SECONDS = 30
internal const val MAX_AI_READ_TIMEOUT_SECONDS = 900

internal fun clampMaxResponseTokens(v: Int): Int =
    v.coerceIn(MIN_MAX_RESPONSE_TOKENS, MAX_MAX_RESPONSE_TOKENS)

internal fun clampAiReadTimeoutSeconds(v: Int): Int =
    v.coerceIn(MIN_AI_READ_TIMEOUT_SECONDS, MAX_AI_READ_TIMEOUT_SECONDS)

/** Effective advanced-setting value: per-profile override (clamped) over the global (clamped). */
internal fun effectiveMaxResponseTokens(profile: AiProfile?, global: Int): Int =
    profile?.maxResponseTokens?.let(::clampMaxResponseTokens) ?: clampMaxResponseTokens(global)

internal fun effectiveReadTimeoutSeconds(profile: AiProfile?, global: Int): Int =
    profile?.readTimeoutSeconds?.let(::clampAiReadTimeoutSeconds) ?: clampAiReadTimeoutSeconds(global)

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

internal val PreferencesStore.selectedFallbackProviderImpl: Flow<AIProvider> get() = fallbackAiProfileImpl.map {
        it?.provider ?: AIProvider.GEMINI
    }

/** Compat reader: fallback slot base URL (null when the slot is empty or another provider). */
internal fun PreferencesStore.fallbackProfileBaseUrlImpl(provider: AIProvider): Flow<String?> =
    fallbackAiProfileImpl.map { it?.takeIf { p -> p.provider == provider }?.baseUrl }

    /** When true, Gemini generateContent requests include the google_search grounding tool. */
internal val PreferencesStore.geminiGoogleSearchEnabledImpl: Flow<Boolean> get() = dataStore.data.map {
        it[Keys.GEMINI_GOOGLE_SEARCH_ENABLED] ?: false
    }
internal suspend fun PreferencesStore.setGeminiGoogleSearchEnabledImpl(v: Boolean) {
        dataStore.edit { it[Keys.GEMINI_GOOGLE_SEARCH_ENABLED] = v }
    }

/** When true, photo staging skips the required-note step. Default false. */
internal val PreferencesStore.skipPhotoNotePromptImpl: Flow<Boolean> get() = dataStore.data.map {
        it[Keys.SKIP_PHOTO_NOTE_PROMPT] ?: false
    }
internal suspend fun PreferencesStore.setSkipPhotoNotePromptImpl(v: Boolean) {
        dataStore.edit { it[Keys.SKIP_PHOTO_NOTE_PROMPT] = v }
    }

internal val PreferencesStore.photoNoteSkipCountImpl: Flow<Int> get() = dataStore.data.map {
        it[Keys.PHOTO_NOTE_SKIP_COUNT] ?: 0
    }
internal suspend fun PreferencesStore.setPhotoNoteSkipCountImpl(v: Int) {
        dataStore.edit { it[Keys.PHOTO_NOTE_SKIP_COUNT] = v.coerceAtLeast(0) }
    }

/** Completed photo staging Analyzes; tip card shows while below the guide threshold. */
internal val PreferencesStore.photoAccuracyGuideCountImpl: Flow<Int> get() = dataStore.data.map {
        it[Keys.PHOTO_ACCURACY_GUIDE_COUNT] ?: 0
    }
internal suspend fun PreferencesStore.setPhotoAccuracyGuideCountImpl(v: Int) {
        dataStore.edit { it[Keys.PHOTO_ACCURACY_GUIDE_COUNT] = v.coerceAtLeast(0) }
    }

    /**
     * Ask the food AI for optional `constituents[]` on composite meals.
     * Default on for cloud providers; [FoodAnalysisService] still forces this off
     * when the selected provider is on-device (local Gemma).
     */
internal val PreferencesStore.mealConstituentsEnabledImpl: Flow<Boolean> get() = dataStore.data.map {
        it[Keys.MEAL_CONSTITUENTS_ENABLED] ?: true
    }
internal suspend fun PreferencesStore.setMealConstituentsEnabledImpl(v: Boolean) {
        dataStore.edit { it[Keys.MEAL_CONSTITUENTS_ENABLED] = v }
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

// -- On-device memory headroom (release-visible dev setting, Codeberg #46) --
internal val PreferencesStore.onDeviceHeadroomPercentImpl: Flow<Int> get() = dataStore.data.map { it[Keys.ON_DEVICE_HEADROOM_PERCENT] ?: 0 }
internal suspend fun PreferencesStore.setOnDeviceHeadroomPercentImpl(v: Int) { dataStore.edit { it[Keys.ON_DEVICE_HEADROOM_PERCENT] = v.coerceIn(0, 20) } }

    /** Rollout gate: whether ON_DEVICE appears as a selectable provider. Default on since 1.14.0. */
internal val PreferencesStore.onDeviceFeatureVisibleImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.ON_DEVICE_FEATURE_VISIBLE] ?: true }
internal suspend fun PreferencesStore.setOnDeviceFeatureVisibleImpl(v: Boolean) { dataStore.edit { it[Keys.ON_DEVICE_FEATURE_VISIBLE] = v } }

// -- One-snapshot AI call config ---------------------------------------

/**
 * Every setting an entry-analysis or coach-chat call needs, decoded from
 * ONE `dataStore.data` snapshot. The call preambles used to do 10-12
 * sequential `.first()` reads — wasted flow setups, and a settings write
 * landing mid-call could tear provider vs. model vs. base-url so the
 * primary and its fallback mixed old and new config. Read fresh per call;
 * never cache across calls.
 */
internal data class AiCallConfig(
    /** Active (or override) profile driving the primary attempt. */
    val profile: AiProfile,
    /** Fallback profile; null = no fallback (or fallback == [profile]). */
    val fallbackProfile: AiProfile?,
    val aiFeaturesEnabled: Boolean,
    val userContext: String,
    val allowInsecureHttp: Boolean,
    val maxResponseTokens: Int,
    val aiReadTimeoutSeconds: Int,
    val geminiGoogleSearch: Boolean,
    val reasoningEffort: OpenRouterReasoningEffort,
    val goalRecalcTier: GoalRecalcTierOverride,
    val fallbackEnabled: Boolean,
) {
    // Derived compat accessors: pre-profile call sites read provider/model/url
    // straight off the config snapshot.
    val provider: AIProvider get() = profile.provider
    val selectedModel: String? get() = profile.model
    /** Raw profile base URL; normalize at the call site. */
    val customBaseUrl: String? get() = profile.baseUrl
    /** Raw profile vision slot. */
    val visionModel: String? get() = profile.visionModel
}

/** In-memory stand-in used when the profile store is empty (pre-migration call). */
internal fun defaultAiProfileSnapshot(provider: AIProvider = AIProvider.GEMINI): AiProfile =
    AiProfile(
        id = UUID.randomUUID(),
        name = provider.name,
        provider = provider,
        createdAt = Instant.now(),
    )

internal suspend fun PreferencesStore.aiCallConfigImpl(profileOverride: AiProfile? = null): AiCallConfig {
    val prefs = dataStore.data.first()
    val profiles = decodeAiProfiles(prefs[Keys.AI_PROFILES_JSON])
    val profile = profileOverride
        ?: profiles.firstOrNull { it.id.toString() == prefs[Keys.ACTIVE_AI_PROFILE_ID] }
        ?: profiles.firstOrNull()
        ?: defaultAiProfileSnapshot()
    val fallbackProfile = profiles.firstOrNull {
        it.id.toString() == prefs[Keys.FALLBACK_AI_PROFILE_ID] && it.id != profile.id
    }
    return AiCallConfig(
        profile = profile,
        fallbackProfile = fallbackProfile,
        aiFeaturesEnabled = prefs[Keys.AI_FEATURES_ENABLED] ?: true,
        userContext = prefs[Keys.USER_CONTEXT].orEmpty(),
        allowInsecureHttp = prefs[Keys.ALLOW_INSECURE_HTTP] ?: false,
        maxResponseTokens = effectiveMaxResponseTokens(profile, prefs[Keys.MAX_RESPONSE_TOKENS] ?: DEFAULT_MAX_RESPONSE_TOKENS),
        aiReadTimeoutSeconds = effectiveReadTimeoutSeconds(profile, prefs[Keys.AI_READ_TIMEOUT_SECONDS] ?: DEFAULT_AI_READ_TIMEOUT_SECONDS),
        geminiGoogleSearch = profile.geminiGoogleSearch ?: (prefs[Keys.GEMINI_GOOGLE_SEARCH_ENABLED] ?: false),
        reasoningEffort = profile.reasoningEffort?.let(OpenRouterReasoningEffort::fromStorage)
            ?: OpenRouterReasoningEffort.fromStorage(prefs[Keys.OPENROUTER_REASONING_EFFORT]),
        goalRecalcTier = GoalRecalcTierOverride.fromStorage(prefs[Keys.GOAL_RECALC_TIER]),
        fallbackEnabled = prefs[Keys.FALLBACK_ENABLED] ?: true,
    )
}
