# On-device LLM (Gemma 4 E2B-it)

Runs **Gemma 4 E2B-it** locally via [Google AI Edge LiteRT-LM](https://developers.google.com/edge/litert-lm/android). Tier A (text) and Tier B (photo) are wired into production dispatch as `AIProvider.ON_DEVICE`, gated behind a default-off Settings toggle (`onDeviceFeatureVisible`). Tier C (coach) stays debug-only.

**Status (2026-09-08):** Smoke-validated on **Pixel 9a / GrapheneOS** with GPU backend (`litertlm-android` **0.16.1**). Recommended daily-driver: **`preset=daily`** (`gpu` + `fewshot_units` + MTP when cache warm). The RAM floor and CPU-fallback latency are provisional until a second, non-Tensor device is tested. Settings notes this on the on-device model download sheet.

The model is **not bundled** in the APK (2.6 GB E2B / 3.7 GB E4B). Users download it in-app from Hugging Face (`litert-community`, native/mobile `.litertlm` build) into `filesDir/models/`. SHA-256 is verified before the file is renamed into place.

## Production integration

| Piece | File |
|---|---|
| Provider enum | [`AIProvider.kt`](../android/app/src/main/java/app/chompass/models/AIProvider.kt): `ON_DEVICE` / `ApiFormat.ON_DEVICE` |
| Model catalog | [`ModelCatalog.kt`](../android/app/src/main/java/app/chompass/services/ondevice/ModelCatalog.kt): HF repo, filename, sha256, size |
| Download | [`ModelDownloadManager.kt`](../android/app/src/main/java/app/chompass/services/ondevice/ModelDownloadManager.kt) / [`ModelDownloadWorker.kt`](../android/app/src/main/java/app/chompass/services/ondevice/ModelDownloadWorker.kt) |
| Capability gate | [`OnDeviceCapability.kt`](../android/app/src/main/java/app/chompass/services/ondevice/OnDeviceCapability.kt): ABI + 6 GB RAM floor |
| Engine lifecycle | [`OnDeviceLlmGateway.kt`](../android/app/src/main/java/app/chompass/services/ondevice/OnDeviceLlmGateway.kt) |
| Dispatch | [`FoodAnalysisService.dispatch()`](../android/app/src/main/java/app/chompass/services/ai/FoodAnalysisService.kt) via [`OnDeviceLlmDispatchClient.kt`](../android/app/src/main/java/app/chompass/services/ai/OnDeviceLlmDispatchClient.kt) |
| Settings UX | [`SettingsAiSection.kt`](../android/app/src/main/java/app/chompass/ui/settings/SettingsAiSection.kt) / [`OnDeviceModelSheet.kt`](../android/app/src/main/java/app/chompass/ui/settings/OnDeviceModelSheet.kt) |

`OnDeviceLlmDispatchClient` routes to `generateWithImage` whenever an image is attached, so Tier B (photo) uses the same dispatch path as Tier A.

**Lean entry schema:** every entry op (`analyzeText`, `analyzeAuto`, `analyzeFood`, `analyzeFoodMulti`) sends the on-device provider a **lean JSON schema** — `name, calories, protein, carbs, fat, serving_size_grams, emoji, unit_options[]` (`EntryConstituentPromptKind.LEAN`) instead of the 34-field meal schema with 21 micronutrients. The full schema pushed E2B replies toward the output cap; truncated JSON is the mechanism behind `AiError.InvalidResponse` reports on 4.7.0 ([#68](https://codeberg.org/fitguy/Chompass/issues/68)). Tradeoff: on-device entries and label photos carry **no micronutrient estimates** (those stay available with cloud providers).

## Model

| Field | Value |
|---|---|
| Model | **Gemma 4 E2B-it** (instruction-tuned, ~2B params) |
| Format | **`.litertlm`** (LiteRT-LM native/mobile bundle, not web/WASM) |
| Quantization | int4 |
| Source | [Hugging Face `litert-community`](https://huggingface.co/litert-community) native/mobile artifact |
| On-device path | `filesDir/models/gemma-4-E2B-it.litertlm` |

Hugging Face listings can include both **web** and **native/mobile** builds. Web variants fail at load (`TF_LITE_PREFILL_DECODE not found`). Only the native `.litertlm` mobile build works with the Android API.

## Stack

| Component | Detail |
|---|---|
| Library | `com.google.ai.edge.litertlm:litertlm-android:0.16.1` |
| Default backend | `Backend.GPU()` (OpenCL via vendor drivers) |
| CPU fallback | `Backend.CPU(numOfThreads = 4)` |
| GPU prerequisites | `uses-native-library` for `libOpenCL.so` and `libvndksupport.so` in the manifest (Android 12+ linker namespace) |

`OnDeviceCapability.isSupported()` hides `ON_DEVICE` on ABIs other than `arm64-v8a` / `x86_64`. There is no native lib for `armeabi-v7a`.

Related: [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) § AI-RECALC (SAFE tier on on-device models); [`ACCURACY.md`](ACCURACY.md) for photo vs text error bands.
