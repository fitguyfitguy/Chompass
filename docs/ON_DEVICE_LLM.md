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
| [`OnDeviceLlmClient.kt`](../android/app/src/debug/java/org/codeberg/fitguy/nofud/services/ai/OnDeviceLlmClient.kt) | LiteRT-LM `Engine` wrapper (load, generate, tool conversations) — **debug source set only** |
| [`OnDeviceLlmSmokeTest.kt`](../android/app/src/debug/java/org/codeberg/fitguy/nofud/services/OnDeviceLlmSmokeTest.kt) | Tier A/C harness, `@Tool` bridge to real `CoachTools` |
| [`OnDeviceLlmDebugLauncher.kt`](../android/app/src/main/java/org/codeberg/fitguy/nofud/debug/OnDeviceLlmDebugLauncher.kt) | Release-safe launcher stub; debug runner in `src/debug/` |
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
| `ondevice_llm_model` | string | `gemma-e2b-int4.litertlm` | Filename under `filesDir/models/` |
| `ondevice_llm_tier` | string | `all` | `all`, `a` (Tier A only), or `c` (Tier C only) |
| `ondevice_llm_prompt` | string | `full` | Tier A prompt: `full`, `compact`, `fewshot_units`, or `twopass` |
| `ondevice_llm_repeat` | int | `1` | Tier A repeat count (1–5) for warm-cache latency comparison |

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

Expect **multi-minute** first-time GPU init when MTP is enabled (extra `verify` subgraph). See **MTP caveats (GPU)** below before using `--ez ondevice_llm_mtp true`.

### MTP caveats (GPU, Gemma 4 E2B, litertlm-android 0.13+)

MTP (multi-token / speculative decoding) can deliver ~1.6×–2.2× faster decode on GPU, but the current runtime has known rough edges. **Default: MTP off** (`ondevice_llm_mtp=false`).

| Issue | Symptom | Workaround |
|-------|---------|------------|
| **Token budget bug** ([#2816](https://github.com/google-ai-edge/LiteRT-LM/issues/2816)) | Output stops early (~half the requested length) — draft + rejected tokens count toward `max_output_tokens` | **Double** `maxOutputToken` when MTP is on. Newer LiteRT-LM Kotlin APIs expose `ConversationConfig.maxOutputToken`; **litertlm-android 0.14.0 does not** — workaround is documentation-only until upgrade. |
| **Streaming stutter** | Token stream pauses briefly | Rejection sampling after bad draft guesses — expected; not fixable client-side. |
| **Hardware locality** | Severe slowdown if drafter and main model split across CPU/GPU | Keep both on **GPU** (LiteRT-LM default when `Backend.GPU()` + MTP). Do not mix CPU backend with MTP. |
| **Shared cache collisions** | “Access is denied” on engine startup (some cross-platform builds) | Point `EngineConfig.cacheDir` at a unique path per engine, or use a `:nocache` sentinel if upstream docs recommend it for your build — increases cold-init time. |

For **Exp 2c** (MTP latency): use **one** `adb` invocation with `mtp=true` and `repeat=2` so cold MTP init happens once and pass=1 measures warm MTP decode. Allow **3–5 minutes** for first `engineInit` with MTP (heartbeats every 15 s). Compare pass=0 vs pass=1 Tier A ms and check for `status=parseFail` (token-budget truncation).

```powershell
adb shell am force-stop org.codeberg.fitguy.nofud.debug
adb logcat -c
# Exp 2c — MTP cold init + warm repeat (fewshot keeps unit_options; compact is faster but drops units)
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --ez ondevice_llm_mtp true --es ondevice_llm_tier a --es ondevice_llm_prompt fewshot_units --ei ondevice_llm_repeat 2
adb logcat -s FudOnDeviceLlm
```

Optional A/B — MTP + compact (speed only, expect `unitOptions=0`):

```powershell
adb shell am force-stop org.codeberg.fitguy.nofud.debug
adb logcat -c
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --ez ondevice_llm_mtp true --es ondevice_llm_tier a --es ondevice_llm_prompt compact --ei ondevice_llm_repeat 2
adb logcat -s FudOnDeviceLlm
```

MTP compile cache lands under `cache/litert-mtp/` (separate from non-MTP runs).

### Experiment examples

```powershell
# Exp 1a — few-shot unit_options (Tier A only)
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt fewshot_units

# Exp 1b — two-pass unit inference (Tier A only)
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt twopass

# Exp 2a — compact prompt latency
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt compact

# Exp 2b — warm repeat (second pass uses GPU cache)
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt compact --ei ondevice_llm_repeat 2

# Exp 2c — MTP cold init + warm repeat (allow 3–5 min for first engineInit)
adb shell am force-stop org.codeberg.fitguy.nofud.debug
adb logcat -c
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --ez ondevice_llm_mtp true --es ondevice_llm_tier a --es ondevice_llm_prompt fewshot_units --ei ondevice_llm_repeat 2
adb logcat -s FudOnDeviceLlm

# Exp 3 — FunctionGemma Tier C (push model first; filename may vary — check HF listing)
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier c --es ondevice_llm_model functiongemma-270m-int4.litertlm

# Exp 4 — Gemma 4 E4B full run (optional quality comparison)
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_model gemma-e4b-int4.litertlm
```

### Log format

All lines use tag **`FudOnDeviceLlm`** with `op=ondevice_llm phase=... key=value` bodies, e.g.:

```
op=ondevice_llm phase=tierA backend=gpu i=0 ms=25683 status=ok name=... calories=1250 unitOptions=0 prompt=full
op=ondevice_llm phase=tierA_prompt backend=gpu pass=0 i=0 promptChars=2847
op=ondevice_llm phase=tierA_twopass backend=gpu pass=0 i=0 ms=12000 unitOptions=2
op=ondevice_llm phase=tierC scenario=single_tool ms=10091 response=...
op=ondevice_llm phase=toolCall tool=get_food_entries args=... ms=12 result=...
op=ondevice_llm phase=done backend=gpu tier=ALL prompt=full
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
| `six_round_chain` | Summary + weight + calories + food + propose water + propose weight | Long multi-tool chain stress test |

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
| Tier A `unit_options` | **0/3** with `full` prompt — fixed with **`fewshot_units`** (see experiment log) |
| Tier C tool selection | **Good** — correct tools and date ranges |
| Tier C result grounding | **Good** after `JsonElement` fix — e.g. `single_tool` listed all four foods from `get_food_entries`; `multi_round_chain` computed ~1750 kcal average from real data |
| Tier C malformed recovery | **Reasonable** — answered from summary + partial weight data despite truncated JSON |

---

## Known issues and caveats

1. **`unit_options` (Tier A, `full` prompt)** — Baseline `full` prompt still returns `unit_options: []` (0/3). **`fewshot_units` prompt fixes this** (3/3 with units on Pixel 9a, 2026-07-14 evening run). Production would need inline few-shot examples or a two-pass `inferServingUnitOptions` call.
2. **Tier A latency** — `compact` (~653 chars): **~7 s** (samples 1–2) / **~14 s** (multi-item sample 0). `fewshot_units` (~1494 chars): **~25–30 s** but keeps units. Baseline `full`: ~23–26 s, no units. Target of 2–5 s not reached on complex items.
3. **GPU cold init** — First OpenCL compile takes 30–90 s (`mtp=false`); **~29–30 s** observed when compile cache is warm (Exp 1a/2c). **Several minutes** possible on a truly cold MTP first run (extra `verify` subgraph); Exp 2c MTP init was ~30 s after prior GPU sessions same evening.
4. **GPU MTP (speculative decoding)** — On Pixel 9a with `fewshot_units`, MTP delivers **~1.6× faster Tier A** (~15–17 s vs ~25–30 s) with **no JSON truncation** in Exp 2c (all `status=ok`, unitOptions preserved). Token-budget bug [#2816](https://github.com/google-ai-edge/LiteRT-LM/issues/2816) may still bite on longer outputs — `litertlm-android` **0.14.0** has no `ConversationConfig.maxOutputToken`. Default **MTP off** for smoke tests; enable when benchmarking decode speed.
5. **LiteRT-LM maturity** — Library is beta; tool-calling and GPU paths have active upstream issues. Pin version deliberately when upgrading.
6. **GrapheneOS** — No Play Services / AICore required; vendor GPU drivers + manifest `uses-native-library` entries are sufficient for OpenCL on Pixel 9a.
7. **Not production** — No UI, no provider toggle, no model management; release APKs do not include LiteRT native libs.

---

## Upgrade / experiment notes

- **Version pin:** [`android/gradle/libs.versions.toml`](../android/gradle/libs.versions.toml) → `litertlm = "0.14.0"`.
- **Other models to try:**
  - **Gemma 4 E4B-it** — `gemma-e4b-int4.litertlm` (~2.5–3 GB); quality rung if E2B gaps remain
  - **[FunctionGemma-270m-it](https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions)** — `functiongemma-270m-int4.litertlm` (~288 MB); Tier C specialist, auto-selects minimal system prompt when filename contains `functiongemma`
- **Production path (future):** Would need `ApiFormat.ON_DEVICE`, Settings UX, model file checks, and wiring `FoodAnalysisService` / `ChatService` dispatch — separate from this smoke test.

---

## Experiment log

Record results from `adb logcat -s FudOnDeviceLlm` after each run. Seed test data first if Tier C needs logged entries:

```powershell
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez seed_test_data true
```

| Experiment | Command flags | Sample 0 `unitOptions` | Tier A ms (GPU) | Tier C grounding | Notes |
|------------|---------------|------------------------|-----------------|------------------|-------|
| **Baseline** (2026-07-14 AM) | default (`full`, E2B) | 0/3 | ~23–26 s | 4/4 good post-JsonElement fix | See validated results above |
| **Exp 1a** `fewshot_units` (2026-07-14 PM) | `tier=a prompt=fewshot_units` | **2** (pizza+coke) | ~30 / ~25 / ~28 s (samples 0–2) | n/a | **PASS** — 3/3 parse ok; unitOptions 2/1/2; promptChars≈1494; engineInit ~29 s |
| **Exp 1b** `twopass` | `tier=a prompt=twopass` | _pending_ | _pending_ (pass1+pass2) | n/a | Pass if `phase=tierA_twopass unitOptions>=1` on sample 0; 1a may make this redundant |
| **Exp 2a** `compact` (2026-07-14 PM) | `tier=a prompt=compact` | 0/3 | **~14 / ~7 / ~7 s** (samples 0–2) | n/a | promptChars≈653; ~2× faster than `fewshot_units`; loses `unit_options`; engineInit ~29 s |
| **Exp 2b** warm repeat (2026-07-14 PM) | `tier=a prompt=compact repeat=2` | 0/3 | pass0: ~13/7/7 s; pass1: ~12/7/7 s | n/a | ~4% faster pass1 on sample 0 only; samples 1–2 already warm; single engineInit ~26 s |
| **Exp 2c** MTP + fewshot (2026-07-14 PM) | `mtp=true tier=a prompt=fewshot_units repeat=2` | **2** (all passes) | pass0: **~18 / ~15 / ~17 s**; pass1: ~17 / ~15 / ~17 s | n/a | **PASS** — ~**1.6×** vs non-MTP fewshot; 6/6 parse ok; unitOptions 2/1/2; engineInit ~30 s; no truncation |
| **Exp 3** FunctionGemma | `tier=c model=functiongemma-...` | n/a | n/a | _pending_ | Compare 5 scenarios vs E2B latency/grounding |
| **Exp 4** E4B | `model=gemma-e4b-int4.litertlm` | _pending_ | _pending_ | _pending_ | Watch logcat for OOM; only if Exp 1–3 insufficient |
| **Exp 5** `six_round_chain` + Tier C (2026-07-14 PM) | `tier=c` (E2B, no MTP) | n/a | n/a | **4/5 good** | See Tier C breakdown below; `six_round_chain` partial — wrong counts, missed propose_log_* |

---

## Decision gate (production integration)

Fill in after experiment log runs on Pixel 9a:

| Use case | Go if… | Hybrid if… | No-go if… |
|----------|--------|------------|-----------|
| **Tier A** text food log | `fewshot_units` + MTP ≤ ~17 s GPU **and** JSON 3/3 with units | compact ~7 s without units; fewshot ~25–30 s without MTP | parse failures or >45 s after best prompt combo |
| **Tier C** Coach | E2B passes 4/5 scenarios; FunctionGemma matches or beats on `six_round_chain` | Tier C stays cloud; on-device Tier A only; simple Coach offline ok | tool results ignored, or long chains consistently fail |
| **`unit_options`** | Exp 1a `fewshot_units` fixes all samples (2026-07-14) | two-pass if few-shot too token-heavy for production | both fewshot and twopass fail on pizza sample |

**Assessment after Exp 1a + 2a/2b/2c + 5 (2026-07-14 PM):**

- Tier A: **Go with tuning** — Best combo: **`fewshot_units` + MTP** (~15–17 s GPU, units preserved). `compact` ~7 s but no units.
- Tier C: **Hybrid** — E2B passes 4/5 scripted scenarios with strong grounding (~9–15 s each). **`six_round_chain` stress-exposed limits**: date typos in tool args, missed `propose_log_water`/`propose_log_weight`, hallucinated numbers (733.8 kg, 128 vs 1206 foods), possible response truncation. Coach on-device viable for simple/medium tool use; long compound requests stay cloud-first.
- `unit_options`: **Go with prompt change** — `fewshot_units`; unaffected by MTP
- MTP: **Go for Tier A decode** — ~1.6× speedup on fewshot; no truncation in Exp 2c

**Tier C scenario results (2026-07-14 PM, E2B GPU, no MTP):**

| Scenario | ms | Tool calls | Grounding | Verdict |
|----------|-----|------------|-----------|---------|
| `single_tool` | ~10.7 s | `get_food_entries` | Listed all 4 foods + kcal | **Pass** |
| `ambiguous` | ~9.2 s | `get_data_summary` | Cited real counts; reasonable follow-up | **Pass** |
| `multi_round_chain` | ~10.2 s | `get_calorie_totals` + `propose_log_water` | ~1750 kcal avg + water proposal | **Pass** |
| `malformed_recovery` | ~15.0 s | summary + truncated weight JSON | Answered from summary + partial weights | **Pass** (intentional corrupt) |
| `six_round_chain` | ~31.0 s | 4 tools (summary, weight, calories, food) | Summary partly wrong; no propose_log calls | **Partial** |

**Suggested production prompt strategy (preliminary):** `fewshot_units` + optional MTP for Tier A on-device; Tier C stays **cloud default**, on-device E2B for offline simple Coach queries only.

---

## What next

Experiments still open vs done:

| Step | Status | Action |
|------|--------|--------|
| Exp 1b `twopass` | **Skip** | Exp 1a + MTP covers units |
| Exp 2a/2b/2c | **Done** | Logged |
| Exp 5 Tier C + `six_round_chain` | **Done** | 4/5 pass; see table above |
| **Exp 3** FunctionGemma | **Next** | Push `functiongemma-270m-int4.litertlm`; run `--es ondevice_llm_tier c` — compare Tier C latency/grounding vs E2B, especially `six_round_chain` |
| **Exp 4** E4B | **Optional** | Only if FunctionGemma worse on Tier C quality |
| **Production integration** | **Deferred** | Decision gate has enough Tier A data; Tier C = hybrid. Next code phase would be `ApiFormat.ON_DEVICE` + Settings — separate milestone |

**Exp 3 commands (after HF download + adb push):**

```powershell
adb push functiongemma-270m-int4.litertlm /data/local/tmp/
adb shell run-as org.codeberg.fitguy.nofud.debug cp /data/local/tmp/functiongemma-270m-int4.litertlm files/models/
adb shell am force-stop org.codeberg.fitguy.nofud.debug
adb logcat -c
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier c --es ondevice_llm_model functiongemma-270m-int4.litertlm
adb logcat -s FudOnDeviceLlm
```

If skipping FunctionGemma/E4B downloads, the **experiments phase is complete** — summarize in a commit, then plan production wiring (Tier A only) as a follow-up task.

---

## Related docs

- [`AGENTS.md`](../AGENTS.md) — debug intent extras, WSL/Windows adb split
- [`DEVELOPMENT.md`](../DEVELOPMENT.md) — devenv build workflow
- [`CALCULATION_METHODS.md`](../CALCULATION_METHODS.md) — unrelated to LLM; listed for agent navigation only
