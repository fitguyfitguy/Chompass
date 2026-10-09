package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * Batched content refresh for existing rows (AI fill-missing apply/undo):
 * entries in different months land in their own buckets in one call, filled
 * values read back, and untouched rows stay byte-identical.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FoodRepositoryUpdateEntriesTest {
    private fun prefs() = PreferencesStore(RuntimeEnvironment.getApplication())

    private fun entry(
        name: String,
        id: UUID = UUID.randomUUID(),
        timestamp: Instant,
        calories: Int = 100,
    ) = FoodEntry(
        id = id,
        name = name,
        calories = calories,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        timestamp = timestamp,
        source = FoodSource.MANUAL,
    )

    @Before
    fun setUp() = runBlocking {
        prefs().replaceAllFoodEntries(emptyList())
    }

    @Test
    fun batchUpdateAcrossMonths_updatesContent_keepsUntouchedByteIdentical() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val september = entry("Oats", timestamp = Instant.parse("2026-09-10T12:00:00Z"), calories = 0)
        val october = entry("Chicken", timestamp = Instant.parse("2026-10-10T12:00:00Z"), calories = 0)
        val untouched = entry("Yogurt", timestamp = Instant.parse("2026-10-11T12:00:00Z"))
        repo.addEntries(listOf(september, october, untouched))

        val filledSeptember = september.copy(calories = 250, protein = 12.0)
        val filledOctober = october.copy(calories = 640, protein = 42.0, fat = 24.0)
        repo.updateEntries(listOf(filledSeptember, filledOctober))

        val readSeptember = prefs.foodEntriesForMonth(YearMonth.of(2026, 9)).first()
        val readOctober = prefs.foodEntriesForMonth(YearMonth.of(2026, 10)).first()

        assertEquals(listOf(filledSeptember), readSeptember)
        // October keeps its untouched row byte-identical, same month bucket.
        assertEquals(listOf(filledOctober, untouched), readOctober.sortedBy { it.timestamp })
    }

    @Test
    fun emptyList_isNoOp() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val only = entry("Oats", timestamp = Instant.parse("2026-09-10T12:00:00Z"))
        repo.addEntries(listOf(only))

        repo.updateEntries(emptyList())

        assertEquals(listOf(only), prefs.foodEntriesForMonth(YearMonth.of(2026, 9)).first())
    }
}
