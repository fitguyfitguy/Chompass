package org.codeberg.fitguy.nofud.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

/**
 * One ingredient in a [Recipe]. Nutrition is stored "at 1x" (base*) plus a
 * user-editable [quantityScale] multiplier, so scaling one ingredient never
 * loses the original values it was added with.
 */
@Serializable
data class RecipeIngredient(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val emoji: String? = null,
    val baseCalories: Int,
    val baseProtein: Double,
    val baseCarbs: Double,
    val baseFat: Double,
    val quantityScale: Double = 1.0,
    val baseSugar: Double? = null,
    val baseAddedSugar: Double? = null,
    val baseFiber: Double? = null,
    val baseSaturatedFat: Double? = null,
    val baseMonounsaturatedFat: Double? = null,
    val basePolyunsaturatedFat: Double? = null,
    val baseCholesterol: Double? = null,
    val baseSodium: Double? = null,
    val basePotassium: Double? = null,
    val baseTransFat: Double? = null,
    val baseCalcium: Double? = null,
    val baseIron: Double? = null,
    val baseMagnesium: Double? = null,
    val baseZinc: Double? = null,
    val baseVitaminA: Double? = null,
    val baseVitaminC: Double? = null,
    val baseVitaminD: Double? = null,
    val baseVitaminB12: Double? = null,
    val baseVitaminE: Double? = null,
    val baseVitaminK: Double? = null,
    val baseFolate: Double? = null,
    val baseOmega3: Double? = null,
) {
    val calories: Int get() = (baseCalories * quantityScale).roundToInt()
    val protein: Double get() = baseProtein * quantityScale
    val carbs: Double get() = baseCarbs * quantityScale
    val fat: Double get() = baseFat * quantityScale
    val sugar: Double? get() = baseSugar?.times(quantityScale)
    val addedSugar: Double? get() = baseAddedSugar?.times(quantityScale)
    val fiber: Double? get() = baseFiber?.times(quantityScale)
    val saturatedFat: Double? get() = baseSaturatedFat?.times(quantityScale)
    val monounsaturatedFat: Double? get() = baseMonounsaturatedFat?.times(quantityScale)
    val polyunsaturatedFat: Double? get() = basePolyunsaturatedFat?.times(quantityScale)
    val cholesterol: Double? get() = baseCholesterol?.times(quantityScale)
    val sodium: Double? get() = baseSodium?.times(quantityScale)
    val potassium: Double? get() = basePotassium?.times(quantityScale)
    val transFat: Double? get() = baseTransFat?.times(quantityScale)
    val calcium: Double? get() = baseCalcium?.times(quantityScale)
    val iron: Double? get() = baseIron?.times(quantityScale)
    val magnesium: Double? get() = baseMagnesium?.times(quantityScale)
    val zinc: Double? get() = baseZinc?.times(quantityScale)
    val vitaminA: Double? get() = baseVitaminA?.times(quantityScale)
    val vitaminC: Double? get() = baseVitaminC?.times(quantityScale)
    val vitaminD: Double? get() = baseVitaminD?.times(quantityScale)
    val vitaminB12: Double? get() = baseVitaminB12?.times(quantityScale)
    val vitaminE: Double? get() = baseVitaminE?.times(quantityScale)
    val vitaminK: Double? get() = baseVitaminK?.times(quantityScale)
    val folate: Double? get() = baseFolate?.times(quantityScale)
    val omega3: Double? get() = baseOmega3?.times(quantityScale)

    /** Converts an already-resolved [FoodEntry] snapshot into a fresh ingredient at 1x scale. */
    companion object {
        fun fromFoodEntry(entry: FoodEntry): RecipeIngredient = RecipeIngredient(
            name = entry.name,
            emoji = entry.emoji,
            baseCalories = entry.calories,
            baseProtein = entry.protein,
            baseCarbs = entry.carbs,
            baseFat = entry.fat,
            baseSugar = entry.sugar,
            baseAddedSugar = entry.addedSugar,
            baseFiber = entry.fiber,
            baseSaturatedFat = entry.saturatedFat,
            baseMonounsaturatedFat = entry.monounsaturatedFat,
            basePolyunsaturatedFat = entry.polyunsaturatedFat,
            baseCholesterol = entry.cholesterol,
            baseSodium = entry.sodium,
            basePotassium = entry.potassium,
            baseTransFat = entry.transFat,
            baseCalcium = entry.calcium,
            baseIron = entry.iron,
            baseMagnesium = entry.magnesium,
            baseZinc = entry.zinc,
            baseVitaminA = entry.vitaminA,
            baseVitaminC = entry.vitaminC,
            baseVitaminD = entry.vitaminD,
            baseVitaminB12 = entry.vitaminB12,
            baseVitaminE = entry.vitaminE,
            baseVitaminK = entry.vitaminK,
            baseFolate = entry.folate,
            baseOmega3 = entry.omega3,
        )
    }

    /** Resolves this ingredient into a loggable diary row, tagged with the shared [recipeLogId]. */
    fun toFoodEntry(logDate: Instant, mealType: MealType, recipeLogId: UUID): FoodEntry = FoodEntry(
        name = name,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        timestamp = logDate,
        emoji = emoji,
        source = FoodSource.MANUAL,
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
        recipeLogId = recipeLogId,
    )
}

/** A named, ordered collection of independently scalable [RecipeIngredient]s — a composable saved meal. */
@Serializable
data class Recipe(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val emoji: String? = null,
    val mealType: MealType = MealType.OTHER,
    val ingredients: List<RecipeIngredient> = emptyList(),
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
) {
    val totalCalories: Int get() = ingredients.sumOf { it.calories }
    val totalProtein: Double get() = ingredients.sumOf { it.protein }
    val totalCarbs: Double get() = ingredients.sumOf { it.carbs }
    val totalFat: Double get() = ingredients.sumOf { it.fat }

    /** Sums a nullable per-ingredient nutrient — null only if every ingredient omitted it. */
    private fun sumOptional(selector: (RecipeIngredient) -> Double?): Double? {
        if (ingredients.none { selector(it) != null }) return null
        return ingredients.sumOf { selector(it) ?: 0.0 }
    }

    val totalSugar: Double? get() = sumOptional { it.sugar }
    val totalFiber: Double? get() = sumOptional { it.fiber }
    val totalSodium: Double? get() = sumOptional { it.sodium }
}
