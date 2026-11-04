package app.chompass.scale

import app.chompass.models.BodyCompositionEntry
import app.chompass.models.BodyFatEntry
import app.chompass.models.WeightEntry
import com.health.openscale.core.bluetooth.data.ScaleMeasurement
import com.health.openscale.core.data.Kg
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.Percent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Date

class ScaleMeasurementMapperTest {

    private fun measurementAt(epochMs: Long): ScaleMeasurement =
        ScaleMeasurement(dateTime = Date(epochMs))

    /** Asserts present and returns the value (org.junit.Assert.assertNotNull is not inlined here). */
    private fun <T> present(actual: T?): T {
        org.junit.Assert.assertNotNull("expected a value", actual)
        return requireNotNull(actual)
    }

    @Test
    fun `full composition reading maps to all three entries`() {
        // 2026-01-15T08:00:00Z
        val t = Instant.parse("2026-01-15T08:00:00Z").toEpochMilli()
        val m = measurementAt(t).apply {
            this[MeasurementType.WEIGHT] = Kg(78.5f)
            this[MeasurementType.BODY_FAT] = Percent(24.0f)
            this[MeasurementType.MUSCLE] = Percent(42.0f)
            this[MeasurementType.WATER] = Percent(55.0f)
            this[MeasurementType.BONE] = Kg(3.2f)
            this[MeasurementType.LBM] = Kg(59.66f)
            this[MeasurementType.VISCERAL_FAT] = 9.0f
        }

        val mapped = ScaleMeasurementMapper.map(m)

        val w = present(mapped.weight)
        assertEquals(78.5, w.weightKg, 1e-6)
        assertEquals(t, w.date.toEpochMilli())

        val f = present(mapped.fat)
        assertEquals(0.24, f.bodyFatFraction, 1e-6)

        val c = present(mapped.composition)
        // muscle: 42% of 78.5 kg
        assertEquals(0.42 * 78.5, present(c.muscleMassKg), 1e-6)
        // water: 55% of 78.5 kg
        assertEquals(0.55 * 78.5, present(c.totalBodyWaterKg), 1e-6)
        assertEquals(3.2, present(c.boneMassKg), 1e-6)
        assertEquals(59.66, present(c.leanMassKg), 1e-6)
        assertEquals(9.0, present(c.visceralFatRating), 1e-6)
    }

    @Test
    fun `ids are deterministic for the same timestamp`() {
        val t = Instant.parse("2026-02-01T07:30:00Z").toEpochMilli()
        val build = {
            measurementAt(t).apply { this[MeasurementType.WEIGHT] = Kg(80f) }
        }
        val a = ScaleMeasurementMapper.map(build())
        val b = ScaleMeasurementMapper.map(build())

        assertEquals(present(a.weight).id, present(b.weight).id)
    }

    @Test
    fun `weightless reading keeps fat but drops percent-of-weight composition`() {
        val m = measurementAt(1000L).apply {
            this[MeasurementType.MUSCLE] = Percent(40f)
            this[MeasurementType.BODY_FAT] = Percent(20f)
        }
        val mapped = ScaleMeasurementMapper.map(m)
        assertNull(mapped.weight)
        // body fat is a standalone percent entry; it does not need the row weight
        assertTrue(present(mapped.fat).bodyFatFraction == 0.20)
        // muscle is percent-of-weight; without weight it is dropped instead of invented
        assertNull(mapped.composition)
    }

    @Test
    fun `out-of-range weight is dropped`() {
        val m = measurementAt(1000L).apply { this[MeasurementType.WEIGHT] = Kg(5f) } // below MIN_WEIGHT_KG
        assertNull(ScaleMeasurementMapper.map(m).weight)
    }

    @Test
    fun `out-of-range body fat is dropped`() {
        val m = measurementAt(1000L).apply {
            this[MeasurementType.WEIGHT] = Kg(78f)
            this[MeasurementType.BODY_FAT] = Percent(80f) // above MAX_BODY_FAT_PERCENT
        }
        val mapped = ScaleMeasurementMapper.map(m)
        present(mapped.weight)
        assertNull(mapped.fat)
    }

    @Test
    fun `muscle above cap is dropped but weight survives`() {
        val m = measurementAt(1000L).apply {
            this[MeasurementType.WEIGHT] = Kg(78.5f)
            this[MeasurementType.WATER] = Percent(50f)
            this[MeasurementType.MUSCLE] = Percent(80f) // 62.8 kg > 70% of 78.5
        }
        val mapped = ScaleMeasurementMapper.map(m)
        present(mapped.weight)
        assertNull(present(mapped.composition).muscleMassKg)
    }

    @Test
    fun `bone outside 0_5_to_15_kg window is dropped`() {
        val t = Instant.parse("2026-03-01T10:00:00Z").toEpochMilli()
        val low = ScaleMeasurementMapper.map(
            measurementAt(t).apply {
                this[MeasurementType.WEIGHT] = Kg(78f)
                this[MeasurementType.WATER] = Percent(50f)
                this[MeasurementType.BONE] = Kg(0.3f)
            },
        )
        assertNull(present(low.composition).boneMassKg)

        val ok = ScaleMeasurementMapper.map(
            measurementAt(t).apply {
                this[MeasurementType.WEIGHT] = Kg(78f)
                this[MeasurementType.BONE] = Kg(3.0f)
            },
        )
        assertEquals(3.0, present(present(ok.composition).boneMassKg), 1e-6)
    }

    @Test
    fun `visceral rating outside 1_to_59 is dropped`() {
        val t = Instant.parse("2026-03-01T10:00:00Z").toEpochMilli()
        val m = ScaleMeasurementMapper.map(
            measurementAt(t).apply {
                this[MeasurementType.WEIGHT] = Kg(78f)
                this[MeasurementType.WATER] = Percent(50f)
                this[MeasurementType.VISCERAL_FAT] = 99.0f
            },
        )
        assertNull(present(m.composition).visceralFatRating)
    }

    @Test
    fun `lean mass above weight is dropped`() {
        val m = ScaleMeasurementMapper.map(
            measurementAt(1000L).apply {
                this[MeasurementType.WEIGHT] = Kg(78f)
                this[MeasurementType.WATER] = Percent(50f)
                this[MeasurementType.LBM] = Kg(90f)
            },
        )
        assertNull(present(m.composition).leanMassKg)
    }

    @Test
    fun `entry types line up with repository import paths`() {
        // Compile-level sanity: mapped entries are exactly the types the three
        // repositories' importFromFile overloads accept.
        val w: WeightEntry? = ScaleMeasurementMapper.map(measurementAt(1L)).weight
        val f: BodyFatEntry? = ScaleMeasurementMapper.map(measurementAt(1L)).fat
        val c: BodyCompositionEntry? = ScaleMeasurementMapper.map(measurementAt(1L)).composition
        assertTrue(w == null && f == null && c == null)
    }
}
