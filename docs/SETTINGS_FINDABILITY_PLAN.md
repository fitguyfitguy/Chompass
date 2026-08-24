# Settings Findability & Grouping Plan (2nd pass)

Status: **WIP 2026-02-13** — analysis complete; implementation started (Phases 1–4 landed in commits `31a195c4`…`85c7e21a`; Phase 5 parity/docs pending).

Successor to the archived [`SETTINGS_OVERHAUL_PLAN.md`](archive/SETTINGS_OVERHAUL_PLAN.md)
(shipped 3.10.0: per-domain sub-screens, cross-links, suggestions). That pass
fixed the big structural problems; this pass targets what remains: items that
are still hard to reach, inconsistently grouped, or mislabeled.

Scope: Android `ui/settings/` + the routes/nav that reach it, plus the PWA
`settings-view.js` mirror labels and `docs/PARITY.md` (settings is a *shared*
matrix row).

## 1. Current state (inventory)

Hub (`SettingsScreen.kt`): 6 groups + About card + dismissible Suggestions card.

| Hub group | Screen(s) behind it | Rows on screen |
|-----------|--------------------|----------------|
| Personal Info | `SETTINGS_PERSONAL` | Gender, Birthday, Height, Weight, Body Fat, Use-BF-for-BMR, Goal Body Fat, Body Measurements link |
| Goals & Nutrition | `SETTINGS_GOALS` | "How targets work" card, Weight Goal, Diet Mode, (Keto ×2), Activity, Weekly Change, Goal Weight, Adaptive Goals, Energy Burn, Calories + 3 macros (lockable), Other Nutrient Goals, Water goal link, Recalculate, Recalc details, Calculation Methods |
| Food & Entry | `SETTINGS_FOOD` | Logging (grams default, sort, meal times), Photo analysis (note prompt, portion clarify, constituents), Serving size (mode, heuristics) |
| App & Display | `SETTINGS_APP` | Home Display link, Appearance, Language, Theme Color (inline dropdown), Fixed Launcher Icon, Week Starts On, Customize Progress, Water link, Nicotine link, Notifications link |
| AI & Speech | `SETTINGS_AI` | AI master switch, Coach tab, Provider, Model, (Vision model), (Reasoning), (API key), (Base URL + insecure HTTP), (On-device model), (Max tokens), (Read timeout), (Gemini search), Serving-unit link · **Custom AI Instructions** · **Fallback Provider** · **Speech-to-Text** |
| Health & Data | `SETTINGS_DATA` | Health Connect, Manage access, (Background sync), Safety/medical expander, Export diary, Export body metrics, Import diary, Import body metrics, Sync link · **Danger zone** (clear food log, delete all) |
| About | on hub | Update check, Share, Open source, Asset credits, Upstream, Report issue, Request feature, Privacy, Donate |

Deeper sub-screens not on the hub: Water, Nicotine, Notifications, Sync
(4× `from=` routes), Home Display, Customize Progress, Optional Nutrient Goals,
Calculation Methods, Body Measurements. Total surface ≈ **120 settings across
15 screens**.

## 2. Problems found (evidence-based)

### Findability

- **F1 — No search.** The archived plan's P6 ("no search") never shipped. With
  ~120 settings across 15 screens and a 6-row hub, the only way to find a
  setting is to know which group it is in. Highest-value fix in this pass.
- **F2 — Units have no home.** Metric/Imperial is set only during onboarding or
  *inside* the Height/Weight/Goal-Weight sheets (`SettingsSheets.kt` HEIGHT /
  WEIGHT / GOAL_WEIGHT pass `onUnitChange`). There is no standalone "Units"
  row. `settings_metric_units` exists in all 16 locales but is **dead code**
  (referenced nowhere). A post-onboarding user who wants imperial must guess:
  Personal → Height → toggle unit inside the sheet.
- **F3 — Home water "auto" badge lands on the Settings hub, not Water.**
  `HomeScreen.kt:512` `WaterProgressRow(onAutoClick = onOpenSettings)` → hub
  top; the user must re-drill App & Display → Water → Dynamic goal. The badge
  is the one place the Home screen points at a specific setting and it points
  at the wrong level.
- **F4 — Speech-to-Text buried at the bottom of a 20+ row AI screen.**
  `AiSettingsScreen.kt` renders AI → Custom Instructions → Fallback → Speech.
  Speech is a distinct concern (own provider, key, language, privacy note) with
  3 rows of its own, but it sits after Fallback Provider, which is itself a
  niche feature. The hub label "AI & Speech" names it, but within the screen it
  is the last card.
- **F5 — Trackers live inside "App & Display".** The group mixes look & feel
  (appearance, theme, language, launcher icon) with trackers (water, nicotine)
  and reminders (notifications). The hub summary (`settings_group_app_summary`)
  says "Appearance, home, water, notifications" and does not mention nicotine
  at all. A user looking for water/nicotine from the hub must know they are
  filed under "App & Display".
- **F6 — Progress settings are 3 levels deep with no entry from Progress.**
  `weekStartDay` feeds Home's WeekStrip (`HomeScreen.kt:115`) and
  `progressDefaultRangeId` feeds Progress; both are edited only under
  Settings → App & Display → (Week Starts On / Customize Progress). The
  Progress tab has no settings affordance (`ProgressScreen.kt` references
  settings only in a comment).
- **F7 — Sync/backup reachable only via Health & Data (or the 14-day
  suggestion).** Acceptable as the only destination, but the hub summary says
  "Health Connect, export, sync" — "backup" is the user word; consider wording.

### Understanding

- **U1 — "Notifications" vs "Reminders" naming split.** Screen title
  "Notifications" (`settings_notifications`), inner card "Reminders"
  (`settings_section_notifications`), hub row "Notifications", suggestion
  "Turn on **reminders** for streaks and goals", body copy "Choose which
  **reminders** Chompass sends". Two vocabularies for one feature.
- **U2 — "How your targets work" is a static text dump.** Three stacked
  `bodySmall` paragraphs (adaptive, energy-burn, lock semantics) before the
  rows they explain; the Energy Burn line references Health Connect before the
  user has seen the toggle. Works, but it is the first thing on the screen and
  the longest.
- **U3 — AI screen complexity.** Master switch + Coach tab + provider/model +
  fallback + on-device + custom instructions is a lot of interdependent state;
  the per-provider privacy footnotes are good but arrive after the user has
  already committed to a provider.

### Function

- **X1 — About card runs an update check on every hub visit.**
  `AboutSettingsRows` fires `AndroidUpdateChecker.check` in
  `LaunchedEffect(currentVersion)` each time the Settings tab composes
  (tab switch recreates the composable via `restoreState`). Each hub visit = a
  network call to the Codeberg releases API for an informational row (F-Droid
  is the real update channel). Should be cached with a TTL, not re-checked.
- **X2 — Water goal has 3 entry points** (Goals cross-link row, App & Display
  row, Water screen) plus 2 suggestion rows. Values stay consistent, but the
  duplication is a symptom of the missing "Trackers" grouping (F5).

### Consistency

- **C1 — Theme Color uses an inline `DropdownMenu` while Appearance, Language
  and Week Starts On open sheets.** Same kind of single-choice picker, two
  interaction patterns. The dropdown also needs a fragile zero-size anchor
  `Box(Modifier.align(Alignment.BottomEnd))` hack (`SettingsAppSection.kt`) to
  position under the row's trailing edge.
- **C2 — "Related" footer used on only 2 of 7 sub-screens.** Water and
  Notifications have it; Goals (links Water goal + Calculation Methods), AI
  (links Food & Entry serving), Data (links Sync inline) do not. Rule C of the
  archived plan applied inconsistently.
- **C3 — Screen title vs inner card label mismatch.** App screen title is
  "App & Display" (`settings_group_app_display`) but its section card says
  "App Settings" (`settings_section_app`). Notifications screen title
  "Notifications" vs card "Reminders" (U1).
- **C4 — Duplicated group labels.** Screen title and section card repeat the
  group name on Personal, Goals, Health & Data ("Health & Data" card + "Danger
  zone" card under a "Health & Data" screen title). Harmless, but adds vertical
  noise where a single title would do.

## 3. Goals and non-goals

**Goals**
1. Every setting reachable in ≤2 taps from the hub (or via search in 1).
2. One obvious home per concept (units, trackers, progress display).
3. One vocabulary per feature (reminders), consistent interaction patterns.
4. No dead strings, no repeated network work.

**Non-goals**
- No new settings features (no new toggles/behaviors beyond re-homing).
- No PWA behavior change beyond label/group parity on the settings hub.
- No formula/data/contract changes (settings prefs are Android-local).

## 4. Proposed changes

### Phase 1 — Naming & consistency quick wins (small commits, no nav changes)

1. **U1/C3 — pick "Reminders" as the feature name.** `settings_notifications`
   → "Reminders" (screen title + hub row + App & Display row + suggestion text
   already says reminders). Keep the word "Notifications" only where it means
   the OS permission channel ("Notifications are off — allow them in system
   settings" style copy). Mirrors the PWA? (PWA hub uses "App"; check its
   reminders label and align.)
2. **C3 — App & Display card label** `settings_section_app` "App Settings" →
   "App & Display" (or whatever Phase 3 renames the group to).
3. **C1 — Theme Color → sheet.** Replace the inline dropdown + anchor hack with
   the standard `SettingsSheet.THEME_COLOR` sheet flow (swatch list like
   `AppearanceSheet`). Removes the zero-size-anchor fragility.
4. **X1 — cache update check.** Hold update state in a container-level holder
   with a TTL (e.g., 6 h) so hub visits don't re-hit the network; add a manual
   refresh row tap (already exists for Idle/Failed).
5. **C2 — "Related" footers on remaining sub-screens.** Goals → (Water,
   Calculation Methods); AI → (Food & Entry); Personal → (Body Measurements).
   Small, mechanical.
6. **U2 — tighten the "How your targets work" card.** Convert the three
   paragraphs to one short intro + the info-dialog pattern already used by the
   Adaptive/Energy toggles (or keep text but drop it below the toggles it
   explains).

### Phase 2 — Give Units a home (F2)

7. Add a **Units** row at the top of Personal Info (below/above the profile
   rows) opening a sheet that sets `heightUnit` + `weightUnit` together
   (single Metric/Imperial toggle, mirroring onboarding's
   `OnboardingProfileSteps`). In-sheet unit toggles on Height/Weight remain as
   quick overrides.
8. Wire the orphaned `settings_metric_units` string into the new row (removes
   dead string across all 16 locales), or delete the string if the row uses a
   combined label.

### Phase 3 — Re-group: trackers out of "App & Display" (F5, F6, F4)

9. **Split the hub group.** "App & Display" → **Display** (appearance, theme,
   language, fixed launcher icon, Home Display, Customize Progress) and a new
   **Trackers & Reminders** group row (Water, Nicotine, Notifications) that
   navigates straight to the existing `water/nicotine/notifications` routes
   (drop the now-redundant cross-link rows from the Display screen). Hub grows
   6 → 7 groups; update `settings_group_*_summary` strings to name nicotine and
   reminders explicitly.
10. **Progress entry point.** Add a small "Customize" affordance on the
    Progress screen header → `CUSTOMIZE_PROGRESS`; move **Week Starts On** into
    the Customize Progress screen (it is a calendar/progress preference, and it
    removes a row from Display).
11. **Speech reorder** (F4): in `AiSettingsScreen.kt` render AI → **Speech** →
    Custom Instructions → Fallback, and extend the hub summary to say
    "Providers, models, speech-to-text". (Keep Speech in the AI screen; a
    dedicated screen is overkill for 3 rows.)
12. **Backup wording** (F7): Data hub summary "Health Connect, export, backup"
    (mentions the user word), and keep the Sync row + suggestion as-is.

### Phase 4 — Settings search (F1)

13. New `ui/settings/SettingsIndex.kt`: a static, offline registry of every
    setting: `(group, label, keywords, route)` — keywords include synonyms
    ("units", "imperial", "ml", "reminder", "backup", "webdav"). Conditional
    rows (keto, on-device, heuristics) get a group tag so search still finds
    them.
14. Hub gains a **search field** under the title (or a search icon opening a
    results screen). Typing filters the index; each result shows group context
    and navigates on tap. Route builders for the 4 `from=` routes; plain routes
    for the rest. No AI, no data-layer changes; pure UI + registry.
15. Success bar: any setting findable by typing a word a user would plausibly
    guess (test with a list of the 120 labels → keywords coverage ≥ 90 %).

### Phase 5 — Verification & parity

16. Update `docs/PARITY.md` settings row: hub group rename (Display +
    Trackers & Reminders), Units row, search (Android-only unless mirrored),
    reminders naming.
17. Mirror hub group labels in PWA `settings-view.js` (it already uses
    "Personal Info" / "Goals & Nutrition" subpage bars; align Display/Trackers
    wording if the PWA hub groups change; otherwise note the delta).
18. Device pass (Windows adb): walk each hub group + search results; check
    back-label `from=` routing still reads correctly after re-homing;
    screenshots via `release:screenshots` where the harness covers settings.
19. `devenv tasks run release:check-parity` + Android unit tests green.

## 5. Strings & locales

Every label change (Phases 1–3) touches `res/values/strings.xml` **and** all
15 locale variants (`settings_*` keys) plus `testdata/parity/locales.json`
per the shared-locale contract. New keys: Units row, Display group, Trackers &
Reminders group, search placeholder/empty state. Dead keys to delete:
`settings_metric_units` only if the Units row doesn't reuse it.

## 6. Risks / anti-patterns

- **Don't hide dependencies** (archived Rule A): Water reminder toggle must
  stay visible-and-disabled with its "Needs water tracking" link when tracking
  is off; the Trackers group must not orphan the `from=` back labels.
- **Search must not grow scope**: static index only; no fuzzy/AI matching, no
  result persistence, no deep-link handling.
- **Hub length**: 7 groups + About + Suggestions is still one screen of
  scrolling; if it feels long after Phase 3, revisit Suggestions placement
  (keep — it is the onboarding nudge) before collapsing groups.
- **Don't re-introduce inline conditionals** (archived P1): keto/on-device rows
  stay gated where they are; the registry, not the UI, carries discoverability.

## 7. Success criteria

- Every hub group name matches the screen title it opens (no C3-style drift).
- Units, nicotine, water, progress customization each findable in ≤2 taps or
  one search.
- No settings screen > ~16 rows; AI screen no longer the last-resort home for
  speech.
- Zero dead strings; hub visit performs zero network calls.
- PARITY + PWA hub labels agree with Android; parity check green.

## 8. Open questions

1. "Trackers & Reminders" vs "Water, Nicotine & Reminders" — is nicotine
   significant enough for its own row, or a sub-row of a "Trackers" card?
   **Executed: Trackers & Reminders group with a dedicated screen** (future
   trackers like caffeine slot in).
2. Should the Progress "Customize" affordance be a gear icon or a text chip
   (consistency with the Home auto-badge pattern)?
   **Executed: tune icon + "Customize progress" text chip above the range
   picker.**
3. Is a full search field on the hub better than a search icon → dedicated
   screen? **Executed: full search field on the hub** (results replace the
   group list inline; one less navigation hop).
4. Reuse `settings_metric_units` as the new Units row label, or add
   "Units"? **Executed: new `settings_units` "Units" key** (the row shows the
   current system, not just metric); `settings_metric_units` remains dead but
   is kept for locale stability.

## 9. Execution log (deviations from the plan)

- **X1 (update check on every hub visit) — dropped as non-issue.**
  `AndroidUpdateChecker.check` is a no-op in the release build (returns
  `UpToDate` immediately; F-Droid is the update channel). No network call
  happens, so no caching work was needed.
- **U1 resolved as "Notifications" (not "Reminders").** All 15 locale files
  already translate the feature name as "notifications"; the mismatch was the
  section card "Reminders" label, which now reuses the screen-title key. Body
  copy keeps "reminders" as the prose word. Zero retranslation needed.
- **New-string budget:** 5 new EN-only keys (`settings_units`,
  `settings_group_trackers` + summary, `settings_search_hint` + empty); locale
  caps 35 → 40 (same mechanism as the nicotine-tracker bump).
- **Goals Related footer** carries Water + Calculation Methods (both moved out
  of the goals card, per the plan's C2). AI/Personal footers were skipped:
  their cross-links already exist as in-card value rows, so a duplicate footer
  row would be noise.
- **C2 footers**: only Goals got a footer; Water/Notifications already had one.

## 10. Device pass (pending — maintainer, run from Windows PowerShell)

No USB device is visible from WSL adb; the walk below is the remaining
verification. Build first (`devenv shell bash -lc 'cd android && ./gradlew
:app:assembleDebug'`), then install + walk from the Windows host:

```powershell
adb install --user 0 -r \\wsl$\<distro>\home\<user>\chompass\android\app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
adb shell am start -n app.chompass.debug/app.chompass.MainActivity
# Walk: Settings tab → search "units" → Personal Info row → Units sheet toggles
#   metric↔imperial and the Height/Weight sheets follow; Settings → Trackers &
#   Reminders → each of Water / Nicotine / Reminders keeps a correct back label
#   ("Trackers & Reminders"); Settings → Display (6 rows, no tracker links);
#   Progress tab → Customize chip → Week Starts On + default range; back labels
#   from Goals footer (Water, Calculation Methods).
# Search coverage spot-checks: "keto", "webdav", "speech", "theme".
```
