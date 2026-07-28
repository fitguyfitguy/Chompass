# Chompass Privacy Policy

Chompass is an ad-free calorie tracker forked from [Fud AI](https://github.com/apoorvdarshan/fud-ai). Available as an Android app and a browser PWA.

## Summary

- **No ads** - Chompass does not include AdMob or any other advertising SDK.
- **No analytics** - No usage tracking, crash analytics, or telemetry SDKs are bundled.
- **No accounts** - There is no sign-in or central user database. Optional **user-hosted sync** (WebDAV / sync JSON) is configured by you against your own server; Chompass does not operate a sync backend.
- **Local storage** - Food logs, weight history, profile, and Coach chat are stored on your device (or in the browser for the PWA).
- **Bring your own AI key** - API keys are encrypted at rest on your device before storage. Food analysis and Coach requests go from your device to the AI provider you choose, not through a Chompass server.
- **On-Device (Private)** - On Android, optional Gemma 4 keeps food text and photo analysis on the device; nothing is uploaded for that path.

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
| AI food analysis / Coach | Meal text, images, or chat context you submit | Your configured AI provider |
| Barcode scan | Scanned barcode | Open Food Facts public API |
| Health Connect | Nutrition, weight, body fat, height (write); sleep, resting heart rate, hydration, steps, energy (read) if you enable sync | Google Health Connect on-device |
| User-hosted sync | Sync document (diary, metrics, water, favorites, recipes; not API keys or food photos) | WebDAV URL you configure |
| App update check | Installed version | Not used (manual updates via Codeberg releases) |

Optional **Health Connect background sync** (off by default) only reads from Health Connect on-device on a periodic schedule; it sends nothing off the device. User-hosted WebDAV sync runs only when you tap **Sync now**.

Chompass does not sell or share your health data for advertising.

## Meal sharing

Shared meals use a `chompass://` deep link with base64-encoded nutrition JSON. Links you send leave your device through the app or messenger you choose. Chompass can **import** meals shared from upstream Fud AI (`fudai://` links) but does not link outbound shares to third-party websites.

## Upstream

Chompass is based on open-source software by Apoorv Darshan under the MIT License. See [NOTICE](NOTICE.md) and [LICENSE](../LICENSE).

## Contact

Source and issues: https://codeberg.org/fitguy/chompass
