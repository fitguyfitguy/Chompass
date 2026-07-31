package app.chompass.services

import app.chompass.models.FoodConstituent
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.models.ServingUnitOption
import app.chompass.parity.ParityFixtures
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Meal-share payload in testdata/parity/meal-share-sample.json must encode through
 * [MealShare.link] with matching meal names/calories/serving units/constituents.
 */
class MealShareParityTest {
    @Test
    fun paritySampleRoundTripsThroughLinkPayload() {
        val sample = ParityFixtures.readJson("meal-share-sample.json")
        assertEquals(2, sample.getInt("v"))
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
                servingUnitOptions = parseUnits(m.optJSONArray("servingUnitOptions")),
                selectedServingUnit = m.optString("selectedServingUnit").takeIf { it.isNotEmpty() },
                selectedServingQuantity = if (m.has("selectedServingQuantity")) {
                    m.getDouble("selectedServingQuantity")
                } else {
                    null
                },
                constituents = parseConstituents(m.optJSONArray("constituents")),
                customNote = m.optString("customNote").takeIf { it.isNotEmpty() },
                timestamp = Instant.parse("2026-01-01T12:00:00Z"),
                source = FoodSource.MANUAL,
            )
        }

        val link = MealShare.link(entries)
        assertTrue(link.startsWith("${MealShare.SCHEME}://${MealShare.HOST}?d="))
        val encoded = link.substringAfter("d=")
        val decoded = JSONObject(String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8))
        assertEquals(2, decoded.getInt("v"))
        val out = decoded.getJSONArray("meals")
        assertEquals(meals.length(), out.length())
        for (i in 0 until meals.length()) {
            val src = meals.getJSONObject(i)
            val got = out.getJSONObject(i)
            assertEquals(src.getString("name"), got.getString("name"))
            assertEquals(src.getInt("calories"), got.getInt("calories"))
            if (src.has("selectedServingUnit")) {
                assertEquals(src.getString("selectedServingUnit"), got.getString("selectedServingUnit"))
            }
            if (src.has("servingUnitOptions")) {
                assertEquals(
                    src.getJSONArray("servingUnitOptions").length(),
                    got.getJSONArray("servingUnitOptions").length(),
                )
            }
            val srcConst = src.optJSONArray("constituents") ?: JSONArray()
            val gotConst = got.optJSONArray("constituents") ?: JSONArray()
            assertEquals(srcConst.length(), gotConst.length())
            if (srcConst.length() > 0) {
                assertEquals(
                    srcConst.getJSONObject(0).getString("name"),
                    gotConst.getJSONObject(0).getString("name"),
                )
                assertEquals(
                    srcConst.getJSONObject(0).getInt("calories"),
                    gotConst.getJSONObject(0).getInt("calories"),
                )
            }
        }
    }

    private fun parseUnits(arr: JSONArray?): List<ServingUnitOption> {
        if (arr == null) return emptyList()
        val out = mutableListOf<ServingUnitOption>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val unit = o.optString("unit").takeIf { it.isNotBlank() } ?: continue
            val gpu = o.optDouble("gramsPerUnit", Double.NaN)
            if (!gpu.isFinite() || gpu <= 0) continue
            out += ServingUnitOption(
                unit = unit,
                gramsPerUnit = gpu,
                quantity = if (o.has("quantity")) o.getDouble("quantity") else null,
            )
        }
        return out
    }

    private fun parseConstituents(arr: JSONArray?): List<FoodConstituent> {
        if (arr == null) return emptyList()
        val out = mutableListOf<FoodConstituent>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: continue
            out += FoodConstituent(
                name = name,
                calories = o.optInt("calories"),
                protein = o.optDouble("protein", 0.0),
                carbs = o.optDouble("carbs", 0.0),
                fat = o.optDouble("fat", 0.0),
                servingSizeGrams = o.optDouble("servingSizeGrams", 0.0),
                emoji = o.optString("emoji").takeIf { it.isNotEmpty() },
                servingUnitOptions = parseUnits(o.optJSONArray("servingUnitOptions")),
                selectedServingUnit = o.optString("selectedServingUnit").takeIf { it.isNotEmpty() },
                selectedServingQuantity = if (o.has("selectedServingQuantity")) {
                    o.getDouble("selectedServingQuantity")
                } else {
                    null
                },
            )
        }
        return out
    }
}
