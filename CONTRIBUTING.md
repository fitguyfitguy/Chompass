# Contributing to Chompass

Android is the product of record; the PWA under `web/` stays data-compatible via shared fixtures. Environment setup: [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

## Contributor tiers

| Tier | Touch | Day-1 commands |
|------|--------|----------------|
| **A: Android only** | `android/` | `devenv shell` → `build-debug` or `devenv tasks run build:debug`; unit tests: `./gradlew :app:testDebugUnitTest` |
| **B: Shared domain** | Kotlin + `web/app/src/lib/chompass-core/` + `testdata/parity/` ± `contracts/` | After A: `devenv tasks run ci:verify` (or `release:check-parity`) |
| **C: Release / site / F-Droid** | `docs/CHANGELOG.md`, `website/`, `docs/fdroid/`, metadata | [`docs/RELEASE.md`](docs/RELEASE.md); do **not** push fdroiddata |

Most UI bugs are Tier A. Formula, export JSON, AI defaults, or locale-contract changes are Tier B.

## Happy path (after `devenv shell` / direnv)

| Goal | Command |
|------|---------|
| Debug APK | `build-debug` or `devenv tasks run build:debug` |
| Unit tests + parity | `devenv tasks run ci:verify` |
| Parity only | `devenv tasks run release:check-parity` |
| Kotlin style | `kotlin-lint` / `devenv tasks run lint:kotlin` |
| PWA tests | `pwa-test` |
| PWA serve | `pwa-serve` |

Canonical automation is **`devenv tasks`**. Thin shell aliases (`build-debug`, `pwa-test`, …) stay for daily typing; prefer tasks in docs and CI mental models.

Automated shells (CI, editors, scripts) often skip direnv:

```bash
devenv shell bash -lc 'cd android && ./gradlew :app:assembleDebug'
```

Python scripts always use ephemeral `uv` (packaged in devenv): `uv run python scripts/…`.

## Architecture notes (keep lean)

- Single `:app` module; manual DI via `AppContainer` (no Hilt)
- Persistence: DataStore Preferences (domain split across `PreferencesStore*.kt`); no Room
- Networking: OkHttp + kotlinx.serialization (no Retrofit)
- On-device LLM (`litertlm-android`) stays on the **release** classpath for normal builds

## Device / ADB

Maintainer setup is WSL2 build + Windows host USB adb. If you are on **native Linux or macOS** with a USB device, use your normal `adb` on the default port and ignore `ANDROID_ADB_SERVER_PORT=5038` / Windows `adb.exe` paths in the docs.

## Reporting issues

File issues on [Codeberg](https://codeberg.org/fitguy/Chompass/issues). Two
templates are available (bug report, feature request); questions can use a
blank issue. A good report has: app version (Settings > About), phone model +
Android version, and repro steps. Check the latest release first: the
maintainer ships fixes in batches and many reports are already fixed. If the
issue touches nutrition formulas, diary export/import, meal sharing, or Health
Connect data, say so: those live in Kotlin **and** the PWA
(`web/app/src/lib/chompass-core/`) and must change together
([docs/PARITY.md](docs/PARITY.md)).

## Do not

- Commit secrets, keystores, diary exports, or release APKs
- Add AI/Cursor attribution to git commits
- Open F-Droid inclusion or update MRs (see [`docs/FDROID_SUBMISSION.md`](docs/FDROID_SUBMISSION.md); the listing is live: F-Droid `checkupdates` handles updates)
