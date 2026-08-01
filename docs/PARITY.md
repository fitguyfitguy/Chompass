# Android ↔ PWA feature parity

Living matrix of what is **shared**, **android-only**, **pwa-only**, or **wip**.
Android is the product of record; the PWA is a companion that reimplements daily-driver UX and stays **data-interoperable** via export contracts.

For formula / wire-format correctness (not feature lists), see:

- [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) — formula register + change checklist
- [`../testdata/parity/`](../testdata/parity/) — shared golden fixtures (formulas, wire samples, AI defaults, pref defaults, goal-prompt fragments)
- [`../contracts/`](../contracts/) — JSON Schemas for diary / body-metrics / meal-share (structural / version guard; see contracts README)
- `devenv tasks run release:check-parity` — PWA tests + schema validation + **this matrix’s structure check** (also runs inside `release:package`)

| Surface | Status | Android | PWA | Notes |
|---------|--------|---------|-----|-------|
| Home diary (meals, gauge, tubes/chips) | shared | `ui/home/` | `components/home-*` | Pref-driven tubes; fiber in default set; **53-week snap pager**; defaults locked in `testdata/parity/pref-defaults.json` |
| Progress (weight, BF, measurements, forecast) | shared | `ui/progress/` | `components/progress-*` | FCAST/ADAPT mirrored; interactive chart tips; equal range chips; **configurable default range** (`progressDefaultRangeId`, factory `1W`) with last-viewed precedence; display-only **7-day weight trend** overlay (not Adaptive Goals) |
| Settings (profile, goals, units, AI, data) | shared | `ui/settings/` hub + group sub-screens | `settings-view.js` hub (Personal / Goals / App / AI / Data / About) | Custom P/C/F pins; browser speech language; Android-only called out. Known drifts: accent default (`system` vs `teal`), primary AI when unset, fiber chip display |
| Onboarding | shared | `ui/onboarding/` | `components/onboarding-*` | Birthday + editable plan-ready macros; BF stored as fraction |
| Food entry (manual, barcode OFF, AI photo/text) | shared | entry flows | entry + `photo-ai-flow` | In-app camera + multi-photo review; **all images** sent to BYOK providers; photo AI **auto-scans barcodes** in stills → Open Food Facts → soft prompt context (Android `OffPromptContext` / PWA `off-prompt-context.js`) with **Checking barcodes…** progress step (parallel with JPEG prep on PWA); progressive phase overlay + **streamed partial field preview** when the provider supports SSE (`partial-json.js` / Android `FoodPartialJsonAssembler`); FoodResult-like review/Log with **quantity + unit picker + live macro scale** (`serving-units.js` / Android `ServingQuantityCard`); **editable meal constituents** with per-row g/unit (`ConstituentReconcile` / `constituents.js`); edit-entry **Ask AI to correct** with chips + before/after diff (Android `EditFoodEntrySheet`); voice sheet; barcode reticle. Android-only: serving-unit inference mode settings, AI_CALL unit inference, heuristic customization sheet, saved-photo reprocess |
| Saved meals / recipes / favorites | shared | models + UI | recipes / saved | Add Food heroes: Photo / Note / Recents; Frequent & Favorites in More |
| Copy day / meal share | shared | `MealShare.kt` | `meal-share.js` | Native `chompass://`; PWA `#/add-meal?d=`; wire `v` **2** (`contracts/meal-share-v2.schema.json`; import still accepts v1) |
| Diary JSON export/import 1.2 | shared | `export/Diary*` | `diary-format.js` | Contract: `contracts/diary-1.2.schema.json` (import still accepts 1.0/1.1); serving units + embedded constituents |
| Body-metrics JSON 1.0 | shared | `export/BodyMetrics*` | `body-metrics-format.js` | Contract: `contracts/body-metrics-1.0.schema.json` |
| User-hosted sync 1.1 (WebDAV / sync JSON) | shared | `sync/SyncRepository`, `export/SyncDocument` | `sync.js`, `chompass-core/sync-*.js` | Contract: `contracts/sync-1.1.schema.json` (import still accepts 1.0); excludes API keys and food photos |
| Deterministic goal formulas | shared | `UserProfile` + services | `chompass-core/formulas.js` | Goldens: `testdata/parity/formulas-expected.json`; custom macro pins honored |
| AI Coach (BYOK cloud) | shared | coach + AI services | `lib/ai/` | Overlapping provider/model defaults: `testdata/parity/ai-provider-defaults.json` (Gemini / Anthropic Haiku / OpenAI mini); goal-prompt formula lines: `goal-formula-prompt-fragments.json` |
| Health Connect | android-only | HC services | — | Measured TDEE path not on web |
| Widgets / notifications | android-only | glance / NotificationService | — | |
| On-device LLM (Gemma / LiteRT) | android-only | debug + production flag | — | See `ON_DEVICE_LLM.md` |
| Grounded entry | wip / android | grounding services | — | Feature flag; see `GROUNDED_ENTRY.md` |
| Full i18n pack | shared | `res/values*` | `lib/i18n/` + catalogs | Shared 15-locale contract [`testdata/parity/locales.json`](../testdata/parity/locales.json); see [`LOCALIZATION.md`](LOCALIZATION.md). PWA covers core surfaces; Android has fuller resource packs with EN fallback |
| Service worker / installability | pwa-only | — | `sw.js`, manifest | |
| IndexedDB local store | pwa-only | DataStore | `db.js` | Optional WebDAV sync is shared |
| Desktop chrome (rail + wider column) | pwa-only | — | `css/main.css` `@media (min-width: 900px)` | Left nav rail, `56rem` content, content-pinned FAB/coach/banner, constrained sheets; phone shell unchanged below breakpoint |

## Maintenance rules

1. When adding a **user-visible** surface on one client, update this table in the same change. `scripts/check_parity_matrix.py` (via `release:check-parity`) fails if required surfaces or Status values go missing/malformed.
2. When changing formulas or export JSON, follow [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) and bump / extend `contracts/` + `testdata/parity/` as needed.
3. When changing shared BYOK model defaults or AI goal-prompt formula fragments, update both clients **and** the matching fixture under `testdata/parity/`.
4. Platform features (HC, widgets, on-device LLM) stay **android-only** by design — do not treat them as parity bugs.
