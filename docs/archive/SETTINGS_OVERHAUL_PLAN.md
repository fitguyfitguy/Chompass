# Settings Overhaul Plan — UX, Simplicity, Understandability

Status: **Executed 2026-08-12** (Phases 1–3 + 5-lite + 6) · Scope: Android (`ui/settings/`) + PWA mirror + parity docs
Goal: restructure the settings information architecture and unify UI primitives so the
~85 interactive rows are scannable, predictable, and each screen fits ~1.5 phone screens
when fully expanded. Settings become a **connected graph** (cross-linked domains) with
**proactive nudges** toward optimal configuration. **No behavior, storage, or formula
changes to existing keys** — pure IA + copy + presentation (+ 2 new prefs keys for nudges).

**Execution log:** Phases 1 (IA restructure), 2 (primitives), 3 (Suggestions) landed;
Phase 5 partially — hub labels mirrored in the PWA (`en.js` + `settings-view.js`), full
PWA Food & Entry / Water / Notifications split deferred (PWA keeps 5-group hub; divergence
documented in `docs/PARITY.md`). Phase 4 (hub search) deferred (D3). QA: `./gradlew test`
and `scripts/check_parity.sh` green; debug APK assembles.

---

## 1. Current state (inventory)

All screens are Compose, wired in `NoFUDNavHost.kt` under 5 hub groups + About.

| Hub group | Screen file(s) | Rows at full expansion | Notes |
|---|---|---|---|
| Personal Info | `PersonalSettingsScreen.kt` → `SettingsPersonalSection.kt` | 7 | Gender, birthday, height, weight, body fat, goal body fat (conditional), measurements → |
| Goals & Nutrition | `GoalsSettingsScreen.kt` → `SettingsGoalsSection.kt` | up to 16 | Goal, diet mode (+2 keto rows), activity, weekly change, goal weight, adaptive, energy burn, 4 macro rows, optional nutrients →, recalculate, calc methods → |
| App & Display | `AppSettingsScreen.kt` → `SettingsAppSection.kt` | up to **24** | Home display →, appearance, theme, grams, **water block (inline, ~9–15 rows)**, sort, week, range, meal times, **notifications block (inline, ~10 rows)** |
| AI & Speech | `AiSettingsScreen.kt` → `SettingsAiSection` + `CustomInstructions` + `Fallback` + `Speech` | ~22 | Provider, model, key, URL, on-device, tokens, timeout, Google search, portion clarify, photo note, constituents, serving units, instructions, fallback, speech |
| Health, Data & Sync | `DataSettingsScreen.kt` → `SettingsHealthDataSection` + `SettingsSyncSection` | ~19 | Health Connect, manage access, bg sync, safety expander, export ×2, import ×2, clear log, delete all, **WebDAV form (3 fields + 4 actions)** |
| About (card on hub) | `AboutScreen.kt` | 9 | Update check, share, links, privacy |

Second-level sub-screens that already exist: Home Display (`HomeDisplaySettingsScreen.kt`, 6 rows), Optional Nutrient Goals, Calculation Methods, Body Measurements.

Supporting infrastructure: `SettingsViewModel.kt` (1,274 lines), `SettingsSheets.kt` (786-line sheet dispatcher), `SettingsPickerSheets.kt`, `SettingsPrimitives.kt` (561 lines), `SettingsScreenPreview.kt` (release screenshots). **284 `settings_` strings × 15 locales.**

---

## 2. Problems found (evidence-based)

### P1 — Inline conditional nesting makes screens unpredictably long
`SettingsAppSection.kt` renders a base of ~10 rows; enabling *Water tracking* injects ~9 rows
(goal, dynamic, presets), enabling *Dynamic goal* injects 6 more + a ~300-word warning box
(`settings_water_dynamic_warning_body`). Notifications do the same (master toggle + 7 type
rows + water plan + battery row). The same screen is 10 or 24 rows depending on state, rows
appear/disappear mid-card causing layout jumps, and the water sub-domain is effectively a
hidden screen living inside one card.

### P2 — Group labels don't match screen titles
- Hub "App & Display" (`settings_group_app_display`) → screen title "App Settings" (`settings_section_app`)
- Hub "AI & Speech" (`settings_group_ai`) → screen title "AI Provider" (`settings_section_ai`) — yet the screen also holds fallback, custom instructions, and speech
- Hub "Health, Data & Sync" → screen sections "Health & Data" + "Sync"

Users navigating by label see different names after tapping.

### P3 — The AI screen mixes three unrelated concerns
Provider wiring (provider/model/key/URL/tokens/timeout), **entry-flow behavior** (portion
clarify, photo note prompt, meal constituents, serving-unit inference + heuristics), and
**other services** (fallback provider, custom AI instructions, speech-to-text). A user
tapping "AI Provider" to fix a key is confronted with 4 cards of unrelated toggles.

### P4 — Destructive actions sit among routine actions
In `SettingsHealthDataSection.kt`, *Clear Food Log* and *Delete All Data* are plain rows
between imports and sync. Nothing separates "moves data" from "destroys data" except icon
tint — no section break, no warning styling beyond the row text color.

### P5 — WebDAV sync is a full form embedded in the Data screen
3 text fields + save + auto-sync + export/import/sync-now = ~8 items in one card. Sync is a
first-class domain (it already has its own state machine and blurb) and deserves a
sub-screen — the app already has the pattern (Home Display).

### P6 — No search
~85 interactive rows, 284 strings, no search. Power users and returning users cannot find a
setting by name; the hub only lists 5 groups with 1-line summaries.

### P7 — No screen-level context
Sub-screens open straight into row lists (`SettingsSubScreen.kt` has no intro slot). Only
Sync has a blurb. Concepts that need explanation — Adaptive Goals vs. manual macro locks,
Energy Burn anchor, dynamic water — rely on per-row ⓘ taps and post-hoc dialogs
(`GoalsSettingsScreen.kt` has 5 stacked info-dialog states).

### P8 — Confusing lock affordance
`LockableGoalRow` (SettingsPrimitives.kt): the lock is documented as a **read-only
indicator** but renders as an interactive-looking glyph with three states (dimmed gray,
pink filled, gray outline). Users tap the lock expecting to unlock. Adaptive-on rows open an
explainer dialog only after the row tap — no upfront hint that the rows are owned by
Adaptive Goals.

### P9 — Primitive sprawl
- `ToggleRow`, `ToggleRowWithInfo`, `EnergyBurnGoalsRow`, `AdaptiveGoalsRow` are 4 variants of the same row (two with busy-spinner states)
- `ActivityLevelSettingRow` duplicates `SettingRow` with `UnfoldMore`
- Footer text is hand-rolled everywhere at 12.sp / 13.sp / `bodySmall` with alpha 0.45 / 0.55 / 0.65 (SettingsAiSection, SettingsFallbackSection, SettingsSpeechSection, HomeDisplaySettingsScreen, SettingsHealthDataSection)
- Divider alpha differs (0.08 in HomeDisplay, default elsewhere)

### P10 — Inconsistent promotion rule
Home Display (6 rows) gets a sub-screen; Water (up to 15 rows) and Notifications (10 rows)
are inline. There is no consistent rule for *when a domain becomes a screen*.

### P11 — Settings are silos, not a graph
- The water-reminder toggle is **hidden** unless water tracking is on
  (`SettingsNotificationRows.kt:66`) — a user who enabled tracking never discovers reminders
  exist, and one who sees reminders can't find where the water goal lives.
- Goals & Nutrition has no pointer to water targets even though water is a daily target
  users think about alongside calories.
- Nothing nudges users toward beneficial-but-optional setups (Adaptive Goals, Health
  Connect, backups, reminders). The only precedent is the "Tap to update" recalc nudge
  (`SettingsGoalsSection.kt:199`) — in-screen, tiny, and goal-specific.

---

## 3. Design principles for the overhaul

1. **One screen per domain.** Anything that expands past ~8 rows gets its own sub-screen.
2. **Uniform depth.** Hub → group screen → (sub-screen | bottom-sheet). No inline mega-blocks.
3. **Group by user intent, not by technology.** "How does entry behave" (Food & Entry) is
   separate from "which services/keys are wired" (AI & Speech). Destructive actions get
   their own visual zone.
4. **Consistent affordances.** Chevron = opens sheet/navigates · Switch = boolean ·
   ⓘ = explanation · "Auto" chip instead of lock glyph.
5. **Copy that matches navigation.** Hub labels == screen titles; every sub-screen gets a
   one-line intro.
6. **Settings are a connected graph.** One edit surface per setting (single source of
   truth), but related screens cross-link to it: read-only value rows that navigate, and
   dependency rows shown *disabled with a link* instead of hidden.
7. **Nudge, don't nag.** A calm Suggestions card on the hub proposes optimal setups; users
   always make the final decision by tapping the real toggle. No permission auto-granting.
8. **Zero behavior change to existing keys.** No prefs keys, formulas, export formats, or DI
   touched (only 2 new keys for the nudge engine, §6.3).
9. **Keep the identity.** Glass cards and bottom-sheet pickers stay. No third-party settings
   library, no rewrite of the sheet system.

---

## 4. Proposed information architecture

```
Settings (hub — Suggestions card + 6 group rows + About card)
│
│  ┌─ Suggestions ─────────────────────────────────────────────┐
│  │ 1–3 rows, derived from state, dismissible (see §6.3)      │
│  └───────────────────────────────────────────────────────────┘
│
├─ 1 Profile (Person)                · unchanged content, + intro line
│
├─ 2 Goals & Nutrition (Equalizer)   · + explainer card at top
│   │                                   (Adaptive Goals ↔ manual locks ↔ Energy Burn)
│   │  · Water goal → (cross-link, shows "2.0 L")
│   ├─ Optional Nutrient Goals →     (existing)
│   ├─ Calculation Methods →         (existing)
│   └─ Body Measurements →           (existing, also from Profile)
│
├─ 3 Food & Entry (Restaurant)  NEW  · what logging *does*
│   · Default to grams
│   · Food log sort order
│   · Meal times
│   · Serving size detection (mode + rules →)
│   · Ask for a photo note
│   · Portion clarify
│   · Meal constituents
│
├─ 4 App & Display (Settings)        · appearance, theme color, progress range,
│   │                                   week start, home display →
│   ├─ Home Display →                (existing)
│   ├─ Water →                  NEW · tracking, goal, dynamic (+base/temp/activity/
│   │   │                               food/preview/warning), presets,
│   │   │                               Reminders → (cross-link to Notifications)
│   │   └─ Related: Goals & Nutrition · Notifications
│   └─ Notifications →          NEW · master, 7 types (water row always visible —
│                                       disabled + "Needs water tracking" link when
│                                       tracking is off), water plan, battery opt
│
├─ 5 AI & Speech (SmartToy)          · provider wiring only + existing sub-sections
│   · Provider, model, API key, base URL, on-device model, max tokens, timeout,
│     Google search
│   · Fallback provider (section) · Custom AI instructions (section) · Speech (section)
│   · footnote: serving-size settings live in Food & Entry
│
├─ 6 Health & Data (FolderOpen)      · Health Connect, manage access, bg sync,
│   │                                   safety info, export diary/metrics, import ×2,
│   │                                   Sync → (cross-link with last-sync summary)
│   ├─ Sync (WebDAV) →         NEW  · full form + export/import/sync now
│   └─ ── Danger zone ──            · Clear food log · Delete all data
│
└─ About (card, unchanged)           · update badge on Settings tab keeps working
```

**Decision points for the maintainer:**
- **D1:** Water as sub-screen of App & Display (recommended, keeps hub at 6 rows) vs. a
  top-level hub row (Water is big; some fitness apps promote it). If promoted, hub → 7 rows.
- **D2:** About stays an inline card (recommended — preserves the update dot) vs. 7th hub row.
- **D3:** Search (Phase 4) in or out of scope.
- **D4:** Nudge gating thresholds (3/7/14 days) — tune to real usage (see §6.3).

---

## 5. Screen-by-screen changes

### 5.1 Hub (`SettingsScreen.kt`, `SettingsScreenPreview.kt`)
- Suggestions card (§6.3) at top, then 6 group rows with new labels + summaries; About card
  unchanged below.
- New/renamed strings (see §8); update the preview composable so release screenshots match.

### 5.2 New: Food & Entry screen (`FoodEntrySettingsScreen.kt` + `SettingsFoodEntrySection.kt`)
- **Move in** from `SettingsAppSection`: default-grams toggle, food log sort, meal times.
- **Move in** from `SettingsAiSection`: portion clarify, photo note prompt, meal
  constituents, serving size detection (+ heuristics sheet).
- Sections: "Logging" (grams, sort, meal times) · "Photo analysis" (photo note, portion
  clarify, constituents) · "Serving size" (mode + heuristics).
- Result: AI screen loses 8 rows; Food & Entry gains a coherent home for "how entry behaves".

### 5.3 New: Water screen (`WaterSettingsScreen.kt` + `SettingsWaterSection.kt`)
- Move the entire water block out of `SettingsAppSection` (~15 rows): tracking toggle,
  goal, dynamic goal (+ base, manual temp, profile activity, food water, live preview,
  warning box), quick presets.
- Keep the warning box but move it to the bottom of the *Dynamic* section, and add the
  existing one-line reminder note as the screen intro.
- Add **Reminders →** row (value: "Off" or "8:00–21:00") navigating to the Notifications
  screen (§6.2) — this is the cross-link the current design is missing.
- `SettingsWaterReminderSheet.kt` unchanged; reachable from Water screen **and**
  Notifications screen (same sheet, two routes).

### 5.4 New: Notifications screen (`NotificationsSettingsScreen.kt`)
- Move master toggle + `NotificationTypeRows` + battery-optimization row out of
  `SettingsAppSection`. Add the "none selected" hint (already exists) as the screen footer.
- Master toggle gets the existing permission-launcher logic (moves with it from
  `AppSettingsScreen.kt`).
- **Water reminder row becomes always visible**: when water tracking is off, render the
  toggle disabled with a "Needs water tracking" subtitle whose tap navigates to the Water
  screen (§6.2, Rule B). Fixes P11.

### 5.5 New: Sync screen (`SyncSettingsScreen.kt`)
- Move `SettingsSyncSection` out of `DataSettingsScreen.kt` verbatim (form + save + auto +
  export/import/sync-now + blurb). `DataSettingsScreen.kt` shrinks by ~8 items and loses
  the sync-message dialog state.

### 5.6 App & Display (`SettingsAppSection.kt`)
- Shrinks to: Home display →, appearance, theme color, progress default range, week start,
  Water →, Notifications →. Now ≤ 7 rows at every state — no conditional blocks left.

### 5.7 AI & Speech (`AiSettingsScreen.kt`, `SettingsAiSection.kt`)
- Remove entry-behavior rows (moved to Food & Entry). Keep provider wiring + the three
  existing sub-sections (fallback, custom instructions, speech).
- Rename screen title to match hub ("AI & Speech").
- Add a footnote linking to Food & Entry for serving-size settings (§6.2, Rule C).

### 5.8 Health & Data (`SettingsHealthDataSection.kt`, `DataSettingsScreen.kt`)
- Remove Sync (moved). Keep Health Connect block, safety expander, export/import.
- Add **Sync →** row with last-sync summary (cross-link, §6.2).
- **Danger zone:** move Clear Food Log + Delete All Data into their own `SectionCard` at the
  bottom with warning/error-tinted icon bubbles and a small caption
  ("These actions cannot be undone."). Optional: confirmation dialogs get the destructive
  styling they already use.

### 5.9 Goals (`SettingsGoalsSection.kt`, `GoalsSettingsScreen.kt`)
- Add a top explainer card (reuse the existing dialog copy for Adaptive Goals / locks /
  Energy Burn) so users understand ownership *before* tapping a macro row.
- Add **Water goal →** cross-link row showing the current goal (e.g., "2.0 L") at the end of
  the target rows (§6.2).
- Replace the lock glyph per §7.4.

---

## 6. Cross-links & nudges (new)

### 6.1 Why
Sub-screens fix length but risk deepening silos. The graph rules below keep every setting
discoverable from the domains it relates to, and the Suggestions engine surfaces
beneficial setups users would otherwise never find.

### 6.2 Cross-link rules

**Rule A — one edit surface, many entrances.** Every setting has exactly one place where it
is edited (its owning screen/sheet). Related screens show *read-only value rows* that
navigate there (chevron →). No duplicated toggles, so state can never drift.

**Rule B — never hide a dependency; disable it with a link.** When a setting depends on
another domain, show the row always, disabled, with a "Needs X" subtitle whose tap
navigates to the owner screen. This replaces the current hide-until-enabled pattern
(P11). Precedent already exists: `EnergyBurnGoalsRow` shows "Needs Health Connect"
(`SettingsPrimitives.kt:418`).

**Rule C — Related links footer.** Every sub-screen ends with a small "Related" section
(1–3 rows) linking to adjacent domains, so users can follow the graph without going back
to the hub.

Cross-link inventory:

| From | Row (read-only value) | Always visible | Navigates to |
|---|---|---|---|
| Goals & Nutrition | Water goal (e.g., "2.0 L") | yes | Water screen |
| Water screen | Reminders ("Off" / "8:00–21:00") | yes | Notifications screen |
| Notifications | Water reminder toggle — disabled + "Needs water tracking" when tracking off | yes (Rule B) | Water screen |
| Goals & Nutrition | Energy Burn — "Needs Health Connect" subtitle when HC off (exists) | yes | Health & Data (manage access) |
| Health & Data | Sync (last-sync summary) | yes | Sync screen |
| AI & Speech | footnote: "Serving size settings live in Food & Entry" | yes | Food & Entry |
| Water screen | Related: Goals & Nutrition · Notifications | yes (Rule C) | respective screens |
| Notifications | Related: Water · App & Display | yes (Rule C) | respective screens |

**Back-navigation:** cross-navigated sub-screens show the *source* screen's name as the
back label (`SettingsSubScreen` already takes `backLabel`; pass it via a nav argument, e.g.
`settings/water?from=goals`) so users can retrace: Goals → Water → back to Goals.

### 6.3 Suggestions engine (nudges)

**Pattern:** a "Suggestions" card at the top of the Settings hub — at most 3 rows, each:
short title + one-tap action button ([Turn on] · [Connect] · [Set up]) + X to dismiss.
The card auto-hides rows whose condition resolves; dismissals persist. Nothing is enabled
without the user tapping the real toggle on the target screen — suggestions only navigate
(privacy-friendly, matches the app's ethos).

**State:** all conditions derive from existing `SettingsUiState` fields. Two new prefs keys
(following the `HAS_SEEN_CAMERA_SCALE_TIP` precedent in `PreferencesKeys.kt`):
- `FIRST_LAUNCH_AT` (`longPreferencesKey`, seeded on first `onCreate` in `MainActivity`)
  — gates nudges by install age so new users aren't nagged;
- one boolean per suggestion id (e.g., `SUGGESTION_WATER_TRACKING_DISMISSED`) — simpler
  than a string-set, matches the existing tip pattern.

**Candidates v1** (priority order; top 3 visible):

| id | Condition | Suggestion | Action |
|---|---|---|---|
| water_tracking | tracking off, install ≥ 3 d | "Track water to hit your hydration goal" | → Water |
| water_reminders | tracking on, reminders off | "Set a drinking-window reminder plan" | → Water (opens reminder plan sheet) |
| adaptive_goals | adaptive off, install ≥ 7 d, profile set | "Adaptive Goals keeps your calorie & macro targets current each week" | → Goals |
| health_connect | HC off, install ≥ 7 d, weight history exists | "Sync weight & body fat automatically via Health Connect" | → Health & Data |
| notifications | notifications off, install ≥ 7 d | "Turn on reminders for streaks & goals" | → Notifications |
| backup | no WebDAV URL, install ≥ 14 d | "Back up your diary with WebDAV sync" | → Sync |

**Rules:** never show during onboarding or before 48 h of use; max 3 rows; one line each;
re-evaluate on every state change (derived in `SettingsViewModel`, not stored); dismissal
is explicit (X) and permanent per suggestion (until condition resolves — then the row
simply never returns).

**In-screen micro-nudges** (non-dismissible, part of Rule B): the disabled-with-link rows
in §6.2 and the Goals explainer card (§5.9).

**Out of scope v1:** Home-screen banners, push-based setup prompts, PWA suggestions (PWA
mirrors only the cross-link rows; `PARITY.md` marks Suggestions Android-only).

### 6.4 Anti-patterns to avoid
- No "Enable all" buttons (users should understand each toggle).
- No suggestion that requires a permission the user already denied — re-check permission
  state, not just the pref.
- No red-dot badges on the Settings tab for suggestions (the tab already has the update
  badge; two badges would compete).

---

## 7. Primitive + consistency work (`SettingsPrimitives.kt` and friends)

| # | Change | Detail |
|---|---|---|
| 7.1 | `SettingFootnote(text)` | One 13.sp / onSurface 55% / 16dp-padding footer composable; sweep all hand-rolled footers (Ai, Fallback, Speech, HomeDisplay, HealthData, Water). |
| 7.2 | `BusyToggleRow(label, icon, checked, busy, onInfo, onChange)` | Collapse `ToggleRowWithInfo`, `EnergyBurnGoalsRow`, `AdaptiveGoalsRow` into one row with optional spinner + info. |
| 7.3 | Remove `ActivityLevelSettingRow` | Use `SettingRow` with `inlineMenu = true`. |
| 7.4 | Lock → "Auto" chip | Replace the read-only lock glyph in `LockableGoalRow` with a small "Auto" text chip (Material `AssistChip`-style, non-interactive) when the value is auto-balanced; drop the lock icon entirely. The picker's "Reset to Auto-balance" stays. Goals explainer card (§5.9) covers the Adaptive case. |
| 7.5 | Divider + spacing constants | Single divider alpha (0.10) and row padding (14dp vertical) everywhere. |
| 7.6 | Sheet dispatcher split (optional, low priority) | Split `SettingsSheets.kt`'s 786-line `when` into per-domain files (water, goals, ai, speech) as the screens move — makes Phase 1 diffs reviewable. |
| 7.7 | `DisabledLinkRow` primitive | Row with disabled control + "Needs X" subtitle + whole-row tap → owner screen (Rule B). Used by water-reminder row (§5.4) and Energy Burn's existing pattern. |

---

## 8. Terminology pass (strings)

Reuse existing strings where possible; new strings go to **all 15 locales**
(per `docs/LOCALIZATION.md` contract).

| Current | Proposed | Where |
|---|---|---|
| Hub "App & Display" → screen "App Settings" | "App & Display" both | strings 744–752 |
| Screen "AI Provider" | "AI & Speech" | `settings_section_ai` |
| "Serving unit inference mode" | "Serving size detection" | `settings_serving_unit_mode` |
| "Heuristic serving units" | "Serving size rules" | `settings_serving_unit_heuristics` |
| "Max response tokens" | "Max response length" | `settings_max_tokens` |
| "Default to grams" | unchanged | — |
| WebDAV / API key / base URL / model names | unchanged (technical, correct) | — |
| New | "Food & Entry" group + 1-line summary | new |
| New | Sub-screen intros (Profile, Goals, Water, Notifications, Sync) | new |
| New | Cross-link rows + "Needs water tracking" + Related footers | new |
| New | Suggestions card + 6 suggestion strings + actions ("Turn on", "Connect", "Set up") | new |

Estimate: ~30 new + ~6 renamed strings, ~15 locale files.

---

## 9. PWA + parity impact

- `web/app/src/components/settings-view.js` (1,383 lines) mirrors the same 5-group hub —
  mirror the new hub + sub-screens **and the cross-link rows** in the same release, or
  update the matrix to call the divergence out. Suggestions stay Android-only v1.
- `docs/PARITY.md` settings row (§17) must be updated (new group names + sub-screens +
  cross-links; Suggestions marked Android-only).
- `testdata/parity/`: **no fixture changes** — no formulas, export formats, or storage keys
  change. `locales.json` untouched (string keys are Android-side only).
- `release:screenshots`: `SettingsScreenPreview.kt` + new previews for Water/Notifications/
  Food & Entry/Suggestions must be regenerated (`devenv tasks run release:screenshots`).

---

## 10. Phased implementation

| Phase | Work | Files | Est. |
|---|---|---|---|
| **1 — IA restructure** | New routes + screens; move sections; hub + preview update; cross-link rows + `backLabel` nav args; strings + 15 locales | `NoFUDRoutes.kt`, `NoFUDNavHost.kt`, 4 new screen files, `SettingsAppSection.kt`, `SettingsAiSection.kt`, `DataSettingsScreen.kt`, `SettingsScreen.kt`, `strings.xml` ×15 | 3–4 days |
| **2 — Primitives + copy** | `SettingFootnote`, `BusyToggleRow`, `DisabledLinkRow`, Auto chip, Goals explainer, footer sweep | `SettingsPrimitives.kt`, `SettingsGoalsSection.kt`, section files | 1 day |
| **3 — Suggestions** | New prefs keys (`FIRST_LAUNCH_AT` + 6 dismissal booleans), VM derivation + priority, hub card, strings ×15 | `PreferencesKeys.kt`, `PreferencesStore.kt`, `MainActivity.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` | 1–2 days |
| **4 — Search (D3)** | Hub search box filtering a static index (label → route/sheet) + "no results" state | `SettingsScreen.kt`, `strings.xml` ×15 | 1 day |
| **5 — Parity** | PWA hub + cross-link mirror, `PARITY.md`, screenshot previews + regeneration | `settings-view.js`, `PARITY.md`, preview files | 1–2 days |
| **6 — QA** | `./gradlew test`, `release:package` (parity gate), `install_debug.sh` manual pass with seed extras | — | 1 day |

Ordering note: Phase 1 is a pure move (no logic changes) so it can land independently;
Phases 2–3 are presentation-only and safe to batch; Phase 5 must ship with Phase 1 to keep
the parity matrix honest (or document the temporary divergence).

**Out of scope / explicitly not doing:** no migration of existing prefs keys, no formula or
export-format changes, no DI changes, no third-party settings library, no ViewModel
refactor (`SettingsViewModel.kt` grows only with the nudge derivation), no push-based setup
prompts, no Home-screen banners.

## 11. Success criteria

- Hub stays ≤ 6 group rows (+ Suggestions card with ≤ 3 rows); **no group screen exceeds
  ~12 rows at full expansion** (today: App 24, Data 19, AI 22).
- Every value reachable in ≤ 3 taps (hub → group → sheet/screen).
- Consistent affordances: chevron / switch / ⓘ / Auto chip / disabled-with-link only.
- Group labels match screen titles everywhere.
- **Every sub-screen is reachable from ≥ 1 related screen via an explicit cross-link row**
  (graph property, §6.2).
- **Water-reminder toggle is never hidden** — visible (enabled or disabled-with-link) in
  every state (§6.2 Rule B).
- Suggestions: ≤ 3 rows, no new-user nagging (48 h gate), dismissals persist, rows
  auto-hide when conditions resolve.
- `release:package` green (tests + parity + schemas), screenshots regenerated.
- No existing prefs keys, export formats, or formulas changed (diff-check; only the 2 new
  nudge keys).

## 12. Open questions

1. D1: Water top-level vs. sub-screen of App & Display?
2. D2: About stays a card vs. hub row?
3. D3: Include search in this overhaul or a follow-up?
4. D4: Nudge gating thresholds (3/7/14 days) — sensible defaults to ship, or tune by
   real usage first?
5. Should the Goals explainer card be collapsible (default open) or dismissible?
6. Renames in §8 touch 15 locales — acceptable for one release, or split renames to Phase 2?
7. Suggestions: 6 boolean dismissal keys (recommended, matches existing tip pattern) vs. a
   string-set of dismissed ids?
8. PWA: mirror cross-link rows in the same release (recommended) or document as Android-only
   temporarily?
