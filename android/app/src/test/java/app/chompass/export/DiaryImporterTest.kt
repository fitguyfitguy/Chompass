package app.chompass.export

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DiaryImporterTest {
    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun acceptsFormat11WithMicronutrients() {
        val json = """
            {
              "export": {
                "app": "Fud AI",
                "format_version": "1.1",
                "date_range": { "start": "2026-07-20", "end": "2026-07-20" }
              },
              "days": [{
                "date": "2026-07-20",
                "totals": { "calories": 200, "protein_g": 22.0, "carbs_g": 0.0, "fat_g": 12.0 },
                "targets": { "calories": 2000, "protein_g": 150.0, "carbs_g": 200.0, "fat_g": 60.0 },
                "remaining": { "calories": 1800, "protein_g": 128.0, "carbs_g": 200.0, "fat_g": 48.0 },
                "meals": [{
                  "type": "lunch",
                  "items": [{
                    "name": "Salmon",
                    "quantity_g": 150.0,
                    "calories": 200,
                    "protein_g": 22.0,
                    "carbs_g": 0.0,
                    "fat_g": 12.0,
                    "sugar_g": null,
                    "added_sugar_g": null,
                    "fiber_g": 1.2,
                    "saturated_fat_g": 2.0,
                    "monounsaturated_fat_g": null,
                    "polyunsaturated_fat_g": null,
                    "cholesterol_mg": 55.0,
                    "sodium_mg": 50.0,
                    "potassium_mg": null,
                    "trans_fat_g": null,
                    "calcium_mg": null,
                    "iron_mg": null,
                    "magnesium_mg": null,
                    "zinc_mg": null,
                    "vitamin_a_mcg": null,
                    "vitamin_c_mg": null,
                    "vitamin_d_mcg": 10.5,
                    "vitamin_b12_mcg": null,
                    "vitamin_e_mg": null,
                    "vitamin_k_mcg": null,
                    "folate_mcg": null,
                    "omega3_g": 1.8,
                    "time": "12:30",
                    "source": "ai_estimated",
                    "note": "grilled"
                  }]
                }]
              }]
            }
        """.trimIndent()

        val result = DiaryImporter.parse(json, zone)
        assertTrue(result is DiaryImportResult.Success)
        val entry = (result as DiaryImportResult.Success).entries.single()
        assertEquals("Salmon", entry.name)
        assertEquals(200, entry.calories)
        assertEquals(22.0, entry.protein, 0.0)
        assertEquals(150.0, entry.servingSizeGrams)
        assertEquals(1.2, entry.fiber)
        assertEquals(50.0, entry.sodium)
        assertEquals(10.5, entry.vitaminD)
        assertEquals(1.8, entry.omega3)
        assertEquals(2.0, entry.saturatedFat)
        assertEquals(55.0, entry.cholesterol)
        assertEquals("grilled", entry.customNote)
        assertEquals(MealType.LUNCH, entry.mealType)
        assertEquals(FoodSource.TEXT_INPUT, entry.source)
        assertEquals(
            LocalDate.of(2026, 7, 20).atTime(12, 30).atZone(zone).toInstant(),
            entry.timestamp,
        )
    }

    @Test
    fun acceptsLegacyFormat10MacrosOnly() {
        val json = """
            {
              "export": {
                "app": "Chompass",
                "format_version": "1.0",
                "date_range": { "start": "2026-01-01", "end": "2026-01-01" }
              },
              "days": [{
                "date": "2026-01-01",
                "totals": { "calories": 100, "protein_g": 10.0, "carbs_g": 5.0, "fat_g": 2.0 },
                "targets": { "calories": 2000, "protein_g": 150.0, "carbs_g": 200.0, "fat_g": 60.0 },
                "remaining": { "calories": 1900, "protein_g": 140.0, "carbs_g": 195.0, "fat_g": 58.0 },
                "meals": [{
                  "type": "breakfast",
                  "items": [{
                    "name": "Oats",
                    "quantity_g": 80.0,
                    "calories": 100,
                    "protein_g": 10.0,
                    "carbs_g": 5.0,
                    "fat_g": 2.0,
                    "time": "08:00",
                    "source": "manually_edited",
                    "note": null
                  }]
                }]
              }]
            }
        """.trimIndent()
        val result = DiaryImporter.parse(json, zone)
        assertTrue(result is DiaryImportResult.Success)
        val entry = (result as DiaryImportResult.Success).entries.single()
        assertEquals("Oats", entry.name)
        assertEquals(100, entry.calories)
        assertEquals(10.0, entry.protein, 0.0)
        assertEquals(5.0, entry.carbs, 0.0)
        assertEquals(2.0, entry.fat, 0.0)
        assertEquals(80.0, entry.servingSizeGrams)
        assertEquals(MealType.BREAKFAST, entry.mealType)
        assertEquals(FoodSource.MANUAL, entry.source)
        assertEquals(null, entry.fiber)
        assertEquals(null, entry.sodium)
    }

    @Test
    fun acceptsNoFudAppStampFormat11() {
        val json = """
            {
              "export": {
                "app": "NoFUD",
                "format_version": "1.1",
                "date_range": { "start": "2026-07-20", "end": "2026-07-20" }
              },
              "days": [{
                "date": "2026-07-20",
                "totals": { "calories": 200, "protein_g": 22.0, "carbs_g": 0.0, "fat_g": 12.0 },
                "targets": { "calories": 2000, "protein_g": 150.0, "carbs_g": 200.0, "fat_g": 60.0 },
                "remaining": { "calories": 1800, "protein_g": 128.0, "carbs_g": 200.0, "fat_g": 48.0 },
                "meals": [{
                  "type": "lunch",
                  "items": [{
                    "name": "Salmon",
                    "quantity_g": 150.0,
                    "calories": 200,
                    "protein_g": 22.0,
                    "carbs_g": 0.0,
                    "fat_g": 12.0,
                    "fiber_g": 1.2,
                    "time": "12:30",
                    "source": "ai_estimated",
                    "note": null
                  }]
                }]
              }]
            }
        """.trimIndent()
        val result = DiaryImporter.parse(json, zone)
        assertTrue(result is DiaryImportResult.Success)
        val entry = (result as DiaryImportResult.Success).entries.single()
        assertEquals("Salmon", entry.name)
        assertEquals(1.2, entry.fiber)
        assertEquals(MealType.LUNCH, entry.mealType)
    }

    @Test
    fun rejectsUnknownFormatVersion() {
        val json = """
            {
              "export": { "app": "Fud AI", "format_version": "2.0",
                "date_range": { "start": "2026-01-01", "end": "2026-01-01" } },
              "days": []
            }
        """.trimIndent()
        val result = DiaryImporter.parse(json, zone)
        assertTrue(result is DiaryImportResult.UnsupportedFormat)
        assertTrue(
            (result as DiaryImportResult.UnsupportedFormat).reason.contains("format_version"),
        )
    }

    @Test
    fun mapsBarcodeAndGroundedSources() {
        val json = """
            {
              "export": {
                "app": "Chompass",
                "format_version": "1.1",
                "date_range": { "start": "2026-07-20", "end": "2026-07-20" }
              },
              "days": [{
                "date": "2026-07-20",
                "totals": { "calories": 0, "protein_g": 0.0, "carbs_g": 0.0, "fat_g": 0.0 },
                "targets": { "calories": 0, "protein_g": 0.0, "carbs_g": 0.0, "fat_g": 0.0 },
                "remaining": { "calories": 0, "protein_g": 0.0, "carbs_g": 0.0, "fat_g": 0.0 },
                "meals": [{
                  "type": "snack",
                  "items": [
                    {
                      "name": "Bar", "calories": 1, "protein_g": 0.0, "carbs_g": 0.0, "fat_g": 0.0,
                      "time": "10:00", "source": "barcode", "note": null
                    },
                    {
                      "name": "Grounded", "calories": 1, "protein_g": 0.0, "carbs_g": 0.0, "fat_g": 0.0,
                      "time": "11:00", "source": "grounded", "note": null
                    }
                  ]
                }]
              }]
            }
        """.trimIndent()

        val entries = (DiaryImporter.parse(json, zone) as DiaryImportResult.Success).entries
        assertEquals(FoodSource.BARCODE, entries[0].source)
        assertEquals(FoodSource.GROUNDED, entries[1].source)
    }

    @Test
    fun roundTripPreservesMicronutrients() {
        val original = FoodEntry(
            name = "Salmon",
            calories = 200,
            protein = 22.0,
            carbs = 0.0,
            fat = 12.0,
            source = FoodSource.MANUAL,
            mealType = MealType.LUNCH,
            timestamp = Instant.parse("2026-07-20T12:00:00Z"),
            fiber = 1.2,
            sodium = 50.0,
            vitaminD = 10.5,
            servingSizeGrams = 150.0,
        )
        val exported = DiaryExporter.build(
            entries = listOf(original),
            start = LocalDate.of(2026, 7, 20),
            end = LocalDate.of(2026, 7, 20),
            format = DiaryFormat.JSON,
            profile = null,
            mealDisplay = { it.name },
        ) ?: error("expected export")

        assertTrue(exported.second.contains("\"format_version\": \"1.1\""))
        val imported = DiaryImporter.parse(exported.second, ZoneId.systemDefault())
        assertTrue(imported is DiaryImportResult.Success)
        val entry = (imported as DiaryImportResult.Success).entries.single()
        assertEquals("Salmon", entry.name)
        assertEquals(1.2, entry.fiber)
        assertEquals(50.0, entry.sodium)
        assertEquals(10.5, entry.vitaminD)
        assertEquals(FoodSource.MANUAL, entry.source)
    }
}
