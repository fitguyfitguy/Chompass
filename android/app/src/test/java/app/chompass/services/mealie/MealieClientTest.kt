package app.chompass.services.mealie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealieClientTest {
    @Test
    fun listUrl_normalizesHostAndDisablesPagination() {
        val url = MealieClient.listUrl("mealie.home.arpa")
        assertTrue(url.startsWith("https://mealie.home.arpa/api/recipes"))
        assertTrue(url.contains("perPage=-1"))
    }

    @Test
    fun detailUrl_encodesSlugPath() {
        assertEquals(
            "https://mealie.example/api/recipes/evening-salad",
            MealieClient.detailUrl("https://mealie.example/", "evening-salad"),
        )
    }

    @Test
    fun listUrl_keepsExplicitHttp() {
        val url = MealieClient.listUrl("http://192.168.1.10:9000")
        assertTrue(url.startsWith("http://192.168.1.10:9000/api/recipes"))
    }
}
