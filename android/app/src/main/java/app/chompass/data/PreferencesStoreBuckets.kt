package app.chompass.data

import androidx.datastore.preferences.core.edit
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.FoodEntry
import app.chompass.models.WaterEntry
import app.chompass.models.WeightEntry
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.time.YearMonth
import java.time.ZoneId

/**
 * One-time migration of unbounded datasets out of the single DataStore proto
 * into JsonBucketStore month files (docs/local/PLAN_FILE_BUCKETS.md).
 *
 * Idempotent and crash-safe: driven by key presence, not a flag. Each dataset
 * is moved only while its legacy key still exists — the month files are
 * written first, then the key is dropped in one DataStore edit. A crash
 * mid-migration re-runs and overwrites identical file content; a device that
 * already migrated skips straight past (the key is gone). This also makes the
 * migration safe across incremental releases (water shipped before food).
 *
 * Must run BEFORE any impl that reads or writes the moved datasets, or the
 * legacy keys could be dropped with data still in them (reads would go to the
 * files, writes would go to the files, and the old blob would be stranded).
 */
internal suspend fun PreferencesStore.migrateBucketsToFilesIfNeeded() {
    // Pre-bucket-era food history lives in the single `foodEntries` blob; the
    // file migration must never run before the existing bucket migration, or
    // that history would be stranded in a key the file store never reads.
    migrateFoodEntriesToBucketsIfNeededImpl()

    val prefs = dataStore.data.first()

    val waterRaw = prefs[Keys.WATER_ENTRIES]
    if (waterRaw != null) {
        val entries = decodeListOrEmpty(waterRaw, WaterEntry.serializer())
        waterBucketStore.replaceAll(
            entries.groupBy { YearMonth.from(it.date.atZone(ZoneId.systemDefault())) }
        )
        dataStore.edit { it.remove(Keys.WATER_ENTRIES) }
    }

    val weightsRaw = prefs[Keys.WEIGHT_ENTRIES]
    if (weightsRaw != null) {
        val entries = decodeListOrEmpty(weightsRaw, WeightEntry.serializer())
        weightBucketStore.replaceAll(
            entries.groupBy { YearMonth.from(it.date.atZone(ZoneId.systemDefault())) }
        )
        dataStore.edit { it.remove(Keys.WEIGHT_ENTRIES) }
    }

    val bodyFatRaw = prefs[Keys.BODY_FAT_ENTRIES]
    if (bodyFatRaw != null) {
        val entries = decodeListOrEmpty(bodyFatRaw, BodyFatEntry.serializer())
        bodyFatBucketStore.replaceAll(
            entries.groupBy { YearMonth.from(it.date.atZone(ZoneId.systemDefault())) }
        )
        dataStore.edit { it.remove(Keys.BODY_FAT_ENTRIES) }
    }

    val measurementsRaw = prefs[Keys.BODY_MEASUREMENTS]
    if (measurementsRaw != null) {
        val entries = decodeListOrEmpty(measurementsRaw, BodyMeasurement.serializer())
        measurementBucketStore.replaceAll(
            entries.groupBy { YearMonth.from(it.date.atZone(ZoneId.systemDefault())) }
        )
        dataStore.edit { it.remove(Keys.BODY_MEASUREMENTS) }
    }

    val foodKeys = prefs.asMap().keys.filter { it.name.startsWith(FOOD_ENTRIES_BUCKET_PREFIX) }
    if (foodKeys.isNotEmpty()) {
        val byMonth = foodKeys.mapNotNull { key ->
            val month = key.name.removePrefix(FOOD_ENTRIES_BUCKET_PREFIX)
                .let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: return@mapNotNull null
            month to decodeListOrEmpty(prefs[key] as? String ?: return@mapNotNull null, FoodEntry.serializer())
        }.toMap()
        foodBucketStore.replaceAll(byMonth)
        dataStore.edit { it ->
            foodKeys.forEach { key -> it.remove(key) }
        }
    }
}

private fun <T> PreferencesStore.decodeListOrEmpty(raw: String, serializer: KSerializer<T>): List<T> =
    runCatching { json.decodeFromString(ListSerializer(serializer), raw) }.getOrNull().orEmpty()
