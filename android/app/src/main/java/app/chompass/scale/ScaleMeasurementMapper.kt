package app.chompass.scale

import app.chompass.models.BodyCompositionEntry
import app.chompass.models.BodyFatEntry
import app.chompass.models.WeightEntry
import com.health.openscale.core.bluetooth.data.ScaleMeasurement
import com.health.openscale.core.data.MeasurementType
import java.util.Date
import java.util.UUID

/**
 * Pure-JVM mapping from a vendored openScale [ScaleMeasurement] to Chompass
 * diary entries. Conversion caps and guards mirror export/BodyMetricsImporter.kt
 * (parseOpenScaleCsv / parseOpenScale* constants) — the two entry paths must
 * accept exactly the same value ranges. See the OPENSCALE_* constants there.
 */
object ScaleMeasurementMapper {
    /** Cap: total-body-water kg may not exceed this fraction of the same-row weight. */
    private const val MAX_TBW_FRACTION_OF_WEIGHT = 0.85
    /** Cap: muscle kg may not exceed this fraction of the same-row weight. */
    private const val MAX_MUSCLE_FRACTION_OF_WEIGHT = 0.70
    private const val MIN_BONE_KG = 0.5
    private const val MAX_BONE_KG = 15.0
    private const val MIN_WEIGHT_KG = 20.0
    private const val MAX_WEIGHT_KG = 500.0
    private const val MIN_BODY_FAT_PERCENT = 3.0
    private const val MAX_BODY_FAT_PERCENT = 65.0
    private const val MIN_VISCERAL_RATING = 1.0
    private const val MAX_VISCERAL_RATING = 59.0

    data class MappedWeighIn(
        val weight: WeightEntry?,
        val fat: BodyFatEntry?,
        val composition: BodyCompositionEntry?,
    )

    /**
     * Deterministic ids make re-imports idempotent: the same weigh-in synced twice
     * (or re-synced after reinstall) collapses onto the same row via the
     * repositories' by-id import merge.
     */
    private fun deterministicId(kind: String, epochMs: Long): UUID =
        UUID.nameUUIDFromBytes("scale-sync:$kind:$epochMs".toByteArray())

    fun map(m: ScaleMeasurement): MappedWeighIn {
        val date = (m.dateTime ?: Date()).toInstant()
        val epochMs = date.toEpochMilli()

        val weightKg = m[MeasurementType.WEIGHT]?.value?.toDouble()
            ?.takeIf { it.isFinite() && it in MIN_WEIGHT_KG..MAX_WEIGHT_KG }

        val weight = weightKg?.let {
            WeightEntry(id = deterministicId("weight", epochMs), date = date, weightKg = it)
        }

        val fat = m[MeasurementType.BODY_FAT]?.value?.toDouble()
            ?.takeIf { it.isFinite() && it in MIN_BODY_FAT_PERCENT..MAX_BODY_FAT_PERCENT }
            ?.let { pct ->
                BodyFatEntry(
                    id = deterministicId("fat", epochMs),
                    date = date,
                    bodyFatFraction = pct / 100.0,
                )
            }

        // Percent-of-weight composition columns need the row's weight to become kg;
        // without a valid weight they are dropped instead of inventing a mass.
        val composition = if (weightKg != null) {
            var c = BodyCompositionEntry(id = deterministicId("comp", epochMs), date = date)

            m[MeasurementType.MUSCLE]?.value?.toDouble()
                ?.takeIf { it.isFinite() && it > 0 && it <= 100 }
                ?.let { pct ->
                    val kg = pct / 100.0 * weightKg
                    if (kg <= MAX_MUSCLE_FRACTION_OF_WEIGHT * weightKg) {
                        c = c.copy(muscleMassKg = kg)
                    }
                }

            m[MeasurementType.WATER]?.value?.toDouble()
                ?.takeIf { it.isFinite() && it > 0 && it <= 100 }
                ?.let { pct ->
                    val kg = pct / 100.0 * weightKg
                    if (kg <= MAX_TBW_FRACTION_OF_WEIGHT * weightKg) {
                        c = c.copy(totalBodyWaterKg = kg)
                    }
                }

            m[MeasurementType.BONE]?.value?.toDouble()
                ?.takeIf { it.isFinite() && it > MIN_BONE_KG && it <= MAX_BONE_KG }
                ?.let { c = c.copy(boneMassKg = it) }

            m[MeasurementType.LBM]?.value?.toDouble()
                ?.takeIf { it.isFinite() && it > 0 && it <= weightKg }
                ?.let { c = c.copy(leanMassKg = it) }

            m[MeasurementType.VISCERAL_FAT]?.toDouble()
                ?.takeIf { it.isFinite() && it in MIN_VISCERAL_RATING..MAX_VISCERAL_RATING }
                ?.let { c = c.copy(visceralFatRating = it) }

            if (c.hasAnyValue) c else null
        } else {
            null
        }

        return MappedWeighIn(weight, fat, composition)
    }
}
