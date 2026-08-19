# F-Droid listing

Chompass is **live on F-Droid**: [f-droid.org/packages/app.chompass](https://f-droid.org/packages/app.chompass/) (package `app.chompass`). This page is the maintainer reference for keeping the listing healthy.

**Application ID:** `app.chompass`  
**Current version:** 3.19.0 (versionCode 59)  
**Build task:** `release` in `android/app` subdir (`assembleRelease` with `-PreleaseAbi=arm64-v8a`)  
**Signing key SHA-256 (upstream):** `2694994fcb99d70e2c3978f770384dcf3091a310d9c56a23d4a145f150658dcf` (F-Droid signs its own builds)

---

## Status

- Inclusion MR [fdroiddata!42984](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42984) was **merged**; the listing is live and F-Droid clients install / auto-update `app.chompass` directly.
- **Updates:** F-Droid's `checkupdates` opens update MRs automatically from the `Builds:` entry in [`fdroid/app.chompass.yml`](fdroid/app.chompass.yml). No manual inclusion MR is needed, and **none should be opened** (duplicates review work).
- **Maintainer:** update `docs/fdroid/app.chompass.yml` in this repo only; do **not** push to the fdroiddata repo. The maintainer may assist bot update MRs via the GitLab web GUI when needed. The submission-era `submit_fdroiddata_mr.sh` is archived locally (`docs/local/archive/`) and is no longer part of the release flow.
- **Donate:** the live listing's `Donate:` field points at Ko-fi (`https://ko-fi.com/fitguy`), matching the mirror. See Codeberg issue #36.

## Per-release checklist (now that the listing is live)

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`
2. Update `docs/CHANGELOG.md` and **add** `metadata/en-US/changelogs/<versionCode>.txt` (F-Droid's `checkupdates` copies this file into the fdroiddata repo when it builds the new version; without it the version ships with no changelog)
3. Sync [`fdroid/app.chompass.yml`](fdroid/app.chompass.yml): replace the previous `Builds:` entry with the new version, set `commit:` to the **full** release commit hash (not the `vX.Y.Z` tag), update `CurrentVersion` / `CurrentVersionCode`
4. Run `devenv tasks run release:check-metadata` (verifies gradle / changelog / fdroid yml / hugo.toml agree)
5. Tag `v<version>` on Codeberg and publish APKs
6. Push upstream `metadata/en-US/` to Codeberg `main` before F-Droid picks up the tag
7. Verify the F-Droid `checkupdates` bot MR for the new version lands; if a reviewer flags something, reply in the GitLab web GUI (never open a fresh inclusion MR)

## Build notes (what the F-Droid build needs)

- **Repo:** `https://codeberg.org/fitguy/chompass.git`
- **Subdir:** `android/app` (app module; parent `android/settings.gradle.kts` is found automatically)
- **Gradle:** `yes` (`assembleRelease`)
- **Props:** `-PreleaseAbi=arm64-v8a` (via `gradleprops:`)
- **Output:** `build/outputs/apk/release/app-release-unsigned.apk` under `subdir` (no `output:` needed)
- **FOSS scanner notes:** no Play Core; barcode via FOSS **zxing-cpp** (Apache-2.0); on-device LLM runtime (`litertlm-android`) ships in the build: the model itself is an opt-in runtime download, and F-Droid accepted the listing.
- Store metadata lives in upstream `metadata/en-US/` (Fastlane/Triple-T).

## Privacy & network use

Full policy: https://codeberg.org/fitguy/chompass/src/branch/main/docs/PRIVACY.md

| Feature | Network? | Notes |
|---------|----------|-------|
| Core logging (manual, barcode) | Optional | Barcode uses Open Food Facts public API |
| Cloud AI (BYOK) | User opt-in | User supplies API key; requests go to their chosen provider only |
| On-device AI | Opt-in download | Gemma 4 `.litertlm` fetched once from Hugging Face (`litert-community`); app works without it |
| Health Connect | On-device | No cloud sync |
| Update check | None | Manual updates via releases / F-Droid client |

No bundled analytics, ads, Firebase, or Google Play Services.

## Health Connect

Reads/writes nutrition, weight, body fat, height; reads steps, exercise, sleep, resting HR, hydration, energy burn when user grants permissions. Optional background sync is **off by default** and only offered when the device’s Health Connect module supports background reads.

**Delivery:** Android 13 and lower use the Play Store Health Connect APK; Android 14+ uses the system/Mainline module. Chompass talks to both through Jetpack `connect-client` and does not require sandboxed Play. De-Googled ROMs that omit the binder service will report HC unavailable: file import/export remains the fallback.

## Archived

The inclusion MR body (merged via fdroiddata!42984) and the submission-era `submit_fdroiddata_mr.sh` are archived locally in `docs/local/archive/` (gitignored, not published).
