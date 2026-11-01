package app.chompass.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FoodSource {
    @SerialName("snapFood") SNAP_FOOD,
    @SerialName("nutritionLabel") NUTRITION_LABEL,
    @SerialName("barcode") BARCODE,
    @SerialName("textInput") TEXT_INPUT,
    @SerialName("manual") MANUAL,
    /** Add Food "Search food" database pick (Open Food Facts / USDA / Swiss). */
    @SerialName("search") SEARCH,
    /** Optional grounded entry: model recognition + local USDA/OFF/history nutrients. */
    @SerialName("grounded") GROUNDED,
}

/**
 * A fresh review of this source can be stored as a favorite without a diary
 * row (Codeberg #126). Barcode shipped first; photo reviews use the same
 * action. Saved-meal relogs are a separate gate and stay out.
 */
fun FoodSource.offersFavoriteSave(): Boolean =
    this == FoodSource.BARCODE || this == FoodSource.SNAP_FOOD
