package app.chompass.services.ai

import app.chompass.models.FoodGroundingProvenance
import app.chompass.models.GroundingConfidence
import app.chompass.models.MicronutrientValues
import app.chompass.models.ServingUnitOption
import app.chompass.models.OptionalNutrientGoals
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

fun MicronutrientValues.applyTo(analysis: FoodAnalysis): FoodAnalysis = analysis.copy(
    sugar = sugar,
    addedSugar = addedSugar,
    fiber = fiber,
    saturatedFat = saturatedFat,
    monounsaturatedFat = monounsaturatedFat,
    polyunsaturatedFat = polyunsaturatedFat,
    cholesterol = cholesterol,
    sodium = sodium,
    potassium = potassium,
    transFat = transFat,
    calcium = calcium,
    iron = iron,
    magnesium = magnesium,
    zinc = zinc,
    vitaminA = vitaminA,
    vitaminC = vitaminC,
    vitaminD = vitaminD,
    vitaminB12 = vitaminB12,
    vitaminE = vitaminE,
    vitaminK = vitaminK,
    folate = folate,
    omega3 = omega3,
)

fun FoodAnalysis.toMicronutrients() = MicronutrientValues(
    sugar = sugar,
    addedSugar = addedSugar,
    fiber = fiber,
    saturatedFat = saturatedFat,
    monounsaturatedFat = monounsaturatedFat,
    polyunsaturatedFat = polyunsaturatedFat,
    cholesterol = cholesterol,
    sodium = sodium,
    potassium = potassium,
    transFat = transFat,
    calcium = calcium,
    iron = iron,
    magnesium = magnesium,
    zinc = zinc,
    vitaminA = vitaminA,
    vitaminC = vitaminC,
    vitaminD = vitaminD,
    vitaminB12 = vitaminB12,
    vitaminE = vitaminE,
    vitaminK = vitaminK,
    folate = folate,
    omega3 = omega3,
)

/** Result of AI food-photo / text analysis. */
@Serializable
data class FoodAnalysis(
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val servingSizeGrams: Double,
    val emoji: String? = null,
    val sugar: Double? = null,
    val addedSugar: Double? = null,
    val fiber: Double? = null,
    val saturatedFat: Double? = null,
    val monounsaturatedFat: Double? = null,
    val polyunsaturatedFat: Double? = null,
    val cholesterol: Double? = null,
    val sodium: Double? = null,
    val potassium: Double? = null,
    val transFat: Double? = null,
    val calcium: Double? = null,
    val iron: Double? = null,
    val magnesium: Double? = null,
    val zinc: Double? = null,
    val vitaminA: Double? = null,
    val vitaminC: Double? = null,
    val vitaminD: Double? = null,
    val vitaminB12: Double? = null,
    val vitaminE: Double? = null,
    val vitaminK: Double? = null,
    val folate: Double? = null,
    val omega3: Double? = null,
    val servingUnitOptions: List<ServingUnitOption> = emptyList(),
    val selectedServingUnit: String? = null,
    val selectedServingQuantity: Double? = null,
    val customNote: String? = null,
    /** Present when nutrients were grounded (USDA / OFF / history) rather than free-form estimated. */
    val grounding: FoodGroundingProvenance? = null,
    val groundingConfidence: GroundingConfidence? = null,
)

/**
 * Straight 1:1 mapping to a persistable [app.chompass.models.FoodEntry]
 * (no scaling or user edits). Used by the debug entry-perf benchmark to persist an
 * analyzed result; the interactive save path in HomeViewModel builds its own entry
 * because it also applies serving scaling and manual field overrides.
 */
fun FoodAnalysis.toFoodEntry(
    source: app.chompass.models.FoodSource,
    mealType: app.chompass.models.MealType,
): app.chompass.models.FoodEntry =
    toMicronutrients().applyTo(
        app.chompass.models.FoodEntry(
            name = name,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            emoji = emoji,
            source = source,
            mealType = mealType,
            servingSizeGrams = servingSizeGrams,
            servingUnitOptions = servingUnitOptions,
            selectedServingUnit = selectedServingUnit,
            selectedServingQuantity = selectedServingQuantity,
            customNote = customNote,
            grounding = grounding,
        )
    )

/** Per-100g nutrition-label reading. Scaled to a real serving via [scaled]. */
data class NutritionLabelAnalysis(
    val name: String,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingSizeGrams: Double? = null,
    val sugarPer100g: Double? = null,
    val addedSugarPer100g: Double? = null,
    val fiberPer100g: Double? = null,
    val saturatedFatPer100g: Double? = null,
    val monounsaturatedFatPer100g: Double? = null,
    val polyunsaturatedFatPer100g: Double? = null,
    val cholesterolPer100g: Double? = null,
    val sodiumPer100g: Double? = null,
    val potassiumPer100g: Double? = null,
    val transFatPer100g: Double? = null,
    val calciumPer100g: Double? = null,
    val ironPer100g: Double? = null,
    val magnesiumPer100g: Double? = null,
    val zincPer100g: Double? = null,
    val vitaminAPer100g: Double? = null,
    val vitaminCPer100g: Double? = null,
    val vitaminDPer100g: Double? = null,
    val vitaminB12Per100g: Double? = null,
    val vitaminEPer100g: Double? = null,
    val vitaminKPer100g: Double? = null,
    val folatePer100g: Double? = null,
    val omega3Per100g: Double? = null,
    val servingUnitOptions: List<ServingUnitOption> = emptyList()
) {
    fun scaled(toGrams: Double): FoodAnalysis {
        val scale = toGrams / 100.0
        val selectedOption = servingUnitOptions.firstOrNull()
        val micros = MicronutrientValues(
            sugar = sugarPer100g,
            addedSugar = addedSugarPer100g,
            fiber = fiberPer100g,
            saturatedFat = saturatedFatPer100g,
            monounsaturatedFat = monounsaturatedFatPer100g,
            polyunsaturatedFat = polyunsaturatedFatPer100g,
            cholesterol = cholesterolPer100g,
            sodium = sodiumPer100g,
            potassium = potassiumPer100g,
            transFat = transFatPer100g,
            calcium = calciumPer100g,
            iron = ironPer100g,
            magnesium = magnesiumPer100g,
            zinc = zincPer100g,
            vitaminA = vitaminAPer100g,
            vitaminC = vitaminCPer100g,
            vitaminD = vitaminDPer100g,
            vitaminB12 = vitaminB12Per100g,
            vitaminE = vitaminEPer100g,
            vitaminK = vitaminKPer100g,
            folate = folatePer100g,
            omega3 = omega3Per100g,
        ).scaled(scale, round1 = true)
        return micros.applyTo(
            FoodAnalysis(
                name = name,
                calories = (caloriesPer100g * scale).toInt(),
                protein = proteinPer100g * scale,
                carbs = carbsPer100g * scale,
                fat = fatPer100g * scale,
                servingSizeGrams = toGrams,
                servingUnitOptions = servingUnitOptions,
                selectedServingUnit = selectedOption?.unit,
                selectedServingQuantity = selectedOption?.quantityFor(toGrams)
            )
        )
    }
}

data class HealthEnergyGoalSuggestion(
    val calories: Int,
    val reason: String? = null
)

/** AI-computed daily targets returned by FoodAnalysisService.calculateGoals. */
data class GoalCalculation(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val reason: String? = null
)

internal object FoodJsonParser {
    fun extractJson(text: String): String {
        var cleaned = text.trim()

        val openFence = cleaned.indexOf("```json", ignoreCase = true)
            .takeIf { it >= 0 }
            ?: cleaned.indexOf("```").takeIf { it >= 0 }
        if (openFence != null) {
            val after = if (cleaned.regionMatches(openFence, "```json", 0, 7, ignoreCase = true)) openFence + 7 else openFence + 3
            cleaned = cleaned.substring(after)
            val closeFence = cleaned.lastIndexOf("```")
            if (closeFence >= 0) cleaned = cleaned.substring(0, closeFence)
        }
        cleaned = cleaned.trim()

        val firstBrace = cleaned.indexOf('{')
        if (firstBrace < 0) return cleaned
        var depth = 0
        var inString = false
        var escape = false
        var endIndex = -1
        for (i in firstBrace until cleaned.length) {
            val ch = cleaned[i]
            if (escape) { escape = false; continue }
            if (ch == '\\') { escape = true; continue }
            if (ch == '"') { inString = !inString; continue }
            if (inString) continue
            if (ch == '{') depth++
            else if (ch == '}') {
                depth--
                if (depth == 0) { endIndex = i + 1; break }
            }
        }
        return if (endIndex > firstBrace) cleaned.substring(firstBrace, endIndex) else cleaned
    }

    fun parseFood(text: String): FoodAnalysis {
        val json = runCatching { JSONObject(extractJson(text)) }.getOrNull()
            ?: throw AiError.InvalidResponse
        val name = json.optString("name").takeIf { it.isNotEmpty() } ?: throw AiError.InvalidResponse
        val servingSizeGrams = optDouble(json, "serving_size_grams") ?: 100.0
        val unitOptions = parseServingUnitOptions(json, servingSizeGrams)
        val selectedOption = unitOptions.firstOrNull()
        fun optDouble(key: String): Double? =
            optDouble(json, key)
        return MicronutrientValues.fromJson(::optDouble).applyTo(
            FoodAnalysis(
                name = name,
                calories = json.optInt("calories"),
                protein = optDouble("protein") ?: 0.0,
                carbs = optDouble("carbs") ?: 0.0,
                fat = optDouble("fat") ?: 0.0,
                servingSizeGrams = servingSizeGrams,
                emoji = json.optString("emoji").takeIf { it.isNotEmpty() },
                servingUnitOptions = unitOptions,
                selectedServingUnit = selectedOption?.unit,
                selectedServingQuantity = selectedOption?.quantityFor(servingSizeGrams)
            )
        )
    }

    fun parseRecognition(text: String): app.chompass.models.FoodRecognitionResult {
        val json = runCatching { JSONObject(extractJson(text)) }.getOrNull()
            ?: throw AiError.InvalidResponse
        val mealName = json.optString("meal_name").ifBlank {
            json.optString("name")
        }.takeIf { it.isNotBlank() } ?: throw AiError.InvalidResponse
        val componentsArr = json.optJSONArray("components") ?: JSONArray()
        val components = mutableListOf<app.chompass.models.RecognizedFoodComponent>()
        for (i in 0 until componentsArr.length()) {
            val raw = componentsArr.optJSONObject(i) ?: continue
            val name = raw.optString("name").trim().takeIf { it.isNotEmpty() } ?: continue
            components += app.chompass.models.RecognizedFoodComponent(
                name = name,
                brand = raw.optString("brand").takeIf { it.isNotBlank() },
                preparation = raw.optString("preparation").takeIf { it.isNotBlank() },
                estimatedGrams = optDouble(raw, "estimated_grams"),
                portionHint = raw.optString("portion_hint").takeIf { it.isNotBlank() },
                barcode = raw.optString("barcode").filter { it.isDigit() }.takeIf { it.length >= 8 },
                quantity = optDouble(raw, "quantity"),
                unit = raw.optString("unit").takeIf { it.isNotBlank() },
            )
        }
        if (components.isEmpty()) {
            components += app.chompass.models.RecognizedFoodComponent(name = mealName)
        }
        return app.chompass.models.FoodRecognitionResult(
            mealName = mealName,
            emoji = json.optString("emoji").takeIf { it.isNotBlank() && it != "null" },
            components = components,
            notes = json.optString("notes").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    fun parseLabel(text: String): NutritionLabelAnalysis {
        val json = runCatching { JSONObject(extractJson(text)) }.getOrNull()
            ?: throw AiError.InvalidResponse
        val name = json.optString("name").takeIf { it.isNotEmpty() } ?: throw AiError.InvalidResponse
        fun optDouble(key: String): Double? =
            optDouble(json, key)
        val servingSizeGrams = optDouble("serving_size_grams")
        val micros = MicronutrientValues.fromLabelJson(::optDouble)
        return NutritionLabelAnalysis(
            name = name,
            caloriesPer100g = optDouble("calories_per_100g") ?: throw AiError.InvalidResponse,
            proteinPer100g = optDouble("protein_per_100g") ?: throw AiError.InvalidResponse,
            carbsPer100g = optDouble("carbs_per_100g") ?: throw AiError.InvalidResponse,
            fatPer100g = optDouble("fat_per_100g") ?: throw AiError.InvalidResponse,
            servingSizeGrams = servingSizeGrams,
            sugarPer100g = micros.sugar,
            addedSugarPer100g = micros.addedSugar,
            fiberPer100g = micros.fiber,
            saturatedFatPer100g = micros.saturatedFat,
            monounsaturatedFatPer100g = micros.monounsaturatedFat,
            polyunsaturatedFatPer100g = micros.polyunsaturatedFat,
            cholesterolPer100g = micros.cholesterol,
            sodiumPer100g = micros.sodium,
            potassiumPer100g = micros.potassium,
            transFatPer100g = micros.transFat,
            calciumPer100g = micros.calcium,
            ironPer100g = micros.iron,
            magnesiumPer100g = micros.magnesium,
            zincPer100g = micros.zinc,
            vitaminAPer100g = micros.vitaminA,
            vitaminCPer100g = micros.vitaminC,
            vitaminDPer100g = micros.vitaminD,
            vitaminB12Per100g = micros.vitaminB12,
            vitaminEPer100g = micros.vitaminE,
            vitaminKPer100g = micros.vitaminK,
            folatePer100g = micros.folate,
            omega3Per100g = micros.omega3,
            servingUnitOptions = parseServingUnitOptions(json, servingSizeGrams)
        )
    }

    fun parseServingUnitOptions(text: String, servingSizeGrams: Double?): List<ServingUnitOption> {
        val json = runCatching { JSONObject(extractJson(text)) }.getOrNull()
            ?: throw AiError.InvalidResponse
        return parseServingUnitOptions(json, servingSizeGrams)
    }

    fun parseOptionalNutrientGoals(text: String): OptionalNutrientGoals {
        val json = runCatching { JSONObject(extractJson(text)) }.getOrNull()
            ?: throw AiError.InvalidResponse
        fun optInt(vararg keys: String, fallback: Int): Int =
            keys.firstNotNullOfOrNull { key ->
                if (!json.has(key) || json.isNull(key)) null
                else when (val value = json.opt(key)) {
                    is Number -> value.toDouble().roundToInt()
                    is String -> value.toDoubleOrNull()?.roundToInt()
                    else -> null
                }
            }?.coerceAtLeast(0) ?: fallback
        return OptionalNutrientGoals(
            sugar = optInt("sugar", "sugar_g", fallback = OptionalNutrientGoals.Default.sugar),
            addedSugar = optInt("added_sugar", "addedSugar", "added_sugar_g", fallback = OptionalNutrientGoals.Default.addedSugar),
            fiber = optInt("fiber", "fiber_g", fallback = OptionalNutrientGoals.Default.fiber),
            saturatedFat = optInt("saturated_fat", "saturatedFat", "saturated_fat_g", fallback = OptionalNutrientGoals.Default.saturatedFat),
            cholesterol = optInt("cholesterol", "cholesterol_mg", fallback = OptionalNutrientGoals.Default.cholesterol),
            sodium = optInt("sodium", "sodium_mg", fallback = OptionalNutrientGoals.Default.sodium),
            potassium = optInt("potassium", "potassium_mg", fallback = OptionalNutrientGoals.Default.potassium),
            transFat = optInt("trans_fat", "transFat", "trans_fat_g", fallback = OptionalNutrientGoals.Default.transFat),
            calcium = optInt("calcium", "calcium_mg", fallback = OptionalNutrientGoals.Default.calcium),
            iron = optInt("iron", "iron_mg", fallback = OptionalNutrientGoals.Default.iron),
            magnesium = optInt("magnesium", "magnesium_mg", fallback = OptionalNutrientGoals.Default.magnesium),
            zinc = optInt("zinc", "zinc_mg", fallback = OptionalNutrientGoals.Default.zinc),
            vitaminA = optInt("vitamin_a", "vitaminA", "vitamin_a_mcg", fallback = OptionalNutrientGoals.Default.vitaminA),
            vitaminC = optInt("vitamin_c", "vitaminC", "vitamin_c_mg", fallback = OptionalNutrientGoals.Default.vitaminC),
            vitaminD = optInt("vitamin_d", "vitaminD", "vitamin_d_mcg", fallback = OptionalNutrientGoals.Default.vitaminD),
            vitaminB12 = optInt("vitamin_b12", "vitaminB12", "vitamin_b12_mcg", fallback = OptionalNutrientGoals.Default.vitaminB12),
            vitaminE = optInt("vitamin_e", "vitaminE", "vitamin_e_mg", fallback = OptionalNutrientGoals.Default.vitaminE),
            vitaminK = optInt("vitamin_k", "vitaminK", "vitamin_k_mcg", fallback = OptionalNutrientGoals.Default.vitaminK),
            folate = optInt("folate", "folate_mcg", fallback = OptionalNutrientGoals.Default.folate),
            omega3 = optInt("omega_3", "omega3", "omega_3_g", fallback = OptionalNutrientGoals.Default.omega3)
        )
    }

    fun parseHealthEnergyGoalSuggestion(text: String): HealthEnergyGoalSuggestion {
        val json = runCatching { JSONObject(extractJson(text)) }.getOrNull()
            ?: throw AiError.InvalidResponse
        val calories = when (val value = json.opt("calories")) {
            is Number -> value.toDouble().roundToInt()
            is String -> value.toDoubleOrNull()?.roundToInt()
            else -> null
        } ?: throw AiError.InvalidResponse
        return HealthEnergyGoalSuggestion(
            calories = calories.coerceIn(800, 6000),
            reason = json.optString("reason").takeIf { it.isNotBlank() }
        )
    }

    fun parseGoalCalculation(text: String): GoalCalculation {
        val json = runCatching { JSONObject(extractJson(text)) }.getOrNull()
            ?: throw AiError.InvalidResponse
        fun intOf(key: String): Int? = when (val value = json.opt(key)) {
            is Number -> value.toDouble().roundToInt()
            is String -> value.toDoubleOrNull()?.roundToInt()
            else -> null
        }
        val calories = intOf("calories") ?: throw AiError.InvalidResponse
        fun macro(key: String, cap: Int): Int = (intOf(key) ?: 0).coerceIn(0, cap)
        return GoalCalculation(
            calories = calories.coerceIn(800, 6000),
            protein = macro("protein", 500),
            carbs = macro("carbs", 1200),
            fat = macro("fat", 400),
            reason = json.optString("reason").takeIf { it.isNotBlank() }
        )
    }

    private fun parseServingUnitOptions(
        json: JSONObject,
        servingSizeGrams: Double?
    ): List<ServingUnitOption> {
        val rawOptions = json.optJSONArray("unit_options")
            ?: json.optJSONArray("serving_unit_options")
            ?: JSONArray()
        val seen = mutableSetOf<String>()
        val options = mutableListOf<ServingUnitOption>()
        for (i in 0 until rawOptions.length()) {
            val raw = rawOptions.optJSONObject(i) ?: continue
            val unit = raw.optString("unit").takeIf { it.isNotBlank() } ?: continue
            val gramsPerUnit = optDouble(raw, "grams_per_unit")
                ?: optDouble(raw, "gramsPerUnit")
                ?: continue
            val quantity = optDouble(raw, "quantity")
            val option = ServingUnitOption(
                unit = unit,
                gramsPerUnit = gramsPerUnit,
                quantity = quantity ?: servingSizeGrams
                    ?.takeIf { gramsPerUnit > 0 }
                    ?.let { it / gramsPerUnit }
            )
            if (!option.isValid || option.isGramUnit || option.id in seen) continue
            seen.add(option.id)
            options.add(option)
        }
        return options.take(4)
    }

    private fun optDouble(json: JSONObject, key: String): Double? {
        if (!json.has(key) || json.isNull(key)) return null
        return when (val value = json.opt(key)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }?.takeUnless { it.isNaN() || it.isInfinite() }
    }
}
