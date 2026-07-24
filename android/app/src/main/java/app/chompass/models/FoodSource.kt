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
    /** Optional grounded entry: model recognition + local USDA/OFF/history nutrients. */
    @SerialName("grounded") GROUNDED,
}
