# Android ↔ PWA feature parity

Living matrix of what is **shared**, **android-only**, **pwa-only**, or **wip**.
Android is the product of record; the PWA is a companion that reimplements daily-driver UX and stays **data-interoperable** via export contracts.

For formula / wire-format correctness (not feature lists), see:

- [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) — formula register + change checklist
- [`../testdata/parity/`](../testdata/parity/) — shared golden fixtures
- [`../contracts/`](../contracts/) — JSON Schemas for diary / body-metrics / meal-share
- `devenv tasks run release:check-parity` — PWA tests + schema validation (also runs inside `release:package`)

| Surface | Status | Android | PWA | Notes |
|---------|--------|---------|-----|-------|
| Home diary (meals, gauge, tubes/chips) | shared | `ui/home/` | `components/home-*` | Pref-driven tubes; fiber in default set |
| Progress (weight, BF, measurements, forecast) | shared | `ui/progress/` | `components/progress-*` | FCAST/ADAPT mirrored in `nofud-core/forecast.js` |
| Settings (profile, goals, units, AI, data) | shared | `ui/settings/` | `components/settings-*` | Defaults aligned; prefs not exported between apps |
| Onboarding | shared | `ui/onboarding/` | `components/onboarding-*` | |
| Food entry (manual, barcode OFF, AI photo/text) | shared | entry flows | entry components | Wait UX: phased overlay + single-flight lock (PWA mirrors Android Preparing → Calling AI → Reading result); OFF field mapping has web parity tests |
| Saved meals / recipes / favorites | shared | models + UI | recipes / saved | |
| Copy day / meal share | shared | `MealShare.kt` | `meal-share.js` | Native `nofud://`; PWA `#/add-meal?d=` |
| Diary JSON export/import 1.1 | shared | `export/Diary*` | `diary-format.js` | Contract: `contracts/diary-1.1.schema.json` |
| Body-metrics JSON 1.0 | shared | `export/BodyMetrics*` | `body-metrics-format.js` | Contract: `contracts/body-metrics-1.0.schema.json` |
| Deterministic goal formulas | shared | `UserProfile` + services | `nofud-core/formulas.js` | Goldens: `testdata/parity/formulas-expected.json` |
| AI Coach (BYOK cloud) | shared | coach + AI services | `lib/ai/` | Provider/model defaults must stay aligned |
| Health Connect | android-only | HC services | — | Measured TDEE path not on web |
| Widgets / notifications | android-only | glance / NotificationService | — | |
| On-device LLM (Gemma / LiteRT) | android-only | debug + production flag | — | See `ON_DEVICE_LLM.md` |
| Grounded entry | wip / android | grounding services | — | Feature flag; see `GROUNDED_ENTRY.md` |
| Full i18n pack | android-only | `res/values*` | EN-first copy | |
| 53-week pager | android-only | progress UI | — | |
| Service worker / installability | pwa-only | — | `sw.js`, manifest | |
| IndexedDB local store | pwa-only | DataStore | `db.js` | No cloud sync between clients |

## Maintenance rules

1. When adding a **user-visible** surface on one client, update this table in the same change.
2. When changing formulas or export JSON, follow [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) and bump / extend `contracts/` + `testdata/parity/` as needed.
3. Platform features (HC, widgets, on-device LLM) stay **android-only** by design — do not treat them as parity bugs.
