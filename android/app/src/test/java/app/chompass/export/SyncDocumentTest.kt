package app.chompass.export

import kotlinx.serialization.json.jsonArray
import app.chompass.parity.ParityFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class SyncDocumentTest {
    @Test
    fun paritySampleParses() {
        val json = ParityFixtures.readText("sync-sample.json")
        val result = SyncDocument.parse(json, ZoneOffset.UTC)
        assertTrue("expected Success but was $result", result is SyncDocument.ParseResult.Success)
        val parsed = (result as SyncDocument.ParseResult.Success).parsed
        assertEquals(2, parsed.foodEntries.count { it.entry != null })
        assertEquals("Chicken salad", parsed.foodEntries.first { it.entry != null }.entry?.name)
        val salad = parsed.foodEntries.first { it.entry?.name == "Chicken salad" }.entry!!
        assertEquals("bowl", salad.selectedServingUnit)
        assertEquals(2, salad.constituents.size)
        assertEquals(90.0, salad.constituents[0].servingUnitOptions.single().gramsPerUnit, 0.0)
        val coffee = parsed.foodEntries.first { it.entry?.name == "Black coffee" }.entry!!
        assertTrue(coffee.constituents.isEmpty())
        assertEquals(1, parsed.weights.count { it.entry != null })
        assertEquals(1, parsed.water.count { it.entry != null })
        assertTrue(parsed.profile != null)
    }

    @Test
    fun mergeRawDocumentsKeepsBothMeals() {
        val phone = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.0"},
             "food_entries":[{"id":"b","updated_at":"2026-07-24T08:00:00Z","deleted_at":null,"name":"Oats","date":"2026-07-24","time":"08:00","meal_type":"breakfast","calories":300,"protein_g":10,"carbs_g":50,"fat_g":5}],
             "favorites":[],"weights":[],"body_fat":[],"measurements":[],"water":[],"recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val desktop = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.0"},
             "food_entries":[{"id":"l","updated_at":"2026-07-24T12:30:00Z","deleted_at":null,"name":"Salad","date":"2026-07-24","time":"12:30","meal_type":"lunch","calories":420,"protein_g":38,"carbs_g":12,"fat_g":22}],
             "favorites":[],"weights":[],"body_fat":[],"measurements":[],"water":[],"recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val local = (SyncDocument.parse(phone, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed.raw
        val remote = (SyncDocument.parse(desktop, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed.raw
        val merged = SyncDocument.mergeRawDocuments(local, remote)
        assertEquals(2, merged["food_entries"]!!.jsonArray.size)
    }
}
