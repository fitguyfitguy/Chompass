# Changelog

All notable changes to NoFUD are documented here.

## [Unreleased]

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
