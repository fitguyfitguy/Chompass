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
 * network source is exercised only through the injected [FoodDatabaseSearch]
 * seam — the real OFF client has its own MockWebServer suite
 * ([OpenFoodFactsSearchTest] et al.).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FoodDatabaseSearchTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun newSearch(
        prefs: PreferencesStore = PreferencesStore(context),
        offSearch: suspend (String) -> List<DatabaseSearchResult> = { emptyList() },
    ): FoodDatabaseSearch =
        FoodDatabaseSearch(
            prefs = prefs,
            usda = UsdaFoodIndex(context),
            swiss = SwissFoodIndex(context),
            offSearch = offSearch,
        )

    /** An Open Food Facts backend that is simply not answering. */
    private class OffUnreachable : java.io.IOException("open food facts unreachable")

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
    fun search_dropsSourcesTurnedOffInSettings() = runBlocking {
        val prefs = PreferencesStore(context)
        val search = newSearch(prefs = prefs)
        val both = setOf(FoodDatabaseSearch.Source.USDA, FoodDatabaseSearch.Source.SWISS)

        prefs.setFoodSearchUsdaEnabled(false)
        val withoutUsda = search.search("pork", sources = both)
        assertTrue("expected Swiss hits to survive, got ${withoutUsda.size}", withoutUsda.isNotEmpty())
        assertTrue(withoutUsda.none { it.sourceKind == NutrientSourceKind.USDA })

        // Every source off is a valid state: search falls back to the user's own
        // saved foods, which the ranker adds outside this class.
        prefs.setFoodSearchSwissEnabled(false)
        assertTrue(search.search("pork", sources = both).isEmpty())

        prefs.setFoodSearchUsdaEnabled(true)
        prefs.setFoodSearchSwissEnabled(true)
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
    fun search_deadOpenFoodFacts_stillReturnsOfflineRows() = runBlocking {
        // The whole point of the per-source isolation: Open Food Facts being
        // down is the common case (no signal, captive portal, OFF outage), and
        // it must cost the user nothing but the packaged products. The bundled
        // indexes are on-device, so their rows are unaffected.
        var offCalls = 0
        val search = newSearch(offSearch = { offCalls++; throw OffUnreachable() })
        val results = search.search(
            "pork ground",
            sources = setOf(
                FoodDatabaseSearch.Source.OPEN_FOOD_FACTS,
                FoodDatabaseSearch.Source.USDA,
                FoodDatabaseSearch.Source.SWISS,
            ),
        )
        assertEquals("the OFF leg should still have been attempted", 1, offCalls)
        assertTrue("expected offline hits despite a dead OFF, got ${results.size}", results.isNotEmpty())
        assertTrue(
            results.all {
                it.sourceKind == NutrientSourceKind.USDA || it.sourceKind == NutrientSourceKind.SWISS
            },
        )
        // Still ranked, not just concatenated: a failed leg must not leave the
        // survivors in fan-out order.
        val scores = results.map { it.matchScore }
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun searchOffline_neverTouchesOpenFoodFacts() = runBlocking {
        // The Add Food sheet's per-keystroke leg. It must stay on-device even
        // with the OFF source enabled in Settings, or typing would reach the
        // network before the user opted in.
        var offCalls = 0
        val search = newSearch(offSearch = { offCalls++; throw OffUnreachable() })
        val results = search.searchOffline("pork ground")
        assertEquals(0, offCalls)
        assertTrue("expected offline hits, got ${results.size}", results.isNotEmpty())
    }

    @Test
    fun searchOnline_deadOpenFoodFacts_isEmptyRatherThanThrowing() = runBlocking {
        // The sheet merges this leg's result into a list that already has rows
        // on screen; a throw here would take those down with it.
        val search = newSearch(offSearch = { throw OffUnreachable() })
        assertTrue(search.searchOnline("pork ground").isEmpty())
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
