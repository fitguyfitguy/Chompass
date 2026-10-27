package app.chompass.data

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
 * Codeberg #142: saved-foods library plumbing — hidden identity keys drop a
 * food out of Add Food suggestions while diary history stays intact, plus
 * the template fix / delete-all-logged-rows mutations the Settings browser
 * and review sheet are built on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FoodRepositoryHiddenFoodsTest {
    private fun prefs() = PreferencesStore(RuntimeEnvironment.getApplication())

    private fun entry(
        name: String,
        id: UUID = UUID.randomUUID(),
        calories: Int = 100,
        mealType: String = MealType.LUNCH.id,
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
    fun `hideFood persists the key, unfavorites, syncs legacy favoriteKeys`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val favorite = entry("Apple")
        prefs.setFavoriteFoodEntries(listOf(favorite))
        prefs.replaceAllFoodEntries(listOf(entry("Apple"), entry("Banana")))

        repo.hideFood("apple")

        assertEquals(setOf("apple"), prefs.hiddenFoodKeys.first())
        assertTrue(prefs.favoriteFoodEntries.first().isEmpty())
        assertTrue(prefs.favoriteKeys.first().isEmpty())
        // Diary rows are untouched by hide.
        assertEquals(2, prefs.foodEntries.first().size)
    }

    @Test
    fun `unhideFood clears the key without re-favoriting`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        prefs.setFavoriteFoodEntries(listOf(entry("Apple")))
        repo.hideFood("apple")

        repo.unhideFood("apple")

        assertTrue(prefs.hiddenFoodKeys.first().isEmpty())
        assertTrue(prefs.favoriteFoodEntries.first().isEmpty())
    }

    @Test
    fun `savedFoodIndex excludes hidden foods, includeHidden returns them`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val now = Instant.now()
        prefs.replaceAllFoodEntries(
            listOf(
                entry("Apple", timestamp = now.minusSeconds(2 * 86_400)),
                entry("Banana", timestamp = now.minusSeconds(2 * 86_400)),
            ),
        )
        repo.hideFood("apple")

        val visible = repo.savedFoodIndex().map { it.template.favoriteKey }
        assertEquals(listOf("banana"), visible)

        val everything = repo.savedFoodIndex(includeHidden = true).map { it.template.favoriteKey }
        assertEquals(listOf("apple", "banana"), everything)
    }

    @Test
    fun `updateSavedTemplate edits a plain diary row in place`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val original = entry("Apple", calories = 100)
        prefs.replaceAllFoodEntries(listOf(original, entry("Banana")))

        repo.updateSavedTemplate(original, entry("Apple pie", calories = 300))

        val rows = prefs.foodEntries.first()
        assertEquals(2, rows.size)
        val edited = rows.single { it.id == original.id }
        assertEquals("Apple pie", edited.name)
        assertEquals(300, edited.calories)
        assertEquals(original.timestamp, edited.timestamp)
    }

    @Test
    fun `updateSavedTemplate updates a favorite in place and leaves the diary alone`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val favorite = entry("Apple", calories = 100)
        val diaryRow = entry("Apple", calories = 100, timestamp = Instant.parse("2026-08-02T12:00:00Z"))
        prefs.setFavoriteFoodEntries(listOf(favorite))
        prefs.replaceAllFoodEntries(listOf(diaryRow))

        repo.updateSavedTemplate(favorite, entry("Apple pie", calories = 300))

        val favorites = prefs.favoriteFoodEntries.first()
        assertEquals(1, favorites.size)
        assertEquals(favorite.id, favorites.single().id)
        assertEquals("Apple pie", favorites.single().name)
        assertEquals(300, favorites.single().calories)
        // Diary row untouched: same id, same name, same calories.
        val rows = prefs.foodEntries.first()
        assertEquals(1, rows.size)
        assertEquals(diaryRow.id, rows.single().id)
        assertEquals("Apple", rows.single().name)
    }

    @Test
    fun `deleteLoggedRows removes exactly the key's rows across two months`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val august = entry("Apple", timestamp = Instant.parse("2026-08-01T12:00:00Z"))
        val september = entry("Apple", timestamp = Instant.parse("2026-09-15T12:00:00Z"))
        val banana = entry("Banana", timestamp = Instant.parse("2026-09-16T12:00:00Z"))
        prefs.replaceAllFoodEntries(listOf(august, september, banana))

        val removed = repo.deleteLoggedRows("apple")

        assertEquals(2, removed)
        assertEquals(listOf(banana.id), prefs.foodEntries.first().map { it.id })
        // Deleting a key with no rows is a no-op.
        assertEquals(0, repo.deleteLoggedRows("apple"))
    }

    @Test
    fun `libraryVersion increments on every library mutation`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val original = entry("Apple")
        prefs.setFavoriteFoodEntries(listOf(original))
        prefs.replaceAllFoodEntries(listOf(original))
        val start = repo.libraryVersion.value

        repo.hideFood("apple")
        assertEquals(start + 1, repo.libraryVersion.value)

        repo.unhideFood("apple")
        assertEquals(start + 2, repo.libraryVersion.value)

        repo.updateSavedTemplate(original, entry("Apple pie"))
        assertEquals(start + 3, repo.libraryVersion.value)

        repo.deleteLoggedRows("apple pie")
        assertEquals(start + 4, repo.libraryVersion.value)

        // No-op delete mutates nothing.
        repo.deleteLoggedRows("nothing")
        assertEquals(start + 4, repo.libraryVersion.value)
    }
}
