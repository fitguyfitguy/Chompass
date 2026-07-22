package org.codeberg.fitguy.nofud.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.FoodGroundingProvenance
import org.codeberg.fitguy.nofud.models.FoodSource
import org.codeberg.fitguy.nofud.models.GroundedComponentProvenance
import org.codeberg.fitguy.nofud.models.MealType
import org.codeberg.fitguy.nofud.models.NutrientSourceKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

sealed class DiaryImportResult {
    data class Success(val entries: List<FoodEntry>) : DiaryImportResult()
    object EmptyPayload : DiaryImportResult()
    object UnsupportedFormat : DiaryImportResult()
    data class Malformed(val reason: String) : DiaryImportResult()
}

/**
 * Parses the JSON structure emitted by [DiaryExporter] (and Fud AI) into [FoodEntry] rows.
 * Requires format version 1.1 (macros + micronutrients).
 */
object DiaryImporter {
    private const val SUPPORTED_VERSION = "1.1"

    private val parser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(jsonText: String, zone: ZoneId = ZoneId.systemDefault()): DiaryImportResult {
        val root = try {
            parser.parseToJsonElement(jsonText).jsonObject
        } catch (t: Throwable) {
            return DiaryImportResult.Malformed(t.localizedMessage ?: "Invalid JSON")
        }

        val export = root["export"]?.asObjectOrNull() ?: return DiaryImportResult.UnsupportedFormat
        val app = export["app"]?.asString().orEmpty().trim().lowercase()
        if (app != "fud ai" && app != "nofud") return DiaryImportResult.UnsupportedFormat

        val version = export["format_version"]?.asString()
        if (version != SUPPORTED_VERSION) return DiaryImportResult.UnsupportedFormat

        val days = root["days"]?.asArrayOrNull() ?: return DiaryImportResult.UnsupportedFormat
        if (days.isEmpty()) return DiaryImportResult.EmptyPayload

        val collected = mutableListOf<FoodEntry>()
        for (dayElement in days) {
            val day = dayElement.asObjectOrNull()
                ?: return DiaryImportResult.Malformed("Invalid day object")
            val date = day["date"]?.asString()?.let { parseDate(it) }
                ?: return DiaryImportResult.Malformed("Invalid day date")
            val meals = day["meals"]?.asArrayOrNull()
                ?: return DiaryImportResult.Malformed("Missing meals array")
            for (mealElement in meals) {
                val meal = mealElement.asObjectOrNull()
                    ?: return DiaryImportResult.Malformed("Invalid meal object")
                val mealType = parseMealType(meal["type"]?.asString())
                val items = meal["items"]?.asArrayOrNull()
                    ?: return DiaryImportResult.Malformed("Missing meal items")
                for (itemElement in items) {
                    val item = itemElement.asObjectOrNull()
                        ?: return DiaryImportResult.Malformed("Invalid meal item")
                    val name = item["name"]?.asString()?.trim().orEmpty()
                    if (name.isEmpty()) return DiaryImportResult.Malformed("Food name cannot be blank")
                    val time = parseTime(item["time"]?.asString()) ?: LocalTime.NOON
                    val timestamp = date.atTime(time).atZone(zone).toInstant()

                    collected += FoodEntry(
                        name = name,
                        calories = item["calories"]?.asInt() ?: 0,
                        protein = item["protein_g"]?.asDouble() ?: 0.0,
                        carbs = item["carbs_g"]?.asDouble() ?: 0.0,
                        fat = item["fat_g"]?.asDouble() ?: 0.0,
                        timestamp = timestamp,
                        source = parseSource(item["source"]?.asString()),
                        mealType = mealType,
                        sugar = item["sugar_g"]?.asDouble(),
                        addedSugar = item["added_sugar_g"]?.asDouble(),
                        fiber = item["fiber_g"]?.asDouble(),
                        saturatedFat = item["saturated_fat_g"]?.asDouble(),
                        monounsaturatedFat = item["monounsaturated_fat_g"]?.asDouble(),
                        polyunsaturatedFat = item["polyunsaturated_fat_g"]?.asDouble(),
                        cholesterol = item["cholesterol_mg"]?.asDouble(),
                        sodium = item["sodium_mg"]?.asDouble(),
                        potassium = item["potassium_mg"]?.asDouble(),
                        transFat = item["trans_fat_g"]?.asDouble(),
                        calcium = item["calcium_mg"]?.asDouble(),
                        iron = item["iron_mg"]?.asDouble(),
                        magnesium = item["magnesium_mg"]?.asDouble(),
                        zinc = item["zinc_mg"]?.asDouble(),
                        vitaminA = item["vitamin_a_mcg"]?.asDouble(),
                        vitaminC = item["vitamin_c_mg"]?.asDouble(),
                        vitaminD = item["vitamin_d_mcg"]?.asDouble(),
                        vitaminB12 = item["vitamin_b12_mcg"]?.asDouble(),
                        vitaminE = item["vitamin_e_mg"]?.asDouble(),
                        vitaminK = item["vitamin_k_mcg"]?.asDouble(),
                        folate = item["folate_mcg"]?.asDouble(),
                        omega3 = item["omega3_g"]?.asDouble(),
                        servingSizeGrams = item["quantity_g"]?.asDouble(),
                        customNote = item["note"]?.asString()?.takeIf { it.isNotBlank() },
                        grounding = parseGrounding(item["grounding"]?.asObjectOrNull()),
                    )
                }
            }
        }
        if (collected.isEmpty()) return DiaryImportResult.EmptyPayload
        return DiaryImportResult.Success(collected.sortedBy { it.timestamp })
    }

    private fun parseDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.trim()) }.getOrNull()

    private fun parseTime(raw: String?): LocalTime? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        return runCatching { LocalTime.parse(cleaned) }.getOrNull()
    }

    private fun parseMealType(raw: String?): MealType =
        when (raw?.trim()?.lowercase()) {
            "breakfast" -> MealType.BREAKFAST
            "lunch" -> MealType.LUNCH
            "dinner" -> MealType.DINNER
            "snack" -> MealType.SNACK
            else -> MealType.OTHER
        }

    private fun parseSource(raw: String?): FoodSource =
        when (raw?.trim()?.lowercase()) {
            "manually_edited" -> FoodSource.MANUAL
            "barcode" -> FoodSource.BARCODE
            "grounded" -> FoodSource.GROUNDED
            else -> FoodSource.TEXT_INPUT
        }

    private fun parseSourceKind(raw: String?): NutrientSourceKind =
        when (raw?.trim()) {
            "usda" -> NutrientSourceKind.USDA
            "openFoodFacts" -> NutrientSourceKind.OPEN_FOOD_FACTS
            "history" -> NutrientSourceKind.HISTORY
            "nutritionLabel" -> NutrientSourceKind.NUTRITION_LABEL
            else -> NutrientSourceKind.MODEL_ESTIMATE
        }

    private fun parseGrounding(obj: JsonObject?): FoodGroundingProvenance? {
        if (obj == null) return null
        val components = obj["components"]?.asArrayOrNull()?.mapNotNull { el ->
            val c = el.asObjectOrNull() ?: return@mapNotNull null
            val name = c["name"]?.asString()?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            GroundedComponentProvenance(
                name = name,
                grams = c["grams"]?.asDouble() ?: 0.0,
                sourceKind = parseSourceKind(c["source_kind"]?.asString()),
                sourceId = c["source_id"]?.asString(),
                sourceName = c["source_name"]?.asString(),
                matchedBy = c["matched_by"]?.asString(),
            )
        }.orEmpty()
        return FoodGroundingProvenance(
            sourceKind = parseSourceKind(obj["source_kind"]?.asString()),
            sourceId = obj["source_id"]?.asString(),
            sourceName = obj["source_name"]?.asString(),
            datasetVersion = obj["dataset_version"]?.asString(),
            identityEvidence = obj["identity_evidence"]?.asString(),
            portionEvidence = obj["portion_evidence"]?.asString(),
            identityConfirmed = obj["identity_confirmed"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
            portionConfirmed = obj["portion_confirmed"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
            userCorrected = obj["user_corrected"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
            components = components,
            validationNotes = obj["validation_notes"]?.asArrayOrNull()
                ?.mapNotNull { it.asString() }
                .orEmpty(),
        )
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull
    private fun JsonElement.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull
}
