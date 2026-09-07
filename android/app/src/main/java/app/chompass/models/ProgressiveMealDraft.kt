package app.chompass.models

import app.chompass.services.ai.ConstituentReconcile
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.toMicronutrients
import java.time.Instant
import java.util.UUID

/**
 * One reviewed ingredient in an in-progress weigh-as-you-go meal (upstream #168).
 * [analysis] is already user-edited (serving scale applied); [imageBytes] is the
 * capture for that ingredient (optional for text-only additions later).
 */
data class ProgressiveMealItem(
    val id: UUID = UUID.randomUUID(),
    val analysis: FoodAnalysis,
    val imageBytes: ByteArray? = null,
    val mealType: String = MealType.OTHER.id,
    val source: FoodSource = FoodSource.SNAP_FOOD,
    val selectedServingUnit: String? = null,
    val selectedServingQuantity: Double? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProgressiveMealItem) return false
        return id == other.id &&
            analysis == other.analysis &&
            mealType == other.mealType &&
            source == other.source &&
            selectedServingUnit == other.selectedServingUnit &&
            selectedServingQuantity == other.selectedServingQuantity &&
            imageBytes.contentEquals(other.imageBytes)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + analysis.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + mealType.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (selectedServingUnit?.hashCode() ?: 0)
        result = 31 * result + (selectedServingQuantity?.hashCode() ?: 0)
        return result
    }
}

/** In-memory session for accumulating photo-per-ingredient meal builds. */
data class ProgressiveMealDraft(
    val name: String = "",
    val mealType: String = MealType.currentMealId,
    val items: List<ProgressiveMealItem> = emptyList(),
) {
    val totalCalories: Int get() = items.sumOf { it.analysis.calories }
    val totalProtein: Double get() = items.sumOf { it.analysis.protein }
    val totalCarbs: Double get() = items.sumOf { it.analysis.carbs }
    val totalFat: Double get() = items.sumOf { it.analysis.fat }
}

/**
 * Builds diary rows for a progressive meal.
 *
 * Named draft ([name] non-blank after trim): one [FoodEntry] whose
 * [FoodEntry.constituents] are the builder items (Codeberg #91). Unnamed:
 * one row per item sharing [recipeLogId], same grouping as
 * [app.chompass.data.RecipeRepository.logRecipe]. Image persistence is the
 * caller's job via [imageFilenameFor]. [resolveName] disambiguates the
 * composite meal name, and each ingredient name on the unnamed path.
 */
fun ProgressiveMealDraft.toFoodEntries(
    recipeLogId: UUID,
    timestamp: Instant,
    imageFilenameFor: (ProgressiveMealItem, UUID) -> String?,
    resolveName: (String) -> String = { it },
): List<FoodEntry> {
    val trimmedName = name.trim()
    if (trimmedName.isNotEmpty()) {
        return listOfNotNull(
            toCompositeEntry(
                mealName = resolveName(trimmedName),
                timestamp = timestamp,
                imageFilenameFor = imageFilenameFor,
            ),
        )
    }
    val mealType = this.mealType
    return items.map { item ->
        val entryId = UUID.randomUUID()
        val analysis = item.analysis
        val filename = imageFilenameFor(item, entryId)
        analysis.toMicronutrients().applyTo(
            FoodEntry(
                id = entryId,
                name = resolveName(analysis.name),
                calories = analysis.calories,
                protein = analysis.protein,
                carbs = analysis.carbs,
                fat = analysis.fat,
                timestamp = timestamp,
                imageFilename = filename,
                emoji = analysis.emoji,
                source = item.source,
                mealType = mealType,
                servingSizeGrams = analysis.servingSizeGrams,
                servingUnitOptions = analysis.servingUnitOptions,
                selectedServingUnit = if (analysis.servingUnitOptions.isEmpty()) {
                    null
                } else {
                    item.selectedServingUnit
                },
                selectedServingQuantity = if (analysis.servingUnitOptions.isEmpty()) {
                    null
                } else {
                    item.selectedServingQuantity
                },
                customNote = analysis.customNote,
                grounding = analysis.grounding,
                recipeLogId = recipeLogId,
                constituents = analysis.constituents,
                productMetadata = analysis.productMetadata,
                microsCompositionSignature = microsCompositionSignature(analysis.constituents),
            ),
        )
    }
}

private fun ProgressiveMealDraft.toCompositeEntry(
    mealName: String,
    timestamp: Instant,
    imageFilenameFor: (ProgressiveMealItem, UUID) -> String?,
): FoodEntry? {
    if (items.isEmpty()) return null
    val constituents = items.map { it.toConstituent() }
    val agg = ConstituentReconcile.aggregatesFrom(constituents) ?: return null
    val entryId = UUID.randomUUID()
    var filename: String? = null
    for (item in items) {
        filename = imageFilenameFor(item, entryId)
        if (filename != null) break
    }
    val sources = items.map { it.source }.distinct()
    val source = sources.singleOrNull() ?: FoodSource.MANUAL
    return sumMicros(constituents).applyTo(
        FoodEntry(
            id = entryId,
            name = mealName,
            calories = agg.calories,
            protein = agg.protein,
            carbs = agg.carbs,
            fat = agg.fat,
            timestamp = timestamp,
            imageFilename = filename,
            emoji = items.firstNotNullOfOrNull { it.analysis.emoji },
            source = source,
            mealType = mealType,
            servingSizeGrams = agg.servingSizeGrams,
            recipeLogId = null,
            constituents = constituents,
            microsCompositionSignature = microsCompositionSignature(constituents),
        ),
    )
}

private fun ProgressiveMealItem.toConstituent(): FoodConstituent {
    val analysis = this.analysis
    return FoodConstituent(
        name = analysis.name,
        calories = analysis.calories,
        protein = analysis.protein,
        carbs = analysis.carbs,
        fat = analysis.fat,
        servingSizeGrams = analysis.servingSizeGrams ?: 0.0,
        emoji = analysis.emoji,
        servingUnitOptions = analysis.servingUnitOptions,
        selectedServingUnit = if (analysis.servingUnitOptions.isEmpty()) {
            null
        } else {
            selectedServingUnit
        },
        selectedServingQuantity = if (analysis.servingUnitOptions.isEmpty()) {
            null
        } else {
            selectedServingQuantity
        },
        sugar = analysis.sugar,
        addedSugar = analysis.addedSugar,
        fiber = analysis.fiber,
        saturatedFat = analysis.saturatedFat,
        monounsaturatedFat = analysis.monounsaturatedFat,
        polyunsaturatedFat = analysis.polyunsaturatedFat,
        cholesterol = analysis.cholesterol,
        sodium = analysis.sodium,
        potassium = analysis.potassium,
        transFat = analysis.transFat,
        calcium = analysis.calcium,
        iron = analysis.iron,
        magnesium = analysis.magnesium,
        zinc = analysis.zinc,
        vitaminA = analysis.vitaminA,
        vitaminC = analysis.vitaminC,
        vitaminD = analysis.vitaminD,
        vitaminB12 = analysis.vitaminB12,
        vitaminE = analysis.vitaminE,
        vitaminK = analysis.vitaminK,
        folate = analysis.folate,
        omega3 = analysis.omega3,
        caffeine = analysis.caffeine,
    )
}

private fun sumMicros(rows: List<FoodConstituent>): MicronutrientValues {
    fun s(pick: (FoodConstituent) -> Double?): Double? {
        val present = rows.mapNotNull(pick)
        return if (present.isEmpty()) null else present.sum()
    }
    return MicronutrientValues(
        sugar = s { it.sugar },
        addedSugar = s { it.addedSugar },
        fiber = s { it.fiber },
        saturatedFat = s { it.saturatedFat },
        monounsaturatedFat = s { it.monounsaturatedFat },
        polyunsaturatedFat = s { it.polyunsaturatedFat },
        cholesterol = s { it.cholesterol },
        sodium = s { it.sodium },
        potassium = s { it.potassium },
        transFat = s { it.transFat },
        calcium = s { it.calcium },
        iron = s { it.iron },
        magnesium = s { it.magnesium },
        zinc = s { it.zinc },
        vitaminA = s { it.vitaminA },
        vitaminC = s { it.vitaminC },
        vitaminD = s { it.vitaminD },
        vitaminB12 = s { it.vitaminB12 },
        vitaminE = s { it.vitaminE },
        vitaminK = s { it.vitaminK },
        folate = s { it.folate },
        omega3 = s { it.omega3 },
        caffeine = s { it.caffeine },
    )
}

/** Convenience for tests that don't care about images. */
fun ProgressiveMealDraft.toFoodEntriesForTest(
    recipeLogId: UUID = UUID.randomUUID(),
    timestamp: Instant = Instant.parse("2026-07-29T12:00:00Z"),
): List<FoodEntry> = toFoodEntries(
    recipeLogId = recipeLogId,
    timestamp = timestamp,
    imageFilenameFor = { _, _ -> null },
)
