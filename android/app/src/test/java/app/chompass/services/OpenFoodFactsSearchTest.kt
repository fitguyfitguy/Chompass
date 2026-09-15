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
        OpenFoodFactsService.resetRateLimitForTest()
    }

    @After
    fun tearDown() {
        server.shutdown()
        OpenFoodFactsService.resetRateLimitForTest()
    }

    /** Fake wall clock for rate-limit breaker tests. */
    private var nowMs = 0L

    private fun useFakeClock() {
        nowMs = 1_000_000L
        OpenFoodFactsService.rateLimitNowMs = { nowMs }
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(body)

    private val serviceUnavailable: MockResponse =
        MockResponse().setResponseCode(503).setBody("<!DOCTYPE html><html><body>unavailable</body></html>")

    private val garbage: MockResponse =
        MockResponse().setResponseCode(200).setBody("<!DOCTYPE html><html><body>not json</body></html>")

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
    fun search_retries503Once_andReturnsHits() {
        // 503 = OFF's global request limit: one retry is allowed...
        server.enqueue(serviceUnavailable)
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))

        val hits = search("Aldi Laugen")

        assertEquals(1, hits.size)
        assertEquals("Laugen Brezen", hits[0].name)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun search_stopsWalk_when503PersistsAfterRetry() {
        // ...a second 503 means the shared endpoint is saturated: the whole
        // candidate walk stops instead of pushing more queries at it.
        repeat(6) { server.enqueue(serviceUnavailable) }

        val hits = search("Aldi Laugen")

        assertTrue(hits.isEmpty())
        assertEquals(2, server.requestCount)
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
        // Non-JSON 200s are ordinary backend failures (not a 503 global
        // limit): candidate 1 burns its retries, then the shorter "Laugen"
        // candidate still gets its chance within the attempt budget.
        server.enqueue(garbage)
        server.enqueue(garbage)
        server.enqueue(garbage)
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))

        val hits = search("Aldi Laugen")

        assertEquals(1, hits.size)
        assertEquals("Laugen Brezen", hits[0].name)
        assertEquals(4, server.requestCount)
        assertEquals("Laugen", lastQueryParameter("search_terms"))
    }

    @Test
    fun search_capsTotalNetworkAttempts_atFour() {
        // Budget: at most 4 network attempts per search() call across all
        // candidates and retries. "Aldi Laugen" would walk three candidates
        // (2 attempts each on empty: one try + the empty retry) — the cap
        // stops it before the third candidate ("Aldi") is ever asked.
        repeat(6) { server.enqueue(jsonResponse("""{"count":0,"products":[]}""")) }

        val hits = search("Aldi Laugen")

        assertTrue(hits.isEmpty())
        assertEquals(4, server.requestCount)
        assertEquals("Laugen", lastQueryParameter("search_terms"))
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

    @Test
    fun search_429_tripsBreaker_nextSearchMakesNoRequest() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))

        val tripped = search("Aldi Laugen")

        assertTrue(tripped.isEmpty())
        assertEquals(1, server.requestCount)

        // While the cooldown lasts, a fresh search answers empty without
        // touching the network at all.
        val shortCircuited = search("Aldi Laugen")
        assertTrue(shortCircuited.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun search_breakerHonorsRetryAfterHeader() {
        useFakeClock()
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        assertTrue(search("Aldi Laugen").isEmpty())

        // One second before the 30 s cooldown ends: still blocked.
        nowMs += 29_000
        assertTrue(search("Aldi Laugen").isEmpty())
        assertEquals(1, server.requestCount)

        // Past the cooldown: the breaker is open again and the request goes out.
        nowMs += 2_000
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))
        assertEquals(1, search("Aldi Laugen").size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun search_breakerExpires_afterDefaultCooldown() {
        useFakeClock()
        // No Retry-After header: the default 60 s cooldown applies.
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(search("Aldi Laugen").isEmpty())

        nowMs += 59_000
        assertTrue(search("Aldi Laugen").isEmpty())
        assertEquals(1, server.requestCount)

        nowMs += 2_000
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))
        assertEquals(1, search("Aldi Laugen").size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun search_breakerCooldown_isCappedAt120Seconds() {
        useFakeClock()
        // A hostile/huge Retry-After must not lock searches out for hours.
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "9999"))
        assertTrue(search("Aldi Laugen").isEmpty())

        nowMs += 119_000
        assertTrue(search("Aldi Laugen").isEmpty())
        assertEquals(1, server.requestCount)

        nowMs += 2_000
        server.enqueue(jsonResponse(productsJson("Laugen Brezen" to "Aldi")))
        assertEquals(1, search("Aldi Laugen").size)
        assertEquals(2, server.requestCount)
    }
}
