# NoFUD

**Ad-free AI calorie tracker for Android** - a privacy-focused fork of [Fud AI](https://github.com/apoorvdarshan/fud-ai).

Snap, speak, or type your food. Bring your own AI provider key. Everything stays on your device - no accounts, no cloud sync, **no ads**.

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

- Copyright (c) 2026 Apoorv Darshan - [MIT License](LICENSE)
- Modifications Copyright (c) 2026 fitguy - MIT License

See [NOTICE](NOTICE) and [ASSET_CREDITS.md](ASSET_CREDITS.md).

## Privacy

See [PRIVACY.md](PRIVACY.md).

## Build (Android)

Requirements: JDK 17+, Android SDK 36.

### WSL / Nix (devenv)

If you use home-manager with devenv and direnv (as on WSL2 Arch + Nix), the repo provides a project environment with JDK 17 and Android SDK 36:

```bash
cd NoFUD
direnv allow          # first time only
devenv update         # first time only; downloads SDK (~1-2 GB without emulator)
devenv shell          # or rely on direnv auto-load after allow
```

Build (inside the devenv shell):

```bash
build-debug
build-release
```

Debug APK package: `org.codeberg.fitguy.nofud.debug` (`assemblePlayDebug`).

Or from outside the shell:

```bash
devenv tasks run build:debug
devenv tasks run build:release
devenv shell -- build-debug
```

In Cursor/agent shells where direnv does not load, run builds explicitly:

```bash
devenv shell -c 'cd android && ./gradlew :app:assemblePlayDebug'
```

If `platforms;android-36.1` is missing from nixpkgs, add the android-nixpkgs input:

```bash
devenv inputs add android-nixpkgs github:tadfisher/android-nixpkgs --follows nixpkgs
devenv update
```

### Generic

```bash
cd android
./gradlew :app:assembleDebug
```

Install the debug APK (side-by-side package `org.codeberg.fitguy.nofud.debug`):

```bash
adb install -r android/app/build/outputs/apk/play/debug/app-play-debug.apk
```

First launch walks through onboarding. A free Gemini key is available at https://aistudio.google.com/apikey - configure any supported provider under **Settings → AI Access**.

## Install (release APK)

Download the latest signed APK from [Codeberg Releases](https://codeberg.org/fitguy/nofud/releases). On your phone, allow **Install unknown apps** for the browser or file manager you use to open the APK.

- Release package: `org.codeberg.fitguy.nofud`
- Debug builds (from source) install side-by-side as `org.codeberg.fitguy.nofud.debug`

```bash
adb install -r NoFUD-1.0.0.apk
```

See [RELEASE.md](RELEASE.md) for maintainer build and release steps.

## App icon

NoFUD uses original launcher and splash artwork (distinct from upstream Fud AI). Regenerate themed variants with `uv run --with pillow python scripts/generate_icons.py` after editing `scripts/nofud_icon_master.png`. See [ASSET_CREDITS.md](ASSET_CREDITS.md).

## Exercise data

The Workouts tab bundles the [Free Exercise DB](https://github.com/yuhonas/free-exercise-db) dataset under `android/app/src/main/assets/exercises/` (~20 MB of optimized WebP photos plus `exercises.json`). License: see `exercises/LICENSE.md` in that folder.

Regenerate optimized exercise photos after updating upstream JPEGs:

```bash
uv run --with pillow python scripts/optimize_exercise_images.py
```

Validate the bundled image budget (fails if total WebP size exceeds 25 MB or any image is wider/taller than 800 px):

```bash
uv run --with pillow python scripts/optimize_exercise_images.py --check-only
```

## License

MIT - see [LICENSE](LICENSE).
