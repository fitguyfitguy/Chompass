# NoFUD

<img src="android/app/src/main/res/drawable-nodpi/ic_logo_teal.png" alt="NoFUD teal icon" width="120" />

**Ad-free AI calorie tracker for Android** - a privacy-focused app based on [Fud AI](https://github.com/apoorvdarshan/fud-ai).

NoFUD keeps the core Fud AI experience while removing monetization and tracking surface area.  
Snap, speak, scan, or type your food using your own AI provider key - no account required, no cloud sync, **no ads**.

> **Platform status:** NoFUD is currently **Android-only**. iOS is not supported yet.

Home: https://codeberg.org/fitguy/NoFUD

## Screenshots

Material 3 **dark theme** (light theme is also available). Images in [`docs/screenshots/`](docs/screenshots/) are regenerated automatically during [`release:package`](RELEASE.md#release-screenshots-optional) whenever UI previews change.

<table>
  <tr>
    <td align="center">
      <img src="docs/screenshots/home.png" width="200" alt="Home screen in dark theme" /><br />
      <sub><b>Home</b> — calorie ring, macro bars, and today's meal log</sub>
    </td>
    <td align="center">
      <img src="docs/screenshots/progress.png" width="200" alt="Progress screen in dark theme" /><br />
      <sub><b>Progress</b> — weight &amp; body-fat charts, calorie history, goals</sub>
    </td>
    <td align="center">
      <img src="docs/screenshots/add-food.png" width="200" alt="Add food sheet in dark theme" /><br />
      <sub><b>Add food</b> — photo, voice, barcode, manual, and saved meals</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/screenshots/coach.png" width="200" alt="AI Coach in dark theme" /><br />
      <sub><b>Coach</b> — on-device AI chat with your own provider key</sub>
    </td>
    <td align="center" colspan="2">
      <img src="docs/screenshots/settings.png" width="200" alt="Settings in dark theme" /><br />
      <sub><b>Settings</b> — profile, diet modes (incl. keto), Health Connect, themes</sub>
    </td>
  </tr>
</table>

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

## Health ecosystem connectivity

NoFUD talks to the rest of your health setup through **Android Health Connect** — no vendor SDKs, no accounts. Anything that syncs into Health Connect works with NoFUD, including fully free/open-source bridges:

| Companion | What it brings | Notes |
|-----------|----------------|-------|
| [Gadgetbridge](https://gadgetbridge.org/) | Steps, exercise sessions, weight from wearables (Huawei, Honor, Amazfit, Mi Band, Pebble, …) | FOSS, on F-Droid; enable *Settings → External Integrations → Health Connect* in Gadgetbridge |
| [openScale](https://f-droid.org/en/packages/com.health.openscale/) | Weight and body composition from Bluetooth scales | FOSS, on F-Droid |
| Samsung Health, Fitbit, Withings, … | Weight, activity, energy burn | Via each app's Health Connect sync |

What syncs:

- **In:** weight and body fat (live, incremental), meals logged by other apps (live), steps + exercise (shown on the Progress tab), active/total energy burn (feeds the Energy Burn goal anchor)
- **Out:** every meal you log, as a full `NutritionRecord` (macros + micronutrients), plus weight and body fat entries

Huawei Health users: pair your wearable with Gadgetbridge (which supports Huawei devices natively) instead of the Huawei Health app — the proprietary Huawei Health Kit requires an HMS account and is not supported.

## Migration: FUD-AI -> NoFUD

Two practical migration paths are supported today:

- **Path A (file-based):** in FUD-AI, export your food diary as JSON. In NoFUD, open `Settings` and use `Import Food Diary JSON`.
- **Path B (Health Connect):** enable Health Connect in both apps and grant read permissions so historical data can be restored from Health Connect.

Before switching apps:

- Export a food diary JSON from FUD-AI if you want a file backup/import path.
- Optionally sync your latest entries to Health Connect in FUD-AI.

What transfers today:

- **Food log:** JSON import and/or Health Connect restore.
- **Weight + body fat:** Health Connect read import and sync behavior.

Current limitation:

- No dedicated **weight/body-composition file import/export** exists yet; those metrics currently migrate through Health Connect.

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

- `fud-ai` (`android-v3.0.4`) universal APK: **121.4 MB**
- `NoFUD` (`v1.4.0`) release APKs: **~25 MB** (universal + per-ABI)
- Size delta (universal): NoFUD is about **96.4 MB smaller** (~**79% smaller**)
- Sources: [Fud AI releases](https://github.com/apoorvdarshan/fud-ai/releases), [NoFUD releases](https://codeberg.org/fitguy/NoFUD/releases)

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
