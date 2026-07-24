package app.chompass.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.chompass.models.AutoBalanceMacro
import app.chompass.models.HomeTopNutrient
import app.chompass.models.OptionalNutrient

/** Core nutrients that share a per-theme palette (P / C / F / fiber). */
enum class MacroKind {
    CALORIES,
    PROTEIN,
    CARBS,
    FAT,
    FIBER,
    ;

    fun color(): Color = when (this) {
        CALORIES -> AppColors.Calorie
        PROTEIN -> AppColors.Protein
        CARBS -> AppColors.Carbs
        FAT -> AppColors.Fat
        FIBER -> AppColors.Fiber
    }

    val glyph: String
        get() = when (this) {
            PROTEIN -> "P"
            CARBS -> "C"
            FAT -> "F"
            FIBER -> "Fi"
            CALORIES -> "kcal"
        }
}

fun AutoBalanceMacro.toMacroKind(): MacroKind = when (this) {
    AutoBalanceMacro.PROTEIN -> MacroKind.PROTEIN
    AutoBalanceMacro.CARBS -> MacroKind.CARBS
    AutoBalanceMacro.FAT -> MacroKind.FAT
}

fun macroKindFromGlyph(glyph: String): MacroKind? = when (glyph.uppercase()) {
    "P" -> MacroKind.PROTEIN
    "C" -> MacroKind.CARBS
    "F" -> MacroKind.FAT
    else -> null
}

fun OptionalNutrient.macroAccentColor(): Color? = when (this) {
    OptionalNutrient.FIBER -> AppColors.Fiber
    else -> null
}

/** Per-theme macro colors — distinct but harmonious with each accent. */
data class MacroPalette(
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val fiber: Color,
) {
    fun proteinArgb(): Int = protein.toArgb() and 0xFFFFFF
    fun carbsArgb(): Int = carbs.toArgb() and 0xFFFFFF
    fun fatArgb(): Int = fat.toArgb() and 0xFFFFFF
    fun fiberArgb(): Int = fiber.toArgb() and 0xFFFFFF

    fun colorFor(nutrient: HomeTopNutrient): Color = when (nutrient) {
        HomeTopNutrient.PROTEIN -> protein
        HomeTopNutrient.CARBS -> carbs
        HomeTopNutrient.FAT -> fat
        HomeTopNutrient.FIBER -> fiber
        else -> Color(0xFF79747E)
    }

    fun hexForNutrientId(id: String): Int = when (id) {
        HomeTopNutrient.PROTEIN.storageKey -> proteinArgb()
        HomeTopNutrient.CARBS.storageKey -> carbsArgb()
        HomeTopNutrient.FAT.storageKey -> fatArgb()
        HomeTopNutrient.FIBER.storageKey -> fiberArgb()
        else -> proteinArgb()
    }

}

/**
 * Per-theme macro colors — distinct but harmonious with each accent.
 *
 * Semantic conventions (aligned with common nutrition-app UX, e.g. Cronometer / Lose It):
 * - **Protein** — cool blue / indigo (muscle, amino acids)
 * - **Carbs** — warm amber / gold (grains, quick energy)
 * - **Fat** — coral / rose-red (oils, lipids)
 * - **Fiber** — leaf green (plants; always greenish, never teal/cyan so it
 *   stays distinct from teal/blue theme accents)
 *
 * Each palette keeps similar chroma so the four macros read as one family while
 * the hues stay recognizable across all eight theme accents.
 */
internal val ThemeMacroPalettes: Map<AppThemeColor, MacroPalette> = mapOf(
    AppThemeColor.TEAL to MacroPalette(
        protein = Color(0xFF5B6AD6),
        carbs = Color(0xFFD9A014),
        fat = Color(0xFFE06B58),
        fiber = Color(0xFF3D9B56),
    ),
    AppThemeColor.BLUE to MacroPalette(
        protein = Color(0xFF6370E0),
        carbs = Color(0xFFEDAE2E),
        fat = Color(0xFFEB7070),
        fiber = Color(0xFF48A84A),
    ),
    AppThemeColor.GREEN to MacroPalette(
        protein = Color(0xFF7A5FD8),
        carbs = Color(0xFFCF9B1F),
        fat = Color(0xFFD25A42),
        fiber = Color(0xFF5CB85C),
    ),
    AppThemeColor.PURPLE to MacroPalette(
        protein = Color(0xFF5068D6),
        carbs = Color(0xFFEDB42F),
        fat = Color(0xFFEE7A62),
        fiber = Color(0xFF6BB04A),
    ),
    AppThemeColor.PINK to MacroPalette(
        protein = Color(0xFF6F6AD8),
        carbs = Color(0xFFEAB02E),
        fat = Color(0xFFE45A82),
        fiber = Color(0xFF4FAF62),
    ),
    AppThemeColor.ORANGE to MacroPalette(
        protein = Color(0xFF6878D8),
        carbs = Color(0xFFF5A623),
        fat = Color(0xFFE04E30),
        fiber = Color(0xFF699E42),
    ),
    AppThemeColor.INDIGO to MacroPalette(
        protein = Color(0xFF5A7AE8),
        carbs = Color(0xFFF0B830),
        fat = Color(0xFFF06860),
        fiber = Color(0xFF58AD50),
    ),
    AppThemeColor.NEUTRAL to MacroPalette(
        protein = Color(0xFF6285D0),
        carbs = Color(0xFFC49A28),
        fat = Color(0xFFC87068),
        fiber = Color(0xFF63966A),
    ),
)
