package org.codeberg.fitguy.nofud.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Single body-fat reading (fraction 0–1). Latest value syncs to UserProfile.bodyFatPercentage. */
@Serializable
data class BodyFatEntry(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = InstantSerializer::class)
    val date: Instant = Instant.now(),
    /** Stored as a fraction (0.0–1.0), same convention as UserProfile.bodyFatPercentage. */
    val bodyFatFraction: Double
) {
    /** Convenience for views that prefer 0–100 scale (e.g. "23%"). */
    val bodyFatPercent: Double get() = bodyFatFraction * 100
}
