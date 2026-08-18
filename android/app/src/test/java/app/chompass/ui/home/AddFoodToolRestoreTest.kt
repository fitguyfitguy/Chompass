package app.chompass.ui.home

import android.app.Application
import app.chompass.data.PreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Codeberg #30: the `lastAddFoodTool` pref round-trips (SavedMeals-style
 * accessors), and the pure restore-decision helper falls back to the grid
 * when the stored tool is unknown or currently unavailable (AI-off hides
 * photo/note/voice, grounded stays behind its feature gate).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AddFoodToolRestoreTest {
    private lateinit var prefs: PreferencesStore

    @Before
    fun setUp() {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        // The DataStore is a process-wide singleton: restore the pref this
        // class mutates so later suites see pristine state.
        runBlocking { prefs.setLastAddFoodTool("") }
    }

    @Test
    fun lastAddFoodToolRoundTrips() = runBlocking {
        assertEquals("", prefs.lastAddFoodTool.first())
        prefs.setLastAddFoodTool(AddFoodTool.NOTE.storageId)
        assertEquals(AddFoodTool.NOTE.storageId, prefs.lastAddFoodTool.first())
        prefs.setLastAddFoodTool(AddFoodTool.BARCODE.storageId)
        assertEquals(AddFoodTool.BARCODE.storageId, prefs.lastAddFoodTool.first())
    }

    @Test
    fun storageIdsRoundTrip() {
        AddFoodTool.entries.forEach { tool ->
            assertEquals(tool, AddFoodTool.fromStorageId(tool.storageId))
        }
    }

    @Test
    fun blankOrNullStoredFallsBackToGrid() {
        assertNull(resolveAddFoodRestoreTool(null, aiFeaturesEnabled = true))
        assertNull(resolveAddFoodRestoreTool("", aiFeaturesEnabled = true))
    }

    @Test
    fun unknownStoredIdFallsBackToGrid() {
        assertNull(resolveAddFoodRestoreTool("nope", aiFeaturesEnabled = true))
    }

    @Test
    fun aiToolsRestoreWithAiOnAndFallBackToGridWithAiOff() {
        for (id in listOf("photo", "note", "voice")) {
            assertEquals(
                AddFoodTool.fromStorageId(id),
                resolveAddFoodRestoreTool(id, aiFeaturesEnabled = true),
            )
            assertNull(resolveAddFoodRestoreTool(id, aiFeaturesEnabled = false))
        }
    }

    @Test
    fun nonAiToolsRestoreWithAiOff() {
        for (id in listOf("savedRecents", "barcode", "manual", "copyFromDay", "search", "manualActive")) {
            assertEquals(
                AddFoodTool.fromStorageId(id),
                resolveAddFoodRestoreTool(id, aiFeaturesEnabled = false),
            )
        }
    }

    @Test
    fun groundedRespectsFeatureGate() {
        assertNull(
            resolveAddFoodRestoreTool("grounded", aiFeaturesEnabled = true, groundedEnabled = false),
        )
        assertEquals(
            AddFoodTool.GROUNDED,
            resolveAddFoodRestoreTool("grounded", aiFeaturesEnabled = true, groundedEnabled = true),
        )
    }

    @Test
    fun photoNeverPersists() {
        assertEquals(false, AddFoodTool.PHOTO.persists)
        AddFoodTool.entries.filter { it != AddFoodTool.PHOTO }.forEach { tool ->
            assertEquals("every non-photo tool persists: ${tool.storageId}", true, tool.persists)
        }
    }
}
