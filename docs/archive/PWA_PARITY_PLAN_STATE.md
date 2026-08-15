# PWA ↔ Android visual parity plan — state

Last updated: 2026-08-07 (Phases 1–6 done, committed, pushed to `origin/main`).

## Goal

Bring the Chompass PWA UI (`web/app/`) to visual parity with the Android native
app (`android/app/`). Android is the product of record; the PWA must stay
data-interoperable (shared JSON contracts) and a no-bundler Web Components app.
Keep PWA-only structural features (desktop rail, install banner, day-nav
chevrons, per-row ⋮ menu, Frequent/Favorites tiles) — restyle, don't remove.

## Phase status

| # | Phase | Status | Commit(s) |
|---|-------|--------|-----------|
| 1 | Home hero / week strip / gauge parity | ✅ done | 4cff75d (+ earlier) |
| 2 | Food log rows / meal cards / macros | ✅ done | 4cff75d |
| 3 | Bottom nav M3 parity | ✅ done | cce9a6f |
| 4 | Progress screen | ✅ done | e8f44bb, f0f4207 |
| 5 | Settings hub | ✅ done | f7445b6 |
| 6 | Add-food sheet | ✅ done | f7445b6 |
| 7 | Coach shell (`coach-view.js`) | ⏳ **next** | — |
| 8 | Final verification (i18n sync, parity gate, screenshot diff, PARITY.md) | ⏳ pending | — |

Every phase ends with: tsc clean, 171/171 PWA tests, `devenv tasks run
release:check-parity` green, fresh-profile screenshot + vision diff vs
`release-screenshots/*.png`.

## What changed per phase (resume cheat sheet)

- **P1/2 (home):** `--font-display/--font-body` → system-ui; M3 tokens
  (`--nav-pill`, `--nav-icon-active`, `--nav-bar`); sentence-case micro-headers;
  week strip = Android `WeekStrip.kt` (36px circle, gradient+glow selected,
  hollow teal ring today); gauge hero floats, `of 2016` (no "kcal"), burn
  caption `🔥 380 of 560 active` (pref-gated), over-state teal-clamped + "0
  left"; ⓘ → `GAUGE_INFO_ICON` Material SVG (ⓘ is a tofu box in headless
  Chromium); macro tubes `64g left`/`24g over`; "View More ›"; meal headers
  `436 kcal · 13P 74C 7F`; food rows name + lowercase locale time, teal kcal +
  `· 24g` serving, tinted macro pills. Desktop hero tap-bar is a
  `<div role="button" tabindex="0">` (buttons can't wrap block content — the
  nested-`<button>` bug escaped the mobile container as ghost bars).
- **P3 (nav):** `.nav-pill` 64×32dp radius 999px (`--nav-pill` secondaryContainer
  active), single filled Material icons (Android renders filled always — no
  outline swap), `--nav-h` 5rem, no border-top, `--nav-bar` = exact Android
  surfaceContainer (#211f26 dark / #f3edf7 light, both `prefers-color-scheme`
  and `[data-theme]` blocks), desktop rail restyled to M3 NavigationRail
  (6rem, same pill). `applyNavLabels()` in `app.js` must preserve `.nav-pill`
  (it used to wipe everything but the first svg).
- **P4 (progress):** no title; `.range-chips` = 6 equal M3 FilterChips;
  `.metric-toggle` segmented Weight/Body Fat (track 1.125rem radius, active =
  teal gradient); `.progress-head` title + teal `+ Log Weight` (addCircle);
  boxless `.stat-badge` 4-col (Current/Goal/Net Change/Average, units + signed
  net); `.chart-legend` dot + dashed swatch + "needs at least 2 weigh-in days"
  hint; `charts.js` `lineChartSvg` gained `grid` option (3 horizontal
  `.chart-grid` lines); `.history-link` rows (teal ListAlt icon, NO bubble —
  Android `FudIconBubble` is bare) + chevron; Calories card `Avg: N kcal`;
  `.macro-progress` 3 rows (Protein/Carbs/Fat — no Fiber) "100g / 147g" + 8dp
  track/fill; fixed `toISOString()` UTC-shift bug in `todayIso`/`shiftDate`
  (Europe/Berlin rendered Sat-start weeks) — `localIsoDate(d)` helper, same fix
  as diary-view. New keys: `progress.log_weight`, `progress.stat_*`,
  `progress.*_history`, `progress.history_count_format`, `progress.avg_format`,
  `progress.macro_averages`, `progress.macro_progress_format`,
  `progress.log_first_*`, `progress.no_food`, `progress.metric_body_fat`
  ("Body Fat" — Android title case, used for toggle + BF card title).
  `dev-seed.js` `seedAll()` now also seeds 42d body fat (Android
  `seed_body_metrics` parity).
- **P5 (settings hub):** `.screen-title` → 1.75rem (28sp); `.settings-hub` glass
  card, rows = `.settings-hub__row` (28dp teal icon bubble, label 1rem/500 +
  subtitle 0.78rem 55%, chevron 40%), 1px dividers; 5 groups (Personal Info,
  Goals & Nutrition, App & Display, AI & Speech, Health, Data & Sync) + second
  card for About; Android-exact summaries ("Diet, macros, adaptive goals",
  "Appearance, home, notifications"); `settings.hub.data_hint` = "Export, sync,
  backup" (deliberately NOT "Health Connect, export, sync" — HC is Android-only).
  Icons = Android `Icons.Outlined` paths (person/equalizer/settings/smartToy/
  folderOpen/info). Subpages unchanged.
- **P6 (add-food sheet, diary-view.js):** hero tiles i18n'd Android-exact:
  Photo/"Camera or gallery", Note/"Describe what you ate", Recents/"Recents &
  favorites" (`.add-food-tile--hero`: 96dp min, 26dp bubble, 1rem radius);
  quick-relog section "Log again" + hint "Favorites and past meals. Faster than
  rescanning." + horizontal chips (already Android-style); empty state =
  Android string; "More ways to log" `.add-food-grid` 4-col compact (64dp min,
  12dp radius, 20dp bubble): Voice/Barcode/Manual/Copy from day +
  Frequent/Favorites/Active burn + spacer (Frequent/Favorites are PWA-only,
  kept). Tiles are solid `var(--surface)`, **`border: 0`** — removing the old
  border exposed the UA `2px outset` button default. New keys: 16 ×
  `add_food.*`. `tile()` gained a `hero` param.

## i18n rules (still in force)

- Strings live in `web/app/src/lib/i18n/catalogs/*.js`, 16 locales (ar az de en
  es fr hi it ja ko nl pt-BR ro ru zh-CN), gated by `testdata/parity/locales.json`.
- Every EN key must exist in all 15 catalogs (`i18n.test.js`). Value changes too
  — update all catalogs, not just EN. Scripts used: `/tmp/insert_progress_keys.py`,
  `/tmp/add_food_keys.py`, `/tmp/data_hint.py` (pattern: regex-replace value or
  insert after an anchor key, then `node -e
  "import('./web/app/src/lib/i18n/catalogs/index.js').then(m =>
  console.log(Object.keys(m.CATALOGS).length))"` to verify).
- Apply text tweaks at the format-function level where possible (see
  `home-nutrients.js` for `tubeStatus`/`formatMacroChipLine`/`formatFoodPills`).

## Environment gotchas (learned the hard way)

- **Port 8787 is NOT chompass** — a `chompass-demo-weighin` dev server holds it.
  Chompass PWA dev server: `PORT=8788 nohup node web/serve.mjs` (serve.mjs
  serves `web/app/`, so URLs are `/src/...`, hash routes `#/...`).
- **Always use unique `--user-data-dir` per run** — the SW serves cache-first
  and stale `index.html`/`app.js` masked fixes twice. Also: if two runs share a
  `--remote-debugging-port`, the second script silently connects to the FIRST
  (stale) browser — kill leftovers (`pkill -f "remote-debugging-port=9"`) or
  bump the port.
- CLI `--screenshot` fires before async render; use CDP with ~9s waits after
  seed/navigation. Seed via `#/home?seed=1` first (fresh profile lands on
  onboarding otherwise), then `location.hash = "#/settings"` etc.
- Screenshot scripts: `/tmp/pwa-shots/progress.mjs` (args: mode out segment),
  `/tmp/pwa-shots/phase56.mjs` (mode page out), `tilecheck.mjs`, `dbg.mjs`.
- Vision diffs: `pi -p --no-skills -nt --no-context-files --model
  google/gemini-3.5-flash-lite @release-screenshots/<android>.png
  @/tmp/pwa-shots/<pwa>.png "prompt"`. Android shots: 01/05 home, 02/06 progress,
  03/08 coach, 04/09 settings, 07/10 add-food (light/dark).
- Verification: `devenv shell bash -lc 'cd web && tsc --checkJs --noEmit -p
  tsconfig.json && node --test app/src/lib/chompass-core/__tests__/*.test.js
  app/src/lib/__tests__/*.test.js'` (171 tests), then `devenv tasks run
  release:check-parity`.

## Phase 7 — Coach shell (next)

Android sources: `android/app/src/main/java/app/chompass/ui/coach/`
(`CoachScreen.kt`, `CoachInputBar.kt`, `CoachMessageBubble.kt`,
`CoachViewModel.kt`, `CoachVoiceInput.kt`); screenshots `03-coach-light.png`,
`08-coach-dark.png`. PWA: `web/app/src/components/coach-view.js` (205 lines,
still has `.screen-title`).

Per the plan: persistent header + reset icon, outlined suggestion chips, pill
input with circular send button. Keep PWA-only features (install banner,
rail). Coach is text-only for the PWA (on-device LLM is Android-only).

## Phase 8 — Final verification

- i18n catalog sync re-check (all keys in all 15 catalogs).
- Full gate: tsc + 171 tests + `release:check-parity`.
- Fresh-profile screenshot diff of ALL tabs (home, progress, coach, settings,
  add-food) vs `release-screenshots/`, dark + light.
- Update `docs/PARITY.md` notes if any feature-matrix row changed.

## Environment / server note

The maintainer committed each phase's work as `fitguy` (commit-msg hook rejects
AI trailers — never add `Co-authored-by: Cursor` etc.). Work is pushed to
`origin/main` (Codeberg). `docs/local/` is the right home for stateful docs
like this one.
