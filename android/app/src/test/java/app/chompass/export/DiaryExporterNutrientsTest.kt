package app.chompass.export

import app.chompass.models.FoodConstituent
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DiaryExporterNutrientsTest {
    @Test
    fun jsonExportIncludesMicronutrients() {
        val entry = FoodEntry(
            name = "Salmon",
            calories = 200,
            protein = 22.0,
            carbs = 0.0,
            fat = 12.0,
            source = FoodSource.MANUAL,
            mealType = MealType.LUNCH.id,
            timestamp = Instant.parse("2026-07-20T12:00:00Z"),
            fiber = 1.2,
            sodium = 50.0,
            vitaminD = 10.5,
            caffeine = 95.0,
        )
        val result = DiaryExporter.build(
            entries = listOf(entry),
            start = java.time.LocalDate.of(2026, 7, 20),
            end = java.time.LocalDate.of(2026, 7, 20),
            format = DiaryFormat.JSON,
            profile = null,
            mealDisplay = { it },
        ) ?: error("expected export")

        val content = result.second
        assertTrue(content.contains("\"format_version\": \"1.5\""))
        assertTrue(content.contains("\"fiber_g\": 1.2"))
        assertTrue(content.contains("\"sodium_mg\": 50"))
        assertTrue(content.contains("\"vitamin_d_mcg\": 10.5"))
        assertTrue(content.contains("\"caffeine_mg\": 95"))
    }

    @Test
    fun jsonExportIncludesConstituentMicronutrients() {
        val entry = FoodEntry(
            name = "Chicken bowl",
            calories = 520,
            protein = 42.0,
            carbs = 40.0,
            fat = 18.0,
            source = FoodSource.MANUAL,
            mealType = MealType.LUNCH.id,
            timestamp = Instant.parse("2026-07-20T12:00:00Z"),
            constituents = listOf(
                FoodConstituent(
                    name = "Chicken",
                    calories = 280,
                    protein = 32.0,
                    carbs = 0.0,
                    fat = 12.0,
                    servingSizeGrams = 150.0,
                    saturatedFat = 4.5,
                    cholesterol = 145.0,
                    sodium = 320.0,
                    vitaminB12 = 0.6,
                    caffeine = 12.0,
                ),
            ),
        )
        val result = DiaryExporter.build(
            entries = listOf(entry),
            start = java.time.LocalDate.of(2026, 7, 20),
            end = java.time.LocalDate.of(2026, 7, 20),
            format = DiaryFormat.JSON,
            profile = null,
            mealDisplay = { it },
        ) ?: error("expected export")

        val content = result.second
        assertTrue(content.contains("\"format_version\": \"1.5\""))
        assertTrue(content.contains("\"saturated_fat_g\": 4.5"))
        assertTrue(content.contains("\"cholesterol_mg\": 145"))
        assertTrue(content.contains("\"vitamin_b12_mcg\": 0.6"))
        assertTrue(content.contains("\"caffeine_mg\": 12"))
    }
}
