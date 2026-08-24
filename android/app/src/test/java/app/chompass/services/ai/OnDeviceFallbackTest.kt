package app.chompass.services.ai

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.services.ondevice.ModelCatalog
import app.chompass.services.ondevice.ModelDownloadManager
import app.chompass.services.ondevice.OnDeviceLlmEngine
import app.chompass.services.ondevice.OnDeviceLlmGateway
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

/**
 * Codeberg #54: an on-device primary that cannot load (E4B + photo on a
 * memory-starved device) must fall back to the configured on-device fallback
 * model (E2B) instead of re-attempting the same model. Drives the real
 * `FoodAnalysisService.dispatch` path with a fake engine; the gateway's
 * memory preflights run for real against Robolectric's MemoryInfo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OnDeviceFallbackTest {
    private lateinit var prefs: PreferencesStore
    private val app: Application get() = RuntimeEnvironment.getApplication()

    private val foodJson = """
      {
        "name": "Pizza",
        "calories": 800,
        "protein": 30.0,
        "carbs": 90.0,
        "fat": 35.0,
        "serving_size_grams": 360.0,
        "unit_options": []
      }
    """.trimIndent()

    private class FakeEngine(private val onGenerate: (String) -> String) : OnDeviceLlmEngine {
        var visionCalls = 0
        var lastPrompt: String = ""
        override val visionEnabled: Boolean = true
        override suspend fun ensureLoaded(): Long = 0L
        override suspend fun generate(systemPrompt: String, userPrompt: String): String =
            error("photo analysis never sends text-only calls")
        override suspend fun generateWithImage(userPrompt: String, imageBytes: ByteArray, systemPrompt: String): String {
            visionCalls++
            lastPrompt = userPrompt
            return onGenerate(userPrompt)
        }
        override fun close() = Unit
    }

    @Before
    fun setUp() {
        // Robolectric reports armeabi-v7a; the app only supports arm64-v8a /
        // x86_64, so the capability gate would reject every model. Override so
        // the E4B-vs-E2B fallback test exercises real capability logic.
        ShadowBuild.setSupportedAbis(arrayOf("arm64-v8a", "x86_64"))
        prefs = PreferencesStore(app)
        runBlocking {
            prefs.setSelectedAIProvider(AIProvider.ON_DEVICE)
            prefs.setSelectedAIModel(ModelCatalog.E4B.modelId)
            prefs.setFallbackEnabled(true)
            prefs.setSelectedFallbackProvider(AIProvider.ON_DEVICE)
            prefs.setSelectedFallbackModel(ModelCatalog.E2B.modelId)
        }
        // isDownloaded() only checks existence — stub files suffice (no native load).
        listOf(ModelCatalog.E2B, ModelCatalog.E4B).forEach { stubModel(it) }
        // Free memory fits E2B vision (size + 1.5 GiB headroom ≈ 3.9 GiB) but
        // not E4B vision (≈ 4.9 GiB) — the reporter's #54 memory state.
        val mem = ActivityManager.MemoryInfo().apply {
            totalMem = 8L * 1024 * 1024 * 1024
            availMem = ModelCatalog.E2B.sizeBytes + 2_000L * 1024 * 1024
        }
        shadowOf(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).setMemoryInfo(mem)
    }

    @After
    fun tearDown() {
        File(app.filesDir, "models").deleteRecursively()
        runBlocking {
            prefs.setSelectedAIProvider(AIProvider.GEMINI)
            prefs.setFallbackEnabled(false)
        }
    }

    private fun stubModel(entry: app.chompass.services.ondevice.OnDeviceModelEntry) {
        val file = ModelDownloadManager(app).modelFile(entry)
        file.parentFile?.mkdirs()
        if (!file.exists()) file.writeBytes(byteArrayOf(1))
    }

    private fun serviceWith(
        gateway: OnDeviceLlmGateway,
        onDeviceModelDownloaded: (String) -> Boolean = { true },
    ): FoodAnalysisService = FoodAnalysisService(
        prefs = prefs,
        okHttp = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build(),
        onDeviceGateway = gateway,
        onDeviceModelDownloaded = onDeviceModelDownloaded,
        keyLookup = { "test-key" }, // ON_DEVICE needs no key; satisfies the init guard
    )

    /**
     * The #54 repro: E4B primary + photo + free memory too low for E4B vision.
     * The fallback leg must actually load E2B (the gateway must honor the
     * model id the dispatcher resolved) and the analysis must succeed.
     */
    @Test
    fun onDevicePhotoAnalysis_fallsBackFromE4B_toE2B_whenPrimaryVisionWouldOom() = runBlocking {
        val engine = FakeEngine { foodJson }
        val paths = mutableListOf<String>()
        val gateway = OnDeviceLlmGateway(
            context = app,
            prefs = prefs,
            engineFactory = { modelPath, _, _, _ ->
                paths += modelPath
                engine
            },
        )
        val service = serviceWith(gateway)

        val result = service.analyzeFood(imageBytes = byteArrayOf(1, 2, 3))

        assertEquals("Pizza", result.name)
        assertEquals("exactly one engine — the fallback E2B", 1, paths.size)
        assertTrue(
            "must load the E2B file, not the E4B primary",
            paths.single().endsWith(ModelCatalog.E2B.filename),
        )
        assertEquals("vision call must have run on the fallback engine", 1, engine.visionCalls)
        assertTrue("the photo prompt must reach the engine", engine.lastPrompt.contains("Analyze this food image"))
    }

    /** Guard: without a downloaded fallback model, the low-memory error surfaces. */
    @Test
    fun onDevicePhotoAnalysis_withoutDownloadedFallback_surfacesLowMemoryError() = runBlocking {
        val engine = FakeEngine { foodJson }
        val gateway = OnDeviceLlmGateway(app, prefs, engineFactory = { _, _, _, _ -> engine })
        val service = serviceWith(gateway, onDeviceModelDownloaded = { false })

        try {
            service.analyzeFood(imageBytes = byteArrayOf(1, 2, 3))
            org.junit.Assert.fail("expected AiError.OnDeviceLowMemory")
        } catch (e: AiError.OnDeviceLowMemory) {
            // expected — no fallback available, the primary error surfaces
        }
        assertEquals("no vision call may run without a loadable model", 0, engine.visionCalls)
    }
}
