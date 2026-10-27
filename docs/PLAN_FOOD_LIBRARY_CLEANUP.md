# Plan: Food library cleanup (Codeberg #142)

Status: WP1-3 shipped with device pass (2026-10-26/27). WP4 merge/dedupe
implemented 2026-10-27 — rides the next release train per the release shape
below.
Reporter @burdick-ryan, candidate, M total. Android-only v1; parity call below.

## Problem

Barcode/photo logging accretes a cluttered saved-food library: duplicates
(same food under two `favoriteKey`s), near-duplicates (different names for the
same product), wrong items (bad AI name/macros on the template), and one-off
items that pollute Add Food suggestions forever. Today the only cleanup is
export → wipe → edit → re-import (reporter's workaround).

## Ground truth (what the "database" is)

- There is no separate food table. A saved food = **newest diary row per
  `favoriteKey`** (within the 365-day index window) plus the **favorites
  list**; `buildSavedFoodIndex` (`data/SavedFoodIndex.kt:49`) collapses both
  into RECENT / FREQUENT / FAVORITE suggestions.
- Add Food rows: tap = pre-filled log flow (`onPick`), long-press = review
  sheet (`onReview`, `AddFoodSearchSection.kt:532-613`). The review sheet's
  only save writes a **new diary row** — editing a saved food this way logs a
  duplicate, exactly the reporter's complaint.
- Favorites already edit in place: FAVORITES tab tap → `EditFavoriteSheet` →
  `updateFavorite` (`HomeScreen.kt:1646-1662`, `FoodRepository.kt:467`).
  `dedupeFavoritesByIdentity` (`FoodRepository.kt:560`) already dedupes the
  favorites list at load.
- Diary rows long-press to multi-select delete (`HomeScreen.kt:937-939`) —
  history deletion exists but is day-scoped and destroys history.

## Semantics (decisions)

1. **Fix** a template = write the edit onto the template's diary row
   (`updateEntry` path), so the next re-log picks it up. For favorites this is
   `updateFavorite`; for RECENT/FREQUENT templates it edits that newest row.
   No new row, nothing logged.
2. **Remove from suggestions** = hide, not delete. New DataStore string-set
   `hidden_food_keys` of `favoriteKey`s; `buildSavedFoodIndex` (and the relog
   chips, same identity) filters hidden keys. Diary history stays intact.
   Hiding a favorited key unfavorites it (favorite wins otherwise).
3. **Destroy** stays explicit and scoped: "delete all N logged rows" per key,
   confirm dialog naming the count. Never the default action.
4. **Merge** two keys = rewrite rows of key A to key B (row-level
   `favoriteKey` update), re-point/remove favorite A, refresh template. Manual
   action from the browser's dedupe view; candidates suggested by equal
   normalized name (`QueryNormalizer`) or same barcode with distinct keys.

## Work packages

- **WP1 — hide plumbing (S):** `hidden_food_keys` in `PreferencesKeys` +
  `FoodRepository` accessors; filter in `buildSavedFoodIndex`; index cache
  invalidation on change (`HomeViewModel.kt:1005`); unit tests for filter +
  favorite-wins rule.
- **WP2 — saved-row actions (S):** in the review sheet opened from a saved
  row, add "Save to library" (fix template per semantics 1; favorites keep
  their existing save) and "Hide from suggestions"; destructive "Delete N
  logged rows" only inside the library browser, not here.
- **WP3 — library browser (M):** Settings → Health & Data → new "Saved foods"
  row (`DataSettingsScreen.kt` data group) → `FoodLibraryScreen`:
  `savedFoodIndex()` + hidden keys, search by name, sort by name/last
  logged/log count, per-key actions (edit template, favorite/unfavorite,
  hide/unhide, delete-all-with-count). Hidden-keys section with unhide.
- **WP4 — merge/dedupe (S-M, fast-follow):** candidate detection + merge per
  semantics 4. Ships separately after WP1-3 usage shows whether candidates
  detection pulls its weight.

## Tests

JVM (existing suites `FoodRepositoryFavoritesTest`, `FoodIdentityReuseTest`
as models): hidden-key filtering (index + relog), fix-template writes to the
template row only, hide clears favorite, merge rewrites rows + favorites +
template, delete-all removes exactly the key's rows. Browser UI verified on
the next device pass (long-press actions, unhide flow).

## Strings / i18n / docs

New user-facing strings go to `res/values/strings.xml` + all 18 locale files
(`docs/LOCALIZATION.md` flow). Docs: PARITY.md row "Saved meals / recipes /
favorites" gains the Android-only library browser note; CHANGELOG `[Unreleased]`
at ship; #142 gets the release reply (no ack now — nothing askable, ship
message carries it).

## Parity

Android-only v1: the PWA's saved surface (`saved-meals.js` tile hub) has no
suggestion-index equivalent to clean, and no contract changes (diary export
1.7 round-trips `favoriteKey` rewrites unchanged). PARITY.md records the gap;
PWA cleanup is a separate future ticket if wanted.

## Release shape

WP1-3 land as one train with a device pass (long-press actions, browser,
unhide); WP4 rides the next one. No wire/contract bumps.
