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

## Project website (Codeberg Pages)

Hugo site sources live in [`website/`](website/). Screenshots are mounted from [`docs/screenshots/`](docs/screenshots/) at build time. Live URL after deploy: [fitguy.codeberg.page/NoFUD](https://fitguy.codeberg.page/NoFUD/).

```bash
site-serve   # http://localhost:1313/NoFUD/
site-build   # writes website/public/
# or:
devenv tasks run site:serve
devenv tasks run site:build
```

Deploy is automatic on pushes to `main` that touch `website/**`, `docs/screenshots/**`, or [`.forgejo/workflows/pages.yml`](.forgejo/workflows/pages.yml), via the `git-pages` Forgejo Action. Enable **Actions** once under Codeberg repo Settings → Units. Update product copy in `website/content/` when messaging changes.

## App icon

NoFUD uses original launcher and splash artwork (distinct from Fud AI). Regenerate themed variants with:

```bash
uv run --with pillow python scripts/generate_icons.py
```

Edit `scripts/nofud_icon_master.png` before running the command. See [ASSET_CREDITS.md](ASSET_CREDITS.md).

## On-device LLM smoke test (debug only)

Proof-of-concept for **Gemma 4 E2B-it** via LiteRT-LM on real hardware (validated on Pixel 9a / GrapheneOS). Not integrated into production AI dispatch.

Full workflow: model push, intent extras, GPU/CPU backends, latency results, known issues:

**[docs/ON_DEVICE_LLM.md](docs/ON_DEVICE_LLM.md)**
