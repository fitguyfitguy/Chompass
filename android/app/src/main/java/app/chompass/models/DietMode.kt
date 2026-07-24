package app.chompass.models

import androidx.annotation.StringRes
import app.chompass.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DietMode {
    @SerialName("standard") STANDARD,
    @SerialName("keto") KETO;

    @get:StringRes
    val displayNameRes: Int get() = when (this) {
        STANDARD -> R.string.diet_mode_standard
        KETO -> R.string.diet_mode_keto_beta
    }
}
