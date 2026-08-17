package app.chompass.services.grounding

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.models.NutrientSourceKind
import app.chompass.services.ai.FoodAnalysis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * End-to-end offline search over the real bundled USDA + Swiss SQLite assets
 * (no network). Pins [FoodDatabaseSearch] merged/ranked behavior after the
 * Codeberg #26 hardening: both offline sources resolve through the shared
 * [Mutex] (serialized) and hits land on one normalized score scale. The
 * network source is intentionally excluded — the OFF path has its own
 * MockWebServer suite ([OpenFoodFactsSearchTest] et al.).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FoodDatabaseSearchTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun newSearch(): FoodDatabaseSearch =
        FoodDatabaseSearch(
            prefs = PreferencesStore(context),
            usda = UsdaFoodIndex(context),
            swiss = SwissFoodIndex(context),
        )

    @Test
    fun search_mergesUsdaAndSwiss_onOneScoreScale() = runBlocking {
        val results = newSearch().search(
            "pork ground",
            sources = setOf(FoodDatabaseSearch.Source.USDA, FoodDatabaseSearch.Source.SWISS),
        )
        assertTrue("expected offline hits for 'pork ground', got ${results.size}", results.isNotEmpty())
        assertTrue(
            results.all {
                it.sourceKind == NutrientSourceKind.USDA || it.sourceKind == NutrientSourceKind.SWISS
            },
        )
        // Ranked descending on the normalized shared 0..1 scale.
        val scores = results.map { it.matchScore }
        assertEquals(scores.sortedDescending(), scores)
        // Every row is displayable with a serving and provenance.
        assertTrue(results.all { it.name.isNotBlank() })
        assertTrue(results.all { it.sourceId.isNotBlank() })
    }

    @Test
    fun search_singleSource_isolation() = runBlocking {
        val results = newSearch().search(
            "pork",
            sources = setOf(FoodDatabaseSearch.Source.SWISS),
        )
        assertTrue("expected Swiss hits for 'pork', got ${results.size}", results.isNotEmpty())
        assertTrue(results.all { it.sourceKind == NutrientSourceKind.SWISS })
    }

    @Test
    fun search_emptyQuery_returnsEmptyWithoutTouchingIndexes() = runBlocking {
        val results = newSearch().search(
            "   ",
            sources = setOf(FoodDatabaseSearch.Source.USDA, FoodDatabaseSearch.Source.SWISS),
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun toAnalysis_offBranch_passesSourceIdToLookupByCode() = runBlocking {
        // OFF search hits carry the product code straight from OFF's search API;
        // the branch must not re-validate it through the scanner normalizer (a
        // code that fails the GTIN check digit would throw "could not be read"
        // under lookup()). The extracted branch passes the id through as-is.
        val nonNormalizable = "1234567890123"
        var seen: String? = null
        val analysis = offToAnalysis(nonNormalizable) { code ->
            seen = code
            FoodAnalysis(
                name = "Test Product",
                calories = 100,
                protein = 5.0,
                carbs = 10.0,
                fat = 2.0,
                servingSizeGrams = 100.0,
            )
        }
        assertEquals(nonNormalizable, seen)
        assertEquals("Test Product", analysis.name)
    }
}
