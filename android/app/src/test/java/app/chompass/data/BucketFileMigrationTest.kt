package app.chompass.data

import android.app.Application
import androidx.datastore.preferences.core.edit
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.FoodEntry
import app.chompass.models.MealType
import app.chompass.models.WaterEntry
import app.chompass.models.WeightEntry
import app.chompass.models.FoodSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * One-shot migration of unbounded datasets out of the DataStore proto into
 * JsonBucketStore month files — key-presence driven, idempotent, crash-safe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BucketFileMigrationTest {
    private fun sip(id: String, ts: String, ml: Int = 250) =
        WaterEntry(id = UUID.nameUUIDFromBytes(id.toByteArray()), date = Instant.parse(ts), milliliters = ml)

    @Test
    fun `water blob migrates to month files and the key is dropped`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val aug = sip("aug", "2026-08-01T10:00:00Z")
        val jul = sip("jul", "2026-07-05T10:00:00Z")
        prefs.dataStore.edit {
            it[Keys.WATER_ENTRIES] = prefs.json.encodeToString(ListSerializer(WaterEntry.serializer()), listOf(aug, jul))
        }

        prefs.migrateBucketsToFilesIfNeeded()

        assertEquals(listOf(jul, aug), prefs.waterBucketStore.readAll())
        assertEquals(listOf(aug), prefs.waterBucketStore.readMonth(YearMonth.of(2026, 8)))
        assertNull(prefs.dataStore.data.first()[Keys.WATER_ENTRIES])
        // The reactive facade now reads from the bucket files.
        assertEquals(listOf(jul, aug), prefs.waterEntries.first())
    }

    @Test
    fun `re-running the migration is a no-op once the key is gone`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val a = sip("a", "2026-08-01T10:00:00Z")
        prefs.dataStore.edit {
            it[Keys.WATER_ENTRIES] = prefs.json.encodeToString(ListSerializer(WaterEntry.serializer()), listOf(a))
        }
        prefs.migrateBucketsToFilesIfNeeded()
        prefs.migrateBucketsToFilesIfNeeded()

        assertEquals(listOf(a), prefs.waterBucketStore.readAll())
        assertEquals(listOf(a), prefs.waterEntries.first())
    }

    @Test
    fun `fresh install without a water blob migrates to nothing`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        prefs.dataStore.edit { it.remove(Keys.WATER_ENTRIES) }
        prefs.migrateBucketsToFilesIfNeeded()
        assertEquals(emptyList<WaterEntry>(), prefs.waterBucketStore.readAll())
        assertEquals(emptyList<WaterEntry>(), prefs.waterEntries.first())
    }

    @Test
    fun `weight body-fat and measurement blobs migrate to month files`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val w = WeightEntry(id = UUID.randomUUID(), date = Instant.parse("2026-08-03T08:00:00Z"), weightKg = 73.5)
        val bf = BodyFatEntry(id = UUID.randomUUID(), date = Instant.parse("2026-07-01T08:00:00Z"), bodyFatFraction = 0.18)
        val m = BodyMeasurement(id = UUID.randomUUID(), date = Instant.parse("2026-08-10T07:00:00Z"), waistCm = 85.0)
        prefs.dataStore.edit {
            it[Keys.WEIGHT_ENTRIES] = prefs.json.encodeToString(ListSerializer(WeightEntry.serializer()), listOf(w))
            it[Keys.BODY_FAT_ENTRIES] = prefs.json.encodeToString(ListSerializer(BodyFatEntry.serializer()), listOf(bf))
            it[Keys.BODY_MEASUREMENTS] = prefs.json.encodeToString(ListSerializer(BodyMeasurement.serializer()), listOf(m))
        }

        prefs.migrateBucketsToFilesIfNeeded()

        assertEquals(listOf(w), prefs.weightBucketStore.readAll())
        assertEquals(listOf(bf), prefs.bodyFatBucketStore.readAll())
        assertEquals(listOf(m), prefs.measurementBucketStore.readAll())
        val remaining = prefs.dataStore.data.first()
        assertNull(remaining[Keys.WEIGHT_ENTRIES])
        assertNull(remaining[Keys.BODY_FAT_ENTRIES])
        assertNull(remaining[Keys.BODY_MEASUREMENTS])
        // Facades read from the bucket files.
        assertEquals(listOf(w), prefs.weightEntries.first())
        assertEquals(listOf(bf), prefs.bodyFatEntries.first())
        assertEquals(listOf(m), prefs.bodyMeasurements.first())
    }

    @Test
    fun `month-scoped water write touches exactly one bucket file`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val aug = sip("aug", "2026-08-01T10:00:00Z")
        val jul = sip("jul", "2026-07-05T10:00:00Z")
        prefs.applyWaterBucketChanges(upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(aug)))
        prefs.applyWaterBucketChanges(upsertsByMonth = mapOf(YearMonth.of(2026, 7) to listOf(jul)))

        assertEquals(listOf(jul, aug), prefs.waterEntries.first())
        assertEquals(1, prefs.waterBucketStore.readMonth(YearMonth.of(2026, 8)).size)
        // Deleting the August sip leaves July intact and removes only that file.
        prefs.applyWaterBucketChanges(removalIdsByMonth = mapOf(YearMonth.of(2026, 8) to setOf(aug.id)))
        assertEquals(listOf(jul), prefs.waterEntries.first())
        assertEquals(emptyList<WaterEntry>(), prefs.waterBucketStore.readMonth(YearMonth.of(2026, 8)))
        assertEquals(listOf("2026-07"), prefs.waterBucketStore.monthsOnDisk().map { it.toString() })
    }

    @Test
    fun `food bucket keys migrate to month files and are dropped`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val aug = food("aug", "2026-08-01T12:00:00Z")
        val jul = food("jul", "2026-07-05T12:00:00Z")
        prefs.dataStore.edit {
            it[Keys.foodEntriesBucket(YearMonth.of(2026, 8))] =
                prefs.json.encodeToString(ListSerializer(FoodEntry.serializer()), listOf(aug))
            it[Keys.foodEntriesBucket(YearMonth.of(2026, 7))] =
                prefs.json.encodeToString(ListSerializer(FoodEntry.serializer()), listOf(jul))
        }

        prefs.migrateBucketsToFilesIfNeeded()

        assertEquals(listOf(jul, aug), prefs.foodBucketStore.readAll())
        val remaining = prefs.dataStore.data.first()
        assertNull(remaining[Keys.foodEntriesBucket(YearMonth.of(2026, 8))])
        assertNull(remaining[Keys.foodEntriesBucket(YearMonth.of(2026, 7))])
        // Facades read from the bucket files, month-scoped and full.
        assertEquals(listOf(aug), prefs.foodEntriesForMonth(YearMonth.of(2026, 8)).first())
        assertEquals(listOf(jul, aug), prefs.foodEntries.first())
    }

    @Test
    fun `food writes land in one month file and cross-month moves stay recoverable`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val aug = food("aug", "2026-08-01T12:00:00Z")
        prefs.applyFoodEntryBucketChanges(upsertsByMonth = mapOf(YearMonth.of(2026, 8) to listOf(aug)))

        assertEquals(listOf(aug), prefs.foodEntriesForMonth(YearMonth.of(2026, 8)).first())
        assertEquals(listOf("2026-08"), prefs.foodBucketStore.monthsOnDisk().map { it.toString() })

        // Cross-month move: new month upsert + old month removal in one call.
        val moved = aug.copy(timestamp = Instant.parse("2026-07-31T23:00:00Z"))
        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = mapOf(YearMonth.of(2026, 7) to listOf(moved)),
            removalIdsByMonth = mapOf(YearMonth.of(2026, 8) to setOf(aug.id)),
        )
        assertEquals(listOf(moved), prefs.foodEntries.first())
        assertEquals(emptyList<FoodEntry>(), prefs.foodEntriesForMonth(YearMonth.of(2026, 8)).first())

        // replaceAll (reseed path) wipes every file and rewrites from the new list.
        val fresh = food("fresh", "2026-09-01T12:00:00Z")
        prefs.replaceAllFoodEntries(listOf(fresh))
        assertEquals(listOf("2026-09"), prefs.foodBucketStore.monthsOnDisk().map { it.toString() })
        assertEquals(listOf(fresh), prefs.foodEntries.first())
    }

    private fun food(id: String, ts: String) = FoodEntry(
        id = UUID.nameUUIDFromBytes(id.toByteArray()),
        name = id,
        calories = 100,
        protein = 5.0,
        carbs = 10.0,
        fat = 2.0,
        timestamp = Instant.parse(ts),
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH.id,
    )
}
