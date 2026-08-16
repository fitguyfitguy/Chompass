Status: implemented (2026-08-16, commits `a05ec03`…`06e5fa1`) — see § Work plan status below for per-phase state.

# Security, integrity & privacy hardening plan

Goal: one analysis pass over every external-input and data-leaving surface of
Chompass (Android in depth, PWA companion at parity), flag concrete risks with
severity, and propose fixes. Executes as a **plan-first, code-later** exercise:
this document inventories the surfaces, records what is already sound (so we
don't regress it), and lists the hardening work in priority order.

Baselines to keep in mind while reading:

- [`PRIVACY.md`](PRIVACY.md) — the published privacy stance (local-first, BYOK,
  no ads/analytics). This plan **verifies** the code against that doc; mismatches
  are findings.
- [`AGENTS.md`](../../AGENTS.md) § "Food / entry intent invariants" — shared-meal,
  share, gallery, camera, shortcuts and notification channels must stay separate.
  Several findings touch these exact channels.
- Product of record is Android (`android/app/`). PWA (`web/app/`) must stay
  parity-compatible; its findings are lighter-weight but not ignored.

## 1. Attack-surface inventory (what the pass covers)

| # | Surface | Entry point | Notes |
|---|---------|-------------|-------|
| S1 | Debug intent extras | `MainActivity` (exported; deep links + share aliases) | **P1 finding** |
| S2 | `chompass://add-meal?d=` and `fudai://`/`nofud://` legacy links | `MealShare.meals()` in `handleLaunchIntent` | **P2 findings** |
| S3 | `chompass://go/<dest>` notification/deep link | `handleLaunchDestination` → `nav.navigate(dest)` | **P2 finding** |
| S4 | Image share-in / gallery / camera bytes | `handleSharedImages`, `foodGalleryPicker`, `FoodPhotoSession`, `FoodImageStore`, `AiImageBytes`, `BarcodeImageDecoder` | **P2 findings** |
| S5 | AI prompt construction (food analysis, OFF context, coach, goals) | `FoodAnalysisService`, `OffPromptContext`, `ChatService`, `CoachTools` | **P2 findings** |
| S6 | AI output parsing → persistence | `FoodJsonParser`, `MealShare.entryFrom`, `DiaryImporter` | **P2 finding** |
| S7 | Credentials at rest + in transit | `KeyStore`, `WebDavClient`, `AiHttp`, `LocalEndpointTrust`, `network_security_config` | Mostly sound; P3 items |
| S8 | Exported components | manifest: `MainActivity`, 8 launcher aliases, `HealthPermissionsRationaleActivity`, 4 widget receivers, FileProvider | Review; mostly sound |
| S9 | Backup / device transfer | `allowBackup`, `backup_rules.xml`, `data_extraction_rules.xml` | P3 items |
| S10 | On-device model pipeline | `ModelDownloadWorker`, `OnDeviceLlmClient` | Sound (SHA-256 verified); verify direction |
| S11 | Third-party data egress | OFF, Open-Meteo, STT providers, WebDAV, AI providers | Verify against PRIVACY.md |
| S12 | PWA companion | `web/app` meal-share decode, `innerHTML` render sites, sync, key storage | P3 items |

## 2. What is already sound (do not regress)

Called out so the hardening pass doesn't churn working code:

- **Keys:** `EncryptedSharedPreferences` AES-256-GCM/SIV backed by Android
  Keystore (`data/KeyStore.kt`); per-provider key prefixes incl. isolated
  fallback-slot keys; keychain file excluded from both cloud backup and
  device transfer; recovery path wipes keystore alias on AEAD mismatch.
- **Release build:** `minifyEnabled` + shrink resources on; `GEMINI_API_KEY`
  buildConfigField is `""` in release (never baked); no Play Core (`InAppReview`
  is a no-op stub); update checker is a local no-op (no remote integrity surface).
- **Network:** release `network_security_config` is HTTPS-only; user-CA trust is
  scoped to the one user-entered provider (CUSTOM_OPENAI); the comment and design
  in `LocalEndpointTrust` correctly avoid letting a user-installed CA intercept
  cloud AI traffic, WebDAV, or STT.
- **Model downloads:** streamed to `.part`, SHA-256 verified before atomic rename,
  free-space checked (`ModelDownloadWorker`).
- **LLM tool loops:** `MAX_TOOL_ROUNDS = 6`; `CoachTools` caps tool results at 365
  rows; `propose_log_*` tools never persist — a user confirmation is required.
- **OFF hygiene:** NaN/Infinity filtered (`flexibleDouble`), `GroundingValidator`
  plausibility checks (kJ/kcal, g→mg sodium), per-100g bounds.
- **Exported surface:** FileProvider exposes only `cache/capture`; `ReminderReceiver`
  not exported; widget receivers handle only benign APPWIDGET_UPDATE.
- **Web:** shared-meal and OFF names are HTML-escaped before `innerHTML` in the
  data-rendering components (66 escape call sites; spot-checked `add-meal-view.js`
  and `entry-form.js`).

## 3. Findings

Severity: **P1** = data corruption / integrity break reachable by any app or link
without user consent; **P2** = crash / DoS / integrity break under realistic but
crafted input; **P3** = policy, hygiene, hardening.

### P1-1 Ungated debug intent extras run in release builds

- **Files:** `MainActivity.kt` (`consumeDebugIntentExtras` + `launchDebugIntentActions`),
  `services/TestDataSeeder.kt`, `services/LauncherShortcuts.kt`.
- **Issue:** `seed_test_data`, `seed_body_metrics`, `seed_body_metrics_2y`,
  `seed_keto_settings`, `seed_active_calories`, `seed_over_goal`,
  `reset_onboarding`, `restore_real_data`, and the `set_*` display extras are
  consumed with **no `BuildConfig.DEBUG` gate**, while `demo_ai`,
  `clear_pending_draft`, `run_entry_benchmark`, `run_ondevice_llm_test`,
  `diagnose_health_connect` are gated — the pattern to follow already exists.
  `MainActivity` is exported for legitimate deep links, so **any installed app**
  can deliver these extras.
- **Impact:** a five-line `startActivity` from any app can silently overwrite the
  entire diary / weight / body-fat history with sample data (seeders call
  `replaceAll`), reset onboarding, or flip settings. `restore_real_data` swaps in
  the debug snapshot blob. Side-effect: `snapshotRealDataIfNeeded` stores a full
  copy of the user's real diary+profile in DataStore key `testSeedBackupJson`.
- **Fix:** gate every one of these behind `BuildConfig.DEBUG &&` (mirror the
  existing inline pattern); strip the extras in release so `recreate()` can't
  re-fire; add a unit test that asserts a release-flagged parse ignores them.
  Reroute the "restore" path so the debug snapshot blob cannot exist or be read in
  release builds (see P3-2).

### P2-1 Deep-link crash: unvalidated `chompass://go/<dest>`

- **Files:** `MainActivity.handleLaunchDestination`, `ui/navigation/ChompassRoutes`,
  `ui/navigation/NoFUDNavHost` `launchDestinationInbox` collector.
- **Issue:** `dest` is any path string; `nav.navigate(dest)` throws
  `IllegalArgumentException` for a route that doesn't exactly match a registered
  pattern. An app sends `ACTION_VIEW` `chompass://go/whatever` → crash; a valid
  route requiring args (e.g. `settings/water?from={from}`) sent without the arg
  crashes the same way.
- **Fix:** whitelist `dest` against `ChompassRoutes` (exact match or registered
  arg-pattern match) before setting `launchDestinationInbox`; ignore unknown
  values. Add a crash-regression instrumentation test.

### P2-2 Shared-meal links: main-thread decode + unbounded payload

- **Files:** `MainActivity.handleLaunchIntent`, `services/MealShare.kt`.
- **Issue:** `MealShare.meals(uri)` runs URL-decoder + Base64 decode + `JSONObject`
  parse synchronously on the main thread during `onCreate`/`onNewIntent`, with no
  byte cap and no caps on meal/constituent counts. A crafted ~1 MB intent payload
  → ANR/OOM from a cold start. Imported values (`entryFrom`) are not sanitized:
  `optInt("calories")`/`optDouble` accept NaN, negatives, absurd magnitudes; name
  / customNote accept control characters and bidi overrides that later render in
  UI and can be interpolated into AI prompts.
- **Fix:** cap encoded `d` length (e.g. 256 KB) and meals (e.g. 50) / constituents
  (e.g. 20); decode off the main thread before pushing `pendingSharedMeals`;
  validate every numeric field (finite, ≥ 0, per-nutrient upper bounds) and scrub
  names/notes (strip control chars, cap length) in `entryFrom`. Add fixture-based
  unit tests mirroring `diary-format` validation style.

### P2-3 Unbounded / under-sampled image ingestion

- **Files:** `MainActivity.handleSharedImages`, `foodGalleryPicker`,
  `services/FoodImageStore.kt`, `services/ai/AiImageBytes.kt`,
  `services/BarcodeImageDecoder.kt`.
- **Issue:** shared/gallery URIs are read with `readBytes()` (no per-image byte
  cap; 10 images staged as raw `ByteArray`s in `FoodPhotoSession`). The
  analysis/upload and barcode paths (`AiImageBytes.jpegForUpload`,
  `BarcodeImageDecoder`) call `BitmapFactory.decodeByteArray` at full resolution
  without `inSampleSize` — only `FoodImageStore` samples. A sender sharing a
  huge-dimension image → OOM crash on the analysis path. Additionally
  `FoodImageStore.storeBytes` falls back to writing **raw undecoded bytes** to
  disk when decode fails (disk-fill; file re-run through decoders later).
- **Fix:** cap bytes per image (e.g. 25 MB) and per batch at ingest; make
  `AiImageBytes` / `BarcodeImageDecoder` bounds-first (`inJustDecodeBounds`) like
  `FoodImageStore`; never persist undecodable raw bytes (store a stub / drop);
  add a decompression-bomb regression test.

### P2-4 Prompt injection via untrusted data + trusting LLM output

- **Files:** `services/ai/FoodAnalysisService.kt` (all prompt builders +
  `callAi`), `services/OffPromptContext.kt`, `services/ai/FoodJsonParser.kt`,
  `services/ai/ChatService.kt` `buildSystemPrompt`, `services/MealShare.kt`.
- **Issue A (injection):** user free text (`analyzeText(description)`,
  `appendUserMealContext`) is interpolated **before** the "Respond ONLY with
  JSON" constraints; the persistent `userContext` block and OFF product names
  (public, user-editable DB) are interpolated as authoritative prose
  (`OffPromptContext.format` → `- name: ${hit.name}`) with no untrusted-data
  delimiters. A crafted shared meal `customNote`/name, a hostile OFF product
  name/brand, or a description can steer food-analysis and coach outputs.
- **Issue B (output trust):** `FoodJsonParser.parseFood` does **not** clamp
  calories/macros or guard NaN/Infinity (contrast `parseGoalCalculation` clamps
  800–6000 and `parseOptionalNutrientGoals` coerces ≥ 0). An injected model
  response can persist negative or absurd values; `MealShare.entryFrom`
  (P2-2) can inject them directly.
- **Fix:** wrap all untrusted values in `<user_data>…</user_data>` (or equivalent)
  with an explicit "data, not instructions" sentence in **every** prompt builder
  (food analysis, OFF context, coach diary/context); put user text **after** the
  output constraints; sanitize centrally in `FoodJsonParser` — finite, ≥ 0,
  bounded per-nutrient maxima, name length + control-char scrub, emoji whitelist;
  validate at MealShare import (P2-2). Add adversarial fixtures
  (`testdata/parity/`-style) with injection payloads in names/notes/OFF names.

### P3-1 Backup/device transfer includes health diary + photos

- **Files:** `AndroidManifest.xml` (`allowBackup="true"`), `res/xml/backup_rules.xml`,
  `res/xml/data_extraction_rules.xml`.
- **Issue:** only the keychain is excluded. Diary DataStore, profile, food photos
  (`filesDir/fudai-food-images`), and — after any debug seeding — the full-diary
  `testSeedBackupJson` copy are in cloud backup/device transfer.
- **Decision needed:** device transfer / backup of user data is a feature; the
  open questions are (a) exclude `testSeedBackupJson` from backup and from release
  builds entirely (P1-1), (b) optionally exclude photo dirs or note the tradeoff,
  (c) document. Add a checklist item; don't silently flip `allowBackup=false`.

### P3-2 Debug seed snapshot holds a real-diary copy

- **File:** `services/TestDataSeeder.kt` (`snapshotRealDataIfNeeded`,
  `restore`), `data/PreferencesStoreMisc.kt` (key `testSeedBackupJson`).
- **Issue:** after a debug seed runs, the user's full real diary/profile live in a
  plaintext DataStore key that also goes to cloud backup (P3-1). In release
  builds the seed extras must be inert (P1-1), which also keeps this key empty.
- **Fix:** fold into P1-1; verify the key is never written when `!BuildConfig.DEBUG`.

### P3-3 Credential/network hygiene items

- `sync/WebDavUrl.kt` + `data/PreferencesStoreSync.kt`: WebDAV **username** is
  plaintext DataStore (password is encrypted in the keychain) — acceptable for a
  user-hosted server but worth a comment/decision; password must never be stored
  anywhere else.
- Release `network_security_config` permits cleartext to `10.0.2.2` (emulator
  loopback only) — negligible; consider removing for a stricter release posture.
- `KeyStore.openOrRecover` wipes the entire keychain on any open failure — a
  crash-recovery tradeoff that already has a comment; keep but note in docs.
- Custom OpenAI base URL can target LAN/loopback — by design (user-entered
  endpoint; the device is the caller); add an explicit settings-screen note so
  users know the endpoint receives their data, including when pointing at
  "localhost" on a non-emulator device where that resolves differently.

### P3-4 Privacy policy vs. code verification

- **Files:** `services/weather/OpenMeteoClient.kt`, `data/WeatherRepository.kt`,
  `services/speech/SpeechService.kt`, manifest Health Connect permission block.
- **Verify and align PRIVACY.md:** (a) weather sends city lat/lon + city name to
  Open-Meteo — the PRIVACY.md network table doesn't list weather; add it.
  (b) STT sends recorded audio to the chosen provider only when a cloud STT
  provider is selected; confirm the default is on-device/Native and that remote
  STT requires explicit provider selection. (c) `READ_HEALTH_DATA_IN_BACKGROUND`
  is declared — confirm default-off and that onboarding/settings copy states it.
  (d) Confirm no release path sends screenshots to a provider (the
  `HighlightScreenshotContent` composable is marketing-capture only; `demo_ai` is
  debug-gated).

### P3-5 PWA companion

- `web/app/src/lib/meal-share.js`: cap decoded payload size (URL-length bound
  exists, but be explicit) and validate numeric fields — parity with P2-2.
- `innerHTML` audit: 66 escape call sites are good; the remaining non-escaped
  sites render static content — add a one-time audit step with a lint rule if easy
  (no bundler; a custom grep script in `scripts/`).
- No CSP observed on the served site; recommend `Content-Security-Policy` +
  `X-Content-Type-Options` headers on static hosting (Hugo/Pages config) and a
  `nosniff` check.
- `sync.js` stores WebDAV config (URL/username) in localStorage — keep the
  password in the Web Crypto keyring pattern used by AI keys if it isn't already.

### P3-6 Verification tooling (add to the pass, not a bug)

- Release-build regression tests for all P1/P2 items (JVM unit + one or two
  instrumentation cases run via Windows adb on the release APK).
- `./gradlew lintRelease` run + fix of security-relevant lint findings.
- Dependency audit: no Gradle lockfile today; recommend a scheduled
  `dependencyUpdates` / OSV-Scanner or Trivy job on `--all` deps before releases.
- `apksigner verify --print-certs` and `apkanalyzer manifest print` in the
  release checklist; F-Droid reproducibility note.

## 4. Work plan (ordered)

| Phase | Item | Deliverable |
|-------|------|-------------|
| 0 | Baseline | Add a `docs/local/THREAT_MODEL.md` (threat actors: other installed apps, malicious deep links, hostile OFF/public data, LLM injection, device-transfer extraction); severity rubric above. Small. |
| 1 | P1-1 debug extras | Gate all un-gated extras on `BuildConfig.DEBUG`; strip in release; ensure `testSeedBackupJson` never written in release; unit test. **Smallest risk, do first.** |
| 2 | P2-1/P2-2 deep links | Whitelist `go` dest; caps on `d`/meals/constituents; off-main decode; MealShare value + string sanitization; crash-regression tests. |
| 3 | P2-3 image pipeline | Byte caps at ingest; bounds-first decode in `AiImageBytes`/`BarcodeImageDecoder`; no raw-bytes persistence; bomb test fixtures. |
| 4 | P2-4 AI hardening | Untrusted-data delimiters in all prompt builders; `FoodJsonParser` central sanitization (finite/bounds/name scrub); adversarial prompt fixtures + tests; OFF-context delimiter. |
| 5 | P3 policy | Backup/extraction decision (exclude `testSeedBackupJson`, photo-dir decision); PRIVACY.md updates (weather, STT, background Health Connect); WebDAV username note; release `10.0.2.2` cleanup; custom-endpoint settings copy. |
| 6 | Verification | `lintRelease`; dep audit; release-APK rehearsal of every finding via Windows adb on a debug-flavored *release-simulating* build (or the release APK on a test device): fire `seed_test_data`, `chompass://go/nonsense`, giant share image, hostile meal-share URL; capture before/after crashes. Update `docs/CHANGELOG.md` + check `release:package` parity gates. |

Each phase lands as separate commits; PWA changes (`web/app/`) must keep the
`contracts/` + `testdata/parity/` gates green where validation behavior changes.

## 5. Work plan status (2026-08-16)

All phases executed as separate commits (`a05ec03` P0+1 … `06e5fa1` P5);
Phase 6 verification below. No commit attributes to AI.

| Phase | Status | Notes |
|-------|--------|-------|
| 0 | ✅ done | `docs/local/THREAT_MODEL.md` (unversioned working doc, per repo convention) |
| 1 | ✅ done | `MainActivityDebugExtras.kt` gates everything on `BuildConfig.DEBUG`; `TestDataSeeder.snapshotRealDataIfNeeded`/`restore` no-op in release; `MainActivityDebugExtrasGateTest` (4 tests) |
| 2 | ✅ done | `ChompassRoutes.isGoDestination` whitelist + `ChompassRoutesTest`; MealShare payload/row caps + `InputSanitizer` + off-main decode; `MealShareSecurityTest` (7 tests) |
| 3 | ✅ done | 25 MB/image + 150 MB/batch ingest caps; bounds-first decode in `AiImageBytes` + `BarcodeImageDecoder`; `FoodImageStore` no longer persists undecodable raw bytes; `InputSanitizerTest` |
| 4 | ✅ done | `<user_data>`/`<external_data>` delimiters + delimiter-token neutralization in food-analysis, OFF-context, coach prompts; `FoodJsonParser` clamping/scubbing; `FoodJsonParserSecurityTest` + `OffPromptContextTest`; benchmark-status log entry #24 |
| 5 | ✅ done | PRIVACY.md network table now lists weather + on-device STT default + background Health-Connect wording; release netsec drops `10.0.2.2`; WebDAV username comment; PWA meal-share caps mirrored + parity tests; PWA meta CSP |
| 6 | ✅ partial | `release:check-parity` ✓, full `testDebugUnitTest` ✓, `lintRelease` ✓ no new errors (baseline 989 `MissingTranslation` + Compose warnings pre-exist; no new strings added for that reason). **Not run:** device rehearsal (per maintainer, no device available), dep-audit CI job, server-header CSP, custom-endpoint settings copy (deferred: would add 16× localized strings; documented in PRIVACY.md instead). |

### Verification results

- `devenv tasks run release:check-parity` — PASS (PWA `node --test` incl. new
  meal-share cap tests, `tsc`, fixture/schema validation).
- `./gradlew :app:testDebugUnitTest` — PASS (no regressions; new tests green).
- `./gradlew :app:lintRelease` — FAILS on pre-existing baseline only
  (989 `MissingTranslation` + Compose `LocalContext` calls); **no lint error in
  any file touched by this pass**. Lint is not part of the healthy release path
  today; a separate maintenance pass could address `MissingTranslation` config.
- ktlint — clean on all touched files.

### Manual rehearsal checklist (device, when available)

Install the release APK (or a debug build standing in for release) and verify:

```
# 1. Debug extras inert in release (P1-1)
adb shell am start -n app.chompass/app.chompass.MainActivity --ez seed_test_data true
adb shell am start -n app.chompass/app.chompass.MainActivity --ez restore_real_data true
adb shell am start -n app.chompass/app.chompass.MainActivity --ez reset_onboarding true
# Expect: diary/onboarding unchanged on release; works as before on app.chompass.debug

# 2. Unknown go-destination no longer crashes (P2-1)
adb shell am start -a android.intent.action.VIEW -d "chompass://go/__garbage__" app.chompass
# Expect: app opens (or resumes); no crash. Known routes (e.g. /progress) still navigate.

# 3. Oversized meal-share payload is refused, not ANR (P2-2)
# Craft d= with >64 KB of base64 (see MealShareSecurityTest.oversizedPayload_isRejected)
adb shell am start -a android.intent.action.VIEW -d "chompass://add-meal?d=<big>" app.chompass
# Expect: nothing staged; app responds promptly.

# 4. Oversized shared image is skipped, not OOM (P2-3)
# Share a 40 MB image via the share sheet from another app (or gallery pick).
# Expect: photo skipped / import-failed snackbar; process alive.
```

### Follow-up recommendations (not blocking, tracked)

- Server-header `Content-Security-Policy` for the hosted PWA (meta tag covers
  in-page; headers also protect pre-meta render) + `X-Content-Type-Options`.
- OSV/Trivy dependency scan wired into CI (no Gradle lockfile today; add
  `dependencyLocking` first).
- Custom-endpoint settings copy (needs 16-locale strings) — defer to a release
  that already touches l10n.
- Re-benchmark food-accuracy prompt wording once a harness run is due
  (see benchmark-status log entry #24).

## 6. Out of scope for this pass

- Encrypted-backup opt-in (targetSdk feature with UX impact) — separate decision.
- Full PWA CSP/keyring migration — tracked in P3-5 as recommendations.
- Gradle dependency lockfile — P3-6 recommendation, needs a maintainer decision.
- Grounded entry (USDA/OFF/history) is feature-flagged WIP; when it ships,
  re-run this pass against `services/grounding/*` (prompt boundaries, dataset
  integrity, per-component validation).