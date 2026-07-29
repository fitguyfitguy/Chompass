package app.chompass.models

import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.applyTo
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
    val mealType: MealType = MealType.OTHER,
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
    val mealType: MealType = MealType.currentMeal,
    val items: List<ProgressiveMealItem> = emptyList(),
) {
    val totalCalories: Int get() = items.sumOf { it.analysis.calories }
    val totalProtein: Double get() = items.sumOf { it.analysis.protein }
    val totalCarbs: Double get() = items.sumOf { it.analysis.carbs }
    val totalFat: Double get() = items.sumOf { it.analysis.fat }
}

/**
 * Builds diary rows for a progressive meal, sharing one [recipeLogId] (same
 * grouping as [RecipeRepository.logRecipe]). Pure helper for unit tests —
 * image persistence is the caller's job via [imageFilenameFor].
 */
fun ProgressiveMealDraft.toFoodEntries(
    recipeLogId: UUID,
    timestamp: Instant,
    imageFilenameFor: (ProgressiveMealItem, UUID) -> String?,
    resolveName: (String) -> String = { it },
): List<FoodEntry> {
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
            )
        )
    }
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
