package app.chompass.data

import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.WeightEntry
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
}
