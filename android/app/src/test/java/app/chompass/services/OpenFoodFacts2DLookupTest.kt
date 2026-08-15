package app.chompass.services

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.services.OpenFoodFactsService.LookupException
import app.chompass.services.ai.FoodAnalysis
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 2D matrix-code path through `OpenFoodFactsService.lookup()` (see
 * `docs/local/PLAN_2D_MATRIX_CODES.md`): GS1 / QR text is normalized to the
 * GTIN before the cache check and the OFF request; non-product codes fail
 * with "could not be read" and no network call. Robolectric because `lookup()`
 * needs a real [PreferencesStore].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OpenFoodFacts2DLookupTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var prefs: PreferencesStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val productJson =
        """{"code":"9339687206605","status":1,"product":{"product_name":"Spirals Australian Pasta","brands":"San Remo","quantity":"500 g","serving_quantity":100,"nutriments":{"energy-kcal_100g":356,"proteins_100g":12,"carbohydrates_100g":70,"fat_100g":2}}}"""

    private fun lookup(barcode: String): FoodAnalysis = runBlocking {
        OpenFoodFactsService.lookup(
            barcode = barcode,
            prefs = prefs,
            client = client,
            baseUrl = server.url("/").toString(),
        )
    }

    @Test
    fun lookup_2dGs1Text_hitsServerWithNormalizedGtin() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(productJson))
        val analysis = lookup("(01)09400597028233(15)260821(10)96735717")
        assertEquals("San Remo Spirals Australian Pasta", analysis.name)
        val path = server.takeRequest().path!!
        assertTrue(
            "expected normalized GTIN path, got $path",
            path.contains("/api/v2/product/9400597028233.json?fields="),
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun lookup_nonProductCode_throwsWithoutNetworkCall() {
        try {
            lookup("1111201I")
            fail("expected LookupException")
        } catch (e: LookupException) {
            assertEquals("That barcode could not be read. Try scanning it again.", e.message)
        }
        assertEquals(0, server.requestCount)
    }
}
