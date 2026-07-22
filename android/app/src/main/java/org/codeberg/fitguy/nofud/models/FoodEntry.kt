package org.codeberg.fitguy.nofud.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class FoodEntry(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now(),
    /** Filename (not path) under filesDir/fudai-food-images/ where the JPEG lives. */
    val imageFilename: String? = null,
    val emoji: String? = null,
    val source: FoodSource,
    val mealType: MealType = MealType.OTHER,
    val sugar: Double? = null,
    val addedSugar: Double? = null,
    val fiber: Double? = null,
    val saturatedFat: Double? = null,
    val monounsaturatedFat: Double? = null,
    val polyunsaturatedFat: Double? = null,
    val cholesterol: Double? = null,
    val sodium: Double? = null,
    val potassium: Double? = null,
    val transFat: Double? = null,
    val calcium: Double? = null,
    val iron: Double? = null,
    val magnesium: Double? = null,
    val zinc: Double? = null,
    val vitaminA: Double? = null,
    val vitaminC: Double? = null,
    val vitaminD: Double? = null,
    val vitaminB12: Double? = null,
    val vitaminE: Double? = null,
    val vitaminK: Double? = null,
    val folate: Double? = null,
    val omega3: Double? = null,
    val servingSizeGrams: Double? = null,
    val servingUnitOptions: List<ServingUnitOption> = emptyList(),
    val selectedServingUnit: String? = null,
    val selectedServingQuantity: Double? = null,
    val customNote: String? = null,
    /** Shared id across all diary rows produced by one Recipe log; null for non-recipe entries. */
    @Serializable(with = UuidSerializer::class)
    val recipeLogId: UUID? = null,
    /**
     * Optional grounding provenance. Null for legacy / ungrounded entries.
     * Defaults keep older DataStore JSON and diary exports decodable.
     */
    val grounding: FoodGroundingProvenance? = null,
) {
    /**
     * Stable identity for Favorites / Frequent / Recents dedup.
     *
     * Keyed by normalized name only — not calories or serving size — so re-logging
     * the same food at a different grams / piece / unit amount stays one food.
     * Brand-new scans / manual entries that would collide are renamed via
     * [disambiguateFoodName] ("Name (2)", …) before save. Diary rows remain
     * separate; only the Saved Meals pickers collapse by this key.
     */
    val favoriteKey: String get() = name.trim().lowercase()

    /** New entry for the given log date (new id), copying nutrition and media from this entry. */
    fun duplicatedForLogging(
        logDate: Instant,
        mealType: MealType = MealType.currentMeal
    ): FoodEntry = FoodEntry(
        id = UUID.randomUUID(),
        name = name,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        timestamp = logDate,
        imageFilename = null, // new id -> new filename will be assigned on save
        emoji = emoji,
        source = source,
        mealType = mealType,
        sugar = sugar,
        addedSugar = addedSugar,
        fiber = fiber,
        saturatedFat = saturatedFat,
        monounsaturatedFat = monounsaturatedFat,
        polyunsaturatedFat = polyunsaturatedFat,
        cholesterol = cholesterol,
        sodium = sodium,
        potassium = potassium,
        transFat = transFat,
        calcium = calcium,
        iron = iron,
        magnesium = magnesium,
        zinc = zinc,
        vitaminA = vitaminA,
        vitaminC = vitaminC,
        vitaminD = vitaminD,
        vitaminB12 = vitaminB12,
        vitaminE = vitaminE,
        vitaminK = vitaminK,
        folate = folate,
        omega3 = omega3,
        servingSizeGrams = servingSizeGrams,
        servingUnitOptions = servingUnitOptions,
        selectedServingUnit = selectedServingUnit,
        selectedServingQuantity = selectedServingQuantity,
        customNote = customNote,
        grounding = grounding,
    )
}
