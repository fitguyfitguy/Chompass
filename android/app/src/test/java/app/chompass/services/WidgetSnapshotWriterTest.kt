package app.chompass.services

import androidx.datastore.preferences.core.edit
import app.chompass.data.CaffeineRepository
import app.chompass.data.FoodRepository
import app.chompass.data.PreferencesStore
import app.chompass.data.ProfileRepository
import app.chompass.data.WaterRepository
import app.chompass.data.WeatherRepository
import app.chompass.models.GoalJournalEntry
import app.chompass.models.GoalJournalSource
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanMode
import app.chompass.models.UserProfile
import app.chompass.models.WidgetSnapshot
import app.chompass.services.health.DebugActivityDay
import app.chompass.services.health.HealthConnectManager
import app.chompass.services.health.HomeActivityReader
import app.chompass.services.weather.OpenMeteoClient
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Widget snapshot freshness: a burn-only write — the measured active average
 * or the day-type active history — republishes the snapshot without a diary
 * write. Guards the combine wiring: both burn inputs must ride
 * [WidgetSnapshotWriter]'s SnapshotInputs value, otherwise
 * distinctUntilChanged swallows the write and the widget keeps the stale burn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WidgetSnapshotWriterTest {
    private lateinit var prefs: PreferencesStore
    private lateinit var profiles: ProfileRepository
    private lateinit var writer: WidgetSnapshotWriter

    @Before
    fun setUp() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        prefs = PreferencesStore(app)
        // The DataStore behind PreferencesStore is a process-wide singleton
        // (ChompassDataStoreHolder), so seeds written by one test method would
        // otherwise stay visible to the next. Start each method empty.
        prefs.dataStore.edit { it.clear() }
        profiles = ProfileRepository(prefs)
        val health = HealthConnectManager(app)
        writer = WidgetSnapshotWriter(
            context = app,
            prefs = prefs,
            foodRepository = FoodRepository(prefs),
            profileRepository = profiles,
            homeActivityReader = HomeActivityReader(health, prefs),
            waterRepository = WaterRepository(prefs),
            weatherRepository = WeatherRepository(prefs, OpenMeteoClient(OkHttpClient())),
            caffeineRepository = CaffeineRepository(prefs),
        )
        profiles.save(UserProfile(weightKg = 80.0))
    }

    private suspend fun awaitSnapshot(
        predicate: suspend (WidgetSnapshot) -> Boolean = { true },
    ): WidgetSnapshot = withTimeout(10_000) {
        prefs.widgetSnapshot.filterNotNull().first { predicate(it) }
    }

    @Test
    fun measuredActiveWrite_republishesSnapshot() = runBlocking {
        prefs.setHomeCalorieDisplayMode(HomeCalorieDisplayMode.ADD_ACTIVE.storageKey)
        val job = launch { writer.observe().collect() }
        val initial = awaitSnapshot()

        prefs.setHealthEnergyMeasuredActive(400)

        val updated = awaitSnapshot { it.activeCaloriesToday != initial.activeCaloriesToday }
        // ADD_ACTIVE hold-back with no live source credits the estimate, which
        // after the write is the measured average, not the PAL estimate.
        assertEquals(HomeCalorieDisplayMode.ADD_ACTIVE.storageKey, updated.calorieDisplayMode)
        assertEquals(400, updated.activeCaloriesToday)
        job.cancel()
    }

    @Test
    fun activeByDayWrite_republishesDayTypeTypical() = runBlocking {
        prefs.setHomeCalorieDisplayMode(HomeCalorieDisplayMode.ADD_ACTIVE.storageKey)
        // activeByDay surfaces only as a per-day-type typical average, so the
        // profile needs a plan whose id the journal entries repeat for
        // >= DayTypeActiveStats.MIN_SAMPLES days.
        val planId = "training"
        profiles.save(
            profiles.current()!!.copy(
                macroPlan = MacroPlan(
                    enabled = true,
                    profiles = listOf(MacroDayProfile(planId, "Training", 2_000, 150, 220, 70)),
                    mode = MacroPlanMode.MANUAL,
                    defaultProfileId = planId,
                ),
            ),
        )
        val today = LocalDate.now()
        prefs.setGoalJournal(
            (0L..2L).map { back ->
                GoalJournalEntry(
                    date = today.minusDays(back).toString(),
                    calories = 2_000,
                    proteinG = 150,
                    carbsG = 220,
                    fatG = 70,
                    profileId = planId,
                    updatedAtMillis = 1L,
                    source = GoalJournalSource.PLAN,
                )
            },
        )
        // Without a live energy source the hold-back credit is the blend
        // (measured average or PAL), never the typical — the ring slides from
        // the blend. Debug activity makes energy live, so the typical reaches
        // the snapshot as the expected-arc projection instead.
        prefs.setDebugActivityDays(
            listOf(DebugActivityDay(date = today.toString(), steps = 2_000L, activeCalories = 120)),
        )
        val job = launch { writer.observe().collect() }
        val initial = awaitSnapshot()

        prefs.setHealthEnergyActiveByDay(
            mapOf(
                today.toString() to 500,
                today.minusDays(1).toString() to 700,
                today.minusDays(2).toString() to 750,
            ),
        )

        val updated = awaitSnapshot { it.activeBurnTypical != initial.activeBurnTypical }
        // The arc's projected norm is the mean of the completed journaled days
        // of the type, (700 + 750) / 2 = 725. The credited burn stays at the
        // live measurement; the typical moves the arc end, not the burn.
        assertEquals(725, updated.activeBurnTypical)
        assertEquals(initial.activeCaloriesToday, updated.activeCaloriesToday)
        job.cancel()
    }
}
