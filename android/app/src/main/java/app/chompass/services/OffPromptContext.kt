package app.chompass.services

import app.chompass.data.PreferencesStore
import app.chompass.services.ai.FoodAnalysis
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formats Open Food Facts product hits as structured AI prompt context
 * (soft hint — photo AI still runs). Shared wording with the PWA helper.
 */
object OffPromptContext {
    private const val LOOKUP_TIMEOUT_MS = 8_000L
    private val INSTRUCTIONS = """
Use these values as authoritative package label data for identity and nutrient density.
Scale to the portion actually visible in the photo (may differ from one serving).
If other foods are visible that are not covered by these products, estimate them separately and include them in the total.
Do not invent conflicting macros for the matched packaged item when this data is present.
""".trimIndent()

    data class ProductHit(
        val barcode: String,
        val name: String,
        val servingGrams: Double,
        val calories: Int,
        val proteinG: Double,
        val carbsG: Double,
        val fatG: Double,
        val sugarG: Double? = null,
        val fiberG: Double? = null,
        val sodiumMg: Double? = null,
    )

    fun format(hits: List<ProductHit>): String {
        if (hits.isEmpty()) return ""
        val header = if (hits.size == 1) {
            "Open Food Facts match detected in the attached photo(s):"
        } else {
            "Open Food Facts matches detected in the attached photo(s):"
        }
        val blocks = hits.joinToString("\n") { hit ->
            buildString {
                append("- barcode: ${hit.barcode}\n")
                append("  name: ${hit.name}\n")
                append(
                    "  nutrition for one labeled serving (${formatGrams(hit.servingGrams)}): " +
                        "${hit.calories} kcal, P ${formatMacro(hit.proteinG)} g, " +
                        "C ${formatMacro(hit.carbsG)} g, F ${formatMacro(hit.fatG)} g"
                )
                val micros = buildList {
                    hit.sugarG?.let { add("sugar ${formatMacro(it)} g") }
                    hit.fiberG?.let { add("fiber ${formatMacro(it)} g") }
                    hit.sodiumMg?.let { add("sodium ${formatMacro(it)} mg") }
                }
                if (micros.isNotEmpty()) {
                    append("\n  also: ${micros.joinToString(", ")}")
                }
                val per100 = per100Line(hit)
                if (per100 != null) {
                    append("\n  per 100 g (derived from labeled serving): $per100")
                }
            }
        }
        return "$header\n$blocks\n\n$INSTRUCTIONS"
    }

    fun formatFromAnalyses(analyses: List<FoodAnalysis>): String {
        val hits = analyses.mapNotNull { analysis ->
            val barcode = analysis.grounding?.sourceId?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            ProductHit(
                barcode = barcode,
                name = analysis.name,
                servingGrams = analysis.servingSizeGrams ?: 100.0,
                calories = analysis.calories,
                proteinG = analysis.protein,
                carbsG = analysis.carbs,
                fatG = analysis.fat,
                sugarG = analysis.sugar,
                fiberG = analysis.fiber,
                sodiumMg = analysis.sodium,
            )
        }
        return format(hits)
    }

    /**
     * Decode barcodes from [imageBytesList], look up OFF (cache/network), return
     * prompt block or null. Fail-soft: never throws; timeout/miss → null.
     */
    suspend fun collectFromImages(
        imageBytesList: List<ByteArray>,
        prefs: PreferencesStore?,
    ): String? {
        if (prefs == null || imageBytesList.isEmpty()) return null
        val codes = BarcodeImageDecoder.decodeAll(imageBytesList)
        if (codes.isEmpty()) return null
        val analyses = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            coroutineScope {
                codes.map { code ->
                    async {
                        runCatching { OpenFoodFactsService.lookup(code, prefs) }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
        }.orEmpty()
        if (analyses.isEmpty()) return null
        return formatFromAnalyses(analyses).takeIf { it.isNotBlank() }
    }

    private fun per100Line(hit: ProductHit): String? {
        val g = hit.servingGrams
        if (g <= 0.0) return null
        val scale = 100.0 / g
        return "${(hit.calories * scale).roundToInt()} kcal, " +
            "P ${formatMacro(hit.proteinG * scale)} g, " +
            "C ${formatMacro(hit.carbsG * scale)} g, " +
            "F ${formatMacro(hit.fatG * scale)} g"
    }

    private fun formatGrams(g: Double): String =
        if (g == g.toLong().toDouble()) "${g.toLong()}g" else String.format(Locale.US, "%.1fg", g)

    private fun formatMacro(v: Double): String =
        String.format(Locale.US, "%.1f", v)
}
