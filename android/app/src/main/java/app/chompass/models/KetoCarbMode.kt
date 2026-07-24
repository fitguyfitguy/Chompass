package app.chompass.models

import androidx.annotation.StringRes
import app.chompass.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class KetoCarbMode {
    @SerialName("adaptive") ADAPTIVE,
    @SerialName("manual") MANUAL;

    @get:StringRes
    val displayNameRes: Int get() = when (this) {
        ADAPTIVE -> R.string.keto_carb_mode_adaptive
        MANUAL -> R.string.keto_carb_mode_manual
    }
}
