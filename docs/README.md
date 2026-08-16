# Chompass docs index

Landing page for `docs/`. Shipped-work record lives in
[`docs/CHANGELOG.md`](CHANGELOG.md): **always check it before asking "did this
ship?"**. Cross-app parity matrix: [`docs/PARITY.md`](PARITY.md); wire schemas in
[`contracts/`](../contracts/); golden fixtures in [`testdata/parity/`](../testdata/parity/).

## Doc status convention

Design/plan docs carry a first-line `Status:`: one of
`shipped in <x.y.z> (date)` / `WIP` / `parked` / `ARCHIVED`, with a date.
Archived plans are stubs that link into [`docs/archive/`](archive/). If a doc's
status is missing or stale, fix it (release checklist step 3).

## Reference & contracts

| Doc | Contents |
|-----|----------|
| [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) | Formula register (BMR/TDEE/goals/water), scientific audit, **calculation-change checklist** (dual Kotlin + PWA) |
| [`PARITY.md`](PARITY.md) | Android ↔ PWA feature matrix + shared/Android-only/PWA-only scope |
| [`LOCALIZATION.md`](LOCALIZATION.md) | Shared 16-locale contract, PWA `lib/i18n/`, Android `values-*` |
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
| [`PERFORMANCE.md`](PERFORMANCE.md) | Perf baseline capture (Windows adb) |
| [`WEB_PRESENCE.md`](WEB_PRESENCE.md) | chompass.app site + outreach checklist |
| [`fdroid/`](fdroid/) | F-Droid build metadata (`app.chompass.yml`), mirror of the live listing |
| [`screenshots/`](screenshots/README.md) | Published feature screenshots (README, dark only) |

## Design & current state (read the Status line first)

| Doc | Status |
|-----|--------|
| [`GROUNDED_ENTRY.md`](GROUNDED_ENTRY.md) | **WIP: not production**, UI off via feature flag |
| [`UNCERTAINTY_DRIVEN_ENTRY.md`](UNCERTAINTY_DRIVEN_ENTRY.md) | Strategy; Bet 1 shipping since 2026-07-29 |
| [`WATER_DYNAMIC_GOAL_DESIGN.md`](WATER_DYNAMIC_GOAL_DESIGN.md) | Shipped 3.13.0 (opt-in Beta): design + formula record |
| [`WEATHER_INTEGRATION_DESIGN.md`](WEATHER_INTEGRATION_DESIGN.md) | Shipped 3.13.0 (Open-Meteo only) |
| [`LOCAL_ENDPOINT_TRUST_DESIGN.md`](LOCAL_ENDPOINT_TRUST_DESIGN.md) | Shipped 3.9.0 (cleartext + user-CA trust) |
| [`ON_DEVICE_LLM.md`](ON_DEVICE_LLM.md) | Gemma on-device: production Tiers A/B + debug extras |
| [`DEMO_HERO_FIREFOX.md`](DEMO_HERO_FIREFOX.md) | **OPEN**: Firefox/embedded demo reload loop |
| [`SECURITY_HARDENING_PLAN.md`](SECURITY_HARDENING_PLAN.md) | Shipped 3.16.0: security/privacy audit + hardening (debug-extras gate, deep-link caps, image bounds, prompt-injection delimiters, PWA CSP); device rehearsal run 2026-08-16 |

## Benchmarks

| Doc | Contents |
|-----|----------|
| [`FOOD_ACCURACY_BENCHMARK_STATUS.md`](FOOD_ACCURACY_BENCHMARK_STATUS.md) | **Live state**: defaults, gates, findings log (append-only) |
| [`FOOD_ACCURACY_BENCHMARK.md`](FOOD_ACCURACY_BENCHMARK.md) | Benchmark methodology + harness docs |
| [`benchmarks/food_accuracy/`](benchmarks/food_accuracy/README.md) | Harness code (uv-run), manifests, scorers |

## Archive

[`docs/archive/`](archive/): executed plans, kept as history (stubs at the old
paths link here). Shipped records live in the CHANGELOG, not here.

## Local-only (gitignored, not committed)

[`docs/local/`](local/): maintainer-only state and notes: triage backlogs,
idea mines, reply/style guides, in-flight plans. Not published; published docs
deliberately do not link into it.
