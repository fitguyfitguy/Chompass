package app.chompass.services.grounding

import app.chompass.data.SavedFoodIndexEntry
import app.chompass.data.buildSavedFoodIndex
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.models.NutrientSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FoodSuggestionRankerTest {
    private val now: Instant = Instant.parse("2024-06-10T12:00:00Z")

    private fun entry(name: String, timestamp: Instant = now, calories: Int = 100) = FoodEntry(
        name = name,
        calories = calories,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        timestamp = timestamp,
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH.id,
    )

    private fun saved(vararg entries: FoodEntry): List<SavedFoodIndexEntry> =
        buildSavedFoodIndex(entries = entries.toList(), favorites = emptyList())

    private fun db(
        name: String,
        kind: NutrientSourceKind = NutrientSourceKind.USDA,
        id: String = name,
        brand: String? = null,
        score: Double = 0.8,
        incompleteEnergy: Boolean = false,
    ) = DatabaseSearchResult(
        sourceKind = kind,
        sourceId = id,
        name = name,
        brand = brand,
        caloriesPerServing = 120.0,
        incompleteEnergy = incompleteEnergy,
        matchScore = score,
    )

    @Test
    fun blankQueryReturnsNothing() {
        assertTrue(
            FoodSuggestionRanker.rank("  ", saved(entry("Apple")), emptyList(), emptyList(), now).isEmpty(),
        )
    }

    @Test
    fun savedFoodOutranksAStrongerScoringDatabaseHit() {
        val out = FoodSuggestionRanker.rank(
            query = "greek yogurt",
            saved = saved(entry("Greek Yogurt")),
            recipes = emptyList(),
            // A perfect provider score still must not beat the user's own food.
            database = listOf(db("Greek Yogurt Plain", score = 1.0)),
            now = now,
        )
        assertTrue(out.first() is FoodSuggestion.SavedFood)
    }

    @Test
    fun exactMatchOutranksPrefixOutranksOverlap() {
        val out = FoodSuggestionRanker.rank(
            query = "oat",
            saved = saved(
                entry("Oat"),
                entry("Oatmeal"),
                entry("Breakfast with oat and milk"),
            ),
            recipes = emptyList(),
            database = emptyList(),
            now = now,
        )
        assertEquals(listOf("Oat", "Oatmeal", "Breakfast with oat and milk"), out.map { it.name })
    }

    @Test
    fun recencyBreaksTiesBetweenEquallyLexicalSavedFoods() {
        val out = FoodSuggestionRanker.rank(
            query = "apple",
            saved = buildSavedFoodIndex(
                entries = listOf(
                    entry("Apple", now.minusSeconds(120L * 24 * 3600)),
                    entry("apple pie", now),
                ),
                favorites = emptyList(),
            ),
            recipes = emptyList(),
            database = emptyList(),
            now = now,
        )
        // "Apple" is an exact match and still wins: recency separates equally
        // good matches, it never overturns a better one.
        assertEquals("Apple", out.first().name)
    }

    @Test
    fun weakMatchesAreDroppedRatherThanPaddingTheList() {
        val out = FoodSuggestionRanker.rank(
            query = "chicken",
            saved = saved(entry("Banana bread")),
            recipes = emptyList(),
            database = emptyList(),
            now = now,
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun databaseHitsAreCappedIndependentlyOfSavedFoods() {
        val out = FoodSuggestionRanker.rank(
            query = "yogurt",
            saved = emptyList(),
            recipes = emptyList(),
            // Distinct normalized names: rows that normalize alike are meant to
            // collapse, which would mask the cap.
            database = listOf("plain", "strained", "skyr", "kefir", "quark", "labneh").map {
                db("Yogurt $it", kind = NutrientSourceKind.USDA, id = "usda-$it", score = 0.9)
            },
            now = now,
            maxDatabase = 4,
        )
        assertEquals(4, out.size)
    }

    @Test
    fun incompleteEnergyLosesTheOffBarcodeTieBreak() {
        val out = FoodSuggestionRanker.rank(
            query = "yogurt",
            saved = emptyList(),
            recipes = emptyList(),
            database = listOf(
                db("Yogurt", NutrientSourceKind.OPEN_FOOD_FACTS, id = "111", brand = "Acme", score = 0.95, incompleteEnergy = true),
                db("Yogurt", NutrientSourceKind.OPEN_FOOD_FACTS, id = "222", brand = "Acme", score = 0.60),
            ),
            now = now,
        )
        val hit = out.single() as FoodSuggestion.DatabaseHit
        assertEquals("222", hit.result.sourceId)
    }

    /**
     * A saved food used to fall below [FoodSuggestionRanker.MIN_LEXICAL] as soon
     * as the query was more than one word and not a literal substring of its
     * name, while database rows — whose names are verbose enough to contain the
     * typed phrase — survived. The Add Food count then read as if only the food
     * databases had been searched.
     */
    @Test
    fun everyQueryWordPresentMatchesEvenOutOfOrder() {
        val saved = saved(entry("Chicken rice bowl"))
        assertTrue(
            FoodSuggestionRanker.rank("chicken bowl", saved, emptyList(), emptyList(), now)
                .single() is FoodSuggestion.SavedFood,
        )
        assertTrue(
            FoodSuggestionRanker.rank("bowl chicken", saved, emptyList(), emptyList(), now)
                .single() is FoodSuggestion.SavedFood,
        )
        // A word that is not in the name at all still rules the row out.
        assertTrue(
            FoodSuggestionRanker.rank("chicken waffle", saved, emptyList(), emptyList(), now).isEmpty(),
        )
    }

    @Test
    fun deadOnlineLegStillLeavesSavedFoodsAndOfflineRows() {
        // What the Add Food sheet sees when Open Food Facts is unreachable (or
        // simply not opted into): the online leg contributes nothing, and the
        // list is still the user's own foods followed by the bundled databases.
        val saved = saved(entry("Greek yogurt"))
        val offlineOnly = listOf(
            db("Yogurt, greek, plain", NutrientSourceKind.USDA, id = "usda-1", score = 0.9),
            db("Greek yogurt, natural", NutrientSourceKind.SWISS, id = "swiss-1", score = 0.7),
        )
        val out = FoodSuggestionRanker.rank("greek yogurt", saved, emptyList(), offlineOnly, now)

        assertTrue("expected the saved food first", out.first() is FoodSuggestion.SavedFood)
        // Both bundled sources survive. Their order between themselves is the
        // ranker's business (lexical fit, then the source tie-break), not this
        // test's — what matters is that neither went missing with the OFF leg.
        val databaseKinds = out.filterIsInstance<FoodSuggestion.DatabaseHit>()
            .map { it.result.sourceKind }
            .toSet()
        assertEquals(
            setOf(NutrientSourceKind.USDA, NutrientSourceKind.SWISS),
            databaseKinds,
        )
        // Identical to what the same query produces before the online leg would
        // have landed: a dead OFF is indistinguishable from one still in flight.
        assertEquals(
            FoodSuggestionRanker.rank("greek yogurt", saved, emptyList(), emptyList(), now)
                .filterIsInstance<FoodSuggestion.SavedFood>()
                .map { it.key },
            out.filterIsInstance<FoodSuggestion.SavedFood>().map { it.key },
        )
    }

    @Test
    fun rankingIsDeterministicForIdenticalScores() {
        val database = listOf(
            db("Yogurt", NutrientSourceKind.SWISS, id = "swiss-1", score = 0.8),
            db("Yogurt", NutrientSourceKind.USDA, id = "usda-1", score = 0.8),
        )
        val first = FoodSuggestionRanker.rank("yogurt", emptyList(), emptyList(), database, now)
        val second = FoodSuggestionRanker.rank("yogurt", emptyList(), emptyList(), database.reversed(), now)
        assertEquals(first.map { it.key }, second.map { it.key })
        // USDA sorts ahead of Swiss on the source tie-break.
        assertEquals("db:USDA:usda-1", first.first().key)
    }
}
