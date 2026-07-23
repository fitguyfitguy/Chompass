# NoFUD PWA (dev notes)

Companion web app for NoFUD — diary logging, manual/photo/barcode food entry,
AI coach, progress, onboarding, settings. Data-interoperable with the Android
app's JSON diary/body-metrics export format. Parity work targets daily-driver
UX (Home / Progress / Settings) while staying a no-bundler Web Components app.

**Feature matrix:** [`docs/PARITY.md`](../docs/PARITY.md).  
**Shared fixtures / schemas:** [`testdata/parity/`](../testdata/parity/), [`contracts/`](../contracts/).  
**Gate:** `devenv tasks run release:check-parity` (also part of `release:package`).

## Stack

No SPA framework, no bundler — native Web Components + a hash router, served
as plain ES modules (`<script type="module">`). TypeScript is dev-time only
(`tsc --checkJs` over JSDoc-annotated `.js`), not a build or runtime
dependency; deploy is "copy files."

## Dev commands

Inside `devenv shell` (adds `node`/`tsc` to PATH):

```bash
pwa-serve       # serve web/app/ at http://localhost:8787/
pwa-test        # node --test over nofud-core golden + home-nutrients / OFF / meal-share parity tests
pwa-typecheck   # tsc --checkJs --noEmit
# or full gate (tests + typecheck + JSON Schema validation):
devenv tasks run release:check-parity
```

Or directly:

```bash
node web/serve.mjs
node --test web/app/src/lib/nofud-core/__tests__/*.test.js web/app/src/lib/__tests__/*.test.js
cd web && tsc --checkJs --noEmit -p tsconfig.json
```

## Layout

- `app/` — the deployed PWA (index.html, manifest, sw.js, css/, src/, vendor/, icons/)
- `app/src/lib/nofud-core/` — data-compatibility layer (formulas, FCAST/ADAPT forecast,
  diary/body-metrics export format, shared models), golden-tested against
  [`testdata/parity/`](../testdata/parity/) (and schemas in [`contracts/`](../contracts/)).
  If Android's formula register changes, mirror here — see
  `docs/CALCULATION_METHODS.md`'s change checklist and `docs/PARITY.md`.
- `app/src/lib/ai/` — BYOK AI coach + diary food analysis (`food-analyze.js`),
  encrypted key storage, providers, tools.
- `app/src/lib/off-client.js` + `barcode-scanner.js` — Open Food Facts + camera
  (`BarcodeDetector`) with manual digit fallback.
- `serve.mjs` — zero-dependency static file server for local testing.

## Status (Android parity track)

Canonical matrix: [`docs/PARITY.md`](../docs/PARITY.md). Summary below for quick scanning.

| Area | Notes |
|------|--------|
| Shell | Home / Progress / Coach / Settings nav, SVG icons, Compose-like tokens |
| Home | Semicircle gauge, pref-driven nutrient tubes (default P/C/F/**Fiber**, count 4), meal glass cards, week prev/next, day swipe, water **off by default** (goal 2000 ml), Add Food sheet, tap gauge → nutrition detail |
| Food rows | Overflow sheet (edit / meal / favorite / share / duplicate / delete), swipe-left delete + undo, swipe-right duplicate; pref-driven macro chips |
| Entry | Manual, barcode (OFF fiber + micros), photo/text AI with phased wait overlay (Preparing → Calling AI → Reading result) + single-flight lock → review form; expandable micros; Recents/Frequent/Favorites/Recipes; subpage chrome |
| Saved meals | Recents / Frequent / Favorites tabs + recipe builder/log; favorites/recipes/share carry full micros |
| Copy / share | Copy-from-day multi-select; meal share encode + `#/add-meal?d=` import (https/hash bridge) |
| Progress | Glass chart cards, ranges, log weight/BF dialogs, history delete confirm, forecast, measurements (incl. calf/wrist); P/C/F/**fiber** averages |
| Settings | Profile/goals/optional nutrients (Android defaults)/units+meal times/home tube+chip pickers/data/AI (curated Gemini models; default `gemini-3.6-flash` + fallback `gemini-3.5-flash-lite`)/about; CSV+Markdown diary + body CSV; custom AI instructions + fallback |
| Coach | Broader read tools, persistent chat, glass proposals, SVG camera + Web Speech voice; propose_log_food micros; uses `primaryAiProvider` |
| UI polish | Bottom sheets (drag-dismiss) + glass dialogs; Manrope; reduced-motion; home date persisted across tabs; form field overflow fixed; calorie hero eaten/goal/remaining; colored meal P/C/F chips (fiber omitted on rows) |
| Onboarding | Branded welcome, selection cards, optional body fat + AI key step, building-plan animation, plan-ready targets |
| Deploy | `deploy_pages.sh` rsyncs `web/app/` → `website/public/app/` |

**Defaults note:** New IndexedDB installs match Android (`showWater: false`, water 2000 ml, home tubes include fiber@30g, `aiFallbackEnabled: true`, Gemini fallback `gemini-3.5-flash-lite`). Existing installs keep stored prefs; optional nutrient goals deep-merge with Android defaults for missing keys.

**Not ported (by design):** grounded entry WIP, on-device LLM, Health Connect,
notifications, widgets, full i18n pack, 53-week pager.

Landing URL: `fitguy.codeberg.page/NoFUD/app/` (linked from site nav + Download).

Service worker cache: `nofud-shell-v8`.

### Manual PWA audit checklist

- Manifest: use `manifest.json` (not `.webmanifest`) so Codeberg
  git-pages serves `application/json` — Go/`mime.TypeByExtension` does not
  know `.webmanifest` (falls back to sniff → `text/plain` + `nosniff`).
  After a rename, also change file bytes (e.g. add `"id"`) so git-pages does
  not reuse the old blob’s stored Content-Type by hash. Fields: `id` /
  `name` / `short_name` / `start_url`+`scope` under `/NoFUD/app/`,
  `display: standalone`, icons (any + maskable).
- Theme color, viewport-fit, apple-touch meta tags.
- Nav links have visible text; FABs have `aria-label`.
- SW precaches shell assets; purges other `CACHE_NAME`s; never caches
  cross-origin AI / OFF requests.

**Run a real Lighthouse pass before treating installability as verified.**

**Brave / Chrome Android:** there is usually **no auto install popup**. Use
the browser menu → **Add to Home screen** / **Install app**. After redeploy,
confirm live `Content-Type` for `/app/manifest.json` is `application/json`.

Known trade-offs:
- Barcode fallback for non-Chromium is manual entry (no vendored JS decoder —
  keeps the no-bundler / no-runtime-deps architecture).
- Web Crypto non-extractable-key fallback for older/incognito contexts is still
  not implemented.
- Custom-scheme `nofud://add-meal` cold-start is native-only; PWA uses `#/add-meal?d=`.
- Web Speech API availability varies (Safari/Firefox uneven).
