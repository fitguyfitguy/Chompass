package org.codeberg.fitguy.nofud.services.grounding

import org.codeberg.fitguy.nofud.data.FoodRepository
import org.codeberg.fitguy.nofud.data.PreferencesStore
import org.codeberg.fitguy.nofud.models.FoodGroundingProvenance
import org.codeberg.fitguy.nofud.models.FoodRecognitionResult
import org.codeberg.fitguy.nofud.models.GroundedComponentProvenance
import org.codeberg.fitguy.nofud.models.GroundingCandidate
import org.codeberg.fitguy.nofud.models.GroundingConfidence
import org.codeberg.fitguy.nofud.models.GroundingValidator
import org.codeberg.fitguy.nofud.models.NutrientBasis
import org.codeberg.fitguy.nofud.models.NutrientSourceKind
import org.codeberg.fitguy.nofud.models.RecognizedFoodComponent
import org.codeberg.fitguy.nofud.models.ServingUnitOption
import org.codeberg.fitguy.nofud.services.OpenFoodFactsService
import org.codeberg.fitguy.nofud.services.ai.FoodAnalysis
import org.codeberg.fitguy.nofud.services.ai.FoodAnalysisService
import org.codeberg.fitguy.nofud.ui.home.EntryAnalysisPhase
import org.codeberg.fitguy.nofud.ui.home.FoodAnalysisProgress
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * Bounded grounded-entry orchestrator.
 *
 * Cloud path: model tool loop (search_usda / search_history / lookup_barcode →
 * finalize_grounding) then deterministic nutrient scaling from selected rows.
 * On-device / test delegates: recognize → retrieve → rank (legacy path).
 * Models never overwrite retrieved nutrients.
 */
class GroundedFoodEntryService(
    private val foodAnalysis: FoodAnalysisService,
    private val foodRepository: FoodRepository,
    private val prefs: PreferencesStore,
    private val usdaIndex: UsdaFoodIndex,
    private val recognizeDelegate: (suspend (
        description: String?,
        images: List<ByteArray>,
        onProgress: (FoodAnalysisProgress) -> Unit,
    ) -> FoodRecognitionResult)? = null,
    private val barcodeLookup: (suspend (String) -> FoodAnalysis)? = null,
    private val estimateFallback: (suspend (String) -> FoodAnalysis)? = null,
) {

    data class ComponentResolution(
        val component: RecognizedFoodComponent,
        val selected: GroundingCandidate?,
        val candidates: List<GroundingCandidate>,
        val analysis: FoodAnalysis?,
        val needsUserChoice: Boolean,
        val question: String? = null,
    )

    data class GroundedResult(
        val analysis: FoodAnalysis,
        val resolutions: List<ComponentResolution>,
        val recognition: FoodRecognitionResult,
    )

    suspend fun analyze(
        description: String? = null,
        imageBytesList: List<ByteArray> = emptyList(),
        onProgress: (FoodAnalysisProgress) -> Unit = {},
        /**
         * Optional map of component index → chosen sourceId when the user
         * already answered an ambiguous match. Empty on first pass.
         */
        selectedSourceIds: Map<Int, String> = emptyMap(),
        /** Optional per-component gram overrides from the correction UI. */
        gramOverrides: Map<Int, Double> = emptyMap(),
    ): GroundedResult {
        val historyPool = buildHistoryPool()
        val useToolLoop = selectedSourceIds.isEmpty() &&
            recognizeDelegate == null &&
            runCatching { foodAnalysis.supportsGroundedToolLoop() }.getOrDefault(false)

        if (useToolLoop) {
            return analyzeWithToolLoop(
                description = description,
                imageBytesList = imageBytesList,
                onProgress = onProgress,
                historyPool = historyPool,
                gramOverrides = gramOverrides,
            )
        }

        return analyzeDeterministic(
            description = description,
            imageBytesList = imageBytesList,
            onProgress = onProgress,
            historyPool = historyPool,
            selectedSourceIds = selectedSourceIds,
            gramOverrides = gramOverrides,
        )
    }

    private suspend fun analyzeWithToolLoop(
        description: String?,
        imageBytesList: List<ByteArray>,
        onProgress: (FoodAnalysisProgress) -> Unit,
        historyPool: List<org.codeberg.fitguy.nofud.models.FoodEntry>,
        gramOverrides: Map<Int, Double>,
    ): GroundedResult {
        val tools = GroundingTools(
            usdaIndex = usdaIndex,
            historyPool = historyPool,
            prefs = prefs,
            barcodeLookup = barcodeLookup,
        )
        val userMessage = buildString {
            appendLine("Ground this meal. Search databases, then call finalize_grounding.")
            val text = description?.trim().orEmpty()
            if (text.isNotEmpty()) {
                appendLine()
                appendLine("User description: $text")
            }
            if (imageBytesList.any { it.isNotEmpty() }) {
                appendLine()
                appendLine("Use the attached image(s) as primary visual evidence.")
            }
        }
        val loop = try {
            foodAnalysis.runGroundedToolLoop(
                tools = tools,
                userMessage = userMessage,
                imageBytesList = imageBytesList,
                onProgress = onProgress,
            )
        } catch (_: Throwable) {
            // Provider cannot tool-call — fall back to recognize + lexical rank.
            return analyzeDeterministic(
                description = description,
                imageBytesList = imageBytesList,
                onProgress = onProgress,
                historyPool = historyPool,
                selectedSourceIds = emptyMap(),
                gramOverrides = gramOverrides,
            )
        }

        val finalize = loop.finalize
        val recognition = FoodRecognitionResult(
            mealName = finalize.mealName,
            emoji = finalize.emoji,
            components = finalize.components.map { c ->
                RecognizedFoodComponent(
                    name = c.name,
                    brand = c.brand,
                    preparation = c.preparation,
                    estimatedGrams = c.grams,
                    portionHint = c.portionHint,
                    barcode = c.barcode,
                )
            },
            notes = finalize.notes,
        )

        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Resolving))
        val resolutions = finalize.components.mapIndexed { index, pick ->
            resolveFinalizedComponent(
                pick = pick,
                historyPool = historyPool,
                gramOverride = gramOverrides[index],
            )
        }
        return finishResolutions(recognition, resolutions, onProgress, selectedSourceIdsEmpty = true)
    }

    private suspend fun analyzeDeterministic(
        description: String?,
        imageBytesList: List<ByteArray>,
        onProgress: (FoodAnalysisProgress) -> Unit,
        historyPool: List<org.codeberg.fitguy.nofud.models.FoodEntry>,
        selectedSourceIds: Map<Int, String>,
        gramOverrides: Map<Int, Double>,
    ): GroundedResult {
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Recognizing))
        val recognition = if (recognizeDelegate != null) {
            recognizeDelegate.invoke(description, imageBytesList, onProgress)
        } else {
            foodAnalysis.recognizeFoodComponents(description, imageBytesList, onProgress)
        }

        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.SearchingHistory))
        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.SearchingUsda))
        val resolutions = recognition.components.mapIndexed { index, component ->
            resolveComponent(
                component = component,
                historyPool = historyPool,
                selectedSourceId = selectedSourceIds[index],
                gramOverride = gramOverrides[index],
            )
        }

        onProgress(FoodAnalysisProgress.Phase(EntryAnalysisPhase.Resolving))
        return finishResolutions(
            recognition,
            resolutions,
            onProgress,
            selectedSourceIdsEmpty = selectedSourceIds.isEmpty(),
        )
    }

    private suspend fun finishResolutions(
        recognition: FoodRecognitionResult,
        resolutions: List<ComponentResolution>,
        onProgress: (FoodAnalysisProgress) -> Unit,
        selectedSourceIdsEmpty: Boolean,
    ): GroundedResult {
        val ambiguous = resolutions.filter { it.needsUserChoice }
        if (ambiguous.isNotEmpty() && selectedSourceIdsEmpty) {
            val partialParts = resolutions.mapNotNull { it.analysis }
            val draft = if (partialParts.isNotEmpty()) {
                NutrientScaling.sumAnalyses(recognition.mealName, recognition.emoji, partialParts)
            } else {
                FoodAnalysis(
                    name = recognition.mealName,
                    calories = 0,
                    protein = 0.0,
                    carbs = 0.0,
                    fat = 0.0,
                    servingSizeGrams = 100.0,
                    emoji = recognition.emoji,
                )
            }
            val withProv = attachProvenance(draft, resolutions, partial = true)
            return GroundedResult(withProv, resolutions, recognition)
        }

        val parts = mutableListOf<FoodAnalysis>()
        val finalResolutions = resolutions.toMutableList()
        for ((index, resolution) in resolutions.withIndex()) {
            val analysis = resolution.analysis
                ?: fallbackEstimate(resolution.component, null)
            parts += analysis
            if (resolution.analysis == null) {
                finalResolutions[index] = resolution.copy(
                    analysis = analysis,
                    selected = GroundingCandidate(
                        sourceKind = NutrientSourceKind.MODEL_ESTIMATE,
                        sourceId = "model:${resolution.component.name}",
                        displayName = resolution.component.name,
                        score = 0.0,
                        matchedBy = "model_fallback",
                    ),
                    needsUserChoice = false,
                )
            }
        }

        val combined = NutrientScaling.sumAnalyses(recognition.mealName, recognition.emoji, parts)
        val grounded = attachProvenance(combined, finalResolutions, partial = false)
        onProgress(FoodAnalysisProgress.Complete(grounded))
        return GroundedResult(grounded, finalResolutions, recognition)
    }

    private suspend fun resolveFinalizedComponent(
        pick: GroundingTools.FinalizeComponent,
        historyPool: List<org.codeberg.fitguy.nofud.models.FoodEntry>,
        gramOverride: Double?,
    ): ComponentResolution {
        val component = RecognizedFoodComponent(
            name = pick.name,
            brand = pick.brand,
            preparation = pick.preparation,
            estimatedGrams = pick.grams,
            portionHint = pick.portionHint,
            barcode = pick.barcode,
        )
        val query = listOfNotNull(pick.brand, pick.name, pick.preparation).joinToString(" ").trim()

        if (pick.needsUserChoice) {
            val ranked = gatherCandidates(query, historyPool)
            return ComponentResolution(
                component = component,
                selected = null,
                candidates = ranked,
                analysis = null,
                needsUserChoice = true,
                question = "Which match is \"${pick.name}\"?",
            )
        }

        if (pick.rejectToEstimate || pick.sourceId.isNullOrBlank()) {
            return ComponentResolution(
                component = component,
                selected = null,
                candidates = emptyList(),
                analysis = null,
                needsUserChoice = false,
                question = "No database match for \"${pick.name}\". Estimate nutrients?",
            )
        }

        val kind = parseSourceKind(pick.sourceKind, pick.sourceId)
        val grams = gramOverride ?: pick.grams
        return when (kind) {
            NutrientSourceKind.OPEN_FOOD_FACTS -> {
                val barcode = pick.sourceId.filter { it.isDigit() }.ifEmpty {
                    pick.barcode?.filter { it.isDigit() }.orEmpty()
                }
                resolveComponent(
                    component = component.copy(barcode = barcode.takeIf { it.length >= 8 }),
                    historyPool = historyPool,
                    selectedSourceId = barcode,
                    gramOverride = grams,
                )
            }
            NutrientSourceKind.USDA -> {
                val fdcId = pick.sourceId.toLongOrNull()
                val record = fdcId?.let { usdaIndex.getByFdcId(it) }
                if (record == null || record.calories == null) {
                    val ranked = gatherCandidates(query, historyPool)
                    return ComponentResolution(
                        component = component,
                        selected = null,
                        candidates = ranked,
                        analysis = null,
                        needsUserChoice = ranked.isNotEmpty(),
                        question = if (ranked.isNotEmpty()) {
                            "Which match is \"${pick.name}\"?"
                        } else {
                            "No database match for \"${pick.name}\". Estimate nutrients?"
                        },
                    )
                }
                val g = grams ?: record.servingGrams ?: 100.0
                val candidate = record.toCandidate(10.0, usdaIndex.version())
                val analysis = record.toFoodAnalysis(g, usdaIndex.version())
                ComponentResolution(
                    component = component,
                    selected = candidate,
                    candidates = listOf(candidate),
                    analysis = analysis,
                    needsUserChoice = false,
                )
            }
            NutrientSourceKind.HISTORY -> {
                val hit = ConfirmedHistorySearch.search(historyPool, query, limit = 8)
                    .firstOrNull { it.entry.favoriteKey == pick.sourceId }
                    ?: historyPool.firstOrNull { it.favoriteKey == pick.sourceId }?.let {
                        ConfirmedHistorySearch.HistoryHit(
                            entry = it,
                            score = 10.0,
                            frequency = 1,
                            daysSince = 0,
                            matchedBy = "finalize_source_id",
                        )
                    }
                if (hit == null) {
                    return ComponentResolution(
                        component = component,
                        selected = null,
                        candidates = gatherCandidates(query, historyPool),
                        analysis = null,
                        needsUserChoice = true,
                        question = "Which match is \"${pick.name}\"?",
                    )
                }
                val candidate = ConfirmedHistorySearch.toCandidate(hit)
                val g = grams ?: component.estimatedGrams ?: hit.entry.servingSizeGrams ?: 100.0
                ComponentResolution(
                    component = component,
                    selected = candidate,
                    candidates = listOf(candidate),
                    analysis = historyToAnalysis(hit.entry, g, component),
                    needsUserChoice = false,
                )
            }
            else -> resolveComponent(
                component = component,
                historyPool = historyPool,
                selectedSourceId = pick.sourceId,
                gramOverride = grams,
            )
        }
    }

    private fun parseSourceKind(raw: String?, sourceId: String): NutrientSourceKind {
        when (raw?.lowercase()) {
            "usda", "survey_fndds_food", "foundation_food" -> return NutrientSourceKind.USDA
            "history" -> return NutrientSourceKind.HISTORY
            "openfoodfacts", "open_food_facts", "off" -> return NutrientSourceKind.OPEN_FOOD_FACTS
        }
        val asLong = sourceId.toLongOrNull()
        if (asLong != null && usdaIndex.getByFdcId(asLong) != null) {
            return NutrientSourceKind.USDA
        }
        if (sourceId.all { it.isDigit() } && sourceId.length >= 8) {
            return NutrientSourceKind.OPEN_FOOD_FACTS
        }
        if (asLong != null) return NutrientSourceKind.USDA
        return NutrientSourceKind.HISTORY
    }

    private fun gatherCandidates(
        query: String,
        historyPool: List<org.codeberg.fitguy.nofud.models.FoodEntry>,
    ): List<GroundingCandidate> {
        val candidates = mutableListOf<GroundingCandidate>()
        candidates += ConfirmedHistorySearch.search(historyPool, query).map {
            ConfirmedHistorySearch.toCandidate(it)
        }
        candidates += usdaIndex.search(query, limit = 6)
        return candidates
            .sortedByDescending { sourceAwareScore(it) }
            .distinctBy { "${it.sourceKind}:${it.sourceId}" }
            .take(6)
    }

    private suspend fun buildHistoryPool(): List<org.codeberg.fitguy.nofud.models.FoodEntry> {
        val diary = foodRepository.entries.first()
        val favorites = runCatching { foodRepository.migratedFavorites() }.getOrDefault(emptyList())
        return diary + favorites
    }

    private suspend fun resolveComponent(
        component: RecognizedFoodComponent,
        historyPool: List<org.codeberg.fitguy.nofud.models.FoodEntry>,
        selectedSourceId: String?,
        gramOverride: Double?,
    ): ComponentResolution {
        val query = listOfNotNull(component.brand, component.name, component.preparation)
            .joinToString(" ")
            .trim()
        val candidates = mutableListOf<GroundingCandidate>()

        val barcode = component.barcode?.filter { it.isDigit() }?.takeIf { it.length >= 8 }
        if (barcode != null) {
            val off = runCatching {
                barcodeLookup?.invoke(barcode)
                    ?: OpenFoodFactsService.lookup(barcode, prefs)
            }.getOrNull()
            if (off != null) {
                val grams = gramOverride
                    ?: component.estimatedGrams
                    ?: off.servingSizeGrams
                val scaled = if (grams != off.servingSizeGrams && off.servingSizeGrams > 0) {
                    val scale = grams / off.servingSizeGrams
                    off.copy(
                        calories = (off.calories * scale).roundToInt(),
                        protein = off.protein * scale,
                        carbs = off.carbs * scale,
                        fat = off.fat * scale,
                        servingSizeGrams = grams,
                    )
                } else {
                    off
                }
                val candidate = GroundingCandidate(
                    sourceKind = NutrientSourceKind.OPEN_FOOD_FACTS,
                    sourceId = barcode,
                    displayName = off.name,
                    score = 100.0,
                    caloriesPer100g = if (off.servingSizeGrams > 0) {
                        off.calories * 100.0 / off.servingSizeGrams
                    } else null,
                    proteinPer100g = if (off.servingSizeGrams > 0) {
                        off.protein * 100.0 / off.servingSizeGrams
                    } else null,
                    carbsPer100g = if (off.servingSizeGrams > 0) {
                        off.carbs * 100.0 / off.servingSizeGrams
                    } else null,
                    fatPer100g = if (off.servingSizeGrams > 0) {
                        off.fat * 100.0 / off.servingSizeGrams
                    } else null,
                    servingSizeGrams = grams,
                    matchedBy = "barcode",
                    datasetVersion = "openfoodfacts-live",
                )
                return ComponentResolution(
                    component = component,
                    selected = candidate,
                    candidates = listOf(candidate),
                    analysis = scaled.copy(
                        grounding = (scaled.grounding ?: FoodGroundingProvenance(
                            sourceKind = NutrientSourceKind.OPEN_FOOD_FACTS,
                            sourceId = barcode,
                        )).copy(
                            portionEvidence = component.portionHint
                                ?: "estimated_grams=${component.estimatedGrams}",
                        ),
                    ),
                    needsUserChoice = false,
                )
            }
        }

        val historyHits = ConfirmedHistorySearch.search(historyPool, query)
        candidates += historyHits.map { ConfirmedHistorySearch.toCandidate(it) }
        candidates += usdaIndex.search(query, limit = 6)

        val ranked = candidates
            .sortedByDescending { sourceAwareScore(it) }
            .distinctBy { "${it.sourceKind}:${it.sourceId}" }
            .take(6)

        if (ranked.isEmpty()) {
            return ComponentResolution(
                component = component,
                selected = null,
                candidates = emptyList(),
                analysis = null,
                needsUserChoice = false,
                question = "No database match for \"${component.name}\". Estimate nutrients?",
            )
        }

        val selected = when {
            selectedSourceId != null ->
                ranked.firstOrNull { it.sourceId == selectedSourceId } ?: ranked.first()
            ranked.size >= 2 && sourceAwareScore(ranked[0]) - sourceAwareScore(ranked[1]) < 1.2 ->
                null
            else -> ranked.first()
        }

        if (selected == null) {
            return ComponentResolution(
                component = component,
                selected = null,
                candidates = ranked,
                analysis = null,
                needsUserChoice = true,
                question = "Which match is \"${component.name}\"?",
            )
        }

        val grams = gramOverride
            ?: component.estimatedGrams
            ?: selected.servingSizeGrams
            ?: 100.0

        val analysis = when (selected.sourceKind) {
            NutrientSourceKind.USDA -> {
                val fdcId = selected.sourceId.toLongOrNull()
                val record = fdcId?.let { usdaIndex.getByFdcId(it) }
                if (record != null && record.calories == null) {
                    return ComponentResolution(
                        component = component,
                        selected = null,
                        candidates = ranked.filter { !it.incompleteEnergy },
                        analysis = null,
                        needsUserChoice = ranked.any { !it.incompleteEnergy },
                        question = "Which match is \"${component.name}\"?",
                    )
                }
                record?.toFoodAnalysis(grams, usdaIndex.version())
                    ?: analysisFromCandidate(selected, grams, component)
            }
            NutrientSourceKind.HISTORY -> {
                val hit = historyHits.firstOrNull { it.entry.favoriteKey == selected.sourceId }
                if (hit != null) {
                    historyToAnalysis(hit.entry, grams, component)
                } else {
                    analysisFromCandidate(selected, grams, component)
                }
            }
            else -> analysisFromCandidate(selected, grams, component)
        }

        return ComponentResolution(
            component = component,
            selected = selected,
            candidates = ranked,
            analysis = analysis,
            needsUserChoice = false,
        )
    }

    private fun sourceAwareScore(c: GroundingCandidate): Double {
        val base = c.score
        val bonus = when (c.sourceKind) {
            NutrientSourceKind.OPEN_FOOD_FACTS -> 50.0
            NutrientSourceKind.USDA -> 8.0
            NutrientSourceKind.HISTORY -> 4.0
            NutrientSourceKind.NUTRITION_LABEL -> 40.0
            NutrientSourceKind.MODEL_ESTIMATE -> 0.0
        }
        return base + bonus
    }

    private fun historyToAnalysis(
        entry: org.codeberg.fitguy.nofud.models.FoodEntry,
        grams: Double,
        component: RecognizedFoodComponent,
    ): FoodAnalysis {
        val baseGrams = entry.servingSizeGrams?.takeIf { it > 0 } ?: 100.0
        val scale = grams / baseGrams
        fun s(v: Double?) = v?.let { it * scale }
        return FoodAnalysis(
            name = entry.name,
            calories = (entry.calories * scale).roundToInt(),
            protein = entry.protein * scale,
            carbs = entry.carbs * scale,
            fat = entry.fat * scale,
            servingSizeGrams = grams,
            emoji = entry.emoji,
            sugar = s(entry.sugar),
            addedSugar = s(entry.addedSugar),
            fiber = s(entry.fiber),
            saturatedFat = s(entry.saturatedFat),
            monounsaturatedFat = s(entry.monounsaturatedFat),
            polyunsaturatedFat = s(entry.polyunsaturatedFat),
            cholesterol = s(entry.cholesterol),
            sodium = s(entry.sodium),
            potassium = s(entry.potassium),
            transFat = s(entry.transFat),
            calcium = s(entry.calcium),
            iron = s(entry.iron),
            magnesium = s(entry.magnesium),
            zinc = s(entry.zinc),
            vitaminA = s(entry.vitaminA),
            vitaminC = s(entry.vitaminC),
            vitaminD = s(entry.vitaminD),
            vitaminB12 = s(entry.vitaminB12),
            vitaminE = s(entry.vitaminE),
            vitaminK = s(entry.vitaminK),
            folate = s(entry.folate),
            omega3 = s(entry.omega3),
            servingUnitOptions = entry.servingUnitOptions,
            selectedServingUnit = entry.selectedServingUnit,
            selectedServingQuantity = entry.selectedServingQuantity,
            grounding = FoodGroundingProvenance(
                sourceKind = NutrientSourceKind.HISTORY,
                sourceId = entry.favoriteKey,
                sourceName = entry.name,
                nutrientBasis = NutrientBasis.ABSOLUTE,
                identityEvidence = "confirmed history",
                portionEvidence = component.portionHint
                    ?: "scaled from last logged ${baseGrams}g — not auto-copied",
                identityConfirmed = true,
                portionConfirmed = false,
            ),
        )
    }

    private fun analysisFromCandidate(
        selected: GroundingCandidate,
        grams: Double,
        component: RecognizedFoodComponent,
    ): FoodAnalysis {
        val scale = grams / 100.0
        return FoodAnalysis(
            name = selected.displayName,
            calories = ((selected.caloriesPer100g ?: 0.0) * scale).roundToInt(),
            protein = (selected.proteinPer100g ?: 0.0) * scale,
            carbs = (selected.carbsPer100g ?: 0.0) * scale,
            fat = (selected.fatPer100g ?: 0.0) * scale,
            servingSizeGrams = grams,
            grounding = FoodGroundingProvenance(
                sourceKind = selected.sourceKind,
                sourceId = selected.sourceId,
                sourceName = selected.displayName,
                nutrientBasis = NutrientBasis.PER_100G,
                datasetVersion = selected.datasetVersion,
                identityEvidence = selected.matchedBy,
                portionEvidence = component.portionHint,
            ),
        )
    }

    private suspend fun fallbackEstimate(
        component: RecognizedFoodComponent,
        gramOverride: Double?,
    ): FoodAnalysis {
        val desc = buildString {
            append(component.name)
            component.brand?.let { append(" ").append(it) }
            component.preparation?.let { append(", ").append(it) }
            val grams = gramOverride ?: component.estimatedGrams
            if (grams != null) append(", ").append(grams.roundToInt()).append("g")
            else component.portionHint?.let { append(", ").append(it) }
        }
        val estimated = if (estimateFallback != null) {
            estimateFallback.invoke(desc)
        } else {
            foodAnalysis.analyzeText(desc)
        }
        return estimated.copy(
            grounding = FoodGroundingProvenance(
                sourceKind = NutrientSourceKind.MODEL_ESTIMATE,
                sourceId = null,
                sourceName = component.name,
                nutrientBasis = NutrientBasis.ABSOLUTE,
                identityEvidence = "no database match — model estimate",
                portionEvidence = component.portionHint,
            ),
        )
    }

    private fun attachProvenance(
        analysis: FoodAnalysis,
        resolutions: List<ComponentResolution>,
        partial: Boolean,
    ): FoodAnalysis {
        val components = resolutions.mapNotNull { r ->
            val selected = r.selected ?: return@mapNotNull null
            GroundedComponentProvenance(
                name = r.component.name,
                grams = r.analysis?.servingSizeGrams
                    ?: r.component.estimatedGrams
                    ?: 0.0,
                sourceKind = selected.sourceKind,
                sourceId = selected.sourceId,
                sourceName = selected.displayName,
                matchedBy = selected.matchedBy,
            )
        }
        val dups = GroundingValidator.duplicateComponentNames(
            resolutions.map { it.component.name },
        )
        val validation = GroundingValidator.validateServing(
            analysisName = analysis.name,
            calories = analysis.calories,
            protein = analysis.protein,
            carbs = analysis.carbs,
            fat = analysis.fat,
            servingGrams = analysis.servingSizeGrams,
            sodiumMg = analysis.sodium,
        )
        val notes = buildList {
            addAll(validation.notes)
            if (dups.isNotEmpty()) add("Duplicate components: ${dups.joinToString()}")
            if (partial) add("Awaiting user candidate selection for ambiguous components.")
        }
        val primaryKind = components
            .groupingBy { it.sourceKind }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: NutrientSourceKind.MODEL_ESTIMATE

        val identityConf = when {
            components.any { it.sourceKind == NutrientSourceKind.OPEN_FOOD_FACTS } -> 0.95
            components.any { it.sourceKind == NutrientSourceKind.USDA } -> 0.85
            components.any { it.sourceKind == NutrientSourceKind.HISTORY } -> 0.75
            else -> 0.4
        }
        val portionConf = if (resolutions.all {
                it.component.estimatedGrams != null || it.analysis?.servingSizeGrams != null
            }
        ) 0.7 else 0.4
        val nutrientConf = when (primaryKind) {
            NutrientSourceKind.USDA, NutrientSourceKind.OPEN_FOOD_FACTS -> 0.9
            NutrientSourceKind.HISTORY, NutrientSourceKind.NUTRITION_LABEL -> 0.75
            NutrientSourceKind.MODEL_ESTIMATE -> 0.35
        }

        var result = analysis.copy(
            calories = validation.correctedCalories ?: analysis.calories,
            sodium = validation.correctedSodiumMg ?: analysis.sodium,
            grounding = FoodGroundingProvenance(
                sourceKind = primaryKind,
                sourceId = components.firstOrNull()?.sourceId,
                sourceName = analysis.name,
                nutrientBasis = NutrientBasis.ABSOLUTE,
                datasetVersion = usdaIndex.version(),
                retrievedAtEpochMs = System.currentTimeMillis(),
                components = components,
                validationNotes = notes,
            ),
            groundingConfidence = GroundingConfidence(
                identity = identityConf,
                portion = portionConf,
                nutrientSource = nutrientConf,
            ),
        )
        if (result.servingUnitOptions.isEmpty() && resolutions.size == 1) {
            val only = resolutions.first().analysis
            if (only != null && only.servingUnitOptions.isNotEmpty()) {
                result = result.copy(
                    servingUnitOptions = only.servingUnitOptions,
                    selectedServingUnit = only.selectedServingUnit,
                    selectedServingQuantity = only.selectedServingQuantity,
                )
            } else if (result.servingSizeGrams > 0) {
                result = result.copy(
                    servingUnitOptions = listOf(
                        ServingUnitOption(
                            unit = "serving",
                            gramsPerUnit = result.servingSizeGrams,
                            quantity = 1.0,
                        )
                    ),
                    selectedServingUnit = "serving",
                    selectedServingQuantity = 1.0,
                )
            }
        }
        return result
    }
}
