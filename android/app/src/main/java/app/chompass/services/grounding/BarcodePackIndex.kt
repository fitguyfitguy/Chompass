package app.chompass.services.grounding

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.chompass.models.FoodGroundingProvenance
import app.chompass.models.FoodProductMetadata
import app.chompass.models.NutrientBasis
import app.chompass.models.NutrientSourceKind
import app.chompass.models.ServingUnitOption
import app.chompass.services.ai.FoodAnalysis
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** One row of a downloadable offline barcode pack (barcode-exact, per-100g macros). */
data class BarcodePackProduct(
    val code: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double?,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
    val servingGrams: Double?,
)

/**
 * Resolves the optional offline barcode packs for the barcode lookup chain
 * (null = pack not installed). Supplied from AppContainer; callers that
 * don't pass one behave exactly as before the packs existed.
 */
interface PackResolver {
    /** USDA FDC branded foods (worldwide GTINs). */
    fun gtins(): BarcodePackIndex?

    /** Regional OFF slice (Open Food Facts Germany pilot). */
    fun regional(): BarcodePackIndex?
}

/**
 * Read-only handle over a downloaded barcode pack SQLite file
 * (`filesDir/offline_index/<pack-id>.sqlite`, installed by
 * [app.chompass.services.offline.OfflineIndexManager]). Pack files never ship
 * in the APK. Schema (no FTS — barcode-exact lookups only):
 *
 * ```
 * barcodes(code TEXT PRIMARY KEY, name TEXT NOT NULL, brand TEXT,
 *          kcal REAL, protein REAL, carbs REAL, fat REAL, serving_g REAL)
 * meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)   -- pack_id, dataset_version, …
 * ```
 */
class BarcodePackIndex(private val dbFile: File) {
    private var db: SQLiteDatabase? = null

    /** Opens the read-only handle; idempotent. */
    fun open() {
        if (db == null) {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
        }
    }

    fun close() {
        db?.close()
        db = null
    }

    /** Exact barcode match; null when the code is not in the pack. */
    fun lookup(code: String): BarcodePackProduct? {
        open()
        val d = db ?: return null
        return d.rawQuery(
            "SELECT code, name, brand, kcal, protein, carbs, fat, serving_g " +
                "FROM barcodes WHERE code = ? LIMIT 1",
            arrayOf(code),
        ).use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                BarcodePackProduct(
                    code = c.getString(0),
                    name = c.getString(1),
                    brand = c.stringOrNull(2),
                    kcalPer100g = c.realOrNull(3),
                    proteinPer100g = c.realOrNull(4),
                    carbsPer100g = c.realOrNull(5),
                    fatPer100g = c.realOrNull(6),
                    servingGrams = c.realOrNull(7),
                )
            }
        }
    }

    fun meta(key: String): String? {
        open()
        val d = db ?: return null
        return d.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.stringOrNull(0) else null
        }
    }
}

/** Cursor column readers that distinguish NULL from 0. */
private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
private fun Cursor.realOrNull(index: Int): Double? = if (isNull(index)) null else getDouble(index)

/**
 * Maps a pack row to a reviewable [FoodAnalysis], mirroring the OFF barcode
 * mapping (brand-prefixed display name, per-serving macros from per-100g ×
 * serving/100 — per-100g when the row has no serving — a "serving" unit
 * option when labeled, grounding provenance stamped with the pack identity,
 * and barcode-only product metadata so the sheet shows provenance like every
 * OFF hit).
 */
fun BarcodePackProduct.toFoodAnalysis(index: BarcodePackIndex): FoodAnalysis {
    val packId = index.meta("pack_id") ?: "unknown"
    val version = index.meta("dataset_version")
    val datasetVersion = if (version != null) "$packId:$version" else packId
    val sourceKind = when (packId) {
        "usda-gtins" -> NutrientSourceKind.USDA
        else -> NutrientSourceKind.OPEN_FOOD_FACTS
    }
    val labeledServing = servingGrams?.takeIf { it > 0 }
    val servingGrams = maxOf(labeledServing ?: 100.0, 1.0)
    val scale = servingGrams / 100.0
    val name = displayName()
    val servingOptions = labeledServing?.let {
        listOf(ServingUnitOption(unit = "serving", gramsPerUnit = it, quantity = 1.0))
    } ?: emptyList()
    return FoodAnalysis(
        name = name,
        calories = kcalPer100g?.times(scale)?.roundToInt() ?: 0,
        protein = proteinPer100g?.times(scale) ?: 0.0,
        carbs = carbsPer100g?.times(scale) ?: 0.0,
        fat = fatPer100g?.times(scale) ?: 0.0,
        servingSizeGrams = servingGrams,
        emoji = "🏷️",
        servingUnitOptions = servingOptions,
        selectedServingUnit = servingOptions.firstOrNull()?.unit,
        selectedServingQuantity = servingOptions.firstOrNull()?.let { 1.0 },
        grounding = FoodGroundingProvenance(
            sourceKind = sourceKind,
            sourceId = code,
            sourceName = name,
            nutrientBasis = NutrientBasis.PER_SERVING,
            datasetVersion = datasetVersion,
            retrievedAtEpochMs = System.currentTimeMillis(),
            identityEvidence = "barcode:$code",
            portionEvidence = "serving_quantity=${servingGrams}g",
            identityConfirmed = true,
        ),
        productMetadata = FoodProductMetadata(barcode = code),
    )
}

/** Brand-prefixed display name, matching the OFF product name composition. */
private fun BarcodePackProduct.displayName(): String {
    val primary = name.trim().takeIf { it.isNotEmpty() }
    val brand = brand?.trim()?.takeIf { it.isNotEmpty() }
    if (primary != null && brand != null &&
        !primary.lowercase(Locale.US).contains(brand.lowercase(Locale.US))
    ) {
        return "$brand $primary"
    }
    return primary ?: brand ?: "Barcode $code"
}
