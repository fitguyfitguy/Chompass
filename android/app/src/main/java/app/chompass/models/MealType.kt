package app.chompass.models

import androidx.annotation.StringRes
import app.chompass.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalTime

@Serializable
enum class MealType {
    @SerialName("breakfast") BREAKFAST,
    @SerialName("lunch") LUNCH,
    @SerialName("dinner") DINNER,
    @SerialName("snack") SNACK,
    @SerialName("other") OTHER;

    val id: String get() = when (this) {
        BREAKFAST -> "breakfast"
        LUNCH -> "lunch"
        DINNER -> "dinner"
        SNACK -> "snack"
        OTHER -> "other"
    }

    @get:StringRes
    val displayNameRes: Int get() = when (this) {
        BREAKFAST -> R.string.meal_breakfast
        LUNCH -> R.string.meal_lunch
        DINNER -> R.string.meal_dinner
        SNACK -> R.string.meal_snack
        OTHER -> R.string.meal_other
    }

    companion object {
        val currentMeal: MealType get() = CurrentMealCatalog.value.mealTypeAt(LocalTime.now())
        val currentMealId: String get() = CurrentMealCatalog.value.mealIdAt(LocalTime.now())

        fun fromId(id: String?): MealType? = when (id?.trim()?.lowercase()) {
            "breakfast" -> BREAKFAST
            "lunch" -> LUNCH
            "dinner" -> DINNER
            "snack" -> SNACK
            "other" -> OTHER
            else -> null
        }

        fun fromIdOrOther(id: String?): MealType = fromId(id) ?: OTHER

        fun iconMeal(id: String?): MealType = fromId(id) ?: OTHER
    }
}
