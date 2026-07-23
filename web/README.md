# NoFUD PWA (dev notes)

Scoped-down companion web app for NoFUD — diary logging, manual/photo/barcode
food entry, AI coach, profile/settings. Data-interoperable with the Android
app's JSON diary/body-metrics export format. Full plan context: see the
project's PWA planning doc (not checked into this repo).

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
- `app/src/lib/nofud-core/` — data-compatibility layer (formulas, diary/body-metrics
  export format, shared models), golden-tested against the real fixture files at
  repo root (`Fud-Food-Diary-*.json`, `FudAI-Weight-Import.json`). If Android's
  formula register or export shape changes, this module must be manually
  mirrored — see `docs/CALCULATION_METHODS.md`'s change checklist.
- `serve.mjs` — zero-dependency static file server for local testing.

## Status

Implemented: app shell + manifest + service worker (Phase 0), diary CRUD over
IndexedDB (Phase 1), the nofud-core data-compatibility layer with golden/
parity tests plus Settings import/export and TDEE display (Phase 2).

Not yet implemented: AI coach + photo/text analysis (Phase 3), barcode
scanning (Phase 4), PWA polish/Lighthouse pass (Phase 5), deploy wiring into
`scripts/deploy_pages.sh` (Phase 6). BYOK key storage (Web Crypto) is
designed but not built — needed before Phase 3.
