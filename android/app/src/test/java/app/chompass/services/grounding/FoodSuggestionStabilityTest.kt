package app.chompass.services.grounding

import app.chompass.data.buildSavedFoodIndex
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.models.NutrientSourceKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The suggestion list publishes three times per query — local index, then the
 * offline databases, then Open Food Facts. The local prefix must be byte-stable
 * across all three or the rows the user is reaching for shuffle under their
 * finger. This is what the saved-food score prior buys us.
 */
class FoodSuggestionStabilityTest {
    private val now: Instant = Instant.parse("2024-06-10T12:00:00Z")

    private fun entry(name: String, timestamp: Instant = now) = FoodEntry(
        name = name,
        calories = 100,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        timestamp = timestamp,
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH.id,
    )

    private fun db(name: String, kind: NutrientSourceKind, id: String, score: Double) =
        DatabaseSearchResult(
            sourceKind = kind,
            sourceId = id,
            name = name,
            caloriesPerServing = 120.0,
            matchScore = score,
        )

    @Test
    fun localPrefixIsIdenticalAcrossAllThreePublishes() {
        val saved = buildSavedFoodIndex(
            entries = listOf(
                entry("Yogurt bowl", now.minusSeconds(3600)),
                entry("Greek yogurt", now.minusSeconds(7200)),
                entry("Yogurt drink", now.minusSeconds(10800)),
            ),
            favorites = emptyList(),
        )
        val offline = listOf(
            db("Yogurt, plain", NutrientSourceKind.USDA, "usda-1", 1.0),
            db("Yogurt nature", NutrientSourceKind.SWISS, "swiss-1", 1.0),
        )
        val online = listOf(db("Yogurt Deluxe", NutrientSourceKind.OPEN_FOOD_FACTS, "off-1", 1.0))

        val localOnly = FoodSuggestionRanker.rank("yogurt", saved, emptyList(), emptyList(), now)
        val withOffline = FoodSuggestionRanker.rank("yogurt", saved, emptyList(), offline, now)
        val withEverything = FoodSuggestionRanker.rank("yogurt", saved, emptyList(), offline + online, now)

        val localKeys = localOnly.map { it.key }
        assertEquals(3, localKeys.size)
        assertEquals(localKeys, withOffline.take(localKeys.size).map { it.key })
        assertEquals(localKeys, withEverything.take(localKeys.size).map { it.key })
    }

    @Test
    fun laterLegsOnlyAppend() {
        val saved = buildSavedFoodIndex(entries = listOf(entry("Oatmeal")), favorites = emptyList())
        val offline = listOf(db("Oats, rolled", NutrientSourceKind.USDA, "usda-1", 0.9))
        val before = FoodSuggestionRanker.rank("oat", saved, emptyList(), emptyList(), now)
        val after = FoodSuggestionRanker.rank("oat", saved, emptyList(), offline, now)
        assertEquals(before.map { it.key }, after.take(before.size).map { it.key })
        assertEquals(before.size + 1, after.size)
    }
}
