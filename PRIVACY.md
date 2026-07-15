# NoFUD Privacy Policy

NoFUD is a privacy-first, ad-free calorie tracker forked from [Fud AI](https://github.com/apoorvdarshan/fud-ai).

## Summary

- **No ads** - NoFUD does not include AdMob or any other advertising SDK.
- **No analytics** - No usage tracking, crash analytics, or telemetry SDKs are bundled.
- **No accounts** - There is no sign-in, cloud sync, or central user database.
- **Local-first** - Food logs, weight history, profile, and Coach chat are stored on your device.
- **Bring your own AI key** - API keys are encrypted on-device (Android Keystore + EncryptedSharedPreferences). Food analysis and Coach requests go directly from your phone to the AI provider you choose.

## Data on your device

The app stores locally:

- Food entries, photos, favorites, and diary exports
- Weight and body-fat history
- Optional water intake log (local only; not written to Health Connect unless you use a separate app that syncs hydration)
- Profile, goals, and settings
- Coach conversation history
- Widget snapshots

**Delete All Data** (Settings) wipes app storage only. It does not remove records you previously synced to Health Connect - manage those in the Health Connect app if needed.

## Network requests

NoFUD may contact external services only when you use a feature that requires it:

| Feature | What is sent | Where |
|---------|----------------|-------|
| AI food analysis / Coach | Meal text, images, or chat context you submit | Your configured AI provider |
| Barcode scan | Scanned barcode | Open Food Facts public API |
| Health Connect | Nutrition, weight, body fat, height (write); sleep, resting heart rate, hydration, steps, energy (read) if you enable sync | Google Health Connect on-device |
| App update check | Installed version | Not used (manual updates via Codeberg releases) |

Optional **background sync** (off by default) only reads from Health Connect on-device on a periodic schedule; it sends nothing off the device.

NoFUD does not sell or share your health data for advertising.

## Meal sharing

Shared meals use a `nofud://` deep link with base64-encoded nutrition JSON. Links you send leave your device through the app or messenger you choose. NoFUD can **import** meals shared from upstream Fud AI (`fudai://` links) but does not link outbound shares to third-party websites.

## Upstream

NoFUD is based on open-source software by Apoorv Darshan under the MIT License. See [NOTICE](NOTICE) and [LICENSE](LICENSE).

## Contact

Source and issues: https://codeberg.org/fitguy/NoFUD
