package app.chompass.export

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
            mealType = MealType.LUNCH,
            timestamp = Instant.parse("2026-07-20T12:00:00Z"),
            fiber = 1.2,
            sodium = 50.0,
            vitaminD = 10.5,
        )
        val result = DiaryExporter.build(
            entries = listOf(entry),
            start = java.time.LocalDate.of(2026, 7, 20),
            end = java.time.LocalDate.of(2026, 7, 20),
            format = DiaryFormat.JSON,
            profile = null,
            mealDisplay = { it.name },
        ) ?: error("expected export")

        val content = result.second
        assertTrue(content.contains("\"format_version\": \"1.1\""))
        assertTrue(content.contains("\"fiber_g\": 1.2"))
        assertTrue(content.contains("\"sodium_mg\": 50"))
        assertTrue(content.contains("\"vitamin_d_mcg\": 10.5"))
    }
}
