package app.chompass.data

import android.app.Application
import app.chompass.models.CoachQuickPromptEntry
import app.chompass.models.CoachQuickPrompts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Codeberg #155: the coach quick-prompt strip round-trips through DataStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CoachQuickPromptsPrefsTest {
    @Test
    fun coachQuickPrompts_defaultNull_andPersists() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        assertNull(prefs.coachQuickPrompts.first())

        val prompts = CoachQuickPrompts(listOf(CoachQuickPromptEntry(id = "predict_30_days")))
        prefs.setCoachQuickPrompts(prompts)
        assertEquals(prompts, prefs.coachQuickPrompts.first())

        prefs.setCoachQuickPrompts(null)
        assertNull(prefs.coachQuickPrompts.first())
    }
}
