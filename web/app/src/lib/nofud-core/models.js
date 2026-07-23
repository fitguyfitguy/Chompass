// @ts-check
/**
 * Shared data shapes for the PWA, mirrored field-for-field against the
 * Android Kotlin models so the IndexedDB layer and the diary/body-metrics
 * export layer agree on one source of truth.
 *
 * Kotlin references:
 *   android/app/src/main/java/org/codeberg/fitguy/nofud/export/DiaryExporter.kt
 *   android/app/src/main/java/org/codeberg/fitguy/nofud/export/BodyMetricsExporter.kt
 */

/**
 * @typedef {"manual" | "ai_estimated" | "barcode" | "grounded"} FoodSource
 * Wire values on export are "manually_edited" | "ai_estimated" | "barcode" | "grounded";
 * `"manual"` here is the internal/model-level id, translated by diary-format.js.
 */

/**
 * @typedef {Object} GroundingComponent
 * @property {string} name
 * @property {number} grams
 * @property {"usda"|"openFoodFacts"|"history"|"nutritionLabel"|"modelEstimate"} sourceKind
 * @property {string|null} [sourceId]
 * @property {string|null} [sourceName]
 * @property {string|null} [matchedBy]
 */

/**
 * @typedef {Object} Grounding
 * @property {"usda"|"openFoodFacts"|"history"|"nutritionLabel"|"modelEstimate"} sourceKind
 * @property {string|null} [sourceId]
 * @property {string|null} [sourceName]
 * @property {string|null} [datasetVersion]
 * @property {boolean} identityConfirmed
 * @property {boolean} portionConfirmed
 * @property {boolean} userCorrected
 * @property {string|null} [identityEvidence]
 * @property {string|null} [portionEvidence]
 * @property {string[]|null} [validationNotes]
 * @property {GroundingComponent[]|null} [components]
 */

/**
 * @typedef {Object} Macro
 * @property {number} calories
 * @property {number} proteinG
 * @property {number} carbsG
 * @property {number} fatG
 */

/**
 * All optional micronutrient fields, mirrored 1:1 against ItemDto in
 * DiaryExporter.kt (24 fields, all nullable numbers, rounded to 1dp on export).
 * @typedef {Object} Micronutrients
 * @property {number|null} [sugarG]
 * @property {number|null} [addedSugarG]
 * @property {number|null} [fiberG]
 * @property {number|null} [saturatedFatG]
 * @property {number|null} [monounsaturatedFatG]
 * @property {number|null} [polyunsaturatedFatG]
 * @property {number|null} [cholesterolMg]
 * @property {number|null} [sodiumMg]
 * @property {number|null} [potassiumMg]
 * @property {number|null} [transFatG]
 * @property {number|null} [calciumMg]
 * @property {number|null} [ironMg]
 * @property {number|null} [magnesiumMg]
 * @property {number|null} [zincMg]
 * @property {number|null} [vitaminAMcg]
 * @property {number|null} [vitaminCMg]
 * @property {number|null} [vitaminDMcg]
 * @property {number|null} [vitaminB12Mcg]
 * @property {number|null} [vitaminEMg]
 * @property {number|null} [vitaminKMcg]
 * @property {number|null} [folateMcg]
 * @property {number|null} [omega3G]
 */

/**
 * @typedef {Macro & Micronutrients & Object} FoodEntry
 * @property {string} id
 * @property {string} name
 * @property {number|null} [quantityG]
 * @property {"breakfast"|"lunch"|"dinner"|"snack"} mealType
 * @property {string} date        ISO date "YYYY-MM-DD"
 * @property {string} time        "HH:mm"
 * @property {FoodSource} source
 * @property {string|null} [note]
 * @property {Grounding|null} [grounding]
 * @property {string|null} [recipeLogId]  shared id across rows from one Recipe log
 */

/**
 * @typedef {Object} RecipeIngredient
 * @property {string} id
 * @property {string} name
 * @property {number} baseCalories
 * @property {number} baseProteinG
 * @property {number} baseCarbsG
 * @property {number} baseFatG
 * @property {number} [quantityScale]
 * @property {number|null} [baseFiberG]
 * @property {number|null} [baseSugarG]
 * @property {number|null} [baseSodiumMg]
 * @property {number|null} [baseQuantityG]
 */

/**
 * @typedef {Object} Recipe
 * @property {string} id
 * @property {string} name
 * @property {"breakfast"|"lunch"|"dinner"|"snack"} mealType
 * @property {RecipeIngredient[]} ingredients
 * @property {string} createdAt
 */

/**
 * @typedef {Object} WeightEntry
 * @property {string} id
 * @property {string} date        ISO-8601 instant string
 * @property {number} weightKg
 */

/**
 * @typedef {Object} BodyFatEntry
 * @property {string} id
 * @property {string} date        ISO-8601 instant string
 * @property {number} bodyFatPercent   0-100 scale (wire); stored as fraction internally, see body-metrics-format.js
 */

/**
 * @typedef {Object} BodyMeasurement
 * @property {string} id
 * @property {string} date        ISO-8601 instant string
 * @property {number|null} [neckCm]
 * @property {number|null} [waistCm]
 * @property {number|null} [hipsCm]
 * @property {number|null} [chestCm]
 * @property {number|null} [upperArmCm]
 * @property {number|null} [thighCm]
 * @property {number|null} [calfCm]
 * @property {number|null} [wristCm]
 */

/**
 * @typedef {Object} WaterEntry
 * @property {string} id
 * @property {string} date        ISO date "YYYY-MM-DD"
 * @property {number} amountMl
 */

/**
 * @typedef {"male"|"female"|"other"} Sex
 * @typedef {"sedentary"|"light"|"moderate"|"active"|"very_active"|"extra_active"} ActivityLevel
 * @typedef {"lose"|"maintain"|"gain"} Goal
 */

/**
 * @typedef {Object} UserProfile
 * @property {Sex} sex
 * @property {number} age
 * @property {number} heightCm
 * @property {number} weightKg
 * @property {number|null} [bodyFatPercentage]  fraction 0-1, null when unknown
 * @property {ActivityLevel} activityLevel
 * @property {Goal} goal
 * @property {number|null} [weeklyChangeKg]      unsigned magnitude; sign derived from `goal` at calc time; default 0.5 when unset
 * @property {boolean} [ketoMode]
 * @property {number|null} [goalWeightKg]
 * @property {number|null} [customCalories]      when set, overrides formula dailyCalories (adaptive / manual pin)
 * @property {number|null} [customProtein]       g/day pin (Android effectiveProtein)
 * @property {number|null} [customCarbs]         g/day pin
 * @property {number|null} [customFat]           g/day pin
 * @property {string|null} [birthday]            ISO date YYYY-MM-DD; when set, age is derived at save time
 * @property {number|null} [goalBodyFatPercentage] fraction 0-1 optional goal
 */

export {};
