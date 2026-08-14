package app.chompass.models

import app.chompass.R

/**
 * Single catalog for optional micronutrients shared by food review/edit sheets,
 * analysis ↔ entry mapping, and JSON parse keys.
 *
 * Keeps typed fields on the models (no Map-based diary wire format).
 */
enum class MicronutrientField(
    val labelRes: Int,
    val unitRes: Int,
    /** Snake_case key in food-analysis JSON. */
    val jsonKey: String,
    /** Snake_case key in nutrition-label (per-100g) JSON. */
    val jsonKeyPer100g: String,
) {
    SUGAR(R.string.sheet_micro_sugar, R.string.unit_g, "sugar", "sugar_per_100g"),
    ADDED_SUGAR(R.string.sheet_micro_added_sugar, R.string.unit_g, "added_sugar", "added_sugar_per_100g"),
    FIBER(R.string.nutrition_label_fiber, R.string.unit_g, "fiber", "fiber_per_100g"),
    SATURATED_FAT(R.string.sheet_micro_saturated_fat, R.string.unit_g, "saturated_fat", "saturated_fat_per_100g"),
    MONOUNSATURATED_FAT(R.string.sheet_micro_mono_fat, R.string.unit_g, "monounsaturated_fat", "monounsaturated_fat_per_100g"),
    POLYUNSATURATED_FAT(R.string.sheet_micro_poly_fat, R.string.unit_g, "polyunsaturated_fat", "polyunsaturated_fat_per_100g"),
    CHOLESTEROL(R.string.sheet_micro_cholesterol, R.string.unit_mg, "cholesterol", "cholesterol_per_100g"),
    SODIUM(R.string.sheet_micro_sodium, R.string.unit_mg, "sodium", "sodium_per_100g"),
    POTASSIUM(R.string.sheet_micro_potassium, R.string.unit_mg, "potassium", "potassium_per_100g"),
    TRANS_FAT(R.string.nutrition_label_trans_fat, R.string.unit_g, "trans_fat", "trans_fat_per_100g"),
    CALCIUM(R.string.nutrition_label_calcium, R.string.unit_mg, "calcium", "calcium_per_100g"),
    IRON(R.string.nutrition_label_iron, R.string.unit_mg, "iron", "iron_per_100g"),
    MAGNESIUM(R.string.nutrition_label_magnesium, R.string.unit_mg, "magnesium", "magnesium_per_100g"),
    ZINC(R.string.nutrition_label_zinc, R.string.unit_mg, "zinc", "zinc_per_100g"),
    VITAMIN_A(R.string.nutrition_label_vitamin_a, R.string.unit_mcg, "vitamin_a", "vitamin_a_per_100g"),
    VITAMIN_C(R.string.nutrition_label_vitamin_c, R.string.unit_mg, "vitamin_c", "vitamin_c_per_100g"),
    VITAMIN_D(R.string.nutrition_label_vitamin_d, R.string.unit_mcg, "vitamin_d", "vitamin_d_per_100g"),
    VITAMIN_B12(R.string.nutrition_label_vitamin_b12, R.string.unit_mcg, "vitamin_b12", "vitamin_b12_per_100g"),
    VITAMIN_E(R.string.nutrition_label_vitamin_e, R.string.unit_mg, "vitamin_e", "vitamin_e_per_100g"),
    VITAMIN_K(R.string.nutrition_label_vitamin_k, R.string.unit_mcg, "vitamin_k", "vitamin_k_per_100g"),
    FOLATE(R.string.nutrition_label_folate, R.string.unit_mcg, "folate", "folate_per_100g"),
    OMEGA3(R.string.nutrition_label_omega3, R.string.unit_g, "omega_3", "omega_3_per_100g");

    companion object {
        /** Expandable "More Nutrition" section (fiber stays in the primary macros card). */
        val MoreNutrition: List<MicronutrientField> = entries.filter { it != FIBER }
    }
}

/** Snapshot of all optional micronutrient fields for edit UI and mapping. */
data class MicronutrientValues(
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
) : java.io.Serializable {
    operator fun get(field: MicronutrientField): Double? = when (field) {
        MicronutrientField.SUGAR -> sugar
        MicronutrientField.ADDED_SUGAR -> addedSugar
        MicronutrientField.FIBER -> fiber
        MicronutrientField.SATURATED_FAT -> saturatedFat
        MicronutrientField.MONOUNSATURATED_FAT -> monounsaturatedFat
        MicronutrientField.POLYUNSATURATED_FAT -> polyunsaturatedFat
        MicronutrientField.CHOLESTEROL -> cholesterol
        MicronutrientField.SODIUM -> sodium
        MicronutrientField.POTASSIUM -> potassium
        MicronutrientField.TRANS_FAT -> transFat
        MicronutrientField.CALCIUM -> calcium
        MicronutrientField.IRON -> iron
        MicronutrientField.MAGNESIUM -> magnesium
        MicronutrientField.ZINC -> zinc
        MicronutrientField.VITAMIN_A -> vitaminA
        MicronutrientField.VITAMIN_C -> vitaminC
        MicronutrientField.VITAMIN_D -> vitaminD
        MicronutrientField.VITAMIN_B12 -> vitaminB12
        MicronutrientField.VITAMIN_E -> vitaminE
        MicronutrientField.VITAMIN_K -> vitaminK
        MicronutrientField.FOLATE -> folate
        MicronutrientField.OMEGA3 -> omega3
    }

    fun with(field: MicronutrientField, value: Double?): MicronutrientValues = when (field) {
        MicronutrientField.SUGAR -> copy(sugar = value)
        MicronutrientField.ADDED_SUGAR -> copy(addedSugar = value)
        MicronutrientField.FIBER -> copy(fiber = value)
        MicronutrientField.SATURATED_FAT -> copy(saturatedFat = value)
        MicronutrientField.MONOUNSATURATED_FAT -> copy(monounsaturatedFat = value)
        MicronutrientField.POLYUNSATURATED_FAT -> copy(polyunsaturatedFat = value)
        MicronutrientField.CHOLESTEROL -> copy(cholesterol = value)
        MicronutrientField.SODIUM -> copy(sodium = value)
        MicronutrientField.POTASSIUM -> copy(potassium = value)
        MicronutrientField.TRANS_FAT -> copy(transFat = value)
        MicronutrientField.CALCIUM -> copy(calcium = value)
        MicronutrientField.IRON -> copy(iron = value)
        MicronutrientField.MAGNESIUM -> copy(magnesium = value)
        MicronutrientField.ZINC -> copy(zinc = value)
        MicronutrientField.VITAMIN_A -> copy(vitaminA = value)
        MicronutrientField.VITAMIN_C -> copy(vitaminC = value)
        MicronutrientField.VITAMIN_D -> copy(vitaminD = value)
        MicronutrientField.VITAMIN_B12 -> copy(vitaminB12 = value)
        MicronutrientField.VITAMIN_E -> copy(vitaminE = value)
        MicronutrientField.VITAMIN_K -> copy(vitaminK = value)
        MicronutrientField.FOLATE -> copy(folate = value)
        MicronutrientField.OMEGA3 -> copy(omega3 = value)
    }

    /** Scale every present value (1-decimal rounding matches sheet preview). */
    fun scaled(scale: Double, round1: Boolean = true): MicronutrientValues {
        fun s(v: Double?): Double? = v?.let {
            val raw = it * scale
            if (round1) kotlin.math.round(raw * 10) / 10.0 else raw
        }
        return MicronutrientValues(
            sugar = s(sugar),
            addedSugar = s(addedSugar),
            fiber = s(fiber),
            saturatedFat = s(saturatedFat),
            monounsaturatedFat = s(monounsaturatedFat),
            polyunsaturatedFat = s(polyunsaturatedFat),
            cholesterol = s(cholesterol),
            sodium = s(sodium),
            potassium = s(potassium),
            transFat = s(transFat),
            calcium = s(calcium),
            iron = s(iron),
            magnesium = s(magnesium),
            zinc = s(zinc),
            vitaminA = s(vitaminA),
            vitaminC = s(vitaminC),
            vitaminD = s(vitaminD),
            vitaminB12 = s(vitaminB12),
            vitaminE = s(vitaminE),
            vitaminK = s(vitaminK),
            folate = s(folate),
            omega3 = s(omega3),
        )
    }

    fun applyTo(entry: FoodEntry): FoodEntry = entry.copy(
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

    companion object {
        fun from(entry: FoodEntry) = MicronutrientValues(
            sugar = entry.sugar,
            addedSugar = entry.addedSugar,
            fiber = entry.fiber,
            saturatedFat = entry.saturatedFat,
            monounsaturatedFat = entry.monounsaturatedFat,
            polyunsaturatedFat = entry.polyunsaturatedFat,
            cholesterol = entry.cholesterol,
            sodium = entry.sodium,
            potassium = entry.potassium,
            transFat = entry.transFat,
            calcium = entry.calcium,
            iron = entry.iron,
            magnesium = entry.magnesium,
            zinc = entry.zinc,
            vitaminA = entry.vitaminA,
            vitaminC = entry.vitaminC,
            vitaminD = entry.vitaminD,
            vitaminB12 = entry.vitaminB12,
            vitaminE = entry.vitaminE,
            vitaminK = entry.vitaminK,
            folate = entry.folate,
            omega3 = entry.omega3,
        )

        fun fromJson(optDouble: (String) -> Double?) = MicronutrientValues(
            sugar = optDouble(MicronutrientField.SUGAR.jsonKey),
            addedSugar = optDouble(MicronutrientField.ADDED_SUGAR.jsonKey),
            fiber = optDouble(MicronutrientField.FIBER.jsonKey),
            saturatedFat = optDouble(MicronutrientField.SATURATED_FAT.jsonKey),
            monounsaturatedFat = optDouble(MicronutrientField.MONOUNSATURATED_FAT.jsonKey),
            polyunsaturatedFat = optDouble(MicronutrientField.POLYUNSATURATED_FAT.jsonKey),
            cholesterol = optDouble(MicronutrientField.CHOLESTEROL.jsonKey),
            sodium = optDouble(MicronutrientField.SODIUM.jsonKey),
            potassium = optDouble(MicronutrientField.POTASSIUM.jsonKey),
            transFat = optDouble(MicronutrientField.TRANS_FAT.jsonKey),
            calcium = optDouble(MicronutrientField.CALCIUM.jsonKey),
            iron = optDouble(MicronutrientField.IRON.jsonKey),
            magnesium = optDouble(MicronutrientField.MAGNESIUM.jsonKey),
            zinc = optDouble(MicronutrientField.ZINC.jsonKey),
            vitaminA = optDouble(MicronutrientField.VITAMIN_A.jsonKey),
            vitaminC = optDouble(MicronutrientField.VITAMIN_C.jsonKey),
            vitaminD = optDouble(MicronutrientField.VITAMIN_D.jsonKey),
            vitaminB12 = optDouble(MicronutrientField.VITAMIN_B12.jsonKey),
            vitaminE = optDouble(MicronutrientField.VITAMIN_E.jsonKey),
            vitaminK = optDouble(MicronutrientField.VITAMIN_K.jsonKey),
            folate = optDouble(MicronutrientField.FOLATE.jsonKey),
            omega3 = optDouble(MicronutrientField.OMEGA3.jsonKey),
        )

        fun fromLabelJson(optDouble: (String) -> Double?) = MicronutrientValues(
            sugar = optDouble(MicronutrientField.SUGAR.jsonKeyPer100g),
            addedSugar = optDouble(MicronutrientField.ADDED_SUGAR.jsonKeyPer100g),
            fiber = optDouble(MicronutrientField.FIBER.jsonKeyPer100g),
            saturatedFat = optDouble(MicronutrientField.SATURATED_FAT.jsonKeyPer100g),
            monounsaturatedFat = optDouble(MicronutrientField.MONOUNSATURATED_FAT.jsonKeyPer100g),
            polyunsaturatedFat = optDouble(MicronutrientField.POLYUNSATURATED_FAT.jsonKeyPer100g),
            cholesterol = optDouble(MicronutrientField.CHOLESTEROL.jsonKeyPer100g),
            sodium = optDouble(MicronutrientField.SODIUM.jsonKeyPer100g),
            potassium = optDouble(MicronutrientField.POTASSIUM.jsonKeyPer100g),
            transFat = optDouble(MicronutrientField.TRANS_FAT.jsonKeyPer100g),
            calcium = optDouble(MicronutrientField.CALCIUM.jsonKeyPer100g),
            iron = optDouble(MicronutrientField.IRON.jsonKeyPer100g),
            magnesium = optDouble(MicronutrientField.MAGNESIUM.jsonKeyPer100g),
            zinc = optDouble(MicronutrientField.ZINC.jsonKeyPer100g),
            vitaminA = optDouble(MicronutrientField.VITAMIN_A.jsonKeyPer100g),
            vitaminC = optDouble(MicronutrientField.VITAMIN_C.jsonKeyPer100g),
            vitaminD = optDouble(MicronutrientField.VITAMIN_D.jsonKeyPer100g),
            vitaminB12 = optDouble(MicronutrientField.VITAMIN_B12.jsonKeyPer100g),
            vitaminE = optDouble(MicronutrientField.VITAMIN_E.jsonKeyPer100g),
            vitaminK = optDouble(MicronutrientField.VITAMIN_K.jsonKeyPer100g),
            folate = optDouble(MicronutrientField.FOLATE.jsonKeyPer100g),
            omega3 = optDouble(MicronutrientField.OMEGA3.jsonKeyPer100g),
        )
    }
}
