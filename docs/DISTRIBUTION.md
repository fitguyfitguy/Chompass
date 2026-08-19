# Distribution builds

Chompass ships a **single Gradle build** aimed at F-Droid and direct APK downloads from [Codeberg releases](https://codeberg.org/fitguy/chompass/releases).

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
| Release | `:app:assembleRelease` | `Chompass-fdroid-<version>.apk` (universal only on Codeberg) |

Inside devenv:

```bash
build-debug      # assembleDebug
build-release    # assembleRelease
devenv tasks run release:package
```

APK outputs (before packaging rename):

- Debug: `android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (also universal / per-ABI)
- Release universal: `android/app/build/outputs/apk/release/app-universal-release.apk` (ARM only: arm64-v8a + armeabi-v7a; no x86/x86_64)

Release packaging still uses the **`Chompass-fdroid-*` filename prefix** so existing F-Droid metadata and download URLs stay stable.

## F-Droid

The live listing metadata is mirrored in [`fdroid/app.chompass.yml`](fdroid/app.chompass.yml). Listing: https://f-droid.org/packages/app.chompass/.

- **Tags ≤ v1.14.1:** Gradle task `fdroidRelease` (historical product flavor).
- **Tags after the flavor removal:** Gradle metadata `yes` (runs `assembleRelease`; not a flavor name)
- **Inclusion:** merged via [fdroiddata!42984](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42984); the listing is live. F-Droid `checkupdates` opens version-update MRs automatically: never open an inclusion/update MR yourself. Update `docs/fdroid/app.chompass.yml` in this repo only; the maintainer may help bot MRs via the GitLab web GUI.
- **Builds entry:** replace the previous version with the new one; set `commit:` to the full release commit hash (not `vX.Y.Z`).

Keep `CurrentVersion` / `CurrentVersionCode` in sync with `android/app/build.gradle.kts` (`devenv tasks run release:check-metadata`). Full workflow: [`FDROID_SUBMISSION.md`](FDROID_SUBMISSION.md).

## Dependencies note

The single build intentionally omits proprietary **Google Play Core** libraries. Barcode scanning uses FOSS **zxing-cpp** (Apache-2.0).

On-device LLM (`litertlm-android`) is bundled in this build and ships in the F-Droid listing; the Gemma model itself is an opt-in runtime download. See [`docs/ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md).

## Reclaim Codeberg quota

**Policy:** one latest release; universal APK + `SHA256SUMS` only.

```bash
./scripts/manage_release_assets.sh list
./scripts/manage_release_assets.sh keep-latest -y
./scripts/manage_release_assets.sh prune-abi-splits v1.14.10 -y   # if splits linger on the kept release
./scripts/manage_release_assets.sh prune-play-assets -y           # leftover Chompass-play-* if any
```

See [`RELEASE.md`](RELEASE.md#codeberg-storage-quota).