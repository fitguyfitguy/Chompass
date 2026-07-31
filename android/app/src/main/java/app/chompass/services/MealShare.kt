package app.chompass.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.chompass.models.FoodConstituent
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.models.ServingUnitOption
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import kotlin.math.roundToInt

/**
 * Encodes/decodes logged meals into `chompass://add-meal?d=<base64url>` deep links.
 * Import also accepts `nofud://` (pre-rename links), `fudai://`, and upstream https
 * links for cross-app compatibility. Payload schema matches upstream Fud AI / iOS MealShare.
 */
object MealShare {
    const val SCHEME = "chompass"
    const val HOST = "add-meal"
    /** Pre-rename NoFUD scheme — import-only so old shared links keep working. */
    private const val NOFUD_SCHEME = "nofud"
    /** Upstream Fud AI scheme — import-only for shared meals from the original app. */
    private const val LEGACY_SCHEME = "fudai"
    private const val LEGACY_WEB_HOST = "www.fud-ai.app"
    private const val LEGACY_WEB_HOST_ALT = "fud-ai.app"
    const val WEB_PATH = "/add-meal"
    private const val VERSION = 2
    /** Versions accepted on decode. New encodes always stamp [VERSION]. */
    private val SUPPORTED_IMPORT_VERSIONS = setOf(1, 2)

    fun link(entries: List<FoodEntry>): String {
        val meals = JSONArray()
        entries.forEach { meals.put(mealJson(it)) }
        val payload = JSONObject().put("v", VERSION).put("meals", meals)
        val b64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
        return "$SCHEME://$HOST?d=$b64"
    }

    fun share(context: Context, entries: List<FoodEntry>) {
        if (entries.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText(entries))
        }
        context.startActivity(Intent.createChooser(send, "Share meal"))
    }

    fun shareSeparately(context: Context, entries: List<FoodEntry>) {
        if (entries.isEmpty()) return
        val payload = entries.joinToString("\n\n") { entry -> shareText(listOf(entry)) }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        context.startActivity(Intent.createChooser(send, "Share meals"))
    }

    fun shareText(entries: List<FoodEntry>): String {
        val lines = entries.map { e ->
            val macros = "${e.protein.roundToInt()}P · ${e.carbs.roundToInt()}C · ${e.fat.roundToInt()}F"
            val prefix = e.emoji?.let { "$it " } ?: ""
            "$prefix${e.name} — ${e.calories} kcal · $macros"
        }.toMutableList()
        lines.add("")
        lines.add("Open in Chompass to add:")
        lines.add(link(entries))
        return lines.joinToString("\n")
    }

    private fun mealJson(e: FoodEntry): JSONObject {
        val d = JSONObject()
            .put("name", e.name)
            .put("calories", e.calories)
            .put("protein", e.protein)
            .put("carbs", e.carbs)
            .put("fat", e.fat)
            .put("mealType", e.mealType.name.lowercase())
        e.emoji?.let { d.put("emoji", it) }
        fun put(key: String, v: Double?) { if (v != null) d.put(key, v) }
        put("sugar", e.sugar); put("addedSugar", e.addedSugar); put("fiber", e.fiber)
        put("saturatedFat", e.saturatedFat); put("monounsaturatedFat", e.monounsaturatedFat)
        put("polyunsaturatedFat", e.polyunsaturatedFat); put("cholesterol", e.cholesterol)
        put("sodium", e.sodium); put("potassium", e.potassium); put("transFat", e.transFat)
        put("calcium", e.calcium); put("iron", e.iron); put("magnesium", e.magnesium); put("zinc", e.zinc)
        put("vitaminA", e.vitaminA); put("vitaminC", e.vitaminC); put("vitaminD", e.vitaminD)
        put("vitaminB12", e.vitaminB12); put("vitaminE", e.vitaminE); put("vitaminK", e.vitaminK)
        put("folate", e.folate); put("omega3", e.omega3)
        put("servingSizeGrams", e.servingSizeGrams)
        if (e.servingUnitOptions.isNotEmpty()) {
            d.put("servingUnitOptions", servingUnitsJson(e.servingUnitOptions))
        }
        e.selectedServingUnit?.let { d.put("selectedServingUnit", it) }
        put("selectedServingQuantity", e.selectedServingQuantity)
        e.customNote?.let { d.put("customNote", it) }
        d.put("constituents", constituentsJson(e.constituents))
        return d
    }

    private fun servingUnitsJson(options: List<ServingUnitOption>): JSONArray {
        val arr = JSONArray()
        for (o in options) {
            val row = JSONObject()
                .put("unit", o.unit)
                .put("gramsPerUnit", o.gramsPerUnit)
            o.quantity?.let { row.put("quantity", it) }
            arr.put(row)
        }
        return arr
    }

    private fun constituentsJson(rows: List<FoodConstituent>): JSONArray {
        val arr = JSONArray()
        for (c in rows) {
            val d = JSONObject()
                .put("name", c.name)
                .put("calories", c.calories)
                .put("protein", c.protein)
                .put("carbs", c.carbs)
                .put("fat", c.fat)
                .put("servingSizeGrams", c.servingSizeGrams)
            c.emoji?.let { d.put("emoji", it) }
            if (c.servingUnitOptions.isNotEmpty()) {
                d.put("servingUnitOptions", servingUnitsJson(c.servingUnitOptions))
            }
            c.selectedServingUnit?.let { d.put("selectedServingUnit", it) }
            c.selectedServingQuantity?.let { d.put("selectedServingQuantity", it) }
            arr.put(d)
        }
        return arr
    }

    fun handles(uri: Uri): Boolean {
        if (uri.scheme == SCHEME && uri.host == HOST) return true
        if (uri.scheme == NOFUD_SCHEME && uri.host == HOST) return true
        if (uri.scheme == LEGACY_SCHEME && uri.host == HOST) return true
        return uri.scheme == "https" &&
            (uri.host == LEGACY_WEB_HOST || uri.host == LEGACY_WEB_HOST_ALT) &&
            uri.path == WEB_PATH
    }

    fun meals(uri: Uri): List<FoodEntry>? {
        if (!handles(uri)) return null
        val encoded = uri.getQueryParameter("d") ?: return null
        val json = runCatching {
            val bytes = Base64.getUrlDecoder().decode(encoded)
            JSONObject(String(bytes, Charsets.UTF_8))
        }.getOrNull() ?: return null
        val version = json.optInt("v", 1)
        if (version !in SUPPORTED_IMPORT_VERSIONS) return null
        val mealsArr = json.optJSONArray("meals") ?: return null
        val entries = (0 until mealsArr.length()).mapNotNull { i ->
            mealsArr.optJSONObject(i)?.let(::entryFrom)
        }
        return entries.ifEmpty { null }
    }

    private fun parseServingUnits(arr: JSONArray?): List<ServingUnitOption> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val unit = o.optString("unit").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (!o.has("gramsPerUnit")) return@mapNotNull null
            val grams = o.optDouble("gramsPerUnit")
            if (grams <= 0) return@mapNotNull null
            ServingUnitOption(
                unit = unit,
                gramsPerUnit = grams,
                quantity = if (o.has("quantity") && !o.isNull("quantity")) o.optDouble("quantity") else null,
            )
        }
    }

    private fun parseConstituents(arr: JSONArray?): List<FoodConstituent> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = d.optString("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            fun dbl(k: String): Double? = if (d.has(k) && !d.isNull(k)) d.optDouble(k) else null
            FoodConstituent(
                name = name,
                calories = d.optInt("calories"),
                protein = d.optDouble("protein", 0.0),
                carbs = d.optDouble("carbs", 0.0),
                fat = d.optDouble("fat", 0.0),
                servingSizeGrams = d.optDouble("servingSizeGrams", 0.0),
                emoji = if (d.has("emoji")) d.optString("emoji") else null,
                servingUnitOptions = parseServingUnits(d.optJSONArray("servingUnitOptions")),
                selectedServingUnit = if (d.has("selectedServingUnit")) d.optString("selectedServingUnit") else null,
                selectedServingQuantity = dbl("selectedServingQuantity"),
            )
        }
    }

    private fun entryFrom(d: JSONObject): FoodEntry? {
        val name = d.optString("name").takeIf { it.isNotEmpty() } ?: return null
        if (!d.has("calories")) return null
        fun dbl(k: String): Double? = if (d.has(k) && !d.isNull(k)) d.optDouble(k) else null
        val meal = runCatching { MealType.valueOf(d.optString("mealType").uppercase()) }
            .getOrDefault(MealType.currentMeal)
        return FoodEntry(
            name = name,
            calories = d.optInt("calories"),
            protein = d.optDouble("protein", 0.0),
            carbs = d.optDouble("carbs", 0.0),
            fat = d.optDouble("fat", 0.0),
            emoji = if (d.has("emoji")) d.optString("emoji") else null,
            source = FoodSource.MANUAL,
            mealType = meal,
            sugar = dbl("sugar"), addedSugar = dbl("addedSugar"), fiber = dbl("fiber"),
            saturatedFat = dbl("saturatedFat"), monounsaturatedFat = dbl("monounsaturatedFat"),
            polyunsaturatedFat = dbl("polyunsaturatedFat"), cholesterol = dbl("cholesterol"),
            sodium = dbl("sodium"), potassium = dbl("potassium"), transFat = dbl("transFat"),
            calcium = dbl("calcium"), iron = dbl("iron"), magnesium = dbl("magnesium"), zinc = dbl("zinc"),
            vitaminA = dbl("vitaminA"), vitaminC = dbl("vitaminC"), vitaminD = dbl("vitaminD"),
            vitaminB12 = dbl("vitaminB12"), vitaminE = dbl("vitaminE"), vitaminK = dbl("vitaminK"),
            folate = dbl("folate"), omega3 = dbl("omega3"),
            servingSizeGrams = dbl("servingSizeGrams"),
            servingUnitOptions = parseServingUnits(d.optJSONArray("servingUnitOptions")),
            selectedServingUnit = if (d.has("selectedServingUnit")) d.optString("selectedServingUnit") else null,
            selectedServingQuantity = dbl("selectedServingQuantity"),
            customNote = if (d.has("customNote")) d.optString("customNote") else null,
            constituents = parseConstituents(d.optJSONArray("constituents")),
        )
    }
}
