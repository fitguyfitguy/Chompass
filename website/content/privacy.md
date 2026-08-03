---
title: Privacy
description: No ads, no analytics, no accounts. Food logs stay on your device. API keys are encrypted at rest. BYOK cloud AI or on-device inference on Android.
layout: single
---

Chompass is a private calorie tracker for the [installable browser PWA](https://chompass.app/app/) and Android. It is based on [Fud AI](https://github.com/apoorvdarshan/fud-ai).

## Summary

- **No ads:** No AdMob or other advertising SDKs.
- **No analytics:** No usage tracking, crash analytics, or telemetry SDKs.
- **No accounts:** No sign-in or central user database. Optional **user-hosted sync** (WebDAV / sync JSON) uses a server you configure; Chompass does not operate a sync backend.
- **Local storage:** Food logs, weight history, profile, and Coach chat stay on your device.
- **Bring your own AI key:** Keys are encrypted at rest on your device, then sent only to the provider you choose (not through a Chompass server).
- **On-Device (Private):** On Android, optional Gemma 4 keeps food text and photo analysis on the device; nothing is uploaded for that path.

## API keys

| Client | How keys are stored |
|--------|---------------------|
| **Android** | EncryptedSharedPreferences with an AES-256 master key in the Android Keystore. The keychain file is excluded from cloud backup and device transfer. |
| **PWA** | Web Crypto AES-GCM with a non-extractable wrapping key; ciphertext in IndexedDB. |

Encryption protects keys sitting on disk. It does not protect against malware, a rooted device, or a compromised browser page that can run as the app. Clearing app or site data removes stored keys.

## Web app (PWA)

The [Chompass PWA](https://chompass.app/app/) runs in any modern browser and stores diary data in IndexedDB. Same BYOK stance as the Android app: no account, no analytics, cloud AI only to the provider you choose. Optional user-hosted WebDAV sync (or sync JSON import/export) can keep Android and the PWA aligned. Chromium-based browsers generally offer the best install and media APIs; Firefox and Safari work with some gaps.

## Data on your device

The Android app stores locally: food entries and photos, weight and body-fat history, optional water log, profile and goals, Coach history, and widget snapshots. The web app stores the equivalent diary, metrics, and settings in the browser.

**Delete All Data** (Settings) wipes app storage only. On Android it does not remove records previously synced to Health Connect. Manage those in the Health Connect app.

## Network requests

Chompass contacts external services only when you use a feature that needs them:

| Feature | What is sent | Where |
|---------|----------------|-------|
| AI food analysis / Coach | Meal text, images, or chat context you submit | Your configured AI provider |
| Barcode scan | Scanned barcode | Open Food Facts public API |
| Health Connect | Nutrition, weight, body fat, height (write); sleep, HR, hydration, steps, energy (read) if enabled | Google Health Connect on-device (Android 14+: system module; ≤13: optional Play Store APK). No Chompass cloud. |
| User-hosted sync | Sync document (diary, metrics, water, favorites, recipes; not API keys or food photos) | WebDAV URL you configure |
| App updates | (none) | Manual via Codeberg releases |

Optional **Health Connect background sync** (off by default) only reads Health Connect on-device when the module supports background reads; it sends nothing off the device. User-hosted WebDAV sync runs when you tap **Sync now**, or optionally once per day when you open the app if you enable **Sync on open**. On Android 14+, Chompass does not require the Play Store Health Connect APK or sandboxed Play — if the ROM does not expose HC to apps, use file export/import or WebDAV.

Chompass does not sell or share your health data for advertising.

## Meal sharing

Shared meals use a `chompass://` deep link (Android) or a hash URL on the web app, with base64-encoded nutrition JSON. Links leave your device through the app or messenger you choose.

## Contact

Source and issues: [codeberg.org/fitguy/chompass](https://codeberg.org/fitguy/chompass).

Canonical copy also lives in the repository as [PRIVACY.md](https://codeberg.org/fitguy/chompass/src/branch/main/docs/PRIVACY.md).
