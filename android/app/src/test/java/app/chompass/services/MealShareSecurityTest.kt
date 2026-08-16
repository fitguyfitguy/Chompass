package app.chompass.services

import android.app.Application
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/**
 * Security regression (docs/SECURITY_HARDENING_PLAN.md P2-2): `chompass://add-meal`
 * payloads are attacker-controlled (any app or web page can fire a VIEW intent).
 * Decode must be bounded (payload size, row counts) and values sanitized before
 * they land in the user's diary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MealShareSecurityTest {
    private fun linkFor(payload: JSONObject): Uri =
        Uri.parse(
            "chompass://add-meal?d=" +
                Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.toString().toByteArray(Charsets.UTF_8)),
        )

    private fun mealJson(name: String, calories: Int = 300): JSONObject =
        JSONObject()
            .put("name", name)
            .put("calories", calories)
            .put("protein", 10.0)
            .put("carbs", 5.0)
            .put("fat", 5.0)

    @Test
    fun oversizedPayload_isRejected() {
        val bigName = "x".repeat(200_000)
        val payload = JSONObject().put("v", 2).put("meals", JSONArray().put(mealJson(bigName)))
        assertNull(MealShare.meals(linkFor(payload)))
    }

    @Test
    fun mealCount_isCapped() {
        val meals = JSONArray()
        repeat(120) { meals.put(mealJson("meal-$it")) }
        val entries = MealShare.meals(linkFor(JSONObject().put("v", 2).put("meals", meals)))
        assertEquals(MealShare.MAX_MEALS, entries?.size)
    }

    @Test
    fun absurdValues_areClampedOrDropped() {
        val meal = mealJson("monster")
            .put("calories", 999_999_999)
            .put("protein", -5.0)
            .put("sodium", 9_999_999.0)
            .put("servingSizeGrams", -100.0)
        val entries = MealShare.meals(
            linkFor(JSONObject().put("v", 2).put("meals", JSONArray().put(meal))),
        )
        val e = entries!!.single()
        assertEquals(InputSanitizer.MAX_CALORIES.toInt(), e.calories)
        assertEquals(0.0, e.protein, 0.0)
        assertEquals(InputSanitizer.MAX_MICRO_UNITS, e.sodium!!, 0.0)
        assertNull(e.servingSizeGrams)
    }

    @Test
    fun controlAndBidiChars_areStrippedFromNamesAndNotes() {
        val meal = mealJson("evil\u0000name\u202E")
            .put("customNote", "note\u0000with\u202Eoverride")
        val e = MealShare.meals(
            linkFor(JSONObject().put("v", 2).put("meals", JSONArray().put(meal))),
        )!!.single()
        assertEquals("evilname", e.name)
        assertEquals("notewithoverride", e.customNote)
    }

    @Test
    fun nameLength_isCapped() {
        val longName = "n".repeat(500)
        val e = MealShare.meals(
            linkFor(JSONObject().put("v", 2).put("meals", JSONArray().put(mealJson(longName)))),
        )!!.single()
        assertEquals(InputSanitizer.MAX_NAME_LENGTH, e.name.length)
    }

    @Test
    fun constituentCount_isCapped() {
        val constituents = JSONArray()
        repeat(60) { i ->
            constituents.put(
                JSONObject()
                    .put("name", "c-$i")
                    .put("calories", 50)
                    .put("protein", 2.0)
                    .put("carbs", 3.0)
                    .put("fat", 1.0)
                    .put("servingSizeGrams", 30.0),
            )
        }
        val meal = mealJson("composite").put("constituents", constituents)
        val e = MealShare.meals(
            linkFor(JSONObject().put("v", 2).put("meals", JSONArray().put(meal))),
        )!!.single()
        assertEquals(MealShare.MAX_CONSTITUENTS, e.constituents.size)
    }

    @Test
    fun normalLink_stillRoundTrips() {
        val e = MealShare.meals(
            linkFor(JSONObject().put("v", 2).put("meals", JSONArray().put(mealJson("eggs", 220)))),
        )!!.single()
        assertEquals("eggs", e.name)
        assertEquals(220, e.calories)
    }
}
