package app.chompass.services.ondevice

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.services.ai.AiError
import com.google.ai.edge.litertlm.Backend
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * #46 gateway behavior: GPU→CPU retry, no retry for vision, and the
 * defensive E4B→E2B resolution on sub-E4B devices.
 *
 * The LiteRT-LM native libs are never touched — [OnDeviceLlmGateway] gets a
 * fake [OnDeviceLlmEngine] via its engineFactory seam.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OnDeviceLlmGatewayTest {
    private lateinit var prefs: PreferencesStore
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        prefs = PreferencesStore(app)
        runBlocking {
            prefs.setSelectedAIProvider(AIProvider.ON_DEVICE)
            prefs.setSelectedAIModel(ModelCatalog.E2B.modelId)
        }
        // isDownloaded() only checks existence — a stub file suffices (no native load).
        File(app.filesDir, "models").apply { mkdirs() }
            .let { File(it, ModelCatalog.E2B.filename).writeText("stub") }
    }

    @After
    fun tearDown() {
        File(app.filesDir, "models").deleteRecursively()
        runBlocking { prefs.setSelectedAIProvider(AIProvider.GEMINI) }
    }

    private fun setMemoryInfo(totalMem: Long, availMem: Long, lowMemory: Boolean = false) {
        val mem = ActivityManager.MemoryInfo().apply {
            this.totalMem = totalMem
            this.availMem = availMem
            this.lowMemory = lowMemory
        }
        shadowOf(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).setMemoryInfo(mem)
    }

    /** Vision preflight: availMem ≥ model size + 1.5 GiB headroom. */
    private fun withEnoughFreeMemoryForVision() =
        setMemoryInfo(8L * GB, ModelCatalog.E2B.sizeBytes + 4_000L * MB)

    private class FakeEngine(
        override val visionEnabled: Boolean,
        private val failures: MutableList<Throwable>,
    ) : OnDeviceLlmEngine {
        var closed = false
        var loaded = false
        override suspend fun ensureLoaded(): Long {
            if (failures.isNotEmpty()) throw failures.removeAt(0)
            loaded = true
            return 42L
        }
        override suspend fun generate(systemPrompt: String, userPrompt: String): String = """{"ok":true}"""
        override suspend fun generateWithImage(userPrompt: String, imageBytes: ByteArray, systemPrompt: String): String = """{"ok":true}"""
        override fun close() { closed = true }
    }

    private fun gateway(
        failures: List<Throwable>,
        engines: MutableList<FakeEngine>,
        backends: MutableList<Backend>,
        modelPaths: MutableList<String>,
    ): OnDeviceLlmGateway {
        val remaining = failures.toMutableList()
        return OnDeviceLlmGateway(
            context = app,
            prefs = prefs,
            engineFactory = { modelPath, _, backend, vision ->
                modelPaths += modelPath
                backends += backend
                FakeEngine(vision, remaining).also { engines += it }
            },
        )
    }

    @Test
    fun gpuInitFailure_retriesOnceOnCpu_forText() = runBlocking {
        val engines = mutableListOf<FakeEngine>()
        val backends = mutableListOf<Backend>()
        val paths = mutableListOf<String>()
        val g = gateway(listOf(IllegalStateException("OpenCL init failed")), engines, backends, paths)

        val out = g.generate("sys", "user")

        assertEquals("""{"ok":true}""", out)
        assertEquals("GPU first, then CPU fallback", 2, backends.size)
        assertTrue("first attempt must be GPU", backends[0] is Backend.GPU)
        assertTrue("fallback must be CPU", backends[1] is Backend.CPU)
        assertEquals(2, engines.size)
        assertTrue("failed GPU engine must be closed", engines[0].closed)
        assertTrue("CPU engine must be loaded", engines[1].loaded)
        assertTrue(g.isLoaded)
    }

    @Test
    fun gpuInitFailure_isNotRetried_forVision() = runBlocking {
        withEnoughFreeMemoryForVision()
        val engines = mutableListOf<FakeEngine>()
        val backends = mutableListOf<Backend>()
        val paths = mutableListOf<String>()
        val g = gateway(listOf(IllegalStateException("OpenCL init failed")), engines, backends, paths)

        try {
            g.generateWithImage("user", byteArrayOf(1, 2, 3))
            fail("expected AiError.OnDeviceEngineInit")
        } catch (e: AiError.OnDeviceEngineInit) {
            // expected — vision never retries on CPU (upstream #2056)
        }
        assertEquals("vision must not retry on CPU", 1, backends.size)
        assertTrue(backends[0] is Backend.GPU)
        assertEquals(1, engines.size)
        assertTrue(engines[0].closed)
    }

    @Test
    fun visionPreflight_rejectsWhenFreeMemoryIsLow() = runBlocking {
        setMemoryInfo(8L * GB, availMem = ModelCatalog.E2B.sizeBytes + 100L * MB)
        val g = gateway(emptyList(), mutableListOf(), mutableListOf(), mutableListOf())

        try {
            g.generateWithImage("user", byteArrayOf(1, 2, 3))
            fail("expected AiError.OnDeviceLowMemory")
        } catch (e: AiError.OnDeviceLowMemory) {
            // expected — catchable, not an OS kill
        }
    }

    @Test
    fun persistedE4B_onSubE4BDevice_resolvesToE2B() = runBlocking {
        // 6 GB-class device (≈5.5 GiB usable): above E2B floor, below E4B floor.
        setMemoryInfo(totalMem = (5.5 * GB).toLong(), availMem = (5.5 * GB).toLong())
        prefs.setSelectedAIModel(ModelCatalog.E4B.modelId)

        val engines = mutableListOf<FakeEngine>()
        val backends = mutableListOf<Backend>()
        val paths = mutableListOf<String>()
        val g = gateway(emptyList(), engines, backends, paths)

        g.generate("sys", "user")

        assertEquals("exactly one engine — for E2B", 1, engines.size)
        assertTrue("must load the E2B file, not E4B", paths.single().endsWith(ModelCatalog.E2B.filename))
        assertTrue(engines[0].loaded)
    }

    private companion object {
        const val MB = 1024L * 1024
        const val GB = 1024L * MB
    }
}
