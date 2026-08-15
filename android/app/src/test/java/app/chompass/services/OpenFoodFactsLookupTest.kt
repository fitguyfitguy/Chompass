package app.chompass.services

import app.chompass.services.OpenFoodFactsService.LookupException
import app.chompass.services.ai.FoodAnalysis
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Barcode-lookup path against a scripted OFF backend: 404 vs 5xx error
 * splitting and retry-with-backoff (Codeberg #24).
 */
class OpenFoodFactsLookupTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val productJson =
        """{"code":"9339687206605","status":1,"product":{"product_name":"Spirals Australian Pasta","brands":"San Remo","quantity":"500 g","serving_quantity":100,"nutriments":{"energy-kcal_100g":356,"proteins_100g":12,"carbohydrates_100g":70,"fat_100g":2}}}"""

    private fun lookup(): FoodAnalysis = runBlocking {
        OpenFoodFactsService.lookupNetwork(
            code = "9339687206605",
            client = client,
            baseUrl = server.url("/").toString(),
        )
    }

    private fun assertLookupFails(expectedMessage: String) {
        try {
            lookup()
            fail("expected LookupException: $expectedMessage")
        } catch (e: LookupException) {
            assertEquals(expectedMessage, e.message)
        }
    }

    @Test
    fun lookup_200_returnsAnalysis() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(productJson))
        val analysis = lookup()
        assertEquals("San Remo Spirals Australian Pasta", analysis.name)
        assertEquals(356, analysis.calories)
        assertEquals(100.0, analysis.servingSizeGrams!!, 0.001)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun lookup_404_throwsProductNotFound_withoutRetry() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertLookupFails("Product not found in Open Food Facts. Scan the nutrition label instead.")
        assertEquals(1, server.requestCount)
    }

    @Test
    fun lookup_200_status0_throwsProductNotFound() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"9421011990608","status":0}"""))
        assertLookupFails("Product not found in Open Food Facts. Scan the nutrition label instead.")
        assertEquals(1, server.requestCount)
    }

    @Test
    fun lookup_retries503_thenSucceeds() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody(productJson))
        val analysis = lookup()
        assertEquals("San Remo Spirals Australian Pasta", analysis.name)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun lookup_retries429_thenSucceeds() {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(200).setBody(productJson))
        assertNotNull(lookup())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun lookup_persistent503_throwsTroubleMessage() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        assertLookupFails("Open Food Facts is having trouble right now. Try again in a moment.")
        assertEquals(3, server.requestCount)
    }

    @Test
    fun lookup_other4xx_throwsUnexpectedResponse() {
        server.enqueue(MockResponse().setResponseCode(400))
        assertLookupFails("Open Food Facts returned an unexpected response.")
        assertEquals(1, server.requestCount)
    }

    @Test
    fun lookup_networkFailure_retries_thenFails() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        try {
            lookup()
            fail("expected LookupException")
        } catch (e: LookupException) {
            assertTrue(e.message!!.startsWith("Barcode lookup failed:"))
        }
        assertEquals(3, server.requestCount)
    }
}
