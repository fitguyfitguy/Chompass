# Changelog

All notable changes to NoFUD are documented here.

## [1.12.0] - 2026-07-14

### Added

- Recipes: multi-ingredient saved meals, created and edited via a dedicated recipe builder, with one-tap logging of every ingredient as its own diary entry
- Coach can propose logging food, weight, or water entries from chat; you confirm or discard before anything is saved
- Barcode lookup caching for faster, offline-friendly repeat scans

### Changed

- Settings: removed the "What's New" section from About (changelog notes live on Codeberg releases instead)
- Food analysis guidance refined to prefer non-gram serving units where appropriate
- Wheel picker visual feedback aligned with Material3 design

## [1.11.0] - 2026-07-14

### Added

- Optional water tracking (off by default): quick-log from Home, daily goal, reminders, and a home-screen widget; stored locally only
- Customizable meal time boundaries in Settings (defaults match previous automatic breakfast/lunch/dinner/snack windows)
- Multi-photo meal capture: add up to 10 photos from camera or gallery before AI analysis
- Health Connect **Manage access** entry in Settings and onboarding for easier permission review on Android 14+
- Configurable water quick-log presets for the Add food slider (ml or fl oz when using imperial units)

### Changed

- Home macro cards show grams remaining or over goal instead of a static goal subtitle
- Widget gauge labels scale down for long values so numbers do not crowd the ring
- Add food sheet: compact water slider (replaces large water tile grid)

## [1.10.0] - 2026-07-14

### Added

- Live progress while AI analyzes a food entry (preparing request, calling AI, reading result, inferring serving units)
- Fallback AI provider: when the primary provider fails (overload, rate limit, network), NoFUD retries automatically with a configured fallback model

### Changed

- Home calorie gauge simplified: removed Net and Dual display modes; active calories shown with clearer copy in Static and Add Active modes
- Food photos downscaled more efficiently before upload to AI providers (smaller payloads, faster analysis)

## [1.9.0] - 2026-07-14

### Added

- Serving unit inference settings (Settings → Food logging): choose grams-only, heuristic, or AI-inferred units, with customizable grams-per-unit heuristics per food category
- Loading indicators and disabled submit buttons while food entries are being saved, preventing duplicate submissions

### Changed

- Food diary stored in monthly buckets for faster add, update, and delete on large histories
- Home screen and food-entry flows refactored for snappier UI responsiveness
- Preferences and Health Connect code split into focused modules (no user-facing behavior change)

## [1.8.0] - 2026-07-09

### Added

- Import weight and body data from a file (Settings → Health &amp; Data): NoFUD JSON/CSV exports, [openScale](https://github.com/oliexdev/openScale) CSV, and generic weight CSVs (MyFitnessPal / SparkyFitness style, kg or lb). Re-importing the same file is idempotent and never duplicates manual entries
- Export now covers weight, body-fat **and** body-measurement history, in either CSV or JSON
- Wellness card on the Progress tab: sleep, resting heart rate and hydration read from Health Connect (new Sleep, Resting Heart Rate and Hydration read permissions)
- Height now syncs to Health Connect (new Height write permission), so scales and other apps can use it
- Optional background sync (Settings → Health &amp; Data, **off by default**): checks Health Connect for new data every few hours even when NoFUD is closed
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
- Home calorie gauge shows whether today's active burn is measured (Health Connect) or estimated, with clearer breakdown copy and screen-reader labels
- About screen attribution updated to NoFUD by fitguy (fork of Fud AI)

## [1.7.0] - 2026-07-09

### Added

- Share photos into NoFUD from the camera or gallery (system share sheet) to start an image food entry — up to two images, composed side-by-side like dual capture
- Activity card on the Progress tab: daily steps and exercise minutes from Health Connect (new Steps + Exercise read permissions; wearables via Gadgetbridge, Samsung Health, etc.)
- Live import of meals other apps log to Health Connect (incremental, deduplicated; own records are never echoed back)
- Health-ecosystem compatibility notes in README and Settings (Gadgetbridge, openScale, Samsung Health, Fitbit — all via Health Connect, no vendor SDKs)

### Fixed

- The floating "+" add button no longer sits underneath the bottom navigation bar
- Keto diet mode now reaches the AI goal calculation and meal advice prompts (previously only the Coach chat knew about it, so AI goals could contradict the app's keto carb target)
- AI responses (food names, coach replies, advice) follow the app's language instead of always answering in English

## [1.6.0] - 2026-07-08

### Added

- Optional glass blur effect with a settings toggle for frosted UI surfaces

### Changed

- Macro nutrient chips and color palette aligned with Material theme across home and detail views
- Text input sheets use `FudGlassTextField` for consistent glass styling
- UI components refactored to `MaterialTheme` colors for better light/dark consistency
- Release APK size optimizations (debug symbols, native lib packaging, dependency metadata exclusions) for F-Droid and IzzyOnDroid compliance

## [1.5.1] - 2026-07-08

### Changed
- Publish both `play` and `fdroid` flavor APK assets on Codeberg releases (with `NoFUD-play-*` and `NoFUD-fdroid-*` filenames).

## [1.5.0] - 2026-07-08

### Added

- Bulk diary import support for migrating larger food-log datasets in one pass

### Changed

- Barcode scanning flow updates and APK size optimizations

### Fixed

- Import error handling improvements for clearer recovery during migration/import flows

## [1.4.0] - 2026-07-08

### Added

- Migration and export flow improvements for smoother data portability

### Changed

- README install guidance refined, including architecture-aware APK selection notes
- README feature/platform status copy clarified and package-size context improved

## [1.3.0] - 2026-07-08

### Added

- Multi-architecture Android release packaging with dedicated APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- Universal APK output preserved for users who prefer a single download artifact

### Changed

- Build/release pipeline now emits ABI-targeted artifacts to improve compatibility across more devices
- Onboarding logo handling refined for dynamic theme changes
- Documentation updates for Android development and performance workflow clarity

## [1.2.0] - 2026-07-08

### Added

- Android performance baseline capture workflow (`scripts/capture_android_perf_baseline.sh`, `PERFORMANCE.md`)
- Pending food-input draft persistence to recover interrupted logging sessions
- Diet mode and keto-carb configuration support across onboarding, settings, and profile models

### Changed

- Progress charts now use phased animations and improved loading-state handling for smoother rendering
- Progress data processing/state management refactors to reduce UI jank and improve stability
- Home screen theming, shadows, and meal section nutrient presentation polish
- App/icon activity-alias theming consistency and updated docs for install/distribution flows

### Fixed

- State restoration edge cases in home/progress flows to prevent lost in-flight input

## [1.1.0] - 2026-07-08

### Added

- Safety and medical guidance in onboarding and settings
- New food logging `AddFoodSheet` flow and improved camera capture behavior
- Codeberg release publishing helper script (`scripts/publish_release.sh`)

### Removed

- Legacy exercise data and related image assets

### Changed

- Food logging UX refinements for photo and text input flows
- UI theme consistency and component behavior across key screens
- Updated app icons/logos and localized string copy for improved clarity
- Android development environment and release docs updates

## [1.0.0] - 2026-07-07

Initial public release of NoFUD - an ad-free, privacy-focused Android fork of [Fud AI](https://github.com/apoorvdarshan/fud-ai).

### Added

- NoFUD branding, Codeberg home, and `nofud://` meal-share deep links
- Upstream MIT attribution (`NOTICE`, `ASSET_CREDITS.md`, About screen, README)
- Original NoFUD launcher icon and splash logo (see [ASSET_CREDITS.md](ASSET_CREDITS.md))
- [PRIVACY.md](PRIVACY.md) describing local-first, no-ads data practices
- `scripts/optimize_exercise_images.py` and `assets/exercises/IMAGE_MANIFEST.json` audit trail
- About screen link to [ASSET_CREDITS.md](ASSET_CREDITS.md); `assets/muscle/LICENSE` (MIT)

### Removed

- Google AdMob / `play-services-ads` dependency and banner ad UI from the Android app
- Upstream package ID `com.apoorvdarshan.calorietracker`
- Unused `muscle_icon_group_*.png` muscle-filter assets

### Changed

- Application ID -> `org.codeberg.fitguy.nofud`
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
