package app.chompass.data

import android.app.Application
import app.chompass.models.FastingGoalPreset
import app.chompass.models.FastingPhase
import app.chompass.models.phase
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

/**
 * Intermittent-fasting timer state machine (docs/local/PLAN_FASTING_TRACKER.md):
 * start (manual or auto) / stop (incl. exact goal-time end), restart
 * re-derivation from persisted scalars, the one-shot goal latch, the
 * auto-started flag, and the onSessionChanged re-arm hook.
 * the one-shot goal latch, and the onSessionChanged re-arm hook.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FastingRepositoryTest {
    @Before
    fun setUp() = runBlocking {
        // DataStore is a process-wide singleton: start every test clean.
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        prefs.setFastingSessionFields(
            startedAtMillis = null,
            lastEndedAtMillis = null,
            lastFastStartedAtMillis = null,
            goalReachedNotified = false,
        )
        prefs.setFastingEnabled(false)
        prefs.setFastingGoalHours(0)
        prefs.setFastingGoalNotificationEnabled(true)
        prefs.setFastingAutoWindows(false)
        prefs.setFastingStartReminderEnabled(false)
        prefs.setFastingStartReminderLeadMinutes(DEFAULT_FASTING_START_REMINDER_LEAD_MINUTES)
        prefs.setFastingEndReminderLeadMinutes(DEFAULT_FASTING_END_REMINDER_LEAD_MINUTES)
        prefs.setFastingEatHours(0)
    }

    private fun repo(
        prefs: PreferencesStore,
        changed: MutableList<Int> = mutableListOf(),
    ) = FastingRepository(prefs).apply {
        onSessionChanged = { changed.add(1) }
    }

    @Test
    fun `defaults to idle`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val s = prefs.fastingSession.first()
        assertFalse(s.isFasting)
        assertNull(s.startedAtMillis)
        assertNull(s.lastEndedAtMillis)
        assertEquals(0L, s.elapsedMillis())
    }

    @Test
    fun `start begins a fast and computes elapsed`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        val t0 = 1_000_000L

        r.start(t0)

        val s = prefs.fastingSession.first()
        assertTrue(s.isFasting)
        assertEquals(t0, s.startedAtMillis)
        assertEquals(5_000L, s.elapsedMillis(t0 + 5_000L))
    }

    @Test
    fun `start while fasting is a no-op`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        r.start(1_000L)
        r.start(9_000L)

        assertEquals(1_000L, prefs.fastingSession.first().startedAtMillis)
    }

    @Test
    fun `stop records the fast as last fast and returns to idle`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        r.start(1_000L)
        r.stop(10_000L)

        val s = prefs.fastingSession.first()
        assertFalse(s.isFasting)
        assertNull(s.startedAtMillis)
        assertEquals(10_000L, s.lastEndedAtMillis)
        assertEquals(1_000L, s.lastFastStartedAtMillis)
        assertEquals(9_000L, s.lastFastDurationMillis())
    }

    @Test
    fun `auto start marks the session auto and manual start does not`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        r.start(1_000L, auto = true)
        assertTrue(prefs.fastingSession.first().autoStarted)

        r.stop(10_000L)
        assertFalse(prefs.fastingSession.first().autoStarted)
        r.start(20_000L) // manual
        assertFalse(prefs.fastingSession.first().autoStarted)
    }

    @Test
    fun `stop at goal anchors the end to the exact goal instant`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        r.start(1_000L)
        val goal = 1_000L + 16 * 3_600_000L
        r.stop(atGoalMillis = goal)

        val s = prefs.fastingSession.first()
        assertFalse(s.isFasting)
        assertEquals(goal, s.lastEndedAtMillis)
        assertEquals(16 * 3_600_000L, s.lastFastDurationMillis())
    }

    @Test
    fun `auto defaults are off and require an eating window`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        assertFalse(prefs.fastingAutoWindows.first())
        assertFalse(prefs.fastingSession.first().autoStarted)
    }

    @Test
    fun `state re-derives after a restart (persisted scalars)`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        r.start(1_000L)
        // Simulate app restart: a fresh repository over the same DataStore.
        val restarted = FastingRepository(prefs)
        val s = restarted.current()
        assertTrue(s.isFasting)
        assertEquals(1_000L, s.startedAtMillis)
    }

    @Test
    fun `goal reached derives from elapsed versus goal hours`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        r.start(1_000L)

        val s = prefs.fastingSession.first()
        assertFalse(s.goalReached(goalHours = 16, nowMillis = 1_000L + 10 * 3_600_000L))
        assertTrue(s.goalReached(goalHours = 16, nowMillis = 1_000L + 17 * 3_600_000L))
        assertFalse(s.goalReached(goalHours = 0, nowMillis = 1_000L + 20 * 3_600_000L))
    }

    @Test
    fun `goal latch fires once and is reset by the next start`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        r.start(1_000L)
        r.markGoalReachedNotified()
        assertTrue(prefs.fastingSession.first().goalReachedNotified)

        r.markGoalReachedNotified() // second call is a no-op
        assertTrue(prefs.fastingSession.first().goalReachedNotified)

        r.stop(10_000L)
        assertFalse(prefs.fastingSession.first().goalReachedNotified)
        r.start(20_000L)
        assertFalse(prefs.fastingSession.first().goalReachedNotified)
    }

    @Test
    fun `session change hooks fire on start and stop`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val changed = mutableListOf<Int>()
        val r = repo(prefs, changed)

        r.start(1_000L)
        r.stop(10_000L)
        r.start(20_000L)
        r.stop(atGoalMillis = 30_000L)

        assertEquals(4, changed.size)
    }

    @Test
    fun `pref defaults are off with no goal`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        assertFalse(prefs.fastingEnabled.first())
        assertEquals(0, prefs.fastingGoalHours.first())
        assertTrue(prefs.fastingGoalNotificationEnabled.first())
    }

    @Test
    fun `remaining until goal counts down and zeroes at the goal`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        r.start(1_000L)

        val s = prefs.fastingSession.first()
        assertEquals(16 * 3_600_000L, s.remainingUntilGoalMillis(16, nowMillis = 1_000L))
        assertEquals(3_600_000L, s.remainingUntilGoalMillis(16, nowMillis = 1_000L + 15 * 3_600_000L))
        assertEquals(0L, s.remainingUntilGoalMillis(16, nowMillis = 1_000L + 16 * 3_600_000L))
        assertEquals(0L, s.remainingUntilGoalMillis(16, nowMillis = 1_000L + 20 * 3_600_000L))
        // No goal / idle → 0.
        assertEquals(0L, s.remainingUntilGoalMillis(0, nowMillis = 1_000L))
        r.stop(9 * 3_600_000L + 1_000L)
        assertEquals(0L, prefs.fastingSession.first().remainingUntilGoalMillis(16, nowMillis = 1_000L))
    }

    @Test
    fun `start reminder defaults are off with a 15 min lead`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        assertFalse(prefs.fastingStartReminderEnabled.first())
        assertEquals(15, prefs.fastingStartReminderLeadMinutes.first())
        assertEquals(15, prefs.fastingEndReminderLeadMinutes.first())
        assertEquals(0, prefs.fastingEatHours.first())
    }

    @Test
    fun `eating window ends at stop plus eat hours and phases derive from it`() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        val r = repo(prefs)
        r.start(1_000L)
        r.stop(9 * 3_600_000L + 1_000L) // stopped at t=9h

        val s = prefs.fastingSession.first()
        // No eat hours → no eating phase.
        assertEquals(null, s.eatingWindowEndsAtMillis(0, nowMillis = 10_000L))
        assertEquals(FastingPhase.IDLE, s.phase(0, nowMillis = 10_000L))

        // Eat window 8 h from the stop.
        val windowEnds = s.eatingWindowEndsAtMillis(8, nowMillis = 10_000L)
        assertEquals((9 * 3_600_000L + 1_000L) + 8 * 3_600_000L, windowEnds)
        assertEquals(FastingPhase.EATING, s.phase(8, nowMillis = 10_000L))
        // After the window closes the phase lapses to idle.
        assertEquals(null, s.eatingWindowEndsAtMillis(8, nowMillis = (17 * 3_600_000L) + 2_000L))
        assertEquals(FastingPhase.IDLE, s.phase(8, nowMillis = (17 * 3_600_000L) + 2_000L))
    }

    @Test
    fun `popular presets cover the mainstream protocols`() {
        val hours = FastingGoalPreset.Popular.map { it.fastHours }
        assertEquals(listOf(12, 14, 16, 18, 20, 23), hours)
        // Fast + eating window always sums to a full day (the ratio meaning).
        FastingGoalPreset.Popular.forEach { preset ->
            assertEquals(24, preset.fastHours + preset.eatHours)
        }
        assertEquals("16:8", FastingGoalPreset.Popular.first { it.fastHours == 16 }.label)
    }
}
