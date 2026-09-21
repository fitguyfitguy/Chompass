package app.chompass.data

import app.chompass.models.Recipe
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Recipe writes go through [PreferencesStore.editListPref], where the
 * read-modify-write runs inside one `dataStore.edit`. A stale
 * `first()`-then-set pattern loses concurrent updates; these tests pin that
 * racing writers all commit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class RecipeRepositoryAtomicRmwTest {
    private lateinit var prefs: PreferencesStore
    private lateinit var repo: RecipeRepository

    private fun recipe(name: String): Recipe = Recipe(name = name)

    @Before
    fun setUp() = runBlocking {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        // The process-wide DataStore singleton is shared across tests in this
        // JVM; start each test from an empty recipes list.
        prefs.dataStore.edit { it.remove(Keys.RECIPES) }
        repo = RecipeRepository(prefs, foodRepository = FoodRepository(prefs))
    }

    @Test
    fun concurrentSaves_allSurvive() = runBlocking {
        val names = (1..8).map { "Recipe $it" }
        coroutineScope {
            names.map { name -> async { repo.saveRecipe(recipe(name)) } }.awaitAll()
        }
        val stored = repo.recipes.first()
        assertEquals(names.toSet(), stored.map { it.name }.toSet())
        assertEquals(names.size, stored.size)
    }

    @Test
    fun deleteRacingSave_noResurrectionNoLoss() = runBlocking {
        val doomed = recipe("Doomed")
        val keeper = recipe("Keeper")
        repo.saveRecipe(doomed)
        repo.saveRecipe(keeper)
        coroutineScope {
            val delete = async { repo.deleteRecipe(doomed) }
            val save = async { repo.saveRecipe(recipe("Newcomer")) }
            listOf(delete, save).awaitAll()
        }
        val ids = repo.recipes.first().map { it.id }
        assertFalse("deleted recipe resurrected", doomed.id in ids)
        assertTrue("keeper lost to a racing write", keeper.id in ids)
        assertEquals(2, ids.size)
    }

    @Test
    fun deleteThenResaveById_latestWriteWins() = runBlocking {
        val target = recipe("Target")
        repo.saveRecipe(target)
        coroutineScope {
            val delete = async { repo.deleteRecipe(target) }
            val resave = async { repo.saveRecipe(target.copy(name = "Target v2")) }
            listOf(delete, resave).awaitAll()
        }
        assertTrue(repo.recipes.first().count { it.id == target.id } <= 1)
        val stored = repo.recipes.first().firstOrNull { it.id == target.id }
        // Both orders are acceptable: delete-before-save leaves the row,
        // save-before-delete removes it — but never a torn state.
        assertTrue(stored == null || stored.name == "Target v2")
        assertEquals(1, repo.recipes.first().count { it.id == target.id })
    }
}
