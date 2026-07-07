package com.fitguy.nofud.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class WeightEntry(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = InstantSerializer::class)
    val date: Instant = Instant.now(),
    val weightKg: Double
) {
    val weightLbs: Double get() = weightKg * 2.20462
}
