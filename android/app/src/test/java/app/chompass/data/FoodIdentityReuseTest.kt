package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodGroundingProvenance
import app.chompass.models.FoodProductMetadata
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * Codeberg #98, scan identity reuse: a re-encountered saved food (same
 * barcode, or the analysis name *is* a saved identity) merges into that
 * identity instead of forking into "Name (2)", and a merged scan/AI save
 * keeps a persisted favorite's macros in step with the newest serving
 * snapshot. Manual entries keep disambiguating — a different food typed under
 * a taken name stays distinct there (maintainer decision).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FoodIdentityReuseTest {
    private fun prefs() = PreferencesStore(RuntimeEnvironment.getApplication())

    private fun entry(
        name: String,
        id: UUID = UUID.randomUUID(),
        calories: Int = 100,
        protein: Double = 10.0,
        carbs: Double = 20.0,
        fat: Double = 5.0,
        timestamp: Instant = Instant.parse("2026-08-01T12:00:00Z"),
        grounding: FoodGroundingProvenance? = null,
        productMetadata: FoodProductMetadata? = null,
    ) = FoodEntry(
        id = id,
        name = name,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        timestamp = timestamp,
        source = FoodSource.BARCODE,
        mealType = MealType.LUNCH.id,
        grounding = grounding,
        productMetadata = productMetadata,
    )

    @Before
    fun setUp() = runBlocking {
        // DataStore is a process-wide singleton: start every test clean.
        val prefs = prefs()
        prefs.setFavoriteFoodEntries(emptyList())
        prefs.setFavoriteKeys(emptySet())
        prefs.replaceAllFoodEntries(emptyList())
    }

    @Test
    fun `barcode match reuses the saved identity`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val scanned = entry(
            "Snickers",
            grounding = FoodGroundingProvenance(sourceId = "40012345"),
            productMetadata = FoodProductMetadata(barcode = "40012345"),
        )
        prefs.replaceAllFoodEntries(listOf(scanned))

        assertEquals(scanned.id, repo.savedTemplateFor("Snickers", "40012345")?.id)
        assertEquals("Snickers", repo.savedOrDisambiguatedName("Snickers", "40012345"))
    }

    @Test
    fun `barcode match wins over the name leg and survives renames`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        // One product renamed between scans: two identities, one barcode.
        val old = entry(
            "Chocolate Bar",
            timestamp = Instant.parse("2026-08-01T12:00:00Z"),
            grounding = FoodGroundingProvenance(sourceId = "40012345"),
        )
        val renamed = entry(
            "Choco Riegel",
            timestamp = Instant.parse("2026-08-02T12:00:00Z"),
            grounding = FoodGroundingProvenance(sourceId = "40012345"),
        )
        val other = entry("Snickers", timestamp = Instant.parse("2026-08-03T12:00:00Z"))
        prefs.replaceAllFoodEntries(listOf(old, renamed, other))

        // The name leg alone would pick "Snickers"; the newest barcode
        // identity wins, and the raw name is kept without a suffix.
        assertEquals("Choco Riegel", repo.savedTemplateFor("Snickers", "40012345")?.name)
        assertEquals("Snickers", repo.savedOrDisambiguatedName("Snickers", "40012345"))
    }

    @Test
    fun `product metadata barcode matches without grounding`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        prefs.replaceAllFoodEntries(
            listOf(entry("Oat Bar", productMetadata = FoodProductMetadata(barcode = "778899"))),
        )

        assertEquals("oat bar", repo.savedTemplateFor("Müsliriegel", "778899")?.favoriteKey)
    }

    @Test
    fun `analysis-path name match reuses identity without suffix`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        prefs.replaceAllFoodEntries(listOf(entry("Oatmeal", calories = 250)))

        // Before #98 this suffixed to "Oatmeal (2)" and forked the food.
        assertEquals("oatmeal", repo.savedTemplateFor("Oatmeal", null)?.favoriteKey)
        assertEquals("Oatmeal", repo.savedOrDisambiguatedName("Oatmeal", null))
        // Casing of the requested name is preserved (as with suffixing); only
        // surrounding whitespace is trimmed away.
        assertEquals("OATMEAL", repo.savedOrDisambiguatedName("  OATMEAL ", null))
    }

    @Test
    fun `brand-new analysis name stays templateless and unsuffixed`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        prefs.replaceAllFoodEntries(listOf(entry("Oatmeal")))

        assertNull(repo.savedTemplateFor("Kohlrabi Sticks", null))
        assertEquals("Kohlrabi Sticks", repo.savedOrDisambiguatedName("Kohlrabi Sticks", null))
    }

    @Test
    fun `manual same-name entries keep suffixing`() {
        // The unchanged manual contract (saveManualEntry, meal resolveName,
        // edited confirm names): a different food under a taken name forks.
        assertEquals("Oatmeal (2)", disambiguateFoodName("Oatmeal", setOf("oatmeal")))
        assertEquals("Oatmeal (3)", disambiguateFoodName("Oatmeal", setOf("oatmeal", "oatmeal (2)")))
    }

    @Test
    fun `savedFoodTemplates keeps newest diary row per identity then unlogged favorites`() {
        val older = entry("Oats", calories = 150, timestamp = Instant.parse("2026-08-01T12:00:00Z"))
        val newer = entry("Oats", calories = 300, timestamp = Instant.parse("2026-08-02T12:00:00Z"))
        val unloggedFavorite = entry("Protein Powder", timestamp = Instant.parse("2026-07-01T12:00:00Z"))
        val loggedFavorite = entry("Oats", calories = 999, timestamp = Instant.parse("2026-08-03T12:00:00Z"))

        val templates = savedFoodTemplates(listOf(older, newer), listOf(unloggedFavorite, loggedFavorite))

        // The diary row wins for a logged identity; never-logged favorites
        // still join — the buildSavedFoodIndex collapse.
        assertEquals(300, templates.first { it.name == "Oats" }.calories)
        assertEquals(setOf("oats", "protein powder"), templates.map { it.favoriteKey }.toSet())
    }

    @Test
    fun `mergesWithSavedFood matches trimmed case-insensitive identity only`() {
        val oats = entry("Oatmeal")
        assertTrue(mergesWithSavedFood("Oatmeal", oats))
        assertTrue(mergesWithSavedFood("  OATMEAL ", oats))
        assertFalse(mergesWithSavedFood("Oatmeal (2)", oats))
        assertFalse(mergesWithSavedFood("Porridge", oats))
        assertFalse(mergesWithSavedFood("Oatmeal", null))
    }

    @Test
    fun `merge refreshes a favorite only when macros drifted beyond 25 percent`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val favorite = entry("Oatmeal", calories = 250, protein = 8.0, carbs = 40.0, fat = 4.0)
        prefs.setFavoriteFoodEntries(listOf(favorite))
        prefs.setFavoriteKeys(setOf(favorite.favoriteKey))
        prefs.replaceAllFoodEntries(listOf(favorite))
        val template = repo.savedTemplateFor("Oatmeal", null)

        // Estimation wobble inside 25 %: no refresh — the library must not
        // churn on every re-scan.
        val wobble = entry("Oatmeal", calories = 260, protein = 8.4, carbs = 42.0, fat = 4.2)
        assertFalse(repo.refreshFavoriteOnMerge(template, wobble))
        assertEquals(250, prefs.favoriteFoodEntries.first().single().calories)

        // One macro drifting beyond 25 %: refresh from the newest snapshot,
        // keeping name and id.
        val drifted = entry("Oatmeal", calories = 250, protein = 20.0, carbs = 40.0, fat = 4.0)
        assertTrue(repo.refreshFavoriteOnMerge(template, drifted))
        val stored = prefs.favoriteFoodEntries.first().single()
        assertEquals(favorite.id, stored.id)
        assertEquals("Oatmeal", stored.name)
        assertEquals(20.0, stored.protein, 0.0)
        assertEquals(40.0, stored.carbs, 0.0)
    }

    @Test
    fun `merge fills an empty placeholder favorite`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val placeholder = entry("Müsli", calories = 0, protein = 0.0, carbs = 0.0, fat = 0.0)
        prefs.setFavoriteFoodEntries(listOf(placeholder))

        val fresh = entry("Müsli", calories = 320, protein = 9.0, carbs = 45.0, fat = 7.0)
        assertTrue(repo.refreshFavoriteOnMerge(repo.savedTemplateFor("Müsli", null), fresh))

        val stored = prefs.favoriteFoodEntries.first().single()
        assertEquals(placeholder.id, stored.id)
        assertEquals(320, stored.calories)
        assertEquals(7.0, stored.fat, 0.0)
    }

    @Test
    fun `merge without a favorite does not touch the favorites library`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        prefs.replaceAllFoodEntries(listOf(entry("Oatmeal", calories = 250)))
        val banana = entry("Banana")
        prefs.setFavoriteFoodEntries(listOf(banana))

        // New food: no template at all.
        assertFalse(repo.refreshFavoriteOnMerge(null, entry("New Food")))
        // Diary-only identity: no persisted action — the diary collapse
        // already offers the newest row as the template.
        val diaryTemplate = repo.savedTemplateFor("Oatmeal", null)
        assertEquals(250, diaryTemplate?.calories)
        assertFalse(repo.refreshFavoriteOnMerge(diaryTemplate, entry("Oatmeal", calories = 500)))
        assertEquals(listOf(banana.id), prefs.favoriteFoodEntries.first().map { it.id })
    }
}
