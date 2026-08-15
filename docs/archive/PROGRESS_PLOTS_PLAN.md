# Progress Plots Plan — Customizable body-measurement trends (Codeberg #18)

Status: **Executed 2026-08-13** · Scope: Android only (`ui/progress/` + `ui/settings/` + prefs)
Goal: close the [#18](https://codeberg.org/fitguy/Chompass/issues/18) gap — the reporter explicitly
wants a graph view for how body measurements (waist, hips, …) change over time. Core data entry
already exists (Settings → Personal Info → Body measurements, 8 sites + history + derived metrics);
what's missing is a **per-site delta/trend view** on the Progress tab.

Design: **optional, customizable progress plots** — off by default, enabled per-site from a new
**Customize Progress** settings sub-screen, one compact trend chart per enabled site on the
Progress tab (respecting the existing 1W/1M/3M/6M/1Y/All range picker). No contract, formula,
or PWA changes; export already carries `measurements[]`.

**Execution log:** All steps landed. Verification: `./gradlew test` (442 unit tests incl. new
`MeasurementTrendTest`), `assembleDebug` + `validateDebugScreenshotTest` green, `release:check-parity`
passed (PARITY matrix 22 surfaces, locales, pref-defaults). New dedicated `progress-plots` screenshot
previews (light + dark) vision-verified — Waist/Hips/Chest cards render full trend charts with dots,
y-axis ticks and date labels in both themes; added to the release export lists + README gallery.
Deviation from §2.2: the plot card header shows the latest value (Δ lives in the **Net change**
badge, no duplication); sites render only when the range holds ≥ 1 entry. `pref-defaults.json` gained
`progressMeasurementSites: []` with a `PrefDefaultsParityTest` assertion (off-by-default semantic).
`TestDataSeeder.seedBodyMetrics()` now also seeds 90 days of weekly waist/hips/chest/neck tape
readings for device verification. Device pass still pending (Windows adb).

---

## 1. Current state (what exists)

| Piece | Where | Notes |
|-------|-------|-------|
| Measurement data model | `models/BodyMeasurement.kt` | 8 sites (cm), `value(site)` / `setting(site, cm)` accessors, derived metrics |
| Repository | `data/BodyMeasurementRepository.kt` | `entries: Flow<List<BodyMeasurement>>` |
| Entry UI | `ui/progress/BodyMeasurementsScreen.kt` | Wheel picker per site + history sheet (absolute values only — no deltas, no chart) |
| Progress tab data | `ui/progress/ProgressViewModel.kt` | **Already collects `bodyMeasurements`** into `BaseProgressData`/`ProgressUiState` — never rendered |
| Chart primitives | `ui/progress/ProgressCharts.kt` | `WeightChartModel`/`BodyFatChartModel`, `downsampleTrend`, `niceAxisTicks`, `TrendXAxisLabels`, deferred chart pattern (`DeferredChart`, `withFrameNanos` phases) |
| Stats row | `ui/progress/ProgressPrimitives.kt` | `StatBadgeRow`/`StatBadge` (Current, Goal, Net Change, Average) |
| Range pref | `SettingsAppSection.kt` row + `SettingsSheets.kt` `PROGRESS_DEFAULT_RANGE` `ListSheet` + `SettingsViewModel` | `progressDefaultRangeId` (factory `1W`), last-viewed precedence |
| Units | `prefs.heightUnit` (`cm`/`ftin`) | `BodyMeasurementsScreen` already uses it for cm-vs-in display; reuse for plots |
| Seed/debug | `services/TestDataSeeder.seedBodyMetrics()` | Extend with a few `BodyMeasurement` entries for device verification |

Nothing renders today if the user never opens Personal Info → measurements — the Progress tab is
unaffected. The new plots are **additive and off by default**, so existing users see zero change.

## 2. Design

### 2.1 Settings: new "Customize Progress" sub-screen

- New route `ChompassRoutes.CUSTOMIZE_PROGRESS = "settings/customize-progress"`, registered in
  `NoFUDNavHost.kt` (same pattern as `HOME_DISPLAY`).
- `SettingsAppSection.kt`: replace the **Progress default range** row with a **Customize progress**
  row (value line: range + plot count summary, e.g. `1W · Plots off`), navigating to the new screen.
  This keeps App & Display (already ~10–24 rows) from growing and groups all progress
  configuration in one place — consistent with the 2026-08-12 settings overhaul's connected-graph IA.
- New `CustomizeProgressScreen.kt` (scaffold + back row pattern from `HomeDisplaySettingsScreen`):
  1. **Default range** card — the existing `ListSheet` `PROGRESS_DEFAULT_RANGE` case moves here
     (enum + dispatcher entry stay in `SettingsSheets.kt`; `SettingsAppSection` stops opening it).
  2. **Body measurement plots** card — one row per site (8), each with a `Switch`; **all off by
     default**. Row subtitle: latest logged value (`Waist · 84 cm`) or `No measurements yet`
     (reuses `measure_*` labels). Helper copy: plots appear on the Progress tab; data comes from
     Personal Info → Body measurements.
  3. No master toggle (YAGNI): per-site switches only; "off by default" = empty site set.
- `SettingsViewModel`: add `progressMeasurementSites: Set<String> = emptySet()` to `SettingsUiState`
  (+ read in the load, + `setProgressMeasurementSites`), same shape as `progressDefaultRangeId`.

### 2.2 Progress tab: per-site plots

- **Placement:** after the weight/body-fat `BodyMetricToggle` card and the two history links,
  before the Calorie section — body metrics stay together. Render inside the existing
  `heavySectionsReady` gate (up to 8 canvases; keeps the first-frame budget like Calorie/Macros).
- **Per enabled site with ≥ 1 entry in the selected range:** one `CardSection` per site:
  - Header: site label (reuse `measure_*`), latest value in range, signed **Δ vs the range's first
    entry** (`UnitFormat.signedDelta` + unit, `cm`/`in` per `heightUnit`) in the **Net change** badge
    (`progress_stat_net_change`) — no header/badge duplication.
  - `StatBadgeRow`: Current · Net change (reuse `progress_stat_current` / `progress_stat_net_change`).
  - Compact trend chart (~140 dp): straight line + dots + `TrendXAxisLabels`, same colors as the
    weight chart, no goal rule, no dashed 7-day trend overlay (measurement cadence is too sparse).
  - Sites with no entries in range: not rendered (no phantom empty cards).
- **Data flow (minimal):** `ProgressViewModel` combine gains `container.prefs.progressMeasurementSites`;
  `toUiState` adds `filteredMeasurements` (range-filtered, sorted — same `instantRange` window as
  weights/BF) and parsed `measurementSites: Set<BodyMeasurement.Site>` into `ProgressUiState`.
  Per-site series + delta stats computed in the composable via `remember` (mirrors
  `WeightChartCanvas` building its model from entries).
- **Chart model:** `buildMeasurementChartModel(points: List<TrendPoint>)` in `ProgressCharts.kt`
  returning the existing unit-agnostic `WeightChartModel` shape (`yMin/yMax/ticks/tRange/
  singleEntry/showsYear/xLabelFmt/points`), plus a `MeasurementChartCanvas` modeled on
  `BodyFatChartCanvas` (line + dots, no goal/trend). `downsampleTrend`/`niceAxisTicks` reused.
- **Units:** `heightUnit` pref (same as `BodyMeasurementsScreen`), not `weightUnit`.

### 2.3 Prefs

- `PreferencesKeys.kt`: `PROGRESS_MEASUREMENT_SITES = stringPreferencesKey("progressMeasurementSites")`.
- `PreferencesStore.kt` + impl (JSON `Set<String>` of site storage ids — copy the
  `dismissedSuggestionIds` pattern; default empty). Storage ids = enum names lowercase
  (`neck`, `waist`, …) so unknown values are ignored on read.

### 2.4 Alternatives considered (and rejected)

| Option | Verdict |
|--------|---------|
| Always-on plot card with a site chip selector (like `BodyMetricToggle`) | One chart, but hides multi-site comparison behind taps; reporter asked for "plots" |
| Master toggle + "enable all with data" | Friction; per-site switches are more explicit and match the "customizable" ask |
| Full-height 180 dp charts | 8 × 180 dp is a wall of scrolling; compact 140 dp keeps the tab usable |
| Put plots in the Body measurements screen only | Reporter wants change-over-time *alongside* weight/BF tracking — Progress tab is the right home |
| PWA parity (charts in `progress-view.js`) | PWA has no measurement charts and no measurement entry flow; Android-only, called out in `PARITY.md` (same as Activity/Wellness sections) |

## 3. File-by-file change list

| # | File | Change |
|---|------|--------|
| 1 | `data/PreferencesKeys.kt` | New key `PROGRESS_MEASUREMENT_SITES` |
| 2 | `data/PreferencesStore.kt` + `data/PreferencesStoreMisc.kt` | `progressMeasurementSites: Flow<Set<String>>` + setter (JSON set impl) |
| 3 | `ui/navigation/ChompassRoutes.kt` | `CUSTOMIZE_PROGRESS` route |
| 4 | `ui/navigation/NoFUDNavHost.kt` | Route registration |
| 5 | `ui/settings/CustomizeProgressScreen.kt` | **New** — range row (reused sheet) + 8 site switches |
| 6 | `ui/settings/SettingsAppSection.kt` | Range row → **Customize progress** row |
| 7 | `ui/settings/SettingsSheets.kt` | `PROGRESS_DEFAULT_RANGE` case unchanged (opened from new screen) |
| 8 | `ui/settings/SettingsViewModel.kt` | `progressMeasurementSites` in state + setter |
| 9 | `ui/progress/ProgressViewModel.kt` | Combine prefs; `filteredMeasurements` + `measurementSites` in `ProgressUiState` |
| 10 | `ui/progress/ProgressCharts.kt` | `buildMeasurementChartModel` + `MeasurementChartCanvas` |
| 11 | `ui/progress/ProgressScreen.kt` | Render per-site plot cards (in `heavySectionsReady` gate); preview content renders them from `ui` |
| 12 | `services/TestDataSeeder.kt` | `seedBodyMetrics()` gains a handful of `BodyMeasurement` entries (waist/hips/chest/neck, weekly, ~3 months) for device verification |
| 13 | `res/values/strings.xml` + 15 locale files | New strings (see §5) |
| 14 | `docs/PARITY.md` | Progress + Settings rows: Android-only notes |
| 15 | `docs/CHANGELOG.md` | Unreleased → Added entry |
| 16 | `docs/local/ISSUE_BACKLOG.md` | #18 row updated when shipped |

## 4. Tests & screenshots

- **Unit (`android/app/src/test/.../ui/progress/MeasurementTrendTest.kt`):**
  - `buildMeasurementChartModel`: ticks/range/single-entry/`showsYear` (reuse `WeightTrendTest` style)
  - Delta stats: signed, correct across range boundaries; first==last ⇒ `0`
  - Range filter: entry exactly at `rangeStart`/`rangeEnd` instants included; outside excluded
  - Prefs round-trip: `set` → flow returns same set; unknown storage ids dropped
- **Screenshot previews:** `ScreenshotFixtures.progressUiState()` gains `bodyMeasurements` (2–3
  entries, 4 sites, spanning months) + `measurementSites`; `ProgressScreenPreviewContent` renders
  them (fixture keeps one preview with plots on so the release screenshot set shows the feature).
  Refresh references via `release:screenshots`, then `validateDebugScreenshotTest` green.
- **Gradle:** `devenv shell bash -lc 'cd android && ./gradlew test'` + `assembleDebug`.
- **Device (Windows adb):** `--ez seed_body_metrics true` → Personal Info → measurements shows
  entries; enable waist/hips plots in Customize Progress; Progress tab shows both cards; range
  chips re-filter; delete an entry → chart updates. Toggle all off → tab identical to today.

## 5. Strings (EN; other locales EN-fallback or translated per LOCALIZATION.md)

| Key | Value |
|-----|-------|
| `settings_customize_progress` | Customize progress |
| `settings_customize_progress_summary` | %1$s · Plots %2$s (e.g. `1W · Plots off` / `Plots: 3`) |
| `settings_progress_plots` | Body measurement plots |
| `settings_progress_plots_subtitle` | Show per-site circumference trends on the Progress tab. Data comes from Personal Info → Body measurements. |
| `settings_progress_plots_no_data` | No measurements yet |
| `progress_plot_delta` | Δ %1$s |
| (reuse) `measure_*`, `progress_stat_current`, `progress_stat_net_change`, `settings_progress_default_range`, `unit_cm`, `unit_in` | — |

## 6. Parity / contracts

- **No contract change** — `contracts/body-metrics-1.0.schema.json` already carries `measurements[]`;
  export/import untouched; no formula change → no `chompass-core` work.
- **No PWA work** — measurement plots are Android-only (like Activity/Wellness). Update the
  `PARITY.md` Progress row ("configurable default range …" gains: Android-only per-site
  measurement plots, off by default, `Customize Progress` sub-screen) and the Settings row's
  Android sub-screen list. `pref-defaults.json`: add `progressMeasurementPlots: []` only if
  `release:check-parity`'s matrix check requires the new semantic default; otherwise leave (it's
  an Android-only, empty-by-default key — note it in the row instead).

## 7. Verification checklist

1. `devenv shell bash -lc 'cd android && ./gradlew test'` — unit tests green
2. `devenv shell bash -lc 'cd android && ./gradlew :app:assembleDebug'` — builds
3. `devenv tasks run release:check-parity` — PWA tests + matrix + schema validation green
4. `release:screenshots` (update + validate) — previews render plots; references refreshed
5. Device pass via Windows adb (seed → enable → verify charts, range re-filter, toggle off restores
   current tab, no jank)
6. CHANGELOG Unreleased entry; PARITY.md updated; ISSUE_BACKLOG #18 → shipped note

## 8. Rollout to #18

- Reply on the issue: feature exists (Personal Info), and the requested graph view is now
  **Customize progress → Body measurement plots** (per-site toggles, off by default) with a
  screenshot; ask for confirmation to close.
- Update `docs/local/ISSUE_BACKLOG.md` #18 row + counts on the next token run.
