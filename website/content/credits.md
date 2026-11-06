---
title: Credits
description: Licenses, BYOK AI providers, FOSS libraries, and data sources behind Chompass.
layout: single
---

Chompass is free software. This page lists what it is built on: the license, the AI providers you can bring a key for, the FOSS barcode and data stack, companion apps, and the libraries.

## License

Chompass is licensed under [GPL-3.0-or-later](https://codeberg.org/fitguy/chompass/src/branch/main/LICENSE). It is a fork of [Fud AI](https://github.com/apoorvdarshan/fud-ai) by Apoorv Darshan. Upstream portions were previously MIT; that grant is preserved in [NOTICE](https://codeberg.org/fitguy/chompass/src/branch/main/docs/NOTICE.md).

## AI providers (bring your own key)

Cloud AI is optional. You pick the provider and paste your own key. Keys stay on your device, encrypted at rest; no request goes to a Chompass server. See the [privacy policy](/privacy/).

| Client  | Choices                                                                                                                                                                        |
| ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Android | Gemini, OpenAI, Anthropic, xAI, OpenRouter, Together, Groq, Hugging Face, Fireworks, DeepInfra, Mistral, Ollama (local), Ollama Cloud, any OpenAI-compatible endpoint, On-Device (Private) |
| PWA     | Gemini, OpenAI, Anthropic, OpenAI-compatible                                                                                                                                    |

On-Device runs Gemma 4 on the phone via [LiteRT-LM](https://developers.google.com/edge/litert-lm) (Apache-2.0). The model downloads once from Hugging Face; Google's Gemma model terms apply to the weights. No API key, and no server upload for food analysis.

## Barcode scanning and food data

| Component                              | What it does                                                            | License                                  | Link                                                                        |
| -------------------------------------- | ----------------------------------------------------------------------- | ---------------------------------------- | --------------------------------------------------------------------------- |
| zxing-cpp                              | Barcode scanning on Android                                             | Apache-2.0                               | [zxing-cpp/zxing-cpp](https://github.com/zxing-cpp/zxing-cpp)               |
| zxing-wasm (vendored, 3.1.2)           | Browser barcode fallback when native BarcodeDetector is missing         | MIT (wrapper) / Apache-2.0 (zxing-cpp)   | [Sec-ant/zxing-wasm](https://github.com/Sec-ant/zxing-wasm)                 |
| Open Food Facts                        | Live product lookup by barcode and search; photos on the review sheet   | ODbL (data), photos CC BY-SA 3.0         | [world.openfoodfacts.org](https://world.openfoodfacts.org)                  |
| USDA FoodData Central (offline subset) | Offline food search on Android                                          | Public domain (CC0)                      | [FoodData Central](https://fooddatacentral.usda.gov)                        |
| Swiss Food Composition Database        | Offline food search on Android                                          | Official federal data                    | [naehrwertdaten.ch](https://naehrwertdaten.ch)                              |
| usda-gtins, off-de                     | Optional offline barcode packs; separate release assets, never in the APK | Public domain / ODbL                   | [Codeberg releases](https://codeberg.org/fitguy/chompass/releases)          |

Open Food Facts is queried live and never bundled. The USDA and Swiss databases ship inside the app, with provenance badges on results. The Swiss files are the official federal dataset, fetched via the [Food You](https://github.com/maksimowiczm/FoodYou) (GPL-3.0) mirror bundle.

## Companion apps

| App          | What it brings                                             | License | Link                                                                        |
| ------------ | ---------------------------------------------------------- | ------- | --------------------------------------------------------------------------- |
| openScale    | Weight and body composition from Bluetooth scales          | GPL-3.0 | [F-Droid: openScale](https://f-droid.org/en/packages/com.health.openscale/) |
| Gadgetbridge | Steps, exercise, and weight from wearables                 | GPL-3.0 | [gadgetbridge.org](https://gadgetbridge.org/)                               |

Both feed Chompass through Health Connect. They are not bundled and not affiliated; they are separate installs.

## Libraries and fonts

Numerals use [Doto](https://github.com/oliverlalan/Doto) by Óliver Lalan, under the [SIL Open Font License 1.1](https://openfontlicense.org). The Android app is built on Jetpack Compose, Material 3, DataStore, Health Connect, CameraX, Glance, LiteRT-LM, Coil, Vico, OkHttp, Haze, kotlinx.serialization, kotlinx.coroutines, and blessed-kotlin for Bluetooth LE. The PWA is plain Web Components with the vendored zxing-wasm build above.

The full, maintained lists: [ASSET_CREDITS.md](https://codeberg.org/fitguy/chompass/src/branch/main/docs/ASSET_CREDITS.md) and [NOTICE.md](https://codeberg.org/fitguy/chompass/src/branch/main/docs/NOTICE.md) in the repository.
