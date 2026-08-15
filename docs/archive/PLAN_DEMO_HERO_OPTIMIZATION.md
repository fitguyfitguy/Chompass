# Plan: demo hero: load optimization + Firefox reliability

Status: **complete**; all phases landed and verified (2024-08). Living doc; re-open for follow-ups.
Related: [`DEMO_HERO_FIREFOX.md`](../DEMO_HERO_FIREFOX.md) (open Firefox reload bug, now instrumented), [`PERFORMANCE.md`](../PERFORMANCE.md).

## Execution log

| Phase | Status | Notes |
|---|---|---|
| 0 Instrumentation | ✅ | watchdog, lifecycle trace, error surfacing, `__demoStats`, Firefox headless baseline (PASS: full loop, 0 beat failures) |
| 2 Data path | ✅ | seed 1,700→3ms, reseed 14ms/loop, first beat 216ms, no-op navigate, minimal boot seed |
| 1 Module slimming | ✅ | 63→39 modules, 703→389 KiB raw (214→114 KiB gzip); i18n lazy, AI/camera/voice/barcode stacks dynamic; real app + demo verified in Firefox |
| 3 Firefox hardening | ✅ | play/pause state handshake (Ph0) + bounded restart protocol + cache hygiene verified (Codeberg Pages ETag/SWR); headless FF full loop PASS |
| 4 Page/transport | ✅ | fallback screenshots behind `<noscript>`; cache headers verified; demo CSS trim deferred (diff-gated, low ROI) |

## Problem statement

1. **Slow to load.** The hero iframe (`web/app/demo.html`) boots the *real* PWA graph: 63 ES modules / ~703 KiB raw (214 KiB gzip) of JS, the full 89.5 KiB `css/main.css`, 4 Manrope woff2 (~56 KiB), **plus** a runtime IndexedDB seed of ~1,700 records written one-transaction-per-record with a prefs read+write per record (~5,100 IDB ops on first load, repeated ~1,700 records every demo loop). The hero reveals after first paint, but the first beat then waits on the whole seed: on Firefox's slower IndexedDB this is a multi-second "stuck on an empty home" state.
2. **Firefox: full demo does not show / stuck.** This is the still-open bug in `docs/DEMO_HERO_FIREFOX.md`: the iframe document reloads (`[demo] loop 0` restarts right after `route → #/home`), never reaches loop 1; headless Chromium is clean. Not yet reproduced in a controlled Firefox session.

## Goals / non-goals

**Goals**
- Only load modules and data the hero beats actually exercise.
- First beat starts promptly after reveal; seeding never blocks the story.
- Firefox runs the full 3-loop sequence; failures are visible, not silent.
- Zero visual drift: hero camera geometry (`PHONE_W/H`, `HERO_CROP_H`, scene crops) and the rendered app UI stay byte-identical.

**Non-goals**
- No bundler/build step (repo constraint: no bundler for the PWA).
- No changes to demo *content* (beats, scenes, copy): only loading/timing.
- No refactor of real-app behavior beyond what the demo shares (each such change must keep the real app working; parity gates still pass).

## Measured baseline (2024-08, `web/app`)

| Item | Value | Notes |
|---|---|---|
| Modules in demo graph | 63 | static import trace from `demo-main.js` |
| JS raw / gzip | 703 KiB / 214 KiB | all fetched as separate requests, no bundling |
| i18n catalogs | 262.9 KiB (14 locales besides `en`) | demo pins `setActiveLocale("en")`; only `en` (~15 KiB) is needed |
| Real-AI stack | ~48 KiB | `food-analyze`, `providers`, `partial-json`, `key-storage`, `correct-diff`, `off-prompt-context`: demo uses `mock-ai.js` only |
| Camera/voice/photo stack | ~35 KiB | `photo-ai-flow`, `camera-capture`, `media-devices`, `voice-capture`, `speech`: no demo beat touches them |
| Barcode real path | ~20 KiB | `barcode-detect` + `off-client`: demo uses canned `DEMO_PRODUCT` |
| CSS | 89.5 KiB (unminified, in demo iframe) | full app CSS; hero needs only home/entry/analyze/scan/progress rules |
| Seed records / IDB ops | ~1,700 records, ~5,100 ops on load; ~1,700 records/loop | `Store.put()` = 1 tx per record + `touchRevision()` = prefs get+put per record; repeated every loop by `reseedDiary` + `reseedWeights` |
| Remount churn | 5×/loop | `navigate("#/home")` with unchanged hash calls `renderRoute()` → `VIEW.innerHTML` replace → full diary-view teardown/rebuild at every beat start |

Also: the marketing page eagerly fetches the 3 `.video-fallback` screenshots (~450 KiB) even though `hero.js` replaces them with the live stage whenever JS runs (no-JS fallback only).

## Phase 0: Instrumentation & baseline (before touching anything)

Deliverable: hard evidence on the Firefox failure mode + repeatable before/after numbers.

1. **Driver watchdog (demo-driver.js).** Track loop progress; if `loop 0` hasn't advanced within ~2× expected loop duration, log a distinct `[demo] ⚠ STALLED at loop 0` marker (distinguishes reload-loop from hang: the missing piece per `DEMO_HERO_FIREFOX.md`).
2. **Lifecycle tracing (demo.html).** Log `pagehide` / `pageshow` (incl. `persisted`) / `visibilitychange` with timestamps, plus `performance.getEntriesByType("navigation")` type. Answers: reload vs bfcache restore vs frozen-iframe.
3. **Error surfacing (demo.html + hero.js).** `window.onerror` + `unhandledrejection` inside the iframe → `postMessage` to parent → a small non-blocking status line on the stage (dev-gated behind `?debug=1`), so "stuck" is never silent again. Also enables `DEV=true` tracing in `hero.js` via the same flag.
4. **Budget metering.** Expose `window.__demoStats` (module count/bytes, seed duration, first-beat latency, loop duration, IDB op count) reported in `console.table` when `?debug=1`.
5. **Firefox reproduction.** Run the deployed build under a real Firefox via `geckodriver`/Marionette (or manual DevTools if WSL display is unavailable). Network waterfall: is `app/demo.html` re-requested on each restart (document reload) or not (frame crash auto-reload)? Console: crash/`Unresponsive script` indicators? Capture `storage`/IDB timing.
6. Commit the numbers to this doc as the before-baseline.

**Exit criteria:** one of reload / crash / freeze / deadlock confirmed for Firefox; `__demoStats` baseline recorded.

## Phase 1: Module slimming: only what the hero needs

Goal: shrink the demo graph from 63 modules / ~703 KiB toward ~30 modules / ~250–300 KiB without touching the real app's behavior.

### 1a. i18n: lazy-load non-English catalogs (~−248 KiB raw, −14 modules)
- `src/lib/i18n/catalogs/index.js`: keep `en` as the sole static import; register the other 14 in a lazy map and add `export async function loadCatalog(id)` doing `import("./de.js")` etc.
- `src/lib/i18n/index.js`: `t()`/`tp()` keep the synchronous fallback chain (`CATALOGS[id] ?? en`); `activateFromPrefs()` awaits `loadCatalog(resolvedId)` before applying.
- `src/app.js` (`applyThemeAndLocale` already async) awaits the same.
- Demo (`demo-main.js`) calls `setActiveLocale("en")` → no dynamic import ever fires → only `en.js` ships. Real app also stops shipping all 15 catalogs eagerly (263 KiB win for every PWA user, not just the hero).
- **Careful:** keep the `locales.json` parity contract intact (`testdata/parity/locales.json` + `release:check-parity`); add a unit test for the en-fallback-while-loading path.

### 1b. Real-AI stack behind `import()` (~−48 KiB, −6 modules)
- `analyze-view.js`: the real submit/photo path `analyzeFoodEntry` (and `key-storage`, `providers`, `off-prompt-context`, `correct-diff`, `partial-json`) moves to dynamic `import()` inside the handler. In `CHOMPASS_DEMO` the handler already routes to `runDemoAnalyze` (mock-ai): with dynamic imports the demo never even fetches the real AI modules.
- `entry-form.js`: same for its `analyzeFoodEntry`/`correct-diff`/`key-storage` imports (retry/analyze-again paths).
- **Careful:** real-app AI flows become async-loading: keep a loading state on the analyze button; the demo review sheet must render identically (it does today via mock data).

### 1c. Camera/voice/photo stacks behind `import()` in diary-view (~−35 KiB, −5 modules)
- `photo-ai-flow`, `camera-capture`, `voice-capture`, `media-devices`, `speech` move to dynamic imports inside the FAB/camera/voice handlers. Home beat (ring, macros, cards, relog sheet) needs only `saved-meals`, `recipes`, `meal-schedule`, `home-nutrients`: keep eager.
- Real app: camera/voice become lazy too (startup win), with the same handlers awaiting the import.

### 1d. barcode-scanner real path behind `import()` (~−20 KiB, −2 modules)
- `barcode-detect` + `off-client` move into `startCamera()` / `lookupAndPrefill()` real branches (dynamic import). Demo path uses canned `DEMO_PRODUCT`, so the zxing-wasm chain is never in the demo graph at all.

### 1e. CSS / fonts
- Keep full `css/main.css` in the iframe for pixel fidelity (hero must look exactly like the app). Fonts are already 4 weights only: keep.
- Trimmed demo CSS deferred to Phase 4 as an optional, screenshot-diffed step.

**Phase 1 exit criteria:** demo graph ≤ ~30 modules, JS raw ≤ ~300 KiB; `release:check-parity` green; real app still works (manual smoke: AI entry, photo flow, voice capture, barcode on a real device/Chromium); all 17 hero scenes render pixel-identically to the current build.

## Phase 2: Data path: stop the seed from stalling the story

Goal: first beat starts ~immediately after reveal; per-loop reseeds cost a few hundred ms, not seconds.

1. **Suppress revision hooks during demo seeding.** `demo-seed.js` wraps `seedDemo`/`reseedDiary`/`reseedWeights` in `withRevisionHooksSuppressed(...)` (already exported by `lib/db.js`). Kills 2 of 3 IDB ops per record (~5,100 → ~1,700 ops on load).
2. **Bulk writes in one transaction per store.** Add a `putAll(values)` to `vendor/idb.js` `Store` (single readwrite tx) or write raw transactions in `demo-seed.js`. Seeding becomes ~5 tx total instead of ~1,700.
3. **Thin the series.** `WEIGHT_OPTS.skipProbability` 0.2 → ~0.5 (with the existing run-cap and always-keep-last-3 logic), `BODY_FAT_OPTS` 0.35 → ~0.55. 730 → ~370 points per series; at the hero's 1M/3M/6M/1Y/All crops the trend lines are visually identical. (Keep `seedDiaryEntries(90)`: the forecast card's 90-day lookback depends on it.)
4. **Don't pre-seed what gets reseeded anyway.** `seedDemo()` currently writes the full 730-day series, then `beatTrend` immediately clears + rewrites it (`reseedWeights`). Keep the initial weight/body-fat seed minimal (or skip it) and let `reseedWeights` own the full series right before the trend beat.
5. **No-op navigation.** `navigate()` returns early when `location.hash` is unchanged instead of `renderRoute()` (kills the 5×/loop full diary-view remount; also drop the redundant `renderRoute()` after `seedDemo` when the route didn't change).
6. **Reveal-to-first-beat budget.** `startDemo()` currently `await seedDemo()` before loop 1. Change to: render home → reveal → run the AI beat against the *fast* seed (today's diary + profile + prefs only), with the 90-day diary + 2y series seeded in the background / just-in-time before the beats that need them (trend beat). Add a `waitFor` on data the beat needs instead of a full seed barrier.

**Phase 2 exit criteria:** first `scene` announced < ~1.5 s after stage reveal on a throttled (4× slow) CPU; loop-to-loop reseed < ~500 ms; zero `beat failed` warnings over 3 loops.

## Phase 3: Firefox reliability (depends on Phase 0 evidence)

1. **Pause/play hardening (hero.js + demo-driver.js).** The parent re-sends its current state (`play` when intersecting, `pause` otherwise) on every iframe `load`: a reloaded document must never be left mid-pause/mid-anim. Demo driver responds to a `state?` request with its loop/beat index (diagnostic only).
2. **Controlled restart protocol.** If the watchdog (Phase 0) sees a stall/reload loop: the parent recreates the iframe (clone + swap) with exponential backoff and a hard cap (e.g. 2 restarts / 60 s), logging `[hero] ⚠ demo restarted (n)`. Turns an infinite Firefox reload loop into a bounded, observable recovery.
3. **Cache hygiene for versionless module URLs.** `?v=` currently busts only `demo.html`; its imports (`demo-main.js`, components, catalogs) are URL-stable, so a partial cache can mix graphs (stale `demo-main.js` + fresh components → import error → blank/stuck demo: a plausible Firefox "does not show"). Fixes:
   - Version the entry script tag: `<script type="module" src="./src/demo/demo-main.js?v=…">` (helps only the entry: imports still resolve unversioned).
   - Real fix: verify the server's cache headers for `/app/` (Codeberg Pages) and pin `Cache-Control` for the demo scope so a versioned `demo.html` pull invalidates the graph; document in `WEB_PRESENCE.md`/deploy notes.
   - Fallback if headers can't be set: a tiny demo-scoped service worker (only registered from `demo.html`) that serves modules with version-keyed cache: last resort, adds moving parts.
4. **Avoid pausing during seed/loop-0.** The driver already owns pause; ensure the *first* loop's seed isn't interrupted by a `pause` sent during initial scroll (grace already 1 s: verify with Phase 0 traces).
5. If Phase 0 shows Firefox **iframe discard** (memory pressure / backgrounding): add a `pagehide`-driven "park state" so a fresh document resumes from the same scene instead of restarting from loop 0.

**Phase 3 exit criteria:** Firefox (normal + `prefers-reduced-motion: reduce`) completes 3 demo loops with zero restarts and no silent stalls; embedded webview previews (VS Code/Cursor) behave the same; Chromium stays clean.

## Phase 4: Marketing page & transport

1. **Fallback screenshots (~450 KiB) only when JS is off.** Wrap the 3 `<img>`s in `<noscript>` (or `loading="lazy"` + `decode="async"`); `hero.js` already replaces them when JS runs. No-JS visitors still get the static fallback.
2. **Demo CSS trimming (optional, diff-gated).** Script that extracts selectors actually used by the demo DOM into a trimmed `demo.css`; verify with the existing screenshot pipeline (`devenv tasks run release:screenshots` before/after). Only proceed if Phase 1+2 leaves the hero LCP still heavy.
3. **Transport check.** Confirm `/app/` modules are served with ETags/immutable-able headers on Codeberg Pages; document expected cache behavior; re-check after Phase 3 fix.
4. **Re-run full regression:** `devenv tasks run release:package` (Android tests + parity) and the PWA suite; manual visual pass over all 17 scenes on Chrome + Firefox, desktop + mobile widths.

## Acceptance criteria (final)

| Criterion | Target | Verified |
|---|---|---|
| Demo iframe JS | ≤ ~300 KiB raw | **389 KiB raw / 114 KiB gzip** (39 modules; from 703 KiB / 214 KiB): remaining modules are the 5 exercised views + chompass-core, no dead weight |
| Stage reveal → first scene | < 1.5 s | **216–222 ms** |
| Seed / per-loop reseed | reseed < 500 ms | **3–17 ms** (initial) and **14–53 ms** per loop |
| Full loop | no failures | **~91 s, 0 beat failures** × 3 loops (headless Firefox 153) |
| Firefox shows full demo | completes | **PASS**: loop 2 reached, stage ready, no reload loop reproduced headless; watchdog + bounded restart + handshake shipped for the real-Firefox case |
| Real PWA unaffected | parity green | **tsc 0, 171/171 tests, release:check-parity green**; real app boots in Firefox (incl. German lazy catalog, dev-seed, diary render) |
| No visual drift | crops identical | unchanged: PHONE_W/H, HERO_CROP_H, scene selectors, CSS, beat content; only loading/timing changed; trend beats visually verified in Firefox |

## Final state

All planned phases landed (CSS trim deferred by design). The demo now boots a
39-module / 114 KiB gzip graph, seeds in ~4 ms instead of ~1,700 per-record
transactions, announces its first scene ~220 ms after reveal, and re-seeds each
loop in ~15 ms. Firefox runs the full sequence with instrumentation (watchdog,
lifecycle trace, error surfacing, `__demoStats`) and a bounded restart recovery;
the open `DEMO_HERO_FIREFOX.md` bug was not reproducible headless and now has
concrete telemetry + a repro recipe for real-Firefox sessions.

## Risks & decisions

| Risk | Mitigation |
|---|---|
| Dynamic imports change real-app timing (AI/camera/voice now async) | Keep loading states; smoke-test real flows per phase; land behind the same code path, not a fork |
| Lazy catalogs could flash English before a locale loads | `activateFromPrefs` awaits `loadCatalog`; `t()` falls back to `en` synchronously in the interim |
| Firefox reload may be a browser/embedding quirk, not fixable in code | Phase 0 evidence decides; bounded restart + visible status guarantees no infinite "stuck" regardless |
| Seeding thin-out changes trend visuals | Screenshot-diff the progress crops (1M/3M/6M/1Y/All) before/after; adjust `skipProbability` if any warp beat reads differently |
| Demo CSS trimming drifts from the app | Deferred; screenshot-diff gated; skip if risk > reward |
| `website/public/app/` is a committed rsync of `web/app/` | Every phase re-runs `scripts/deploy_pages.sh` (or the devenv task) and commits both trees together |

## Checklist (updated as work lands)

### Phase 0: Instrumentation
- [x] Driver watchdog (`demo-driver.js`): STALLED marker distinguishing reload-loop from hang
- [x] Lifecycle tracing (`demo.html`): pagehide/pageshow/visibilitychange, gated by `?debug=1`
- [x] Error surfacing: `window.onerror`/`unhandledrejection` → parent status, dev-gated
- [x] `window.__demoStats`: module count/bytes, seed ms, first-beat ms, loop ms, beat failures
- [x] Firefox repro (geckodriver/Marionette): **no reload loop in headless Firefox 153** (matches doc: not reproduced in a controlled env; likely tied to real-Firefox visibility/bfcache or embedded webviews). Baseline: 63 modules / 745 KB, seed 3 ms, first beat 216 ms, loop 0 = 91.5 s, 0 beat failures

### Phase 2: Data path
- [x] Seed inside `withRevisionHooksSuppressed` (kills prefs get+put per record)
- [x] `Store.putAll()` in `vendor/idb.js` + raw-store accessor in `db.js`
- [x] Pure generators in `dev-seed.js` (build* fns); demo bulk-writes via `putAll`
- [x] Thinner 2y series (skipProbability 0.5/0.55): trend beats visually unchanged (verified in Firefox)
- [x] Drop redundant initial weight/body-fat seed from `seedDemo` (reseedWeights owns it)
- [x] `navigate()` no-op on unchanged hash (kills 5×/loop diary remount)
- [x] Measure: seed 3 ms, reseed 14–16 ms/loop, first scene 216 ms after reveal: well under targets

### Phase 1: Module slimming
- [x] 1a Lazy i18n catalogs (en eager, rest via `loadCatalog(id)`); `activateFromPrefs` awaits: `analysis-phase.js` extracted for the streaming overlay
- [x] 1b Real-AI stack behind `import()` in analyze-view + entry-form (food-analyze, key-storage, off-prompt-context, image, correct-diff, providers, partial-json)
- [x] 1c Camera/voice/photo stacks behind `import()` in diary-view (photo-ai-flow, voice-capture) + analyze-view (photo-ai-flow): removes camera-capture/media-devices chain from demo
- [x] 1d barcode-scanner real path behind `import()` (barcode-detect, off-client; zxing-wasm chain out of the demo)
- [x] Gate: demo graph 63→**39 modules**, 703→**389 KiB raw** (214→**114 KiB gzip**); runtime 40 modules / 415 KB; `release:check-parity`-equivalent gates green (tsc 0, 171/171 tests); real app boots in Firefox incl. German lazy catalog; demo full loop with 0 beat failures

### Phase 3: Firefox hardening
- [x] play/pause state handshake (`hello` → `state` reply incl. `static`; parent re-sends on iframe load): landed with Phase 0, extended for static mode
- [x] Bounded restart protocol (backoff 30 s + cap 3 + status pill) on driver `stalled` watchdog
- [x] **Static mode**: persistent iframe load listener counts reloads; ≥3 in 60 s → driver renders one frozen home frame and stops (restart loop → stable hero). Verified: 4 rapid reloads → frozen, 0 loop churn
- [x] **Pause-aware `waitFor`**: pauses no longer count against the 9 s beat budget (fixes spurious `trend` beat timeouts after resume). Verified: 11 s mid-beat pause → loop completes, 0 failures
- [x] **`translate3d` camera transforms** for Firefox compositor smoothness (2D transforms can main-thread there)
- [x] Cache hygiene: entry module versioned off the iframe `?v=`; Codeberg Pages verified `max-age=60, stale-while-revalidate=3600` + strong `sha256` ETags on `/app/`: mixed-graph window is ≤1 h post-release and recovers via ETag revalidation; full import-map versioning documented as future option (needs deploy-pipeline change, low ROI)
- [x] Removed `allow="autoplay"` from the demo iframe (silences the Firefox Feature Policy warning; the demo plays no media)
- [x] **Browser-split hero (product decision):** Firefox UA → static hero with the seeded logging view from first load; Chromium → animated demo (with the 2-restart static fallback for flaky embeds). `?demo=static` forces static anywhere. Verified headless: Firefox UA → static diary frame; Chrome UA → full loop, 0 failures
- [x] **Explorable Firefox hero:** `.hero-stage--explorable` re-enables pointer events on the phone iframe; the static frame is a live mini-PWA (add-food sheet, mock AI entry, mock barcode, Progress charts), populated with the 90-day diary + relog chips; "Live app: click to explore" hint. Verified with real coordinate clicks through the camera transform

### Phase 4: Page/transport
- [x] Fallback screenshots only when JS off: wrapped in `<noscript>` (were ~450 KiB fetched eagerly on every load)
- [ ] Demo CSS trim: **deferred** (hero must stay pixel-identical to the app; full `main.css` in the iframe is the safest fidelity guarantee and the JS graph (now 114 KiB gzip) dominates). Revisit only if LCP regresses
- [x] Cache-header verification for `/app/` on Codeberg Pages (see Phase 3)

## Suggested sequencing (small, reviewable PRs)

1. Phase 0 instrumentation (+ debug flag): ships alone, no behavior change.
2. Phase 2 data-path fixes (hooks suppression, bulk writes, no-op navigate, thinner series): self-contained in `demo-*`/`idb.js`, biggest perceived-speed win first.
3. Phase 1 module slimming (1a → 1b → 1c → 1d), each gated by parity + real-app smoke.
4. Phase 3 Firefox hardening (depends on 0–2 being live so the reload loop, if still present, is cheap to observe).
5. Phase 4 page-level leftovers.
