# NoFUD

**Ad-free AI calorie tracker for Android** — a privacy-focused fork of [Fud AI](https://github.com/apoorvdarshan/fud-ai).

Snap, speak, or type your food. Bring your own AI provider key. Everything stays on your device — no accounts, no cloud sync, **no ads**.

Home: https://codeberg.org/fitguy/NoFUD

## What changed from Fud AI

| | Fud AI | NoFUD |
|---|--------|-------|
| Banner ads | Yes (AdMob) | **Removed** |
| Analytics / tracking SDKs | None | None |
| Package ID | `com.apoorvdarshan.calorietracker` | `org.codeberg.fitguy.nofud` |
| Source home | GitHub | Codeberg |

All other core features are preserved: AI food logging, Coach, workouts library, Health Connect, widgets, diary export, meal sharing, and 15 languages.

See [CHANGELOG.md](CHANGELOG.md) for full version history.

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

Install the debug APK (side-by-side package `org.codeberg.fitguy.nofud.debug`):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

First launch walks through onboarding. A free Gemini key is available at https://aistudio.google.com/apikey — configure any supported provider under **Settings → AI Access**.

## App icon

NoFUD uses original launcher and splash artwork (distinct from upstream Fud AI). Regenerate themed variants with `uv run --with pillow python scripts/generate_icons.py` after editing `scripts/nofud_icon_master.png`. See [ASSET_CREDITS.md](ASSET_CREDITS.md).

## Exercise data

The Workouts tab bundles the [Free Exercise DB](https://github.com/yuhonas/free-exercise-db) dataset under `android/app/src/main/assets/exercises/` (~99 MB). License: see `exercises/LICENSE.md` in that folder.

## License

MIT — see [LICENSE](LICENSE).
