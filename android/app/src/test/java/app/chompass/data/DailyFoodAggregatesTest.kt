package app.chompass.data

import android.app.Application
import androidx.datastore.preferences.core.edit
import app.chompass.models.DailyFoodTotals
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

/**
 * Daily food aggregates (flippidity C.1): the per-day totals cache Progress
 * reads instead of the year of FoodEntry rows. Derived from the food month
 * files on every write; a cache, never a source of truth.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DailyFoodAggregatesTest {
    private fun food(
        id: String,
        ts: String,
        calories: Int = 100,
        protein: Double = 10.0,
        carbs: Double = 20.0,
        fat: Double = 5.0,
        fiber: Double? = null,
        sugar: Double? = null,
        sodium: Double? = null,
        iron: Double? = null,
        saturatedFat: Double? = null,
        vitaminC: Double? = null,
        caffeine: Double? = null,
    ) = FoodEntry(
        id = UUID.nameUUIDFromBytes(id.toByteArray()),
        name = id,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        sugar = sugar,
        sodium = sodium,
        iron = iron,
        saturatedFat = saturatedFat,
        vitaminC = vitaminC,
        caffeine = caffeine,
        timestamp = Instant.parse(ts),
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH.id,
    )

    private fun totals(month: YearMonth, prefs: PreferencesStore): List<DailyFoodTotals> =
        runBlocking { prefs.dailyFoodTotalsForMonths(listOf(month)).first() }

    @Test
    fun `food write maintains the aggregate rows for the touched month`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = mapOf(
                YearMonth.of(2026, 8) to listOf(
                    food("a", "2026-08-01T12:00:00Z"),
                    food("b", "2026-08-01T18:00:00Z", calories = 300, protein = 30.0),
                    food("c", "2026-08-03T12:00:00Z", calories = 200, fat = 9.0),
                )
            )
        )

        val rows = totals(YearMonth.of(2026, 8), prefs)
        assertEquals(listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3)), rows.map { it.date })
        assertEquals(DailyFoodTotals(LocalDate.of(2026, 8, 1), 400, 40.0, 40.0, 10.0), rows[0])
        assertEquals(DailyFoodTotals(LocalDate.of(2026, 8, 3), 200, 10.0, 20.0, 9.0), rows[1])
    }

    @Test
    fun `aggregate sums match the old per-entry day grouping exactly`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val entries = listOf(
            food("a", "2026-07-04T08:00:00Z", calories = 111, protein = 1.1, carbs = 2.2, fat = 3.3),
            food("b", "2026-07-04T20:00:00Z", calories = 222, protein = 4.4, carbs = 5.5, fat = 6.6),
            food("c", "2026-07-05T09:00:00Z", calories = 333, protein = 7.7, carbs = 8.8, fat = 9.9),
        )
        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = mapOf(YearMonth.of(2026, 7) to entries)
        )

        // Manual per-day sums in entry order — same left-fold the old
        // groupByLocalDateInRange performed, so doubles are bit-identical.
        val expected = entries
            .groupBy { it.timestamp.atZone(ZoneOffset.UTC).toLocalDate() }
            .map { (day, dayEntries) ->
                DailyFoodTotals(
                    date = day,
                    calories = dayEntries.sumOf { it.calories },
                    protein = dayEntries.sumOf { it.protein },
                    carbs = dayEntries.sumOf { it.carbs },
                    fat = dayEntries.sumOf { it.fat },
                )
            }
        assertEquals(expected, totals(YearMonth.of(2026, 7), prefs))
    }

    @Test
    fun `a zero-calorie day still gets a row`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = mapOf(
                YearMonth.of(2026, 8) to listOf(food("z", "2026-08-05T12:00:00Z", calories = 0, protein = 0.0, carbs = 0.0, fat = 0.0))
            )
        )
        // The row must survive so Progress' macro-average day count matches the
        // old per-entry grouping (a 0-kcal log still counts as a logged day).
        assertEquals(
            listOf(LocalDate.of(2026, 8, 5)),
            totals(YearMonth.of(2026, 8), prefs).map { it.date },
        )
    }

    @Test
    fun `deleting the last entry of a day drops its stale row`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val aug = food("aug", "2026-08-01T12:00:00Z")
        val jul = food("jul", "2026-07-05T12:00:00Z")
        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = mapOf(
                YearMonth.of(2026, 8) to listOf(aug),
                YearMonth.of(2026, 7) to listOf(jul),
            )
        )
        prefs.applyFoodEntryBucketChanges(
            removalIdsByMonth = mapOf(YearMonth.of(2026, 8) to setOf(aug.id))
        )

        assertEquals(emptyList<DailyFoodTotals>(), totals(YearMonth.of(2026, 8), prefs))
        assertEquals(listOf(LocalDate.of(2026, 7, 5)), totals(YearMonth.of(2026, 7), prefs).map { it.date })
        // A phantom zero day would skew Progress' macro-average day count.
        assertEquals(emptyList<DailyFoodTotals>(), totals(YearMonth.of(2026, 8), prefs))
    }

    @Test
    fun `editing an entry updates its day totals`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val aug = food("aug", "2026-08-01T12:00:00Z", calories = 100)
        prefs.applyFoodEntryBucketChanges(upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(aug)))
        assertEquals(100, totals(YearMonth.of(2026, 8), prefs).single().calories)

        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(aug.copy(calories = 250)))
        )
        assertEquals(250, totals(YearMonth.of(2026, 8), prefs).single().calories)
    }

    @Test
    fun `cross-month move updates both months`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val aug = food("aug", "2026-08-01T12:00:00Z")
        prefs.applyFoodEntryBucketChanges(upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(aug)))

        // 09:00Z keeps July 31 in every plausible test zone — the point is the
        // cross-month mechanics, not the exact hour.
        val moved = aug.copy(timestamp = Instant.parse("2026-07-31T09:00:00Z"))
        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = mapOf(YearMonth.of(2026, 7) to listOf(moved)),
            removalIdsByMonth = mapOf(YearMonth.of(2026, 8) to setOf(aug.id)),
        )
        assertEquals(emptyList<DailyFoodTotals>(), totals(YearMonth.of(2026, 8), prefs))
        assertEquals(listOf(LocalDate.of(2026, 7, 31)), totals(YearMonth.of(2026, 7), prefs).map { it.date })
    }

    @Test
    fun `replaceAll rebuilds every month and clear empties the cache`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(food("a", "2026-08-01T12:00:00Z")))
        )

        val fresh = listOf(
            food("f1", "2026-09-01T12:00:00Z"),
            food("f2", "2026-07-01T12:00:00Z", calories = 500),
        )
        prefs.replaceAllFoodEntries(fresh)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 1)),
            totals(YearMonth.of(2026, 9), prefs).map { it.date },
        )
        assertEquals(
            listOf(LocalDate.of(2026, 7, 1)),
            totals(YearMonth.of(2026, 7), prefs).map { it.date },
        )
        assertEquals(emptyList<DailyFoodTotals>(), totals(YearMonth.of(2026, 8), prefs))

        prefs.replaceAllFoodEntries(emptyList())
        assertEquals(emptyList<DailyFoodTotals>(), totals(YearMonth.of(2026, 9), prefs))
        assertEquals(emptyList<DailyFoodTotals>(), totals(YearMonth.of(2026, 7), prefs))
    }

    @Test
    fun `migration builds aggregates from existing food files once`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        // Food files present but the aggregate cache empty — the upgrade shape
        // from a pre-aggregate build. Write the food files directly, bypassing
        // the write-through impl, to simulate a device that already has data.
        prefs.foodBucketStore.replaceAll(
            mapOf(
                YearMonth.of(2026, 8) to listOf(food("a", "2026-08-01T12:00:00Z")),
                YearMonth.of(2026, 7) to listOf(food("b", "2026-07-05T12:00:00Z", calories = 250)),
            )
        )
        assertEquals(emptyList<YearMonth>(), prefs.foodAggregateBucketStore.monthsOnDisk())

        prefs.migrateBucketsToFilesIfNeeded()

        assertEquals(listOf(LocalDate.of(2026, 8, 1)), totals(YearMonth.of(2026, 8), prefs).map { it.date })
        assertEquals(250, totals(YearMonth.of(2026, 7), prefs).single().calories)
        // Idempotent: re-running must not duplicate or drop rows.
        prefs.migrateBucketsToFilesIfNeeded()
        assertEquals(1, totals(YearMonth.of(2026, 8), prefs).size)
        assertEquals(1, totals(YearMonth.of(2026, 7), prefs).size)
    }

    @Test
    fun `dailyTotalsBetween filters to the date window`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = mapOf(
                YearMonth.of(2026, 8) to listOf(
                    food("a", "2026-08-01T12:00:00Z"),
                    food("b", "2026-08-19T12:00:00Z"),
                )
            )
        )
        val repo = FoodRepository(prefs)
        val window = repo.dailyTotalsBetween(
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 8, 31),
        ).first()
        assertEquals(listOf(LocalDate.of(2026, 8, 19)), window.map { it.date })
    }

    @Test
    fun `aggregateFoodEntriesByDay is a pure per-day fold`() {
        val entries = listOf(
            food("a", "2026-08-01T12:00:00Z"),
            food("b", "2026-08-01T18:00:00Z", calories = 300),
            food("c", "2026-08-02T09:00:00Z"),
        )
        val rows = aggregateFoodEntriesByDay(entries, ZoneOffset.UTC)
        assertEquals(2, rows.size)
        assertEquals(DailyFoodTotals(LocalDate.of(2026, 8, 1), 400, 20.0, 40.0, 10.0), rows[0])
        assertTrue(rows[0].id != rows[1].id)
    }

    @Test
    fun `aggregateFoodEntriesByDay sums fiber sugar sodium`() {
        val entries = listOf(
            food("a", "2026-08-01T12:00:00Z", fiber = 4.0, sugar = 8.0, sodium = 200.0),
            food("b", "2026-08-01T18:00:00Z", calories = 300, fiber = 6.0, sugar = 2.0, sodium = 100.0),
        )
        val row = aggregateFoodEntriesByDay(entries, ZoneOffset.UTC).single()
        assertEquals(10.0, row.fiber, 0.01)
        assertEquals(10.0, row.sugar, 0.01)
        assertEquals(300.0, row.sodium, 0.01)
    }

    @Test
    fun `aggregateFoodEntriesByDay sums the broadened nutrient set`() {
        val entries = listOf(
            food(
                "a", "2026-08-01T12:00:00Z",
                fiber = 4.0, sugar = 8.0, sodium = 200.0,
                iron = 2.0, saturatedFat = 5.0, vitaminC = 30.0, caffeine = 80.0,
            ),
            food(
                "b", "2026-08-01T18:00:00Z", calories = 300,
                fiber = 6.0, sugar = 2.0, sodium = 100.0,
                iron = 3.0, saturatedFat = 7.0, vitaminC = 10.0, caffeine = 40.0,
            ),
        )
        val row = aggregateFoodEntriesByDay(entries, ZoneOffset.UTC).single()
        assertEquals(5.0, row.iron, 0.01)
        assertEquals(12.0, row.saturatedFat, 0.01)
        assertEquals(40.0, row.vitaminC, 0.01)
        assertEquals(120.0, row.caffeine, 0.01)
    }

    @Test
    fun `broadened sums survive write and replaceAll round trips`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val aug = food(
            "aug", "2026-08-01T12:00:00Z", calories = 300,
            iron = 6.0, saturatedFat = 9.0, vitaminC = 25.0, caffeine = 95.0,
        )
        prefs.applyFoodEntryBucketChanges(upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(aug)))

        var row = totals(YearMonth.of(2026, 8), prefs).single()
        assertEquals(6.0, row.iron, 0.01)
        assertEquals(9.0, row.saturatedFat, 0.01)
        assertEquals(25.0, row.vitaminC, 0.01)
        assertEquals(95.0, row.caffeine, 0.01)

        prefs.replaceAllFoodEntries(listOf(aug.copy(id = UUID.nameUUIDFromBytes("n".toByteArray()))))
        row = totals(YearMonth.of(2026, 8), prefs).single()
        assertEquals(6.0, row.iron, 0.01)
        assertEquals(95.0, row.caffeine, 0.01)
    }

    @Test
    fun `schema 2 aggregate cache rebuilds to the broadened shape without a food write`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        // Simulate the on-device upgrade shape: the food month file exists and
        // the aggregate cache holds old-schema rows (schema 2 — fiber/sugar/
        // sodium only, iron and friends absent). Written directly, bypassing
        val food = food("a", "2026-08-01T12:00:00Z", calories = 300, fiber = 4.0, iron = 6.0)
        prefs.foodBucketStore.replaceAll(mapOf(YearMonth.of(2026, 8) to listOf(food)))
        prefs.foodAggregateBucketStore.replaceAll(
            mapOf(
                YearMonth.of(2026, 8) to listOf(
                    DailyFoodTotals(
                        date = LocalDate.of(2026, 8, 1),
                        calories = 300,
                        protein = 10.0, carbs = 20.0, fat = 5.0,
                        fiber = 4.0, sugar = 8.0, sodium = 200.0,
                    )
                )
            )
        )
        prefs.dataStore.edit { it[Keys.FOOD_AGGREGATES_SCHEMA] = Keys.FOOD_AGGREGATES_SCHEMA_MICROS }

        // First read after the update: the schema gate rebuilds the month
        // from the food file — iron goes 0 → 6 with no food write in between.
        val row = totals(YearMonth.of(2026, 8), prefs).single()
        assertEquals(6.0, row.iron, 0.01)
        assertEquals(4.0, row.fiber, 0.01)
        // One-time: the flag now reads the latest schema, so a second read
        // serves the rebuilt cache without another rebuild pass.
        prefs.migrateBucketsToFilesIfNeeded()
        assertEquals(6.0, totals(YearMonth.of(2026, 8), prefs).single().iron, 0.01)
    }
}
