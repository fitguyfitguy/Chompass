package app.chompass.services

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.data.setBarcodeCacheImpl
import app.chompass.services.OpenFoodFactsService.LookupException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `lookupWithImage()` end-to-end: the API request still goes to the
 * MockWebServer `baseUrl`, while `images.openfoodfacts.org` photo downloads
 * are short-circuited by an interceptor (upstream test pattern). The photo
 * download must never fail the lookup. Robolectric because the barcode cache
 * needs a real [PreferencesStore].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OpenFoodFactsLookupWithImageTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: PreferencesStore

    private val productJson =
        """{"code":"9339687206605","status":1,"product":{"product_name":"Spirals Australian Pasta","brands":"San Remo","quantity":"500 g","product_quantity":500,"product_quantity_unit":"g","serving_quantity":100,"image_front_url":"https://images.openfoodfacts.org/images/products/933/968/720/6605/front_en.4.400.jpg","nutriments":{"energy-kcal_100g":356,"proteins_100g":12,"carbohydrates_100g":70,"fat_100g":2}}}"""

    private val imageBytes = ByteArray(96) { (it % 251).toByte() }

    private val imageUrl =
        "https://images.openfoodfacts.org/images/products/933/968/720/6605/front_en.4.400.jpg"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        // Shared DataStore across tests in this class: clear the barcode cache
        // so one test's cached lookup can't short-circuit another's request.
        runBlocking { prefs.setBarcodeCacheImpl(emptyMap()) }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Client whose interceptor answers OFF image-host requests without network. */
    private fun imageServingClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                if (request.url.host != "images.openfoodfacts.org") {
                    return@Interceptor chain.proceed(request)
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(imageBytes.toResponseBody("image/jpeg".toMediaType()))
                    .build()
            },
        )
        .build()

    private fun lookupWithImage(client: OkHttpClient) = runBlocking {
        OpenFoodFactsService.lookupWithImage(
            barcode = "9339687206605",
            prefs = prefs,
            client = client,
            baseUrl = server.url("/").toString(),
        )
    }

    @Test
    fun imageDownloadSuccess_returnsBytesBesideAnalysis() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(productJson))
        val result = lookupWithImage(imageServingClient())
        assertEquals("San Remo Spirals Australian Pasta", result.analysis.name)
        assertArrayEquals(imageBytes, result.productImageBytes)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun nonImageContentType_yieldsNullBytesButAnalysisSurvives() {
        val htmlClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.host != "images.openfoodfacts.org") {
                    return@addInterceptor chain.proceed(request)
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("<html></html>".toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()
        server.enqueue(MockResponse().setResponseCode(200).setBody(productJson))
        val result = lookupWithImage(htmlClient)
        assertNotNull(result.analysis.name)
        assertNull(result.productImageBytes)
    }

    @Test
    fun oversizeBody_yieldsNullBytes() {
        // The guard rejects a declared content length above the cap before
        // reading the body (ResponseBody.contentLength lies; source is small).
        val oversizeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.host != "images.openfoodfacts.org") {
                    return@addInterceptor chain.proceed(request)
                }
                val small = imageBytes.toResponseBody("image/jpeg".toMediaType())
                val lying = object : okhttp3.ResponseBody() {
                    override fun contentType() = small.contentType()
                    override fun contentLength() = 6_000_000L
                    override fun source() = small.source()
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(lying)
                    .build()
            }
            .build()
        assertNull(OpenFoodFactsService.productImageBytes(imageUrl, oversizeClient))

        server.enqueue(MockResponse().setResponseCode(200).setBody(productJson))
        val result = lookupWithImage(oversizeClient)
        assertNotNull(result.analysis)
        assertNull(result.productImageBytes)
    }

    @Test
    fun missingImageUrl_yieldsNullBytesAndNoSecondRequest() {
        val noImage = productJson.replace(
            ""","image_front_url":"https://images.openfoodfacts.org/images/products/933/968/720/6605/front_en.4.400.jpg"""",
            "",
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(noImage))
        val result = lookupWithImage(imageServingClient())
        assertEquals("San Remo Spirals Australian Pasta", result.analysis.name)
        assertNull(result.productImageBytes)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun notFoundProduct_stillThrowsLookupException() {
        server.enqueue(MockResponse().setResponseCode(404))
        try {
            lookupWithImage(imageServingClient())
            fail("expected LookupException")
        } catch (e: LookupException) {
            assertTrue(e.message!!.contains("not found"))
        }
        assertEquals(1, server.requestCount)
    }
}
