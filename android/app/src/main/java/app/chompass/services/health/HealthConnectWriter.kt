package app.chompass.services.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.MealType as HCMealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import app.chompass.models.BodyFatEntry
import app.chompass.models.FoodEntry
import app.chompass.models.WeightEntry
import java.time.Instant
import java.util.UUID

internal class HealthConnectWriter(
    private val client: () -> HealthConnectClient?
) {

    suspend fun writeWeight(entry: WeightEntry): Boolean {
        val c = client() ?: return false
        val record = WeightRecord(
            time = entry.date,
            zoneOffset = null,
            weight = Mass.kilograms(entry.weightKg),
            metadata = Metadata.manualEntry(clientRecordId = tag(entry.id))
        )
        return runCatching { c.insertRecords(listOf(record)) }.isSuccess
    }

    suspend fun deleteWeight(entryId: UUID): Boolean {
        val c = client() ?: return false
        return runCatching {
            c.deleteRecords(
                recordType = WeightRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf(tag(entryId))
            )
        }.isSuccess
    }

    suspend fun writeBodyFat(entry: BodyFatEntry): Boolean {
        val c = client() ?: return false
        val record = BodyFatRecord(
            time = entry.date,
            zoneOffset = null,
            // BodyFatRecord wants 0–100 percent, not a fraction.
            percentage = Percentage(entry.bodyFatFraction * 100),
            metadata = Metadata.manualEntry(clientRecordId = tag(entry.id))
        )
        return runCatching { c.insertRecords(listOf(record)) }.isSuccess
    }

    suspend fun deleteBodyFat(entryId: UUID): Boolean {
        val c = client() ?: return false
        return runCatching {
            c.deleteRecords(
                recordType = BodyFatRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf(tag(entryId))
            )
        }.isSuccess
    }

    /** Push the user's height as a single Health Connect record. Delete-then-write
     *  under a fixed clientRecordId so re-saving a corrected height replaces the
     *  record instead of stacking duplicates. Body-circumference sites have no HC
     *  record type, so height is the only body-measurement we can mirror. */
    suspend fun writeHeight(heightCm: Double): Boolean {
        val c = client() ?: return false
        if (heightCm <= 0) return false
        val clientId = "${HealthConnectManager.CLIENT_PREFIX}height"
        runCatching {
            c.deleteRecords(
                recordType = HeightRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf(clientId)
            )
        }
        val record = HeightRecord(
            time = Instant.now(),
            zoneOffset = null,
            height = Length.meters(heightCm / 100.0),
            metadata = Metadata.manualEntry(clientRecordId = clientId)
        )
        return runCatching { c.insertRecords(listOf(record)) }.isSuccess
    }

    suspend fun writeNutrition(entry: FoodEntry): Boolean {
        val c = client() ?: return false
        val start = entry.timestamp
        if (start.isAfter(Instant.now())) return false
        // Nutrition records need a non-zero duration or Health Connect rejects them; use 1 minute.
        val end = start.plusSeconds(60)
        return runCatching {
            val record = NutritionRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = null,
                endZoneOffset = null,
                name = entry.name,
                mealType = mealTypeFor(entry.mealType),
                energy = Energy.kilocalories(entry.calories.toDouble()),
                protein = Mass.grams(entry.protein),
                totalCarbohydrate = Mass.grams(entry.carbs),
                totalFat = Mass.grams(entry.fat),
                dietaryFiber = entry.fiber?.let { Mass.grams(it) },
                sugar = entry.sugar?.let { Mass.grams(it) },
                saturatedFat = entry.saturatedFat?.let { Mass.grams(it) },
                monounsaturatedFat = entry.monounsaturatedFat?.let { Mass.grams(it) },
                polyunsaturatedFat = entry.polyunsaturatedFat?.let { Mass.grams(it) },
                transFat = entry.transFat?.let { Mass.grams(it) },
                cholesterol = entry.cholesterol?.let { Mass.milligrams(it) },
                sodium = entry.sodium?.let { Mass.milligrams(it) },
                potassium = entry.potassium?.let { Mass.milligrams(it) },
                calcium = entry.calcium?.let { Mass.milligrams(it) },
                iron = entry.iron?.let { Mass.milligrams(it) },
                magnesium = entry.magnesium?.let { Mass.milligrams(it) },
                zinc = entry.zinc?.let { Mass.milligrams(it) },
                vitaminA = entry.vitaminA?.let { Mass.micrograms(it) },
                vitaminC = entry.vitaminC?.let { Mass.milligrams(it) },
                vitaminD = entry.vitaminD?.let { Mass.micrograms(it) },
                vitaminB12 = entry.vitaminB12?.let { Mass.micrograms(it) },
                vitaminE = entry.vitaminE?.let { Mass.milligrams(it) },
                vitaminK = entry.vitaminK?.let { Mass.micrograms(it) },
                folate = entry.folate?.let { Mass.micrograms(it) },
                metadata = Metadata.manualEntry(clientRecordId = tag(entry.id))
            )
            c.insertRecords(listOf(record))
        }.isSuccess
    }

    suspend fun updateNutrition(entry: FoodEntry): Boolean {
        // Health Connect doesn't allow true updates across clientRecordIds; delete-then-write
        // preserves the UUID linkage.
        deleteNutrition(entry.id)
        return writeNutrition(entry)
    }

    suspend fun deleteNutrition(entryId: UUID): Boolean {
        val c = client() ?: return false
        return runCatching {
            c.deleteRecords(
                recordType = NutritionRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf(tag(entryId))
            )
        }.isSuccess
    }

    private fun tag(id: UUID): String = "${HealthConnectManager.CLIENT_PREFIX}${id}"

    private fun mealTypeFor(meal: app.chompass.models.MealType): Int = when (meal) {
        app.chompass.models.MealType.BREAKFAST -> HCMealType.MEAL_TYPE_BREAKFAST
        app.chompass.models.MealType.LUNCH -> HCMealType.MEAL_TYPE_LUNCH
        app.chompass.models.MealType.DINNER -> HCMealType.MEAL_TYPE_DINNER
        app.chompass.models.MealType.SNACK -> HCMealType.MEAL_TYPE_SNACK
        app.chompass.models.MealType.OTHER -> HCMealType.MEAL_TYPE_UNKNOWN
    }
}
