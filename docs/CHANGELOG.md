# Changelog

All notable changes to Chompass are documented here.

## [Unreleased]

### Fixed

- Gallery food-photo pick opens the multi-photo review sheet again instead of silently returning to Home (Android).
- Launcher Camera / Voice / Barcode shortcuts target the enabled launcher icon alias (same as share), so image capture and gallery pick no longer die on the Home screen after selecting a photo (Android).
- Launcher Voice (and Barcode) shortcuts keep their destination in the sticky inbox until the sheet dismisses, and System theme no longer remounts the whole UI on every resume — so Voice opens instead of dropping to Home (Android).

## [3.6.0] - 2026-08-01

### Added

- Protein goal modes: grams/day, g/kg body weight, or g/kg lean mass (Android Settings + PWA); rate modes update daily grams when weight or body fat changes.
- Manual active burn log from Add Food (Android): name + kcal merges into today’s ADD_ACTIVE budget (with Health Connect or activity-level estimate).
- Add Food “Log again” chips prefer favorites, then recents, then frequent (Android + PWA), with empty-state guidance.
- Progress chart default range setting, with last-viewed range remembered (Android + PWA).
- Display-only 7-day moving-average weight trend on Progress charts (Android + PWA); not used by Adaptive Goals.

### Changed

- Food review sheets put name, serving, macros, and meal first; portion check, ingredients, micros, and What-if sit below (Android + PWA).
- Clearer ADD_ACTIVE calorie-mode copy on Home and Home Display settings; waiting hint when burn is still zero.
- Share or pick up to 10 food photos into the multi-photo review sheet (Android; was capped at 2 for share-ins).
- PWA desktop home hero (≥900px): week-strip day arrows and horizontal calorie/macro bars; mobile keeps the semicircle gauge and vertical tubes.

### Fixed

- Sharing images into Chompass while launched from a launcher shortcut no longer drops the inbox when Home is stopped (Android).
- Gallery multi-photo import survives activity recreation more reliably (Android).

## [3.5.1] - 2026-08-01

### Added

- Health and accuracy disclaimers on onboarding plan-ready (Android + PWA): not medical advice; photo estimates and vague portion labels are often wrong; AI/LLM output is estimates only.
- Accuracy note under Android Settings → Safety & Medical.

### Changed

- PWA onboarding is fully localized across the shared 15-locale set (steps, choices, AI setup, plan-ready), not only welcome/CTA buttons.

## [3.5.0] - 2026-07-31

### Added

- Opt-in WebDAV **Sync on open** (Android + PWA): once per local day when the app is opened; off by default. Manual Sync now unchanged.
- Android launcher long-press shortcuts for Camera, Voice, and Barcode food logging.
- Meal constituents + g/unit (#154): composite AI meals can return editable `constituents[]` with per-row serving units; bounded client reconcile keeps row totals aligned with the meal. Diary **1.2**, sync **1.1**, and meal-share **v2** round-trip serving units and constituents (older versions still import).
- Settings toggle **Meal ingredient breakdown** (Android + PWA): opt out of AI `constituents[]`. Always off for on-device Gemma; on-device also skips the extra AI serving-unit call and uses heuristics instead.

### Changed

- Food review sheets (Android + PWA) show grouped ingredient rows: scale, edit, add, or remove constituents; meal macros follow the rows.
- Adaptive launcher icons (API 26+) with themed backgrounds.

### Fixed

- Clearer error when Gemini rejects the request because the network location is unsupported (VPN / region guidance).

## [3.4.0] - 2026-07-31

### Added

- Streaming AI food analysis: calories and macros fill in as the provider responds (Android and PWA when SSE is available).
- Ask AI to correct on a logged entry: quick chips, note, before/after field diff, then Save (Android and PWA).
- Quick context chips on photo review (No oil, Extra cheese, Large portion, Grilled).
- Shared 15-locale UI contract: PWA Language setting with core-surface catalogs; Android locale-aware date/number formatting.

### Changed

- Modal bottom sheets stay open while a busy operation is in progress (no accidental drag-dismiss).
- Photo context and edit-entry copy clarified (“Tell AI what this is”, “Ask AI to correct”).

## [3.3.3] - 2026-07-31

### Fixed

- WebDAV second sync no longer fails with "conflict persisted": `If-None-Match: *` is only used when creating the remote file; updates without a usable ETag overwrite after merge (and weak `W/` ETags are normalized for `If-Match`).

## [3.3.2] - 2026-07-31

### Fixed

- WebDAV Basic auth now uses UTF-8 (matching curl), so passwords with characters like ß or § work against hosts such as Hetzner Storage Box.

## [3.3.1] - 2026-07-31

### Added

- Progressive meal draft on Android: weigh and analyze ingredients one at a time, then log the combined meal (or add another item).
- Optional confirmed total portion weight during food entry clarification, so analysis can use a ground-truth gram amount.
- Desktop PWA layout at 900px+: left nav rail, wider content column, and centered sheets.
- PWA update toast to reload onto a new service-worker version without silently swapping mid-session (IndexedDB data kept).

### Fixed

- WebDAV sync URL normalization on Android and the PWA: missing scheme defaults to HTTPS; stacked schemes (e.g. `https://https://…`) are collapsed. Clearer URL hint for storage-box style hosts.

### Changed

- Food-logging accuracy docs and blog post updated with tiered input methods and revised benchmark metrics (maintainer tooling only; grounded entry remains off in shipping builds).

## [3.3.0] - 2026-07-29

### Changed

- Settings uses a compact hub with drill-down groups on Android and the PWA (Personal, Goals & Nutrition, App & Display, AI & Speech, Health/Data/Sync, About) for easier overview.

## [3.2.0] - 2026-07-28

### Added

- Optional user-hosted WebDAV sync between Android and the PWA (manual **Sync now**; API keys and food photos excluded).
- Photo food entry can decode a visible barcode and enrich AI analysis with Open Food Facts product context (Android and PWA).

## [3.1.3] - 2026-07-28

### Fixed

- Editing grams on a newly logged food (photo/text/voice review) now correctly rescales calories/protein/carbs/fat when saved — previously the on-screen preview updated live but the persisted diary entry kept the macros from the original serving size.
- The nutrition lock/unlock toggle on an already-saved diary entry now covers all macros and micronutrients (calories, protein, carbs, fat, fiber, and the "More Nutrition" fields), matching the new-entry review sheet. Previously only fiber was editable there.

## [3.1.2] - 2026-07-26

### Fixed

- Diary food-row swipe favorite and delete actions now use width-relative triggers, so they stay reachable on more screen sizes.

### Changed

- Maintainer tooling: ktlint in devenv, git commit-msg hooks, and F-Droid inclusion MR submit script targeting the existing fdroiddata MR.

## [3.1.1] - 2026-07-26

### Added

- Camera scale tip during food photo capture to help estimate portions more accurately (dismissible; remembered).

### Changed

- Clearer Active calorie / Activity Level copy (Health Connect, settings, PWA hint, calculation docs): Activity Level stays the everyday baseline when Add Active is on.

## [3.1.0] - 2026-07-25

### Added

- Opt-in **Portion size check (Beta)** for photo food entries: when the estimate looks uncertain, a Small / Regular / Large / Restaurant-size chip row appears; answering re-analyzes with that context (default off in Settings).
- Onboarding draft persistence so leaving mid-setup can resume later (Android).
- Accuracy documentation (`docs/ACCURACY.md`) and site copy explaining typed vs photo AI logging performance.

### Fixed

- Diary JSON import accepts legacy format version `1.0` (macros-only) as well as `1.1`, so older Fud AI / early NoFUD exports restore in Chompass.

### Changed

- PWA onboarding pace UI and service-worker shell cache bump; marketing site header/nav responsiveness and lightweight site shell on pages.

## [3.0.0] - 2026-07-24

**NoFUD is now Chompass** (chompass.app). New name, new fork-compass logo, same app, same maintainer, same license.

### Migration from NoFUD

- **Android:** the application ID changed from `org.codeberg.fitguy.nofud` to `app.chompass`, so Chompass installs as a *new app*. In NoFUD: Settings → Export (diary JSON + body metrics JSON). Install Chompass, then Settings → Import both files. Old NoFUD export files import unchanged. Uninstall NoFUD when done.
- **PWA:** the web app moved to `https://chompass.app/app/`. Browser storage does not carry across domains — export from the old PWA, import at the new address, then remove the old installation.
- Old `nofud://add-meal` and upstream `fudai://` share links continue to open in Chompass.

### Changed

- Application ID / package: `app.chompass`; project, themes, and resources renamed accordingly.
- New fork-compass launcher icon, PWA icons, and website logo (all 18 theme variants regenerated).
- Website and PWA hosted at `https://chompass.app/` (Codeberg Pages custom domain); Codeberg repo renamed to `fitguy/chompass`.
- Release APKs are now named `Chompass-fdroid-<version>*.apk`.
- Diary / body-metrics exports stamp `"app": "Chompass"`; importers on both platforms accept `chompass`, `nofud`, and `fud ai`.
- Primary meal-share deep link scheme is `chompass://` (`nofud://` and `fudai://` remain accepted for import).

## [2.0.0] - 2026-07-23

Major release: ships the **companion PWA** alongside Android, with shared export/formula contracts gated in release packaging. Android daily-driver UX is largely continuous with 1.14.x; Health Connect, widgets, notifications, on-device LLM, and full i18n remain Android-only. Grounded food entry stays WIP and disabled.

### Added

- Companion PWA at `chompass.app/app/` — diary, progress charts, manual/barcode/AI food entry, saved meals/recipes, copy-from-day / meal share, BYOK AI Coach, settings, and onboarding (data-compatible JSON with Android).
- Cross-app parity fixtures and JSON Schemas (`testdata/parity/`, `contracts/`) plus `release:check-parity` (PWA tests, typecheck, schema validation), also run inside `release:package`.
- AI API key validation during onboarding (Android and PWA).

### Changed

- Codeberg Pages deploy rsyncs the PWA into `/Chompass/app/` with the marketing site (`deploy_pages.sh` / `publish_release.sh`).
- Release asset management and distribution docs aligned with Codeberg quota policy (latest release, universal APK only).

## [1.14.10] - 2026-07-22

### Changed

- Regenerated launcher icons and in-app logos; default teal accent uses a deeper green-teal (`#006B5E`).

### Fixed

- About and Health Connect privacy / asset-credit links point at `docs/` paths after the maintainer-docs move.

## [1.14.9] - 2026-07-22

### Fixed

- Streak meal reminder no longer fires when today’s food diary already has entries (upstream #150).
- Copy-from-day stamps new entries with the current time and current meal instead of the source entry’s clock/meal (upstream #149).

## [1.14.8] - 2026-07-22

### Fixed

- Diary JSON import accepts format version 1.1 and restores micronutrients (Fud AI / Chompass exports after 1.14.7).

## [1.14.7] - 2026-07-22

### Added

- Activity level picker subtitles now include approximate daily step guides (upstream #141/#132).
- Diary export (JSON / CSV / Markdown) includes all stored micronutrients; export format version 1.1.
- Add-food sheet opens Recents, Frequent, or Favorites directly (upstream reuse-meal menu split).
- Health Connect privacy rationale activity for API ≤33 discovery (`HealthPermissionsRationaleActivity`).

### Changed

- Saved Meals Recents limited to last 30 days; Frequent to last 90 days (upstream rolling windows).
- AI read timeout: 30–600 s range; default 180 s applies to Ollama/Custom only (`AiHttp.clientForProvider`).
- Clear food log prunes orphaned image files instead of wiping the entire image cache.
- AI fallback provider is enabled by default for new installs / unset preference.
- Gemini model list updated (`gemini-3.6-flash`, `gemini-3.5-flash-lite`); Gemini fallback default is `gemini-3.5-flash-lite`; Gemini speech default is `gemini-3.6-flash`.
- Removed bundled exercise / muscle image assets (smaller APK).

### Fixed

- Saved Meals Recents / Frequent / Favorites no longer treat different servings of the same food as separate items. Re-logging with new grams, pieces, or units updates the template instead of stacking duplicates.
- Brand-new foods (scan, AI, manual, coach) that would collide on name are auto-renamed (`Name (2)`, …) so accidental collisions stay distinct from intentional re-logs.
- Logging from the review sheet no longer double-applies serving scale (could inflate calories when changing portion size).
- Anthropic responses with thinking blocks no longer fail parsing (#139).
- OpenRouter/OpenAI truncated or reasoning-only responses retry once with compact settings (#145).
- Ollama over HTTP on a LAN address works (cleartext permitted for user-supplied local endpoints).
- Orphaned food photo JPEGs from older builds are pruned safely at startup and after log edits.

## [1.14.6] - 2026-07-20

### Fixed

- Water tracking shows fl oz when using imperial units (home, widgets, add-food flows).
- AI API keys are trimmed on save and in request headers (fixes auth failures from pasted trailing newlines).
- Configurable AI read timeout in Settings (30–300 s, default 60 s).
- Max AI response tokens clamped to 256–8192.
- In-app camera preview matches the captured photo framing.
- Settings weekly goal pace shows correct lbs values in imperial mode.
- Undo snackbar after swipe-deleting a food entry.
- Home screen widgets time out stale DataStore reads instead of hanging on the loading spinner.
- Food log save finishes before clearing the draft (more durable if the app is killed mid-save).
- Less accidental day swipes and swipe-to-delete (higher gesture thresholds).

## [1.14.5] - 2026-07-20

### Changed

- Replace proprietary ML Kit barcode scanning with FOSS zxing-cpp (Apache-2.0). F-Droid and Codeberg builds now share the same on-device scanner and barcode tile.

## [1.14.4] - 2026-07-20

### Fixed

- F-Droid packaging: `-PreleaseAbi=arm64-v8a` now uses `ndk.abiFilters` with ABI splits disabled, so the APK is the plain `app-release-unsigned.apk` name (avoids F-Droid `output:` / “Failed to find any output apks”, and keeps native libs consistent for the scanner).

## [1.14.3] - 2026-07-20

### Fixed

- F-Droid build compatibility: remove Gradle foojay-resolver plugin (flagged by fdroid scanner); make ML Kit barcode optional via `-Pnofud.barcodeMlkit=false` (F-Droid builds hide the barcode tile).

## [1.14.2] - 2026-07-15

### Fixed

- On-device AI: the 1.14.1 memory guard for E4B photo analysis was accidentally blocking E2B photo analysis too. The preflight memory check and CPU/GPU backend split now only apply to E4B. E2B photo analysis works as before.

## [1.14.1] - 2026-07-15

### Fixed

- On-device AI: E4B photo analysis now runs text on CPU and vision on GPU. A memory preflight check shows an in-app message instead of letting the OS kill the app when free memory is too low. On-device images are downscaled to 1024px before vision inference.

## [1.14.0] - 2026-07-15

### Added

- **On-device AI (opt-in):** Settings → AI Provider → **On-Device (Private)** runs food text and photo analysis locally via Gemma 4 (E2B or E4B). Download the model once from Hugging Face (~2.4–3.4 GB); nothing you log is sent to a server. Automatic cloud fallback when Fallback Provider is enabled.
- Settings explains that on-device models are much smaller than cloud AI (Gemini, GPT, Claude, etc.) and often misread portions, brands, and photos.

### Changed

- On-device provider is now shown on supported devices (arm64/x86_64, 6 GB+ RAM).

## [1.13.0] - 2026-07-15

### Added

- Internal prep for on-device AI food analysis (Gemma 4 E2B-it). Runs fully on-device with automatic cloud fallback. Not enabled for any users yet; still behind an internal rollout flag until a second device is tested.

## [1.12.0] - 2026-07-14

### Added

- Recipes: multi-ingredient saved meals, created and edited via a dedicated recipe builder, with one-tap logging of every ingredient as its own diary entry
- Coach can propose logging food, weight, or water entries from chat; you confirm or discard before anything is saved
- Barcode lookup caching for faster repeat scans, including offline

### Changed

- Settings: removed the "What's New" section from About (changelog notes live on Codeberg releases instead)
- Food analysis prompts now prefer non-gram serving units where appropriate
- Wheel picker feedback matches Material3

## [1.11.0] - 2026-07-14

### Added

- Optional water tracking (off by default): quick-log from Home, daily goal, reminders, and a home-screen widget; stored locally only
- Customizable meal time boundaries in Settings (defaults match previous automatic breakfast/lunch/dinner/snack windows)
- Multi-photo meal capture: add up to 10 photos from camera or gallery before AI analysis
- Health Connect **Manage access** entry in Settings and onboarding to review permissions on Android 14+
- Configurable water quick-log presets for the Add food slider (ml or fl oz when using imperial units)

### Changed

- Home macro cards show grams remaining or over goal instead of a static goal subtitle
- Widget gauge labels scale down for long values so numbers do not crowd the ring
- Add food sheet: compact water slider (replaces large water tile grid)

## [1.10.0] - 2026-07-14

### Added

- Live progress while AI analyzes a food entry (preparing request, calling AI, reading result, inferring serving units)
- Fallback AI provider: when the primary provider fails (overload, rate limit, network), Chompass retries automatically with a configured fallback model

### Changed

- Home calorie gauge simplified: removed Net and Dual display modes; active calories use simpler labels in Static and Add Active modes
- Food photos downscaled before upload to AI providers (smaller payloads, faster analysis)

## [1.9.0] - 2026-07-14

### Added

- Serving unit inference settings (Settings → Food logging): choose grams-only, heuristic, or AI-inferred units, with customizable grams-per-unit heuristics per food category
- Loading indicators and disabled submit buttons while food entries are being saved, preventing duplicate submissions

### Changed

- Food diary stored in monthly buckets for faster add, update, and delete on large histories
- Home screen and food-entry code reorganized for faster UI
- Preferences and Health Connect code split into focused modules (no user-facing behavior change)

## [1.8.0] - 2026-07-09

### Added

- Import weight and body data from a file (Settings → Health &amp; Data): Chompass JSON/CSV exports, [openScale](https://github.com/oliexdev/openScale) CSV, and generic weight CSVs (MyFitnessPal / SparkyFitness style, kg or lb). Re-importing the same file is idempotent and never duplicates manual entries
- Export now covers weight, body-fat **and** body-measurement history, in either CSV or JSON
- Wellness card on the Progress tab: sleep, resting heart rate and hydration read from Health Connect (new Sleep, Resting Heart Rate and Hydration read permissions)
- Height now syncs to Health Connect (new Height write permission), so scales and other apps can use it
- Optional background sync (Settings → Health &amp; Data, **off by default**): checks Health Connect for new data every few hours even when Chompass is closed
- Nutrition calculation audit documentation ([`CALCULATION_METHODS.md`](CALCULATION_METHODS.md)) with formula register, scientific policy decisions, and release checklist
- Unit tests for BMR/TDEE, macro goals, keto carb heuristics, weight forecast, adaptive goals, and body-composition estimates
- Calculation Methods UI sections for weight forecast, adaptive goals, and tape-measure body metrics
- Golden scenario tests (`CalculationGoldenScenariosTest`) and shared `GoalFormulaReference` for AI prompt parity
- **System** accent theme (Android 12+): follows the device wallpaper / Material You palette; now the default in Settings → Appearance
- Dynamic launcher icon that matches your selected accent color
- Food entry thumbnails load off the UI thread; orphaned photos are removed when entries or favorites are deleted

### Changed

- Unified energy-balance constant to **7,700 kcal/kg** across goal pacing, forecasts, adaptive goals, and AI prompts (was 7,000 in goal math only; ~10% underestimate at 0.5 kg/week pace)
- Weight forecast uses **calendar-day intake averaging** when fewer than 50% of lookback days have food logs
- Observed weight trend now uses **Theil–Sen** robust regression instead of ordinary least squares
- AI goal prompts pull multiplier/protein constants from shared `GoalFormulaReference`
- Home calorie gauge **Add Active** and **Dual** modes now use your activity-level estimate (TDEE minus BMR) when Health Connect is unavailable; **Add Active** no longer double-counts activity when Health Connect is on (goal is split into sedentary base + today's burn)
- Home calorie gauge shows whether today's active burn is measured (Health Connect) or estimated, with breakdown labels and screen-reader text
- About screen attribution updated to Chompass by fitguy (fork of Fud AI)

## [1.7.0] - 2026-07-09

### Added

- Share photos into Chompass from the camera or gallery (system share sheet) to start an image food entry: up to two images, composed side-by-side like dual capture
- Activity card on the Progress tab: daily steps and exercise minutes from Health Connect (new Steps + Exercise read permissions; wearables via Gadgetbridge, Samsung Health, etc.)
- Live import of meals other apps log to Health Connect (incremental, deduplicated; own records are never echoed back)
- Health-ecosystem compatibility notes in README and Settings (Gadgetbridge, openScale, Samsung Health, Fitbit; all via Health Connect, no vendor SDKs)

### Fixed

- The floating "+" add button no longer sits underneath the bottom navigation bar
- Keto diet mode now reaches the AI goal calculation and meal advice prompts (previously only the Coach chat knew about it, so AI goals could contradict the app's keto carb target)
- AI responses (food names, coach replies, advice) follow the app's language instead of always answering in English

## [1.6.0] - 2026-07-08

### Added

- Optional glass blur effect with a settings toggle for frosted UI surfaces

### Changed

- Macro nutrient chips and color palette use Material theme colors across home and detail views
- Text input sheets use `FudGlassTextField` for the same glass styling
- UI components use `MaterialTheme` colors for consistent light/dark mode
- Release APK size cuts (debug symbols, native lib packaging, dependency metadata exclusions) for F-Droid and IzzyOnDroid compliance

## [1.5.1] - 2026-07-08

### Changed
- Publish both `play` and `fdroid` flavor APK assets on Codeberg releases (with `Chompass-play-*` and `Chompass-fdroid-*` filenames).

## [1.5.0] - 2026-07-08

### Added

- Bulk diary import for larger food-log datasets in one pass

### Changed

- Barcode scanning updates and smaller release APKs

### Fixed

- Import errors show plain messages during migration

## [1.4.0] - 2026-07-08

### Added

- Migration and export flow fixes

### Changed

- README install notes updated, including architecture-aware APK selection
- README feature list and package-size notes updated

## [1.3.0] - 2026-07-08

### Added

- Multi-architecture Android release packaging with dedicated APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- Universal APK kept for users who want one download

### Changed

- Build/release pipeline emits ABI-targeted APKs for more devices
- Onboarding logo updates when the theme changes
- Docs updated for Android development and performance workflows

## [1.2.0] - 2026-07-08

### Added

- Android performance baseline capture workflow (`scripts/capture_android_perf_baseline.sh`, `docs/PERFORMANCE.md`)
- Pending food-input draft persistence to recover interrupted logging sessions
- Diet mode and keto-carb configuration support across onboarding, settings, and profile models

### Changed

- Progress charts use phased animations and loading states while data loads
- Progress data processing reorganized to reduce UI jank
- Home screen theming, shadows, and meal-section nutrient styling updated
- App/icon activity-alias theming fixed; install/distribution docs updated

### Fixed

- State restoration fixes in home/progress flows so in-flight input is not lost

## [1.1.0] - 2026-07-08

### Added

- Safety and medical guidance in onboarding and settings
- New food logging `AddFoodSheet` flow and camera capture fixes
- Codeberg release publishing helper script (`scripts/publish_release.sh`)

### Removed

- Legacy exercise data and related image assets

### Changed

- Food logging UX tweaks for photo and text input
- UI theme and component behavior fixes across key screens
- App icons/logos and localized strings updated
- Android development and release docs updated

## [1.0.0] - 2026-07-07

Initial public release of Chompass - an ad-free, privacy-focused Android fork of [Fud AI](https://github.com/apoorvdarshan/fud-ai).

### Added

- Chompass branding, Codeberg home, and `chompass://` meal-share deep links
- Upstream MIT attribution (`NOTICE`, `ASSET_CREDITS.md`, About screen, README)
- Original Chompass launcher icon and splash logo (see [ASSET_CREDITS.md](ASSET_CREDITS.md))
- [PRIVACY.md](PRIVACY.md) documents local-first, no-ads data practices
- `scripts/optimize_exercise_images.py` and `assets/exercises/IMAGE_MANIFEST.json` for bundled exercise photos
- About screen link to [ASSET_CREDITS.md](ASSET_CREDITS.md); `assets/muscle/LICENSE` (MIT)

### Removed

- Google AdMob / `play-services-ads` dependency and banner ad UI from the Android app
- Upstream package ID `com.apoorvdarshan.calorietracker`
- Unused `muscle_icon_group_*.png` muscle-filter assets

### Changed

- Application ID -> `app.chompass`
- App name, user-facing strings, privacy copy, and share text
- Source home from GitHub to Codeberg
- Exercise photos: single-frame WebP derivatives (max 800 px edge, ~19 MB total vs ~94 MB JPEG) via `scripts/optimize_exercise_images.py`
- Splash logos regenerated at 512 px (`scripts/generate_icons.py`) instead of 2048 px

### Preserved from upstream

- AI food logging (photo, voice, text, barcode)
- Coach chat, workouts library, Health Connect sync
- Home-screen widgets, diary export (JSON / Markdown / CSV)
- Meal import from upstream Fud AI (`fudai://` links)
- 15-language localization
