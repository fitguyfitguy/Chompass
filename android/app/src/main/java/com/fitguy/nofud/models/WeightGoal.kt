package com.fitguy.nofud.models

import androidx.annotation.StringRes
import com.fitguy.nofud.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WeightGoal {
    @SerialName("lose") LOSE,
    @SerialName("maintain") MAINTAIN,
    @SerialName("gain") GAIN;

    @get:StringRes
    val displayNameRes: Int get() = when (this) {
        LOSE -> R.string.goal_lose
        MAINTAIN -> R.string.goal_maintain
        GAIN -> R.string.goal_gain
    }
}
