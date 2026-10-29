package app.chompass.export

import app.chompass.models.BodyCompositionEntry
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BodyMetricsExporterTest {
    private val t0 = Instant.parse("2026-01-02T08:30:00Z")
    private val t1 = Instant.parse("2026-01-03T08:30:00Z")

    private val weights = listOf(WeightEntry(date = t0, weightKg = 80.0), WeightEntry(date = t1, weightKg = 79.5))
    private val bodyFats = listOf(BodyFatEntry(date = t0, bodyFatFraction = 0.20))
    private val measurements = listOf(BodyMeasurement(date = t0, waistCm = 85.0, neckCm = 38.0))
    private val compositions = listOf(
        BodyCompositionEntry(
            date = t0,
            boneMassKg = 3.2,
            totalBodyWaterKg = 46.0,
            muscleMassKg = 30.4,
            leanMassKg = 65.2,
            visceralFatRating = 8.0,
        ),
        // Lean-only row: every other field stays absent on the wire.
        BodyCompositionEntry(date = t1, leanMassKg = 64.5),
    )

    @Test
    fun `empty inputs return null`() {
        assertNull(
            BodyMetricsExporter.build(
                weights = emptyList(),
                bodyFats = emptyList(),
                measurements = emptyList(),
                format = BodyMetricsFormat.CSV,
            )
        )
        assertNull(
            BodyMetricsExporter.build(
                weights = emptyList(),
                bodyFats = emptyList(),
                measurements = listOf(BodyMeasurement(date = t0)),
                format = BodyMetricsFormat.JSON,
            )
        )
    }

    @Test
    fun `csv keeps long metric header and one row per site`() {
        val (name, csv) = BodyMetricsExporter.build(
            weights = weights,
            bodyFats = bodyFats,
            measurements = measurements,
            format = BodyMetricsFormat.CSV,
        )!!
        assertTrue(name.endsWith(".csv"))
        val lines = csv.trim().lines()
        assertEquals("metric,timestamp,value,unit", lines.first())
        assertTrue(lines.any { it.startsWith("weight,") && it.endsWith(",kg") })
        assertTrue(lines.any { it.startsWith("body_fat,") && it.endsWith(",percent") })
        assertTrue(lines.any { it.startsWith("waist,") && it.endsWith(",cm") })
        assertTrue(lines.any { it.startsWith("neck,") && it.endsWith(",cm") })
    }

    @Test
    fun `csv writes scale composition rows in kg plus the unitless rating`() {
        val (_, csv) = BodyMetricsExporter.build(
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            compositions = compositions,
            format = BodyMetricsFormat.CSV,
        )!!
        val lines = csv.trim().lines().drop(1)
        assertTrue(lines.any { it.startsWith("bone_mass,") && it.endsWith(",kg") })
        assertTrue(lines.any { it.startsWith("total_body_water,") && it.endsWith(",kg") })
        assertTrue(lines.any { it.startsWith("muscle_mass,") && it.endsWith(",kg") })
        assertTrue(lines.any { it.startsWith("lean_mass,") && it.endsWith(",kg") })
        assertTrue(lines.any { it.startsWith("visceral_fat,") && it.endsWith(",index") })
        // t1 row has ONLY lean mass; nothing else is fabricated for it.
        val t1Rows = lines.filter { it.contains("2026-01-03") }
        assertEquals(1, t1Rows.size)
        assertTrue(t1Rows.single().startsWith("lean_mass,"))
    }

    @Test
    fun `json round-trips through the importer preserving ids and values`() {
        val (name, json) = BodyMetricsExporter.build(
            weights = weights,
            bodyFats = bodyFats,
            measurements = measurements,
            format = BodyMetricsFormat.JSON,
        )!!
        assertTrue(name.endsWith(".json"))
        val result = BodyMetricsImporter.parse(json)
        assertTrue(result is BodyMetricsImportResult.Success)
        result as BodyMetricsImportResult.Success
        assertEquals(BodyMetricsSourceFormat.NOFUD_JSON, result.sourceFormat)
        assertEquals(weights.map { it.id }.toSet(), result.weights.map { it.id }.toSet())
        assertEquals(79.5, result.weights.first { it.date == t1 }.weightKg, 0.001)
        assertEquals(0.20, result.bodyFats.first().bodyFatFraction, 0.001)
        assertEquals(85.0, result.measurements.first().waistCm!!, 0.001)
    }

    @Test
    fun `json stamps 1_1 and carries body_composition iff rows exist`() {
        val (_, withRows) = BodyMetricsExporter.build(
            weights = weights,
            bodyFats = emptyList(),
            measurements = emptyList(),
            compositions = compositions,
            format = BodyMetricsFormat.JSON,
        )!!
        assertTrue(withRows.contains("\"format_version\": \"${BodyMetricsExporter.FORMAT_VERSION_COMPOSITION}\""))
        assertTrue(withRows.contains("\"body_composition\""))

        val (_, withoutRows) = BodyMetricsExporter.build(
            weights = weights,
            bodyFats = emptyList(),
            measurements = emptyList(),
            format = BodyMetricsFormat.JSON,
        )!!
        assertTrue(withoutRows.contains("\"format_version\": \"${BodyMetricsExporter.FORMAT_VERSION}\""))
        assertFalse(withoutRows.contains("body_composition"))
    }

    @Test
    fun `json composition rows round-trip including the lean-only row`() {
        val (_, json) = BodyMetricsExporter.build(
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            compositions = compositions,
            format = BodyMetricsFormat.JSON,
        )!!
        val result = BodyMetricsImporter.parse(json) as BodyMetricsImportResult.Success
        assertEquals(compositions.map { it.id }.toSet(), result.compositions.map { it.id }.toSet())
        val full = result.compositions.first { it.date == t0 }
        assertEquals(3.2, full.boneMassKg!!, 0.0001)
        assertEquals(46.0, full.totalBodyWaterKg!!, 0.0001)
        assertEquals(30.4, full.muscleMassKg!!, 0.0001)
        assertEquals(65.2, full.leanMassKg!!, 0.0001)
        assertEquals(8.0, full.visceralFatRating!!, 0.0001)
        val leanOnly = result.compositions.first { it.date == t1 }
        assertNull(leanOnly.boneMassKg)
        assertEquals(64.5, leanOnly.leanMassKg!!, 0.0001)
    }
}
