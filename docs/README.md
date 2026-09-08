# Chompass docs index

Landing page for `docs/`. Shipped-work record lives in
[`docs/CHANGELOG.md`](CHANGELOG.md): **always check it before asking "did this
ship?"**. Cross-app parity matrix: [`docs/PARITY.md`](PARITY.md); wire schemas in
[`contracts/`](../contracts/); golden fixtures in [`testdata/parity/`](../testdata/parity/).

## Doc status convention

Design docs carry a first-line `Status:`: one of
`shipped in <x.y.z> (date)` / `WIP` / `parked`, with a date. If a doc's
status is missing or stale, fix it (release checklist).

## Reference & contracts

| Doc | Contents |
|-----|----------|
| [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) | Formula register (BMR/TDEE/goals/water), scientific audit, **calculation-change checklist** (dual Kotlin + PWA) |
| [`PARITY.md`](PARITY.md) | Android ↔ PWA feature matrix + shared/Android-only/PWA-only scope |
| [`LOCALIZATION.md`](LOCALIZATION.md) | Shared 18-locale contract, PWA `lib/i18n/`, Android `values-*` |
| [`TRANSLATION_GUIDE.md`](TRANSLATION_GUIDE.md) | Translator-facing guide: parent languages (EN semantic, DE fit + voice), voice, compact-label budgets, collisions, validation |
| [`ACCURACY.md`](ACCURACY.md) | User-facing accuracy explainer (what the AI numbers mean) |
| [`PRIVACY.md`](PRIVACY.md) | Privacy stance: no ads/analytics, local-first, API-key handling |
| [`ASSET_CREDITS.md`](ASSET_CREDITS.md) / [`NOTICE.md`](NOTICE.md) | Asset provenance, licenses |

## Process

| Doc | Contents |
|-----|----------|
| [`DEVELOPMENT.md`](DEVELOPMENT.md) | devenv/Nix setup, SDK, first-time build |
| [`RELEASE.md`](RELEASE.md) | Tag/publish runbook, token split, quota policy |
| [`DISTRIBUTION.md`](DISTRIBUTION.md) | Single F-Droid/Codeberg build; `play` flavor disabled |
| [`FDROID_SUBMISSION.md`](FDROID_SUBMISSION.md) | F-Droid listing (`app.chompass`): keep [`fdroid/app.chompass.yml`](fdroid/app.chompass.yml) in sync |
| [`fdroid/`](fdroid/) | F-Droid build metadata (`app.chompass.yml`), mirror of the live listing |
| [`screenshots/`](screenshots/) | Published feature screenshots (README, dark only) |

## Design & current state (read the Status line first)

| Doc | Status |
|-----|--------|
| [`GROUNDED_ENTRY.md`](GROUNDED_ENTRY.md) | **WIP: not production**, UI off via feature flag |
| [`ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md) | Gemma on-device: production Tiers A/B behind a default-off Settings toggle |

## Benchmarks

| Doc | Contents |
|-----|----------|
| [`FOOD_ACCURACY_BENCHMARK.md`](FOOD_ACCURACY_BENCHMARK.md) | Benchmark methodology + harness docs |
| [`benchmarks/food_accuracy/`](benchmarks/food_accuracy/README.md) | Harness code (uv-run), manifests, scorers |
| [`benchmarks/body_fat/`](benchmarks/body_fat/README.md) | **WIP**: BF% estimation research (formulas vs BYOK/local LLM; Track A first) |
