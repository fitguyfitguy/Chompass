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

        val lunch = entries.first { it.name == "Sample Lunch Item 1" }
        assertEquals(3, lunch.constituents.size)
        assertEquals("piece", lunch.constituents[0].selectedServingUnit)
        assertEquals(2.0, lunch.constituents[0].selectedServingQuantity!!, 0.0)
        assertEquals(110.0, lunch.constituents[0].servingUnitOptions.single().gramsPerUnit, 0.0)
        assertEquals("bowl", lunch.selectedServingUnit)
        assertEquals(1.0, lunch.selectedServingQuantity!!, 0.0)

        val emptyConstituents = entries.first { it.name == "Sample Dinner Item 1" }
        assertTrue(emptyConstituents.constituents.isEmpty())
        assertEquals("cup", emptyConstituents.selectedServingUnit)
    }

    @Test
    fun syncSampleImportsServingAndConstituents() {
        val json = ParityFixtures.readText("sync-sample.json")
        val result = SyncDocument.parse(json, ZoneOffset.UTC)
        assertTrue(result is SyncDocument.ParseResult.Success)
        val foods = (result as SyncDocument.ParseResult.Success).parsed.foodEntries.mapNotNull { it.entry }
        assertEquals(2, foods.size)
        val salad = foods.first { it.name == "Chicken salad" }
        assertEquals("bowl", salad.selectedServingUnit)
        assertEquals(2, salad.constituents.size)
        assertEquals("piece", salad.constituents[0].selectedServingUnit)
        val coffee = foods.first { it.name == "Black coffee" }
        assertTrue(coffee.constituents.isEmpty())
        assertEquals("cup", coffee.selectedServingUnit)
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
