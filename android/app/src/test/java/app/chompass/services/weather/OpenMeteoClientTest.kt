package app.chompass.services.weather

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Open-Meteo client against a scripted backend: geocoding + today's high. */
class OpenMeteoClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OpenMeteoClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenMeteoClient(
            OkHttpClient.Builder().build(),
            geocodingBaseUrl = server.url("/geocoding").toString(),
            forecastBaseUrl = server.url("/forecast").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun searchCities_parsesGeocodingResults() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"results":[
                    {"id":2950159,"name":"Berlin","latitude":52.52,"longitude":13.405,
                     "country":"Germany","admin1":"Berlin","timezone":"Europe/Berlin"},
                    {"id":2950158,"name":"Berlin Süd","latitude":52.4,"longitude":13.3,
                     "country":"Germany","timezone":"Europe/Berlin"}
                ]}""",
            ),
        )

        val cities = client.searchCities("Berlin")

        assertEquals(2, cities.size)
        assertEquals("Berlin", cities[0].name)
        assertEquals("Berlin, Berlin, Germany", cities[0].displayName)
        assertEquals(2950159L, cities[0].id)
        assertEquals(52.52, cities[0].latitude, 0.001)
        assertEquals("Europe/Berlin", cities[0].timezone)
        assertEquals("Berlin Süd, Germany", cities[1].displayName)
    }

    @Test
    fun searchCities_blankQuery_returnsEmptyWithoutCallingServer() = runBlocking {
        assertTrue(client.searchCities("   ").isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun searchCities_serverError_returnsEmpty() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertTrue(client.searchCities("Berlin").isEmpty())
    }

    @Test
    fun todayHighC_parsesDailyMax() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"latitude":52.52,"longitude":13.4,"timezone":"Europe/Berlin",
                    "daily":{"time":["2026-08-14"],"temperature_2m_max":[28.4]}}""",
            ),
        )
        val city = OmCity(
            id = 1, name = "Berlin", country = "Germany", latitude = 52.52,
            longitude = 13.405, timezone = "Europe/Berlin",
        )
        assertEquals(28, client.todayHighC(city))
    }

    @Test
    fun todayHighC_missingDaily_returnsNull() = runBlocking {
        server.enqueue(jsonResponse("""{"daily":{}}"""))
        val city = OmCity(id = 1, name = "X", latitude = 0.0, longitude = 0.0, timezone = "UTC")
        assertNull(client.todayHighC(city))
    }

    @Test
    fun todayHighC_serverError_returnsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        val city = OmCity(id = 1, name = "X", latitude = 0.0, longitude = 0.0, timezone = "UTC")
        assertNull(client.todayHighC(city))
    }
}
