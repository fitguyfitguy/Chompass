package app.chompass.services

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Search-path behavior against a scripted OFF backend: transient-failure
 * retries, the full→shorter candidate chain (brand token dropped first),
 * brand-less result names, scoring.
 */
class OpenFoodFactsSearchTest {
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

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(body)

    private val serviceUnavailable: MockResponse =
        MockResponse().setResponseCode(503).setBody("<!DOCTYPE html><html><body>unavailable</body></html>")

    private fun productsJson(vararg names: Pair<String, String>): String {
        val items = names.map { (name, brand) ->
            """{"code":"${name.hashCode().toLong() and 0x7fffffffL}",
                "product_name":"$name",
                "brands":"$brand",
                "nutriments":{"energy-kcal_100g":200,"proteins_100g":8,"carbohydrates_100g":30,"fat_100g":5}}"""
        }.joinToString(",")
        return """{"count":${names.size},"products":[$items]}"""
    }

    private fun search(
        query: String,
        brand: String? = null,
    ): List<OpenFoodFactsService.SearchHit> = runBlocking {
        OpenFoodFactsService.search(query, brand = brand, client = client, baseUrl = server.url("/").toString())
    }

    private fun lastQueryParameter(name: String): String? {
        // takeRequest(1ms) polls without the default 20s block-on-empty wait.
        var last: String? = null
        while (true) {
            val request = server.takeRequest(1, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            last = request.requestUrl?.queryParameter(name)
        }
        return last
    }

    @Test
    fun search_retriesAfter503_andReturnsHits() {
        server.enqueue(serviceUnavailable)
        server.enqueue(serviceUnavailable)
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))

        val hits = search("Aldi Laugen")

        assertEquals(1, hits.size)
        assertEquals("Laugen Brezen", hits[0].name)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun search_returnsEmpty_onPersistent503() {
        // Every candidate (full, drop-first, drop-last) gets its own retries;
        // when the backend never answers, the search gives up empty.
        repeat(3 * 3) { server.enqueue(serviceUnavailable) }

        val hits = search("Aldi Laugen")

        assertTrue(hits.isEmpty())
        assertEquals(9, server.requestCount)
    }

    @Test
    fun search_fallsBackToShorterQuery_onSuccessfulEmpty() {
        // OFF is up but AND-misses "Aldi Laugen" (empty twice: one retry for
        // OFF's intermittent empty-then-hit behavior), then the brand-ish
        // first token is dropped — "Laugen" alone surfaces the products.
        server.enqueue(jsonResponse("""{"count":0,"products":[]}"""))
        server.enqueue(jsonResponse("""{"count":0,"products":[]}"""))
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))

        val hits = search("Aldi Laugen")

        assertEquals(1, hits.size)
        assertEquals("Laugen Brezen", hits[0].name)
        assertEquals(3, server.requestCount)
        assertEquals("Laugen", lastQueryParameter("search_terms"))
    }

    @Test
    fun search_dropsBrandTokenFirst_whenBrandPassedSeparately() {
        // GroundingTools passes the brand separately: "Aldi Laugen Brezen" is
        // tried first, then the brand is dropped before trailing food terms.
        server.enqueue(jsonResponse("""{"count":0,"products":[]}"""))
        server.enqueue(jsonResponse("""{"count":0,"products":[]}"""))
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))

        val hits = search("Laugen Brezen", brand = "Aldi")

        assertEquals(1, hits.size)
        assertEquals(3, server.requestCount)
        assertEquals("Laugen Brezen", lastQueryParameter("search_terms"))
    }

    @Test
    fun search_advancesToShorterQuery_whenBackendFails() {
        // A 503-ing full query must not end the search: the shorter
        // "Laugen" candidate still gets a chance (OFF often answers shorter
        // queries while the longer one fails).
        server.enqueue(serviceUnavailable)
        server.enqueue(serviceUnavailable)
        server.enqueue(serviceUnavailable)
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))

        val hits = search("Aldi Laugen")

        assertEquals(1, hits.size)
        assertEquals("Laugen Brezen", hits[0].name)
        assertEquals(4, server.requestCount)
        assertEquals("Laugen", lastQueryParameter("search_terms"))
    }

    @Test
    fun search_dropsLastToken_afterFirstTokenMiss() {
        // "aldi laugen" → "laugen" (miss) → "aldi" (brand-only flood, still
        // better than nothing when OFF has no matching products at all).
        server.enqueue(jsonResponse("""{"count":0,"products":[]}"""))
        server.enqueue(jsonResponse("""{"count":0,"products":[]}"""))
        server.enqueue(jsonResponse("""{"count":0,"products":[]}"""))
        server.enqueue(jsonResponse("""{"count":0,"products":[]}"""))
        server.enqueue(jsonResponse(productsJson("Mini Brezen" to "Aldi")))

        val hits = search("Aldi Laugen")

        assertEquals(1, hits.size)
        assertEquals("Mini Brezen", hits[0].name)
        assertEquals("Aldi", lastQueryParameter("search_terms"))
    }

    @Test
    fun search_cancelledMidFlight_cancelsTheCallInsteadOfBlocking() {
        // Backend stalls the body for 3 s; the search is cancelled 300 ms in.
        // The in-flight call must be cancelled with the coroutine (returns
        // promptly) and no retry or shorter candidate may fire afterwards.
        // Before the cancellation-aware request (Codeberg #26) the call would
        // ride out the stall and then keep retrying, holding an IO thread for
        // 9+ s per abandoned keystroke. Called directly (no nested runBlocking):
        // the sheet's LaunchedEffect cancels the same way. The stall is kept
        // under MockWebServer's shutdown wait so tearDown stays clean.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS)
                .setBody("""{"count":0,"products":[]}""")
        )
        runBlocking {
            val job = launch {
                OpenFoodFactsService.search(
                    "Aldi Laugen",
                    client = client,
                    baseUrl = server.url("/").toString(),
                )
            }
            delay(300)
            val start = System.nanoTime()
            job.cancelAndJoin()
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue(
                "cancelled search returned in ${elapsedMs}ms, expected < 5000",
                elapsedMs < 5_000,
            )
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun search_keepsNameBrandless_andBrandSeparate() {
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))

        val hits = search("Aldi Laugen")

        assertEquals(1, hits.size)
        // No "Aldi Aldi Laugen Brezen" — name is the plain product name.
        assertEquals("Laugen Brezen", hits[0].name)
        assertEquals("Aldi", hits[0].brand)
    }

    @Test
    fun search_ranksByTokenOverlap() {
        val body = """{"count":2,"products":[
            {"code":"1","product_name":"Laugen Brezelino","brands":"Aldi",
             "nutriments":{"energy-kcal_100g":200,"proteins_100g":8,"carbohydrates_100g":30,"fat_100g":5}},
            {"code":"2","product_name":"Laugen Brioche","brands":"Aldi",
             "nutriments":{"energy-kcal_100g":300,"proteins_100g":9,"carbohydrates_100g":40,"fat_100g":10}}
        ]}"""
        server.enqueue(jsonResponse(body))

        val hits = search("Laugen Brezel")

        assertEquals(listOf("Laugen Brezelino", "Laugen Brioche"), hits.map { it.name })
        assertTrue(hits[0].score > hits[1].score)
    }

    @Test
    fun search_persistsBrandlessNameThroughMapping() {
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))
        val hit = search("Aldi Laugen").single()

        val result = app.chompass.services.grounding.DatabaseSearchResult.fromOff(hit)

        assertEquals("Laugen Brezen", result.name)
        assertEquals("Aldi", result.brand)
        assertNotNull(result.caloriesPerServing)
        assertNull(result.lang)
    }
}
