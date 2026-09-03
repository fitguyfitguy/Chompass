package app.chompass.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import app.chompass.services.InputSanitizer
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.DailyNote
import app.chompass.models.FoodConstituent
import app.chompass.models.FoodEntry
import app.chompass.models.FoodGroundingProvenance
import app.chompass.models.FoodSource
import app.chompass.models.GoalJournal
import app.chompass.models.GoalJournalEntry
import app.chompass.models.GoalJournalSource
import app.chompass.models.MealType
import app.chompass.models.CaffeineEntry
import app.chompass.models.CaffeineKind
import app.chompass.models.NicotineEntry
import app.chompass.models.NicotineKind
import app.chompass.models.NutrientSourceKind
import app.chompass.models.Recipe
import app.chompass.models.RecipeIngredient
import app.chompass.models.ServingUnitOption
import app.chompass.models.WaterEntry
import app.chompass.models.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Sync-1.3 document parse/build. Mirrors web/.../sync-format.js.
 * 1.2 added the day-granular `daily_notes` array (Codeberg #58a) and, since the
 * #60 goal-journal phase, the optional `goal_journal` array (per-day frozen
 * targets; old docs parse with an empty array — daily_notes precedent).
 * 1.3 adds per-constituent micronutrients (#86).
 * Photos and API keys are intentionally excluded. Imports also accept 1.0-1.2.
 */
object SyncDocument {
    const val APP_NAME = "Chompass"
    const val KIND = "sync"
    const val FORMAT_VERSION = "1.3"
    private val SUPPORTED_IMPORT_VERSIONS = setOf("1.0", "1.1", "1.2", "1.3")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    data class Revision(val updatedAt: String, val deletedAt: String? = null, val kind: String = "food")

    data class SingletonEnvelope(val updatedAt: String, val deletedAt: String? = null, val payload: JsonObject)

    data class Parsed(
        val foodEntries: List<FoodWire>,
        val favorites: List<FoodWire>,
        val weights: List<WeightWire>,
        val bodyFats: List<BodyFatWire>,
        val measurements: List<MeasurementWire>,
        val water: List<WaterWire>,
        val dailyNotes: List<DailyNoteWire>,
        val nicotine: List<NicotineWire>,
        val caffeine: List<CaffeineWire>,
        val recipes: List<RecipeWire>,
        val goalJournal: List<GoalJournalWire>,
        val profile: SingletonEnvelope?,
        val prefs: SingletonEnvelope?,
        val raw: JsonObject,
    )

    data class FoodWire(
        val id: String,
        val updatedAt: String,
        val deletedAt: String?,
        val entry: FoodEntry?,
    )

    data class WeightWire(
        val id: String,
        val updatedAt: String,
        val deletedAt: String?,
        val entry: WeightEntry?,
    )

    data class BodyFatWire(
        val id: String,
        val updatedAt: String,
        val deletedAt: String?,
        val entry: BodyFatEntry?,
    )

    data class MeasurementWire(
        val id: String,
        val updatedAt: String,
        val deletedAt: String?,
        val entry: BodyMeasurement?,
    )

    data class WaterWire(
        val id: String,
        val updatedAt: String,
        val deletedAt: String?,
        val entry: WaterEntry?,
    )

    data class DailyNoteWire(
        val id: String,
        val updatedAt: String,
        val deletedAt: String?,
        val entry: DailyNote?,
    )

    data class NicotineWire(
        val id: String,
        val updatedAt: String,
        val deletedAt: String?,
        val entry: NicotineEntry?,
    )

    data class CaffeineWire(
        val id: String,
        val updatedAt: String,
        val deletedAt: String?,
        val entry: CaffeineEntry?,
    )

    data class RecipeWire(
        val id: String,
        val updatedAt: String,
        val deletedAt: String?,
        val entry: Recipe?,
    )

    data class GoalJournalWire(
        val id: String,
        val updatedAt: String,
        val deletedAt: String?,
        val entry: GoalJournalEntry?,
    )

    sealed class ParseResult {
        data class Success(val parsed: Parsed) : ParseResult()
        object UnsupportedFormat : ParseResult()
        data class Malformed(val reason: String) : ParseResult()
    }

    fun parse(jsonText: String, zone: ZoneId = ZoneId.systemDefault()): ParseResult {
        val root = try {
            json.parseToJsonElement(jsonText).jsonObject
        } catch (t: Throwable) {
            return ParseResult.Malformed(t.localizedMessage ?: "Invalid JSON")
        }
        val export = root["export"]?.asObjectOrNull() ?: return ParseResult.UnsupportedFormat
        val app = export["app"]?.asString().orEmpty().trim().lowercase()
        if (app != "chompass" && app != "nofud" && app != "fud ai") return ParseResult.UnsupportedFormat
        if (export["kind"]?.asString() != KIND) return ParseResult.UnsupportedFormat
        val version = export["format_version"]?.asString()
        if (version == null || version !in SUPPORTED_IMPORT_VERSIONS) return ParseResult.UnsupportedFormat

        fun arr(key: String): JsonArray =
            root[key]?.asArrayOrNull() ?: JsonArray(emptyList())

        return try {
            ParseResult.Success(
                Parsed(
                    foodEntries = arr("food_entries").mapNotNull { parseFoodWire(it, zone) },
                    favorites = arr("favorites").mapNotNull { parseFoodWire(it, zone) },
                    weights = arr("weights").mapNotNull { parseWeightWire(it) },
                    bodyFats = arr("body_fat").mapNotNull { parseBodyFatWire(it) },
                    measurements = arr("measurements").mapNotNull { parseMeasurementWire(it) },
                    water = arr("water").mapNotNull { parseWaterWire(it) },
                    dailyNotes = arr("daily_notes").mapNotNull { parseDailyNoteWire(it) },
                    nicotine = arr("nicotine_entries").mapNotNull { parseNicotineWire(it) },
                    caffeine = arr("caffeine_entries").mapNotNull { parseCaffeineWire(it) },
                    recipes = arr("recipes").mapNotNull { parseRecipeWire(it) },
                    goalJournal = arr("goal_journal").mapNotNull { parseGoalJournalWire(it) },
                    profile = parseSingleton(root["profile"]),
                    prefs = parseSingleton(root["prefs"]),
                    raw = root,
                ),
            )
        } catch (t: Throwable) {
            ParseResult.Malformed(t.localizedMessage ?: "Malformed sync document")
        }
    }

    fun buildJson(
        foodEntries: List<FoodEntry>,
        favorites: List<FoodEntry>,
        weights: List<WeightEntry>,
        bodyFats: List<BodyFatEntry>,
        measurements: List<BodyMeasurement>,
        water: List<WaterEntry>,
        dailyNotes: List<DailyNote> = emptyList(),
        nicotine: List<NicotineEntry> = emptyList(),
        caffeine: List<CaffeineEntry> = emptyList(),
        recipes: List<Recipe>,
        goalJournal: List<GoalJournalEntry> = emptyList(),
        revisions: Map<String, Revision> = emptyMap(),
        profile: SingletonEnvelope? = null,
        prefs: SingletonEnvelope? = null,
        generatedAt: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val root = buildJsonObject {
            put(
                "export",
                buildJsonObject {
                    put("app", APP_NAME)
                    put("kind", KIND)
                    put("format_version", FORMAT_VERSION)
                    put("generated_at", generatedAt.toString())
                },
            )
            put("food_entries", foodArray(foodEntries, revisions, zone))
            put("favorites", foodArray(favorites, revisions, zone))
            put(
                "weights",
                buildJsonArray {
                    for (w in weights) {
                        val meta = metaFor(w.id.toString(), revisions, w.date.toString())
                        add(
                            buildJsonObject {
                                put("id", w.id.toString())
                                put("updated_at", meta.updatedAt)
                                putNullable("deleted_at", meta.deletedAt)
                                put("date", w.date.toString())
                                put("weight_kg", w.weightKg)
                            },
                        )
                    }
                    appendTombstones(revisions, "weight", weights.map { it.id.toString() }.toSet())
                },
            )
            put(
                "body_fat",
                buildJsonArray {
                    for (b in bodyFats) {
                        val meta = metaFor(b.id.toString(), revisions, b.date.toString())
                        add(
                            buildJsonObject {
                                put("id", b.id.toString())
                                put("updated_at", meta.updatedAt)
                                putNullable("deleted_at", meta.deletedAt)
                                put("date", b.date.toString())
                                put("body_fat_percent", b.bodyFatPercent)
                            },
                        )
                    }
                    appendTombstones(revisions, "bodyfat", bodyFats.map { it.id.toString() }.toSet())
                },
            )
            put(
                "measurements",
                buildJsonArray {
                    for (m in measurements) {
                        val meta = metaFor(m.id.toString(), revisions, m.date.toString())
                        add(
                            buildJsonObject {
                                put("id", m.id.toString())
                                put("updated_at", meta.updatedAt)
                                putNullable("deleted_at", meta.deletedAt)
                                put("date", m.date.toString())
                                putNullableNumber("neck_cm", m.neckCm)
                                putNullableNumber("waist_cm", m.waistCm)
                                putNullableNumber("hips_cm", m.hipsCm)
                                putNullableNumber("chest_cm", m.chestCm)
                                putNullableNumber("upper_arm_cm", m.upperArmCm)
                                putNullableNumber("thigh_cm", m.thighCm)
                                putNullableNumber("calf_cm", m.calfCm)
                                putNullableNumber("wrist_cm", m.wristCm)
                            },
                        )
                    }
                    appendTombstones(revisions, "measure", measurements.map { it.id.toString() }.toSet())
                },
            )
            put(
                "water",
                buildJsonArray {
                    for (w in water) {
                        val day = w.date.atZone(zone).toLocalDate().toString()
                        val meta = metaFor(w.id.toString(), revisions, "${day}T00:00:00Z")
                        add(
                            buildJsonObject {
                                put("id", w.id.toString())
                                put("updated_at", meta.updatedAt)
                                putNullable("deleted_at", meta.deletedAt)
                                put("date", day)
                                put("amount_ml", w.milliliters)
                            },
                        )
                    }
                    appendTombstones(revisions, "water", water.map { it.id.toString() }.toSet())
                },
            )
            put(
                "daily_notes",
                buildJsonArray {
                    for (n in dailyNotes) {
                        val meta = metaFor(n.id.toString(), revisions, "${n.date}T00:00:00Z")
                        add(
                            buildJsonObject {
                                put("id", n.id.toString())
                                put("updated_at", meta.updatedAt)
                                putNullable("deleted_at", meta.deletedAt)
                                put("date", n.date.toString())
                                put("text", n.text)
                            },
                        )
                    }
                    appendTombstones(revisions, "daily_note", dailyNotes.map { it.id.toString() }.toSet())
                },
            )
            put(
                "nicotine_entries",
                buildJsonArray {
                    for (n in nicotine) {
                        val day = n.date.atZone(zone).toLocalDate().toString()
                        val meta = metaFor(n.id.toString(), revisions, "${day}T00:00:00Z")
                        add(
                            buildJsonObject {
                                put("id", n.id.toString())
                                put("updated_at", meta.updatedAt)
                                putNullable("deleted_at", meta.deletedAt)
                                put("date", day)
                                put("kind", n.kind.storageKey)
                                put("count", n.count)
                                putNullableNumber("mg", n.mg)
                            },
                        )
                    }
                    appendTombstones(revisions, "nicotine", nicotine.map { it.id.toString() }.toSet())
                },
            )
            put(
                "caffeine_entries",
                buildJsonArray {
                    for (c in caffeine) {
                        val day = c.date.atZone(zone).toLocalDate().toString()
                        val meta = metaFor(c.id.toString(), revisions, "${day}T00:00:00Z")
                        add(
                            buildJsonObject {
                                put("id", c.id.toString())
                                put("updated_at", meta.updatedAt)
                                putNullable("deleted_at", meta.deletedAt)
                                put("date", day)
                                put("kind", c.kind.storageKey)
                                put("mg", c.mg)
                            },
                        )
                    }
                    appendTombstones(revisions, "caffeine", caffeine.map { it.id.toString() }.toSet())
                },
            )
            put(
                "recipes",
                buildJsonArray {
                    for (r in recipes) {
                        val meta = metaFor(r.id.toString(), revisions, r.createdAt.toString())
                        add(
                            buildJsonObject {
                                put("id", r.id.toString())
                                put("updated_at", meta.updatedAt)
                                putNullable("deleted_at", meta.deletedAt)
                                put("name", r.name)
                                put("meal_type", r.mealType)
                                put("created_at", r.createdAt.toString())
                                put(
                                    "ingredients",
                                    buildJsonArray {
                                        for (ing in r.ingredients) {
                                            add(
                                                buildJsonObject {
                                                    put("id", ing.id.toString())
                                                    put("name", ing.name)
                                                    put("base_calories", ing.baseCalories)
                                                    put("base_protein_g", ing.baseProtein)
                                                    put("base_carbs_g", ing.baseCarbs)
                                                    put("base_fat_g", ing.baseFat)
                                                    put("quantity_scale", ing.quantityScale)
                                                    putNullableNumber("base_fiber_g", ing.baseFiber)
                                                    putNullableNumber("base_sugar_g", ing.baseSugar)
                                                    putNullableNumber("base_sodium_mg", ing.baseSodium)
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                    appendTombstones(revisions, "recipe", recipes.map { it.id.toString() }.toSet())
                },
            )
            put(
                "goal_journal",
                buildJsonArray {
                    for (e in goalJournal) {
                        val date = runCatching { LocalDate.parse(e.date) }.getOrNull()
                        val id = date?.let { GoalJournal.idFor(it).toString() } ?: e.date
                        val fallback = Instant.ofEpochMilli(e.updatedAtMillis).toString()
                        val meta = metaFor(id, revisions, fallback)
                        add(
                            buildJsonObject {
                                put("id", id)
                                put("updated_at", meta.updatedAt)
                                putNullable("deleted_at", meta.deletedAt)
                                put("date", e.date)
                                put("calories", e.calories)
                                put("protein_g", e.proteinG)
                                put("carbs_g", e.carbsG)
                                put("fat_g", e.fatG)
                                putNullable("profile_id", e.profileId)
                                putNullable("profile_name", e.profileName)
                                put("source", e.source.name.lowercase())
                            },
                        )
                    }
                    appendTombstones(revisions, "goal_journal", goalJournal.mapNotNull { g ->
                        runCatching { GoalJournal.idFor(LocalDate.parse(g.date)).toString() }.getOrNull()
                    }.toSet())
                },
            )
            if (profile != null) {
                put(
                    "profile",
                    buildJsonObject {
                        put("updated_at", profile.updatedAt)
                        putNullable("deleted_at", profile.deletedAt)
                        put("payload", profile.payload)
                    },
                )
            } else {
                put("profile", JsonNull)
            }
            if (prefs != null) {
                put(
                    "prefs",
                    buildJsonObject {
                        put("updated_at", prefs.updatedAt)
                        putNullable("deleted_at", prefs.deletedAt)
                        put("payload", prefs.payload)
                    },
                )
            } else {
                put("prefs", JsonNull)
            }
        }
        // Also emit food/favorite tombstones not already present
        val withFoodTombs = injectListTombstones(root, revisions, "food", "food_entries")
        val withFavTombs = injectListTombstones(withFoodTombs, revisions, "favorite", "favorites")
        return json.encodeToString(JsonObject.serializer(), withFavTombs)
    }

    fun mergeRawDocuments(local: JsonObject, remote: JsonObject): JsonObject {
        fun mergeList(key: String): JsonArray {
            val localRows = local[key]?.asArrayOrNull()?.mapNotNull { it.asObjectOrNull() }.orEmpty()
            val remoteRows = remote[key]?.asArrayOrNull()?.mapNotNull { it.asObjectOrNull() }.orEmpty()
            val merged = SyncMerge.mergeRecordLists(
                local = localRows,
                remote = remoteRows,
                idOf = { it["id"]?.asString().orEmpty() },
                updatedAt = { it["updated_at"]?.asString().orEmpty() },
                deletedAt = { it["deleted_at"]?.asString() },
            )
            return JsonArray(merged)
        }

        // Weights merge by id AND collapse identical (date, weight_kg) rows that
        // were written under different ids (#39): id-divergent duplicates would
        // otherwise survive the merge and ride into the local store and back up
        // to WebDAV on every sync.
        fun mergeWeights(): JsonArray {
            val localRows = local["weights"]?.asArrayOrNull()?.mapNotNull { it.asObjectOrNull() }.orEmpty()
            val remoteRows = remote["weights"]?.asArrayOrNull()?.mapNotNull { it.asObjectOrNull() }.orEmpty()
            val merged = SyncMerge.dedupeRecordLists(
                local = localRows,
                remote = remoteRows,
                idOf = { it["id"]?.asString().orEmpty() },
                keyOf = { weightDedupeKey(it) },
                updatedAt = { it["updated_at"]?.asString().orEmpty() },
                deletedAt = { it["deleted_at"]?.asString() },
            )
            return JsonArray(merged)
        }

        fun mergeSingle(key: String): JsonElement {
            val l = local[key]?.asObjectOrNull()
            val r = remote[key]?.asObjectOrNull()
            if (l == null) return r ?: JsonNull
            if (r == null) return l
            val winner = SyncMerge.pickNewer(
                local = l,
                remote = r,
                updatedAt = { it["updated_at"]?.asString().orEmpty() },
                deletedAt = { it["deleted_at"]?.asString() },
            )
            return winner ?: JsonNull
        }

        return buildJsonObject {
            put(
                "export",
                buildJsonObject {
                    put("app", APP_NAME)
                    put("kind", KIND)
                    put("format_version", FORMAT_VERSION)
                    val generated = remote["export"]?.asObjectOrNull()?.get("generated_at")?.asString()
                        ?: local["export"]?.asObjectOrNull()?.get("generated_at")?.asString()
                        ?: Instant.now().toString()
                    put("generated_at", generated)
                },
            )
            for (key in listOf(
                "food_entries", "favorites", "body_fat", "measurements", "water", "daily_notes", "nicotine_entries", "caffeine_entries", "recipes", "goal_journal",
            )) {
                put(key, mergeList(key))
            }
            put("weights", mergeWeights())
            put("profile", mergeSingle("profile"))
            put("prefs", mergeSingle("prefs"))
        }
    }

    // --- helpers ---

    private fun foodArray(
        entries: List<FoodEntry>,
        revisions: Map<String, Revision>,
        zone: ZoneId,
    ): JsonArray = buildJsonArray {
        for (e in entries) {
            val localDate = e.timestamp.atZone(zone).toLocalDate()
            val localTime = e.timestamp.atZone(zone).toLocalTime()
            val fallback = "${localDate}T${localTime.withSecond(0).withNano(0)}Z"
            val meta = metaFor(e.id.toString(), revisions, fallback)
            add(foodToWire(e, meta.updatedAt, meta.deletedAt, zone))
        }
    }

    private fun foodToWire(
        e: FoodEntry,
        updatedAt: String,
        deletedAt: String?,
        zone: ZoneId,
    ): JsonObject {
        val localDate = e.timestamp.atZone(zone).toLocalDate()
        val localTime = e.timestamp.atZone(zone).toLocalTime()
        return buildJsonObject {
            put("id", e.id.toString())
            put("updated_at", updatedAt)
            putNullable("deleted_at", deletedAt)
            put("name", e.name)
            putNullable("emoji", e.emoji)
            put("date", localDate.toString())
            put("time", String.format("%02d:%02d", localTime.hour, localTime.minute))
            put("meal_type", e.mealType)
            putNullableNumber("quantity_g", e.servingSizeGrams)
            put("calories", e.calories)
            put("protein_g", e.protein)
            put("carbs_g", e.carbs)
            put("fat_g", e.fat)
            put("source", sourceToWire(e.source))
            putNullable("note", e.customNote)
            putNullable("recipe_log_id", e.recipeLogId?.toString())
            putNullableNumber("sugar_g", e.sugar)
            putNullableNumber("added_sugar_g", e.addedSugar)
            putNullableNumber("fiber_g", e.fiber)
            putNullableNumber("saturated_fat_g", e.saturatedFat)
            putNullableNumber("monounsaturated_fat_g", e.monounsaturatedFat)
            putNullableNumber("polyunsaturated_fat_g", e.polyunsaturatedFat)
            putNullableNumber("cholesterol_mg", e.cholesterol)
            putNullableNumber("sodium_mg", e.sodium)
            putNullableNumber("potassium_mg", e.potassium)
            putNullableNumber("trans_fat_g", e.transFat)
            putNullableNumber("calcium_mg", e.calcium)
            putNullableNumber("iron_mg", e.iron)
            putNullableNumber("magnesium_mg", e.magnesium)
            putNullableNumber("zinc_mg", e.zinc)
            putNullableNumber("vitamin_a_mcg", e.vitaminA)
            putNullableNumber("vitamin_c_mg", e.vitaminC)
            putNullableNumber("vitamin_d_mcg", e.vitaminD)
            putNullableNumber("vitamin_b12_mcg", e.vitaminB12)
            putNullableNumber("vitamin_e_mg", e.vitaminE)
            putNullableNumber("vitamin_k_mcg", e.vitaminK)
            putNullableNumber("folate_mcg", e.folate)
            putNullableNumber("omega3_g", e.omega3)
            putNullableNumber("caffeine_mg", e.caffeine)
            put(
                "serving_unit_options",
                buildJsonArray {
                    for (o in e.servingUnitOptions) {
                        add(
                            buildJsonObject {
                                put("unit", o.unit)
                                put("grams_per_unit", o.gramsPerUnit)
                                putNullableNumber("quantity", o.quantity)
                            },
                        )
                    }
                },
            )
            putNullable("selected_serving_unit", e.selectedServingUnit)
            putNullableNumber("selected_serving_quantity", e.selectedServingQuantity)
            put(
                "constituents",
                buildJsonArray {
                    for (c in e.constituents) {
                        add(constituentToWire(c))
                    }
                },
            )
            val g = e.grounding
            if (g == null) put("grounding", JsonNull)
            else put("grounding", groundingToWire(g))
        }
    }

    private fun constituentToWire(c: FoodConstituent): JsonObject = buildJsonObject {
        put("name", c.name)
        put("calories", c.calories)
        put("protein_g", c.protein)
        put("carbs_g", c.carbs)
        put("fat_g", c.fat)
        put("quantity_g", c.servingSizeGrams)
        putNullable("emoji", c.emoji)
        put(
            "serving_unit_options",
            buildJsonArray {
                for (o in c.servingUnitOptions) {
                    add(
                        buildJsonObject {
                            put("unit", o.unit)
                            put("grams_per_unit", o.gramsPerUnit)
                            putNullableNumber("quantity", o.quantity)
                        },
                    )
                }
            },
        )
        putNullable("selected_serving_unit", c.selectedServingUnit)
        putNullableNumber("selected_serving_quantity", c.selectedServingQuantity)
        putNullableNumber("sugar_g", c.sugar)
        putNullableNumber("added_sugar_g", c.addedSugar)
        putNullableNumber("fiber_g", c.fiber)
        putNullableNumber("saturated_fat_g", c.saturatedFat)
        putNullableNumber("monounsaturated_fat_g", c.monounsaturatedFat)
        putNullableNumber("polyunsaturated_fat_g", c.polyunsaturatedFat)
        putNullableNumber("cholesterol_mg", c.cholesterol)
        putNullableNumber("sodium_mg", c.sodium)
        putNullableNumber("potassium_mg", c.potassium)
        putNullableNumber("trans_fat_g", c.transFat)
        putNullableNumber("calcium_mg", c.calcium)
        putNullableNumber("iron_mg", c.iron)
        putNullableNumber("magnesium_mg", c.magnesium)
        putNullableNumber("zinc_mg", c.zinc)
        putNullableNumber("vitamin_a_mcg", c.vitaminA)
        putNullableNumber("vitamin_c_mg", c.vitaminC)
        putNullableNumber("vitamin_d_mcg", c.vitaminD)
        putNullableNumber("vitamin_b12_mcg", c.vitaminB12)
        putNullableNumber("vitamin_e_mg", c.vitaminE)
        putNullableNumber("vitamin_k_mcg", c.vitaminK)
        putNullableNumber("folate_mcg", c.folate)
        putNullableNumber("omega3_g", c.omega3)
        putNullableNumber("caffeine_mg", c.caffeine)
    }

    private fun parseFoodWire(el: JsonElement, zone: ZoneId): FoodWire? {
        val o = el.asObjectOrNull() ?: return null
        val id = o["id"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val updatedAt = o["updated_at"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val deletedAt = o["deleted_at"]?.asString()
        if (!deletedAt.isNullOrBlank()) {
            return FoodWire(id, updatedAt, deletedAt, entry = null)
        }
        val name = o["name"]?.asString()?.trim().orEmpty()
        if (name.isEmpty()) return FoodWire(id, updatedAt, null, null)
        val date = o["date"]?.asString()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return FoodWire(id, updatedAt, null, null)
        val time = o["time"]?.asString()?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: LocalTime.NOON
        val entry = FoodEntry(
            id = runCatching { UUID.fromString(id) }.getOrElse { UUID.nameUUIDFromBytes(id.toByteArray()) },
            name = name,
            emoji = o["emoji"]?.asString()?.takeIf { it.isNotBlank() },
            calories = o["calories"]?.asInt() ?: 0,
            protein = o["protein_g"]?.asDouble() ?: 0.0,
            carbs = o["carbs_g"]?.asDouble() ?: 0.0,
            fat = o["fat_g"]?.asDouble() ?: 0.0,
            timestamp = date.atTime(time).atZone(zone).toInstant(),
            source = parseSource(o["source"]?.asString()),
            mealType = parseMealType(o["meal_type"]?.asString()),
            sugar = o["sugar_g"]?.asDouble(),
            addedSugar = o["added_sugar_g"]?.asDouble(),
            fiber = o["fiber_g"]?.asDouble(),
            saturatedFat = o["saturated_fat_g"]?.asDouble(),
            monounsaturatedFat = o["monounsaturated_fat_g"]?.asDouble(),
            polyunsaturatedFat = o["polyunsaturated_fat_g"]?.asDouble(),
            cholesterol = o["cholesterol_mg"]?.asDouble(),
            sodium = o["sodium_mg"]?.asDouble(),
            potassium = o["potassium_mg"]?.asDouble(),
            transFat = o["trans_fat_g"]?.asDouble(),
            calcium = o["calcium_mg"]?.asDouble(),
            iron = o["iron_mg"]?.asDouble(),
            magnesium = o["magnesium_mg"]?.asDouble(),
            zinc = o["zinc_mg"]?.asDouble(),
            vitaminA = o["vitamin_a_mcg"]?.asDouble(),
            vitaminC = o["vitamin_c_mg"]?.asDouble(),
            vitaminD = o["vitamin_d_mcg"]?.asDouble(),
            vitaminB12 = o["vitamin_b12_mcg"]?.asDouble(),
            vitaminE = o["vitamin_e_mg"]?.asDouble(),
            vitaminK = o["vitamin_k_mcg"]?.asDouble(),
            folate = o["folate_mcg"]?.asDouble(),
            omega3 = o["omega3_g"]?.asDouble(),
            caffeine = o["caffeine_mg"]?.asDouble(),
            servingSizeGrams = o["quantity_g"]?.asDouble(),
            servingUnitOptions = parseServingUnitOptions(o["serving_unit_options"]?.asArrayOrNull()),
            selectedServingUnit = o["selected_serving_unit"]?.asString()?.takeIf { it.isNotBlank() },
            selectedServingQuantity = o["selected_serving_quantity"]?.asDouble(),
            customNote = o["note"]?.asString()?.takeIf { it.isNotBlank() },
            recipeLogId = o["recipe_log_id"]?.asString()?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            },
            grounding = parseGrounding(o["grounding"]?.asObjectOrNull()),
            constituents = parseConstituents(o["constituents"]?.asArrayOrNull()),
        )
        return FoodWire(id, updatedAt, null, entry)
    }

    private fun parseServingUnitOptions(arr: JsonArray?): List<ServingUnitOption> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { el ->
            val row = el.asObjectOrNull() ?: return@mapNotNull null
            val unit = row["unit"]?.asString()?.trim().orEmpty()
            val grams = row["grams_per_unit"]?.asDouble()
            if (unit.isEmpty() || grams == null || grams <= 0) return@mapNotNull null
            ServingUnitOption(
                unit = unit,
                gramsPerUnit = grams,
                quantity = row["quantity"]?.asDouble(),
            )
        }
    }

    private fun parseConstituents(arr: JsonArray?): List<FoodConstituent> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { el ->
            val row = el.asObjectOrNull() ?: return@mapNotNull null
            val name = row["name"]?.asString()?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            FoodConstituent(
                name = name,
                calories = row["calories"]?.asInt() ?: 0,
                protein = row["protein_g"]?.asDouble() ?: 0.0,
                carbs = row["carbs_g"]?.asDouble() ?: 0.0,
                fat = row["fat_g"]?.asDouble() ?: 0.0,
                servingSizeGrams = row["quantity_g"]?.asDouble() ?: 0.0,
                emoji = row["emoji"]?.asString()?.takeIf { it.isNotBlank() },
                servingUnitOptions = parseServingUnitOptions(row["serving_unit_options"]?.asArrayOrNull()),
                selectedServingUnit = row["selected_serving_unit"]?.asString()?.takeIf { it.isNotBlank() },
                selectedServingQuantity = row["selected_serving_quantity"]?.asDouble(),
                // Constituent micros sanitize like the AI parse and meal-share
                // decode (InputSanitizer.micro); entry-level fields keep the
                // pre-existing raw policy.
                sugar = InputSanitizer.micro(row["sugar_g"]?.asDouble()), addedSugar = InputSanitizer.micro(row["added_sugar_g"]?.asDouble()),
                fiber = InputSanitizer.micro(row["fiber_g"]?.asDouble()), saturatedFat = InputSanitizer.micro(row["saturated_fat_g"]?.asDouble()),
                monounsaturatedFat = InputSanitizer.micro(row["monounsaturated_fat_g"]?.asDouble()),
                polyunsaturatedFat = InputSanitizer.micro(row["polyunsaturated_fat_g"]?.asDouble()),
                cholesterol = InputSanitizer.micro(row["cholesterol_mg"]?.asDouble()), sodium = InputSanitizer.micro(row["sodium_mg"]?.asDouble()),
                potassium = InputSanitizer.micro(row["potassium_mg"]?.asDouble()), transFat = InputSanitizer.micro(row["trans_fat_g"]?.asDouble()),
                calcium = InputSanitizer.micro(row["calcium_mg"]?.asDouble()), iron = InputSanitizer.micro(row["iron_mg"]?.asDouble()),
                magnesium = InputSanitizer.micro(row["magnesium_mg"]?.asDouble()), zinc = InputSanitizer.micro(row["zinc_mg"]?.asDouble()),
                vitaminA = InputSanitizer.micro(row["vitamin_a_mcg"]?.asDouble()), vitaminC = InputSanitizer.micro(row["vitamin_c_mg"]?.asDouble()),
                vitaminD = InputSanitizer.micro(row["vitamin_d_mcg"]?.asDouble()), vitaminB12 = InputSanitizer.micro(row["vitamin_b12_mcg"]?.asDouble()),
                vitaminE = InputSanitizer.micro(row["vitamin_e_mg"]?.asDouble()), vitaminK = InputSanitizer.micro(row["vitamin_k_mcg"]?.asDouble()),
                folate = InputSanitizer.micro(row["folate_mcg"]?.asDouble()), omega3 = InputSanitizer.micro(row["omega3_g"]?.asDouble()),
                caffeine = InputSanitizer.micro(row["caffeine_mg"]?.asDouble()),
            )
        }
    }

    /**
     * Dedupe key for weight rows: date + canonical weight value. The value is
     * parsed to a double and re-stringified without trailing zeros so the
     * Android wire form ("80.0") and the PWA form ("80") collapse to the same
     * key. Blank when the row has no usable date/value (tombstones are skipped
     * by the caller anyway).
     */
    private fun weightDedupeKey(row: JsonObject): String {
        val date = row["date"]?.asString() ?: return ""
        val weight = row["weight_kg"]?.asDouble() ?: return ""
        return "$date|${java.math.BigDecimal.valueOf(weight).stripTrailingZeros().toPlainString()}"
    }

    private fun parseWeightWire(el: JsonElement): WeightWire? {
        val o = el.asObjectOrNull() ?: return null
        val id = o["id"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val updatedAt = o["updated_at"]?.asString() ?: return null
        val deletedAt = o["deleted_at"]?.asString()
        if (!deletedAt.isNullOrBlank()) return WeightWire(id, updatedAt, deletedAt, null)
        val date = parseInstant(o["date"]?.asString()) ?: return WeightWire(id, updatedAt, null, null)
        val entry = WeightEntry(
            id = runCatching { UUID.fromString(id) }.getOrElse { UUID.nameUUIDFromBytes(id.toByteArray()) },
            date = date,
            weightKg = o["weight_kg"]?.asDouble() ?: 0.0,
        )
        return WeightWire(id, updatedAt, null, entry)
    }

    private fun parseBodyFatWire(el: JsonElement): BodyFatWire? {
        val o = el.asObjectOrNull() ?: return null
        val id = o["id"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val updatedAt = o["updated_at"]?.asString() ?: return null
        val deletedAt = o["deleted_at"]?.asString()
        if (!deletedAt.isNullOrBlank()) return BodyFatWire(id, updatedAt, deletedAt, null)
        val date = parseInstant(o["date"]?.asString()) ?: return BodyFatWire(id, updatedAt, null, null)
        val percent = o["body_fat_percent"]?.asDouble() ?: 0.0
        val entry = BodyFatEntry(
            id = runCatching { UUID.fromString(id) }.getOrElse { UUID.nameUUIDFromBytes(id.toByteArray()) },
            date = date,
            bodyFatFraction = percent / 100.0,
        )
        return BodyFatWire(id, updatedAt, null, entry)
    }

    private fun parseMeasurementWire(el: JsonElement): MeasurementWire? {
        val o = el.asObjectOrNull() ?: return null
        val id = o["id"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val updatedAt = o["updated_at"]?.asString() ?: return null
        val deletedAt = o["deleted_at"]?.asString()
        if (!deletedAt.isNullOrBlank()) return MeasurementWire(id, updatedAt, deletedAt, null)
        val date = parseInstant(o["date"]?.asString()) ?: return MeasurementWire(id, updatedAt, null, null)
        val entry = BodyMeasurement(
            id = runCatching { UUID.fromString(id) }.getOrElse { UUID.nameUUIDFromBytes(id.toByteArray()) },
            date = date,
            neckCm = o["neck_cm"]?.asDouble(),
            waistCm = o["waist_cm"]?.asDouble(),
            hipsCm = o["hips_cm"]?.asDouble(),
            chestCm = o["chest_cm"]?.asDouble(),
            upperArmCm = o["upper_arm_cm"]?.asDouble(),
            thighCm = o["thigh_cm"]?.asDouble(),
            calfCm = o["calf_cm"]?.asDouble(),
            wristCm = o["wrist_cm"]?.asDouble(),
        )
        return MeasurementWire(id, updatedAt, null, entry)
    }

    private fun parseWaterWire(el: JsonElement): WaterWire? {
        val o = el.asObjectOrNull() ?: return null
        val id = o["id"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val updatedAt = o["updated_at"]?.asString() ?: return null
        val deletedAt = o["deleted_at"]?.asString()
        if (!deletedAt.isNullOrBlank()) return WaterWire(id, updatedAt, deletedAt, null)
        val dateRaw = o["date"]?.asString() ?: return WaterWire(id, updatedAt, null, null)
        val instant = parseInstant(dateRaw)
            ?: runCatching { LocalDate.parse(dateRaw.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
            ?: return WaterWire(id, updatedAt, null, null)
        val entry = WaterEntry(
            id = runCatching { UUID.fromString(id) }.getOrElse { UUID.nameUUIDFromBytes(id.toByteArray()) },
            date = instant,
            milliliters = o["amount_ml"]?.asInt() ?: 0,
        )
        return WaterWire(id, updatedAt, null, entry)
    }

    private fun parseDailyNoteWire(el: JsonElement): DailyNoteWire? {
        val o = el.asObjectOrNull() ?: return null
        val id = o["id"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val updatedAt = o["updated_at"]?.asString() ?: return null
        val deletedAt = o["deleted_at"]?.asString()
        if (!deletedAt.isNullOrBlank()) return DailyNoteWire(id, updatedAt, deletedAt, null)
        val date = o["date"]?.asString()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return DailyNoteWire(id, updatedAt, null, null)
        val text = o["text"]?.asString()?.trim().orEmpty()
        if (text.isEmpty()) return DailyNoteWire(id, updatedAt, null, null)
        val entry = DailyNote(
            id = runCatching { UUID.fromString(id) }.getOrElse { DailyNote.idFor(date) },
            date = date,
            text = text.take(DailyNote.MAX_TEXT_LENGTH),
        )
        return DailyNoteWire(id, updatedAt, null, entry)
    }

    private fun parseNicotineWire(el: JsonElement): NicotineWire? {
        val o = el.asObjectOrNull() ?: return null
        val id = o["id"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val updatedAt = o["updated_at"]?.asString() ?: return null
        val deletedAt = o["deleted_at"]?.asString()
        if (!deletedAt.isNullOrBlank()) return NicotineWire(id, updatedAt, deletedAt, null)
        val dateRaw = o["date"]?.asString() ?: return NicotineWire(id, updatedAt, null, null)
        val instant = parseInstant(dateRaw)
            ?: runCatching { LocalDate.parse(dateRaw.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
            ?: return NicotineWire(id, updatedAt, null, null)
        val entry = NicotineEntry(
            id = runCatching { UUID.fromString(id) }.getOrElse { UUID.nameUUIDFromBytes(id.toByteArray()) },
            date = instant,
            kind = NicotineKind.fromStorage(o["kind"]?.asString()),
            count = (o["count"]?.asInt() ?: 1).coerceAtLeast(1),
            mg = o["mg"]?.takeIf { it !is JsonNull }?.asDouble(),
        )
        return NicotineWire(id, updatedAt, null, entry)
    }

    private fun parseCaffeineWire(el: JsonElement): CaffeineWire? {
        val o = el.asObjectOrNull() ?: return null
        val id = o["id"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val updatedAt = o["updated_at"]?.asString() ?: return null
        val deletedAt = o["deleted_at"]?.asString()
        if (!deletedAt.isNullOrBlank()) return CaffeineWire(id, updatedAt, deletedAt, null)
        val dateRaw = o["date"]?.asString() ?: return CaffeineWire(id, updatedAt, null, null)
        val instant = parseInstant(dateRaw)
            ?: runCatching { LocalDate.parse(dateRaw.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
            ?: return CaffeineWire(id, updatedAt, null, null)
        val mg = o["mg"]?.takeIf { it !is JsonNull }?.asDouble()
        if (mg == null || mg <= 0) return CaffeineWire(id, updatedAt, null, null)
        val entry = CaffeineEntry(
            id = runCatching { UUID.fromString(id) }.getOrElse { UUID.nameUUIDFromBytes(id.toByteArray()) },
            date = instant,
            kind = CaffeineKind.fromStorage(o["kind"]?.asString()),
            mg = mg,
        )
        return CaffeineWire(id, updatedAt, null, entry)
    }

    private fun parseRecipeWire(el: JsonElement): RecipeWire? {
        val o = el.asObjectOrNull() ?: return null
        val id = o["id"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val updatedAt = o["updated_at"]?.asString() ?: return null
        val deletedAt = o["deleted_at"]?.asString()
        if (!deletedAt.isNullOrBlank()) return RecipeWire(id, updatedAt, deletedAt, null)
        val createdAt = parseInstant(o["created_at"]?.asString()) ?: Instant.parse(updatedAt)
        val ingredients = o["ingredients"]?.asArrayOrNull()?.mapNotNull { ingEl ->
            val ing = ingEl.asObjectOrNull() ?: return@mapNotNull null
            RecipeIngredient(
                id = runCatching { UUID.fromString(ing["id"]?.asString()) }.getOrElse { UUID.randomUUID() },
                name = ing["name"]?.asString().orEmpty(),
                baseCalories = ing["base_calories"]?.asInt() ?: 0,
                baseProtein = ing["base_protein_g"]?.asDouble() ?: 0.0,
                baseCarbs = ing["base_carbs_g"]?.asDouble() ?: 0.0,
                baseFat = ing["base_fat_g"]?.asDouble() ?: 0.0,
                quantityScale = ing["quantity_scale"]?.asDouble() ?: 1.0,
                baseFiber = ing["base_fiber_g"]?.asDouble(),
                baseSugar = ing["base_sugar_g"]?.asDouble(),
                baseSodium = ing["base_sodium_mg"]?.asDouble(),
            )
        }.orEmpty()
        val entry = Recipe(
            id = runCatching { UUID.fromString(id) }.getOrElse { UUID.nameUUIDFromBytes(id.toByteArray()) },
            name = o["name"]?.asString().orEmpty(),
            mealType = parseMealType(o["meal_type"]?.asString()),
            ingredients = ingredients,
            createdAt = createdAt,
        )
        return RecipeWire(id, updatedAt, null, entry)
    }

    /**
     * Goal-journal rows (#60): id is the deterministic per-day record id
     * (GoalJournal.idFor), so merge-by-id is per-day LWW. `updated_at` carries
     * updatedAtMillis; `source` is the provenance enum, lowercase.
     */
    private fun parseGoalJournalWire(el: JsonElement): GoalJournalWire? {
        val o = el.asObjectOrNull() ?: return null
        val id = o["id"]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val updatedAt = o["updated_at"]?.asString() ?: return null
        val deletedAt = o["deleted_at"]?.asString()
        if (!deletedAt.isNullOrBlank()) return GoalJournalWire(id, updatedAt, deletedAt, null)
        val date = o["date"]?.asString()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return GoalJournalWire(id, updatedAt, null, null)
        val entry = GoalJournalEntry(
            date = date.toString(),
            calories = o["calories"]?.asInt() ?: 0,
            proteinG = o["protein_g"]?.asInt() ?: 0,
            carbsG = o["carbs_g"]?.asInt() ?: 0,
            fatG = o["fat_g"]?.asInt() ?: 0,
            profileId = o["profile_id"]?.asString()?.takeIf { it.isNotBlank() },
            profileName = o["profile_name"]?.asString()?.takeIf { it.isNotBlank() },
            updatedAtMillis = parseInstant(updatedAt)?.toEpochMilli() ?: 0L,
            source = when (o["source"]?.asString()?.trim()?.uppercase()) {
                "MANUAL_SWITCH" -> GoalJournalSource.MANUAL_SWITCH
                "OVERRIDE" -> GoalJournalSource.OVERRIDE
                "GAP_FILL" -> GoalJournalSource.GAP_FILL
                else -> GoalJournalSource.PLAN
            },
        )
        return GoalJournalWire(id, updatedAt, null, entry)
    }

    private fun parseSingleton(el: JsonElement?): SingletonEnvelope? {
        val o = el?.asObjectOrNull() ?: return null
        val updatedAt = o["updated_at"]?.asString() ?: return null
        val payload = o["payload"]?.asObjectOrNull() ?: buildJsonObject { }
        return SingletonEnvelope(
            updatedAt = updatedAt,
            deletedAt = o["deleted_at"]?.asString(),
            payload = payload,
        )
    }

    private fun metaFor(id: String, revisions: Map<String, Revision>, fallback: String): Revision {
        return revisions[id] ?: Revision(updatedAt = fallback)
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.appendTombstones(
        revisions: Map<String, Revision>,
        kind: String,
        presentIds: Set<String>,
    ) {
        for ((id, rev) in revisions) {
            if (rev.kind != kind || rev.deletedAt.isNullOrBlank()) continue
            if (id in presentIds) continue
            add(
                buildJsonObject {
                    put("id", id)
                    put("updated_at", rev.updatedAt)
                    put("deleted_at", rev.deletedAt)
                },
            )
        }
    }

    private fun injectListTombstones(
        root: JsonObject,
        revisions: Map<String, Revision>,
        kind: String,
        key: String,
    ): JsonObject {
        val existing = root[key]?.asArrayOrNull()?.mapNotNull { it.asObjectOrNull() }.orEmpty().toMutableList()
        val present = existing.mapNotNull { it["id"]?.asString() }.toHashSet()
        var changed = false
        for ((id, rev) in revisions) {
            if (rev.kind != kind || rev.deletedAt.isNullOrBlank()) continue
            if (id in present) {
                val idx = existing.indexOfFirst { it["id"]?.asString() == id }
                if (idx >= 0) {
                    existing[idx] = buildJsonObject {
                        existing[idx].forEach { (k, v) -> put(k, v) }
                        put("updated_at", rev.updatedAt)
                        put("deleted_at", rev.deletedAt)
                    }
                    changed = true
                }
                continue
            }
            existing += buildJsonObject {
                put("id", id)
                put("updated_at", rev.updatedAt)
                put("deleted_at", rev.deletedAt)
            }
            changed = true
        }
        if (!changed) return root
        return buildJsonObject {
            root.forEach { (k, v) ->
                if (k == key) put(k, JsonArray(existing))
                else put(k, v)
            }
        }
    }

    private fun sourceToWire(source: FoodSource): String = when (source) {
        FoodSource.MANUAL -> "manually_edited"
        FoodSource.BARCODE -> "barcode"
        FoodSource.GROUNDED -> "grounded"
        FoodSource.SEARCH -> "search"
        else -> "ai_estimated"
    }

    private fun parseSource(raw: String?): FoodSource = when (raw?.trim()?.lowercase()) {
        "manually_edited", "manual" -> FoodSource.MANUAL
        "barcode" -> FoodSource.BARCODE
        "grounded" -> FoodSource.GROUNDED
        "search" -> FoodSource.SEARCH
        else -> FoodSource.TEXT_INPUT
    }

    private fun parseMealType(raw: String?): String {
        val id = raw?.trim().orEmpty()
        return id.ifEmpty { MealType.OTHER.id }
    }

    private fun parseInstant(raw: String?): Instant? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        return runCatching { Instant.parse(cleaned) }.getOrNull()
            ?: runCatching { Instant.parse(cleaned.replace(" ", "T")) }.getOrNull()
    }

    private fun groundingToWire(g: FoodGroundingProvenance): JsonObject = buildJsonObject {
        put("source_kind", g.sourceKind.name.lowercase().replaceFirstChar { it.lowercase() }.let {
            when (g.sourceKind) {
                NutrientSourceKind.USDA -> "usda"
                NutrientSourceKind.OPEN_FOOD_FACTS -> "openFoodFacts"
                NutrientSourceKind.SWISS -> "swiss"
                NutrientSourceKind.HISTORY -> "history"
                NutrientSourceKind.NUTRITION_LABEL -> "nutritionLabel"
                NutrientSourceKind.MODEL_ESTIMATE -> "modelEstimate"
            }
        })
        putNullable("source_id", g.sourceId)
        putNullable("source_name", g.sourceName)
        putNullable("dataset_version", g.datasetVersion)
        put("identity_confirmed", g.identityConfirmed)
        put("portion_confirmed", g.portionConfirmed)
        put("user_corrected", g.userCorrected)
        putNullable("identity_evidence", g.identityEvidence)
        putNullable("portion_evidence", g.portionEvidence)
        // Keep notes/components light; importer is best-effort.
    }

    private fun parseGrounding(o: JsonObject?): FoodGroundingProvenance? {
        if (o == null) return null
        val kind = when (o["source_kind"]?.asString()) {
            "usda" -> NutrientSourceKind.USDA
            "openFoodFacts" -> NutrientSourceKind.OPEN_FOOD_FACTS
            "swiss" -> NutrientSourceKind.SWISS
            "history" -> NutrientSourceKind.HISTORY
            "nutritionLabel" -> NutrientSourceKind.NUTRITION_LABEL
            else -> NutrientSourceKind.MODEL_ESTIMATE
        }
        return FoodGroundingProvenance(
            sourceKind = kind,
            sourceId = o["source_id"]?.asString(),
            sourceName = o["source_name"]?.asString(),
            datasetVersion = o["dataset_version"]?.asString(),
            identityConfirmed = o["identity_confirmed"]?.asBoolean() ?: false,
            portionConfirmed = o["portion_confirmed"]?.asBoolean() ?: false,
            userCorrected = o["user_corrected"]?.asBoolean() ?: false,
            identityEvidence = o["identity_evidence"]?.asString(),
            portionEvidence = o["portion_evidence"]?.asString(),
        )
    }

    private fun JsonObjectBuilder.putNullable(key: String, value: String?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun JsonObjectBuilder.putNullableNumber(key: String, value: Double?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun JsonObjectBuilder.put(key: String, value: Boolean) {
        put(key, JsonPrimitive(value))
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement.asInt(): Int? = asDouble()?.toInt()
    private fun JsonElement.asBoolean(): Boolean? =
        (this as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
            ?: (this as? JsonPrimitive)?.let { runCatching { it.content.toBoolean() }.getOrNull() }
}

private typealias JsonObjectBuilder = kotlinx.serialization.json.JsonObjectBuilder
