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
 * Codeberg #142 WP4: merging two saved-food identities. favoriteKey is
 * computed from the name, so the row-level merge is a rename; favorites are
 * re-pointed or dropped per semantics 4, and stale hidden keys clear out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FoodRepositoryMergeFoodsTest {
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
    fun `mergeFoodKey renames source rows onto the target identity`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val august = entry("Oatmeal", timestamp = Instant.parse("2026-08-01T12:00:00Z"))
        val september = entry("Oatmeal", timestamp = Instant.parse("2026-09-15T12:00:00Z"))
        val target = entry("Oatmeal (2)", calories = 250)
        val banana = entry("Banana")
        prefs.replaceAllFoodEntries(listOf(august, september, target, banana))

        val moved = repo.mergeFoodKey("oatmeal", "oatmeal (2)")

        assertEquals(2, moved)
        val rows = prefs.foodEntries.first()
        assertEquals(4, rows.size)
        // Same rows, new identity: ids, timestamps, and macros survive the
        // rename; only the name (hence favoriteKey) changes.
        val movedRows = rows.filter { it.id == august.id || it.id == september.id }
        assertEquals(listOf("Oatmeal (2)", "Oatmeal (2)"), movedRows.map { it.name })
        assertTrue(movedRows.all { it.favoriteKey == "oatmeal (2)" })
        assertEquals(listOf(august.timestamp, september.timestamp), movedRows.map { it.timestamp })
        assertEquals(0, repo.loggedRowCount("oatmeal"))
        // The collapse now offers one identity whose newest row is September's.
        val templates = repo.historyTemplates()
        assertEquals(listOf("banana", "oatmeal (2)"), templates.map { it.favoriteKey }.sorted())
        assertEquals(100, templates.first { it.favoriteKey == "banana" }.calories)
        assertEquals(september.id, templates.first { it.favoriteKey == "oatmeal (2)" }.id)
        // Unrelated rows are untouched.
        assertEquals(banana, rows.first { it.id == banana.id })
    }

    @Test
    fun `mergeFoodKey re-points a source-only favorite`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val favorite = entry("Oatmeal", calories = 100)
        prefs.setFavoriteFoodEntries(listOf(favorite))
        prefs.replaceAllFoodEntries(
            listOf(entry("Oatmeal"), entry("Oatmeal (2)", calories = 250)),
        )

        repo.mergeFoodKey("oatmeal", "oatmeal (2)")

        val favorites = prefs.favoriteFoodEntries.first()
        // Same favorite entry, new identity — curated macros are kept, not
        // overwritten by the merged diary rows.
        assertEquals(1, favorites.size)
        val rePointed = favorites.single()
        assertEquals(favorite.id, rePointed.id)
        assertEquals("Oatmeal (2)", rePointed.name)
        assertEquals("oatmeal (2)", rePointed.favoriteKey)
        assertEquals(100, rePointed.calories)
        assertEquals(setOf("oatmeal (2)"), prefs.favoriteKeys.first())
    }

    @Test
    fun `mergeFoodKey drops the source favorite when both are favorited`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val sourceFavorite = entry("Oatmeal")
        val targetFavorite = entry("Oatmeal (2)", calories = 250)
        prefs.setFavoriteFoodEntries(listOf(sourceFavorite, targetFavorite))
        prefs.replaceAllFoodEntries(listOf(entry("Oatmeal"), entry("Oatmeal (2)")))

        repo.mergeFoodKey("oatmeal", "oatmeal (2)")

        val favorites = prefs.favoriteFoodEntries.first()
        assertEquals(listOf(targetFavorite.id), favorites.map { it.id })
        assertEquals("oatmeal (2)", favorites.single().favoriteKey)
        assertEquals(250, favorites.single().calories)
        assertEquals(setOf("oatmeal (2)"), prefs.favoriteKeys.first())
    }

    @Test
    fun `mergeFoodKey clears a stale hidden source key and keeps the target hidden`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        prefs.replaceAllFoodEntries(
            listOf(entry("Oatmeal"), entry("Oatmeal (2)")),
        )
        repo.hideFood("oatmeal")
        repo.hideFood("oatmeal (2)")

        repo.mergeFoodKey("oatmeal", "oatmeal (2)")

        // The source identity is gone from the log, so its hidden entry is
        // stale; the target's hidden state is the user's call and survives.
        // Membership checks, not whole-set equality: the DataStore singleton
        // is shared across test classes, so foreign hidden keys may be set.
        val hidden = prefs.hiddenFoodKeys.first()
        assertTrue("oatmeal" !in hidden)
        assertTrue("oatmeal (2)" in hidden)
        // Leave the shared store as we found it — FoodRepositoryHiddenFoodsTest
        // asserts the whole hidden set, so leftovers fail that suite.
        prefs.setFoodHidden("oatmeal", false)
        prefs.setFoodHidden("oatmeal (2)", false)
    }

    @Test
    fun `mergeFoodKey bumps libraryVersion`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        prefs.replaceAllFoodEntries(listOf(entry("Oatmeal"), entry("Oatmeal (2)")))
        val before = repo.libraryVersion.value

        repo.mergeFoodKey("oatmeal", "oatmeal (2)")

        assertEquals(before + 1, repo.libraryVersion.value)
    }

    @Test
    fun `mergeFoodKey is a no-op when the target identity is gone`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val row = entry("Oatmeal")
        prefs.replaceAllFoodEntries(listOf(row))

        val moved = repo.mergeFoodKey("oatmeal", "muesli")

        assertEquals(0, moved)
        assertEquals(listOf(row), prefs.foodEntries.first())
    }

    @Test
    fun `mergeFoodKey refuses identical keys`() = runBlocking {
        val prefs = prefs()
        val repo = FoodRepository(prefs)
        val row = entry("Oatmeal")
        prefs.replaceAllFoodEntries(listOf(row))

        assertEquals(0, repo.mergeFoodKey("oatmeal", "oatmeal"))
        assertEquals(listOf(row), prefs.foodEntries.first())
    }
}
