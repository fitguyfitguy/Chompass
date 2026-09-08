# On-device LLM (Gemma 4 E2B-it)

Runs **Gemma 4 E2B-it** locally via [Google AI Edge LiteRT-LM](https://developers.google.com/edge/litert-lm/android). Originally a debug-only proof of concept validating food-analysis JSON extraction (Tier A), food photo analysis (Tier B), and Coach tool-calling (Tier C); Tier A (text) and Tier B (photo) are now wired into production dispatch as `AIProvider.ON_DEVICE`, gated behind a default-off Settings toggle (`onDeviceFeatureVisible`).

**Status (2026-07-15):** Smoke test **passes end-to-end** on **Pixel 9a / GrapheneOS** with GPU backend (`litertlm-android` **0.14.0**). **Tier B vision + daily matrix complete** (runs 1 and 4 recorded). Recommended daily-driver: **`preset=daily`** (`gpu` + `fewshot_units` + MTP when cache warm).

**Device coverage note:** all validation above, plus the production integration's device-capability gate ([`OnDeviceCapability.kt`](../android/app/src/main/java/app/chompass/services/ondevice/OnDeviceCapability.kt), 6GB RAM floor), is based on **Pixel 9a only**. The production plan's Phase 0 called for a second, lower/mid-tier non-Tensor device to validate the CPU-backend fallback path before shipping; that pass was explicitly skipped for this integration. The RAM floor and CPU-fallback latency are provisional until a second device is tested. Settings surfaces this via a note in the on-device model download sheet ("Tested on Pixel 9a (GrapheneOS)...").

**Summary:** Gemma 4 E2B-it on-device via LiteRT-LM. Tier A (text) + Tier B (photo) are production-wired as `AIProvider.ON_DEVICE` behind a default-off Settings toggle; Tier C (coach) stays debug-only. Smoke-validated on Pixel 9a / GrapheneOS, GPU backend; recommended daily preset `preset=daily`. A second non-Tensor device pass is still outstanding (CPU fallback provisional). Debug extras: `run_ondevice_llm_test` + `ondevice_llm_*` intents: see § Debug hooks below.

## Production integration (Milestone 1: Tier A)

| Piece | File |
|-------|------|
| Provider enum | [`AIProvider.kt`](../android/app/src/main/java/app/chompass/models/AIProvider.kt): `ON_DEVICE` / `ApiFormat.ON_DEVICE` |
| Model catalog | [`ModelCatalog.kt`](../android/app/src/main/java/app/chompass/services/ondevice/ModelCatalog.kt): HF repo, filename, sha256, size |
| Download | [`ModelDownloadManager.kt`](../android/app/src/main/java/app/chompass/services/ondevice/ModelDownloadManager.kt) / [`ModelDownloadWorker.kt`](../android/app/src/main/java/app/chompass/services/ondevice/ModelDownloadWorker.kt): WorkManager `dataSync` foreground service, streamed OkHttp download with HTTP Range resume (never truncates `.part` on 200), SHA-256 hashed while writing (sidecar so a crash at 100% does not re-read the file), atomic rename into `filesDir/models/` |
| Capability gate | [`OnDeviceCapability.kt`](../android/app/src/main/java/app/chompass/services/ondevice/OnDeviceCapability.kt): ABI + RAM floor |
| Engine lifecycle | [`OnDeviceLlmGateway.kt`](../android/app/src/main/java/app/chompass/services/ondevice/OnDeviceLlmGateway.kt): process-scoped lazy singleton, explicit unload |
| Dispatch | [`FoodAnalysisService.dispatch()`](../android/app/src/main/java/app/chompass/services/ai/FoodAnalysisService.kt) via [`OnDeviceLlmDispatchClient.kt`](../android/app/src/main/java/app/chompass/services/ai/OnDeviceLlmDispatchClient.kt) |
| Settings UX | [`SettingsAiSection.kt`](../android/app/src/main/java/app/chompass/ui/settings/SettingsAiSection.kt) / [`OnDeviceModelSheet.kt`](../android/app/src/main/java/app/chompass/ui/settings/OnDeviceModelSheet.kt) |

`OnDeviceLlmDispatchClient` routes to `generateWithImage` whenever an image is attached, so Tier B (photo) flows through the same dispatch path as Tier A, but it hasn't been re-validated against this specific pipeline (only against the debug smoke test harness), and the vision-enabled engine is a separate, larger load than text-only. Treat Tier B as untested-in-production until it gets its own pass. Not yet done: Tier C (Coach tool-calling), the `onDeviceFeatureVisible` flag flipping to default-on, and F-Droid packaging (currently `implementation` for both flavors pending an F-Droid policy check; see `build.gradle.kts` comment).

**Lean entry schema (#68, 2026-09):** every entry op (`analyzeText`, `analyzeAuto`, `analyzeFood`, `analyzeFoodMulti`) sends the on-device provider a **lean JSON schema** — `name, calories, protein, carbs, fat, serving_size_grams, emoji, unit_options[]` (`EntryConstituentPromptKind.LEAN` in `FoodAnalysisService.kt`) instead of the 34-field meal schema with 21 micronutrients. The full schema pushed E2B replies toward the litertlm output cap, and truncated (unbalanced) JSON is the grounded mechanism behind the `AiError.InvalidResponse` reports on 4.7.0 ([#68](https://codeberg.org/fitguy/Chompass/issues/68)). Accepted tradeoff: on-device entries and label photos carry **no micronutrient estimates** (they stay available with cloud providers; E2B micro quality was never validated). `analyzeLabel` and `recognizeFood` keep their hardcoded schemas. Parse failures now log one `FudAIPerf` diagnostics line in release builds (`op=entryParse phase=fail chars=… balanced=…`): `balanced=false` ⇒ truncation, `balanced=true` ⇒ garbage. Ask for it with `adb logcat -d -s FudOnDeviceLlm FudAIPerf`.

---

## Scope

| In scope | Out of scope (for now) |
|----------|------------------------|
| Debug smoke test via `adb` intent extra | User-selectable offline provider in Settings |
| Manual model push to app-private storage | In-app model download / management |
| Logcat-only results (`FudOnDeviceLlm` tag) | Shipping LiteRT native libs in release APKs |
| Tier A + Tier B + Tier C scripted scenarios | Full app integration / Settings provider |
| Daily usage matrix (`tier=daily`) | In-app camera / gallery picker |

---

## Model

| Field | Value |
|-------|--------|
| Model | **Gemma 4 E2B-it** (instruction-tuned, ~2B params) |
| Format | **`.litertlm`** (LiteRT-LM native/mobile bundle, **not** `.task` or web/WASM builds) |
| Quantization | int4 (native/mobile build; filename `gemma-4-E2B-it.litertlm`) |
| Source | [Hugging Face `litert-community`](https://huggingface.co/litert-community); use the **native/mobile** artifact for Gemma 4 E2B-it |
| On-device path | `filesDir/models/gemma-4-E2B-it.litertlm` |
| Package path (debug) | `/data/user/0/app.chompass.debug/files/models/` |

**Important:** Hugging Face listings can include both **web** and **native/mobile** builds of the same model. Web variants fail at load with errors like `TF_LITE_PREFILL_DECODE not found in the model`. Only the native `.litertlm` mobile build works with the Android API.

The model is **not bundled** in the APK (2.6 GB E2B / 3.7 GB E4B). Delivery is scripted: [`scripts/push_ondevice_model.sh`](../scripts/push_ondevice_model.sh) downloads once into a local cache and pushes into app-private storage (see below).

---

## Stack

| Component | Detail |
|-----------|--------|
| Library | `com.google.ai.edge.litertlm:litertlm-android:0.14.0` |
| Gradle scope | `debugImplementation` only; release APKs unaffected |
| Default backend | `Backend.GPU()` (OpenCL via vendor drivers) |
| CPU fallback | `Backend.CPU(numOfThreads = 4)` via intent extra |
| GPU prerequisites | `uses-native-library` for `libOpenCL.so` and `libvndksupport.so` in [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml) (Android 12+ linker namespace) |
| MTP (speculative decoding) | **Off by default:** opt-in via intent; adds a large `verify` subgraph and multi-minute cold GPU init |

### Key source files

| File | Role |
|------|------|
| [`OnDeviceLlmClient.kt`](../android/app/src/debug/java/app/chompass/services/ai/OnDeviceLlmClient.kt) | LiteRT-LM `Engine` wrapper (load, text/vision generate, tool conversations); **debug source set only** |
| [`OnDeviceLlmSmokeTest.kt`](../android/app/src/debug/java/app/chompass/services/OnDeviceLlmSmokeTest.kt) | Tier A/B/C harness, `@Tool` bridge to real `CoachTools` |
| [`OnDeviceLlmDebugLauncher.kt`](../android/app/src/main/java/app/chompass/debug/OnDeviceLlmDebugLauncher.kt) | Release-safe launcher stub; debug runner in `src/debug/` |
| [`MainActivity.kt`](../android/app/src/main/java/app/chompass/MainActivity.kt) | Intent-extra trigger (`run_ondevice_llm_test`); works from cold start and warm relaunch (`onNewIntent`) |

Tool results must be returned as `com.google.gson.JsonElement` from `@Tool` functions; raw `String` JSON gets double-encoded by LiteRT-LM's `ToolManager.execute()` and the model ignores tool output. See `CoachToolsToolSet` in the smoke test file.

---

## Build and install

Build in WSL devenv; install from **Windows PowerShell** (USB adb):

```bash
devenv shell bash -lc 'cd android && ./gradlew :app:assembleDebug'
```

```powershell
adb install --user 0 -r \\wsl$\<distro>\home\<user>\chompass\android\app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
```

Use the **arm64-v8a** split on Pixel 9a (or the universal debug APK).

---

## Model delivery (`scripts/push_ondevice_model.sh`)

The download happens **once** on the dev machine into `android/build/ondevice-models/` (gitignored), SHA-256-verified against the hashes in [`ModelCatalog.kt`](../android/app/src/main/java/app/chompass/services/ondevice/ModelCatalog.kt), then pushed into app-private storage via `run-as`. Reinstalls, reflashes, and package switches reuse the cache — no re-download per test cycle. Model filenames match `ModelCatalog.kt`, so the same pushed file serves both the smoke test and production dispatch.

```bash
./scripts/push_ondevice_model.sh                  # e2b: download (if needed) + push
./scripts/push_ondevice_model.sh e4b              # Gemma 4 E4B-it (~3.7 GB)
./scripts/push_ondevice_model.sh --download-only  # fetch + verify into cache only
./scripts/push_ondevice_model.sh --push-only      # push a cached model (e.g. after a phone reflash)
PACKAGE=app.chompass.debug2 ./scripts/push_ondevice_model.sh   # other debug package
```

The script skips the push when the device already has the identical file (sha256 match). When it **does** push a different model file, it wipes the phone's LiteRT compile caches (`cache/litert`, `cache/litert-mtp`) — a cache compiled against an older artifact makes every generation fail with `llm_litert_compiled_model_executor` INTERNAL errors until cleared. The in-app download clears them too.

Manual fallback (any file, any package; `run-as` needs a debuggable app):

```powershell
adb push gemma-4-E2B-it.litertlm /data/local/tmp/gemma-4-E2B-it.litertlm
adb shell run-as app.chompass.debug mkdir -p files/models
adb shell run-as app.chompass.debug cp /data/local/tmp/gemma-4-E2B-it.litertlm files/models/
adb shell rm /data/local/tmp/gemma-4-E2B-it.litertlm
```

Verify:

```powershell
adb shell run-as app.chompass.debug ls -la files/models/
```

---

## Running the smoke test

### Intent extras (`MainActivity`, debug only)

| Extra | Type | Default | Purpose |
|-------|------|---------|---------|
| `run_ondevice_llm_test` | boolean | - | Run the smoke test |
| `ondevice_llm_backend` | string | `gpu` | `gpu` or `cpu` |
| `ondevice_llm_mtp` | boolean | `false` | Enable multi-token prediction on GPU (longer cold init) |
| `ondevice_llm_model` | string | `gemma-4-E2B-it.litertlm` | Filename under `filesDir/models/` |
| `ondevice_llm_tier` | string | `all` | `all`, `a`, **`b`**, `c`, or **`daily`** |
| `ondevice_llm_prompt` | string | `full` | Tier A prompt: `full`, `compact`, `fewshot_units`, or `twopass` |
| `ondevice_llm_repeat` | int | `1` | Tier A repeat count (1–5) for warm-cache latency comparison |
| `ondevice_llm_clear_cache` | boolean | `false` | Delete LiteRT compile cache before run (cold disk cache) |
| `ondevice_llm_preset` | string | - | **`daily`** → `tier=daily`, `prompt=fewshot_units`, `mtp=true`, `backend=gpu` |
| `run_goal_matrix_test` | boolean | - | Run the goal-calculation matrix (below) against the selected provider/tier |
| `goal_matrix_scenarios` | string | all | Comma-separated scenario filter for the goal matrix |
| `goal_matrix_repeat` | int | `1` | Repeat count per scenario (1–5) for variance sampling |
| `goal_matrix_tier` | string | `auto` | **`safe`** / **`smart`** / **`auto`** (per-dispatch: cloud → SMART, on-device → SAFE); forces the prompt tier regardless of provider |
| `goal_matrix_provider` | string | `on_device` | **`on_device`** / **`gemini`** / **`anthropic`** / **`openai`** / **`openrouter`** — provider forced for the run (cloud legs use the stored KeyStore key; keys are never logged and prefs/key are restored after) |
| `goal_matrix_model` | string | provider default | Model id override (OpenRouter accepts any slug; e.g. `google/gemini-3.6-flash`) |
| `set_openrouter_key` | string | - | Debug-only: store an OpenRouter API key in the encrypted KeyStore (imports the benchmark key; stripped after use, never logged) |

If the app is already foreground, `adb shell am start` prints `Activity not started, intent has been delivered to currently running top-most instance`: **this is normal** (`singleTop`); the test still runs via `onNewIntent`.

### Goal calculation matrix (`run_goal_matrix_test`)

Drives the **real production path** (`FoodAnalysisService.calculateGoals` → selected provider) across a fixed scenario matrix: fresh user, sparse/medium/rich weigh-in data (blips up/down/flat), trends-disagree, implied maintenance below BMR, measured Health Connect TDEE, keto, locked calories, all three weight goals, body-fat BMR, and activity extremes. The harness forces the requested provider/tier for the run and restores your settings (and any swapped KeyStore key) afterwards. Results log under tag `GoalMatrix` with the formula/floor/implied anchors, the model's parsed answer, and the tier/provider/model that actually answered per scenario, so you can see exactly when the model anchors on the empirical maintenance instead of the formula.

```powershell
adb shell am force-stop app.chompass.debug
adb logcat -c
# SAFE tier on the on-device model (v4 regression baseline; default):
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_goal_matrix_test true --ei goal_matrix_repeat 2
# SMART tier on Gemini (raw weigh-in series + intake table; needs a Gemini key):
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_goal_matrix_test true --es goal_matrix_tier smart --es goal_matrix_provider gemini
# SMART tier via OpenRouter (benchmark key; any slug):
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_goal_matrix_test true --es goal_matrix_tier smart --es goal_matrix_provider openrouter --es goal_matrix_model google/gemini-3.6-flash
# Just the reported sparse-weigh-in cases, SMART tier:
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_goal_matrix_test true --es goal_matrix_tier smart --es goal_matrix_provider gemini --es goal_matrix_scenarios sparse_up,sparse_up_active,maintain_sparse_up
adb logcat -s GoalMatrix
```

**Measured 2026-08-22 (Pixel 9a):** SAFE tier on Gemma 4 E2B passed all 19 scenarios (formula anchors for thin/below-BMR/disagree, measured anchor 1950 exact, gain pace applied, keto/locked exact); SMART tier on `google/gemini-3.6-flash` (via OpenRouter) passed all 19 (no BMR anchoring, measured anchor honored). `gemini-3.5-flash-lite` with the SMART prompt FAILED 6/19 the same way the on-device model does (clamped to the BMR floor, ignored the measured anchor), which is why small cloud models (`flash-lite`/`nano`/`haiku`/`-mini`/`/free`) are now auto-classified to the SAFE tier — re-check with `--es goal_matrix_provider gemini --es goal_matrix_model gemini-3.5-flash-lite` (auto tier) and expect tier=SAFE with formula anchors.
**Measured 2026-09-03 (Pixel 9a):** SMART tier on `google/gemini-3.8-flash`
(via the OpenRouter benchmark key) passed all 19 the same way — formula
anchors exact on every sparse/thin case (worst deviation 10 kcal), measured
anchor 2500 honored at 1950 exact, locked returned exactly 2400, keto carbs
27 g, and the only floor hits were the two by-design cases
(rich_low_empirical, sedentary_sparse_up). No BMR clamping, so 3.8 Flash
stays the release default.

Warm-cache generations are ~5–7 s each, so a full 19-scenario run with repeat=2 takes under 5 minutes plus cold engine init (cloud legs add a round-trip each).

**Forced-fallback scenario:** with a cloud provider in `auto` tier, the harness appends a fallback scenario (cloud primary key swapped for an invalid one → on-device fallback answers) and logs `phase=fallback PASS/FAIL` for `provider=ON_DEVICE, tier=SAFE, fallbackFired=true` — proving the on-device leg always receives the SAFE prompt and the result reports the fallback. Skipped (with a log) when the on-device model is not downloaded.

The SAFE/SMART tier split, the confidence gates, and the deterministic enforcement are documented in `docs/CALCULATION_METHODS.md` § AI-RECALC; unit-tested in `GoalPromptGateTest` + `GoalTierSelectionTest`.

### GPU (default)

```powershell
adb shell am force-stop app.chompass.debug
adb logcat -c
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true
adb logcat -s FudOnDeviceLlm
```

**First GPU cold init** compiles OpenCL kernels for several subgraphs; allow **~30–90 seconds** for `engineInit` (heartbeats: `phase=engineInit_waiting`). Warm loads are faster thanks to `cacheDir`.

### CPU (A/B comparison)

```powershell
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_backend cpu
adb logcat -s FudOnDeviceLlm
```

### GPU with MTP (optional)

```powershell
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --ez ondevice_llm_mtp true
```

Expect **multi-minute** first-time GPU init when MTP is enabled (extra `verify` subgraph). See **MTP caveats (GPU)** below before using `--ez ondevice_llm_mtp true`.

### MTP caveats (GPU, Gemma 4 E2B, litertlm-android 0.13+)

MTP (multi-token / speculative decoding) can deliver ~1.6×–2.2× faster decode on GPU, but the current runtime has known rough edges. **Default: MTP off** (`ondevice_llm_mtp=false`).

| Issue | Symptom | Workaround |
|-------|---------|------------|
| **Token budget bug** ([#2816](https://github.com/google-ai-edge/LiteRT-LM/issues/2816)) | Output stops early (~half the requested length): draft + rejected tokens count toward `max_output_tokens` | **Double** `maxOutputToken` when MTP is on. Newer LiteRT-LM Kotlin APIs expose `ConversationConfig.maxOutputToken`; **litertlm-android 0.14.0 does not:** workaround is documentation-only until upgrade. |
| **Streaming stutter** | Token stream pauses briefly | Rejection sampling after bad draft guesses; expected; not fixable client-side. |
| **Hardware locality** | Severe slowdown if drafter and main model split across CPU/GPU | Keep both on **GPU** (LiteRT-LM default when `Backend.GPU()` + MTP). Do not mix CPU backend with MTP. |
| **Shared cache collisions** | “Access is denied” on engine startup (some cross-platform builds) | Point `EngineConfig.cacheDir` at a unique path per engine, or use a `:nocache` sentinel if upstream docs recommend it for your build; increases cold-init time. |

For **Exp 2c** (MTP latency): use **one** `adb` invocation with `mtp=true` and `repeat=2` so cold MTP init happens once and pass=1 measures warm MTP decode. Allow **3–5 minutes** for first `engineInit` with MTP (heartbeats every 15 s). Compare pass=0 vs pass=1 Tier A ms and check for `status=parseFail` (token-budget truncation).

```powershell
adb shell am force-stop app.chompass.debug
adb logcat -c
# Exp 2c: MTP cold init + warm repeat (fewshot keeps unit_options; compact is faster but drops units)
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --ez ondevice_llm_mtp true --es ondevice_llm_tier a --es ondevice_llm_prompt fewshot_units --ei ondevice_llm_repeat 2
adb logcat -s FudOnDeviceLlm
```

Optional A/B; MTP + compact (speed only, expect `unitOptions=0`):

```powershell
adb shell am force-stop app.chompass.debug
adb logcat -c
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --ez ondevice_llm_mtp true --es ondevice_llm_tier a --es ondevice_llm_prompt compact --ei ondevice_llm_repeat 2
adb logcat -s FudOnDeviceLlm
```

MTP compile cache lands under `cache/litert-mtp/` (separate from non-MTP runs).

### Tier B: vision only (food photo smoke test)

Requires `EngineConfig.visionBackend = Backend.GPU()`: without it, `Content.ImageBytes` causes a native SIGSEGV. Bundled JPEG fixtures live in `android/app/src/debug/assets/ondevice_llm/` and are preprocessed with [`AiImageBytes.jpegForUpload`](../android/app/src/main/java/app/chompass/services/ai/AiImageBytes.kt) (1600 px / q78) before inference.

```powershell
adb shell am force-stop app.chompass.debug
adb logcat -c
adb shell am start -n app.chompass.debug/app.chompass.MainActivity `
  --ez run_ondevice_llm_test true --es ondevice_llm_tier b --es ondevice_llm_backend gpu
adb logcat -s FudOnDeviceLlm
```

| Fixture | Source (repo root) | Prompt shape | Purpose |
|---------|-------------------|--------------|---------|
| `food_plate.jpg` | `Chicken-and-Rice-Bowl-…Featured-Image.jpg` | `analyzeFood` | Balanced meal (matches Tier A chicken/rice sample) |
| `fast_food_combo.jpg` | `french_fries_chicken_leg_nuggets_onion_rings_ketchup.avif` | `analyzeFood` | Phone-style fast-food plate |
| `nutrition_label.jpg` | `label.jpg` | `analyzeAuto` | Nutrition facts label OCR |
| `pizza_slices.jpg` | `authentic-phone-photo-pizza-served-….webp` | `analyzeFood` | Pizza photo for `unit_options` / slice counting |

**Regenerate fixtures** after swapping source photos in the repo root:

```bash
uv run --with pillow --with pillow-heif python scripts/prepare_ondevice_llm_fixtures.py
```

Only the converted JPEGs ship in the debug APK. Keep license/attribution for stock photos you download.

After the four fixtures, a **multi-turn stability check** sends `food_plate` then `pizza_slices` in one conversation, validating no 2nd-image crash on GPU vision (LiteRT-LM [#2056](https://github.com/google-ai-edge/LiteRT-LM/issues/2056)).

### Daily usage matrix (`tier=daily`)

Single scripted run: Tier A text (3 samples, **`fewshot_units`**), Tier B photo (4 fixtures), Tier C Coach (2 lightweight scenarios). Emits `phase=daily_summary` with per-tier ms totals.

**Capture script** (WSL; set `ADB_BIN` to Windows adb if needed):

```bash
scripts/capture_ondevice_llm_daily.sh 4   # daily-driver preset (run 4)
```

| Run | Flags | What it measures |
|-----|-------|------------------|
| 1 | `clear_cache=true`, `mtp=false`, `tier=daily`, `prompt=fewshot_units` | Cold disk cache, no MTP |
| 2 | `clear_cache=false`, `mtp=false`, `tier=daily`, `prompt=fewshot_units` | Warm engine + warm disk cache |
| 3 | `clear_cache=true`, `mtp=true`, `tier=daily`, `prompt=fewshot_units` | Cold init with MTP verify subgraph |
| 4 | `preset=daily`, `clear_cache=false` | **Daily-driver candidate:** warm cache + MTP + full matrix |

PowerShell (run 4; daily driver):

```powershell
adb shell am force-stop app.chompass.debug
adb logcat -c
adb shell am start -n app.chompass.debug/app.chompass.MainActivity `
  --ez run_ondevice_llm_test true --es ondevice_llm_preset daily --ez ondevice_llm_clear_cache false
adb logcat -s FudOnDeviceLlm
```

**Decision criteria (Pixel 9a, 2026-07-14 PM):**

| Check | Pass if… | Result |
|-------|----------|--------|
| Tier A | jsonOk ≥ 3/3; pizza text `unitOptions` ≥ 1 | **Pass:** 3/3 ok; pizza sample `unitOptions=2` (run 4) |
| Tier B | jsonOk ≥ 3/4; pizza photo `unitOptions` ≥ 1; multi-turn `status=ok` | **Pass:** 4/4 ok; pizza `unitOptions=1`; multi-turn ok (both runs) |
| Tier C daily | `single_tool` + `ambiguous` complete without timeout | **Pass:** ~9.7 s + ~8.5 s (run 4) |
| MTP | Tier A+B ≥25% faster with MTP vs no-MTP baseline; no quality regression | **Pass:** run 1 vs run 4: Tier A **−32%**, Tier B **−32%**, total **−30%** (see comparison below). JSON/units intact both runs. Run 4 also had warm cache (confounded); MTP benefit confirmed from prior Tier A-only Exp 2c too. |

**Daily matrix results (Pixel 9a, real photo fixtures, 2026-07-14 PM):**

| Run | engineInit_ms | tierA_ms | tierB_ms | tierC_ms | total_ms | Notes |
|-----|---------------|----------|----------|----------|----------|-------|
| 1 cold, no MTP | **34 745** | **88 788** | **102 294** | **20 029** | **211 111** | `clearCache=true`, `cache/`. Tier B ms = 4 fixtures only. Wall ~3.5 min incl. init. |
| 2 warm, no MTP | - | - | - | - | - | Not run |
| 3 cold + MTP | - | - | - | - | - | Not run |
| 4 preset daily | **36 360** | **60 207** | **69 256** | **18 194** | **147 657** | `mtp=true`, warm `cache/litert-mtp`, `clearCache=false`. Wall ~2.5 min incl. init. |

**Run 1 vs run 4 comparison** (not isolated A/B; run 4 combines MTP **and** warm compile cache):

| Metric | Run 1 (cold, no MTP) | Run 4 (warm, MTP) | Delta |
|--------|----------------------|-------------------|-------|
| `engineInit_ms` | 34 745 | 36 360 | +5% (comparable) |
| `tierA_ms` | 88 788 | 60 207 | **−32%** |
| `tierB_ms` | 102 294 | 69 256 | **−32%** |
| `tierC_ms` | 20 029 | 18 194 | −9% |
| `total_ms` | 211 111 | 147 657 | **−30%** |

**Run 1 breakdown:**

| Tier | Sample / fixture | ms | Quality |
|------|------------------|-----|---------|
| A | pizza + coke (text) | 36 286 | ok, `unitOptions=2` |
| A | oatmeal (text) | 24 592 | ok, `unitOptions=1` |
| A | chicken/rice (text) | 27 910 | ok, `unitOptions=2` |
| B | `food_plate` | 27 073 | ok, `unitOptions=1` |
| B | `fast_food_combo` | 27 317 | ok, `unitOptions=1` |
| B | `nutrition_label` | 21 704 | ok, `unitOptions=1` |
| B | `pizza_slices` | 26 200 | ok, `unitOptions=1` |
| B | multi-turn (2 images) | 53 976 | ok (not in `tierB_ms` sum) |
| C | `single_tool` | 10 717 | grounded: 4 foods listed |
| C | `ambiguous` | 9 312 | `get_data_summary`, reasonable follow-up |

**Run 4 breakdown:**

| Tier | Sample / fixture | ms | Quality |
|------|------------------|-----|---------|
| A | pizza + coke (text) | 25 909 | ok, `unitOptions=2` |
| A | oatmeal (text) | 15 878 | ok, `unitOptions=1` |
| A | chicken/rice (text) | 18 420 | ok, `unitOptions=2` |
| B | `food_plate` | 18 516 | ok, `unitOptions=1` |
| B | `fast_food_combo` | 18 684 | ok, `unitOptions=1` |
| B | `nutrition_label` | 14 862 | ok, `unitOptions=1` |
| B | `pizza_slices` | 17 194 | ok, `unitOptions=1` |
| B | multi-turn (2 images) | 38 932 | ok (not in `tierB_ms` sum) |
| C | `single_tool` | 9 667 | grounded: 4 foods listed |
| C | `ambiguous` | 8 527 | `get_data_summary`, reasonable follow-up |

**Tier B standalone (`tier=b`, no MTP, same evening):**

| Phase | ms | Notes |
|-------|-----|-------|
| `engineInit` | 28 697 | GPU + vision, cold cache |
| `food_plate` | 33 574 | ok |
| `fast_food_combo` | 26 346 | ok |
| `nutrition_label` | 21 201 | ok |
| `pizza_slices` | 25 637 | ok |
| multi-turn | 52 891 | 2 turns, no crash |

**Daily-driver recommendation (confirmed):** `ondevice_llm_preset=daily` → `gpu` + `fewshot_units` + **`mtp=true`**. After warm compile cache, expect **~16–26 s** text food log, **~15–27 s** photo food log (no MTP) or **~15–19 s** (MTP), **~9–10 s** simple Coach. First launch after `clear_cache` or cold install: **~35 s** `engineInit` + full matrix **~3.5 min** (no MTP) vs **~2.5 min** (MTP + warm cache). Keep process alive or accept one-time cold-start penalty.

### Experiment examples

```powershell
# Exp 1a: few-shot unit_options (Tier A only)
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt fewshot_units

# Exp 1b: two-pass unit inference (Tier A only)
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt twopass

# Exp 2a: compact prompt latency
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt compact

# Exp 2b: warm repeat (second pass uses GPU cache)
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt compact --ei ondevice_llm_repeat 2

# Exp 2c: MTP cold init + warm repeat (allow 3–5 min for first engineInit)
adb shell am force-stop app.chompass.debug
adb logcat -c
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --ez ondevice_llm_mtp true --es ondevice_llm_tier a --es ondevice_llm_prompt fewshot_units --ei ondevice_llm_repeat 2
adb logcat -s FudOnDeviceLlm

# Exp 3: FunctionGemma: SKIPPED (no suitable artifact for this app: see experiment log)

# Exp 4: Gemma 4 E4B full run (optional quality comparison)
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_model gemma-4-E4B-it.litertlm
```

### Log format

All lines use tag **`FudOnDeviceLlm`** with `op=ondevice_llm phase=... key=value` bodies, e.g.:

```
op=ondevice_llm phase=tierA backend=gpu i=0 ms=25683 status=ok name=... calories=1250 unitOptions=0 prompt=full
op=ondevice_llm phase=tierA_prompt backend=gpu pass=0 i=0 promptChars=2847
op=ondevice_llm phase=tierA_twopass backend=gpu pass=0 i=0 ms=12000 unitOptions=2
op=ondevice_llm phase=tierC scenario=single_tool ms=10091 response=...
op=ondevice_llm phase=toolCall tool=get_food_entries args=... ms=12 result=...
op=ondevice_llm phase=tierB backend=gpu fixture=food_plate ms=... jsonOk=true unitOptions=1 rawBytes=... uploadBytes=...
op=ondevice_llm phase=tierB_multiturn backend=gpu ms=... turns=2 status=ok
op=ondevice_llm phase=daily_summary tierA_ms=... tierB_ms=... tierC_ms=... total_ms=... mtp=true engineInit_ms=...
op=ondevice_llm phase=done backend=gpu tier=DAILY prompt=fewshot_units
```

Progress heartbeats during long blocking calls: `phase=engineInit_waiting`, `phase=tierA_waiting`, `phase=tierB_waiting`, `phase=tierC_waiting`.

For GPU/OpenCL framework logs (optional):

```powershell
adb logcat '*:W' | Select-String -Pattern 'OpenCL|LITERT|litert|GpuEnvironment'
```

In **zsh** (WSL), quote the filter: `adb logcat '*:W'`.

---

## Test scenarios

### Tier A: food text → structured JSON

Mirrors production `FoodAnalysisService.analyzeText` prompt shape. Three fixed samples (pizza+coke, oatmeal, chicken/rice/broccoli). Output parsed with `FoodJsonParser.parseFood`.

### Tier B: food photo → structured JSON

Mirrors production `FoodAnalysisService.analyzeFood` / `analyzeAuto` prompt shapes. Real food/label JPEGs under `src/debug/assets/ondevice_llm/` (generated by [`scripts/prepare_ondevice_llm_fixtures.py`](../scripts/prepare_ondevice_llm_fixtures.py) from photos in the repo root). Images downscaled via `AiImageBytes.jpegForUpload` before `Content.ImageBytes` is sent. `visionBackend=GPU` is mandatory.

### Tier C: Coach tool-calling

Uses real `CoachTools` against live DataStore data via LiteRT-LM native `@Tool` function calling:

| Scenario | Prompt gist | What we check |
|----------|-------------|---------------|
| `single_tool` | "What did I eat yesterday?" | Correct tool + **grounded answer** from returned JSON |
| `ambiguous` | "How am I doing?" | Reasonable tool choice / summary |
| `multi_round_chain` | Last-week calorie average + log water | Multi-tool chain + grounded numbers |
| `malformed_recovery` | Data summary + weight history | Behavior when one tool result is deliberately truncated |
| `six_round_chain` | Summary + weight + calories + food + propose water + propose weight | Long multi-tool chain stress test |

---

## Validated results (Pixel 9a, GrapheneOS, 2026-07-14)

**Production dispatch re-validation (2026-08-22, Pixel 9a / GrapheneOS):** the
production ON_DEVICE path (`FoodAnalysisService.analyzeText` → `OnDeviceLlmGateway`
→ engine, `cache/litert`, no MTP) was exercised on-device for the first time,
with the current catalog artifact (`gemma-4-E2B-it.litertlm`, HF repo rebuilt
2026-08-07) on litertlm 0.14.0: cold engine init 34.5 s, full analyze+parse+save
63.2 s, entry persisted and shown on Home (name localized to the phone locale).
Also re-validated the daily preset against the current artifact: engine init
36.8 s, Tier A 3/3, Tier B 4/4, Tier C 2/2, total 139.8 s (matches the 2026-07-14
run 4 baseline). Caveat: a LiteRT compile cache built against an older model
artifact breaks every generation with `llm_litert_compiled_model_executor`
INTERNAL errors until cleared — the push script and the in-app download worker
both wipe `cache/litert` + `cache/litert-mtp` when a new model file lands.

Library: `litertlm-android` **0.14.0**, model: **Gemma 4 E2B-it** int4 `.litertlm`, backend: **GPU**, **MTP off**.

### Latency

| Phase | GPU | CPU (same session, earlier run) |
|-------|-----|----------------------------------|
| Cold `engineInit` | ~30 s | ~11 s |
| Tier A sample 0 | ~26 s | ~71 s |
| Tier A sample 1 | ~23 s | - |
| Tier A sample 2 | ~23 s | - |
| Tier C `single_tool` | ~10 s | - |
| Tier C `ambiguous` | ~9 s | - |
| Tier C `multi_round_chain` | ~11 s | - |
| Tier C `malformed_recovery` | ~15 s | - |
| **Full run** | **`phase=done`** ~2.5 min wall | Tier A ~71 s/sample |

GPU is roughly **3× faster** than CPU on Tier A for this harness. Target of 2–5 s per Tier A call was **not** reached; likely due to the long JSON-schema prompt, large output token count, and OpenCL top-k sampler falling back to CPU sampling in the AAR.

### Quality

| Area | Result |
|------|--------|
| Tier A JSON parse | **3/3 ok:** valid names, calories, macros |
| Tier A `unit_options` | **0/3** with `full` prompt; fixed with **`fewshot_units`** (see experiment log) |
| Tier C tool selection | **Good:** correct tools and date ranges |
| Tier C result grounding | **Good** after `JsonElement` fix; e.g. `single_tool` listed all four foods from `get_food_entries`; `multi_round_chain` computed ~1750 kcal average from real data |
| Tier C malformed recovery | **Reasonable:** answered from summary + partial weight data despite truncated JSON |
| Tier B vision (real photos) | **4/4 json ok**, all `unitOptions` ≥ 1; multi-turn stable on GPU vision |
| Daily matrix run 4 (MTP) | **Pass:** `daily_summary` total ~148 s incl. init; quality ok across A/B/C |

---

## Known issues and caveats

1. **`unit_options` (Tier A, `full` prompt):** Baseline `full` prompt still returns `unit_options: []` (0/3). **`fewshot_units` prompt fixes this** (3/3 with units on Pixel 9a, 2026-07-14 evening run). Production would need inline few-shot examples or a two-pass `inferServingUnitOptions` call.
2. **Tier A latency:** `compact` (~653 chars): **~7 s** (samples 1–2) / **~14 s** (multi-item sample 0). `fewshot_units` (~1494 chars): **~25–30 s** but keeps units. Baseline `full`: ~23–26 s, no units. Target of 2–5 s not reached on complex items.
3. **GPU cold init:** First OpenCL compile takes 30–90 s (`mtp=false`); **~29–30 s** observed when compile cache is warm (Exp 1a/2c). **Several minutes** possible on a truly cold MTP first run (extra `verify` subgraph); Exp 2c MTP init was ~30 s after prior GPU sessions same evening.
4. **GPU MTP (speculative decoding):** On Pixel 9a with `fewshot_units`, MTP delivers **~1.6× faster Tier A** (~15–17 s vs ~25–30 s) with **no JSON truncation** in Exp 2c (all `status=ok`, unitOptions preserved). Token-budget bug [#2816](https://github.com/google-ai-edge/LiteRT-LM/issues/2816) may still bite on longer outputs; `litertlm-android` **0.14.0** has no `ConversationConfig.maxOutputToken`. Default **MTP off** for smoke tests; enable when benchmarking decode speed.
5. **LiteRT-LM maturity:** Library is beta; tool-calling and GPU paths have active upstream issues. Pin version deliberately when upgrading.
6. **GrapheneOS:** No Play Services / AICore required; vendor GPU drivers + manifest `uses-native-library` entries are sufficient for OpenCL on Pixel 9a.
7. **Not production:** No UI, no provider toggle, no model management; release APKs do not include LiteRT native libs.
8. **FunctionGemma (skipped for Chompass):** HF [functiongemma-270m-ft-mobile-actions](https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions) is not a fit: `*_Google_Tensor_G5.litertlm` fails on OpenCL GPU (`Input tensor not found`); `mobile_actions_q8_ekv1024.litertlm` is fine-tuned for Google’s **Mobile Actions** demo intents, not Chompass Coach tools. Tier C stays on **Gemma 4 E2B-it**.
9. **Vision backend:** `visionBackend` must be **GPU** for image input. CPU vision crashes on the 2nd image turn ([#2056](https://github.com/google-ai-edge/LiteRT-LM/issues/2056)). GPU+GPU vision OOM-kills the process on E4B (observed on Pixel 9a, real-world food photo). Production now handles this: `OnDeviceCapability.preferredBackend` (`android/app/src/main/java/app/chompass/services/ondevice/OnDeviceCapability.kt`) forces `backend=cpu` (text) + GPU vision for E4B+vision specifically, and `OnDeviceCapability.hasEnoughAvailableMemoryForVision` runs a preflight `ActivityManager.MemoryInfo.availMem` check before every vision call, throwing a catchable `AiError.OnDeviceLowMemory` instead of risking a silent OS kill. Not yet device-validated beyond that one Pixel 9a repro.
10. **Vision + MTP:** **Validated on Pixel 9a (2026-07-14 PM):** run 4 (`preset=daily`, MTP on): Tier B 4/4 json ok with `unitOptions`; multi-turn ok; Tier B fixture median **~18 s** vs **~26 s** without MTP (tier=b standalone). No JSON truncation observed. If cold MTP init (~36 s) is unacceptable on first app open, use MTP only after warm cache or Tier-A-only MTP in production.

---

## Upgrade / experiment notes

- **Version pin:** [`android/gradle/libs.versions.toml`](../android/gradle/libs.versions.toml) → `litertlm = "0.14.0"`.
- **Other models (experiments closed):**
  - **Gemma 4 E4B-it:** optional quality rung; not run (E2B sufficient for Tier A/C smoke test)
  - **FunctionGemma-270m:** **skipped:** no OpenCL-compatible `.litertlm` with Coach-relevant fine-tuning (Tensor G5 build needs NPU; mobile-actions build is a different task/domain)
- **Production path (future):** Would need `ApiFormat.ON_DEVICE`, Settings UX, model file checks, and wiring `FoodAnalysisService` / `ChatService` dispatch, separate from this smoke test.

---

## Experiment log

Record results from `adb logcat -s FudOnDeviceLlm` after each run. Seed test data first if Tier C needs logged entries:

```powershell
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_test_data true
```

| Experiment | Command flags | Sample 0 `unitOptions` | Tier A ms (GPU) | Tier C grounding | Notes |
|------------|---------------|------------------------|-----------------|------------------|-------|
| **Baseline** (2026-07-14 AM) | default (`full`, E2B) | 0/3 | ~23–26 s | 4/4 good post-JsonElement fix | See validated results above |
| **Exp 1a** `fewshot_units` (2026-07-14 PM) | `tier=a prompt=fewshot_units` | **2** (pizza+coke) | ~30 / ~25 / ~28 s (samples 0–2) | n/a | **PASS:** 3/3 parse ok; unitOptions 2/1/2; promptChars≈1494; engineInit ~29 s |
| **Exp 1b** `twopass` | - | - | - | **Skip:** Exp 1a + MTP covers units |
| **Exp 2a** `compact` (2026-07-14 PM) | `tier=a prompt=compact` | 0/3 | **~14 / ~7 / ~7 s** (samples 0–2) | n/a | promptChars≈653; ~2× faster than `fewshot_units`; loses `unit_options`; engineInit ~29 s |
| **Exp 2b** warm repeat (2026-07-14 PM) | `tier=a prompt=compact repeat=2` | 0/3 | pass0: ~13/7/7 s; pass1: ~12/7/7 s | n/a | ~4% faster pass1 on sample 0 only; samples 1–2 already warm; single engineInit ~26 s |
| **Exp 2c** MTP + fewshot (2026-07-14 PM) | `mtp=true tier=a prompt=fewshot_units repeat=2` | **2** (all passes) | pass0: **~18 / ~15 / ~17 s**; pass1: ~17 / ~15 / ~17 s | n/a | **PASS:** ~**1.6×** vs non-MTP fewshot; 6/6 parse ok; unitOptions 2/1/2; engineInit ~30 s; no truncation |
| **Exp 3** FunctionGemma | - | - | - | **Skip:** Tensor G5: GPU init fail; `mobile_actions`: wrong fine-tune (Mobile Actions demo, not Coach) |
| **Exp 4** E4B | - | - | - | Skipped in this smoke-test harness (E2B adequate for the harness). E4B *is* offered in production (`ModelCatalog.E4B`) and was later found to OOM-kill the app on Pixel 9a when combined with photo analysis; see note 9 above for the CPU/GPU-split + preflight-memory-check mitigation now in place. Still not run through this harness. |
| **Exp 5** `six_round_chain` + Tier C (2026-07-14 PM) | `tier=c` (E2B, no MTP) | n/a | n/a | **4/5 good** | See Tier C breakdown below; `six_round_chain` partial; wrong counts, missed propose_log_* |
| **Tier B** vision (2026-07-14 PM) | `tier=b`, real photo fixtures | n/a | n/a (4× ~21–34 s) | n/a | **PASS:** 4/4 json ok; multi-turn ok; no MTP |
| **Daily run 1** | `tier=daily`, cold cache, no MTP | 2/1/2 (text) | 4/4 (photo) | 2/2 Coach | tierA 89 s / tierB 102 s / tierC 20 s / total 211 s | **PASS:** no-MTP baseline |
| **Daily run 4** | `preset=daily`, warm MTP cache | 2/1/2 (text) | 4/4 (photo) | 2/2 Coach | tierA 60 s / tierB 69 s / tierC 18 s / total 148 s | **PASS:** recommended daily-driver preset |

---

## Decision gate (production integration)

Fill in after experiment log runs on Pixel 9a:

| Use case | Go if… | Hybrid if… | No-go if… |
|----------|--------|------------|-----------|
| **Tier A** text food log | `fewshot_units` + MTP ≤ ~20 s GPU **and** JSON 3/3 with units | compact ~7 s without units; fewshot ~25–30 s without MTP | parse failures or >45 s after best prompt combo |
| **Tier B** photo food log | MTP ~15–19 s/fixture **and** JSON 4/4 with units | no MTP ~21–34 s; disclose latency | parse failures or multi-turn crash |
| **Tier C** Coach | E2B passes 4/5 scenarios (Exp 5) | Tier C **cloud default**; optional on-device E2B for simple offline queries | tool results ignored, or long chains consistently fail |
| **`unit_options`** | Exp 1a `fewshot_units` fixes all samples (2026-07-14) | two-pass if few-shot too token-heavy for production | both fewshot and twopass fail on pizza sample |

**Final assessment (experiments complete, 2026-07-14):**

| Area | Verdict | Production note |
|------|---------|-----------------|
| **Tier A** | **Go (hybrid UX)** | `fewshot_units` + MTP ~16–26 s GPU; units ok. Disclose latency in Settings. |
| **Tier B** | **Go (hybrid UX)** | `analyzeFood`/`analyzeAuto` prompts + MTP ~15–19 s/photo when warm; 4/4 json ok on real fixtures. |
| **Tier C** | **Hybrid** | E2B 4/5 on harness; cloud Coach for compound requests. On-device E2B optional for offline simple queries (~9 s). |
| **`unit_options`** | **Go** | Add few-shot block to production `analyzeText` prompt (mirror harness). |
| **MTP** | **Go for Tier A + Tier B** | ~30% faster vision with MTP when warm; enable after first init or keep process warm |
| **Model** | **Gemma 4 E2B-it** | Single on-device model for text + vision + simple Coach offline |

**Next milestone (separate from experiments):** production wiring: `ApiFormat.ON_DEVICE`, Settings provider + model-file check, `FoodAnalysisService` dispatch (Tier A first), optional Coach branch later.

**Tier C scenario results (Exp 5, E2B GPU, no MTP):**

| Scenario | ms | Tool calls | Grounding | Verdict |
|----------|-----|------------|-----------|---------|
| `single_tool` | ~10.7 s | `get_food_entries` | Listed all 4 foods + kcal | **Pass** |
| `ambiguous` | ~9.2 s | `get_data_summary` | Cited real counts; reasonable follow-up | **Pass** |
| `multi_round_chain` | ~10.2 s | `get_calorie_totals` + `propose_log_water` | ~1750 kcal avg + water proposal | **Pass** |
| `malformed_recovery` | ~15.0 s | summary + truncated weight JSON | Answered from summary + partial weights | **Pass** (intentional corrupt) |
| `six_round_chain` | ~31.0 s | 4 tools (summary, weight, calories, food) | Summary partly wrong; no propose_log calls | **Partial** |

**Suggested production prompt strategy:** `fewshot_units` + **MTP** for Tier A and Tier B on-device; disclose ~15–35 s latency in Settings; Tier C stays **cloud default**, on-device E2B for offline simple Coach only.

---

## What next

**Tier A/C/B experiments + daily matrix: complete** (2026-07-14).

| Step | Status |
|------|--------|
| Exp 1a, 2a/2b/2c, 5 | **Done** |
| Exp 1b, 3, 4 | **Skipped** |
| **Tier B vision harness** | **Done:** 4/4 fixtures, multi-turn OK |
| **Daily matrix runs 1 + 4** | **Done:** baseline + daily-driver preset PASS |
| **Production integration** | **Next:** Tier A + B on-device provider behind Settings; Tier C stays cloud-first |

See **Final assessment** under Decision gate above. Run Tier B + daily matrix before production wiring; re-test on litertlm upgrades.

---

## Related docs

- [`DEVELOPMENT.md`](DEVELOPMENT.md); devenv build workflow
- [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md); formula register (unrelated to LLM)
