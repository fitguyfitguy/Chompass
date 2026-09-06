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
import org.junit.Assert.assertNull
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
        """{"code":"9339687206605","status":1,"product":{"product_name":"Spirals Australian Pasta","brands":"San Remo","quantity":"250 g","product_quantity":250,"product_quantity_unit":"g","serving_quantity":100,"ingredients_text":"Durum wheat semolina, water.","allergens_tags":["en:milk"],"traces_tags":["en:nuts"],"nutriscore_grade":"a","nova_group":2,"ecoscore_grade":"b","labels_tags":["en:organic"],"categories_tags":["en:cereals"],"image_front_url":"https://images.openfoodfacts.org/images/products/933/968/720/6605/front_en.4.400.jpg","nutriments":{"energy-kcal_100g":356,"proteins_100g":12,"carbohydrates_100g":70,"fat_100g":2}}}"""

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
    fun lookup_requestsExactFieldsParameter() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(productJson))
        lookup()
        val requested = server.takeRequest().requestUrl!!
        assertEquals(
            "product_name,generic_name,brands,quantity,product_quantity,product_quantity_unit," +
                "serving_size,serving_quantity,nutriments,ingredients_text,allergens_tags," +
                "traces_tags,nutriscore_grade,nova_group,ecoscore_grade,labels_tags," +
                "categories_tags,image_front_url",
            requested.queryParameter("fields"),
        )
    }

    @Test
    fun lookup_buildsProductMetadataAndPackageOption() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(productJson))
        val analysis = lookup()

        // Package grams (250) differ from the serving (100): both options ship.
        assertEquals(listOf("serving", "package"), analysis.servingUnitOptions.map { it.unit })
        assertEquals(250.0, analysis.servingUnitOptions[1].gramsPerUnit, 0.001)
        assertEquals("serving", analysis.selectedServingUnit)

        val meta = analysis.productMetadata!!
        assertEquals("9339687206605", meta.barcode)
        assertEquals("250 g", meta.packageQuantity)
        assertEquals("Durum wheat semolina, water.", meta.ingredientsText)
        assertEquals(listOf("Milk"), meta.allergens)
        assertEquals(listOf("Nuts"), meta.traces)
        assertEquals("A", meta.nutriScore)
        assertEquals(2, meta.novaGroup)
        assertEquals("B", meta.ecoScore)
        assertEquals(listOf("Organic"), meta.labels)
        assertEquals(listOf("Cereals"), meta.categories)
        assertEquals(
            "https://images.openfoodfacts.org/images/products/933/968/720/6605/front_en.4.400.jpg",
            meta.imageUrl,
        )
        assertTrue(meta.hasDisplayDetails)
    }

    @Test
    fun lookup_packageOptionSkippedWhenEqualToServing() {
        val sameSize = productJson.replace(""""quantity":"250 g","product_quantity":250,"product_quantity_unit":"g","serving_quantity":100,""", """"quantity":"100 g","product_quantity":100,"product_quantity_unit":"g","serving_quantity":100,""")
        server.enqueue(MockResponse().setResponseCode(200).setBody(sameSize))
        val analysis = lookup()
        assertEquals(listOf("serving"), analysis.servingUnitOptions.map { it.unit })
    }

    @Test
    fun analysis_normalizesScoresAndClampsNova() {
        // `unknown` / `not-applicable` grades drop to null; out-of-range NOVA drops.
        val product = org.json.JSONObject(
            """{"product_name":"X","quantity":"330 ml","nutriscore_grade":"unknown",
               "ecoscore_grade":"not-applicable","nova_group":7,
               "nutriments":{"energy-kcal_100g":10,"proteins_100g":1,"carbohydrates_100g":2,"fat_100g":3}}""",
        )
        val analysis = OpenFoodFactsService.analysis(product, "123")
        assertNull(analysis.productMetadata!!.nutriScore)
        assertNull(analysis.productMetadata!!.ecoScore)
        assertNull(analysis.productMetadata!!.novaGroup)
        // 330 ml display quantity parses through the strict fallback: no package
        // option here because serving defaults to 100 g and 330 != 100.
        assertEquals(listOf("serving", "package"), analysis.servingUnitOptions.map { it.unit })
        assertEquals(330.0, analysis.servingUnitOptions[1].gramsPerUnit, 0.001)
    }

    @Test
    fun productImageBytes_rejectsForeignAndNonHttpsHosts() {
        val client = OkHttpClient.Builder().build()
        assertNull(OpenFoodFactsService.productImageBytes("https://evil.example/front.jpg", client))
        assertNull(OpenFoodFactsService.productImageBytes("http://images.openfoodfacts.org/front.jpg", client))
        assertNull(OpenFoodFactsService.productImageBytes("not a url", client))
        assertNull(OpenFoodFactsService.productImageBytes(null, client))
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
