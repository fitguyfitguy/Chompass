package app.chompass.export

import app.chompass.parity.ParityFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * Android importers must accept the committed cross-app samples in testdata/parity/.
 */
class ParityFixtureImportTest {

    @Test
    fun diarySampleImports() {
        val json = ParityFixtures.readText("diary-sample.json")
        val result = DiaryImporter.parse(json, ZoneOffset.UTC)
        assertTrue(result is DiaryImportResult.Success)
        val entries = (result as DiaryImportResult.Success).entries
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.name.isNotBlank() && it.calories >= 0 })
    }

    @Test
    fun bodyMetricsSampleImports() {
        val json = ParityFixtures.readText("body-metrics-sample.json")
        val result = BodyMetricsImporter.parse(json, ZoneOffset.UTC)
        assertTrue("expected Success but was $result", result is BodyMetricsImportResult.Success)
        val success = result as BodyMetricsImportResult.Success
        assertEquals(BodyMetricsSourceFormat.NOFUD_JSON, success.sourceFormat)
        assertTrue(success.weights.isNotEmpty())
        assertTrue(success.bodyFats.isNotEmpty())
        assertTrue(success.measurements.isNotEmpty())
    }
}
