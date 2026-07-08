# NoFUD

<img src="android/app/src/main/res/drawable-nodpi/ic_logo_teal.png" alt="NoFUD teal icon" width="120" />

**Ad-free AI calorie tracker for Android** - a privacy-focused app based on [Fud AI](https://github.com/apoorvdarshan/fud-ai).

NoFUD keeps the core Fud AI experience while removing monetization and tracking surface area.  
Snap, speak, scan, or type your food using your own AI provider key - no account required, no cloud sync, **no ads**.

Home: https://codeberg.org/fitguy/NoFUD

## Install (Android)

No Play Store/F-Droid yet. Install NoFUD using one of these options:

Install note: if multiple APKs are listed in a release, use `arm64-v8a` for most modern phones, `armeabi-v7a` for older 32-bit devices, and `x86_64` for emulators/Chromebooks. Use the universal APK only when unsure.

[![Get it on Obtainium](https://img.shields.io/badge/Get%20it%20on-Obtainium-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fadd%2Fhttps%3A%2F%2Fcodeberg.org%2Ffitguy%2Fnofud)

- **Obtainium (recommended):** tap the banner above, then confirm in Obtainium.
- **Direct APK:** download from [Codeberg Releases](https://codeberg.org/fitguy/nofud/releases).
- **Fallback for manual Obtainium add:** paste `https://codeberg.org/fitguy/nofud` into Obtainium's **Add App** screen.

- Release package: `org.codeberg.fitguy.nofud`
- Debug package (from source): `org.codeberg.fitguy.nofud.debug`

## Why NoFUD

NoFUD focuses on a few high-impact changes:

- **Diet modes, including keto mode**
- **Better entry flow** with `AddFoodSheet` and improved camera/text/photo logging
- **Opinionated UX/UI refinements** for clearer nutrient display and smoother day-to-day use
- **Smaller Android package footprint** via image optimization and asset cleanup
- **No ads** with AdMob removed

## Feature and compatibility status

NoFUD keeps the core Android features from Fud AI:

- AI food logging (camera, text, voice, barcode, manual entry)
- AI Coach chat
- Diet modes (including keto carb mode)
- Workouts library
- Health Connect sync and restore behavior
- Home-screen widgets
- Diary export (JSON / Markdown / CSV)
- Meal sharing and import
- 15-language localization

| Area | Fud AI | NoFUD |
|---|---|---|
| Android AI calorie tracking app | Yes | Yes |
| Banner ads (AdMob) | Yes | **Removed** |
| Analytics/tracking SDKs | None | None |
| Diet mode / keto carb mode | Not in Fud AI | **Added in NoFUD** |
| Improved add-entry flow | Baseline Fud AI flow | **Enhanced (`AddFoodSheet` + logging UX refinements)** |
| APK/package size optimization | Varies by build/assets | **Improved via asset optimization + cleanup** |
| Opinionated UX/UI updates | Baseline | **Expanded in NoFUD** |

See [CHANGELOG.md](CHANGELOG.md) for version-by-version details.

## Package size comparison (`fud-ai` vs `NoFUD`)

Latest release assets (APK files) show:

| APK artifact | Fud AI (`android-v3.0.4`) | NoFUD (`v1.3.0`) |
|---|---:|---:|
| Universal APK | 121.4 MB | **45.0 MB** |
| arm64-v8a APK | n/a (single APK published) | **30.2 MB** |
| armeabi-v7a APK | n/a (single APK published) | **28.6 MB** |
| x86_64 APK | n/a (single APK published) | **31.1 MB** |

- Universal APK delta: NoFUD is ~76.4 MB smaller (~62.9% smaller).
- Numbers are from release asset byte sizes converted with `1 MiB = 1,048,576 bytes`.
- Sources: [Fud AI releases](https://github.com/apoorvdarshan/fud-ai/releases) and [NoFUD releases](https://codeberg.org/fitguy/NoFUD/releases).

## Performance note

On our Android debug perf baseline, recent Progress-screen optimizations reduced worst-frame latency from about ~1.1s to ~0.5s and significantly lowered jank in the captured navigation/render path.

## Fork attribution

NoFUD is based on Fud AI (https://github.com/apoorvdarshan/fud-ai).

- Copyright (c) 2026 Apoorv Darshan - [MIT License](LICENSE)
- Modifications Copyright (c) 2026 fitguy - MIT License

See [NOTICE](NOTICE) and [ASSET_CREDITS.md](ASSET_CREDITS.md).

## Privacy

See [PRIVACY.md](PRIVACY.md).

## Development docs

- Build from source: [DEVELOPMENT.md](DEVELOPMENT.md)
- Maintainer release flow: [RELEASE.md](RELEASE.md)
- Performance notes: [PERFORMANCE.md](PERFORMANCE.md)

## License

MIT - see [LICENSE](LICENSE).
