# PLAN_AI_PROFILES.md

Named AI profiles + queue retry override. Multi-commit work (Android only; no
PWA/parity surface — provider settings are app-local, diary/backup export
excludes them). Delete this file at the release that ships the work.

## End state

- `AiProfile` (models/AiProfile.kt): id, name, provider, model (null =
  provider default at resolve time), baseUrl, visionModel, createdAt.
- Storage: `aiProfilesJson` + `activeAiProfileId` + `fallbackAiProfileId`
  (DataStore), per-profile API keys in KeyStore under `apikey_profile_<id>`.
- One active profile drives analysis / coach / fill; one optional fallback
  profile replaces the old fallback provider/model/key/url fields.
- Queue entries carry `lastAttemptProfile` / `lastAttemptModel` and support a
  one-off "Run with…" override; the active profile stays untouched.
- One-shot migration `ensureAiProfilesMigrated` (ChompassApp.onCreate):
  legacy `selectedAIProvider`/`selectedAIModel`/`visionModel_*`/
  `customBaseURL_*`/`selectedFallbackAI*` keys and legacy keychain entries
  (`apikey_*`, `apikey_fallback_*`) are copied into profiles and deleted. No
  downgrade path (single F-Droid channel).

## Batches (commit order)

1. `AiProfile` model + store flows/mutators + KeyStore per-profile keys +
   migration + `AiProfileStoreTest` (round-trip, fallback-to-first, malformed
   JSON, 3-profile legacy migration + idempotency, fresh install).
2. `AiCallConfig` rebuilt around `profile`/`fallbackProfile` (+ derived
   accessors), `aiCallConfig(profileOverride)`, service cutover
   (FoodAnalysisService, ChatService, SettingsPrefsHydration), keyLookup seam
   keyed by `AiProfile`, queue override threading, `AiProfileOverrideTest`.
3. Settings + onboarding UI: profile switcher / add / rename / delete sheets,
   per-profile model/vision/key/url rows, single fallback-profile row,
   profile-backed onboarding, locale strings.
4. Queue UI: Run-with action + profile picker + last-tried caption.
5. Sweep: no references left to removed legacy APIs; docs; this file.

## Deliberate behavior changes

- Unset fallback = "no fallback" (was implicit keyless Gemini that always
  no-op'd; equivalent in practice).
- Switching a profile's provider resets that profile's model to the provider
  default and clears its baseUrl/visionModel (the old bug clobbered the global
  model on every provider switch; now scoped to the profile being edited).
- An override run keeps the globally configured fallback profile (one-line
  flip in `aiCallConfigImpl` if that surprises in use).
