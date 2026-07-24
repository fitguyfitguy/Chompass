# F-Droid submission pack

Checklist and merge-request text for adding Chompass to [fdroiddata](https://gitlab.com/fdroid/fdroiddata).

**Application ID:** `app.chompass`  
**Current version:** 1.14.6 (versionCode 22)  
**Build task:** `release` in `android/app` subdir (`assembleRelease` with `-PreleaseAbi=arm64-v8a`)  
**Signing key SHA-256:** `2694994fcb99d70e2c3978f770384dcf3091a310d9c56a23d4a145f150658dcf`

---

## Pre-submit checklist

### Source & releases

- [x] Public git repo: https://codeberg.org/fitguy/chompass
- [x] MIT license in repo root
- [x] Version tags matching `versionName` (`v1.14.2`, etc.)
- [x] Single FOSS build; Play Core / ad SDKs removed ([`docs/DISTRIBUTION.md`](DISTRIBUTION.md))
- [x] Confirm `v1.14.6` tag exists on Codeberg and `assembleRelease` succeeds
- [x] Run `devenv tasks run release:check-metadata` before each release
- [x] Push `metadata/en-US/` to Codeberg `main` (required before fdroiddata review)

### Upstream store metadata (`metadata/en-US/`)

- [x] `title.txt`, `short_description.txt`, `full_description.txt` (workouts wording removed)
- [x] `images/icon.png` (512×512, from `ic_logo_teal.png`)
- [x] `images/phoneScreenshots/1.png` … `5.png` (from `docs/screenshots/`)
- [x] `changelogs/22.txt` for current `versionCode`
- [ ] Add `changelogs/<versionCode>.txt` for every future release

### Screenshot map

| File | Source | Shows |
|------|--------|-------|
| `phoneScreenshots/1.png` | `docs/screenshots/home.png` | Home: calorie ring, macros, today's log |
| `phoneScreenshots/2.png` | `docs/screenshots/add-food.png` | Add food: photo, voice, barcode, AI |
| `phoneScreenshots/3.png` | `docs/screenshots/progress.png` | Progress: weight, body fat, activity |
| `phoneScreenshots/4.png` | `docs/screenshots/coach.png` | AI Coach (BYOK) |
| `phoneScreenshots/5.png` | `docs/screenshots/settings.png` | Settings, Health Connect, themes |

Regenerate screenshots when UI changes: `devenv tasks run release:screenshots` (updates `docs/screenshots/` and README), then recopy into `metadata/en-US/images/phoneScreenshots/`.

### fdroiddata MR

- [ ] Fork https://gitlab.com/fdroid/fdroiddata
- [x] Copy [`fdroid/app.chompass.yml`](fdroid/app.chompass.yml) → `metadata/app.chompass.yml` (prepared locally)
- [ ] Push branch `app.chompass` and open MR (needs GitLab auth; see below)
- [ ] Respond to reviewer questions in the MR

### Optional (faster path)

- [ ] Submit to [IzzyOnDroid](https://android.izzysoft.de/applists.php) while main F-Droid review is in progress

---

## fdroiddata merge request body

Copy into the GitLab MR description:

```markdown
## Required

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy)
* [x] The original app author has been notified (and does not oppose the inclusion)
* [x] Builds with `fdroid build` and all pipelines pass
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author.

## Strongly Recommended

* [x] The upstream app source code repo contains the app metadata _(summary/description/images/changelog/etc)_ in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure
* [x] Releases are tagged and auto update is enabled

## Suggested

* [x] External repos are added as git submodules instead of srclibs
* [ ] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds)

  No, I don't want this yet.
* [ ] Multiple apks for native code

  I ship a single arm64 APK for F-Droid (`-PreleaseAbi=arm64-v8a`; ABI splits disabled). `subdir` is `android/app` (the app module), so the unsigned APK lands at `build/outputs/apk/release/app-release-unsigned.apk` with no `output:` override.

---

## Summary

**New app:** Chompass — ad-free, privacy-focused Android calorie and macro tracker.

> **Note:** this MR originally proposed the app as *NoFUD* (`org.codeberg.fitguy.nofud`). The app was renamed to **Chompass** with application ID `app.chompass` before first inclusion; the metadata file in this MR has been renamed accordingly. The old ID never shipped on F-Droid.

- **Application ID:** `app.chompass`
- **License:** MIT
- **Upstream:** https://codeberg.org/fitguy/chompass
- **Category:** Sports & Health, Diet

Chompass is a maintained fork of [Fud AI](https://github.com/apoorvdarshan/fud-ai) with a distinct application ID, branding, and scope (no workout library, no ad/analytics SDKs). There is a single `release` build. On-device barcode scanning uses FOSS **zxing-cpp** (Apache-2.0) in both upstream and F-Droid builds — no ML Kit / proprietary scanner split.

**v3.0.0** (2026-07-24): project renamed NoFUD → Chompass (new app ID, new logo); ships the companion PWA contracts and all 1.x/2.x fixes.

## Build

- **Repo:** `https://codeberg.org/fitguy/chompass.git`
- **Subdir:** `android/app` (app module; parent `android/settings.gradle.kts` is found automatically)
- **Gradle:** `yes` (`assembleRelease`)
- **Commit:** `e5fb72e60cbc0729ebaf6e0b0891613e8f8225ec` (tag `v3.0.0`, versionCode 28)
- **Props:** `-PreleaseAbi=arm64-v8a` (via `gradleprops:`)
- **Output:** `build/outputs/apk/release/app-release-unsigned.apk` under `subdir` (no `output:` needed)
- **Codeberg release:** https://codeberg.org/fitguy/chompass/releases/tag/v3.0.0 (same FOSS build as F-Droid; barcode via zxing-cpp)
- **Reproducible builds:** not enabled yet

Store metadata is in upstream `metadata/en-US/` (Fastlane/Triple-T).

## Privacy & network use

Full policy: https://codeberg.org/fitguy/chompass/src/branch/main/docs/PRIVACY.md

| Feature | Network? | Notes |
|---------|----------|-------|
| Core logging (manual, barcode) | Optional | Barcode uses Open Food Facts public API |
| Cloud AI (BYOK) | User opt-in | User supplies API key; requests go to their chosen provider only |
| On-device AI | Opt-in download | Gemma 4 `.litertlm` fetched once from Hugging Face (`litert-community`); app works without it |
| Health Connect | On-device | No cloud sync |
| Update check | None | Manual updates via releases |

No bundled analytics, ads, Firebase, or Google Play Services.

## Dependencies of note

- `androidx.health.connect:connect-client`: Health Connect
- `io.github.zxing-cpp:android`: on-device barcode (FOSS, Apache-2.0)
- `com.google.ai.edge.litertlm:litertlm-android`: on-device LLM runtime (optional; model not bundled)

## Anti-features

Happy to add labels reviewers suggest. My reading:

- **No Ads / No Tracking:** none bundled
- **NonFreeNet:** only if BYOK cloud AI promotion counts; core app works without any cloud provider (manual entry, barcode, optional on-device AI)
- **TetheredNet:** not applicable (OFF + user-chosen AI endpoint)

## Health Connect

Reads/writes nutrition, weight, body fat, height; reads steps, exercise, sleep, resting HR, hydration, energy burn when user grants permissions. Optional background sync is **off by default**.

## Fork note

Forked from Fud AI with a distinct package ID `app.chompass` vs upstream. I am the Chompass maintainer.
```

---

## Submit commands

**One command** (after `glab auth login --hostname gitlab.com` or `GITLAB_TOKEN`):

```bash
chmod +x scripts/submit_fdroiddata_mr.sh
./scripts/submit_fdroiddata_mr.sh
```

**Manual fallback:**

```bash
devenv tasks run release:check-metadata

git clone https://gitlab.com/<your-gitlab-user>/fdroiddata.git
cd fdroiddata
git checkout -b app.chompass
cp /path/to/Chompass/docs/fdroid/app.chompass.yml metadata/app.chompass.yml
git add metadata/app.chompass.yml
git commit -m "New App: Chompass (app.chompass)"
git push -u origin app.chompass
```

Open MR: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new (paste the MR body block above)

---

## After inclusion

For each release:

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`
2. Update `docs/CHANGELOG.md` and `metadata/en-US/changelogs/<versionCode>.txt`
3. Sync [`fdroid/app.chompass.yml`](fdroid/app.chompass.yml) (`CurrentVersion`, `CurrentVersionCode`, `Builds:` entry with commit hash)
4. Run `./scripts/package_release.sh --check-metadata`, tag `v<version>` on Codeberg, publish APKs
5. Push upstream metadata to Codeberg `main` before fdroiddata picks up the tag
6. F-Droid `checkupdates` may open a follow-up MR automatically; otherwise refresh with `./scripts/submit_fdroiddata_mr.sh`

**Latest release (2026-07-20):** v1.14.6 — upstream bug fixes (water fl oz, AI settings, widgets, swipe UX). Published at https://codeberg.org/fitguy/chompass/releases/tag/v1.14.6
