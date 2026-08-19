package app.chompass.services

import app.chompass.models.FoodSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SampleDataGeneratorsTest {
    private val today = LocalDate.of(2026, 8, 19)

    @Test
    fun fullFoodEntries_isDeterministicAndMixed() {
        val a = SampleDataGenerators.fullFoodEntries(totalDays = 365, today = today)
        val b = SampleDataGenerators.fullFoodEntries(totalDays = 365, today = today)
        assertEquals(a.size, b.size)
        assertEquals(a.map { it.name to it.calories }, b.map { it.name to it.calories })
        assertTrue(a.size in 1_000..1_600)
        assertTrue(a.any { it.source == FoodSource.TEXT_INPUT })
        assertTrue(a.any { it.source == FoodSource.MANUAL })
        assertTrue(a.any { it.source == FoodSource.BARCODE })
        assertTrue(a.any { it.source == FoodSource.SNAP_FOOD })
        assertTrue(a.any { it.fiber != null && it.sodium != null })
        assertTrue(a.any { it.recipeLogId != null })
        val recipeGroups = a.mapNotNull { it.recipeLogId }.distinct()
        assertTrue(recipeGroups.isNotEmpty())
        assertTrue(recipeGroups.all { id -> a.count { it.recipeLogId == id } >= 2 })
    }

    @Test
    fun waterEntries_coverMostDays() {
        val water = SampleDataGenerators.waterEntries(totalDays = 365, today = today)
        assertTrue(water.size in 1_200..3_000)
        assertTrue(water.all { it.milliliters in 200..600 })
        val days = water.map { it.date.toString().take(10) }.toSet()
        assertTrue(days.size in 300..365)
    }

    @Test
    fun sampleRecipes_areMultiIngredient() {
        val recipes = SampleDataGenerators.sampleRecipes(now = Instant.parse("2026-08-19T12:00:00Z"))
        assertEquals(4, recipes.size)
        assertTrue(recipes.all { it.ingredients.size >= 3 })
        assertTrue(recipes.all { it.totalCalories > 0 })
    }

    @Test
    fun sampleFavorites_areDistinctCopies() {
        val favs = SampleDataGenerators.sampleFavorites(now = Instant.parse("2026-08-19T12:00:00Z"))
        assertEquals(10, favs.size)
        assertEquals(favs.size, favs.map { it.favoriteKey }.toSet().size)
    }

    @Test
    fun sampleChat_alternatesRoles() {
        val chat = SampleDataGenerators.sampleChat(now = Instant.parse("2026-08-19T12:00:00Z"))
        assertEquals(16, chat.size)
        assertTrue(chat.size in 10..20)
        assertEquals(8, chat.count { it.role.name == "USER" })
        assertEquals(8, chat.count { it.role.name == "ASSISTANT" })
    }

    @Test
    fun legacyFoodEntries_unchangedShape() {
        val foods = SampleDataGenerators.foodEntries(totalDays = 30, today = today)
        assertTrue(foods.isNotEmpty())
        assertTrue(foods.all { it.source == FoodSource.TEXT_INPUT })
        assertTrue(foods.none { it.fiber != null })
    }
}
