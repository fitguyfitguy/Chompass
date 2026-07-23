# NoFUD PWA (dev notes)

Companion web app for NoFUD — diary logging, manual/photo/barcode food entry,
AI coach, progress, onboarding, settings. Data-interoperable with the Android
app's JSON diary/body-metrics export format. Parity work targets daily-driver
UX (Home / Progress / Settings) while staying a no-bundler Web Components app.

## Stack

No SPA framework, no bundler — native Web Components + a hash router, served
as plain ES modules (`<script type="module">`). TypeScript is dev-time only
(`tsc --checkJs` over JSDoc-annotated `.js`), not a build or runtime
dependency; deploy is "copy files."

## Dev commands

Inside `devenv shell` (adds `node`/`tsc` to PATH):

```bash
pwa-serve       # serve web/app/ at http://localhost:8787/
pwa-test        # node --test over nofud-core golden + fixture-parity tests
pwa-typecheck   # tsc --checkJs --noEmit
```

Or directly:

```bash
node web/serve.mjs
node --test web/app/src/lib/nofud-core/__tests__/*.test.js
cd web && tsc --checkJs --noEmit -p tsconfig.json
```

## Layout

- `app/` — the deployed PWA (index.html, manifest, sw.js, css/, src/, vendor/, icons/)
- `app/src/lib/nofud-core/` — data-compatibility layer (formulas, FCAST/ADAPT forecast,
  diary/body-metrics export format, shared models), golden-tested against fixture
  files at repo root. If Android's formula register changes, mirror here — see
  `docs/CALCULATION_METHODS.md`'s change checklist.
- `app/src/lib/ai/` — BYOK AI coach + diary food analysis (`food-analyze.js`),
  encrypted key storage, providers, tools.
- `app/src/lib/off-client.js` + `barcode-scanner.js` — Open Food Facts + camera
  (`BarcodeDetector`) with manual digit fallback.
- `serve.mjs` — zero-dependency static file server for local testing.

## Status (Android parity track)

| Area | Notes |
|------|--------|
| Shell | Home / Progress / Coach / Settings nav, SVG icons, Compose-like tokens |
| Home | Week strip, calorie ring, macro bars, water row, FAB menu |
| Entry | Manual, barcode, photo/text AI → review form; fiber; recents |
| Progress | Ranges, log weight/BF, bars, history, forecast, measurements |
| Settings | Sectioned prefs, units/theme, onboarding gate, import/export/clear |
| Coach | Broader read tools, persistent chat |
| Deploy | `deploy_pages.sh` rsyncs `web/app/` → `website/public/app/` |

**Not ported (by design):** grounded entry WIP, on-device LLM, Health Connect,
notifications, widgets, full i18n pack.

Landing URL: `fitguy.codeberg.page/NoFUD/app/` (linked from site nav + Download).

Service worker cache: `nofud-shell-v3`.

### Manual PWA audit checklist

- Manifest: `name` / `short_name` / `start_url`+`scope` under `/NoFUD/app/`,
  `display: standalone`, icons (any + maskable).
- Theme color, viewport-fit, apple-touch meta tags.
- Nav links have visible text; FABs have `aria-label`.
- SW precaches shell assets; purges other `CACHE_NAME`s; never caches
  cross-origin AI / OFF requests.

**Run a real Lighthouse pass before treating installability as verified.**

Known trade-offs:
- Barcode fallback for non-Chromium is manual entry (no vendored JS decoder).
- Web Crypto non-extractable-key fallback for older/incognito contexts is still
  not implemented.
