# F-Droid submission pack

Checklist and merge-request text for adding Chompass to [fdroiddata](https://gitlab.com/fdroid/fdroiddata).

**Application ID:** `app.chompass`  
**Current version:** 3.1.2 (versionCode 31)  
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

**Canonical inclusion MR (do not open a second one):**
[fdroiddata!42984](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42984)  
Source branch: `org.codeberg.fitguy.nofud` (kept from the NoFUD-era submission; the metadata file is `metadata/app.chompass.yml`).

- [x] Fork https://gitlab.com/fdroid/fdroiddata
- [x] Copy [`fdroid/app.chompass.yml`](fdroid/app.chompass.yml) → `metadata/app.chompass.yml` (prepared locally)
- [x] Push updates to the **existing** MR source branch (not a new `app.chompass` branch while !42984 is open)
- [ ] Respond to reviewer questions in the MR

Opening a fresh branch/MR while !42984 is open duplicates review work (this happened with !43940). Always refresh **!42984** only — never a second inclusion MR.

Update [`fdroid/app.chompass.yml`](fdroid/app.chompass.yml) in this repo first, then apply the YAML in the GitLab web GUI (or run `submit_fdroiddata_mr.sh` locally if you choose).

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

**v3.1.0** (2026-07-25): Portion size check (Beta) for photo entries, onboarding draft resume, legacy diary format 1.0 import, accuracy docs / PWA polish. Builds on **v3.0.0** rename NoFUD → Chompass.

## Build

- **Repo:** `https://codeberg.org/fitguy/chompass.git`
- **Subdir:** `android/app` (app module; parent `android/settings.gradle.kts` is found automatically)
- **Gradle:** `yes` (`assembleRelease`)
- **Commit:** tag `v3.1.0` (versionCode 29); also lists prior `v3.0.0` / 28
- **Props:** `-PreleaseAbi=arm64-v8a` (via `gradleprops:`)
- **Output:** `build/outputs/apk/release/app-release-unsigned.apk` under `subdir` (no `output:` needed)
- **Codeberg release:** https://codeberg.org/fitguy/chompass/releases/tag/v3.1.0 (same FOSS build as F-Droid; barcode via zxing-cpp)
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

**One command** (optional maintainer automation — after `glab auth login --hostname gitlab.com` or `GITLAB_TOKEN`). Prefer `GITLAB_HOST=gitlab.com` if other GitLab hosts are configured. Default workflow is the GitLab web GUI:

```bash
GITLAB_HOST=gitlab.com ./scripts/submit_fdroiddata_mr.sh
```

The script **updates the open inclusion MR** ([!42984](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42984), branch `org.codeberg.fitguy.nofud`) and **refuses to open a second inclusion MR** while that one is open. Override only if GitLab replaced the MR:

```bash
CANONICAL_INCLUSION_MR_IID=<new> CANONICAL_INCLUSION_BRANCH=<branch> ./scripts/submit_fdroiddata_mr.sh
```

**Manual fallback** (same branch as !42984 — do **not** create `app.chompass` while that MR is open):

```bash
devenv tasks run release:check-metadata

git clone https://gitlab.com/<your-gitlab-user>/fdroiddata.git
cd fdroiddata
git fetch origin org.codeberg.fitguy.nofud
git checkout -B org.codeberg.fitguy.nofud origin/org.codeberg.fitguy.nofud
cp /path/to/Chompass/docs/fdroid/app.chompass.yml metadata/app.chompass.yml
git add metadata/app.chompass.yml
git commit -m "Update app.chompass to <version>"
git push -u origin org.codeberg.fitguy.nofud
```

That push refreshes https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42984 — no new MR.

---

## After inclusion

For each release (and while the inclusion MR is still open):

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`
2. Update `docs/CHANGELOG.md` and `metadata/en-US/changelogs/<versionCode>.txt`
3. Sync [`fdroid/app.chompass.yml`](fdroid/app.chompass.yml): replace the previous `Builds:` entry with the new version, set `commit:` to the **full** release commit hash (not the `vX.Y.Z` tag), update `CurrentVersion` / `CurrentVersionCode`
4. Run `./scripts/package_release.sh --check-metadata`, tag `v<version>` on Codeberg, publish APKs
5. Push upstream metadata to Codeberg `main` before fdroiddata picks up the tag
6. Refresh the **existing** fdroiddata MR in the **GitLab web GUI** (paste/update `metadata/app.chompass.yml` from `docs/fdroid/app.chompass.yml`) — do not open a new MR. Optional maintainer script: `GITLAB_HOST=gitlab.com ./scripts/submit_fdroiddata_mr.sh`
   Canonical until merged: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42984
7. After inclusion is merged, F-Droid `checkupdates` may open follow-up update MRs automatically; use the web GUI (or the submit script locally) only for those update MRs when needed

**Latest release (2026-07-26):** v3.1.1 — camera scale tip, clearer Active calorie / Activity Level guidance. Published at https://codeberg.org/fitguy/chompass/releases/tag/v3.1.1
