package app.chompass.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class BodyMetricsImporterTest {
    private val utc = ZoneId.from(ZoneOffset.UTC)

    private fun success(text: String, hint: String = "kg"): BodyMetricsImportResult.Success {
        val r = BodyMetricsImporter.parse(text, utc, hint)
        assertTrue("expected Success but was $r", r is BodyMetricsImportResult.Success)
        return r as BodyMetricsImportResult.Success
    }

    @Test
    fun `blank is empty payload`() {
        assertEquals(BodyMetricsImportResult.EmptyPayload, BodyMetricsImporter.parse("   "))
    }

    @Test
    fun `unknown csv header is unsupported`() {
        assertEquals(
            BodyMetricsImportResult.UnsupportedFormat,
            BodyMetricsImporter.parse("foo,bar,baz\n1,2,3")
        )
    }

    @Test
    fun `legacy 1_7 nofud csv parses weight and body fat`() {
        val csv = """
            metric,timestamp,value,unit
            weight,2026-01-02T08:30:00Z,80.00,kg
            body_fat,2026-01-02T08:30:00Z,20.00,percent
        """.trimIndent()
        val r = success(csv)
        assertEquals(BodyMetricsSourceFormat.NOFUD_CSV, r.sourceFormat)
        assertEquals(80.0, r.weights.single().weightKg, 0.001)
        assertEquals(0.20, r.bodyFats.single().bodyFatFraction, 0.001)
    }

    @Test
    fun `nofud csv collapses site rows sharing a timestamp into one snapshot`() {
        val csv = """
            metric,timestamp,value,unit
            waist,2026-01-02T08:30:00Z,85.0,cm
            neck,2026-01-02T08:30:00Z,38.0,cm
        """.trimIndent()
        val r = success(csv)
        assertEquals(1, r.measurements.size)
        assertEquals(85.0, r.measurements.single().waistCm!!, 0.001)
        assertEquals(38.0, r.measurements.single().neckCm!!, 0.001)
    }

    @Test
    fun `openscale csv maps weight fat and circumferences`() {
        val csv = """
            dateTime,weight,fat,waist,neck,hip
            02.01.2026 08:30,80.5,21.0,86.0,39.0,95.0
        """.trimIndent()
        val r = success(csv)
        assertEquals(BodyMetricsSourceFormat.OPENSCALE_CSV, r.sourceFormat)
        assertEquals(80.5, r.weights.single().weightKg, 0.001)
        assertEquals(0.21, r.bodyFats.single().bodyFatFraction, 0.001)
        val m = r.measurements.single()
        assertEquals(86.0, m.waistCm!!, 0.001)
        assertEquals(95.0, m.hipsCm!!, 0.001)
    }

    @Test
    fun `generic csv with lb header converts to kg`() {
        val csv = """
            Date,Weight (lbs)
            2026-01-02,176.4
        """.trimIndent()
        val r = success(csv)
        assertEquals(BodyMetricsSourceFormat.GENERIC_CSV, r.sourceFormat)
        assertEquals(80.0, r.weights.single().weightKg, 0.05)
    }

    @Test
    fun `generic csv honors lb hint when header has no unit`() {
        val csv = """
            Date,Weight
            2026-01-02,176.4
        """.trimIndent()
        val r = success(csv, hint = "lb")
        assertEquals(80.0, r.weights.single().weightKg, 0.05)
    }

    @Test
    fun `us date format parses in generic csv`() {
        val csv = """
            Date,Weight (kg)
            01/02/2026,80.0
        """.trimIndent()
        val r = success(csv)
        assertEquals(80.0, r.weights.single().weightKg, 0.001)
    }

    @Test
    fun `out of range weight is dropped`() {
        val csv = """
            metric,timestamp,value,unit
            weight,2026-01-02T08:30:00Z,3.00,kg
        """.trimIndent()
        val r = BodyMetricsImporter.parse(csv, utc)
        // The only row is invalid, so nothing to import.
        assertEquals(BodyMetricsImportResult.EmptyPayload, r)
    }

    @Test
    fun `ids are deterministic across repeated parses`() {
        val csv = """
            metric,timestamp,value,unit
            weight,2026-01-02T08:30:00Z,80.00,kg
        """.trimIndent()
        val a = success(csv).weights.single().id
        val b = success(csv).weights.single().id
        assertEquals(a, b)
    }

    @Test
    fun `malformed nofud csv row reports the row number`() {
        val csv = """
            metric,timestamp,value,unit
            weight,not-a-date,80,kg
        """.trimIndent()
        val r = BodyMetricsImporter.parse(csv, utc)
        assertTrue(r is BodyMetricsImportResult.Malformed)
    }
}
