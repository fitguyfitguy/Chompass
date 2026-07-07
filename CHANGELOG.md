# Changelog

All notable changes to NoFUD are documented here.

## [Unreleased]

### Changed

- Exercise photos: single-frame WebP derivatives (max 800 px edge, ~19 MB total vs ~94 MB JPEG) via `scripts/optimize_exercise_images.py`
- Splash logos regenerated at 512 px (`scripts/generate_icons.py`) instead of 2048 px

### Added

- `scripts/optimize_exercise_images.py` and `assets/exercises/IMAGE_MANIFEST.json` audit trail
- About screen link to [ASSET_CREDITS.md](ASSET_CREDITS.md); `assets/muscle/LICENSE` (MIT)

### Removed

- Unused `muscle_icon_group_*.png` muscle-filter assets

## [1.0.0] - 2026-07-07

Initial public release of NoFUD — an ad-free, privacy-focused Android fork of [Fud AI](https://github.com/apoorvdarshan/fud-ai).

### Added

- NoFUD branding, Codeberg home, and `nofud://` meal-share deep links
- Upstream MIT attribution (`NOTICE`, `ASSET_CREDITS.md`, About screen, README)
- Original NoFUD launcher icon and splash logo (see [ASSET_CREDITS.md](ASSET_CREDITS.md))
- [PRIVACY.md](PRIVACY.md) describing local-first, no-ads data practices

### Removed

- Google AdMob / `play-services-ads` dependency and banner ad UI from the Android app
- Upstream package ID `com.apoorvdarshan.calorietracker`

### Changed

- Application ID → `org.codeberg.fitguy.nofud`
- App name, user-facing strings, privacy copy, and share text
- Source home from GitHub to Codeberg

### Preserved from upstream

- AI food logging (photo, voice, text, barcode)
- Coach chat, workouts library, Health Connect sync
- Home-screen widgets, diary export (JSON / Markdown / CSV)
- Meal import from upstream Fud AI (`fudai://` links)
- 15-language localization
