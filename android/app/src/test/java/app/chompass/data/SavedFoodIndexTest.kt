package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.services.grounding.SuggestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SavedFoodIndexTest {
    private fun entry(
        name: String,
        timestamp: Instant = Instant.parse("2024-06-01T12:00:00Z"),
        calories: Int = 100,
    ) = FoodEntry(
        name = name,
        calories = calories,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        timestamp = timestamp,
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH.id,
    )

    @Test
    fun collapsesByIdentityAndKeepsNewestTemplate() {
        val index = buildSavedFoodIndex(
            entries = listOf(
                entry("Apple", Instant.parse("2024-06-01T08:00:00Z"), calories = 80),
                entry("apple", Instant.parse("2024-06-01T14:00:00Z"), calories = 120),
                entry("Banana", Instant.parse("2024-06-01T10:00:00Z")),
            ),
            favorites = emptyList(),
        )
        assertEquals(2, index.size)
        val apple = index.first { it.template.favoriteKey == "apple" }
        assertEquals(120, apple.template.calories)
        assertEquals(2, apple.logCount)
    }

    @Test
    fun favoritesAreIncludedEvenWhenNeverLogged() {
        val index = buildSavedFoodIndex(
            entries = listOf(entry("Apple")),
            favorites = listOf(entry("Protein Shake")),
        )
        val shake = index.first { it.template.name == "Protein Shake" }
        assertEquals(SuggestionKind.FAVORITE, shake.kind)
        assertEquals(0, shake.logCount)
    }

    @Test
    fun favoriteKindWinsOverLogCount() {
        val index = buildSavedFoodIndex(
            entries = List(5) { entry("Oatmeal", Instant.parse("2024-06-0${it + 1}T12:00:00Z")) },
            favorites = listOf(entry("Oatmeal")),
        )
        assertEquals(SuggestionKind.FAVORITE, index.single().kind)
    }

    @Test
    fun frequentThresholdSplitsRecentFromFrequent() {
        val index = buildSavedFoodIndex(
            entries = listOf(
                entry("Oatmeal", Instant.parse("2024-06-01T12:00:00Z")),
                entry("Oatmeal", Instant.parse("2024-06-02T12:00:00Z")),
                entry("Oatmeal", Instant.parse("2024-06-03T12:00:00Z")),
                entry("Toast", Instant.parse("2024-06-03T12:00:00Z")),
            ),
            favorites = emptyList(),
        )
        assertEquals(SuggestionKind.FREQUENT, index.first { it.template.name == "Oatmeal" }.kind)
        assertEquals(SuggestionKind.RECENT, index.first { it.template.name == "Toast" }.kind)
    }

    @Test
    fun precomputesNormalizedNameAndTokens() {
        val index = buildSavedFoodIndex(
            entries = listOf(entry("200g Greek Yoghurt")),
            favorites = emptyList(),
        )
        val row = index.single()
        // The shared QueryNormalizer applies its synonym map (yoghurt -> yogurt)
        // and drops standalone unit tokens, but a fused quantity+unit like
        // "200g" survives tokenization — documenting that here so a future
        // normalizer change shows up as a deliberate diff, not a surprise.
        assertEquals("200g greek yogurt", row.normalizedName)
        assertTrue("yogurt" in row.nameTokens)
    }

    @Test
    fun skipsBlankIdentities() {
        val index = buildSavedFoodIndex(entries = listOf(entry("   ")), favorites = emptyList())
        assertTrue(index.isEmpty())
    }
}
