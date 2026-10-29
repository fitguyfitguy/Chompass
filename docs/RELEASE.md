# Releasing Chompass

Maintainer steps for tagging and publishing an Android release on Codeberg.

## One-time setup: signing key

Generate the release keystore (back it up offline - losing it blocks updates):

```bash
cd android
keytool -genkey -v -keystore nofud-release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias nofud
cp keystore.properties.template keystore.properties
# storeFile=../nofud-release.jks, fill storePassword / keyPassword / keyAlias
```

**Signing key SHA-256** (for F-Droid `AllowedAPKSigningKeys`):

```
2694994fcb99d70e2c3978f770384dcf3091a310d9c56a23d4a145f150658dcf
```

## Build and package

One command runs Android unit tests, **cross-app parity** (PWA tests + typecheck +
contract schemas), builds release APKs, copies the **universal** APK into
`releases/`, and writes `SHA256SUMS`:

```bash
devenv tasks run release:package
```

Equivalent:

```bash
./scripts/package_release.sh
```

Parity alone (no APK build):

```bash
devenv tasks run release:check-parity
# equivalent: ./scripts/check_parity.sh
```

Useful flags:

```bash
./scripts/package_release.sh --check-only      # validate only; no build/package
./scripts/package_release.sh --skip-build        # package existing Gradle outputs
./scripts/package_release.sh --check-metadata    # also verify CHANGELOG + fdroid metadata
```

Codeberg uploads **universal only** (`Chompass-fdroid-<version>.apk` + `SHA256SUMS`). That APK is ARM-only (`arm64-v8a` + `armeabi-v7a`); x86/x86_64 stay in debug for emulators. Per-ABI splits may still be built locally but are not published.
## Tag and publish on Codeberg

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`
2. Update `docs/CHANGELOG.md` (`## [Unreleased]` → new `## [X.Y.Z] - YYYY-MM-DD` section) and add `metadata/en-US/changelogs/<versionCode>.txt` (F-Droid `checkupdates` copies this; without it the version ships with no store changelog)
3. **Update design-doc status lines** for features shipping in this release: `Status: shipped in <x.y.z> (date)` in the affected design docs (`docs/*_DESIGN.md`) (convention documented in [`docs/README.md`](README.md))
4. Bump `website/hugo.toml` `params.version` (same as `versionName`)
5. Optional: sync `docs/fdroid/app.chompass.yml` and run `devenv tasks run release:check-metadata`
6. Commit, tag, push:

```bash
git tag -a v1.0.0 -m "Chompass 1.0.0 - initial public release"
git push origin v1.0.0
```

7. Publish APKs and redeploy the project site:

```bash
# One-time: create a token at https://codeberg.org/user/settings/applications
# Scopes: read:user + write:repository (repo access: this repo or all)
export CODEBERG_TOKEN='paste-token-here'
./scripts/publish_release.sh 1.0.0
# or: RELEASE_VERSION=1.0.0 devenv tasks run release:publish
```

**Token split: use the release login, not the issue login.** The **release** token
lives in the gitignored `.env.local` as `CODEBERG_TOKEN` (scopes `read:user` +
`write:repository`) and is persisted to tea as the `codeberg-release` login:

```bash
nix shell nixpkgs#tea -c tea logins add -n codeberg-release -u https://codeberg.org -t "$CODEBERG_TOKEN"
```

Run the release scripts with `CODEBERG_LOGIN=codeberg-release` (both
`publish_release.sh` and `manage_release_assets.sh` honor it). The default tea
login `codeberg` (`~/.config/tea/config.yml`) is **issue-triage only**: it
deliberately lacks `write:repository` and must not be used to publish.

`publish_release.sh` uploads F-Droid APK assets + `SHA256SUMS`, pastes changelog notes, then runs [`deploy_pages.sh`](../scripts/deploy_pages.sh) so [chompass.app](https://chompass.app/) shows the new version. Use `--skip-pages` to skip the site step.

The former **`play` flavor is disabled**; see [`DISTRIBUTION.md`](DISTRIBUTION.md).

The script auto-runs `nix shell nixpkgs#tea` / `nixpkgs#hugo` when those tools are not on PATH (e.g. outside devenv).

Stable download URL pattern: `https://codeberg.org/fitguy/chompass/releases/download/v<version>/Chompass-fdroid-<version>.apk`

### Beta channel

A beta is the same packaged APK, published as a Codeberg **prerelease** so it never
disturbs the stable channel:

- Tag `v<version>-beta.N` (F-Droid's `UpdateCheckMode: Tags ^v[0-9.]+$` ignores it)
- Asset `Chompass-beta-<version>.apk` (stable `Chompass-fdroid-*` URL pattern never matches)
- No `docs/fdroid/app.chompass.yml` / `metadata/` entry, no Pages deploy
- Same `versionCode` as the eventual stable: when the stable ships, F-Droid users
  update from the previous stable and beta testers are already on that code

```bash
export CODEBERG_LOGIN=codeberg-release
./scripts/publish_release.sh 5.6.0-beta.1 --prerelease
```

Notes come from the CHANGELOG section of the base version (`5.6.0` for
`5.6.0-beta.1`) with a beta preamble. When the beta graduates, re-tag the final
commit `v<version>` and publish normally; delete the beta tag if you want a clean
history.

### Codeberg storage quota

Codeberg applies a combined quota for **releases, packages, LFS, and attachments** (default **1.5 GiB** per user/org, separate from the 750 MiB git-repo limit).

**Policy:** keep the **last 3 to 5** Codeberg releases (each with universal APK + `SHA256SUMS` only, no per-ABI splits, no release screenshots by default). Older releases are pruned only when quota demands it; the release notes stay visible for recent versions.

**Symptoms:** `quota exceeded` from `tea`, or a release page with only some APKs attached.

**Before publishing**, check attachment usage and drop releases older than the last 5:

```bash
./scripts/manage_release_assets.sh list
./scripts/manage_release_assets.sh keep-n 5 -y
```

**On the kept release**, drop leftover ABI splits / play-flavor APKs if any:

```bash
./scripts/manage_release_assets.sh prune-abi-splits v1.14.10 -y
./scripts/manage_release_assets.sh prune-play-assets -y
```

**Publish** uploads universal APK → checksums. If a run stops mid-way, resume without recreating the release:

```bash
./scripts/publish_release.sh 1.10.0 --assets-only
```

**Screenshots:** `--with-screenshots` adds ~10 PNGs. Prefer committing `docs/screenshots/` for the README and skip attaching screenshots to Codeberg.

**Need more quota?** Libre projects can request an increase (no payment): [Codeberg-e.V./requests](https://codeberg.org/Codeberg-e.V./requests). Check current usage under user/org settings on Codeberg.
## Release screenshots (real device)

Published images are captured from the **real app on a physical device** — native
panel resolution, real Compose frames, real seeded data, and the real navigation
bar. The system status bar (clock, battery, VPN, notification icons) is cropped
from every exported PNG, so device state never reaches a published image. The
JVM `@PreviewTest` renderings stay as the deterministic pixel-regression harness.

```bash
devenv tasks run release:screenshots        # device capture + JVM preview refs
devenv tasks run device:screenshots         # device capture only
```

`./scripts/export_release_screenshots.sh` runs two lanes:

1. `./scripts/capture_device_screenshots.sh` — builds/installs `app.chompass.debug`,
   seeds `seed_full`, pins the brand teal + dark/light via the
   `screenshot_appearance` extra, forces the app locale to en-US for the capture,
   drives Home/Progress/Coach/Settings/deep links and sheets, and writes
   `release-screenshots/*.png` (`01-home-light.png` … `17-scale-metrics-dark.png`),
   the dark README copies in [`screenshots/`](screenshots/) (site gallery + F-Droid
   store source), and the F-Droid listing images in
   [`../metadata/en-US/images/phoneScreenshots/`](../metadata/en-US/images/phoneScreenshots/)
   (`1.png` … `9.png`, same dark set in listing order).
2. `./gradlew :app:updateDebugScreenshotTest` — refreshes
   `android/app/src/screenshotTestDebug/reference/`.
3. `./scripts/build_feature_shots.py` — composes the README feature shots in
   `docs/screenshots/features/` (three-phone hero collage + framed, cropped
   panels with feature captions). Crop regions live in that script as fractions
   of the capture, so a new panel is a config row; tune the fractions against
   the latest captures before committing.

Cadence and gate:

- `release:package` captures automatically after unit tests and parity. The
  device must be visible to adb (WSL adb or Windows `adb.exe`, auto-detected).
  Pass `--skip-screenshots` to package the committed PNGs instead.
- `devenv tasks run release:screenshots:check` fails when UI code changed after
  the last `docs/screenshots/` commit, so a UI change cannot ship with stale
  images. A capture leaves `docs/screenshots/` dirty — commit it with the release.
- Commit `docs/screenshots/` with the version bump. Preview references under
  `android/app/src/screenshotTestDebug/reference/` are committed too.

Options:

```bash
./scripts/capture_device_screenshots.sh --no-build --no-seed      # fast re-run
./scripts/capture_device_screenshots.sh --only home,progress     # subset
./scripts/capture_device_screenshots.sh --appearance dark        # one theme
./scripts/capture_device_screenshots.sh --out /tmp/shots         # review copy
./scripts/export_release_screenshots.sh --validate               # no writes
./scripts/export_release_screenshots.sh --check                  # freshness only
```

The capture restores the app locale and animation scales when it exits. Device
selection is automatic for one device; pass `--serial` when several are attached.
Attach PNGs to Codeberg only if release quota has room (see
[Codeberg storage quota](#codeberg-storage-quota)):

```bash
devenv tasks run release:screenshots
./scripts/publish_release.sh 1.8.0 --with-screenshots
```

## F-Droid follow-up

**Status:** Chompass is **live on F-Droid**: https://f-droid.org/packages/app.chompass/ (inclusion MR [fdroiddata!42984](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42984) merged).

For each release:

- Build release APKs (`assembleRelease`); no proprietary Play Core libraries. See [`DISTRIBUTION.md`](DISTRIBUTION.md)
- Add `metadata/en-US/changelogs/<versionCode>.txt` and keep the rest of `metadata/en-US/` on Codeberg `main` before F-Droid picks up the tag. `checkupdates` copies that file; without it the version ships with no store changelog
- Keep `docs/fdroid/app.chompass.yml` in sync with `versionName` / `versionCode` (`devenv tasks run release:check-metadata`)
- In the YAML `Builds:` block: **one** current version entry, `commit:` = **full git commit hash** of the release commit (not the tag name)
- F-Droid `checkupdates` opens the version-update MR automatically; do **not** open inclusion/update MRs yourself. If a bot MR needs help, reply in the GitLab web GUI.

See [`FDROID_SUBMISSION.md`](FDROID_SUBMISSION.md) for the listing checklist and build notes.

## Calculation changes

Before shipping formula, constant, or guardrail changes:

1. Update implementation and [`NutritionConstants.kt`](../android/app/src/main/java/app/chompass/models/NutritionConstants.kt) when applicable
2. Update [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) (formula register + policy table)
3. Update in-app strings (`settings_calc_*` in `strings.xml`) if user-visible
4. Add or adjust unit tests under `android/app/src/test/`
5. Document user impact in `docs/CHANGELOG.md`
6. Run `devenv shell bash -lc 'cd android && ./gradlew test'`

## APK size baselines (1.4.0)

- Release APKs with zxing-cpp barcode scanning: ~45 MB (universal and per-ABI splits are similar)

## litertlm-android size delta (1.13.0)

Measured via `devenv tasks run release:package` (full multi-ABI build, not `-PreleaseAbi`), comparing 1.12.0 → 1.13.0 (first release with `litertlm-android` as `implementation` for both flavors; see `build.gradle.kts`):

| APK | 1.12.0 | 1.13.0 | Delta |
|-----|--------|--------|-------|
| `arm64-v8a` | ~28.5 MB | ~38.0 MB | **+9.5 MB** |
| `x86_64` | ~28.8 MB | ~39.3 MB | **+10.5 MB** |
| `armeabi-v7a` | ~28.2 MB | ~28.7 MB | +0.5 MB (no native litertlm lib for this ABI; delta is just new Kotlin/resources) |
| universal | ~35.3 MB | ~54.8 MB | **+19.5 MB** (carries both arm64-v8a and x86_64 native libs) |

The model itself (~2.4 GB) is never bundled; it's downloaded at runtime into `filesDir/models/`. This delta is entirely `liblitertlm_jni.so` (R8/debug-symbol stripping couldn't strip it; see the "Unable to strip the following libraries" build warning). Users on `armeabi-v7a`-only devices (rare) get the on-device feature's UI but no working backend, since there's no native lib for that ABI; `OnDeviceCapability.isSupported()` already excludes non-`arm64-v8a`/`x86_64` ABIs from the capability gate, so the Settings picker correctly hides `ON_DEVICE` for them.
