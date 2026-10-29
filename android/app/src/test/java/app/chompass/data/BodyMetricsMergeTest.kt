package app.chompass.data

import app.chompass.models.BodyCompositionEntry
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.WeightEntry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class BodyMetricsMergeTest {
    private val t0 = Instant.parse("2026-01-02T08:30:00Z")

    @Test
    fun `weight upsert by id replaces value and counts change`() {
        val id = UUID.randomUUID()
        val existing = listOf(WeightEntry(id = id, date = t0, weightKg = 80.0))
        val incoming = listOf(WeightEntry(id = id, date = t0, weightKg = 79.0))
        val (merged, changed) = mergeWeightsById(existing, incoming)
        assertEquals(1, merged.size)
        assertEquals(1, changed)
        assertEquals(79.0, merged.single().weightKg, 0.001)
    }

    @Test
    fun `weight re-import of identical file is a no-op`() {
        val existing = listOf(WeightEntry(id = UUID.randomUUID(), date = t0, weightKg = 80.0))
        val (_, changed) = mergeWeightsById(existing, existing)
        assertEquals(0, changed)
    }

    @Test
    fun `weight near-duplicate of a manual entry within a minute is skipped`() {
        val manual = WeightEntry(id = UUID.randomUUID(), date = t0, weightKg = 80.0)
        val imported = WeightEntry(id = UUID.randomUUID(), date = t0.plusSeconds(30), weightKg = 80.0)
        val (merged, changed) = mergeWeightsById(listOf(manual), listOf(imported))
        assertEquals(1, merged.size)
        assertEquals(0, changed)
    }

    @Test
    fun `body fat new id is added`() {
        val existing = listOf(BodyFatEntry(id = UUID.randomUUID(), date = t0, bodyFatFraction = 0.20))
        val incoming = listOf(BodyFatEntry(id = UUID.randomUUID(), date = t0.plusSeconds(3600), bodyFatFraction = 0.19))
        val (merged, changed) = mergeBodyFatsById(existing, incoming)
        assertEquals(2, merged.size)
        assertEquals(1, changed)
    }

    @Test
    fun `measurement upsert only counts when values differ`() {
        val id = UUID.randomUUID()
        val existing = listOf(BodyMeasurement(id = id, date = t0, waistCm = 85.0))
        val same = listOf(BodyMeasurement(id = id, date = t0, waistCm = 85.0))
        assertEquals(0, mergeMeasurementsById(existing, same).second)
        val changed = listOf(BodyMeasurement(id = id, date = t0, waistCm = 84.0))
        assertEquals(1, mergeMeasurementsById(existing, changed).second)
    }

    // --- Scale composition merge (BodyCompositionRepository.importFromFile) ---

    @Test
    fun `composition upsert by id replaces values and counts change`() {
        val id = UUID.randomUUID()
        val existing = listOf(BodyCompositionEntry(id = id, date = t0, leanMassKg = 64.0))
        val incoming = listOf(BodyCompositionEntry(id = id, date = t0, leanMassKg = 64.5, boneMassKg = 3.2))
        val (merged, changed) = mergeCompositionsById(existing, incoming)
        assertEquals(1, merged.size)
        assertEquals(1, changed)
        assertEquals(64.5, merged.single().leanMassKg!!, 0.0001)
        assertEquals(3.2, merged.single().boneMassKg!!, 0.0001)
    }

    @Test
    fun `composition re-import of the same rows is a no-op`() {
        val existing = listOf(
            BodyCompositionEntry(id = UUID.randomUUID(), date = t0, leanMassKg = 64.5, visceralFatRating = 8.0)
        )
        val (merged, changed) = mergeCompositionsById(existing, existing)
        assertEquals(1, merged.size)
        assertEquals(0, changed)
    }

    @Test
    fun `composition near-duplicate with a different id but identical date and values is skipped`() {
        val manual = BodyCompositionEntry(id = UUID.randomUUID(), date = t0, leanMassKg = 64.5)
        val imported = BodyCompositionEntry(id = UUID.randomUUID(), date = t0, leanMassKg = 64.5)
        val (merged, changed) = mergeCompositionsById(listOf(manual), listOf(imported))
        assertEquals(1, merged.size)
        assertEquals(0, changed)
    }

    @Test
    fun `composition same timestamp with a different value is kept`() {
        val manual = BodyCompositionEntry(id = UUID.randomUUID(), date = t0, leanMassKg = 64.5)
        val imported = BodyCompositionEntry(id = UUID.randomUUID(), date = t0, leanMassKg = 63.9)
        val (merged, changed) = mergeCompositionsById(listOf(manual), listOf(imported))
        // Different values = a genuine second reading, not a mirrored row.
        assertEquals(2, merged.size)
        assertEquals(1, changed)
    }

    @Test
    fun `composition sanitizer drops non-finite, non-positive and out-of-range rating values`() {
        val dirty = BodyCompositionEntry(
            date = t0,
            boneMassKg = Double.NaN,
            totalBodyWaterKg = -5.0,
            muscleMassKg = 0.0,
            leanMassKg = 64.5,
            visceralFatRating = 80.0,
        ).sanitizedComposition()
        assertEquals(null, dirty.boneMassKg)
        assertEquals(null, dirty.totalBodyWaterKg)
        assertEquals(null, dirty.muscleMassKg)
        assertEquals(64.5, dirty.leanMassKg!!, 0.0001)
        assertEquals(null, dirty.visceralFatRating)
        assertTrue(dirty.hasAnyValue)
    }

    @Test
    fun `lean mass sanitizer enforces the plausible 20 to 250 kg window`() {
        assertEquals(61.0, sanitizeLeanMassKg(61.0)!!, 0.0)
        assertEquals(null, sanitizeLeanMassKg(6.1))
        assertEquals(null, sanitizeLeanMassKg(251.0))
        assertEquals(null, sanitizeLeanMassKg(Double.NaN))
        assertEquals(null, sanitizeLeanMassKg(null))
    }
}
