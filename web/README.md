# Chompass PWA (dev notes)

Companion web app for Chompass — diary logging, manual/photo/barcode food entry,
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
pwa-test        # node --test over chompass-core golden + home-nutrients / OFF / meal-share parity tests
pwa-typecheck   # tsc --checkJs --noEmit
# or full gate (tests + typecheck + JSON Schema validation):
devenv tasks run release:check-parity
```

Or directly:

```bash
node web/serve.mjs
node --test web/app/src/lib/chompass-core/__tests__/*.test.js web/app/src/lib/__tests__/*.test.js
cd web && tsc --checkJs --noEmit -p tsconfig.json
```

## Layout

- `app/` — the deployed PWA (index.html, manifest, sw.js, css/, src/, vendor/, icons/)
- `app/src/lib/chompass-core/` — data-compatibility layer (formulas, FCAST/ADAPT forecast,
  diary/body-metrics export format, shared models), golden-tested against
  [`testdata/parity/`](../testdata/parity/) (and schemas in [`contracts/`](../contracts/)).
  If Android's formula register changes, mirror here — see
  `docs/CALCULATION_METHODS.md`'s change checklist and `docs/PARITY.md`.
- `app/src/lib/ai/` — BYOK AI coach + diary food analysis (`food-analyze.js`),
  encrypted key storage, providers, tools.
- `app/src/lib/off-client.js` + `barcode-scanner.js` — Open Food Facts + camera
  (`BarcodeDetector`, zxing-wasm fallback via `lib/barcode-detect.js`) with
  manual digit fallback.
- `serve.mjs` — zero-dependency static file server for local testing.

## Status (Android parity track)

Canonical matrix: [`docs/PARITY.md`](../docs/PARITY.md). Summary below for quick scanning.

| Area | Notes |
|------|--------|
| Shell | Home / Progress / Coach / Settings nav, SVG icons, Compose-like tokens; **desktop (≥900px): left nav rail + `56rem` column** |
| Home | Semicircle gauge, pref-driven nutrient tubes (default P/C/F/**Fiber**, count 4), meal glass cards, **53-week snap pager**, day swipe + **prev/next day buttons**, water **off by default** (goal 2000 ml), Add Food sheet (Photo/Note/Recents heroes), tap gauge → nutrition detail |
| Food rows | Overflow sheet (edit / meal / favorite / share / duplicate / delete), swipe-left delete + undo, swipe-right **favorite**; pref-driven macro chips |
| Entry | Manual, barcode (OFF + live reticle on Chromium), **in-app camera** + multi-photo review (≤10, all images to model), text/voice AI with phased wait overlay + single-flight lock → Review food / **Log**; **progressive meal draft**; expandable micros; Recents/Frequent/Favorites/Recipes; **manual active burn** |
| Saved meals | Recents / Frequent / Favorites tabs + recipe builder/log; favorites/recipes/share carry full micros |
| Copy / share | Copy-from-day multi-select; meal share encode + `#/add-meal?d=` import (https/hash bridge) |
| Progress | Glass chart cards, equal range chips (default 1W), tap tips, log weight/BF dialogs, history delete confirm, forecast, measurements; P/C/F/**fiber** averages |
| Settings | Profile/goals (custom kcal + P/C/F)/optional nutrients/units+meal times/home tubes/speech language/data/AI/about; Install (pwa-only); Android-only note for HC/notifications/widgets/on-device LLM |
| Coach | Broader read tools, persistent chat, glass proposals, SVG camera + Web Speech voice; propose_log_food micros; uses `primaryAiProvider` |
| UI polish | Bottom sheets (drag-dismiss; **desktop: centered ≤32rem**) + glass dialogs; Manrope; reduced-motion; home date persisted across tabs; calorie hero eaten/goal/remaining; colored meal P/C/F chips (fiber when selected) |
| Onboarding | Branded welcome, birthday/age, selection cards, optional body fat + goal BF, AI key step, building-plan animation, **editable** plan-ready targets |
| Deploy | `deploy_pages.sh` rsyncs `web/app/` → `website/public/app/` |

**Defaults note:** New IndexedDB installs match Android (`showWater: false`, water 2000 ml, home tubes include fiber@30g, `aiFallbackEnabled: true`, Gemini fallback `gemini-3.5-flash-lite`, accent `system`, primary AI `gemini`, serving-unit inference `gramsOnly`). Existing installs keep stored prefs; optional nutrient goals deep-merge with Android defaults for missing keys.

**Not ported (by design):** grounded entry WIP, on-device LLM, Health Connect measured burn,
notifications, widgets. Localization: core UI surfaces via `lib/i18n/` (15 locales;
see [`docs/LOCALIZATION.md`](../docs/LOCALIZATION.md)).

Landing URL: `fitguy.codeberg.page/Chompass/app/` (linked from site nav + Download).

Service worker cache: `chompass-shell-v7`.

### Desktop layout

At `min-width: 900px` the bottom tab bar becomes a **left nav rail**, `main.view`
widens to `56rem`, and fixed chrome (FAB, coach composer, install banner, toasts)
pins to the content column. Bottom sheets stay phone-height but cap at `32rem`
wide and center. Camera/webcam UX still uses pointer/hover detection (not this
breakpoint). Chromium (Chrome/Edge) is preferred for install + standalone windows;
manifest `orientation` is `any` so desktop landscape works.

### Manual PWA audit checklist

- Manifest: use `manifest.json` (not `.webmanifest`) so Codeberg
  git-pages serves `application/json` — Go/`mime.TypeByExtension` does not
  know `.webmanifest` (falls back to sniff → `text/plain` + `nosniff`).
  After a rename, also change file bytes (e.g. add `"id"`) so git-pages does
  not reuse the old blob’s stored Content-Type by hash. Fields: `id` /
  `name` / `short_name` / `start_url`+`scope` under `/Chompass/app/`,
  `display: standalone`, icons (any + maskable).
- Theme color, viewport-fit, apple-touch meta tags.
- Nav links have visible text; FABs have `aria-label`.
- SW precaches shell assets; purges other `CACHE_NAME`s; never caches
  cross-origin AI / OFF requests.

**Run a real Lighthouse pass before treating installability as verified.**

**Brave / Chrome Android:** there is usually **no auto install popup**. Use
the browser menu → **Add to Home screen** / **Install app**. After redeploy,
confirm live `Content-Type` for `/app/manifest.json` is `application/json`.

**Firefox Android:** no `beforeinstallprompt` — use menu → **Add to Home
screen**. If that does nothing, check Default apps → Home app is set.
**DuckDuckGo Android:** no full PWA install; menu shortcut may be a bookmark
only or a no-op on some launchers — prefer Chrome/Brave for install. The
in-app Install / Add to Home Screen control opens browser-specific help when
the Chromium install prompt is unavailable.

Known trade-offs:
- Live meal camera and barcode preview work on phone cameras and desktop
  webcams (HTTPS / localhost). Desktop uses landscape-friendly constraints,
  full-frame meal capture (no forced 3:4 crop), and a switch-camera control
  when multiple devices exist. File-picker fallbacks omit `capture=` on
  desktop so the OS dialog stays a normal file chooser.
- Barcode scanning is tiered: native `BarcodeDetector` when a startup canvas
  probe confirms it works; otherwise the vendored zxing-wasm reader
  (`app/vendor/zxing/`, ~1 MB wasm, lazy-loaded only on the fallback path —
  covers Firefox/Safari and Brave/degoogled Android where the API exists but
  detection is broken); manual digit entry as last resort. See
  `app/src/lib/barcode-detect.js`.
- Diary/body exports prefer Web Share (`files`) when available (Safari/iOS
  Save to Files), then fall back to `<a download>`.
- Web Crypto master key prefers a non-extractable CryptoKey in IndexedDB;
  Safari Private / restricted storage falls back to an extractable CryptoKey
  or JWK record (AES-GCM wrapping of provider keys is unchanged; JWK is weaker
  against XSS).
- Custom-scheme `chompass://add-meal` cold-start is native-only; PWA uses `#/add-meal?d=`.
- Web Speech API availability varies (Safari/Firefox uneven).
