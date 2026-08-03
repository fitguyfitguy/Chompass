# Development (Android)

Requirements: JDK 17+, Android SDK 36.

Distribution: single F-Droid / Codeberg build. The former **`play` flavor is disabled**. See [`DISTRIBUTION.md`](DISTRIBUTION.md).

Contributor tiers and the day-to-day command table: [`CONTRIBUTING.md`](../CONTRIBUTING.md).

## WSL / Nix (devenv)

If you use home-manager with devenv and direnv (as on WSL2 Arch + Nix), the repo provides a project environment with JDK 17 and Android SDK 36:

```bash
cd Chompass
direnv allow          # first time only
devenv update         # first time only; downloads SDK (~1-2 GB without emulator)
devenv shell          # or rely on direnv auto-load after allow
# optional (also done on devenv enterShell): ./scripts/install_git_hooks.sh
```

`devenv shell` / direnv sets `core.hooksPath` to `scripts/git-hooks` (see `scripts/install_git_hooks.sh`). Packages include `uv` for parity/asset Python scripts.

Build (inside the devenv shell):

```bash
build-debug
install-debug    # assembleDebug + Windows adb install + common seed launch
build-release
```

Debug APK package: `app.chompass.debug` (`assembleDebug`).

Or from outside the shell (prefer **tasks** as the documented entry points):

```bash
devenv tasks run build:debug
devenv tasks run build:release
devenv tasks run ci:verify      # Android unit tests + parity
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

Install the debug APK (side-by-side package `app.chompass.debug`) and launch with the common seed extras:

```bash
./scripts/install_debug.sh            # build + install + seed launch (WSL → Windows adb)
install-debug                         # same, inside devenv shell
./scripts/install_debug.sh --no-build # skip Gradle; install existing APK
./scripts/install_debug.sh --no-seed  # build + install + plain launch
./scripts/install_debug.sh --reseed   # skip build/install; force-stop + seed again
```

**Native Linux / macOS:** use host `adb` on the default port. The `ANDROID_ADB_SERVER_PORT=5038` setting and Windows `adb.exe` paths in this repo are only for the maintainer’s WSL2 + Windows USB split — ignore them if your device is visible to local `adb devices`.

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

Chompass uses original launcher and splash artwork (distinct from Fud AI). The editable source is the SVG mark `scripts/assets/chompass_icon_mark.svg`. Regenerate themed variants with:

```bash
uv run --with pillow python scripts/generate_icons.py
```

Requires `resvg` on `PATH`, or `nix` so the script can run `nix shell nixpkgs#resvg -c resvg`. The script rasterizes the SVG at 2048px, builds a programmatic squircle mask, then writes:

- Density `mipmap-*` PNGs (legacy pre-masked fallbacks) and splash `ic_logo*` drawables
- Adaptive launcher layers: `drawable-nodpi/ic_launcher_foreground.png`, `ic_launcher_monochrome.png`, per-theme `drawable/ic_launcher_background_*.xml`, and `mipmap-anydpi-v26/ic_launcher_*.xml` (system icon shape + Material You themed icons)
- PWA / F-Droid / website store icons (still pre-shaped squircles)
- Generated teal preview `scripts/chompass_icon_master.png` (do not edit)

Edit the SVG (or regenerate it from the CC0 needle via `uv run python scripts/assets/build_icon_mark.py`) before running the command. See [ASSET_CREDITS.md](ASSET_CREDITS.md).

## On-device LLM smoke test (debug only)

Proof-of-concept for **Gemma 4 E2B-it** via LiteRT-LM on real hardware (validated on Pixel 9a / GrapheneOS). Not integrated into production AI dispatch.

Full workflow: model push, intent extras, GPU/CPU backends, latency results, known issues:

**[ON_DEVICE_LLM.md](ON_DEVICE_LLM.md)**
