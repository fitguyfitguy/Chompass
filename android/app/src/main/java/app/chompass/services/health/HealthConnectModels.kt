package app.chompass.services.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal data class DailyEnergy(
    val active: Double,
    val total: Double?
)

data class HealthCapabilities(
    val weightRead: Boolean,
    val weightWrite: Boolean,
    val bodyFatRead: Boolean,
    val bodyFatWrite: Boolean,
    val nutritionRead: Boolean,
    val nutritionWrite: Boolean,
    val energyRead: Boolean,
    val stepsRead: Boolean,
    val exerciseRead: Boolean,
    val heightWrite: Boolean,
    val sleepRead: Boolean,
    val restingHrRead: Boolean,
    val hydrationRead: Boolean
)

/** Per-day energy burn from Health Connect (not persisted). */
data class DailyEnergyBurn(
    val date: LocalDate,
    val active: Double,
    val total: Double?,
)

/** One day of aggregated Health Connect activity — steps plus exercise-session
 *  minutes. Daily totals only; individual records are never persisted. */
data class DailyActivity(
    val date: LocalDate,
    val steps: Long,
    val exerciseMinutes: Int
)

/** One day of aggregated Health Connect wellness signals — sleep minutes, resting
 *  heart rate and hydration. Each is null when unavailable/ungranted for that day.
 *  Display-only; nothing is persisted. */
data class DailyWellness(
    val date: LocalDate,
    val sleepMinutes: Int?,
    val restingHeartRateBpm: Long?,
    val hydrationMl: Double?
)

/** A NutritionRecord read back from Health Connect in Fud AI's own units —
 *  kcal for energy, grams/milligrams/micrograms per nutrient, matching
 *  [HealthConnectManager.writeNutrition]. */
data class ExternalNutrition(
    val time: Instant,
    val name: String?,
    val mealType: app.chompass.models.MealType,
    val calories: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    val sugar: Double?,
    val saturatedFat: Double?,
    val monounsaturatedFat: Double?,
    val polyunsaturatedFat: Double?,
    val transFat: Double?,
    val cholesterol: Double?,
    val sodium: Double?,
    val potassium: Double?,
    val calcium: Double?,
    val iron: Double?,
    val magnesium: Double?,
    val zinc: Double?,
    val vitaminA: Double?,
    val vitaminC: Double?,
    val vitaminD: Double?,
    val vitaminB12: Double?,
    val vitaminE: Double?,
    val vitaminK: Double?,
    val folate: Double?,
    val clientRecordId: String?,
    /** Stable Health Connect record id (Metadata.id) — see [ExternalWeight.recordId]. */
    val recordId: String = ""
)

data class ExternalWeight(
    val time: Instant,
    val weightKg: Double,
    val clientRecordId: String?,
    /** Stable Health Connect record id (Metadata.id) — used as the dedup key when the
     *  source set no clientRecordId, so in-place value edits update rather than duplicate. */
    val recordId: String = ""
) {
    @Suppress("unused")
    val zoneOffset: ZoneOffset? get() = null
}

data class ExternalBodyFat(
    val time: Instant,
    /** 0–1 fraction, matching UserProfile.bodyFatPercentage convention. */
    val bodyFatFraction: Double,
    val clientRecordId: String?,
    /** Stable Health Connect record id (Metadata.id) — see [ExternalWeight.recordId]. */
    val recordId: String = ""
)

data class HealthEnergySummary(
    val activeAverageCalories: Int,
    val basalAverageCalories: Int?,
    val totalAverageCalories: Int?,
    val daysUsed: Int,
    val requestedDays: Int
)
