# Distribution builds

NoFUD ships a **single Gradle build** aimed at F-Droid, IzzyOnDroid, and direct APK downloads from [Codeberg releases](https://codeberg.org/fitguy/nofud/releases).

## Google Play flavor (disabled)

The former **`play` product flavor** (Google Play Core in-app review, in-app update, Play Store update checks) is **removed from the repo and build process for now**.

| Former `play`-only behavior | Current single build |
|-----------------------------|----------------------|
| Play In-App Review | No-op (`InAppReview.bind` does nothing) |
| Play In-App Update API | Update check always reports up-to-date |
| Play Store download links | Codeberg releases URL |

**Why disabled:** The maintainer is not publishing to Google Play at this time. Keeping one build reduces Gradle variants, release packaging, and Codeberg attachment quota usage.

**Re-enabling later:** Reintroduce a `play` product flavor under `android/app/build.gradle.kts`, restore Play Core dependencies (`play-review-ktx`, `play-app-update`), and add flavor-specific implementations under `src/play/` (mirroring the stubs now in `src/main/`). See git history before this change for the prior layout.

## Build commands

| Goal | Gradle task | Release artifact (packaged name) |
|------|-------------|----------------------------------|
| Debug | `:app:assembleDebug` | - |
| Release | `:app:assembleRelease` | `NoFUD-fdroid-<version>.apk` (+ ABI splits) |

Inside devenv:

```bash
build-debug      # assembleDebug
build-release    # assembleRelease
devenv tasks run release:package
```

APK outputs (before packaging rename):

- Debug: `android/app/build/outputs/apk/debug/app-debug.apk`
- Release universal: `android/app/build/outputs/apk/release/app-universal-release.apk`

Release packaging still uses the **`NoFUD-fdroid-*` filename prefix** so existing F-Droid metadata and download URLs stay stable.

## F-Droid metadata

Draft metadata lives in [`fdroid/org.codeberg.fitguy.nofud.yml`](fdroid/org.codeberg.fitguy.nofud.yml).

- **Tags ≤ v1.14.1:** Gradle task `fdroidRelease` (historical product flavor).
- **Tags after the flavor removal:** Gradle metadata `yes` (runs `assembleRelease`; not a flavor name)

Keep `CurrentVersion` / `CurrentVersionCode` in sync with `android/app/build.gradle.kts` (`devenv tasks run release:check-metadata`).

## Dependencies note

The single build intentionally omits proprietary **Google Play Core** libraries. Barcode scanning uses FOSS **zxing-cpp** (Apache-2.0).

On-device LLM (`litertlm-android`) is bundled in this build; whether F-Droid accepts the runtime model fetch is still under review. See [`docs/ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md).

## Reclaim Codeberg quota

Historical releases may still attach **`NoFUD-play-*` APKs** from when both flavors were published. Remove them to free attachment quota (typically ~30–50 MB per release, more when ABI splits were attached):

```bash
./scripts/manage_release_assets.sh list
./scripts/manage_release_assets.sh prune-play-assets --dry-run
./scripts/manage_release_assets.sh prune-play-assets -y
```

Keeps `NoFUD-fdroid-*`, legacy `NoFUD-<version>.apk`, and `SHA256SUMS`. Old `SHA256SUMS` files may still list play APK hashes until the next release repackages checksums.
