package app.chompass.services

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.parity.ParityFixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Meal-share payload in testdata/parity/meal-share-sample.json must encode through
 * [MealShare.link] with matching meal names/calories (no Android Uri / Robolectric).
 */
class MealShareParityTest {

    @Test
    fun paritySampleRoundTripsThroughLinkPayload() {
        val sample = ParityFixtures.readJson("meal-share-sample.json")
        assertEquals(1, sample.getInt("v"))
        val meals = sample.getJSONArray("meals")
        assertTrue(meals.length() >= 1)

        val entries = (0 until meals.length()).map { i ->
            val m = meals.getJSONObject(i)
            FoodEntry(
                id = UUID.randomUUID(),
                name = m.getString("name"),
                calories = m.getInt("calories"),
                protein = m.optDouble("protein", 0.0),
                carbs = m.optDouble("carbs", 0.0),
                fat = m.optDouble("fat", 0.0),
                fiber = if (m.has("fiber")) m.getDouble("fiber") else null,
                sodium = if (m.has("sodium")) m.getDouble("sodium") else null,
                mealType = runCatching {
                    MealType.valueOf(m.optString("mealType", "snack").uppercase())
                }.getOrDefault(MealType.SNACK),
                servingSizeGrams = if (m.has("servingSizeGrams")) m.getDouble("servingSizeGrams") else null,
                customNote = m.optString("customNote").takeIf { it.isNotEmpty() },
                timestamp = Instant.parse("2026-01-01T12:00:00Z"),
                source = FoodSource.MANUAL,
            )
        }

        val link = MealShare.link(entries)
        assertTrue(link.startsWith("${MealShare.SCHEME}://${MealShare.HOST}?d="))
        val encoded = link.substringAfter("d=")
        val decoded = JSONObject(String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8))
        assertEquals(1, decoded.getInt("v"))
        val out = decoded.getJSONArray("meals")
        assertEquals(meals.length(), out.length())
        for (i in 0 until meals.length()) {
            assertEquals(meals.getJSONObject(i).getString("name"), out.getJSONObject(i).getString("name"))
            assertEquals(meals.getJSONObject(i).getInt("calories"), out.getJSONObject(i).getInt("calories"))
        }
    }
}
