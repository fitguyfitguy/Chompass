package app.chompass.models

import kotlinx.serialization.Serializable

/**
 * Display-only product enrichment from a barcode lookup (Open Food Facts).
 * Persists on-device inside [FoodEntry] / FoodAnalysis; deliberately excluded
 * from diary export / sync / meal-share wire formats (photos and this card
 * never leave the device).
 */
@Serializable
data class FoodProductMetadata(
    val barcode: String,
    val packageQuantity: String? = null,
    val ingredientsText: String? = null,
    val allergens: List<String> = emptyList(),
    val traces: List<String> = emptyList(),
    val nutriScore: String? = null,
    val novaGroup: Int? = null,
    val ecoScore: String? = null,
    val labels: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val imageUrl: String? = null,
) {
    val hasDisplayDetails: Boolean
        get() = barcode.isNotEmpty() || packageQuantity != null || ingredientsText != null ||
            allergens.isNotEmpty() || traces.isNotEmpty() || nutriScore != null ||
            novaGroup != null || ecoScore != null || labels.isNotEmpty() || categories.isNotEmpty()
}
