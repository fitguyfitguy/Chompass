# Android Efficiency / Stability / Redundancy Sweep

## Context

Codebase audit (6 parallel evidence-backed sweeps, all claims verified in source this session) of the Android module (`android/app/src/main/java/app/chompass/`, 380 files / ~91k lines) produced three tiers of work: verified correctness bugs, efficiency wins, and small redundancy merges. Repo culture (AGENTS.md) applies: minimal diffs, no Hilt/Room, manual DI, no formatters, one branch with ordered green commits, never push without ask, no AI attribution. All work lands on branch `chore/efficiency-sweep` off current `main`.

Step 0 (repo convention, multi-commit cross-cutting work): copy this plan into the repo as `PLAN_EFFICIENCY_SWEEP.md` at the repo root; delete it when the last commit lands (anti-bloat rule). Commit as the maintainer only.

Do NOT re-propose already-landed work: month-bucket writes, food-aggregates cache, PersistedJsonGuard, HC write retry, settings edit-wins race fix, on-device lean schema, OFF instant-results cap (all in 4.10–5.1 per docs/PERFORMANCE.md + CHANGELOG).

---

## WP1 — Correctness bugs (commit 1–5)

### 1. HomeUiState equals/hashCode drop emissions (verified, HIGH)
`android/app/src/main/java/app/chompass/ui/home/HomeViewModel.kt` — hand-written `equals` (lines 531–628) and `hashCode` (630–725) on `HomeUiState` omit `pendingAnalysis`, `pendingImageBytes`, `pendingAnalysisImages`, `pendingInputImageBytes`, `logTimeOverride`, and compare `pendingReviewSource` twice (equals line ~556+~559; hashCode lines 653+657). `MutableStateFlow(_ui, line 753)` dedupes by equals, so `_ui.update { copy(pendingAnalysis = …) }` emissions can be silently dropped.
Fix: add the 5 missing comparisons to `equals` (`ByteArray`/`List<ByteArray>` via `contentEquals`; `LocalTime` via `==`); remove the duplicate `pendingReviewSource` comparison in both `equals` and `hashCode`. Update `android/app/src/test/java/app/chompass/ui/home/HomeUiStateEqualsTest.kt`: add cases where ONLY each previously-omitted field differs → must be unequal, and StateFlow-emitting is implied by equals returning false.

### 2. Streaming fallback swallows CancellationException (verified, HIGH)
`services/ai/OpenAICompatibleClient.kt` — `analyzeStreaming` `catch (_: Throwable)` (lines 306–311) catches `CancellationException` (and any outer watchdog `TimeoutCancellationException`) and launches a full non-streaming `analyze(…)` retry. Fix: insert `catch (e: CancellationException) { throw e }` before the `catch (_: Throwable)` arm. Add a unit test next to `OpenAIStreamFinishlessRetryTest`: cancel the collecting coroutine mid-stream (MockWebServer slow-body response), assert `CancellationException` propagates and the server received no second request.

### 3. Widget snapshot goes stale across midnight (verified, MED)
`services/WidgetSnapshotWriter.kt` — `observe()` (lines 90–158) combines `foodRepository.entriesForDate(LocalDate.now())` with the date captured ONCE at app start. `publish()` (line 198) filters by `LocalDate.now()` internally, so after midnight a publish triggered by a water/profile change computes today's food from YESTERDAY's entry list → widget shows 0/stale kcal until process restart. The 00:00 alarm (NotificationService.CHANNEL_WIDGET_MIDNIGHT → `refresh()`) fixes it once, but subsequent same-day writes don't.
Fix: remove the entries flow from the outer combine (keep it as a change trigger via `foodRepository.entriesForMonth(YearMonth.now())` re-derived — simplest correct shape: keep the combine member as today's-month flow so same-month writes still trigger, but inside `onEach` fresh-read `val entries = foodRepository.entriesForDate(LocalDate.now()).first()` and pass that to `publish`). Same treatment in the combine's consumer path so `SnapshotInputs.entries` is always freshly read at publish time.

### 4. Silent pref corruption (verified, LOW)
`data/PreferencesStorePrefHelpers.kt` `objectPref` (lines 78–83): `runCatching { json.decodeFromString(...) }.getOrNull()` swallows decode errors with no log, unlike `decodeList` (lines 117–133) which logs and preserves. Fix: add `Log.w(TAG, "Undecodable object pref '$key.name' — treating as null", e)` mirroring `decodeList`'s message shape. (Bytes are already preserved on next write by `preserveUndecodableValue` — no behavior change beyond observability.) Fold into the data-stability commit.

### 5. Review-prompt check-then-act race (verified, LOW)
`data/FoodRepository.kt` lines 217–220 and 243–246: `if (!prefs.reviewPromptedAfterFirstLog.first()) { set…(true); request }` — concurrent `addEntry`/`addEntries` can both pass. Fix: private `val reviewPromptGate = AtomicBoolean(false)` in FoodRepository; `if (reviewPromptGate.compareAndSet(false, true)) { prefs.setReviewPromptedAfterFirstLog(true); ReviewPrompter.requestReview.value = true }`. Keep the pref read as a fast-path guard to avoid burning the CAS on later saves: `if (!prefs.reviewPromptedAfterFirstLog.first() && reviewPromptGate.compareAndSet(false, true)) { … }`.

---

## WP2 — Data-layer atomicity + efficiency (commit 6–8)

### 6. Unserialized read-modify-write in pref-backed repos (verified, MED)
Pattern `prefs.X.first()` → mutate → `prefs.setX(next)` with no atomicity in:
- `data/RecipeRepository.kt` `saveRecipe` (24–30), `upsertRecipes` (33–42), `deleteRecipe` (44–47), `moveRecipe` (49–56)
- `data/ManualActiveRepository.kt` add/update/delete (~19–40)
- `data/ChatRepository.kt` (~15–18)
- `data/BodyMeasurementRepository.kt` (~45–89)
Fix: collapse each into ONE `dataStore.edit { }` using the existing internal `Preferences.decodeList(key, serializer, json)` helper (PreferencesStorePrefHelpers.kt:117) for the read side and in-place encode for the write side. All files are in package `app.chompass.data` with access to `internal val json` and `Keys`. Keep each repo's public signatures unchanged. `SyncRepository.touch/tombstone` calls stay outside the edit (they're separate keys by design).
Test: one new JVM test (pattern: `SettingsPrefsHydrationTest`'s tmp-DataStore setup) launching two concurrent `saveRecipe` calls under `runTest`, asserting both survive; same for a `deleteRecipe` racing `saveRecipe` (no resurrection of the deleted id after both complete).

### 7. Favorites: non-atomic two-key writes + redundant migration (verified, MED)
`data/FoodRepository.kt`:
- `toggleFavorite` (368–369, 379–380), `updateFavorite` (432–433), `ensureFavoritesMigrated` (461–462, 469–470): `setFavoriteFoodEntries` + `setFavoriteKeys` as two separate DataStore edits — crash between leaves the legacy mirror divergent. Fix: one `dataStore.edit { }` writing `Keys.FAVORITE_FOOD_ENTRIES` + `Keys.FAVORITE_KEYS` together (reuse `preserveUnreadableList` semantics from the existing helpers).
- `ensureFavoritesMigrated` (451–472) runs 2–3 full snapshot reads on EVERY favorite op (call sites 363, 390, 425, ~135, ~529). Fix: `private val favoritesMigrated = AtomicBoolean(false)`; skip after the first completed pass.

### 8. Bucket-path efficiency (verified, MED)
- `data/PreferencesStoreBuckets.kt` `migrateBucketsToFilesIfNeeded()` (29–123) runs at EVERY cold collect of `foodEntriesImpl` / `foodEntriesForMonthImpl` / `foodEntriesForMonthsImpl` / `dailyFoodTotalsForMonthsImpl` (PreferencesStoreFood.kt:39–67): full `dataStore.data.first()` + `asMap()` key scan + two `monthsOnDisk()` per collect. Fix: `@Volatile private var bucketsMigrated = false` in PreferencesStore; early-return at the top of the migration when true; set true only after the function completes without exception (single-process assumption is already the design's foundation — document it in the KDoc).
- `PreferencesStoreFood.kt` `foodEntryByIdImpl` (48–49) decodes ALL month buckets to find one row (called per pending item from `NutritionHealthRetry.kt:~171`). Fix: iterate `foodBucketStore.monthsOnDisk()` newest-first, `readMonth(month)` with early exit on id match.
- `FoodRepository.deleteImageIfUnreferenced` (~533–536) makes 4 sequential full snapshot reads; `prefs.foodImageReferenceFilenames()` (PreferencesStoreFood.kt:217–236) computes the same reference set in one pass. Fix: `val referenced = prefs.foodImageReferenceFilenames() ?: return` (null = corrupt, keep the file — fail-safe, matches current prune-skip behavior), then `if (filename !in referenced) delete`.

---

## WP3 — Startup + services (commit 9–11)

### 9. Splash-gate main-thread binder work (verified, MED)
`MainActivity.kt` cold-start chain (389–426) runs on `lifecycleScope.launch` = Main. `AndroidAppIconManager.apply` (line 405) does `resolveLauncherIconTheme` (WallpaperManager binder read) + up to 8 `pm.getComponentEnabledSetting` probes (+ writes when the alias changes) on Main while the splash is pinned. Fix: `val themeColor = AppThemeColor.fromKey(initialThemeColorKey)` stays; wrap ONLY the apply call in `withContext(Dispatchers.Default) { AndroidAppIconManager.apply(…) }` (it touches no UI). `LocaleHelper.apply` (411) stays on Main.
Also: wrap `KeyStore` construction with a `PerfLog.measure("coldStart", "keyStoreInit")` mark (ChompassApp.kt — construction is currently forced at AppContainer init by the four `keyStore` ctor consumers at lines 233/273/313/314, defeating the `by lazy` at line 225). Measure-only this pass: if the cold-start ×5 capture shows ≥50 ms, provider-threading (`() -> KeyStore`) through the 4 ctors is a separate follow-up, not this sweep.

### 10. ReminderReceiver unmanaged scopes (scout-verified, MED)
`services/NotificationService.kt` — ReminderReceiver uses `goAsync()` + fire-and-forget `CoroutineScope(Dispatchers.IO).launch { … }` at 4 sites (~494, ~525, ~545, ~565); the 10s `goAsync` budget can kill mid-flight re-arm work and the scopes are never cancelled. Fix: route the work through the process-lifetime scope — `(context.applicationContext as ChompassApp).applicationScope.launch { … }` and call `pendingResult.finish()` when the launched block completes. Keep each site's work body byte-identical.
Also the daily reminder re-arm at ~655–663 uses `now + 24h` although `nextMidnightMillis()` exists in the same file (used by `scheduleWidgetMidnightRefresh` line 373) — switch to it.

### 11. AI/network layer (scout-verified, MED)
- Config snapshot: `FoodAnalysisService.callAi` preamble (~1704–1755) and `ChatService.sendMessage` (~95–131) do ~10–12 sequential `prefs.first()` reads per call — allow torn config (primary provider read, then fallback keys read after a mid-flight settings write) and waste flow setups. Fix: one new internal `suspend fun aiCallConfig(): AiCallConfig` on PreferencesStore reading all needed keys from a SINGLE `dataStore.data.first()` into a small data class (list the exact fields from the two preambles' reads during implementation; keep read order identical to today's). Rewire both preambles to destructure it. Do NOT cache across calls.
- `services/OpenFoodFactsService.lookupNetwork` (~592–604) uses blocking `execute()` with no cancellation tie-in while `RetryPolicy` already provides cancellation-tied `Call.await` (RetryPolicy.kt:80–90). Fix: route through the existing helper.
- `services/ai/RetryPolicy.kt:~59` buffers the whole error body before parsing. Fix: cap at 64 KB (`okio` peek with byteLimit or manual read loop) — oversized bodies truncate, parse failure falls through to the generic ladder as today.
- Silent parse failures: `OpenAiModelsClient`, `services/mealie/*` mappers, `services/ondevice/ollama*` parsers use `runCatching { … }.getOrNull()` with no log — add `Log.w` with the URL/key-context and exception (match `decodeList`'s style). Grep `getOrNull()` within those three areas; do not touch other packages.

---

## WP4 — Compose recomposition trims (commit 12–13)

### 12. Cheap, mechanical (scout-verified line anchors; re-read each)
- `ui/home/HomeFoodLog.kt:242–250` — `FoodLogMealGroup` `totalCalories/Protein/Carbs/Fat/Fiber/Sugar` are `get() = entries.sumOf { … }` re-evaluated per access. Compute once as constructor vals.
- `ui/home/HomeScreen.kt:648–655` — MacroCard `nutrient.current(ui.todayEntries)` / `nutrient.goal(...)` per card per recomposition. Hoist into `remember(ui.todayEntries, ui.homeTopNutrients, ui.optionalNutrientGoals) { … }` map.
- `ui/home/HomeScreen.kt:369–377` — debug `mealGroups.joinToString()` LaunchedEffect allocates in release. Guard with `if (PerfLog.enabled)` (pattern exists at the FAB).
- `ui/components/WheelPicker.kt:~215` — `items(items.size)` with no key; day lists change size (31→30) and rows recycle wrong. Add `key = { items[it] }` (all call sites build distinct lists; `NumericWheelPicker` dedupes at ~390).
- `ui/home/HomeScreen.kt:~833` and `ui/home/AddFoodSearchSection.kt:~596–613` — add `contentType` (`"food-row"` / `it.kind` respectively).
- `ui/home/HomeScreen.kt:~232, ~264` — LaunchedEffects keyed on volatile VM state re-launch `repeatOnLifecycle` on every analysis-phase flip. Key only on stable identity (`sharedImages`, `lifecycleOwner`; `stagedPhotoBytes?.isNotEmpty()`), pass the busy flag via `rememberUpdatedState`.

### 13. Derived-state + streaming sync (careful tier)
- `ui/home/HomeViewModel.kt` derived `get()` chains — `resolvedDayTargets` (~470, MacroPlanResolver + goalJournal scan), `dayTypeActiveStats` (~525, map merge) — recomputed on every access from hero composables. Fix at call sites in `HomeScreen.kt` (~600–670 hero block): `remember(ui.profile, ui.date, ui.goalJournal, ui.healthEnergyActiveByDay, ui.manualActiveByDay) { ui.resolvedDayTargets }` etc. — do NOT reshape VM state.
- `ui/home/FoodResultSheet.kt:289–312` — `LaunchedEffect(analysis, partial)` rewrites ALL editable fields from `partial.toPreviewAnalysis()` on every streamed token; can clobber user edits once the sheet unlocks. Fix: gate per field with the existing `servingTouched` pattern (add `caloriesTouched`/`nameTouched`-style flags only for fields the partial can overwrite); a field the user edited is never re-synced. No streaming-order changes.

---

## WP5 — Redundancy merges (commit 14)

- `models/GoalFormulaReference.kt:32–33` `formatMultiplier` vs `ui/settings/RecalcResultSheet.kt:302–303` private copy — keep GoalFormulaReference's, make it `internal`, delete the copy.
- Minutes→clock-string: reimplemented in 4 settings files with divergent 12/24h policies (incl. `ui/settings/SettingsMealTimesSheet.kt:332–336` which hardcodes policy and ignores the device 24h setting). Add `fun formatMinutesOfDay(context: Context, minutes: Int): String` to the existing `ui/util/ClockTime.kt` (reuse its `clockTimePattern`/`DateFormat.is24HourFormat` logic); migrate the 4 sites to it. This intentionally aligns SettingsMealTimesSheet with the system 12/24h setting — see Contingencies.
- kcal/P/C/F sums retyped at ~8 sites (verify count by grep `sumOf { it.calories }`): add `fun Iterable<FoodEntry>.nutritionTotals(): NutrientTotals` (or extend the existing totals model if one exists in `models/FoodEntry.kt`) returning calories/protein/carbs/fat(/fiber/sugar where sites need it); replace the retyped blocks. Do not change any rounding/formatting at call sites.
- `data/FoodRepository.kt` `restoreFromHealthConnect` (~556–595) vs `importExternalNutrition` (~646–680): identical ~23-field `FoodEntry` constructor blocks differing only in id source. Extract `private fun externalToFoodEntry(record: …, id: UUID): FoodEntry`; both callers pass their id.

---

## Critical files & anchors

1. `ui/home/HomeViewModel.kt` — equals 531–628, hashCode 630–725, `_ui` 753; derived chains ~470/~525.
2. `data/FoodRepository.kt` — review gate 217–220/243–246; favorites 362–472; image prune ~533–536; external mapping 556–595/646–680.
3. `services/ai/OpenAICompatibleClient.kt` — catch arms 288–311.
4. `data/PreferencesStoreBuckets.kt` + `data/PreferencesStoreFood.kt:39–67` — migration memoization.
5. `services/WidgetSnapshotWriter.kt` — observe 90–158, publish filter 198–200.

Line numbers are pre-edit hints from this session's reads; re-read each anchor before editing.

## Verification

- Full suite after each commit: `devenv shell bash -lc 'cd android && ./gradlew test'` (4 GB heap configured; always via devenv wrapper).
- New tests required: HomeUiStateEquals missing-field cases (WP1.1); streaming-cancel propagation (WP1.2); concurrent-RMW recipe test (WP2.6). Existing suites that must stay green and pin touched behavior: `BucketFileMigrationTest`, `JsonBucketStoreTest`, `FoodRepositoryFavoritesTest`, `WaterRepositoryTest`, `WheelPickerSelectionTest`, `HomeFoodLogLazyMoveTest` (the #56 key workaround — WP4.12 must not change the key format), `RetryPolicyTest`, `AiHttpTest`, `OpenAIStreamFinishlessRetryTest`, `HomeResolvedDayTargetsTest`, `HomeCalorieDisplayTest`, `FoodLogMealGroupsTest`.
- Behavioral smoke (no device needed): `devenv shell bash -lc 'cd android && ./gradlew :app:assembleDebug'` must succeed per commit.
- Optional device pass (maintainer's Windows adb, post-merge): `./scripts/install_debug.sh` then `scripts/capture_android_perf_baseline.sh` cold-start ×5 — the new `keyStoreInit` mark and flippidity benches quantify WP3/WP4.

## Assumptions & contingencies

- Scope: full sweep (user-selected). Deferred by design — record each as one backlog-index row in the final commit, do not implement: HomeUiState split into sibling flows (benchmark-gated refactor), JsonBucketStore LRU eviction (cache is also the change-notification bus), OFF↔RetryPolicy retry-ladder unification (rate-limit breaker entanglement), DiaryImporter↔SyncDocument parser merge (intentionally divergent accepted values), grounded tool-loop dedup (feature gated off), locale-divergent numeric formatters beyond `formatMultiplier` (Locale.US-vs-default may be intentional per site; AI-prompt-facing formatters stay untouched).
- WP1.1 changes emission behavior: previously-dropped `pendingAnalysis` emissions now reach the UI. If a sheet now opens where it previously didn't, that is the fix working; do not add compensating guards.
- WP5 clock unification flips SettingsMealTimesSheet to system 12/24h. If a test or doc pins the old hardcoded policy as intentional, keep the helper parameterized (`is24h: Boolean`) and pass each site's current policy — merge the code, preserve behavior only where provably intentional.
- WP3.9 KeyStore stays eager this pass; if `keyStoreInit` measures ≥50 ms on the baseline device, file the provider-threading follow-up instead of expanding this branch.
- If a scout line anchor drifted (file edited since), trust the current source, not the line number; the symbol names above are the contract.
