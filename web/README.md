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
- `app/src/lib/ai/` — BYOK AI coach: `key-storage.js` (non-extractable Web
  Crypto AES-GCM wrapping key stored in IndexedDB, provider keys encrypted
  at rest), `providers.js` (fetch clients for Anthropic Messages, Gemini
  generateContent, and any OpenAI-compatible chat.completions endpoint),
  `tools.js` (tool schemas), `coach.js` (tool-calling loop — read-only
  `get_diary_context` auto-executes, `propose_log_*` tools only ever return
  a proposal, never write). `coach-view.js` renders proposals as confirm
  cards; food proposals route through the same `entry-form.js` review
  screen manual/barcode entries use.
- `app/src/lib/off-client.js` + `barcode-scanner.js` — Open Food Facts lookup
  and camera scanning via the `BarcodeDetector` API, with a manual
  digit-entry fallback for browsers that lack it (Firefox, Safari) instead
  of vendoring a JS decoder — see the scope note in `barcode-scanner.js`.
- `serve.mjs` — zero-dependency static file server for local testing.

## Status

Implemented: app shell + manifest + service worker (Phase 0), diary CRUD over
IndexedDB (Phase 1), the nofud-core data-compatibility layer with golden/
parity tests plus Settings import/export and TDEE display (Phase 2), BYOK AI
coach with encrypted key storage, tool-calling loop, and photo attachment
(Phase 3), and barcode scanning with Open Food Facts lookup (Phase 4) — all
routing proposed/scanned data through the existing entry-form review screen
so nothing is ever auto-committed.

Not yet implemented: PWA polish/Lighthouse pass (Phase 5) — icon/offline
audit is done but no formal Lighthouse run yet, and there are no progress
charts — and deploy wiring into `scripts/deploy_pages.sh` (Phase 6).

Known scope trade-offs (deliberate, not oversights):
- Barcode fallback for non-Chromium browsers is manual entry, not a full
  JS barcode decoder (would require vendoring a sizeable third-party
  library, cutting against the no-bundler/no-runtime-deps architecture).
- AI provider clients target the public Anthropic/Gemini/OpenAI-compatible
  REST shapes directly; they haven't been exercised against live API keys
  in this session (no network credentials available here) — verify with a
  real key before relying on them.
- `tsc --checkJs` has been configured (`pwa-typecheck` / `web/package.json`)
  but not actually run — needs a `devenv shell` reload to pick up the newly
  added `pkgs.nodePackages.typescript`.
- No browser-automation tool was available in this session, so installability,
  camera permission flow, and the coach/scanner UI have only been verified via
  `node --check` (syntax) and a static dev-server smoke test (asset 200s), not
  by actually driving them in a browser.
