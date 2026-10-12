package app.chompass.services

import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.CaffeineEntry
import app.chompass.models.DailyNote
import app.chompass.models.FoodConstituent
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.models.NicotineEntry
import app.chompass.models.NicotineKind
import app.chompass.models.Recipe
import app.chompass.models.WaterEntry
import app.chompass.models.WeightEntry
import app.chompass.models.microsCompositionSignature
import app.chompass.services.health.DebugActivityDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * Defect families the debug seeder can install (one launch flag each). `ALL`
 * is what the umbrella `seed_defects` extra runs; groups are applied in
 * declaration order.
 */
internal enum class DefectGroup { GAPS, STALE_MICROS, LEGACY, DAYS, METRICS, TRACKERS }

/**
 * Stored *defect* shapes — the rows, readings and marks a real device carries
 * after imports, failed syncs, foreign clients or plain data rot. The app has
 * to tolerate all of them; nothing here is reachable through the UI, so these
 * fixtures are the only way to see those code paths on-device.
 *
 * Every function is pure (data only, no repository/side effects) and every id
 * is deterministic ([seedId]) because the month-bucket stores upsert by id —
 * re-running a defect pass must overwrite, never stack. The seeder
 * ([TestDataSeeder.seedDefects]) owns the writes and the deletions.
 *
 * Kept out of [SampleDataGenerators] on purpose: those generators are pinned
 * by tests/screenshots as the happy path, these shapes must never leak in.
 */
internal object SampleDataDefects {
    /** Deterministic fixture id (bucket stores upsert by id). */
    fun seedId(slug: String): UUID =
        UUID.nameUUIDFromBytes("chompass-seed-defect:$slug".toByteArray())

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant =
        day.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant()

    private fun row(
        slug: String,
        name: String,
        day: LocalDate,
        hour: Int,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        mealType: String = MealType.LUNCH.id,
        source: FoodSource = FoodSource.MANUAL,
    ): FoodEntry = FoodEntry(
        id = seedId(slug),
        name = name,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        timestamp = at(day, hour),
        source = source,
        mealType = mealType,
    )

    // ---------------------------------------------------------------- GAPS --

    /**
     * Current-month rows for the fill-missing surfaces: 3 all-zero rows, 2
     * partial-zero rows, 1 complete-macro row with every micro null (micro
     * gaps only → edit sheet, never the Home banner), 1 Mealie-shaped meal
     * whose 3 constituent rows are all-zero (containment matching), and the
     * two month-boundary rows.
     */
    fun gapRows(today: LocalDate): List<FoodEntry> = listOf(
        row("gap-all-zero-rice", "Reis ohne Werte", today.minusDays(1), 12, 0, 0.0, 0.0, 0.0),
        row("gap-all-zero-mealie", "Mealie Import", today.minusDays(2), 18, 0, 0.0, 0.0, 0.0),
        row("gap-all-zero-soup", "Suppe ohne Werte", today.minusDays(3), 19, 0, 0.0, 0.0, 0.0)
            // Reference to a photo file that no longer exists (pruned media).
            .let { it.copy(imageFilename = FoodImageStore.filenameFor(it.id)) },
        row("gap-partial-protein", "Hähnchen ohne Kohlenhydrate", today.minusDays(4), 13, 240, 28.0, 0.0, 0.0),
        row("gap-partial-carbs", "Apfelmus ohne Protein", today.minusDays(5), 16, 95, 0.0, 25.0, 0.0),
        row("gap-micros-only", "Vollständige Makros, keine Mikros", today.minusDays(6), 12, 420, 30.0, 40.0, 12.0),
        mealieConstituentRow(today),
        row(
            "gap-month-boundary-prev",
            "Reis ohne Werte (Vormonat)",
            YearMonth.from(today).minusMonths(1).atEndOfMonth(),
            12,
            0,
            0.0,
            0.0,
            0.0,
        ),
        row(
            "gap-month-boundary-first",
            "Reis ohne Werte (Monatsanfang)",
            YearMonth.from(today).atDay(1),
            12,
            0,
            0.0,
            0.0,
            0.0,
        ),
    )

    /** Two gapped favorites (the banner scans favorites before the month). */
    fun gapFavorites(today: LocalDate): List<FoodEntry> = listOf(
        row("gap-favorite-zero", "Seed Favorit ohne Werte", today.minusDays(8), 18, 0, 0.0, 0.0, 0.0),
        row("gap-favorite-partial", "Seed Favorit teilweise", today.minusDays(9), 18, 310, 24.0, 0.0, 0.0),
    )

    /**
     * The Mealie import signature: an entry with real totals whose constituent
     * rows are all-zero and named `amount unit food` — what
     * `namesMatchByContainment` / `containsAtWordBoundary` have to bridge to an
     * analysis row named "Mehl".
     */
    private fun mealieConstituentRow(today: LocalDate): FoodEntry {
        val constituents = listOf(
            FoodConstituent(
                name = "200 g Mehl",
                calories = 0,
                protein = 0.0,
                carbs = 0.0,
                fat = 0.0,
                servingSizeGrams = 200.0,
            ),
            FoodConstituent(
                name = "1 Ei",
                calories = 0,
                protein = 0.0,
                carbs = 0.0,
                fat = 0.0,
                servingSizeGrams = 60.0,
            ),
            FoodConstituent(
                name = "250 ml Milch",
                calories = 0,
                protein = 0.0,
                carbs = 0.0,
                fat = 0.0,
                servingSizeGrams = 250.0,
            ),
        )
        return FoodEntry(
            id = seedId("gap-mealie-constituents"),
            name = "Pfannkuchen (Mealie)",
            calories = 620,
            protein = 32.0,
            carbs = 70.0,
            fat = 22.0,
            timestamp = at(today.minusDays(7), 18, 30),
            emoji = "🥞",
            source = FoodSource.MANUAL,
            mealType = MealType.DINNER.id,
            servingSizeGrams = constituents.sumOf { it.servingSizeGrams },
            constituents = constituents,
        )
    }

    // -------------------------------------------------------- STALE_MICROS --

    private fun constituentMix(): List<FoodConstituent> = listOf(
        FoodConstituent("Chicken thigh", 240, 28.0, 0.0, 14.0, 150.0),
        FoodConstituent("Brown rice", 220, 5.0, 46.0, 2.0, 200.0),
        FoodConstituent("Roasted broccoli", 80, 4.0, 12.0, 3.0, 120.0),
    )

    /** The mix the stored signature was recorded for, before one row was renamed. */
    private fun renamedMix(): List<FoodConstituent> =
        constituentMix().mapIndexed { i, row -> if (i == 2) row.copy(name = "Steamed broccoli") else row }

    /**
     * One row whose micros were estimated for a mix that has since changed (the
     * dim stale-composition note + Re-estimate action) and one control row whose
     * signature still matches (no note).
     */
    fun staleMicrosRows(today: LocalDate): List<FoodEntry> {
        val current = constituentMix()
        val stale = FoodEntry(
            id = seedId("stale-micros"),
            name = "Chicken bowl (alte Mikros)",
            calories = 540,
            protein = 37.0,
            carbs = 58.0,
            fat = 19.0,
            timestamp = at(today.minusDays(1), 18, 30),
            source = FoodSource.MANUAL,
            mealType = MealType.DINNER.id,
            servingSizeGrams = current.sumOf { it.servingSizeGrams },
            constituents = current,
            microsCompositionSignature = microsCompositionSignature(renamedMix()),
        ).let { SampleDataGenerators.microsForSeed(0xDEAD).applyTo(it) }
        val control = FoodEntry(
            id = seedId("stale-micros-control"),
            name = "Chicken bowl (Mikros aktuell)",
            calories = 540,
            protein = 37.0,
            carbs = 58.0,
            fat = 19.0,
            timestamp = at(today.minusDays(2), 18, 30),
            source = FoodSource.MANUAL,
            mealType = MealType.DINNER.id,
            servingSizeGrams = current.sumOf { it.servingSizeGrams },
            constituents = current,
            microsCompositionSignature = microsCompositionSignature(current),
        ).let { SampleDataGenerators.microsForSeed(0xD00D).applyTo(it) }
        return listOf(stale, control)
    }

    /** The favorite-editor twin of [staleMicrosRows]: stale signature on a template. */
    fun staleMicrosFavorite(today: LocalDate): FoodEntry {
        val current = constituentMix()
        return FoodEntry(
            id = seedId("stale-micros-favorite"),
            name = "Seed Favorit alte Mikros",
            calories = 540,
            protein = 37.0,
            carbs = 58.0,
            fat = 19.0,
            timestamp = at(today.minusDays(11), 18, 30),
            source = FoodSource.MANUAL,
            mealType = MealType.DINNER.id,
            servingSizeGrams = current.sumOf { it.servingSizeGrams },
            constituents = current,
            microsCompositionSignature = microsCompositionSignature(renamedMix()),
        ).let { SampleDataGenerators.microsForSeed(0xFEED).applyTo(it) }
    }

    // ------------------------------------------------------------- LEGACY --

    /**
     * Foreign / legacy / hostile rows: the pre-signature shape, raw unsanitized
     * text, sanitizer ceiling values, kcal↔macro inconsistencies, an unknown
     * meal token, and a diary row whose favorite key collides with a favorite.
     */
    fun legacyRows(today: LocalDate): List<FoodEntry> = listOf(
        // Pre-signature legacy shape: constituents, no serving weight, OTHER slot.
        FoodEntry(
            id = seedId("legacy-shape"),
            name = "Seed Altbestand",
            calories = 480,
            protein = 26.0,
            carbs = 52.0,
            fat = 18.0,
            timestamp = at(today.minusDays(3), 13, 15),
            source = FoodSource.TEXT_INPUT,
            mealType = MealType.OTHER.id,
            servingSizeGrams = null,
            constituents = constituentMix(),
            microsCompositionSignature = null,
        ),
        // Raw text exactly as a foreign import could carry it: bidi override +
        // control chars + oversized name (InputSanitizer.MAX_NAME_LENGTH = 120).
        row(
            "legacy-hostile-text",
            "\u202Egrilled salmon \u0000\u0008" + "x".repeat(200),
            today.minusDays(4),
            12,
            520,
            40.0,
            12.0,
            30.0,
            source = FoodSource.TEXT_INPUT,
        ).copy(customNote = "\u202A" + "Notiz ".repeat(90)),
        // Sanitizer ceilings (MAX_CALORIES / MAX_MACRO_GRAMS / micro 1e5).
        row(
            "legacy-ceilings",
            "Seed Obergrenze",
            today.minusDays(5),
            12,
            10_000,
            2_000.0,
            2_000.0,
            2_000.0,
        ).copy(sodium = 100_000.0),
        // kcal without macros (0) and macros without kcal — both fillable, and
        // the pair is what the "Ergänzt:" diff line has to reason about.
        row("legacy-kcal-no-macros", "Seed kcal ohne Makros", today.minusDays(6), 12, 900, 0.0, 0.0, 0.0),
        row("legacy-macros-no-kcal", "Seed Makros ohne kcal", today.minusDays(7), 12, 0, 30.0, 40.0, 12.0),
        // Unknown meal token: renders as an orphan group, never crashes the day.
        row(
            "legacy-unknown-meal",
            "Seed unbekannter Slot",
            today.minusDays(2),
            10,
            350,
            12.0,
            44.0,
            11.0,
            mealType = "brunch",
        ),
    )

    /** A favorite whose key collides with the diary rows named "Seed Apfel". */
    fun legacyFavorite(today: LocalDate): FoodEntry = row(
        "legacy-favorite-collision",
        "Seed Apfel",
        today.minusDays(13),
        16,
        95,
        0.5,
        25.0,
        0.3,
        mealType = MealType.SNACK.id,
        source = FoodSource.TEXT_INPUT,
    )

    /** Unknown tracker kind tokens (the `other` fallback path). */
    fun legacyTrackerEntries(today: LocalDate): Pair<List<CaffeineEntry>, List<NicotineEntry>> =
        listOf(
            CaffeineEntry(
                id = seedId("legacy-caffeine-kind"),
                date = at(today.minusDays(1), 9, 30),
                kind = "matetee_future",
                mg = 40.0,
            ),
        ) to listOf(
            NicotineEntry(
                id = seedId("legacy-nicotine-kind"),
                date = at(today.minusDays(1), 10, 15),
                kind = "snus_future",
                count = 1,
                mg = 8.0,
            ),
        )

    /** A recipe with no ingredients (import residue; the builder must not crash). */
    fun legacyRecipe(today: LocalDate): Recipe = Recipe(
        id = seedId("legacy-empty-recipe"),
        name = "Seed leer",
        ingredients = emptyList(),
        createdAt = at(today.minusDays(20), 12),
    )

    // --------------------------------------------------------------- DAYS --

    /** Whole-day gaps plus the note-only / untracked-only days the pass clears. */
    val entryFreeDayOffsets: List<Long> = (14L..20L).toList() + listOf(11L, 12L)

    /** Past days marked untracked: 3 inside the gap window + the untracked-only day. */
    val untrackedDayOffsets: List<Long> = listOf(20L, 19L, 18L, 11L)

    /** Never today — an untracked mark on today would break the manual flow. */
    fun untrackedDays(today: LocalDate): Set<String> =
        untrackedDayOffsets.map { today.minusDays(it).toString() }.toSet()

    fun untrackedKcalByDay(today: LocalDate): Map<String, Int> =
        mapOf(today.minusDays(19L).toString() to 350)

    /** The note-only day: a note and no diary rows, not untracked. */
    fun noteOnlyNote(today: LocalDate): DailyNote = DailyNote(
        id = DailyNote.idFor(today.minusDays(12)),
        date = today.minusDays(12),
        text = "Nur eine Notiz an diesem Tag — keine Mahlzeiten erfasst, kein Urlaub-Marker.",
    )

    /** The same day the untracked-only mark needs: untracked, no rows, no note. */
    fun untrackedOnlyDay(today: LocalDate): LocalDate = today.minusDays(11)

    /** Non-planned future-dated row (the #96 future-week snap pages). */
    fun futureRow(today: LocalDate): FoodEntry = row(
        "future-row",
        "Seed Ausblick",
        today.plusDays(3),
        12,
        480,
        26.0,
        52.0,
        16.0,
    )

    /** Three diary rows sharing one favorite key (recent-list collapse paths). */
    fun sharedFavoriteKeyRows(today: LocalDate): List<FoodEntry> = listOf(5L, 7L, 9L).mapIndexed { i, offset ->
        row(
            "shared-key-$i",
            "Seed Apfel",
            today.minusDays(offset),
            16,
            95 + i * 25,
            0.5 + i * 0.1,
            25.0 + i * 5,
            0.3 + i * 0.1,
            mealType = MealType.SNACK.id,
        )
    }

    // ------------------------------------------------------------ METRICS --

    /** The 3-week sparse-logging window the pass clears from weight + body fat. */
    fun readingGapRange(today: LocalDate): Pair<Instant, Instant> =
        at(today.minusDays(45), 0) to at(today.minusDays(24), 23, 59)

    /** Same-day duplicates + a 5-second near-duplicate pair (dedupe/merge paths). */
    fun duplicateReadingRows(today: LocalDate): Pair<List<WeightEntry>, List<BodyFatEntry>> {
        val day = today.minusDays(2)
        val nearDay = today.minusDays(9)
        val weights = listOf(
            WeightEntry(seedId("dup-weight-0"), at(day, 7, 10), 80.0),
            WeightEntry(seedId("dup-weight-1"), at(day, 7, 40), 80.0),
            WeightEntry(seedId("dup-weight-near-0"), at(nearDay, 7, 0), 80.000),
            WeightEntry(seedId("dup-weight-near-1"), at(nearDay, 7, 0).plusSeconds(5), 80.004),
        )
        val bodyFats = listOf(
            BodyFatEntry(seedId("dup-fat-0"), at(day, 7, 10), 0.200),
            BodyFatEntry(seedId("dup-fat-1"), at(day, 7, 40), 0.200),
            BodyFatEntry(seedId("dup-fat-near-0"), at(nearDay, 7, 0), 0.195),
            BodyFatEntry(seedId("dup-fat-near-1"), at(nearDay, 7, 0).plusSeconds(5), 0.195),
        )
        return weights to bodyFats
    }

    /**
     * Absurd readings written straight into the buckets (the repositories would
     * sync the profile to them): a 0 kg row, a 999 kg row and body-fat
     * fractions at the ends of the plausible range.
     */
    fun absurdReadingRows(today: LocalDate): Pair<List<WeightEntry>, List<BodyFatEntry>> {
        val weights = listOf(
            WeightEntry(seedId("absurd-weight-zero"), at(today.minusDays(2), 8, 30), 0.0),
            WeightEntry(seedId("absurd-weight-max"), at(today.minusDays(3), 8, 30), 999.0),
        )
        val bodyFats = listOf(
            BodyFatEntry(seedId("absurd-fat-low"), at(today.minusDays(2), 8, 30), 0.02),
            BodyFatEntry(seedId("absurd-fat-high"), at(today.minusDays(3), 8, 30), 0.75),
            BodyFatEntry(seedId("absurd-fat-zero"), at(today.minusDays(4), 8, 30), 0.0),
        )
        return weights to bodyFats
    }

    /** Three single-site snapshots (waist only) + the all-null row. */
    fun measurementDefectRows(today: LocalDate): List<BodyMeasurement> = listOf(
        BodyMeasurement(seedId("measure-waist-0"), at(today.minusDays(3), 7, 30), waistCm = 84.5),
        BodyMeasurement(seedId("measure-waist-1"), at(today.minusDays(10), 7, 30), waistCm = 85.2),
        BodyMeasurement(seedId("measure-waist-2"), at(today.minusDays(17), 7, 30), waistCm = 85.8),
        // No site at all: hasAnyValue == false, the exporter drops it, the
        // Customize/plots screens must not list it.
        BodyMeasurement(seedId("measure-all-null"), at(today.minusDays(24), 7, 30)),
    )

    /** Two BMR-prorated resting days and one measured-zero day. */
    fun activityDefectDays(today: LocalDate): List<DebugActivityDay> = listOf(
        DebugActivityDay(date = today.minusDays(30).toString(), steps = 6_400, activeCalories = 220, totalCalories = null),
        DebugActivityDay(date = today.minusDays(31).toString(), steps = 3_100, activeCalories = 140, totalCalories = null),
        DebugActivityDay(date = today.minusDays(32).toString(), steps = 0, activeCalories = 0, totalCalories = 0),
    )

    // ----------------------------------------------------------- TRACKERS --

    /** A zero-count row and a max-dose row. */
    fun nicotineDefectRows(today: LocalDate): List<NicotineEntry> = listOf(
        NicotineEntry(
            id = seedId("nicotine-zero-count"),
            date = at(today, 9),
            kind = NicotineKind.CIGARETTE.storageKey,
            count = 0,
            mg = null,
        ),
        NicotineEntry(
            id = seedId("nicotine-max-dose"),
            date = at(today, 11),
            kind = NicotineKind.POUCH.storageKey,
            count = 1,
            mg = 30.0,
        ),
    )

    /** A caffeine log carrying the custom preset kind + a milk sidecar link. */
    fun caffeineDefectRow(today: LocalDate, linkedFoodEntryId: UUID?): CaffeineEntry = CaffeineEntry(
        id = seedId("caffeine-milk-defect"),
        date = at(today, 8, 15),
        kind = SampleDataGenerators.SEED_CAFFEINE_PRESET_ID,
        mg = 42.0,
        linkedFoodEntryId = linkedFoodEntryId,
    )

    /** Water extremes: one absurd single sip and one zero entry. */
    fun waterDefectRows(today: LocalDate): List<WaterEntry> = listOf(
        WaterEntry(seedId("water-max"), at(today, 6, 30), 5_000),
        WaterEntry(seedId("water-zero"), at(today, 22, 30), 0),
    )

    // ------------------------------------------------------------ helpers --

    /**
     * Every id this layer can install, across all groups. The seeder uses it to
     * keep its day-clearing deletion from erasing its own fixtures (the
     * month-boundary rows can fall inside the cleared window for a mid-month
     * "today").
     */
    fun fixtureIds(today: LocalDate): Set<UUID> = buildSet {
        gapRows(today).forEach { add(it.id) }
        gapFavorites(today).forEach { add(it.id) }
        staleMicrosRows(today).forEach { add(it.id) }
        add(staleMicrosFavorite(today).id)
        legacyRows(today).forEach { add(it.id) }
        add(legacyFavorite(today).id)
        add(legacyRecipe(today).id)
        val (caffeine, nicotine) = legacyTrackerEntries(today)
        caffeine.forEach { add(it.id) }
        nicotine.forEach { add(it.id) }
        add(noteOnlyNote(today).id)
        add(futureRow(today).id)
        sharedFavoriteKeyRows(today).forEach { add(it.id) }
        val (dupWeights, dupFats) = duplicateReadingRows(today)
        dupWeights.forEach { add(it.id) }
        dupFats.forEach { add(it.id) }
        val (absurdWeights, absurdFats) = absurdReadingRows(today)
        absurdWeights.forEach { add(it.id) }
        absurdFats.forEach { add(it.id) }
        measurementDefectRows(today).forEach { add(it.id) }
        nicotineDefectRows(today).forEach { add(it.id) }
        add(caffeineDefectRow(today, linkedFoodEntryId = null).id)
        waterDefectRows(today).forEach { add(it.id) }
    }
}
