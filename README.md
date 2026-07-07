# NoFUD

**Ad-free AI calorie tracker for Android** — a privacy-focused fork of [Fud AI](https://github.com/apoorvdarshan/fud-ai).

Snap, speak, or type your food. Bring your own AI provider key. Everything stays on your device — no accounts, no cloud sync, **no ads**.

Home: https://codeberg.org/fitguy/NoFUD

## What changed from Fud AI

| | Fud AI | NoFUD |
|---|--------|-------|
| Banner ads | Yes (AdMob) | **Removed** |
| Analytics / tracking SDKs | None | None |
| Package ID | `com.apoorvdarshan.calorietracker` | `com.fitguy.nofud` |
| Source home | GitHub | Codeberg |

All other core features are preserved: AI food logging, Coach, workouts library, Health Connect, widgets, diary export, meal sharing, and 15 languages.

## Fork attribution

NoFUD is a fork of Fud AI (https://github.com/apoorvdarshan/fud-ai).

- Copyright (c) 2026 Apoorv Darshan — [MIT License](LICENSE)
- Modifications Copyright (c) 2026 fitguy — MIT License

See [NOTICE](NOTICE) and [ASSET_CREDITS.md](ASSET_CREDITS.md).

## Privacy

See [PRIVACY.md](PRIVACY.md).

## Build (Android)

Requirements: JDK 17+, Android SDK 36.

```bash
cd android
./gradlew :app:assembleDebug
```

Install the debug APK (side-by-side package `com.fitguy.nofud.debug`):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

First launch walks through onboarding. A free Gemini key is available at https://aistudio.google.com/apikey — configure any supported provider under **Settings → AI Access**.

## App icon

The current launcher icon is a temporary NoFUD placeholder (distinct from upstream Fud AI artwork). See [ASSET_CREDITS.md](ASSET_CREDITS.md).

## Exercise data

The Workouts tab bundles the [Free Exercise DB](https://github.com/yuhonas/free-exercise-db) dataset under `android/app/src/main/assets/exercises/` (~99 MB). License: see `exercises/LICENSE.md` in that folder.

## License

MIT — see [LICENSE](LICENSE).
