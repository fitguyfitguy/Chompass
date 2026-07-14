# On-device LLM smoke test (Gemma 4 E2B-it)

Debug-only proof of concept for running **Gemma 4 E2B-it** locally via [Google AI Edge LiteRT-LM](https://developers.google.com/edge/litert-lm/android). Validates food-analysis JSON extraction (Tier A) and Coach tool-calling (Tier C) on real hardware before any production integration.

**Status (2026-07-14):** Smoke test **passes end-to-end** on **Pixel 9a / GrapheneOS** with GPU backend (`litertlm-android` **0.14.0**). Not wired into Settings, `AIProvider`, `FoodAnalysisService`, or `ChatService` — debug builds only.

---

## Scope

| In scope | Out of scope (for now) |
|----------|------------------------|
| Debug smoke test via `adb` intent extra | User-selectable offline provider in Settings |
| Manual model push to app-private storage | In-app model download / management |
| Logcat-only results (`FudOnDeviceLlm` tag) | Shipping LiteRT native libs in release APKs |
| Tier A + Tier C scripted scenarios | Tier B or full app integration |

---

## Model

| Field | Value |
|-------|--------|
| Model | **Gemma 4 E2B-it** (instruction-tuned, ~2B params) |
| Format | **`.litertlm`** (LiteRT-LM native/mobile bundle — **not** `.task`, not web/WASM builds) |
| Quantization | int4 (filename convention: `gemma-e2b-int4.litertlm`) |
| Source | [Hugging Face `litert-community`](https://huggingface.co/litert-community) — use the **native/mobile** artifact for Gemma 4 E2B-it |
| On-device path | `filesDir/models/gemma-e2b-int4.litertlm` |
| Package path (debug) | `/data/user/0/org.codeberg.fitguy.nofud.debug/files/models/` |

**Important:** Hugging Face listings can include both **web** and **native/mobile** builds of the same model. Web variants fail at load with errors like `TF_LITE_PREFILL_DECODE not found in the model`. Only the native `.litertlm` mobile build works with the Android API.

The model is **not bundled** in the APK (~1–2 GB). Delivery is manual via `adb` (see below).

---

## Stack

| Component | Detail |
|-----------|--------|
| Library | `com.google.ai.edge.litertlm:litertlm-android:0.14.0` |
| Gradle scope | `debugImplementation` only — release APKs unaffected |
| Default backend | `Backend.GPU()` (OpenCL via vendor drivers) |
| CPU fallback | `Backend.CPU(numOfThreads = 4)` via intent extra |
| GPU prerequisites | `uses-native-library` for `libOpenCL.so` and `libvndksupport.so` in [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml) (Android 12+ linker namespace) |
| MTP (speculative decoding) | **Off by default** — opt-in via intent; adds a large `verify` subgraph and multi-minute cold GPU init |

### Key source files

| File | Role |
|------|------|
| [`OnDeviceLlmClient.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/ai/OnDeviceLlmClient.kt) | LiteRT-LM `Engine` wrapper (load, generate, tool conversations) |
| [`OnDeviceLlmSmokeTest.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/services/OnDeviceLlmSmokeTest.kt) | Tier A/C harness, `@Tool` bridge to real `CoachTools` |
| [`MainActivity.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/MainActivity.kt) | Intent-extra trigger (`run_ondevice_llm_test`); works from cold start and warm relaunch (`onNewIntent`) |

Tool results must be returned as `com.google.gson.JsonElement` from `@Tool` functions — raw `String` JSON gets double-encoded by LiteRT-LM's `ToolManager.execute()` and the model ignores tool output. See `CoachToolsToolSet` in the smoke test file.

---

## Build and install

Build in WSL devenv; install from **Windows PowerShell** (USB adb):

```bash
devenv shell bash -lc 'cd android && ./gradlew :app:assemblePlayDebug'
```

```powershell
adb install -r \\wsl$\archlinux\home\archliNix\NoFUD\android\app\build\outputs\apk\play\debug\app-play-arm64-v8a-debug.apk
```

Use the **arm64-v8a** split on Pixel 9a (or the universal debug APK).

---

## Model delivery (`adb push`)

From Windows PowerShell (adjust WSL distro name if needed):

```powershell
# 1. Push to a world-readable staging path
adb push gemma-e2b-int4.litertlm /data/local/tmp/gemma-e2b-int4.litertlm

# 2. Copy into app-private storage (debuggable app, no root)
adb shell run-as org.codeberg.fitguy.nofud.debug mkdir -p files/models
adb shell run-as org.codeberg.fitguy.nofud.debug cp /data/local/tmp/gemma-e2b-int4.litertlm files/models/
adb shell rm /data/local/tmp/gemma-e2b-int4.litertlm
```

Verify:

```powershell
adb shell run-as org.codeberg.fitguy.nofud.debug ls -la files/models/
```

---

## Running the smoke test

### Intent extras (`MainActivity`, debug only)

| Extra | Type | Default | Purpose |
|-------|------|---------|---------|
| `run_ondevice_llm_test` | boolean | — | Run the smoke test |
| `ondevice_llm_backend` | string | `gpu` | `gpu` or `cpu` |
| `ondevice_llm_mtp` | boolean | `false` | Enable multi-token prediction on GPU (longer cold init) |

If the app is already foreground, `adb shell am start` prints `Activity not started, intent has been delivered to currently running top-most instance` — **this is normal** (`singleTop`); the test still runs via `onNewIntent`.

### GPU (default)

```powershell
adb shell am force-stop org.codeberg.fitguy.nofud.debug
adb logcat -c
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true
adb logcat -s FudOnDeviceLlm
```

**First GPU cold init** compiles OpenCL kernels for several subgraphs — allow **~30–90 seconds** for `engineInit` (heartbeats: `phase=engineInit_waiting`). Warm loads are faster thanks to `cacheDir`.

### CPU (A/B comparison)

```powershell
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_backend cpu
adb logcat -s FudOnDeviceLlm
```

### GPU with MTP (optional)

```powershell
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --ez ondevice_llm_mtp true
```

Expect **multi-minute** first-time GPU init when MTP is enabled (extra `verify` subgraph).

### Log format

All lines use tag **`FudOnDeviceLlm`** with `op=ondevice_llm phase=... key=value` bodies, e.g.:

```
op=ondevice_llm phase=tierA backend=gpu i=0 ms=25683 status=ok name=... calories=1250
op=ondevice_llm phase=tierC scenario=single_tool ms=10091 response=...
op=ondevice_llm phase=done backend=gpu
```

Progress heartbeats during long blocking calls: `phase=engineInit_waiting`, `phase=tierA_waiting`, `phase=tierC_waiting`.

For GPU/OpenCL framework logs (optional):

```powershell
adb logcat '*:W' | Select-String -Pattern 'OpenCL|LITERT|litert|GpuEnvironment'
```

In **zsh** (WSL), quote the filter: `adb logcat '*:W'`.

---

## Test scenarios

### Tier A — food text → structured JSON

Mirrors production `FoodAnalysisService.analyzeText` prompt shape. Three fixed samples (pizza+coke, oatmeal, chicken/rice/broccoli). Output parsed with `FoodJsonParser.parseFood`.

### Tier C — Coach tool-calling

Uses real `CoachTools` against live DataStore data via LiteRT-LM native `@Tool` function calling:

| Scenario | Prompt gist | What we check |
|----------|-------------|---------------|
| `single_tool` | "What did I eat yesterday?" | Correct tool + **grounded answer** from returned JSON |
| `ambiguous` | "How am I doing?" | Reasonable tool choice / summary |
| `multi_round_chain` | Last-week calorie average + log water | Multi-tool chain + grounded numbers |
| `malformed_recovery` | Data summary + weight history | Behavior when one tool result is deliberately truncated |

---

## Validated results (Pixel 9a, GrapheneOS, 2026-07-14)

Library: `litertlm-android` **0.14.0**, model: **Gemma 4 E2B-it** int4 `.litertlm`, backend: **GPU**, **MTP off**.

### Latency

| Phase | GPU | CPU (same session, earlier run) |
|-------|-----|----------------------------------|
| Cold `engineInit` | ~30 s | ~11 s |
| Tier A sample 0 | ~26 s | ~71 s |
| Tier A sample 1 | ~23 s | — |
| Tier A sample 2 | ~23 s | — |
| Tier C `single_tool` | ~10 s | — |
| Tier C `ambiguous` | ~9 s | — |
| Tier C `multi_round_chain` | ~11 s | — |
| Tier C `malformed_recovery` | ~15 s | — |
| **Full run** | **`phase=done`** ~2.5 min wall | Tier A ~71 s/sample |

GPU is roughly **3× faster** than CPU on Tier A for this harness. Target of 2–5 s per Tier A call was **not** reached — likely due to the long JSON-schema prompt, large output token count, and OpenCL top-k sampler falling back to CPU sampling in the AAR.

### Quality

| Area | Result |
|------|--------|
| Tier A JSON parse | **3/3 ok** — valid names, calories, macros |
| Tier A `unit_options` | **0/3** — model omits non-gram units (pizza slices, etc.); same quirk as earlier WSL2/Ollama runs — likely model/prompt, not integration |
| Tier C tool selection | **Good** — correct tools and date ranges |
| Tier C result grounding | **Good** after `JsonElement` fix — e.g. `single_tool` listed all four foods from `get_food_entries`; `multi_round_chain` computed ~1750 kcal average from real data |
| Tier C malformed recovery | **Reasonable** — answered from summary + partial weight data despite truncated JSON |

---

## Known issues and caveats

1. **`unit_options` gap (Tier A)** — Obvious non-gram units (pizza slices) still return `unit_options: []` on-device and in prior cloud/Ollama tests.
2. **Tier A latency** — ~23–26 s on GPU vs hoped 2–5 s; prompt is token-heavy; sampler may use CPU fallback (`libLiteRtTopKOpenClSampler.so` issues in some AAR versions).
3. **GPU cold init** — First OpenCL compile takes 30–90 s (`mtp=false`); **several minutes** with `ondevice_llm_mtp=true`.
4. **LiteRT-LM maturity** — Library is beta; tool-calling and GPU paths have active upstream issues. Pin version deliberately when upgrading.
5. **GrapheneOS** — No Play Services / AICore required; vendor GPU drivers + manifest `uses-native-library` entries are sufficient for OpenCL on Pixel 9a.
6. **Not production** — No UI, no provider toggle, no model management; release APKs do not include LiteRT native libs.

---

## Upgrade / experiment notes

- **Version pin:** [`android/gradle/libs.versions.toml`](../android/gradle/libs.versions.toml) → `litertlm = "0.14.0"`.
- **Other models to try:** Gemma 4 E4B-it (larger/slower), [FunctionGemma-270m-it](https://huggingface.co/litert-community) (Tier C tool-calling specialist, Tier A unlikely).
- **Production path (future):** Would need `ApiFormat.ON_DEVICE`, Settings UX, model file checks, and wiring `FoodAnalysisService` / `ChatService` dispatch — separate from this smoke test.

---

## Related docs

- [`AGENTS.md`](../AGENTS.md) — debug intent extras, WSL/Windows adb split
- [`DEVELOPMENT.md`](../DEVELOPMENT.md) — devenv build workflow
- [`CALCULATION_METHODS.md`](../CALCULATION_METHODS.md) — unrelated to LLM; listed for agent navigation only
