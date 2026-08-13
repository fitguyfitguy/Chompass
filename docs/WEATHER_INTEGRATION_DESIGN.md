# Design: Weather input for the dynamic water goal

Status: **Implemented (2026-08-13), unreleased — Open-Meteo only.** Issue #3
Phase 5 — real temperature input for WATER-DYN-A from a free API, while
keeping Chompass's privacy stance (no location permission, no account, no
key). The manual °C wheel stays the default and the universal fallback.
Weather-app broadcast input (Breezy Weather etc.) is documented but parked —
see Sources below.

Related: [Codeberg #3](https://codeberg.org/fitguy/Chompass/issues/3),
[`docs/WATER_DYNAMIC_GOAL_DESIGN.md`](WATER_DYNAMIC_GOAL_DESIGN.md) (Phases 1–4),
[`docs/CALCULATION_METHODS.md`](CALCULATION_METHODS.md) (WATER-DYN-A/B/C).

## Problem statement

Phase 1–4 of the dynamic water goal used a **manual "expected high today" (°C)**
as the temperature input — privacy-clean but static: a user who doesn't update
the wheel gets the same goal in a heatwave as in a cold snap. This phase adds
real temperature input from two families of sources:

1. **Free weather APIs** — Open-Meteo (no key, no account, CC BY 4.0 data) as a
   direct fetch for a manually chosen city. (Weather apps as inputs were
evaluated — Breezy Weather/QuickWeather share a standard broadcast — but are
parked for now; see Sources below.)

## Sources and contracts (verified)

### Shipped: Open-Meteo (the "free API" path)

- Geocoding: `https://geocoding-api.open-meteo.com/v1/search?name=…&count=8`
  → `id, name, latitude, longitude, country, admin1, timezone`.
- Forecast: `https://api.open-meteo.com/v1/forecast?latitude=…&longitude=…&daily=temperature_2m_max&timezone=…&forecast_days=1`
  → `daily.temperature_2m_max[0]` (°C, already Celsius).
- No API key, no account, no tracking; free for non-commercial use; data
  CC BY 4.0 (attribution shown in Settings). Only the manually typed city name
  + its coordinates are ever sent — no location permission.
- **Device-verified 2026-08-13** (Pixel 9a, debug build): city search, forecast
  fetch, goal preview + Home ring update, fallback behavior.

### Parked (not shipped): weather-app broadcast

The de-facto FOSS standard is the **Gadgetbridge weather broadcast** — action
`nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER` with
`WeatherJson`/`WeatherSecondaryJson`/gzip `WeatherGz` extras of `WeatherSpec`
JSON (temperatures in **Kelvin**). **Breezy Weather** (F-Droid) ships opt-in
"Data sharing" that discovers receiver packages via
`queryBroadcastReceivers` and sends **explicit** broadcasts
(`setPackage` + `FLAG_INCLUDE_STOPPED_PACKAGES`), so a Chompass manifest
receiver would receive them even while Chompass is not running; **QuickWeather**
conforms too. **Decision (2026-08-13): not shipped for now** — Open-Meteo
covers the need with zero extra apps, and the broadcast path adds an
unverifiable-here surface (sender display is impossible — Android does not
expose the sender UID — plus spoofability and per-app setup). The contract is
kept documented here for a future revisit; a re-implementation is a
manifest receiver + `WeatherSpec` parser + a `SOURCE_APP` branch in
`WeatherRepository` (cache trusted only while it arrived today).

**Breezy's newer ContentProvider** (v6.1.0+, experimental) is richer but needs
a per-app permission grant via their data-sharing library and is pre-1.0 —
also parked.

## Architecture

```
WeatherShareReceiver (parked — not shipped; see Sources above)
        │  WeatherSpec JSON / gz → WeatherSpecParser
        ▼
WeatherRepository ── state: Flow<WeatherState> ──► effectiveHighC: Int
        ▲                                            (source + freshness rules)
        │  selectOmCity / refreshOpenMeteo
OpenMeteoClient (OkHttp, no key)  ─────┘

effectiveHighC consumers (all four water call sites, unchanged interfaces):
  WaterReminderPlanner.next()      → reminder chain + notification text
  HomeViewModel                    → Home ring + next-drink line
  WidgetSnapshotWriter             → widget snapshot + plan line
  SettingsViewModel                → Settings preview breakdown
```

Sources (`weatherSource` pref, default `manual`):

| Source | Value | Freshness rule | Fallback |
|--------|-------|----------------|----------|
| Manual | `manual` | always | — |
| Open-Meteo | `openmeteo` | cache `omDate == today` | manual °C |

`effectiveHighC` is a pure derivation on `WeatherState` (unit-tested); the
reminder chain / widget / Home all consume the same value, so the goal never
breaks when the source is missing or stale — it silently uses the manual °C.

## Persistence (new keys in `PreferencesKeys` + `PreferencesStoreWeather`)

| Key | Type | Default | Meaning |
|-----|------|---------|---------|
| `WEATHER_SOURCE` | string | `"manual"` | `manual` \| `openmeteo` |
| `WEATHER_OM_CITY` | string? | null | selected `OmCity` JSON |
| `WEATHER_OM_HIGH_C` | int | 0 | today's high °C from the last fetch |
| `WEATHER_OM_DATE` | string? | null | local date (`yyyy-MM-dd`) the high applies to |
| `WEATHER_OM_UPDATED_AT` | long | 0 | epoch-millis of the last successful fetch |

`waterManualTempC` keeps its meaning (manual source + universal fallback).

## Refresh triggers

- **City picked / source switched to Open-Meteo** → fetch immediately.
- **Cold start** (`ChompassApp`) → refresh Open-Meteo when it is the source
  (no-op when today's cache already exists).
- **Settings "Today's high" row** (Open-Meteo sheet) → manual refresh.
- The midnight widget-rollover alarm re-derives the snapshot, so a stale-high
  day rolls to the new day's fallback automatically.

## Settings UI (Water → Dynamic goal)

- **Weather source** row (always visible while the dynamic goal is on):
  Manual / Open-Meteo (list sheet with subtitles + help footer).
- **Manual** → the existing °C wheel row.
- **Open-Meteo** → City row (opens search sheet: search field → result list →
  tap to select + fetch) and a status footnote ("28 °C · updated 08:12" /
  fallback) plus the Open-Meteo CC BY 4.0 attribution.

All new copy in `res/values/strings.xml` (locale contract: the locale *list*
is gated by `testdata/parity/locales.json`, not key coverage).

## Security / privacy notes

- **No location permission** anywhere: the API path uses a manually searched
  city.
- **Open-Meteo gets only the city name + coordinates** of the user's chosen
  city; no account, no key, no device identifiers.
- (Parked app-source note: a broadcast receiver would need to be exported for
  sharing apps to discover it — any app could then spoof a payload. Blast
  radius is a cache pref feeding only the water-goal math of an opted-in
  user, but it is one more reason the path stays parked.)

## Parity

Android-only (PWA has no water UI), and WATER-DYN-A's formula register is
unchanged — the temperature *input source* is a pure Android concern. No
`contracts/` or `testdata/parity/` changes. `CALCULATION_METHODS.md`
WATER-DYN-A gains a note about the input sources.

## Testing

- `WeatherRepositoryTest` (5 tests): `effectiveHighC` resolution for manual /
  Open-Meteo fresh / stale / missing (fallback), `omFreshToday`.
- `OpenMeteoClientTest` (MockWebServer): geocoding parse + display name,
  blank-query short-circuit, server errors → empty/null, forecast parse.
- Full `:app:testDebugUnitTest` + `assembleDebug` green.
- **Device pass done 2026-08-13** (Pixel 9a, debug build): Open-Meteo city
  search + forecast + goal preview/Home ring update verified manually.

## Rollout phases

| Phase | Scope | Gate |
|-------|-------|------|
| 1 | `OmCity` model + prefs keys + `WeatherRepository` (state + resolution) + unit tests | ✅ tests green |
| 3 | `OpenMeteoClient` (geocode + forecast) + MockWebServer tests | ✅ tests green |
| 4 | Settings UI (source sheet, city search sheet) + Home/widget/planner wiring + strings | ✅ `assembleDebug` green; device pass done |
| 5 | Docs (this file, WATER_DYNAMIC_GOAL_DESIGN Phase 5, CALCULATION_METHODS note, CHANGELOG) | ✅ release:check-parity |

Follow-ups (parked): weather-app broadcast input (`ACTION_GENERIC_WEATHER`,
Breezy Weather/QuickWeather — contract above); sender package display (needs
an API that exposes the broadcast sender); Breezy ContentProvider integration
once tagged; multi-location picker; Open-Meteo-compatible custom endpoint for
self-hosters.
