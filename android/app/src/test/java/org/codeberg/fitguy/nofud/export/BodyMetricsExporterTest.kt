package org.codeberg.fitguy.nofud.export

import org.codeberg.fitguy.nofud.models.BodyFatEntry
import org.codeberg.fitguy.nofud.models.BodyMeasurement
import org.codeberg.fitguy.nofud.models.WeightEntry
import org.junit.Assert.assertEquals
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

    @Test
    fun `empty inputs return null`() {
        assertNull(BodyMetricsExporter.build(emptyList(), emptyList(), emptyList(), BodyMetricsFormat.CSV))
        assertNull(BodyMetricsExporter.build(emptyList(), emptyList(), listOf(BodyMeasurement(date = t0)), BodyMetricsFormat.JSON))
    }

    @Test
    fun `csv keeps long metric header and one row per site`() {
        val (name, csv) = BodyMetricsExporter.build(weights, bodyFats, measurements, BodyMetricsFormat.CSV)!!
        assertTrue(name.endsWith(".csv"))
        val lines = csv.trim().lines()
        assertEquals("metric,timestamp,value,unit", lines.first())
        assertTrue(lines.any { it.startsWith("weight,") && it.endsWith(",kg") })
        assertTrue(lines.any { it.startsWith("body_fat,") && it.endsWith(",percent") })
        assertTrue(lines.any { it.startsWith("waist,") && it.endsWith(",cm") })
        assertTrue(lines.any { it.startsWith("neck,") && it.endsWith(",cm") })
    }

    @Test
    fun `json round-trips through the importer preserving ids and values`() {
        val (name, json) = BodyMetricsExporter.build(weights, bodyFats, measurements, BodyMetricsFormat.JSON)!!
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
}
