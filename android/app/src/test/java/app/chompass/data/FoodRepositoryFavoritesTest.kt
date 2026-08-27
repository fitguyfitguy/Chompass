package app.chompass.data

import app.chompass.models.FoodEntry
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
 * Codeberg #66: favorites as a permanently editable saved-foods library —
 * updateFavorite replaces a stored favorite in place (id kept for the sync
 * revision chain, order preserved) and rewrites the legacy key set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FoodRepositoryFavoritesTest {
    private fun prefs() = PreferencesStore(RuntimeEnvironment.getApplication())

    private fun entry(
        name: String,
        id: UUID = UUID.randomUUID(),
        calories: Int = 100,
        mealType: String = MealType.LUNCH.id,
        recipeLogId: UUID? = null,
        timestamp: Instant = Instant.parse("2026-08-01T12:00:00Z"),
    ) = FoodEntry(
        id = id,
        name = name,
        calories = calories,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        timestamp = timestamp,
        source = FoodSource.MANUAL,
        mealType = mealType,
        recipeLogId = recipeLogId,
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
    fun `updateFavorite replaces in place preserving order and id`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val a = entry("Apple")
        val b = entry("Banana")
        val c = entry("Cherry")
        prefs.setFavoriteFoodEntries(listOf(a, b, c))

        val edited = entry("Banana", id = UUID.randomUUID(), calories = 200, mealType = MealType.DINNER.id)
        val stored = repo.updateFavorite(b, edited)

        assertEquals(b.id, stored?.id)
        assertEquals(200, stored?.calories)
        assertEquals(MealType.DINNER.id, stored?.mealType)
        assertEquals(listOf(a.id, b.id, c.id), prefs.favoriteFoodEntries.first().map { it.id })
    }

    @Test
    fun `updateFavorite rename rewrites legacy favoriteKeys`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val chicken = entry("Chicken", mealType = MealType.DINNER.id)
        val oats = entry("Oats")
        prefs.setFavoriteFoodEntries(listOf(chicken, oats))

        val renamed = repo.updateFavorite(chicken, entry("Pollo", mealType = MealType.BREAKFAST.id))

        assertEquals("pollo", renamed?.favoriteKey)
        assertEquals(setOf("pollo", "oats"), prefs.favoriteKeys.first())
        assertEquals(listOf("Pollo", "Oats"), prefs.favoriteFoodEntries.first().map { it.name })
    }

    @Test
    fun `updateFavorite normalizes recipeLogId to null`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val recipeId = UUID.randomUUID()
        val fav = entry("Curry", recipeLogId = recipeId)
        prefs.setFavoriteFoodEntries(listOf(fav))

        val stored = repo.updateFavorite(fav, entry("Curry", recipeLogId = recipeId))

        assertNull(stored?.recipeLogId)
        assertNull(prefs.favoriteFoodEntries.first().single().recipeLogId)
    }

    @Test
    fun `updateFavorite falls back to key match on a stale snapshot`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val fav = entry("Chicken")
        prefs.setFavoriteFoodEntries(listOf(fav))
        // UI copy with the same name but a different id must still hit the row.
        val stale = entry("Chicken", id = UUID.randomUUID())

        val stored = repo.updateFavorite(stale, entry("Chicken", id = stale.id, calories = 333))

        assertEquals(fav.id, stored?.id)
        assertEquals(333, prefs.favoriteFoodEntries.first().single().calories)
    }

    @Test
    fun `updateFavorite returns null when nothing matches`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        prefs.setFavoriteFoodEntries(listOf(entry("Apple")))

        assertNull(repo.updateFavorite(entry("Missing"), entry("Missing")))
        assertEquals(1, prefs.favoriteFoodEntries.first().size)
    }

    @Test
    fun `favoriteRenameBlocklist covers diary and other favorites but not itself`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val chicken = entry("Chicken")
        val oats = entry("Oats")
        prefs.setFavoriteFoodEntries(listOf(chicken, oats))
        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = mapOf(java.time.YearMonth.from(chicken.timestamp.atZone(java.time.ZoneId.systemDefault())) to listOf(entry("Salad")))
        )

        val blocklist = repo.favoriteRenameBlocklist(chicken)

        assertTrue(blocklist.contains("oats"))
        assertTrue(blocklist.contains("salad"))
        assertFalse(blocklist.contains("chicken"))
    }

    @Test
    fun `toggleFavorite unfavorites by the new key after a rename`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val chicken = entry("Chicken")
        prefs.setFavoriteFoodEntries(listOf(chicken))
        repo.updateFavorite(chicken, entry("Pollo", calories = 250))

        repo.toggleFavorite(entry("Pollo"))

        assertTrue(prefs.favoriteFoodEntries.first().isEmpty())
        assertTrue(prefs.favoriteKeys.first().isEmpty())
    }

    @Test
    fun `hearting the old name after a rename creates a separate favorite`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val chicken = entry("Chicken")
        prefs.setFavoriteFoodEntries(listOf(chicken))
        repo.updateFavorite(chicken, entry("Pollo"))

        // Documented consequence (name is identity): the old name no longer
        // matches the renamed favorite, so hearting it adds a new one.
        repo.toggleFavorite(entry("Chicken"))

        assertEquals(setOf("pollo", "chicken"), prefs.favoriteKeys.first())
        assertEquals(2, prefs.favoriteFoodEntries.first().size)
    }
}
