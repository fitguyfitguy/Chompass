package app.chompass.data

import android.app.Application
import app.chompass.models.MealCatalog
import app.chompass.models.MealSchedule
import app.chompass.models.MealType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * #88 persistence lock: a schedule that wraps past midnight (dinner at 2:00 in
 * the default breakfast, lunch, dinner, snack order) must survive save and
 * read. Before the clock-sorted gap fix, read validation restored defaults,
 * which is the reported "meal times reset after app close".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MealSchedulePersistenceTest {
    private val wrap = MealCatalog.Default.withStart(MealType.DINNER.id, 2 * 60)

    @Test
    fun `wrap catalog survives save and read`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())

        prefs.setMealCatalog(wrap)

        val read = prefs.mealCatalog.first()
        assertEquals(wrap, read)
        assertTrue(read.isValid)
        assertEquals(MealType.DINNER.id, read.mealIdAt(LocalTime.of(2, 0)))
        assertEquals(MealType.SNACK.id, read.mealIdAt(LocalTime.of(1, 59)))
    }

    @Test
    fun `stored wrap json passes the read validation`() {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val stored = prefs.json.encodeToString(wrap)

        val parsed = prefs.parseMealCatalog(
            raw = stored,
            breakfast = MealSchedule.DEFAULT_BREAKFAST_START,
            lunch = MealSchedule.DEFAULT_LUNCH_START,
            dinner = MealSchedule.DEFAULT_DINNER_START,
            snack = MealSchedule.DEFAULT_SNACK_START,
        )

        assertEquals(wrap, parsed)
        assertEquals(MealType.DINNER.id, parsed.mealIdAt(LocalTime.of(3, 0)))
    }

    @Test
    fun `legacy wrap keys pass the read validation`() {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())

        val parsed = prefs.parseMealCatalog(
            raw = null,
            breakfast = MealSchedule.DEFAULT_BREAKFAST_START,
            lunch = MealSchedule.DEFAULT_LUNCH_START,
            dinner = 2 * 60,
            snack = MealSchedule.DEFAULT_SNACK_START,
        )

        assertTrue(parsed.isValid)
        assertEquals(2 * 60, parsed.toLegacySchedule().dinnerStartMinutes)
        assertEquals(MealType.DINNER.id, parsed.mealIdAt(LocalTime.of(2, 0)))
    }
}
