# Design: Dynamic water goal + adaptive reminders (issue #3)

Status: **Beta. Phases 1–4 implemented; device pass outstanding.** Opt-in feature, default off. Ships with a medical disclaimer (estimate, not advice) in Settings next to the feature, in the Safety &amp; Medical notices, and in the onboarding safety card.
Related: [Codeberg #3](https://codeberg.org/fitguy/Chompass/issues/3), [`docs/local/ISSUE_BACKLOG.md`](local/ISSUE_BACKLOG.md), [`docs/CALCULATION_METHODS.md`](CALCULATION_METHODS.md)

## Problem statement

Water tracking currently has a **fixed** daily goal (`waterDailyGoalMl`, default
2000 ml) and a **single fixed-time** reminder (one alarm at `waterReminderHour:
Minute`, default 14:00, re-armed +24 h by `ReminderReceiver`). The reporter
asks for:

1. **Dynamic goal**: daily ml adapts to weather/temperature, personal
   activity, and water consumed through food.
2. **Adaptive reminders**: interval = `goal ÷ cup ÷ awake window`, e.g.
   2500 ml ÷ 300 ml = 8.33 cups over 13 h (08:00–21:00) → 780 min ÷ 8.33 ≈
   every 93 min, **recalculated after each entry** so drinking more/less than
   usual changes the cadence.

Maintainer constraints (reply 2026-08-11): privacy-first, **no location
permission**; a manual city/temperature setting or a weather-provider
integration is acceptable; rework needed, not in the next release; PR welcome.

## Goals / non-goals

**Goals**
- Deterministic, pure-local formulas (no network, no permission) that reuse
  `UserProfile` (weight, `ActivityLevel`) and the existing
  `WaterRepository` / `PreferencesStoreWater` / `NotificationService` plumbing.
- Reminder interval recomputed after every water entry (reporter's core ask).
- Zero behavior change while the feature is off (defaults unchanged).

**Non-goals (v1)**
- No location permission, no automatic geolocation.
- No weather-provider network call in v1 (manual temperature only; city →
  Open-Meteo is an optional follow-up, see Phase 5).
- No water-from-food *logging* UI; only a coarse optional subtraction from
  the drink goal using existing diary grams.
- No changes to the PWA (water UI is Android-only; see Parity).

## Formula register (new entries for `CALCULATION_METHODS.md`)

### WATER-DYN-A: gross drink goal

```
baseMl     = round50(weightKg × 35)              // EFSA 2010 adult AI ≈ 2.0–2.5 L/day
                                                 // fallback: stored manual goal if no profile weight
tempFactor = 1 + 0.04 × max(0, Tmax°C − 25)      // clamp [1.0, 1.6]; ≤25 °C no reduction
actFactor  = table by ActivityLevel              // SED 1.0 · LIGHT 1.1 · MOD 1.2 · ACT 1.3 · VACT 1.4 · XACT 1.5
grossMl    = round50(baseMl × tempFactor × actFactor)
```

- Temperature input is the **manual expected high for today (°C)**; no
  location permission. Factor is +4 % per °C above 25 °C (30 °C → +20 %,
  35 °C → +40 %), clamped so a heatwave never more than +60 %.
- **Since Phase 5 (2026-08-13)** the temperature input can also come from an
  Open-Meteo city forecast — see
  [WEATHER_INTEGRATION_DESIGN.md](WEATHER_INTEGRATION_DESIGN.md). The manual
  °C stays the default and the universal fallback. (Weather-app broadcast
  input is parked.)
- Activity factor uses the existing `ActivityLevel` enum (already on the
  profile; the TDEE multipliers 1.2–1.9 are BMR-relative and unsuitable to
  reuse directly, hence the dedicated table).

### WATER-DYN-B: food-water subtraction (optional, coarse)

```
foodWaterMl = min(round50(foodGramsToday × 0.6), 1000)   // ~60 % water by mass, hard cap
netGoalMl   = max(grossMl − foodWaterMl, 1000)            // never below 1 L
```

`foodGramsToday` = sum of grams across today's diary entries. Optional toggle,
off by default (estimate is coarse by design).

### WATER-DYN-C: adaptive reminder interval (reporter's formula)

Planning preview (settings):

```
intervalMin = clamp(round5(awakeWindowMinutes ÷ ceil(netGoalMl ÷ cupSizeMl)), 30, 240)
```

(the planning form is clamped identically to the live form so the Settings
preview equals what the alarm chain computes at day start; both return null
when the window is empty / goal already met)

Live, after each entry (recomputed at schedule time):

```
cupsRemaining   = ceil(max(netGoalMl − drunkTodayMl, 0) ÷ cupSizeMl)
windowRemaining = awakeEnd − max(now, awakeStart), minutes
intervalMin     = clamp(round5(windowRemaining ÷ cupsRemaining), 30, 240)
```

- If you are ahead (drank more than plan), fewer cups remain → interval
  lengthens; if behind → shortens, down to the 30 min floor. This is the
  "recalculated after each entry" behavior. The alarm re-arms itself with
  fresh prefs at every fire, and is re-armed immediately on entry add.
- `cupsRemaining ≤ 0` (goal reached) → no more reminders today; the chain
  re-arms for tomorrow's `awakeStart`.

## Scheduling design (implemented)

Keep the single alarm + `REQUEST_WATER` + `ReminderReceiver` chain; the water
re-arm is "next computed fire" (via `WaterReminderPlanner`), not "+24 h":

1. **On fire** (`ReminderReceiver`): read prefs fresh (goal inputs + today's
   entries), compute WATER-DYN-C, schedule `nextFire = now + intervalMin`;
   goal met or `now ≥ awakeEnd` → `nextFire = tomorrow awakeStart` (full-goal
   cadence again). Post the notification with the next-interval text.
2. **On entry add/delete** (`WaterRepository.onEntriesChanged`, wired in
   `ChompassApp`): re-arm immediately so the next reminder reflects the entry
   right away (reporter's ask), not only at the next fire.
3. **On app/settings open**: `SettingsViewModel.syncNotificationSchedules` and
   the `ChompassApp` cold-start block both call `WaterReminderPlanner.rearm`.
4. **On reboot**: `ReminderReceiver` has a `BOOT_COMPLETED` intent-filter
   (`RECEIVE_BOOT_COMPLETED` permission already declared) and re-arms the
   water chain (other daily reminders keep their existing reboot gap; out of
   scope, but the receiver is the natural home for a follow-up).
5. **Notification text**: the notification carries the **quantity to drink**
   (`min(cup, goal − drunkToday)`, one cup per reminder, tail capped at the
   remainder) plus the next cadence: “Drink 300 ml · next in ~90 min.”
   (`notif_water_text_next_qty`) when the interval is known; “Drink 300 ml
   and log it in Chompass.” (`notif_water_text_qty`) for a day-start fire.
   Amounts follow the user's unit (ml / fl oz, same as Home).

Clamps/edges: interval [30, 240] min; awake window shorter than one interval
degenerates to one reminder at `awakeStart`; DST/timezone shifts are absorbed
because every fire recomputes wall-clock times; force-stop kills alarms
(system behavior, already documented for battery optimization).

## Persistence (new keys in `PreferencesKeys` + `PreferencesStoreWater`)

| Key | Type | Default | Meaning |
|-----|------|---------|---------|
| `WATER_DYNAMIC_ENABLED` | bool | false | master toggle; off = today's behavior |
| `WATER_BASE_SOURCE` | string | `"weight"` | `"weight"` (35 ml/kg) vs `"manual"` (existing wheel) |
| `WATER_MANUAL_TEMP_C` | int | 25 | expected high °C (−10…45) |
| `WATER_USE_PROFILE_ACTIVITY` | bool | true | false = factor 1.0 |
| `WATER_FOOD_WATER_ENABLED` | bool | false | WATER-DYN-B |
| `WATER_AWAKE_START_HOUR/MINUTE` | int | 8 / 0 | start of drinking window |
| `WATER_AWAKE_END_HOUR/MINUTE` | int | 21 / 0 | end of drinking window |
| `WATER_CUP_SIZE_ML` | int | 300 | wheel 50…1000, step 50 |

Dedicated **awake-start keys** (not a repurpose of `WATER_REMINDER_HOUR/MINUTE`):
the legacy pair keeps its 14:00 default as the *fixed-reminder time for the
dynamic-off path*; changing its default would alter existing behavior, and
14:00 is not a sensible window start.

No cached goal key: `netGoalMl` is computed on the fly (single source of
truth) in a pure calculator used by Home, Settings, widget snapshot, and the
reminder chain alike.

## Code layout

- `models/WaterGoalCalculator.kt` (new): pure object, WATER-DYN-A/B/C,
  `round50`/`round5` helpers, clamps. Mirrors `AdaptiveGoalService` /
  `DeterministicFormulas` style. **Unit-tested** (see Testing).
- `data/PreferencesStoreWater.kt`: new getters/setters for the keys above.
- `data/WaterRepository.kt`: expose `currentNetGoal()`/`todayMl()` helpers;
  `add()` re-arms the reminder via a callback wired in `ChompassApp`
  (repo stays notification-agnostic, same pattern as the goal-reached
  crossing).
- `services/NotificationService.kt`: `scheduleAdaptiveWaterReminder(nextFire)`
  alongside the existing fixed-time method; `ReminderReceiver` branches on
  `CHANNEL_WATER` for interval re-arm + boot re-arm, and formats the
  notification with the planned quantity.
- `ui/settings/SettingsAppSection.kt` + `SettingsSheets.kt`: new rows/sheets.
- `ui/home/HomeViewModel.kt`, `services/WidgetSnapshotWriter.kt`: read the
  computed goal instead of the raw pref when dynamic is on.
- `ui/home/HomeViewModel.kt` additionally re-derives `waterNextPlan`
  (`WaterReminderPlanner.Plan` with the fire time + drink amount) whenever any
  planner input changes; `ui/home/WaterViews.kt` shows it under the progress
  bar (“Next 300 ml · 15:24”, “tomorrow 08:00” for next-day fires).
- `services/WidgetSnapshotWriter.kt` runs the same plan re-derivation (via the
  dependency form of `WaterReminderPlanner.next`, no container needed) into
  `WidgetSnapshot.waterNextFireAtMillis` / `waterNextDrinkMl`;
  `widget/WaterAppWidget.kt` renders the line under the remaining label
  (hidden once the fire time is in the past — the widget cannot tick).

## Settings UI

**Water section** (below the existing goal row):
- Toggle **"Dynamic goal"** (default off). When on:
  - Base: **body weight (35 ml/kg)** vs fixed base wheel (existing sheet)
  - **Expected high today (°C)** wheel (−10…45, step 1), privacy-clean,
    no permission; hint that it is the manual stand-in for weather
  - **Use profile activity level** toggle (default on)
  - **Subtract water from food** toggle (default off; coarse 60 % of diary
    grams, capped 1 L)
  - Live preview: "Today's goal: 2 650 ml" + one-line breakdown
    (`2 000 × 1.2 × 1.1 − 0`)

**Reminder section** (replaces the implicit 14:00):
- **Drinking window** start/end time pickers (defaults 08:00–21:00)
- **Cup size** wheel (default 300 ml; note it is independent of quick presets)
- Live preview: "≈ every 90 min · 9 cups" (WATER-DYN-C planning form),
  updating as the goal/window/cup change
- Existing master "Water reminder" toggle stays.

**Home water card**: goal ring shows the dynamic goal; small "auto" badge;
tapping the badge opens Settings (the new water section with the breakdown).
Below the bar, a **next-drink line** shows the upcoming reminder's amount and
fire time (“Next 300 ml · 15:24”, “tomorrow 08:00” for next-day fires; fl oz
for imperial users) — null (hidden) when the reminder is off, the goal is
met, or the window is degenerate. The **water widget** shows the same line
under its remaining label (compact 11 sp, single line; hidden once the fire
time has passed, since a widget cannot tick).

**Beta + medical disclaimer**: the dynamic-goal block is labelled **Beta** with a
warning card. The target is an estimate from general guidelines, can be wrong
for an individual (kidneys, medication, conditions, sweat, humidity), consult a
doctor before changing drinking habits, and always drink enough (thirst is the
best guide) even when no reminder fires. Same message appears in the
onboarding safety card and the Settings Safety &amp; Medical notices; the
reminder-plan sheet carries a short "reminders are gentle suggestions" note.
Feature stays **off by default** (`waterDynamicEnabled` default false).

All new copy goes to `res/values/strings.xml` (locale contract
`testdata/parity/locales.json` gates the *locale list*, not key coverage.
translated packs get the usual pass, no parity break).

## Parity

Water UI is **Android-only** today (PWA `chompass-core` carries water entries
in sync format only). New formulas are pure Kotlin, so a future PWA water UI
can mirror them in `chompass-core` and lock via a fixture; no
`contracts/`/`testdata/parity/` change in v1. Register WATER-DYN-A/B/C in
`docs/CALCULATION_METHODS.md` (formula register + "Last audited" bump) per the
calculation-change checklist.

## Testing

- Unit (`:app:testDebugUnitTest`):
  - WATER-DYN-A: weight base + fallback, temp factor table (25/26/30/35/40 °C),
    clamp 1.6, activity table, rounding to 50.
  - WATER-DYN-B: 60 % of grams, 1 L cap, floor at 1 L.
  - WATER-DYN-C: reporter's worked example (2500 ml, 300 ml, 13 h → ~90 min),
    ahead/behind recalculation, goal-reached → no reminder, 30/240 clamps,
    round5, window shorter than interval.
  - Pref defaults parity if defaults are added to `pref-defaults.json` (the
    new keys are feature defaults; follow `PrefDefaultsParityTest` only where
    the fixture already covers water; otherwise keep unit-local).
- Device pass (Windows adb, `.debug` package): dynamic goal on → ring/widget
  update; add entries → reminder cadence visibly changes; reboot → chain
  re-arms; goal reached → silence until next day.

## Rollout phases

| Phase | Scope | Gate |
|-------|-------|------|
| 1 | `WaterGoalCalculator` + prefs keys + unit tests | ✅ done; 24 unit tests green |
| 2 | Settings UI + strings + Home/widget wiring | ✅ done; debug build + full `testDebugUnitTest` green; needs device pass |
| 3 | Adaptive alarm chain + boot re-arm | ✅ done; `WaterReminderPlanner` (fire-time recompute), re-arm on entry add/delete via `WaterRepository.onEntriesChanged`, `BOOT_COMPLETED` re-arm, "next in X min" text; needs device pass (cadence, reboot) |
| 4 | Docs: `CALCULATION_METHODS.md` register, `CHANGELOG.md`, this note → complete | ✅ done; WATER-DYN-A/B/C registered w/ science audit (EFSA 2010, IOM 2004, ACSM 2007, food-moisture 19–30 %), `CHANGELOG.md` Unreleased entry; `release:check-parity` to confirm |
| 5 (optional) | Open-Meteo: manual city name → geocode → today's high; manual temp stays the fallback; no permission | ✅ done 2026-08-13 — `WeatherRepository` three→two-source resolver (`manual` default + universal fallback), Open-Meteo client + city search UI, unit + MockWebServer tests green, **device pass done** (Pixel 9a). Weather-app broadcast input (Breezy Weather) was prototyped and **parked** — see [WEATHER_INTEGRATION_DESIGN.md](WEATHER_INTEGRATION_DESIGN.md) |

Feature ships opt-in (toggle default off), so existing users and the widget
see no change until they enable it.
