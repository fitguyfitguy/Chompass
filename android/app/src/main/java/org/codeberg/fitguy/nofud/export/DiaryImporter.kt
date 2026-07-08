package org.codeberg.fitguy.nofud.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.FoodSource
import org.codeberg.fitguy.nofud.models.MealType
import java.time.Instant
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
 * Parses the JSON structure emitted by [DiaryExporter] and converts it into [FoodEntry] rows.
 */
object DiaryImporter {
    private const val SUPPORTED_VERSION = "1.0"

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

                    val calories = item["calories"]?.asInt() ?: 0
                    val protein = item["protein_g"]?.asDouble() ?: 0.0
                    val carbs = item["carbs_g"]?.asDouble() ?: 0.0
                    val fat = item["fat_g"]?.asDouble() ?: 0.0
                    val serving = item["quantity_g"]?.asDouble()
                    val note = item["note"]?.asString()?.takeIf { it.isNotBlank() }
                    val source = when (item["source"]?.asString()?.trim()?.lowercase()) {
                        "manually_edited" -> FoodSource.MANUAL
                        else -> FoodSource.TEXT_INPUT
                    }

                    collected += FoodEntry(
                        name = name,
                        calories = calories,
                        protein = protein,
                        carbs = carbs,
                        fat = fat,
                        timestamp = timestamp,
                        source = source,
                        mealType = mealType,
                        servingSizeGrams = serving,
                        customNote = note
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

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull
    private fun JsonElement.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull
}
