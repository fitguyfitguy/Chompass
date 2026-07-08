package org.codeberg.fitguy.nofud.models

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Persisted "input-only" draft for camera + note flows that failed before
 * analysis completed (e.g. provider/network errors).
 */
@Serializable
data class PendingFoodInputDraft(
    val imageFilename: String,
    val note: String = "",
    val source: FoodSource = FoodSource.SNAP_FOOD,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now()
)
