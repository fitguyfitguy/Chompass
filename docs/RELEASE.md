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
contract schemas), builds release APKs, copies the **universal** APK to the repo root, and writes `SHA256SUMS`:

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

Codeberg uploads **universal only** (`Chompass-fdroid-<version>.apk` + `SHA256SUMS`). Per-ABI splits may still be built locally but are not published.
## Tag and publish on Codeberg

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`
2. Update `docs/CHANGELOG.md` (`## [Unreleased]` → new `## [X.Y.Z] - YYYY-MM-DD` section)
3. Bump `website/hugo.toml` `params.version` (same as `versionName`)
4. Optional: sync `docs/fdroid/app.chompass.yml` and run `devenv tasks run release:check-metadata`
5. Commit, tag, push:

```bash
git tag -a v1.0.0 -m "Chompass 1.0.0 - initial public release"
git push origin v1.0.0
```

6. Publish APKs and redeploy the project site:

```bash
# One-time: create a token at https://codeberg.org/user/settings/applications
# Scopes: read:user + write:repository (repo access: this repo or all)
export CODEBERG_TOKEN='paste-token-here'
./scripts/publish_release.sh 1.0.0
# or: RELEASE_VERSION=1.0.0 devenv tasks run release:publish
```

`publish_release.sh` uploads F-Droid APK assets + `SHA256SUMS`, pastes changelog notes, then runs [`deploy_pages.sh`](../scripts/deploy_pages.sh) so [chompass.app](https://chompass.app/) shows the new version. Use `--skip-pages` to skip the site step.

The former **`play` flavor is disabled**; see [`DISTRIBUTION.md`](DISTRIBUTION.md).

The script auto-runs `nix shell nixpkgs#tea` / `nixpkgs#hugo` when those tools are not on PATH (e.g. outside devenv).

Stable download URL pattern: `https://codeberg.org/fitguy/chompass/releases/download/v<version>/Chompass-fdroid-<version>.apk`

### Codeberg storage quota

Codeberg applies a combined quota for **releases, packages, LFS, and attachments** (default **1.5 GiB** per user/org, separate from the 750 MiB git-repo limit).

**Policy:** keep **only the latest** Codeberg release, and attach **universal APK + `SHA256SUMS` only** (no per-ABI splits, no release screenshots by default).

**Symptoms:** `quota exceeded` from `tea`, or a release page with only some APKs attached.

**Before publishing**, check attachment usage and drop older releases:

```bash
./scripts/manage_release_assets.sh list
./scripts/manage_release_assets.sh keep-latest -y
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
## Release screenshots (optional)

JVM-based Compose screenshot previews render on the maintainer machine inside devenv. No phone or adb required.

```bash
devenv tasks run release:screenshots
```

This runs `./scripts/export_release_screenshots.sh`, which:

1. Renders `@PreviewTest` composables via `./gradlew :app:updateDebugScreenshotTest`
2. Copies friendly PNGs to `release-screenshots/` (`01-home-light.png` … `10-add-food-dark.png`) and dark-theme copies to `docs/screenshots/` for [README.md](../README.md)

`release:package` runs this export automatically after unit tests (commit `docs/screenshots/` with your release).

Validate without updating reference images:

```bash
./scripts/export_release_screenshots.sh --validate
```

Attach screenshots when publishing (only if release quota has room; see [Codeberg storage quota](#codeberg-storage-quota)):

```bash
devenv tasks run release:screenshots
devenv tasks run release:package
./scripts/publish_release.sh 1.8.0 --with-screenshots
```

Reference images for regression live under `android/app/src/screenshotTestDebug/reference/`.

### Screenshot fallbacks (if JVM previews are insufficient)

**Tier 2: headless Android emulator (no phone):** enable `emulator.enable` and a system image in `devenv.nix`, then capture from a running emulator with `adb exec-out screencap`. A dedicated `scripts/capture_release_screenshots_emulator.sh` can be added when needed.

**Tier 3: physical device via Windows adb:** reuse existing seed intents from `MainActivity.kt` (`seed_test_data`, `seed_body_metrics`) and tab navigation, mirroring [`scripts/capture_android_perf_baseline.sh`](../scripts/capture_android_perf_baseline.sh):

```powershell
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_test_data true
adb exec-out screencap -p > 01-home.png
```

Prefer the emulator over coordinate-based phone taps when automating; screen sizes vary.

## F-Droid follow-up

**Canonical inclusion MR:** [fdroiddata!42984](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42984) (branch `org.codeberg.fitguy.nofud`). Refresh that MR; never open a second inclusion MR while it is open.

Before / during F-Droid review:

- Build release APKs (`assembleRelease`); no proprietary Play Core libraries. See [`DISTRIBUTION.md`](DISTRIBUTION.md)
- Keep store metadata under `metadata/en-US/`
- Keep `docs/fdroid/app.chompass.yml` in sync with `versionName` / `versionCode` (`devenv tasks run release:check-metadata`)
- In the YAML `Builds:` block: **one** current version entry, `commit:` = **full git commit hash** of the release commit (not the tag name)
- Paste/update `docs/fdroid/app.chompass.yml` into [!42984](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42984) via the GitLab web GUI. Optional local helper (maintainer-run): `./scripts/submit_fdroiddata_mr.sh`

See [`FDROID_SUBMISSION.md`](FDROID_SUBMISSION.md) for the checklist and fdroiddata MR body.

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
