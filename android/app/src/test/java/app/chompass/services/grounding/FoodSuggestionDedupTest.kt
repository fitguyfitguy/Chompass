package app.chompass.services.grounding

import app.chompass.data.buildSavedFoodIndex
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.models.NutrientSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The old Search-food sheet did no cross-source dedup at all, so the same food
 * arrived once per provider. These pin the collapse rules for the unified list.
 */
class FoodSuggestionDedupTest {
    private val now: Instant = Instant.parse("2024-06-10T12:00:00Z")

    private fun savedIndex(vararg names: String) = buildSavedFoodIndex(
        entries = names.map {
            FoodEntry(
                name = it,
                calories = 100,
                protein = 10.0,
                carbs = 10.0,
                fat = 5.0,
                timestamp = now,
                source = FoodSource.MANUAL,
                mealType = MealType.LUNCH.id,
            )
        },
        favorites = emptyList(),
    )

    private fun db(
        name: String,
        kind: NutrientSourceKind,
        id: String,
        brand: String? = null,
        score: Double = 0.8,
    ) = DatabaseSearchResult(
        sourceKind = kind,
        sourceId = id,
        name = name,
        brand = brand,
        caloriesPerServing = 120.0,
        matchScore = score,
    )

    @Test
    fun savedFoodAbsorbsTheDatabaseRowOfTheSameName() {
        val out = FoodSuggestionRanker.rank(
            query = "greek yogurt",
            saved = savedIndex("Greek Yogurt"),
            recipes = emptyList(),
            database = listOf(db("greek yogurt", NutrientSourceKind.USDA, "usda-1", score = 1.0)),
            now = now,
        )
        assertEquals(1, out.size)
        assertTrue(out.single() is FoodSuggestion.SavedFood)
    }

    @Test
    fun usdaWinsOverSwissAndOffForTheSameFood() {
        val out = FoodSuggestionRanker.rank(
            query = "yogurt",
            saved = emptyList(),
            recipes = emptyList(),
            database = listOf(
                db("Yogurt", NutrientSourceKind.OPEN_FOOD_FACTS, "off-1", score = 0.8),
                db("Yogurt", NutrientSourceKind.SWISS, "swiss-1", score = 0.8),
                db("Yogurt", NutrientSourceKind.USDA, "usda-1", score = 0.8),
            ),
            now = now,
        )
        assertEquals(1, out.size)
        assertEquals("db:USDA:usda-1", out.single().key)
    }

    @Test
    fun offBarcodeDuplicatesOfOneProductCollapse() {
        val out = FoodSuggestionRanker.rank(
            query = "skyr",
            saved = emptyList(),
            recipes = emptyList(),
            database = listOf(
                db("Skyr", NutrientSourceKind.OPEN_FOOD_FACTS, "3001", brand = "Arla", score = 0.7),
                db("Skyr", NutrientSourceKind.OPEN_FOOD_FACTS, "3002", brand = "Arla", score = 0.9),
            ),
            now = now,
        )
        assertEquals("db:OPEN_FOOD_FACTS:3002", out.single().key)
    }

    @Test
    fun aBetterOffHitSurvivesAFullOfflineLeg() {
        // searchOffline alone can return 12 rows, more than maxDatabase, and the
        // OFF leg is appended after them. The cap has to be applied by score or
        // Open Food Facts never reaches the list on a generic query.
        val offline = listOf(
            "plain", "greek", "strawberry", "vanilla", "banana",
            "coconut", "mango", "cherry", "lemon", "peach",
        ).mapIndexed { i, flavor ->
            db("Yogurt $flavor", NutrientSourceKind.USDA, "usda-$i", score = 0.3)
        }
        val out = FoodSuggestionRanker.rank(
            query = "yogurt",
            saved = emptyList(),
            recipes = emptyList(),
            database = offline + db("Yogurt Deluxe", NutrientSourceKind.OPEN_FOOD_FACTS, "off-1", score = 1.0),
            now = now,
        )
        assertEquals("db:OPEN_FOOD_FACTS:off-1", out.first().key)
    }

    @Test
    fun spellingSynonymsCollapseAcrossSources() {
        val out = FoodSuggestionRanker.rank(
            query = "yogurt",
            saved = savedIndex("Greek Yoghurt"),
            recipes = emptyList(),
            database = listOf(db("Greek yogurt", NutrientSourceKind.USDA, "usda-1")),
            now = now,
        )
        assertEquals(1, out.size)
        assertTrue(out.single() is FoodSuggestion.SavedFood)
    }

    @Test
    fun quantityPrefixedNamesStayDistinctIdentities() {
        val out = FoodSuggestionRanker.rank(
            query = "chicken breast",
            saved = savedIndex("200g Chicken Breast", "Chicken breast"),
            recipes = emptyList(),
            database = emptyList(),
            now = now,
        )
        // These are two different FoodEntry identities (favoriteKey is the raw
        // lowercased name) and QueryNormalizer keeps the fused "200g" token, so
        // they legitimately stay two rows. Collapsing them would merge foods the
        // rest of the app treats as separate.
        assertEquals(2, out.size)
        // The exact match still leads.
        assertEquals("Chicken breast", out.first().name)
    }
}
