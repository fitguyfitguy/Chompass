# Chompass Privacy Policy

Chompass is an ad-free calorie tracker forked from [Fud AI](https://github.com/apoorvdarshan/fud-ai). Available as an Android app and a browser PWA.

## Summary

- **No ads** - Chompass does not include AdMob or any other advertising SDK.
- **No analytics** - No usage tracking, crash analytics, or telemetry SDKs are bundled.
- **No accounts** - There is no sign-in or central user database. Optional **user-hosted sync** (WebDAV / sync JSON) is configured by you against your own server; Chompass does not operate a sync backend.
- **Local storage** - Food logs, weight history, profile, and Coach chat are stored on your device (or in the browser for the PWA).
- **Bring your own AI key** - API keys are encrypted at rest on your device before storage. AI requests go from your device to the provider you choose, not through a Chompass server. What each feature sends is listed in [AI data sharing by feature](#ai-data-sharing-by-feature).
- **On-Device (Private)** - On Android, optional Gemma 4 is the **only fully private configuration**: food analysis, "What if?", and goal estimates run on the device and nothing is uploaded for those paths.

## API keys

| Client | How keys are stored |
|--------|---------------------|
| **Android** | EncryptedSharedPreferences with an AES-256 master key in the Android Keystore. The keychain file is excluded from cloud backup and device transfer. |
| **PWA** | Web Crypto AES-GCM with a non-extractable wrapping key; ciphertext is kept in IndexedDB. |

Keys are decrypted in memory when you use AI features, then sent only to the provider you configure. Encryption protects keys at rest on disk. It does not protect against malware, a rooted/jailbroken device, or a compromised browser page that can run as the app.

Clearing app data (Android) or site data (browser) removes stored keys. Reinstalling the Android app after a Keystore mismatch can also wipe keys.

## Data on your device

The app stores locally:

- Food entries, photos, favorites, and diary exports
- Weight and body-fat history
- Optional water intake log (local only; not written to Health Connect unless you use a separate app that syncs hydration)
- Profile, goals, and settings
- Coach conversation history
- Widget snapshots (Android)

**Delete All Data** (Settings) wipes app storage only. It does not remove records you previously synced to Health Connect - manage those in the Health Connect app if needed.

## Network requests

Chompass may contact external services only when you use a feature that requires it:

| Feature | What is sent | Where |
|---------|----------------|-------|
| AI features (see [AI data sharing by feature](#ai-data-sharing-by-feature)) | Food photos, meal text, chat context, profile, and diary totals, depending on the feature | Your configured AI provider |
| Barcode scan | Scanned barcode | Open Food Facts public API |
| Health Connect | Nutrition, weight, body fat, height (write); sleep, resting heart rate, hydration, steps, energy (read) if you enable sync | Google Health Connect **on-device** (Android 14+: system module; Android 13 and lower: optional Play Store APK). No Chompass cloud. |
| User-hosted sync | Sync document (diary, metrics, water, favorites, recipes; not API keys or food photos) | WebDAV URL you configure |
| App update check | Installed version | Not used (manual updates via Codeberg releases) |

Optional **Health Connect background sync** (off by default) only reads from Health Connect on-device on a periodic schedule when the module supports background reads and you grant that permission; it sends nothing off the device. User-hosted WebDAV sync runs when you tap **Sync now**, or optionally once per day when you open the app if you enable **Sync on open**.

On Android 14+, Health Connect is a system/Mainline module. Chompass never requires installing the old Play Store Health Connect APK on those versions, and does not require sandboxed Play. If your ROM does not expose the Health Connect service to apps, use diary / body-metrics export/import (or WebDAV) instead.
Chompass does not sell or share your health data for advertising.

## AI data sharing by feature

Chompass routes all AI features to the provider you choose in **Settings → AI Provider**. With a **cloud provider** (Gemini, OpenAI, Anthropic, xAI, OpenRouter, Together AI, Groq, Hugging Face, Fireworks, DeepInfra, Mistral, Ollama pointed at a remote server, or a custom OpenAI-compatible endpoint), the following data leaves your device when you use each feature:

| Feature | What is sent to the cloud provider |
|---------|-------------------------------------|
| Food analysis (photo or note) | The food photo(s) and/or meal text you submit |
| "What if?" meal impact | The meal being reviewed, today's logged diary totals before/after, and your profile |
| Coach chat | Your message, chat history, your profile, and recent food/weight/body-fat logs the Coach reads to answer |
| AI goal estimation (onboarding plan, Settings → Recalculate, weekly Adaptive Goals) | Your profile (gender, age, weight, height, body fat, activity, goal, diet mode) and logged food/weight trends |
| Optional nutrient goals | Your profile |
| Gemini Google Search (if enabled) | The AI request context is additionally sent to Google Search |

Your API key goes only to the provider you configured; Chompass has no server in between. Providers' own privacy policies govern how they store or train on data you send them.

**On-device Gemma 4 (Android) is the only fully private configuration.** Food analysis, "What if?", and goal estimates run on your phone and nothing is uploaded for those paths. Coach, meal ingredient breakdown, and grounded entry are not available on-device yet. The model file itself is downloaded from Hugging Face, a third-party host.

If you enable the **Fallback Provider** setting, failed requests are retried with a second provider, and the same data goes to whichever provider actually answers. Local Ollama (localhost) sends requests to your own machine, not a cloud provider.

## Meal sharing

Shared meals use a `chompass://` deep link with base64-encoded nutrition JSON. Links you send leave your device through the app or messenger you choose. Chompass can **import** meals shared from upstream Fud AI (`fudai://` links) but does not link outbound shares to third-party websites.

## Upstream

Chompass is based on open-source software by Apoorv Darshan under the MIT License. See [NOTICE](NOTICE.md) and [LICENSE](../LICENSE).

## Contact

Source and issues: https://codeberg.org/fitguy/chompass
