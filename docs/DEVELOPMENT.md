# Development (Android)

Requirements: JDK 17+, Android SDK 36.

Distribution: single F-Droid / Codeberg build. The former **`play` flavor is disabled**. See [`DISTRIBUTION.md`](DISTRIBUTION.md).

## WSL / Nix (devenv)

If you use home-manager with devenv and direnv (as on WSL2 Arch + Nix), the repo provides a project environment with JDK 17 and Android SDK 36:

```bash
cd Chompass
direnv allow          # first time only
devenv update         # first time only; downloads SDK (~1-2 GB without emulator)
devenv shell          # or rely on direnv auto-load after allow
# optional (also done on devenv enterShell): ./scripts/install_git_hooks.sh
```

`devenv shell` / direnv sets `core.hooksPath` to `scripts/git-hooks` (see `scripts/install_git_hooks.sh`).

Build (inside the devenv shell):

```bash
build-debug
build-release
```

Debug APK package: `app.chompass.debug` (`assembleDebug`).

Or from outside the shell:

```bash
devenv tasks run build:debug
devenv tasks run build:release
devenv shell -- build-debug
```

In shells where direnv does not load, run builds explicitly:

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

Install the debug APK (side-by-side package `app.chompass.debug`):

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

First launch walks through onboarding. A free Gemini key is available at https://aistudio.google.com/apikey - configure any supported provider under **Settings -> AI Access**.

## Project website (Codeberg Pages)

Hugo site sources live in [`website/`](../website/). Screenshots are mounted from [`screenshots/`](screenshots/) at build time. Live URL: [chompass.app](https://chompass.app/).

Outreach checklist (AlternativeTo, Lemmy, etc.): [`WEB_PRESENCE.md`](WEB_PRESENCE.md).

### Codeberg repo settings (manual)

In the Codeberg UI under **Settings**:

1. **Website** (or project description link): `https://chompass.app/`
2. **Description** (About; paste): `Free, open-source calorie tracker. No ads, no trackers. Android app and installable browser PWA (any modern browser; Chromium works best). Community fork of Fud AI with BYOK food logging, keto modes, and open exports.`
3. **Topics** (suggested): `pwa`, `web`, `android`, `calorie-tracker`, `privacy`, `foss`, `health-connect`, `keto`

Leave the site URL in the **Website** field only (do not repeat it in the description).

### Preview / build

```bash
site-serve   # http://localhost:1313/Chompass/
site-build   # writes website/public/
# or:
devenv tasks run site:serve
devenv tasks run site:build
```

Keep `website/hugo.toml` `params.version` in sync with `versionName` in `android/app/build.gradle.kts` (`devenv tasks run release:check-metadata` verifies this).

### One-time webhook (Codeberg UI)

1. Repo **Settings → Webhooks → Add webhook**
2. Type: **Forgejo**
3. Target URL: `https://chompass.app/`
4. Branch filter: `pages`
5. Save (do **not** use “Test delivery”; it fails by design)

### Deploy

Pushes use the SSH Host alias **`codeberg-fitguy`** (see `~/.ssh/config`) so Codeberg authenticates as **fitguy**, not KewLE (bare `codeberg.org` may pick the KewLE key first).

**On release:** [`publish_release.sh`](../scripts/publish_release.sh) redeploys Pages automatically after uploading APKs (pass `--skip-pages` to skip).

**Ad hoc** (copy/logo/content changes without a release):

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

Regenerate the social card after logo changes:

```bash
uv run --with pillow python scripts/generate_og_image.py
```

## App icon

Chompass uses original launcher and splash artwork (distinct from Fud AI). Regenerate themed variants with:

```bash
uv run --with pillow python scripts/generate_icons.py
```

Edit `scripts/chompass_icon_master.png` before running the command. See [ASSET_CREDITS.md](ASSET_CREDITS.md).

## On-device LLM smoke test (debug only)

Proof-of-concept for **Gemma 4 E2B-it** via LiteRT-LM on real hardware (validated on Pixel 9a / GrapheneOS). Not integrated into production AI dispatch.

Full workflow: model push, intent extras, GPU/CPU backends, latency results, known issues:

**[ON_DEVICE_LLM.md](ON_DEVICE_LLM.md)**
