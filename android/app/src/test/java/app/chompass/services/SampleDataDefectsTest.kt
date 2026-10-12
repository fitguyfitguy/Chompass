package app.chompass.services

import app.chompass.models.BodyMeasurement
import app.chompass.models.DailyNote
import app.chompass.models.FoodEntry
import app.chompass.models.WeightEntry
import app.chompass.models.gapReport
import app.chompass.models.microsStaleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/**
 * The defect fixtures are pure data, so every tolerance rule the app relies on
 * can be asserted here without a device: which rows the fill-missing surfaces
 * see, which signature counts as stale, which shapes stay orphans, and that the
 * day-clearing deletion cannot eat its own fixtures.
 */
class SampleDataDefectsTest {
    private val today = LocalDate.of(2026, 8, 19)

    private fun dayOf(entry: FoodEntry): LocalDate = dayOf(entry.timestamp)

    private fun dayOf(instant: Instant): LocalDate =
        LocalDate.from(instant.atZone(ZoneId.systemDefault()))

    private fun clearedDays(): Set<LocalDate> =
        SampleDataDefects.entryFreeDayOffsets.map { today.minusDays(it) }.toSet()

    @Test
    fun allZeroAndPartialRowsReportMacroGaps() {
        val rows = SampleDataDefects.gapRows(today)
        val allZero = rows.filter { it.name.endsWith("ohne Werte") || it.name == "Mealie Import" }
        assertEquals(3, allZero.size)
        assertTrue(allZero.all { it.calories == 0 && it.protein == 0.0 && it.carbs == 0.0 && it.fat == 0.0 })
        assertTrue(allZero.all { it.gapReport().hasMacroGaps })

        val partial = rows.filter { it.name.startsWith("Hähnchen ohne") || it.name.startsWith("Apfelmus ohne") }
        assertEquals(2, partial.size)
        assertTrue(partial.all { it.gapReport().hasMacroGaps })
        // Partial zeros stay partial — the fill pass only writes zeros.
        assertTrue(partial.any { it.carbs == 0.0 && it.fat == 0.0 && it.protein > 0.0 })
        assertTrue(partial.any { it.carbs > 0.0 && it.protein == 0.0 })

        // One of them points at a photo file that no longer exists.
        assertEquals(1, rows.count { it.imageFilename != null })
    }

    @Test
    fun completeMacroRowWithNoMicrosIsMicroOnly() {
        val row = SampleDataDefects.gapRows(today).single { it.name == "Vollständige Makros, keine Mikros" }
        val report = row.gapReport()
        assertFalse(report.hasMacroGaps)
        assertTrue(report.hasGaps)
        assertTrue(report.missingMicros.isNotEmpty())
    }

    @Test
    fun mealieConstituentRowHasThreeFillableRows() {
        val row = SampleDataDefects.gapRows(today).single { it.name == "Pfannkuchen (Mealie)" }
        // Totals stay real; only the constituent rows are empty — the shape
        // namesMatchByContainment / containsAtWordBoundary have to bridge.
        assertTrue(row.calories > 0 && row.protein > 0.0)
        assertEquals(3, row.gapReport().zeroMacroConstituents.size)
        assertTrue(row.constituents.all { it.calories == 0 && it.protein == 0.0 })
        assertTrue(row.constituents.any { it.name == "200 g Mehl" })
        assertTrue(row.gapReport().hasMacroGaps)
    }

    @Test
    fun monthBoundaryRowsSitOnTheEdges() {
        val rows = SampleDataDefects.gapRows(today)
        val previous = rows.single { it.id == SampleDataDefects.seedId("gap-month-boundary-prev") }
        val first = rows.single { it.id == SampleDataDefects.seedId("gap-month-boundary-first") }
        assertEquals(YearMonth.from(today).minusMonths(1).atEndOfMonth(), dayOf(previous))
        assertEquals(YearMonth.from(today).atDay(1), dayOf(first))
        // Both are macro gaps: the scan's month filter is what excludes one.
        assertTrue(previous.gapReport().hasMacroGaps)
        assertTrue(first.gapReport().hasMacroGaps)
    }

    @Test
    fun gapFavoritesAreGappedToo() {
        val favorites = SampleDataDefects.gapFavorites(today)
        assertEquals(2, favorites.size)
        assertTrue(favorites.all { it.gapReport().hasMacroGaps })
        assertEquals(favorites.size, favorites.map { it.favoriteKey }.toSet().size)
    }

    @Test
    fun staleSignatureRowsMatchTheirOwnRule() {
        val rows = SampleDataDefects.staleMicrosRows(today)
        val stale = rows.single { it.id == SampleDataDefects.seedId("stale-micros") }
        val control = rows.single { it.id == SampleDataDefects.seedId("stale-micros-control") }
        assertTrue(microsStaleFor(stale.microsCompositionSignature, stale.constituents))
        assertFalse(microsStaleFor(control.microsCompositionSignature, control.constituents))
        // Both carry micros, so the note is the only difference between them.
        assertTrue(stale.fiber != null && control.fiber != null)
        val favorite = SampleDataDefects.staleMicrosFavorite(today)
        assertTrue(microsStaleFor(favorite.microsCompositionSignature, favorite.constituents))
    }

    @Test
    fun legacyHostileTextIsStoredVerbatim() {
        val row = SampleDataDefects.legacyRows(today)
            .single { it.id == SampleDataDefects.seedId("legacy-hostile-text") }
        // The fixture's whole point: it is NOT sanitized on the way in.
        assertTrue(row.name.startsWith("\u202Egrilled salmon"))
        assertTrue(row.name.contains("\u0000"))
        assertTrue(row.name.length > 120)
        assertTrue(row.customNote!!.startsWith("\u202A"))
        assertTrue(row.customNote!!.length > 500)
    }

    @Test
    fun legacyShapeAndTokenRowsKeepTheirOrphanShapes() {
        val rows = SampleDataDefects.legacyRows(today)
        val legacy = rows.single { it.id == SampleDataDefects.seedId("legacy-shape") }
        assertNull(legacy.microsCompositionSignature)
        assertNull(legacy.servingSizeGrams)
        assertNull(legacy.emoji)
        assertEquals("other", legacy.mealType)
        assertTrue(legacy.constituents.isNotEmpty())
        assertNull(legacy.fiber)

        assertEquals(
            "brunch",
            rows.single { it.id == SampleDataDefects.seedId("legacy-unknown-meal") }.mealType,
        )

        val ceilings = rows.single { it.id == SampleDataDefects.seedId("legacy-ceilings") }
        assertEquals(10_000, ceilings.calories)
        assertEquals(2_000.0, ceilings.protein, 0.001)
        assertEquals(100_000.0, ceilings.sodium!!, 0.001)

        val kcalNoMacros = rows.single { it.id == SampleDataDefects.seedId("legacy-kcal-no-macros") }
        assertTrue(kcalNoMacros.calories > 0 && kcalNoMacros.gapReport().hasMacroGaps)
        val macrosNoKcal = rows.single { it.id == SampleDataDefects.seedId("legacy-macros-no-kcal") }
        assertEquals(0, macrosNoKcal.calories)
        assertTrue(macrosNoKcal.protein > 0.0)

        val (caffeine, nicotine) = SampleDataDefects.legacyTrackerEntries(today)
        assertEquals("matetee_future", caffeine.single().kind)
        assertEquals("snus_future", nicotine.single().kind)

        assertTrue(SampleDataDefects.legacyRecipe(today).ingredients.isEmpty())
    }

    @Test
    fun allNullMeasurementRowHasNoValue() {
        val rows = SampleDataDefects.measurementDefectRows(today)
        val allNull = rows.filterNot { it.hasAnyValue }
        assertEquals(1, allNull.size)
        assertEquals(SampleDataDefects.seedId("measure-all-null"), allNull.single().id)
        // The other three are waist-only partial snapshots.
        val partial = rows.filter { it.hasAnyValue }
        assertEquals(3, partial.size)
        assertTrue(partial.all { it.waistCm != null && it.hipsCm == null })
        assertTrue(partial.all { BodyMeasurement.Site.entries.count { site -> it.value(site) != null } == 1 })
    }

    @Test
    fun untrackedMarksNeverTouchToday() {
        val marked = SampleDataDefects.untrackedDays(today)
        assertTrue(marked.isNotEmpty())
        assertFalse(today.toString() in marked)
        assertTrue(marked.all { LocalDate.parse(it).isBefore(today) })
        val kcal = SampleDataDefects.untrackedKcalByDay(today)
        assertEquals(1, kcal.size)
        assertTrue(kcal.keys.all { it in marked })
    }

    @Test
    fun entryFreeDaysCoverTheGapAndTheTwoSpecialDays() {
        val offsets = SampleDataDefects.entryFreeDayOffsets
        assertEquals(9, offsets.size)
        assertTrue(offsets.containsAll((14L..20L).toList()))
        val cleared = clearedDays()
        // The note-only day and the untracked-only day have no diary rows either.
        assertTrue(SampleDataDefects.noteOnlyNote(today).date in cleared)
        assertTrue(SampleDataDefects.untrackedOnlyDay(today) in cleared)
        // Nothing is cleared on today or in the future.
        assertFalse(today in cleared)
        assertTrue(cleared.all { it.isBefore(today) })
    }

    @Test
    fun rowsOutsideTheClearingWindowStayOutOfIt() {
        val cleared = clearedDays()
        SampleDataDefects.sharedFavoriteKeyRows(today).forEach {
            assertFalse("shared-key row inside the cleared window", dayOf(it) in cleared)
        }
        SampleDataDefects.gapFavorites(today).forEach {
            assertFalse("gap favorite inside the cleared window", dayOf(it) in cleared)
        }
        SampleDataDefects.legacyRows(today).forEach {
            assertFalse("legacy row inside the cleared window", dayOf(it) in cleared)
        }
        assertTrue(dayOf(SampleDataDefects.futureRow(today)).isAfter(today))
        // The month-boundary row CAN fall inside the window for a mid-month
        // today — that is exactly what the fixture-id guard protects.
        assertTrue(SampleDataDefects.seedId("gap-month-boundary-prev") in SampleDataDefects.fixtureIds(today))
    }

    @Test
    fun sharedFavoriteKeyRowsCollapseToOneKey() {
        val rows = SampleDataDefects.sharedFavoriteKeyRows(today)
        assertEquals(3, rows.size)
        assertEquals(1, rows.map { it.favoriteKey }.toSet().size)
        assertEquals(3, rows.map { it.calories }.toSet().size)
        // The legacy favorite reuses that same key (identity collision path).
        assertEquals(rows.first().favoriteKey, SampleDataDefects.legacyFavorite(today).favoriteKey)
    }

    @Test
    fun metricDefectsStayInsideTheirWindows() {
        val (from, to) = SampleDataDefects.readingGapRange(today)
        assertTrue(from.isBefore(to))
        assertEquals(today.minusDays(45), dayOf(from))
        assertEquals(today.minusDays(24), dayOf(to))

        val (duplicateWeights, duplicateFats) = SampleDataDefects.duplicateReadingRows(today)
        assertEquals(4, duplicateWeights.size)
        assertEquals(4, duplicateFats.size)
        // Two same-day rows (identical value, different ids) + a near-duplicate pair.
        assertEquals(2, duplicateWeights.map { dayOf(it.date) }.distinct().size)
        val nearIds = setOf(
            SampleDataDefects.seedId("dup-weight-near-0"),
            SampleDataDefects.seedId("dup-weight-near-1"),
        )
        val near = duplicateWeights.filter { it.id in nearIds }
        assertEquals(2, near.size)
        assertEquals(5L, near.maxOf { it.date }.epochSecond - near.minOf { it.date }.epochSecond)
        assertTrue(abs(near.first().weightKg - near.last().weightKg) < 0.005)

        val (absurdWeights, absurdFats) = SampleDataDefects.absurdReadingRows(today)
        assertEquals(listOf(0.0, 999.0), absurdWeights.map(WeightEntry::weightKg).sorted())
        assertEquals(listOf(0.0, 0.02, 0.75), absurdFats.map { it.bodyFatFraction }.sorted())

        val activity = SampleDataDefects.activityDefectDays(today)
        assertEquals(3, activity.size)
        assertEquals(2, activity.count { it.totalCalories == null })
        assertEquals(1, activity.count { it.steps == 0L && it.activeCalories == 0 })
        assertEquals(activity.size, activity.map { it.date }.toSet().size)
    }

    @Test
    fun trackerDefectsCoverTheExtremes() {
        val nicotine = SampleDataDefects.nicotineDefectRows(today)
        assertEquals(listOf(0, 1), nicotine.map { it.count }.sorted())
        assertEquals(30.0, nicotine.maxOf { it.mg ?: 0.0 }, 0.001)
        val linked = SampleDataDefects.seedId("some-food-row")
        val caffeine = SampleDataDefects.caffeineDefectRow(today, linked)
        assertEquals(SampleDataGenerators.SEED_CAFFEINE_PRESET_ID, caffeine.kind)
        assertEquals(linked, caffeine.linkedFoodEntryId)
        // A null link (no dairy in the diary) must be tolerated too.
        assertNull(SampleDataDefects.caffeineDefectRow(today, null).linkedFoodEntryId)
        assertEquals(listOf(0, 5_000), SampleDataDefects.waterDefectRows(today).map { it.milliliters }.sorted())
    }

    @Test
    fun fixtureIdsAreDeterministicAndCoverEveryGroup() {
        val first = SampleDataDefects.fixtureIds(today)
        val second = SampleDataDefects.fixtureIds(today)
        assertEquals(first, second)
        assertFalse(first.isEmpty())
        assertEquals(first.size, first.map { it.toString() }.toSet().size)
        listOf(
            "gap-all-zero-rice",
            "gap-favorite-zero",
            "stale-micros",
            "stale-micros-favorite",
            "legacy-hostile-text",
            "legacy-favorite-collision",
            "legacy-empty-recipe",
            "legacy-caffeine-kind",
            "future-row",
            "shared-key-0",
            "dup-weight-0",
            "absurd-weight-max",
            "measure-all-null",
            "nicotine-zero-count",
            "caffeine-milk-defect",
            "water-max",
        ).forEach { slug ->
            assertTrue("missing fixture id for $slug", SampleDataDefects.seedId(slug) in first)
        }
        // A different `today` shifts only the date-derived note id; every
        // slug-derived id is stable.
        val noteId = SampleDataDefects.noteOnlyNote(today).id
        assertEquals(DailyNote.idFor(today.minusDays(12)), noteId)
        assertEquals(
            first - noteId,
            SampleDataDefects.fixtureIds(today.plusDays(3)) - SampleDataDefects.noteOnlyNote(today.plusDays(3)).id,
        )
    }
}
