package app.chompass.services

import androidx.datastore.preferences.core.edit
import app.chompass.data.BARCODE_CACHE_SCHEMA
import app.chompass.data.CachedBarcodeProduct
import app.chompass.data.Keys
import app.chompass.data.PreferencesStore
import app.chompass.data.setBarcodeCacheImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The barcode cache must not pin stale portion bases: entries written before
 * [BARCODE_CACHE_SCHEMA] (pre-#168 analyses at the 100 g guess) re-lookup
 * online, serve offline rather than failing the scan, and get rewritten at
 * the current schema on the next successful lookup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class OpenFoodFactsBarcodeCacheTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var prefs: PreferencesStore

    private val code = "3017620422003"

    // Distinct names prove which side of the cache a lookup served.
    private val staleJson =
        """{"code":"$code","status":1,"product":{"product_name":"Nutella Stale","quantity":"400 g","nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9}}}"""
    private val freshJson =
        """{"code":"$code","status":1,"product":{"product_name":"Nutella Fresh","quantity":"400 g e","nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9}}}"""

    @Before
    fun setUp() = runBlocking {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        // DataStore is a process-wide singleton: start every test clean.
        prefs.dataStore.edit { it.remove(Keys.BARCODE_CACHE) }
        OpenFoodFactsService.resetRateLimitForTest()
    }

    @After
    fun tearDown() {
        server.shutdown()
        OpenFoodFactsService.resetRateLimitForTest()
    }

    /** Caches a real analysis, then downgrades it to the pre-schema shape. */
    private fun seedStale() = runBlocking {
        // analysis() takes the product object, like parseLookupResponse unwraps it.
        val analysis = OpenFoodFactsService.analysis(JSONObject(staleJson).getJSONObject("product"), code)
        prefs.cacheBarcodeLookup(code, analysis)
        val downgraded = prefs.barcodeCache.first().mapValues { entry ->
            entry.value.copy(schemaVersion = 0)
        }
        prefs.setBarcodeCacheImpl(downgraded)
    }

    private fun lookupByCode() = runBlocking {
        OpenFoodFactsService.lookupByCode(
            code,
            prefs,
            client = client,
            baseUrl = server.url("/").toString(),
        )
    }

    @Test
    fun lookup_staleEntry_relookupsOnline_andRewritesAtCurrentSchema() = runBlocking {
        seedStale()
        server.enqueue(MockResponse().setResponseCode(200).setBody(freshJson))

        val analysis = lookupByCode()

        assertEquals("Nutella Fresh", analysis.name)
        assertEquals(1, server.requestCount)
        val stored = prefs.barcodeCache.first()[code]!!
        assertEquals(BARCODE_CACHE_SCHEMA, stored.schemaVersion)
        assertEquals("Nutella Fresh", stored.analysis.name)
    }

    @Test
    fun lookup_staleEntry_servesOffline_insteadOfFailingTheScan() = runBlocking {
        seedStale()
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }

        val analysis = lookupByCode()

        // Network exhausted: the stale entry still answers (offline rescan),
        // and it stays at the old schema until an online scan rewrites it.
        assertEquals("Nutella Stale", analysis.name)
        assertEquals(3, server.requestCount)
        assertEquals(0, prefs.barcodeCache.first()[code]!!.schemaVersion)
    }

    @Test
    fun lookup_freshEntry_shortCircuitsWithoutNetwork() = runBlocking {
        val analysis = OpenFoodFactsService.analysis(JSONObject(freshJson).getJSONObject("product"), code)
        prefs.cacheBarcodeLookup(code, analysis)
        server.enqueue(MockResponse().setResponseCode(200).setBody(freshJson))

        val served = lookupByCode()

        assertEquals("Nutella Fresh", served.name)
        assertEquals(0, server.requestCount)
    }
}
