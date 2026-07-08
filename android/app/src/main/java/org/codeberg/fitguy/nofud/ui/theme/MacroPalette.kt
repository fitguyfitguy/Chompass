package org.codeberg.fitguy.nofud.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.codeberg.fitguy.nofud.models.AutoBalanceMacro
import org.codeberg.fitguy.nofud.models.HomeTopNutrient
import org.codeberg.fitguy.nofud.models.OptionalNutrient

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

internal val ThemeMacroPalettes: Map<AppThemeColor, MacroPalette> = mapOf(
    AppThemeColor.TEAL to MacroPalette(
        protein = Color(0xFF4A6CF7),
        carbs = Color(0xFFE5A319),
        fat = Color(0xFFE56B5C),
        fiber = Color(0xFF2D9B6A),
    ),
    AppThemeColor.BLUE to MacroPalette(
        protein = Color(0xFF5B6BC8),
        carbs = Color(0xFFF5B82E),
        fat = Color(0xFFE57373),
        fiber = Color(0xFF26A69A),
    ),
    AppThemeColor.GREEN to MacroPalette(
        protein = Color(0xFF6A4FBF),
        carbs = Color(0xFFD4A017),
        fat = Color(0xFFC45C4A),
        fiber = Color(0xFF4CAF50),
    ),
    AppThemeColor.PURPLE to MacroPalette(
        protein = Color(0xFF3F51B5),
        carbs = Color(0xFFFFB300),
        fat = Color(0xFFFF7043),
        fiber = Color(0xFF7CB342),
    ),
    AppThemeColor.PINK to MacroPalette(
        protein = Color(0xFF7B5FC7),
        carbs = Color(0xFFF9A826),
        fat = Color(0xFFD84A7A),
        fiber = Color(0xFF5DAF6E),
    ),
    AppThemeColor.ORANGE to MacroPalette(
        protein = Color(0xFF5C6BC0),
        carbs = Color(0xFFFF9800),
        fat = Color(0xFFD84315),
        fiber = Color(0xFF689F38),
    ),
    AppThemeColor.INDIGO to MacroPalette(
        protein = Color(0xFF3949AB),
        carbs = Color(0xFFFFC107),
        fat = Color(0xFFEF5350),
        fiber = Color(0xFF00897B),
    ),
    AppThemeColor.NEUTRAL to MacroPalette(
        protein = Color(0xFF607D8B),
        carbs = Color(0xFFC9A227),
        fat = Color(0xFFBC6B6B),
        fiber = Color(0xFF6B8F71),
    ),
)
