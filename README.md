# NoFUD

![NoFUD teal icon](android/app/src/main/res/drawable-nodpi/ic_logo_teal.png)

**Ad-free AI calorie tracker for Android** - a privacy-focused app based on [Fud AI](https://github.com/apoorvdarshan/fud-ai).

NoFUD keeps the core Fud AI experience while removing monetization and tracking surface area.  
Snap, speak, scan, or type your food using your own AI provider key - no account required, no cloud sync, **no ads**.

Home: https://codeberg.org/fitguy/NoFUD

## Why NoFUD

NoFUD focuses on a few high-impact product changes:

- **Diet modes, including keto mode** 
- **Better entry flow** with `AddFoodSheet` and improved camera/text/photo logging
- **Opinionated UX/UI refinements** for clearer nutrient display and smoother day-to-day use
- **Lighter app package** via image optimization and asset cleanup
- **No ads** with AdMob removed

## Feature and compatibility status

NoFUD keeps the core Android features i love from Fud AI:

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

## Fork attribution

NoFUD is based on Fud AI (https://github.com/apoorvdarshan/fud-ai).

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

- **Quick add to Obtainium (recommended):** [![Get it on Obtainium](https://img.shields.io/badge/Get%20it%20on-Obtainium-2ea043?logo=android&logoColor=white)](obtainium://add/codeberg.org/fitguy/nofud)
- **If the deep link is blocked by your browser:** in Obtainium tap **Add App** and paste `https://codeberg.org/fitguy/nofud`

- Release package: `org.codeberg.fitguy.nofud`
- Debug builds (from source) install side-by-side as `org.codeberg.fitguy.nofud.debug`

```bash
adb install -r NoFUD-1.0.0.apk
```

See [RELEASE.md](RELEASE.md) for maintainer build and release steps.

## App icon

NoFUD uses original launcher and splash artwork (distinct from Fud AI). Regenerate themed variants with `uv run --with pillow python scripts/generate_icons.py` after editing `scripts/nofud_icon_master.png`. See [ASSET_CREDITS.md](ASSET_CREDITS.md).

## Exercise data

The Workouts tab bundles the [Free Exercise DB](https://github.com/yuhonas/free-exercise-db) dataset under `android/app/src/main/assets/exercises/` (~20 MB of optimized WebP photos plus `exercises.json`). License: see `exercises/LICENSE.md` in that folder.

Regenerate optimized exercise photos after updating source JPEGs:

```bash
uv run --with pillow python scripts/optimize_exercise_images.py
```

Validate the bundled image budget (fails if total WebP size exceeds 25 MB or any image is wider/taller than 800 px):

```bash
uv run --with pillow python scripts/optimize_exercise_images.py --check-only
```

## License

MIT - see [LICENSE](LICENSE).
