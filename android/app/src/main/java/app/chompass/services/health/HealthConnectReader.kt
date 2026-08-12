package app.chompass.services.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MealType as HCMealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

internal class HealthConnectReader(
    private val client: () -> HealthConnectClient?,
    private val granted: suspend () -> Set<String>,
    private val stepsRead: String,
    private val exerciseRead: String,
    private val sleepRead: String,
    private val restingHrRead: String,
    private val hydrationRead: String,
    private val hasEnergyRead: suspend () -> Boolean,
) {
    suspend fun readWeights(from: Instant, to: Instant): List<ExternalWeight> {
        val c = client() ?: return emptyList()
        val out = mutableListOf<ExternalWeight>()
        var pageToken: String? = null
        // readRecords returns one page (default 1000); follow pageToken so a large
        // history isn't silently truncated to the first page.
        do {
            val response = runCatching {
                c.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(from, to),
                        pageToken = pageToken
                    )
                )
            }.getOrNull() ?: break
            response.records.forEach {
                out.add(
                    ExternalWeight(
                        time = it.time,
                        weightKg = it.weight.inKilograms,
                        clientRecordId = it.metadata.clientRecordId,
                        recordId = it.metadata.id
                    )
                )
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return out
    }

    suspend fun readBodyFats(from: Instant, to: Instant): List<ExternalBodyFat> {
        val c = client() ?: return emptyList()
        val out = mutableListOf<ExternalBodyFat>()
        var pageToken: String? = null
        do {
            val response = runCatching {
                c.readRecords(
                    ReadRecordsRequest(
                        recordType = BodyFatRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(from, to),
                        pageToken = pageToken
                    )
                )
            }.getOrNull() ?: break
            response.records.forEach {
                out.add(
                    ExternalBodyFat(
                        time = it.time,
                        // Convert HC's 0–100 back to our 0–1 fraction convention.
                        bodyFatFraction = it.percentage.value / 100.0,
                        clientRecordId = it.metadata.clientRecordId,
                        recordId = it.metadata.id
                    )
                )
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return out
    }

    /** All NutritionRecords in the range, mapped back to Fud AI's units (the exact
     *  inverse of writeNutrition). Powers the food-log restore after a reinstall
     *  or new phone, where Health Connect data survives but app storage doesn't.
     *  Returns null when any page read fails (rate limit, binder error) so the
     *  caller can leave its one-shot flag unset and retry, instead of treating a
     *  partial read as the complete history. */
    suspend fun readNutrition(from: Instant, to: Instant): List<ExternalNutrition>? {
        val c = client() ?: return null
        val out = mutableListOf<ExternalNutrition>()
        var pageToken: String? = null
        do {
            val response = runCatching {
                c.readRecords(
                    ReadRecordsRequest(
                        recordType = NutritionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(from, to),
                        pageToken = pageToken
                    )
                )
            }.getOrNull() ?: return null
            response.records.forEach { out.add(externalNutritionFrom(it)) }
            pageToken = response.pageToken
        } while (pageToken != null)
        return out
    }

    /** All HydrationRecords in the range — the inverse of writeHydration. Powers
     *  the one-shot water-log restore after a reinstall/new phone. Returns null on
     *  any page failure (rate limit, binder error) so the caller can leave its
     *  one-shot flag unset and retry, mirroring readNutrition. */
    suspend fun readHydration(from: Instant, to: Instant): List<ExternalHydration>? {
        val c = client() ?: return null
        val out = mutableListOf<ExternalHydration>()
        var pageToken: String? = null
        do {
            val response = runCatching {
                c.readRecords(
                    ReadRecordsRequest(
                        recordType = HydrationRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(from, to),
                        pageToken = pageToken
                    )
                )
            }.getOrNull() ?: return null
            response.records.forEach {
                out.add(
                    ExternalHydration(
                        time = it.startTime,
                        milliliters = it.volume.inMilliliters.roundToInt(),
                        clientRecordId = it.metadata.clientRecordId,
                        recordId = it.metadata.id
                    )
                )
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return out
    }

    /** Active + total kilocalories for a single calendar day (today or past). */
    suspend fun readDailyEnergy(date: LocalDate): DailyEnergyBurn? {
        val c = client() ?: return null
        if (!hasEnergyRead()) return null
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val result = runCatching {
            c.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
        }.getOrNull() ?: return null
        val active = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        val total = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.takeIf { it > 0.0 }
        if (active + (total ?: 0.0) <= 0.0) return null
        return DailyEnergyBurn(date = date, active = active, total = total)
    }

    suspend fun readRecentEnergySummary(days: Int = 14): HealthEnergySummary? {
        val c = client() ?: return null
        val requestedDays = maxOf(3, days)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val daily = mutableListOf<DailyEnergy>()

        for (offset in requestedDays downTo 1) {
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val result = runCatching {
                c.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                            TotalCaloriesBurnedRecord.ENERGY_TOTAL
                        ),
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                )
            }.getOrNull() ?: continue

            val active = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            val total = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.takeIf { it > 0.0 }
            if (active + (total ?: 0.0) <= 0.0) continue
            daily.add(DailyEnergy(active = active, total = total))
        }

        if (daily.size < 3) return null

        val activeAverage = daily.sumOf { it.active } / daily.size
        val totalValues = daily.mapNotNull { it.total }
        val totalAverage = totalValues.takeIf { it.isNotEmpty() }?.let { values -> values.sum() / values.size }
        val basalAverage = totalAverage?.let { maxOf(0.0, it - activeAverage) }
        return HealthEnergySummary(
            activeAverageCalories = activeAverage.roundToInt(),
            basalAverageCalories = basalAverage?.roundToInt(),
            totalAverageCalories = totalAverage?.roundToInt(),
            daysUsed = daily.size,
            requestedDays = requestedDays
        )
    }

    /**
     * Per-day steps + exercise minutes for the last [days] days, today included
     * (unlike the energy summary, partial "today" is exactly what an activity
     * card shows). Aggregates only the metrics whose read permission is granted;
     * days with no data are returned with zeros so the caller gets a full range.
     * Aggregated daily totals only — nothing is persisted (a per-record import
     * of high-frequency step data would bloat the DataStore JSON blobs).
     */
    suspend fun readDailyActivity(days: Int = 7): List<DailyActivity> {
        val c = client() ?: return emptyList()
        val g = granted()
        val metrics = buildSet {
            if (stepsRead in g) add(StepsRecord.COUNT_TOTAL)
            if (exerciseRead in g) add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
        }
        if (metrics.isEmpty()) return emptyList()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val out = mutableListOf<DailyActivity>()
        for (offset in (maxOf(1, days) - 1) downTo 0) {
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val result = runCatching {
                c.aggregate(
                    AggregateRequest(
                        metrics = metrics,
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                )
            }.getOrNull()
            out.add(
                DailyActivity(
                    date = date,
                    steps = result?.get(StepsRecord.COUNT_TOTAL) ?: 0L,
                    exerciseMinutes = result?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
                        ?.toMinutes()?.toInt() ?: 0
                )
            )
        }
        return out
    }

    /**
     * Per-day sleep minutes, resting heart rate and hydration for the last [days]
     * days, today included — the display counterpart to [readDailyActivity].
     * Aggregates only the metrics whose read permission is granted; a day with no
     * data for a metric returns null for it. Nothing is persisted.
     */
    suspend fun readDailyWellness(days: Int = 7): List<DailyWellness> {
        val c = client() ?: return emptyList()
        val g = granted()
        val wantSleep = sleepRead in g
        val wantHr = restingHrRead in g
        val wantHydration = hydrationRead in g
        val metrics = buildSet {
            if (wantSleep) add(SleepSessionRecord.SLEEP_DURATION_TOTAL)
            if (wantHr) add(RestingHeartRateRecord.BPM_AVG)
            if (wantHydration) add(HydrationRecord.VOLUME_TOTAL)
        }
        if (metrics.isEmpty()) return emptyList()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val out = mutableListOf<DailyWellness>()
        for (offset in (maxOf(1, days) - 1) downTo 0) {
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val result = runCatching {
                c.aggregate(
                    AggregateRequest(
                        metrics = metrics,
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                )
            }.getOrNull()
            val sleepMinutes = result?.get(SleepSessionRecord.SLEEP_DURATION_TOTAL)?.toMinutes()?.toInt()
            val restingHr = result?.get(RestingHeartRateRecord.BPM_AVG)
            val hydrationMl = result?.get(HydrationRecord.VOLUME_TOTAL)?.inMilliliters
            out.add(
                DailyWellness(
                    date = date,
                    sleepMinutes = sleepMinutes?.takeIf { it > 0 },
                    restingHeartRateBpm = restingHr?.takeIf { it > 0 },
                    hydrationMl = hydrationMl?.takeIf { it > 0 }
                )
            )
        }
        return out
    }

    /** Opaque token used to fetch incremental changes. Call once, persist, pass back later.
     *  Now watches both Weight and BodyFat records — a single token reflects upserts of either. */
    suspend fun getChangesToken(
        recordTypes: Set<kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>> =
            setOf(WeightRecord::class, BodyFatRecord::class)
    ): String? {
        val c = client() ?: return null
        if (recordTypes.isEmpty()) return null
        return runCatching {
            c.getChangesToken(
                androidx.health.connect.client.request.ChangesTokenRequest(recordTypes = recordTypes)
            )
        }.getOrNull()
    }

    /** Returns observed external weight upserts since [sinceToken] plus the next token to use.
     *  Returns null when the token is expired or invalid so the caller re-backfills from scratch
     *  (an expired token is a *successful* response with changesTokenExpired=true, not an exception). */
    suspend fun consumeWeightChanges(sinceToken: String): Pair<List<ExternalWeight>, String?>? {
        val c = client() ?: return null
        val results = mutableListOf<ExternalWeight>()
        var token = sinceToken
        // getChanges returns one page; drain hasMore so we don't truncate a large backlog.
        while (true) {
            val changes = runCatching { c.getChanges(token) }.getOrNull() ?: return null
            if (changes.changesTokenExpired) return null
            changes.changes.filterIsInstance<UpsertionChange>().forEach { change ->
                val rec = change.record as? WeightRecord ?: return@forEach
                // Skip samples we wrote ourselves (prefix matches our tag).
                val cid = rec.metadata.clientRecordId
                if (cid != null && cid.startsWith(HealthConnectManager.CLIENT_PREFIX)) return@forEach
                results.add(
                    ExternalWeight(
                        time = rec.time,
                        weightKg = rec.weight.inKilograms,
                        clientRecordId = cid,
                        recordId = rec.metadata.id
                    )
                )
            }
            token = changes.nextChangesToken
            if (!changes.hasMore) break
        }
        return results to token
    }

    /** Sibling of [consumeWeightChanges] for BodyFat records. The combined
     *  changes-token watches both record types, so callers should drain both
     *  consumers using the SAME nextChangesToken returned by either call.
     *  We expose them as separate functions only to keep each result strongly typed. */
    suspend fun consumeBodyFatChanges(sinceToken: String): Pair<List<ExternalBodyFat>, String?>? {
        val c = client() ?: return null
        val results = mutableListOf<ExternalBodyFat>()
        var token = sinceToken
        while (true) {
            val changes = runCatching { c.getChanges(token) }.getOrNull() ?: return null
            if (changes.changesTokenExpired) return null
            changes.changes.filterIsInstance<UpsertionChange>().forEach { change ->
                val rec = change.record as? BodyFatRecord ?: return@forEach
                val cid = rec.metadata.clientRecordId
                if (cid != null && cid.startsWith(HealthConnectManager.CLIENT_PREFIX)) return@forEach
                results.add(
                    ExternalBodyFat(
                        time = rec.time,
                        bodyFatFraction = rec.percentage.value / 100.0,
                        clientRecordId = cid,
                        recordId = rec.metadata.id
                    )
                )
            }
            token = changes.nextChangesToken
            if (!changes.hasMore) break
        }
        return results to token
    }

    /** Sibling of [consumeWeightChanges] for NutritionRecords — powers the live
     *  import of meals other apps log to Health Connect. Own records (fudai_ tag)
     *  are skipped here so app-written meals don't echo back in. */
    suspend fun consumeNutritionChanges(sinceToken: String): Pair<List<ExternalNutrition>, String?>? {
        val c = client() ?: return null
        val results = mutableListOf<ExternalNutrition>()
        var token = sinceToken
        while (true) {
            val changes = runCatching { c.getChanges(token) }.getOrNull() ?: return null
            if (changes.changesTokenExpired) return null
            changes.changes.filterIsInstance<UpsertionChange>().forEach { change ->
                val rec = change.record as? NutritionRecord ?: return@forEach
                val cid = rec.metadata.clientRecordId
                if (cid != null && cid.startsWith(HealthConnectManager.CLIENT_PREFIX)) return@forEach
                results.add(externalNutritionFrom(rec))
            }
            token = changes.nextChangesToken
            if (!changes.hasMore) break
        }
        return results to token
    }

    private fun externalNutritionFrom(it: NutritionRecord): ExternalNutrition = ExternalNutrition(
        time = it.startTime,
        name = it.name,
        mealType = mealTypeFrom(it.mealType),
        calories = it.energy?.inKilocalories,
        protein = it.protein?.inGrams,
        carbs = it.totalCarbohydrate?.inGrams,
        fat = it.totalFat?.inGrams,
        fiber = it.dietaryFiber?.inGrams,
        sugar = it.sugar?.inGrams,
        saturatedFat = it.saturatedFat?.inGrams,
        monounsaturatedFat = it.monounsaturatedFat?.inGrams,
        polyunsaturatedFat = it.polyunsaturatedFat?.inGrams,
        transFat = it.transFat?.inGrams,
        cholesterol = it.cholesterol?.inMilligrams,
        sodium = it.sodium?.inMilligrams,
        potassium = it.potassium?.inMilligrams,
        calcium = it.calcium?.inMilligrams,
        iron = it.iron?.inMilligrams,
        magnesium = it.magnesium?.inMilligrams,
        zinc = it.zinc?.inMilligrams,
        vitaminA = it.vitaminA?.inMicrograms,
        vitaminC = it.vitaminC?.inMilligrams,
        vitaminD = it.vitaminD?.inMicrograms,
        vitaminB12 = it.vitaminB12?.inMicrograms,
        vitaminE = it.vitaminE?.inMilligrams,
        vitaminK = it.vitaminK?.inMicrograms,
        folate = it.folate?.inMicrograms,
        clientRecordId = it.metadata.clientRecordId,
        recordId = it.metadata.id
    )

    private fun mealTypeFrom(hcMealType: Int): app.chompass.models.MealType = when (hcMealType) {
        HCMealType.MEAL_TYPE_BREAKFAST -> app.chompass.models.MealType.BREAKFAST
        HCMealType.MEAL_TYPE_LUNCH -> app.chompass.models.MealType.LUNCH
        HCMealType.MEAL_TYPE_DINNER -> app.chompass.models.MealType.DINNER
        HCMealType.MEAL_TYPE_SNACK -> app.chompass.models.MealType.SNACK
        else -> app.chompass.models.MealType.OTHER
    }
}
