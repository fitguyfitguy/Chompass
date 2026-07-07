package com.fitguy.nofud.models

import androidx.annotation.StringRes
import com.fitguy.nofud.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AutoBalanceMacro {
    @SerialName("protein") PROTEIN,
    @SerialName("carbs") CARBS,
    @SerialName("fat") FAT;

    @get:StringRes
    val labelRes: Int get() = when (this) {
        PROTEIN -> R.string.autobalance_protein
        CARBS -> R.string.autobalance_carbs
        FAT -> R.string.autobalance_fat
    }

    val kcalPerGram: Int get() = if (this == FAT) 9 else 4
}
