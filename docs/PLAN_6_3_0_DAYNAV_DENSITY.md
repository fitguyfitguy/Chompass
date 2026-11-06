# PLAN 6.3.0 — Day-nav & density design pass (WP-D design bundle)

2026-11-06. Rides 6.3.0-beta.2 together with WP-C (capsule shape token — separate
slice; shape sites flagged here, not fixed). Design decisions approved by the
maintainer 2026-11-06 (all four recommended options). Ground truth from four
surveys (current anchors, verified 11-06): day-nav (HomeScreen.kt), hero
(HomeCalorieHero.kt), planned-day (HomeFoodLog/PlanWeek/HomeViewModel), review
sheet (FoodResultSheet.kt). Supersedes the 09-23 eval anchors, which were stale.

## Decisions

| # | Call | Choice |
|---|---|---|
| 1 | Day-nav affordance | Caption cross-fade on day change + TalkBack prev/next-day custom actions; chevrons stay decorative |
| 2 | Hero legend | "+X planned" caption in all 4 variants + zone legend documented inside the ⓘ budget dialog with LegendSwatch swatches; Home stays height-neutral |
| 3 | Planned on the arc | Keep ghost underlay; add fill-end→planned-end segment (increased alpha + planned-end notch) in arc + bar variants; rides the "+X planned" caption |
| 4 | Review-sheet disclosure | Standard: constituents default-collapsed; macro card collapsed to a one-line summary with tweened expand; what-if + hide rows stay. Maestro `typed-amount-manual-entry` updated |

Sub-decisions (agent, no ask owed): empty "nothing planned/logged" card gains an
Add-food CTA (Effort-S gap); plannedAck generalization skipped; meal-header
planned/logged split skipped (follow-up); planned alpha unification (0.35 vs
0.38) skipped.

## 1. Day-nav (HomeScreen.kt)

- Caption row `:658-705`: wrap contents in `AnimatedContent(selectedDate)`,
  `AppMotion.spatialSpring()` cross-fade (pattern: WeekStrip.kt:249,
  PressFeedback.kt:36). Today vs other-day content unchanged.
- Hero drag region `:615-651` stays byte-identical (accumulator gesture, 16 dp
  dead-zone, 120 dp commit; do not hoist `accum`).
- A11y: `semantics { customActions }` prev/next day on the hero region node
  (precedent HomeFoodLog.kt:508-512), gated by `canSwipeForward` for next.
  New strings `home_a11y_prev_day` / `home_a11y_next_day`.
- New interactive elements: none visible → no testTag churn; keep
  `contentDescription = null` on decorative chevrons (:2549-2563).
- Risks: don't touch `todayEntries` collection (`uiAck` benchmark timing);
  WP-C will re-touch the jump pill `CircleShape` (:687) — flag only.

## 2. Hero planned visibility (HomeCalorieHero.kt)

- `CalorieHeroDerived` (:193-241) gains planned-caption field; all 4 variants
  (dispatch :172-176) render "+X planned" when `planned > 0`:
  Default/SideGauge/CompactArc in the BudgetGoalLine/remaining wedge area,
  CompactHorizontal in its FlowRow (:1265-1373). Height-neutral.
- `CalorieArcCanvas` (:270-447): add segment [fill-end, planned-end] at
  increased alpha over the 0.35 underlay + 2 dp planned-end notch (mirror the
  base-notch pattern :437-447). Bar twin :1164-1170. Alpha calibrated like the
  55 % consumed rule (:418-424) — verify against all theme accents; do not
  blind-copy values.
- `BudgetExplanationDialog` (:1543-1726): new "how to read the ring" legend
  block with `LegendSwatch` (ProgressWeightSection.kt:52-90): eaten ramp,
  planned ghost, estimated-active (tertiary@45 %), live burn (tertiary),
  over-typical (success); gains the `planned` param (no caller change — dialog
  lives inside the hero file). Preserve the one-meaning-per-hue invariant
  (:709-716).
- A11y: arc gains planned/shade context in its semantics when active
  (`progressBarRangeInfo` only today, :320-323); legend labels are visible
  strings, zone naming follows existing `home_active_burn_*_a11y` language.
- Strings: `home_planned_caption` (+%1$s planned), legend labels (5), dialog
  legend title.
- Risks: TextAutoSize min 8sp + widthIn caps for legend rows (long
  translations); widget gauge reads hero colors (WidgetTheme.kt) — no color
  semantics change, only additions; `HomeLookPreview.kt:88-92` passes
  `planned = 0` — update one preview fixture to `planned > 0` so the segment
  renders in preview refs and screenshot fixtures.

## 3. Planned-day empty-state CTA (HomeScreen.kt)

- `:906-923`: empty card gains an Add-food CTA button (same action as the FAB →
  Add Food hub; on future days this naturally lands in the plan flow via
  `plannedFor`, FoodLogTime.kt:57-64). String `home_empty_add_cta`.
- PlanWeek empty day sections (:405-413) stay text-only (day header already has
  Add).
- Presentation-only: no planned data in exports/sync/HC (PARITY row 33 holds).

## 4. Review-sheet standard disclosure (FoodResultSheet.kt)

- Constituents default open → collapsed (`:292-294`); keep
  `remember(effectiveAnalysis)` reset semantics; section plumbing unchanged.
- Macro card (:1031-1067): collapsed to a one-line summary (kcal · P · C · F ·
  fiber) with tweened expand via the per-row wheel pattern (:1485-1490,
  `expandVertically` from Top, `AppMotion.spatialSpring`). Editable state stays
  hoisted in the sheet body (:355-360); collapsing with typed drafts must run
  the `MagnitudeDrafts.commitAll()` flush (comment :309-311).
- Unlock/lock row, tip strip, serving card, micros, what-if, hide rows:
  unchanged.
- Maestro: update `android/maestro/flows/typed-amount-manual-entry.yaml:44-48`
  scroll/swipe assumptions to the collapsed card; keep the flushed-draft
  assertion (521) intact.
- A11y: collapsed macro card toggle gets expanded/collapsed stateDescription;
  `LiveRegionMode.Polite` strip untouched.

## Out of scope

Full on-hero legend row, deep disclosure (more-actions pill, meal+time merge),
edit-mode sheet pass (EditFoodEntrySheet.kt), WP-C shape normalization,
planned-row kcal tinting, plannedAck generalization.

## Verification

- `devenv shell bash -lc 'cd android && ./gradlew :app:testDebugUnitTest'` +
  `:app:assembleDebug`.
- Compose previews incl. updated planned>0 fixture; screenshot fixtures ride
  the release recapture (`release:screenshots`).
- Maestro flow file updated; device pass folded into the maintainer's next
  scale-session device pass (no dedicated pass owed for presentation-only).
- Backlog WP-D row + CHANGELOG Unreleased→[6.3.0] updated at ship.
