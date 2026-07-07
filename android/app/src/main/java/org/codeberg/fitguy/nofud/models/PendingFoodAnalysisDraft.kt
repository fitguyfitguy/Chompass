package org.codeberg.fitguy.nofud.models

import org.codeberg.fitguy.nofud.services.ai.FoodAnalysis
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class PendingFoodAnalysisDraft(
    val analysis: FoodAnalysis,
    val imageFilename: String? = null,
    val source: FoodSource? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now()
)
