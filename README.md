<div align="center">

<img src="metadata/en-US/images/icon.png" alt="Chompass icon" width="96" />

# Chompass

**Ad-free AI calorie tracker: Android app and browser PWA**

Bring your own cloud AI key on either client. On the Android app you can also run Gemma 4 on-device. Fork of [Fud AI](https://github.com/apoorvdarshan/fud-ai) with no ads and no analytics.

[Website](https://chompass.app/) · [Download](https://chompass.app/download/) · [Web app](https://chompass.app/app/) · [Privacy](docs/PRIVACY.md)

[![Latest release](https://img.shields.io/gitea/v/release/fitguy/Chompass?gitea_url=https://codeberg.org&style=flat-square&label=release&color=127059)](https://codeberg.org/fitguy/chompass/releases)
[![PWA](https://img.shields.io/badge/Platform-PWA-127059?style=flat-square&logo=pwa&logoColor=white)](https://chompass.app/app/)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://codeberg.org/fitguy/chompass)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)
[![Privacy](https://img.shields.io/badge/Tracking-None-blue?style=flat-square)](docs/PRIVACY.md)

</div>

Log food with a photo, voice, barcode, share intent, or typed note. Use your own cloud AI key, or download Gemma 4 once for on-device analysis in the Android app. No ads, no account, no analytics. Open diary and body-metrics exports on both clients; [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) for scales and wearables in the Android app.

Use the [installable PWA](https://chompass.app/app/) in any modern browser (Chromium-based browsers work best for install, camera barcode, and speech) or the Android APK. Both use the same diary and body-metrics JSON contracts; there is no cloud sync between clients. Health Connect, widgets, notifications, on-device AI, and full i18n are Android-app features. Project home: [codeberg.org/fitguy/chompass](https://codeberg.org/fitguy/chompass).

## Install

### Web app (PWA)

Open [chompass.app/app/](https://chompass.app/app/) in any modern browser (phone, tablet, or desktop). It is a progressive web app you can install to the home screen or dock. Diary logging, progress, BYOK AI entry and Coach, and shared JSON export/import with the Android app.

Works in Firefox, Safari, and other engines; **Chromium-based browsers** (Chrome, Edge, Brave, Cromite, etc.) generally have the smoothest install prompt, camera barcode, and Web Speech support.

Step-by-step install for iOS, Android, and desktop browsers: [Download → How to install](https://chompass.app/download/#how-to-install) (also **Settings → Install app** inside the PWA).

### Android app

Not on the Play Store. F-Droid metadata is submitted; until the package shows up in the F-Droid client, install from Obtainium or Codeberg Releases.

<div align="center">

[![Get it on Obtainium](https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png)](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://codeberg.org/fitguy/chompass)

[![Codeberg Releases](https://img.shields.io/badge/Download-APK%20on%20Codeberg-127059?style=for-the-badge&logo=android&logoColor=white)](https://codeberg.org/fitguy/chompass/releases)

</div>

- **Obtainium** *(recommended)*: tap the badge above, then confirm in Obtainium. Or paste `https://codeberg.org/fitguy/chompass` into **Add App**.
- **Direct APK**: download from [Codeberg Releases](https://codeberg.org/fitguy/chompass/releases). Prefer `arm64-v8a` on modern phones; use `armeabi-v7a` for older 32-bit devices, `x86_64` for emulators/Chromebooks, or universal only when unsure.
- **F-Droid**: package `app.chompass` ([expected listing](https://f-droid.org/packages/app.chompass/) once indexed).

Release package ID: `app.chompass`. Debug (from source): `app.chompass.debug`.

## Screenshots

Material 3 dark theme (light theme also available). Images are in [`docs/screenshots/`](docs/screenshots/).

<table>
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/home.png" width="180" alt="Home screen in dark theme" /><br />
      <sub><b>Home</b>: calorie ring, macros, meals</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/progress.png" width="180" alt="Progress screen in dark theme" /><br />
      <sub><b>Progress</b>: weight, steps, goals</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/add-food.png" width="180" alt="Add food sheet in dark theme" /><br />
      <sub><b>Add food</b>: photo, voice, barcode, AI</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/coach.png" width="180" alt="AI Coach in dark theme" /><br />
      <sub><b>Coach</b>: chat with your provider key</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/settings.png" width="180" alt="Settings screen in dark theme" /><br />
      <sub><b>Settings</b>: diet modes, Health Connect</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/home-light.png" width="180" alt="Home screen in light theme" /><br />
      <sub><b>Home (light)</b>: same dashboard, light theme</sub>
    </td>
  </tr>
</table>

## Features

Shared on the [PWA](https://chompass.app/app/) and Android app unless noted:

- **Food logging**: multi-photo (up to 10), share into the app (Android), voice, barcode (FOSS [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) on Android; browser / Open Food Facts on web), text, manual entry, saved meals, draft recovery
- **AI**: cloud BYOK (a free [Google AI Studio](https://aistudio.google.com/apikey) key is enough for casual use). On Android: opt-in **On-Device (Private)** Gemma 4 via [LiteRT-LM](https://developers.google.com/edge/litert-lm/android), optional fallback provider. AI Coach chat on both clients.
- **Progress**: weight, body fat, measurements, forecast. **Health Connect** (Android): steps, exercise, wellness; two-way sync with Gadgetbridge, openScale, and other Health Connect apps
- **Diet & extras**: keto and other diet modes, water tracking. Android: home-screen widgets, 15 languages (web is EN-first)
- **Open data**: diary export (JSON / Markdown / CSV), weight and body-metrics import/export, bulk JSON import, meal share links (`chompass://` on Android; hash URL on web)
- **Size (Android)**: ~15 MiB arm64 / ~27 MiB universal; no ads, analytics, or workout library ([releases](https://codeberg.org/fitguy/chompass/releases))

## Why Chompass

Chompass forked [Fud AI](https://github.com/apoorvdarshan/fud-ai) in July 2026 when upstream briefly shipped AdMob banners (removed again in 3.0.3). This fork stays ad-free, ships an Android app and a browser PWA, keeps a smaller APK, and adds open export/import plus Health Connect. It does not try to match upstream feature for feature.

Priorities: privacy ([PRIVACY.md](docs/PRIVACY.md)), FOSS barcode scanning, open data, wearables via Health Connect, lean F-Droid / Codeberg distribution (Play flavor disabled; see [`docs/DISTRIBUTION.md`](docs/DISTRIBUTION.md)), and a data-compatible [PWA](https://chompass.app/app/) for any modern browser.

See [CHANGELOG.md](docs/CHANGELOG.md) for release notes.

## On-device AI (private)

**Android app.** **Settings → AI Provider → On-Device (Private)** runs food logging AI on your phone. No API key, no account, and nothing sent to a cloud provider for text or photo analysis.

| | |
|---|---|
| **Models** | [Gemma 4 Edge](https://developers.google.com/edge/litert-lm/android) (E2B ~2.4 GB or E4B ~3.4 GB), downloaded once from Hugging Face |
| **What stays local** | Food text and photo analysis when logging meals |
| **Requirements** | arm64 or x86_64, 6 GB+ RAM (hidden on unsupported devices) |
| **Accuracy** | On-device models are much smaller than cloud AI and often misread portions, brands, and photos. Cloud AI is still the better default. |
| **Fallback** | Enable **Fallback Provider** so a cloud model retries when on-device inference fails |

Coach chat still needs a cloud provider. Added in [v1.14.0](docs/CHANGELOG.md#1140---2026-07-15).

## Health Connect

**Android app.** Chompass uses **Android Health Connect**. No vendor SDKs, no accounts. Anything that syncs into Health Connect works with Chompass:

| Companion | What it brings | Notes |
|-----------|----------------|-------|
| [Gadgetbridge](https://gadgetbridge.org/) | Steps, exercise, weight from wearables | FOSS; enable *Settings → External Integrations → Health Connect* |
| [openScale](https://f-droid.org/en/packages/com.health.openscale/) | Weight and body composition from Bluetooth scales | FOSS, on F-Droid |
| Samsung Health, Fitbit, Withings, etc. | Weight, activity, energy burn | Via each app's Health Connect sync |

| Direction | Data |
|-----------|------|
| **In** | Weight and body fat (live), meals from other apps, steps and exercise, sleep / resting HR / hydration, active/total energy burn |
| **Out** | Every meal as a full `NutritionRecord`, plus weight, body fat, and height |

Optional **background sync** (Settings → Health & Data, off by default) checks Health Connect every few hours when Chompass is closed.

> **Huawei Health users:** pair your wearable with [Gadgetbridge](https://gadgetbridge.org/) instead. Huawei Health Kit requires an HMS account and is not supported.

## Migrate from Fud AI

| Path | Steps |
|------|-------|
| **A: File export** | In Fud AI, export your food diary as JSON. In Chompass (Android or web), open **Settings → Import Food Diary JSON** |
| **B: Health Connect** | Enable Health Connect in both apps and grant read permissions (Android) |

**Settings → Import Weight & Body Data** also accepts Chompass JSON/CSV, [openScale](https://f-droid.org/en/packages/com.health.openscale/) CSV, and common weight CSVs. Body-circumference sites have no Health Connect record type, so use file transfer for those.

### Fud AI vs Chompass Android vs Chompass PWA

| Feature | [Fud AI](https://github.com/apoorvdarshan/fud-ai) | Chompass Android | [Chompass PWA](https://chompass.app/app/) |
|---------|--------|---------------|-----------|
| Banner ads | Brief AdMob; removed in 3.0.3 | **Never shipped** | **Never shipped** |
| On-device AI (Gemma 4) | No | **Yes** (opt-in) | No (BYOK cloud) |
| Barcode | ML Kit | **FOSS zxing-cpp** | Browser / OFF |
| Workouts library | Yes (~120 MB APK) | **Omitted** (~15 MiB arm64) | Omitted |
| Keto / diet modes | No | **Yes** | **Yes** |
| Health Connect | Partial | **Steps, exercise, sleep, HR, hydration** | No |
| Widgets / notifications | Upstream set | **Yes** | No (installable PWA) |
| Languages | Upstream | **15** | EN-first |
| Distribution | Play-focused | **Codeberg** / Obtainium / F-Droid | **PWA** in any modern browser (`/app/`) |
| Open diary / body JSON | Upstream formats | **Yes** | **Same contracts as Android** |

Sources: [Fud AI releases](https://github.com/apoorvdarshan/fud-ai/releases), [Chompass releases](https://codeberg.org/fitguy/chompass/releases). Capability matrix for maintainers: [`docs/PARITY.md`](docs/PARITY.md).

## Performance

- **Install size:** no workouts bundle or ad SDK; arm64 ~15 MiB, universal ~27 MiB
- **Food log I/O:** monthly diary buckets for large histories
- **UI:** off-main-thread entry thumbnails; Progress screen worst-frame latency cut from ~1.1s to ~0.5s in our debug baseline
- **AI uploads:** image downscaling before provider requests

## Docs & privacy

Guides, changelog, F-Droid draft metadata, and food-accuracy benchmarks live under [`docs/`](docs/).

No ads, analytics, or tracking SDKs. Food logs, body metrics, and Coach chat stay on-device unless you export them or sync through Health Connect. Cloud AI requests go only to the provider you configure (BYOK). API keys are encrypted at rest (Android Keystore / EncryptedSharedPreferences on Android; Web Crypto AES-GCM in the PWA) and are not sent to a Chompass server. **On-Device (Private)** keeps food analysis on the device. See [PRIVACY.md](docs/PRIVACY.md).

## Attribution & license

Chompass is based on [Fud AI](https://github.com/apoorvdarshan/fud-ai).

- Copyright (c) 2026 Apoorv Darshan - [MIT License](LICENSE)
- Modifications Copyright (c) 2026 fitguy - MIT License

See also [NOTICE](docs/NOTICE.md) and [ASSET_CREDITS.md](docs/ASSET_CREDITS.md).
