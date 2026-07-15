# Releasing NoFUD

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

One command runs tests, checks bundled exercise images, builds both release flavors in a single Gradle invocation, copies 8 APKs to the repo root, and writes `SHA256SUMS`:

```bash
devenv tasks run release:package
```

Equivalent:

```bash
./scripts/package_release.sh
```

Useful flags:

```bash
./scripts/package_release.sh --check-only      # validate only; no build/package
./scripts/package_release.sh --skip-build        # package existing Gradle outputs
./scripts/package_release.sh --check-metadata    # also verify CHANGELOG + fdroid metadata
```

For a local single-ABI smoke-test release (not for publishing):

```bash
devenv shell bash -lc 'cd android && ./gradlew -PreleaseAbi=arm64-v8a :app:assemblePlayRelease :app:assembleFdroidRelease'
```

## Tag and publish on Codeberg

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`
2. Update `CHANGELOG.md` (`## [Unreleased]` → new `## [X.Y.Z] - YYYY-MM-DD` section)
3. Optional: sync `fdroid/org.codeberg.fitguy.nofud.yml` and run `devenv tasks run release:check-metadata`
4. Commit, tag, push:

```bash
git tag -a v1.0.0 -m "NoFUD 1.0.0 - initial public release"
git push origin v1.0.0
```

5. Create a release at https://codeberg.org/fitguy/nofud/releases
   - Attach all Play APK assets (`NoFUD-play-<version>.apk` + ABI APKs) and `SHA256SUMS`
   - Also attach F-Droid APK assets (`NoFUD-fdroid-<version>.apk` + ABI APKs) and `SHA256SUMS`
   - Paste changelog notes
   - Stable download URL pattern (Play flavor): `https://codeberg.org/fitguy/nofud/releases/download/v<version>/NoFUD-play-<version>.apk`
   - Stable download URL pattern (F-Droid flavor): `https://codeberg.org/fitguy/nofud/releases/download/v<version>/NoFUD-fdroid-<version>.apk`

   Or with [tea](https://codeberg.org/tea/tea):

```bash
# One-time: create a token at https://codeberg.org/user/settings/applications
# Scopes: read:user + write:repository (repo access: this repo or all)
export CODEBERG_TOKEN='paste-token-here'
./scripts/publish_release.sh 1.0.0
```

The script auto-runs `nix shell nixpkgs#tea` when `tea` is not on PATH (e.g. inside devenv).

### Codeberg storage quota

Codeberg applies a combined quota for **releases, packages, LFS, and attachments** (default **1.5 GiB** per user/org, separate from the 750 MiB git-repo limit). Eight signed APKs per release are ~250 MB, so accumulated release history can hit the cap.

**Symptoms:** `quota exceeded` from `tea`, or a release page with only some APKs attached.

**Before publishing**, check attachment usage:

```bash
./scripts/manage_release_assets.sh list
```

**Free space** by removing old per-ABI split APKs (universal APKs and `SHA256SUMS` stay — enough for direct installs and F-Droid):

```bash
./scripts/manage_release_assets.sh prune-abi-splits --before v1.6.0   # dry-run first with --dry-run
```

**Publish** uploads in batches (play → fdroid → checksums). If a run stops mid-way, resume without recreating the release:

```bash
./scripts/publish_release.sh 1.10.0 --assets-only
```

**Screenshots:** `--with-screenshots` adds ~10 PNGs on top of the APK set. Prefer committing `docs/screenshots/` for the README and skip attaching screenshots to Codeberg unless you have headroom.

**Need more quota?** Libre projects can request an increase (no payment): [Codeberg-e.V./requests](https://codeberg.org/Codeberg-e.V./requests). Check current usage under user/org settings on Codeberg.

## Release screenshots (optional)

JVM-based Compose screenshot previews render on the maintainer machine inside devenv — no phone or adb required.

```bash
devenv tasks run release:screenshots
```

This runs `./scripts/export_release_screenshots.sh`, which:

1. Renders `@PreviewTest` composables via `./gradlew :app:updatePlayDebugScreenshotTest`
2. Copies friendly PNGs to `release-screenshots/` (`01-home-light.png` … `10-add-food-dark.png`) and dark-theme copies to `docs/screenshots/` for [README.md](README.md)

`release:package` runs this export automatically after unit tests (commit `docs/screenshots/` with your release).

Validate without updating reference images:

```bash
./scripts/export_release_screenshots.sh --validate
```

Attach screenshots when publishing (only if release quota has room — see [Codeberg storage quota](RELEASE.md#codeberg-storage-quota)):

```bash
devenv tasks run release:screenshots
devenv tasks run release:package
./scripts/publish_release.sh 1.8.0 --with-screenshots
```

Reference images for regression live under `android/app/src/screenshotTestPlayDebug/reference/`.

### Screenshot fallbacks (if JVM previews are insufficient)

**Tier 2 — headless Android emulator (no phone):** enable `emulator.enable` and a system image in `devenv.nix`, then capture from a running emulator with `adb exec-out screencap`. A dedicated `scripts/capture_release_screenshots_emulator.sh` can be added when needed.

**Tier 3 — physical device via Windows adb:** reuse existing seed intents from `MainActivity.kt` (`seed_test_data`, `seed_body_metrics`) and tab navigation, mirroring [`scripts/capture_android_perf_baseline.sh`](scripts/capture_android_perf_baseline.sh):

```powershell
adb shell am start -n org.codeberg.fitguy.nofud.debug/org.codeberg.fitguy.nofud.MainActivity --ez seed_test_data true
adb exec-out screencap -p > 01-home.png
```

Prefer the emulator over coordinate-based phone taps when automating — screen sizes vary.

## F-Droid follow-up

Before submitting to [fdroiddata](https://gitlab.com/fdroid/fdroiddata):

- Build the `fdroid` flavor (`assembleFdroidRelease`) - omits proprietary Play Core libraries
- Add store metadata under `metadata/en-US/`
- Open an MR with `metadata/org.codeberg.fitguy.nofud.yml` using the signing key fingerprint above
- Keep `fdroid/org.codeberg.fitguy.nofud.yml` in sync with `versionName` / `versionCode` (`devenv tasks run release:check-metadata`)

See the plan in `.cursor/plans/` or project issues for the full F-Droid checklist.

## Calculation changes

Before shipping formula, constant, or guardrail changes:

1. Update implementation and [`NutritionConstants.kt`](android/app/src/main/java/org/codeberg/fitguy/nofud/models/NutritionConstants.kt) when applicable
2. Update [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) (formula register + policy table)
3. Update in-app strings (`settings_calc_*` in `strings.xml`) if user-visible
4. Add or adjust unit tests under `android/app/src/test/`
5. Document user impact in `CHANGELOG.md`
6. Run `devenv shell bash -lc 'cd android && ./gradlew test'`

## APK size baselines (1.4.0)

- `fdroid` release APKs with ML Kit barcode scanning: ~45 MB (universal and per-ABI splits are similar)
- Play and fdroid flavors share the same barcode scanner; only Play Core (in-app review/update) is play-only

## litertlm-android size delta (1.13.0)

Measured via `devenv tasks run release:package` (full multi-ABI build, not `-PreleaseAbi`), comparing 1.12.0 → 1.13.0 (first release with `litertlm-android` as `implementation` for both flavors — see `build.gradle.kts`):

| APK | 1.12.0 | 1.13.0 | Delta |
|-----|--------|--------|-------|
| `arm64-v8a` (play/fdroid) | ~28.5 MB | ~38.0 MB | **+9.5 MB** |
| `x86_64` (play/fdroid) | ~28.8 MB | ~39.3 MB | **+10.5 MB** |
| `armeabi-v7a` (play/fdroid) | ~28.2 MB | ~28.7 MB | +0.5 MB (no native litertlm lib for this ABI — delta is just new Kotlin/resources) |
| universal (play/fdroid) | ~35.3 MB | ~54.8 MB | **+19.5 MB** (carries both arm64-v8a and x86_64 native libs) |

The model itself (~2.4 GB) is never bundled — it's downloaded at runtime into `filesDir/models/`. This delta is entirely `liblitertlm_jni.so` (R8/debug-symbol stripping couldn't strip it — see the "Unable to strip the following libraries" build warning). Users on `armeabi-v7a`-only devices (rare) get the on-device feature's UI but no working backend, since there's no native lib for that ABI; `OnDeviceCapability.isSupported()` already excludes non-`arm64-v8a`/`x86_64` ABIs from the capability gate, so the Settings picker correctly hides `ON_DEVICE` for them.
