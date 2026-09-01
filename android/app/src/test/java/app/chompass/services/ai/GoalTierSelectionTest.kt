package app.chompass.services.ai

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.UserProfile
import app.chompass.models.WeightEntry
import app.chompass.services.WeightAnalysisService
import app.chompass.services.ondevice.ModelCatalog
import app.chompass.services.ondevice.ModelDownloadManager
import app.chompass.services.ondevice.OnDeviceLlmEngine
import app.chompass.services.ondevice.OnDeviceLlmGateway
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import androidx.datastore.preferences.core.edit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Per-dispatch tier selection in `calculateGoals` (docs/CALCULATION_METHODS.md
 * § AI-RECALC): cloud legs run the SMART prompt (raw weigh-in series + intake
 * table + model-side judgment), the on-device leg always runs the SAFE prompt
 * (aggregates only, app-side gates + deterministic snap) — including when the
 * on-device leg is a FALLBACK after a cloud primary failure. The result records
 * which provider/model/tier actually answered and whether a fallback fired.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GoalTierSelectionTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: PreferencesStore

    private val goalJson = """{"calories":1980,"protein":136,"carbs":257,"fat":45,"reason":"test"}"""

    /** Weight series in the past so any test-JVM zone keeps the same local dates. */
    private val weights = listOf(
        WeightEntry(date = Instant.parse("2026-08-01T12:00:00Z"), weightKg = 76.4),
        WeightEntry(date = Instant.parse("2026-08-05T12:00:00Z"), weightKg = 76.2),
    )
    private val foods = listOf(
        FoodEntry(
            name = "test", calories = 2290, protein = 100.0, carbs = 200.0, fat = 50.0,
            source = FoodSource.MANUAL, timestamp = Instant.parse("2026-08-14T12:00:00Z"),
        ),
    )

    private fun profile() = UserProfile(heightCm = 178.0, weightKg = 76.0)

    /** Thin but present forecast: aggregates exist, but the empirical gate withholds. */
    private fun thinForecast(): app.chompass.services.WeightForecast {
        val zone = java.time.ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        val entries = listOf(
            WeightEntry(date = now.minus(4, ChronoUnit.DAYS), weightKg = 76.0),
            WeightEntry(date = now, weightKg = 76.2),
        )
        val dayFoods = listOf(
            FoodEntry(
                name = "test", calories = 2290, protein = 100.0, carbs = 200.0, fat = 50.0,
                source = FoodSource.MANUAL, timestamp = today.minusDays(1).atStartOfDay(zone).toInstant(),
            ),
            FoodEntry(
                name = "test", calories = 2290, protein = 100.0, carbs = 200.0, fat = 50.0,
                source = FoodSource.MANUAL, timestamp = today.minusDays(2).atStartOfDay(zone).toInstant(),
            ),
        )
        return WeightAnalysisService.compute(entries, dayFoods, profile(), now = now, zone = zone)
    }

    private fun newService(
        onDeviceGateway: OnDeviceLlmGateway? = null,
        onDeviceModelDownloaded: ((String) -> Boolean)? = null,
    ): FoodAnalysisService =
        FoodAnalysisService(
            prefs = prefs,
            okHttp = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
            onDeviceGateway = onDeviceGateway,
            onDeviceModelDownloaded = onDeviceModelDownloaded,
            keyLookup = { "test-key" }, // any non-null key satisfies GEMINI's requiresApiKey check
        )

    /** Fake on-device engine + a gateway that will use it (model file stubbed on disk). */
    private fun fakeGateway(onGenerate: (String) -> String): Pair<OnDeviceLlmGateway, FakeOnDeviceEngine> {
        val context = RuntimeEnvironment.getApplication()
        // The real gateway runs the engine-load memory preflight; give Robolectric
        // enough availMem that the fake engine is actually reached.
        val mem = ActivityManager.MemoryInfo().apply {
            totalMem = 8L * 1024 * 1024 * 1024
            availMem = 6L * 1024 * 1024 * 1024
        }
        shadowOf(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).setMemoryInfo(mem)
        val entry = ModelCatalog.forModelId("gemma-4-E2B-it")
        val modelFile = ModelDownloadManager(context).modelFile(entry)
        modelFile.parentFile?.mkdirs()
        if (!modelFile.exists()) modelFile.writeBytes(byteArrayOf(1))
        val engine = FakeOnDeviceEngine(onGenerate)
        val gateway = OnDeviceLlmGateway(context, prefs, engineFactory = { _, _, _, _ -> engine })
        return gateway to engine
    }

    private fun geminiText(text: String): String =
        JSONObject()
            .put(
                "candidates",
                JSONArray().put(
                    JSONObject().put(
                        "content",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))),
                    ),
                ),
            )
            .toString()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        runBlocking {
            prefs.setSelectedAIProvider(AIProvider.GEMINI)
            prefs.setCustomBaseUrl(AIProvider.GEMINI, server.url("/").toString())
            prefs.setFallbackEnabled(false)
            // Pin the primary model: debug builds default to flash-lite, which
            // runs the SAFE goal tier — these tests plumb the SMART prompt.
            prefs.setSelectedAIModel("gemini-3.7-flash")
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        // The DataStore is a process-wide singleton: restore prefs this class
        // mutates so later tests/suites see pristine state.
        runBlocking { prefs.dataStore.edit { it.remove(app.chompass.data.Keys.SELECTED_AI_MODEL) } }
    }

    @Test
    fun cloudPrimary_receivesSmartPromptWithRawSeries_andReportsTier() = runBlocking {
        val service = newService()
        server.enqueue(MockResponse().setResponseCode(200).setBody(geminiText(goalJson)))

        val result = service.calculateGoals(
            profile(), thinForecast(), heightMetric = true, weightMetric = true,
            weights = weights, foods = foods,
        )

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue("SMART prompt must include the raw weigh-in series", body.contains("RAW WEIGH-INS"))
        assertTrue("raw series must carry date + weight", body.contains("2026-08-01: 76.4"))
        assertTrue("SMART prompt must include the intake table", body.contains("RAW INTAKE, last 14 days"))
        assertTrue("intake table must carry day + kcal", body.contains("2026-08-14: 2290"))
        assertTrue("implied maintenance must be shown and labeled as an estimate", body.contains("ROUGH estimate"))
        assertTrue("SMART prompt must carry noise guidance", body.contains("NOISE GUIDANCE"))
        assertEquals(GoalRecalcTier.SMART, result.tier)
        assertEquals(AIProvider.GEMINI, result.provider)
        assertFalse("no fallback fired", result.fallbackFired)
    }

    @Test
    fun onDevicePrimary_receivesSafePromptWithoutRawSeries_andEnforcesSnap() = runBlocking {
        val (gateway, engine) = fakeGateway { goalJson }
        prefs.setSelectedAIProvider(AIProvider.ON_DEVICE)
        prefs.setSelectedAIModel("gemma-4-E2B-it")
        val service = newService(onDeviceGateway = gateway, onDeviceModelDownloaded = { true })

        val result = service.calculateGoals(
            profile(), thinForecast(), heightMetric = true, weightMetric = true,
            weights = weights, foods = foods,
        )

        assertTrue("SAFE prompt must carry the observed section", engine.lastPrompt.contains("OBSERVED DATA"))
        assertTrue("SAFE prompt must say the data is too thin", engine.lastPrompt.contains("too thin"))
        assertFalse("SAFE prompt must NOT leak the raw series", engine.lastPrompt.contains("RAW WEIGH-INS"))
        assertFalse("SAFE prompt must NOT leak the intake table", engine.lastPrompt.contains("RAW INTAKE"))
        assertEquals(GoalRecalcTier.SAFE, result.tier)
        assertEquals(AIProvider.ON_DEVICE, result.provider)
        assertEquals("thin data must snap to the formula anchor", profile().dailyCalories, result.calories)
    }

    @Test
    fun cloudToOnDeviceFallback_onDeviceLegReceivesSafePrompt_andResultReportsFallback() = runBlocking {
        val (gateway, engine) = fakeGateway { goalJson }
        prefs.setFallbackEnabled(true)
        prefs.setSelectedFallbackProvider(AIProvider.ON_DEVICE)
        prefs.setSelectedFallbackModel("gemma-4-E2B-it")
        // Primary cloud leg fails before any content; the on-device fallback answers.
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"boom"}}"""))
        val service = newService(onDeviceGateway = gateway, onDeviceModelDownloaded = { true })

        val result = service.calculateGoals(
            profile(), thinForecast(), heightMetric = true, weightMetric = true,
            weights = weights, foods = foods,
        )

        // The cloud PRIMARY leg ran the SMART prompt (raw series).
        val primaryBody = server.takeRequest().body.readUtf8()
        assertTrue("cloud primary must run the SMART prompt", primaryBody.contains("RAW WEIGH-INS"))
        // The on-device FALLBACK leg ran the SAFE prompt.
        assertFalse("on-device fallback must receive the SAFE prompt, not the raw series", engine.lastPrompt.contains("RAW WEIGH-INS"))
        assertTrue("on-device fallback prompt must carry the thin-data gate", engine.lastPrompt.contains("too thin"))
        // The result reports the leg that actually answered + the fallback.
        assertEquals(GoalRecalcTier.SAFE, result.tier)
        assertEquals(AIProvider.ON_DEVICE, result.provider)
        assertTrue("fallback must be reported", result.fallbackFired)
        assertTrue("primary error must be recorded", result.primaryError?.contains("boom") == true)
    }

    @Test
    fun smallCloudModel_receivesSafePrompt_andEnforcesSnap() = runBlocking {
        // gemini-3.5-flash-lite anchors on the nearest maintenance (~BMR) when
        // handed the raw series (measured 2026-08-22 on-device: 6/19 scenarios
        // clamped to the BMR floor, measured anchor ignored), so it must run the
        // SAFE tier like the on-device model.
        prefs.setSelectedAIModel("gemini-3.5-flash-lite")
        val service = newService()
        server.enqueue(MockResponse().setResponseCode(200).setBody(geminiText(goalJson)))

        val result = service.calculateGoals(
            profile(), thinForecast(), heightMetric = true, weightMetric = true,
            weights = weights, foods = foods,
        )

        val body = server.takeRequest().body.readUtf8()
        assertFalse("small cloud models must NOT receive the raw series", body.contains("RAW WEIGH-INS"))
        assertTrue("small cloud models must carry the thin-data gate", body.contains("too thin"))
        assertEquals(GoalRecalcTier.SAFE, result.tier)
        assertEquals("SAFE enforcement must snap thin data to the formula anchor", profile().dailyCalories, result.calories)
    }

    @Test
    fun forcedSafeTier_cloudLegReceivesSafePrompt_andEnforcesSnap() = runBlocking {
        val service = newService()
        service.goalTierOverrideForTest = GoalRecalcTier.SAFE
        server.enqueue(MockResponse().setResponseCode(200).setBody(geminiText(goalJson)))

        val result = service.calculateGoals(
            profile(), thinForecast(), heightMetric = true, weightMetric = true,
            weights = weights, foods = foods,
        )

        val body = server.takeRequest().body.readUtf8()
        assertFalse("forced SAFE must withhold the raw series", body.contains("RAW WEIGH-INS"))
        assertTrue("forced SAFE must carry the thin-data gate", body.contains("too thin"))
        assertEquals(GoalRecalcTier.SAFE, result.tier)
        assertEquals(AIProvider.GEMINI, result.provider)
        assertEquals("SAFE enforcement must snap thin data to the formula anchor", profile().dailyCalories, result.calories)
    }
}

/** Records the last prompt it saw; returns the canned goal JSON. */
private class FakeOnDeviceEngine(private val onGenerate: (String) -> String) : OnDeviceLlmEngine {
    var lastPrompt: String = ""
    override val visionEnabled: Boolean = false
    override suspend fun ensureLoaded(): Long = 0
    override suspend fun generate(systemPrompt: String, userPrompt: String): String {
        lastPrompt = userPrompt
        return onGenerate(userPrompt)
    }
    override suspend fun generateWithImage(userPrompt: String, imageBytes: ByteArray, systemPrompt: String): String =
        error("goal recalculation never sends images")
    override fun close() = Unit
}
