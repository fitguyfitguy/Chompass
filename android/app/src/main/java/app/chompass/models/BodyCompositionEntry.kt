package app.chompass.models

import app.chompass.R
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * One bioimpedance-scale reading: bone mass, total body water, muscle mass, lean
 * (fat-free) mass — all in kg — plus the vendor's dimensionless visceral-fat rating.
 *
 * Deliberately a separate store from [WeightEntry]: sync/import merge dedupe weights on
 * `(date, weight_kg)`, and Health Connect reports composition as its own record types.
 * Every field is optional because scales differ in what they report; `hasAnyValue`
 * keeps empty rows out of the stores.
 *
 * [leanMassKg] is the only field with a calculation side effect: the newest fresh
 * reading becomes `UserProfile.measuredLeanMassKg`, which takes priority over the
 * body-fat-derived lean mass for Katch-McArdle BMR and the protein cap.
 */
@Serializable
data class BodyCompositionEntry(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = InstantSerializer::class)
    val date: Instant = Instant.now(),
    val boneMassKg: Double? = null,
    val totalBodyWaterKg: Double? = null,
    val muscleMassKg: Double? = null,
    val leanMassKg: Double? = null,
    /** Vendor rating (typically 1–59); display-only, not a physical quantity. */
    val visceralFatRating: Double? = null,
) {
    /** True when at least one composition field is set. */
    val hasAnyValue: Boolean
        get() = listOf(boneMassKg, totalBodyWaterKg, muscleMassKg, leanMassKg, visceralFatRating)
            .any { it != null }

    fun value(metric: CompositionMetric): Double? = when (metric) {
        CompositionMetric.BONE_MASS -> boneMassKg
        CompositionMetric.TOTAL_BODY_WATER -> totalBodyWaterKg
        CompositionMetric.MUSCLE_MASS -> muscleMassKg
        CompositionMetric.LEAN_MASS -> leanMassKg
        CompositionMetric.VISCERAL_FAT -> visceralFatRating
    }

    /** A copy with one metric changed (same id + date — used for in-place daily updates). */
    fun setting(metric: CompositionMetric, value: Double?): BodyCompositionEntry = when (metric) {
        CompositionMetric.BONE_MASS -> copy(boneMassKg = value)
        CompositionMetric.TOTAL_BODY_WATER -> copy(totalBodyWaterKg = value)
        CompositionMetric.MUSCLE_MASS -> copy(muscleMassKg = value)
        CompositionMetric.LEAN_MASS -> copy(leanMassKg = value)
        CompositionMetric.VISCERAL_FAT -> copy(visceralFatRating = value)
    }

    /** The five display metrics, in body-composition-model order (bone first, rating last). */
    enum class CompositionMetric(
        val label: String,
        val labelRes: Int,
        /** Mass metrics honour the kg/lb unit toggle; the rating is unitless. */
        val isMass: Boolean,
    ) {
        BONE_MASS("Bone mass", R.string.progress_scale_metrics_bone_mass, true),
        TOTAL_BODY_WATER("Total body water", R.string.progress_scale_metrics_water, true),
        MUSCLE_MASS("Muscle mass", R.string.progress_scale_metrics_muscle, true),
        LEAN_MASS("Lean mass", R.string.progress_scale_metrics_lean, true),
        VISCERAL_FAT("Visceral fat", R.string.progress_scale_metrics_visceral, false);

        companion object {
            /** Metrics that show up as their own history card. */
            val massMetrics: List<CompositionMetric> get() = entries.filter { it.isMass }
        }
    }
}
