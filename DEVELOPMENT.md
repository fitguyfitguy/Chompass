# Development (Android)

Requirements: JDK 17+, Android SDK 36.

Distribution: single F-Droid / Codeberg build. The former **`play` flavor is disabled**. See [`docs/DISTRIBUTION.md`](docs/DISTRIBUTION.md).

## WSL / Nix (devenv)

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

Debug APK package: `org.codeberg.fitguy.nofud.debug` (`assembleDebug`).

Or from outside the shell:

```bash
devenv tasks run build:debug
devenv tasks run build:release
devenv shell -- build-debug
```

In Cursor/agent shells where direnv does not load, run builds explicitly:

```bash
devenv shell bash -lc 'cd android && ./gradlew :app:assembleDebug'
```

For debug-scoped unit tests while iterating:

```bash
devenv shell bash -lc 'cd android && ./gradlew :app:testDebugUnitTest'
```

Reserve `debug2` (`assembleDebug2`) for side-by-side installs only.

If `platforms;android-36.1` is missing from nixpkgs, add the android-nixpkgs input:

```bash
devenv inputs add android-nixpkgs github:tadfisher/android-nixpkgs --follows nixpkgs
devenv update
```

## Generic Gradle build

```bash
cd android
./gradlew :app:assembleDebug
```

Install the debug APK (side-by-side package `org.codeberg.fitguy.nofud.debug`):

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

First launch walks through onboarding. A free Gemini key is available at https://aistudio.google.com/apikey - configure any supported provider under **Settings -> AI Access**.

## App icon

NoFUD uses original launcher and splash artwork (distinct from Fud AI). Regenerate themed variants with:

```bash
uv run --with pillow python scripts/generate_icons.py
```

Edit `scripts/nofud_icon_master.png` before running the command. See [ASSET_CREDITS.md](ASSET_CREDITS.md).

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

## On-device LLM smoke test (debug only)

Proof-of-concept for **Gemma 4 E2B-it** via LiteRT-LM on real hardware (validated on Pixel 9a / GrapheneOS). Not integrated into production AI dispatch.

Full workflow: model push, intent extras, GPU/CPU backends, latency results, known issues:

**[docs/ON_DEVICE_LLM.md](docs/ON_DEVICE_LLM.md)**
