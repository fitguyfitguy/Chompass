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

Hugo site sources live in [`website/`](website/). Screenshots are mounted from [`docs/screenshots/`](docs/screenshots/) at build time. Live URL: [fitguy.codeberg.page/NoFUD](https://fitguy.codeberg.page/NoFUD/).

**No Forgejo Actions runner required.** Deploy is local Hugo build + force-push to an orphan `pages` branch; Codeberg Pages picks it up via a repo webhook ([docs](https://docs.codeberg.org/codeberg-pages/)).

### Preview / build

```bash
site-serve   # http://localhost:1313/NoFUD/
site-build   # writes website/public/
# or:
devenv tasks run site:serve
devenv tasks run site:build
```

### One-time webhook (Codeberg UI)

1. Repo **Settings → Webhooks → Add webhook**
2. Type: **Forgejo**
3. Target URL: `https://fitguy.codeberg.page/NoFUD/`
4. Branch filter: `pages`
5. Save (do **not** use “Test delivery” — it fails by design)

### Deploy

Pushes use the SSH Host alias **`codeberg-fitguy`** (see `~/.ssh/config`) so Codeberg authenticates as **fitguy**, not KewLE (the agent often offers the KewLE key first for bare `codeberg.org`).

```bash
./scripts/deploy_pages.sh          # build + force-push pages
./scripts/deploy_pages.sh --dry-run
# or: site-deploy / devenv tasks run site:deploy
```

Optional overrides: `PAGES_SSH_HOST`, `PAGES_PUSH_URL`, `PAGES_REMOTE`, `PAGES_BRANCH`.

For day-to-day git on this repo, prefer the same host in `origin`:

```bash
git remote set-url origin ssh://git@codeberg-fitguy/fitguy/nofud.git
```

Update product copy in `website/content/` when messaging changes, then run `deploy_pages.sh` again.

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
