package app.chompass.services

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import app.chompass.AppContainer
import app.chompass.debug.OnDeviceLlmDebugConfig
import app.chompass.debug.OnDeviceLlmDefaults
import app.chompass.services.ai.AiImageBytes
import app.chompass.services.ai.CoachTools
import app.chompass.services.ai.FoodJsonParser
import app.chompass.services.ai.ON_DEVICE_LLM_TAG
import app.chompass.services.ai.OnDeviceLlmClient
import app.chompass.services.ai.plainText
import org.json.JSONObject

/**
 * Debug-only on-device LLM smoke test, triggered by the `run_ondevice_llm_test`
 * intent extra in MainActivity. See docs/ON_DEVICE_LLM.md for model delivery,
 * intent extras, and experiment matrix.
 */
class OnDeviceLlmSmokeTest(
    private val container: AppContainer,
    private val config: OnDeviceLlmDebugConfig,
) {

    constructor(
        container: AppContainer,
        backendName: String = "gpu",
        enableMtp: Boolean = false,
    ) : this(
        container,
        OnDeviceLlmDebugConfig(
            enabled = true,
            backendName = backendName,
            enableMtp = enableMtp,
        ),
    )

    private val backend: Backend = OnDeviceLlmClient.backendFromIntentValue(config.backendName)
    private val backendLabel: String = OnDeviceLlmClient.backendLabel(backend)
    private val tierMode: TierMode = TierMode.fromIntent(config.tier)
    private val promptMode: PromptMode = PromptMode.fromIntent(config.promptMode)
    private val repeatCount: Int = config.repeatCount.coerceIn(1, 5)
    private val enableVision: Boolean = tierMode.runB
    private var dailyTierAMs: Long = 0L
    private var dailyTierBMs: Long = 0L
    private var dailyTierCMs: Long = 0L
    private val useFunctionGemmaPrompt: Boolean =
        config.modelFilename.contains("functiongemma", ignoreCase = true) ||
            config.modelFilename.contains("mobile_actions", ignoreCase = true)

    suspend fun run() {
        val modelFile = File(container.appContext.filesDir, "models/${config.modelFilename}")
        if (!modelFile.exists()) {
            Log.e(
                ON_DEVICE_LLM_TAG,
                "op=ondevice_llm phase=start status=fail err=model_missing path=${modelFile.absolutePath}"
            )
            return
        }
        clearCacheIfRequested()
        val cacheDir = resolveCacheDir()
        Log.i(
            ON_DEVICE_LLM_TAG,
            "op=ondevice_llm phase=start backend=$backendLabel mtp=${config.enableMtp} tier=$tierMode " +
                "prompt=$promptMode repeat=$repeatCount vision=$enableVision clearCache=${config.clearCache} " +
                "functionGemmaPrompt=$useFunctionGemmaPrompt cacheDir=$cacheDir path=${modelFile.absolutePath}"
        )

        val client = OnDeviceLlmClient(
            modelPath = modelFile.absolutePath,
            cacheDir = cacheDir,
            backend = backend,
            enableMtp = config.enableMtp,
            enableVision = enableVision,
        )
        try {
            val loadMs = client.ensureLoaded()
            Log.i(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=modelLoad backend=$backendLabel ms=$loadMs")

            if (tierMode == TierMode.DAILY) {
                dailyTierAMs = 0L
                dailyTierBMs = 0L
                dailyTierCMs = 0L
                runTierA(client)
                runTierB(client)
                runTierC(client, TIER_C_DAILY_SCENARIOS)
                logDailySummary(loadMs, cacheDir)
            } else {
                if (tierMode.runA) runTierA(client)
                if (tierMode.runB) runTierB(client)
                if (tierMode.runC) runTierC(client)
            }

            Log.i(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=done backend=$backendLabel tier=$tierMode prompt=$promptMode")
        } catch (e: Throwable) {
            Log.e(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=fatal backend=$backendLabel err=${e.message}", e)
        } finally {
            client.close()
        }
    }

    /** MTP uses a separate compile-cache dir to avoid drafter/main-model cache collisions. */
    private fun resolveCacheDir(): String {
        val base = container.appContext.cacheDir
        if (!config.enableMtp) return base.absolutePath
        return File(base, "litert-mtp").apply { mkdirs() }.absolutePath
    }

    private fun clearCacheIfRequested() {
        if (!config.clearCache) return
        val dirs = listOf(
            container.appContext.cacheDir,
            File(container.appContext.cacheDir, "litert-mtp"),
        )
        for (dir in dirs) {
            if (dir.exists()) {
                dir.deleteRecursively()
                Log.i(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=cacheClear path=${dir.absolutePath}")
            }
        }
    }

    private fun logDailySummary(engineInitMs: Long, cacheDir: String) {
        val totalMs = dailyTierAMs + dailyTierBMs + dailyTierCMs
        Log.i(
            ON_DEVICE_LLM_TAG,
            "op=ondevice_llm phase=daily_summary tierA_ms=$dailyTierAMs tierB_ms=$dailyTierBMs " +
                "tierC_ms=$dailyTierCMs total_ms=$totalMs mtp=${config.enableMtp} cacheDir=$cacheDir " +
                "engineInit_ms=$engineInitMs prompt=fewshot_units backend=$backendLabel"
        )
    }

    private suspend fun runTierA(client: OnDeviceLlmClient) {
        repeat(repeatCount) { pass ->
            for ((i, description) in SAMPLES.withIndex()) {
                Log.i(
                    ON_DEVICE_LLM_TAG,
                    "op=ondevice_llm phase=tierA_begin backend=$backendLabel pass=$pass i=$i prompt=$promptMode"
                )
                val start = System.nanoTime()
                try {
                    val prompt = tierAPrompt(description)
                    Log.i(
                        ON_DEVICE_LLM_TAG,
                        "op=ondevice_llm phase=tierA_prompt backend=$backendLabel pass=$pass i=$i " +
                            "promptChars=${prompt.length}"
                    )
                    val raw = withInferenceHeartbeat(phase = "tierA", detail = "pass=$pass i=$i") {
                        withTimeout(INFERENCE_TIMEOUT_MS) {
                            client.generate(systemPrompt = "", userPrompt = prompt)
                        }
                    }
                    val pass1Ms = (System.nanoTime() - start) / 1_000_000
                    val analysis = runCatching { FoodJsonParser.parseFood(raw) }.getOrNull()
                    var unitOptions = analysis?.servingUnitOptions?.size ?: 0
                    var totalMs = pass1Ms

                    if (promptMode == PromptMode.TWOPASS && analysis != null) {
                        val twoPassStart = System.nanoTime()
                        val unitsRaw = withInferenceHeartbeat(phase = "tierA_units", detail = "pass=$pass i=$i") {
                            withTimeout(INFERENCE_TIMEOUT_MS) {
                                client.generate(
                                    systemPrompt = "",
                                    userPrompt = inferServingUnitPrompt(
                                        name = analysis.name,
                                        servingSizeGrams = analysis.servingSizeGrams,
                                        description = description,
                                    ),
                                )
                            }
                        }
                        val twoPassMs = (System.nanoTime() - twoPassStart) / 1_000_000
                        totalMs += twoPassMs
                        val inferred = runCatching {
                            FoodJsonParser.parseServingUnitOptions(unitsRaw, analysis.servingSizeGrams)
                        }.getOrNull()
                        if (inferred != null && inferred.isNotEmpty()) {
                            unitOptions = inferred.size
                        }
                        Log.i(
                            ON_DEVICE_LLM_TAG,
                            "op=ondevice_llm phase=tierA_twopass backend=$backendLabel pass=$pass i=$i ms=$twoPassMs " +
                                "unitOptions=$unitOptions"
                        )
                    }

                    val status = if (analysis != null) "ok" else "parseFail"
                    Log.i(
                        ON_DEVICE_LLM_TAG,
                        "op=ondevice_llm phase=tierA backend=$backendLabel pass=$pass i=$i ms=$totalMs pass1Ms=$pass1Ms " +
                            "status=$status name=${analysis?.name} calories=${analysis?.calories} " +
                            "unitOptions=$unitOptions prompt=$promptMode"
                    )
                    if (tierMode == TierMode.DAILY) dailyTierAMs += totalMs
                } catch (e: Throwable) {
                    val ms = (System.nanoTime() - start) / 1_000_000
                    Log.e(
                        ON_DEVICE_LLM_TAG,
                        "op=ondevice_llm phase=tierA backend=$backendLabel pass=$pass i=$i ms=$ms " +
                            "status=fail err=${e.message}"
                    )
                }
            }
        }
    }

    private fun tierAPrompt(description: String): String = when {
        tierMode == TierMode.DAILY -> tierAFewshotUnitsPrompt(description)
        else -> when (promptMode) {
            PromptMode.COMPACT -> tierACompactPrompt(description)
            PromptMode.FEWSHOT_UNITS -> tierAFewshotUnitsPrompt(description)
            PromptMode.TWOPASS, PromptMode.FULL -> tierAFullPrompt(description)
        }
    }

    private fun tierAFullPrompt(description: String): String = """
        Estimate the nutritional content for: $description
        Parse any quantities, brands, and multiple items from the text. If a brand is mentioned, use that brand's known nutritional data. If multiple items are described, sum up the total nutrition.
        Respond ONLY with JSON:
        {"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,"serving_size_grams":0.0,"emoji":"<single specific food emoji>","sugar":0.0,"added_sugar":0.0,"fiber":0.0,"saturated_fat":0.0,"monounsaturated_fat":0.0,"polyunsaturated_fat":0.0,"cholesterol":0.0,"sodium":0.0,"potassium":0.0,"trans_fat":0.0,"calcium":0.0,"iron":0.0,"magnesium":0.0,"zinc":0.0,"vitamin_a":0.0,"vitamin_c":0.0,"vitamin_d":0.0,"vitamin_b12":0.0,"vitamin_e":0.0,"vitamin_k":0.0,"folate":0.0,"omega_3":0.0,"unit_options":[]}
        Calories are integers. Protein/carbs/fat are decimal gram values when needed. serving_size_grams is the estimated total weight in grams. Nutrients are numbers: sugar/fiber/sat fat/mono fat/poly fat/trans fat/omega-3 in grams; cholesterol/sodium/potassium/calcium/iron/magnesium/zinc/vitamin C/vitamin E in milligrams; vitamin A/vitamin D/vitamin B12/vitamin K/folate in micrograms.
        The [] in unit_options above is only a JSON shape placeholder; replace it with options when a non-gram unit is obvious.
        unit_options is required when the text names an obvious non-gram serving unit, and optional otherwise. Use slice/piece for pizza, cake, bread, cookies, fruit pieces, etc.; use ml/cup/fl oz for drinks, milk, soup, smoothies, sauces, etc.; use tbsp/tsp for spooned foods; use can/packet when packaged. Its quantity must describe the whole analyzed amount, not always 1. Do not copy any sample number; use the quantity stated or clearly implied by the meal. Use [] only when no non-gram unit is apparent. Do not include g/grams in unit_options.
        For "emoji" pick the single most specific food emoji that depicts this dish — e.g. 🥚 for eggs, 🍕 for pizza, 🍎 for an apple, 🥗 for a salad, 🍔 for a burger, 🍜 for ramen, 🍰 for cake, 🥑 for avocado, ☕ for coffee, 🍣 for sushi. Only fall back to 🍽️ when the food truly cannot be represented by any specific emoji. Use null for any nutrient you cannot estimate.
    """.trimIndent()

    private fun tierACompactPrompt(description: String): String = """
        Estimate the nutritional content for: $description
        Parse quantities and multiple items from the text. Sum total nutrition when multiple items are described.
        Respond ONLY with JSON:
        {"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,"serving_size_grams":0.0,"emoji":"<single specific food emoji>","unit_options":[]}
        Calories are integers. Macros are decimal grams. serving_size_grams is total estimated weight in grams.
        unit_options: use slice/piece for pizza etc., ml/can for drinks when obvious; [] only when no non-gram unit applies.
        Pick the most specific food emoji; fall back to 🍽️ only when necessary.
    """.trimIndent()

    private fun tierAFewshotUnitsPrompt(description: String): String = """
        Estimate the nutritional content for: $description
        Parse any quantities, brands, and multiple items from the text. If multiple items are described, sum up the total nutrition.
        Respond ONLY with JSON:
        {"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,"serving_size_grams":0.0,"emoji":"<single specific food emoji>","sugar":0.0,"added_sugar":0.0,"fiber":0.0,"saturated_fat":0.0,"monounsaturated_fat":0.0,"polyunsaturated_fat":0.0,"cholesterol":0.0,"sodium":0.0,"potassium":0.0,"trans_fat":0.0,"calcium":0.0,"iron":0.0,"magnesium":0.0,"zinc":0.0,"vitamin_a":0.0,"vitamin_c":0.0,"vitamin_d":0.0,"vitamin_b12":0.0,"vitamin_e":0.0,"vitamin_k":0.0,"folate":0.0,"omega_3":0.0,"unit_options":[]}

        unit_options examples (replace numbers with your estimates):
        Pizza: {"unit_options":[{"unit":"slice","quantity":2.0,"grams_per_unit":120.0}]}
        Soda can: {"unit_options":[{"unit":"can","quantity":1.0,"grams_per_unit":355.0},{"unit":"ml","quantity":355.0,"grams_per_unit":1.03}]}
        Oatmeal bowl: {"unit_options":[{"unit":"bowl","quantity":1.0,"grams_per_unit":250.0}]}

        unit_options is required when the text names an obvious non-gram serving unit. Use slice/piece for pizza, cake, bread; ml/cup/can for drinks; tbsp/tsp for spooned foods. Quantity must match the analyzed amount. Use [] only when no non-gram unit is apparent. Do not include g/grams in unit_options.
        For "emoji" pick the single most specific food emoji. Use null for nutrients you cannot estimate.
    """.trimIndent()

    private fun inferServingUnitPrompt(name: String, servingSizeGrams: Double, description: String): String = """
        The previous food analysis returned grams only. Infer non-gram serving unit options for the same food and amount.

        Food: $name
        Total grams for the analyzed amount: ${String.format(Locale.US, "%.1f", servingSizeGrams)}
        User context: $description

        Return ONLY JSON:
        {"unit_options":[{"unit":"slice","quantity":8.0,"grams_per_unit":45.0}]}

        Rules:
        - Replace the sample numbers with the actual best estimate.
        - For pizza, cake, pie, bread, cookies, fruit pieces, use slice or piece.
        - For liquids (soda, milk, soup), use ml or can when packaged.
        - grams_per_unit is grams for one unit. For countable units, use total grams / visible quantity.
        - Return [] only if no non-gram unit is apparent.

        Good outputs:
        {"unit_options":[{"unit":"slice","quantity":2.0,"grams_per_unit":120.0}]}
        {"unit_options":[{"unit":"can","quantity":1.0,"grams_per_unit":355.0},{"unit":"ml","quantity":355.0,"grams_per_unit":1.03}]}
    """.trimIndent()

    private suspend fun runTierB(client: OnDeviceLlmClient) {
        for (fixture in TIER_B_FIXTURES) {
            Log.i(
                ON_DEVICE_LLM_TAG,
                "op=ondevice_llm phase=tierB_begin backend=$backendLabel fixture=${fixture.name}"
            )
            val start = System.nanoTime()
            try {
                val rawBytes = loadTierBAsset(fixture.assetFile)
                val uploadBytes = AiImageBytes.jpegForUpload(rawBytes)
                val prompt = when (fixture.promptKind) {
                    TierBPromptKind.ANALYZE_FOOD -> tierBAnalyzeFoodPrompt()
                    TierBPromptKind.ANALYZE_AUTO -> tierBAnalyzeAutoPrompt()
                }
                Log.i(
                    ON_DEVICE_LLM_TAG,
                    "op=ondevice_llm phase=tierB_prompt backend=$backendLabel fixture=${fixture.name} " +
                        "promptChars=${prompt.length} rawBytes=${rawBytes.size} uploadBytes=${uploadBytes.size}"
                )
                val raw = withInferenceHeartbeat(phase = "tierB", detail = "fixture=${fixture.name}") {
                    withTimeout(INFERENCE_TIMEOUT_MS) {
                        client.generateWithImage(userPrompt = prompt, imageBytes = uploadBytes)
                    }
                }
                val ms = (System.nanoTime() - start) / 1_000_000
                val analysis = runCatching { FoodJsonParser.parseFood(raw) }.getOrNull()
                val unitOptions = analysis?.servingUnitOptions?.size ?: 0
                val jsonOk = analysis != null
                Log.i(
                    ON_DEVICE_LLM_TAG,
                    "op=ondevice_llm phase=tierB backend=$backendLabel fixture=${fixture.name} ms=$ms " +
                        "jsonOk=$jsonOk name=${analysis?.name} calories=${analysis?.calories} " +
                        "unitOptions=$unitOptions rawBytes=${rawBytes.size} uploadBytes=${uploadBytes.size}"
                )
                if (tierMode == TierMode.DAILY) dailyTierBMs += ms
            } catch (e: Throwable) {
                val ms = (System.nanoTime() - start) / 1_000_000
                Log.e(
                    ON_DEVICE_LLM_TAG,
                    "op=ondevice_llm phase=tierB backend=$backendLabel fixture=${fixture.name} ms=$ms " +
                        "status=fail err=${e.message}"
                )
            }
        }

        runTierBMultiTurnCheck(client)
    }

    private suspend fun runTierBMultiTurnCheck(client: OnDeviceLlmClient) {
        Log.i(
            ON_DEVICE_LLM_TAG,
            "op=ondevice_llm phase=tierB_multiturn_begin backend=$backendLabel"
        )
        val start = System.nanoTime()
        try {
            val plateBytes = AiImageBytes.jpegForUpload(loadTierBAsset("food_plate.jpg"))
            val pizzaBytes = AiImageBytes.jpegForUpload(loadTierBAsset("pizza_slices.jpg"))
            val prompt = tierBAnalyzeFoodPrompt()
            val responses = withInferenceHeartbeat(phase = "tierB_multiturn", detail = "turns=2") {
                withTimeout(INFERENCE_TIMEOUT_MS) {
                    client.generateMultiTurnWithImages(
                        turns = listOf(
                            plateBytes to prompt,
                            pizzaBytes to prompt,
                        ),
                    )
                }
            }
            val ms = (System.nanoTime() - start) / 1_000_000
            Log.i(
                ON_DEVICE_LLM_TAG,
                "op=ondevice_llm phase=tierB_multiturn backend=$backendLabel ms=$ms turns=${responses.size} " +
                    "status=ok response0=${responses.getOrNull(0)?.take(120)?.replace('\n', ' ')}"
            )
        } catch (e: Throwable) {
            val ms = (System.nanoTime() - start) / 1_000_000
            Log.e(
                ON_DEVICE_LLM_TAG,
                "op=ondevice_llm phase=tierB_multiturn backend=$backendLabel ms=$ms status=fail err=${e.message}"
            )
        }
    }

    private fun loadTierBAsset(fileName: String): ByteArray =
        container.appContext.assets.open("$TIER_B_ASSET_DIR/$fileName").use { it.readBytes() }

    private fun tierBAnalyzeFoodPrompt(): String = """
        Analyze this food image. Identify the food and estimate its nutritional content.
        Respond ONLY with JSON:
        {"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,"serving_size_grams":0.0,"sugar":0.0,"added_sugar":0.0,"fiber":0.0,"saturated_fat":0.0,"monounsaturated_fat":0.0,"polyunsaturated_fat":0.0,"cholesterol":0.0,"sodium":0.0,"potassium":0.0,"trans_fat":0.0,"calcium":0.0,"iron":0.0,"magnesium":0.0,"zinc":0.0,"vitamin_a":0.0,"vitamin_c":0.0,"vitamin_d":0.0,"vitamin_b12":0.0,"vitamin_e":0.0,"vitamin_k":0.0,"folate":0.0,"omega_3":0.0,"unit_options":[]}
        Calories are integers. Protein/carbs/fat are decimal gram values when needed. serving_size_grams is the estimated weight in grams of the serving shown. Nutrients are numbers: sugar/fiber/sat fat/mono fat/poly fat/trans fat/omega-3 in grams; cholesterol/sodium/potassium/calcium/iron/magnesium/zinc/vitamin C/vitamin E in milligrams; vitamin A/vitamin D/vitamin B12/vitamin K/folate in micrograms.
        The [] in unit_options above is only a JSON shape placeholder; replace it with options when a non-gram unit is obvious.
        unit_options is required for obvious non-gram units visible in the food — almost every solid or liquid food has one; treat [] as a last resort only for loose, uncountable food (e.g. plain scrambled eggs, mixed stir-fry) where no natural unit exists. Use slice/piece for pizza, cake, bread, cookies, fruit pieces, etc.; use ml/cup/fl oz for drinks, milk, soup, smoothies, sauces, etc.; use tbsp/tsp for spooned foods; use can/packet when packaged. Its quantity must describe the whole analyzed amount, not always 1. For a whole or mostly-whole divisible food like cake, pie, or pizza, count the visible pieces/slices and derive grams_per_unit from serving_size_grams / quantity. If N slices are visible, return quantity N. Use quantity 1 only when a single piece/slice is actually the analyzed portion. If you are uncertain, still give your single best-guess unit and quantity rather than returning []; a plausible guess is always more useful than none. Example: a plate showing 2 visible pizza slices with serving_size_grams 360 should return "unit_options":[{"unit":"slice","quantity":2,"grams_per_unit":180}]. Do not include g/grams in unit_options.
        Give your best estimate for the visible food amount shown in the image. For whole/mostly-whole cakes, pizzas, pies, loaves, or similar foods, estimate the total visible item/remaining item weight rather than defaulting to one slice. Use null for any nutrient you cannot estimate.
    """.trimIndent()

    private fun tierBAnalyzeAutoPrompt(): String = """
        Analyze this image. It could be either a photo of food OR a nutrition facts label.

        If it's a food photo: identify the food and estimate nutritional content for the serving shown.
        If it's a nutrition label: read the values and calculate for one serving size as listed on the label.

        Respond ONLY with JSON:
        {"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,"serving_size_grams":0.0,"sugar":0.0,"added_sugar":0.0,"fiber":0.0,"saturated_fat":0.0,"monounsaturated_fat":0.0,"polyunsaturated_fat":0.0,"cholesterol":0.0,"sodium":0.0,"potassium":0.0,"trans_fat":0.0,"calcium":0.0,"iron":0.0,"magnesium":0.0,"zinc":0.0,"vitamin_a":0.0,"vitamin_c":0.0,"vitamin_d":0.0,"vitamin_b12":0.0,"vitamin_e":0.0,"vitamin_k":0.0,"folate":0.0,"omega_3":0.0,"unit_options":[]}
        Calories are integers. Protein/carbs/fat are decimal gram values when needed. serving_size_grams is the estimated weight in grams of the serving. Nutrients are numbers: sugar/fiber/sat fat/mono fat/poly fat/trans fat/omega-3 in grams; cholesterol/sodium/potassium/calcium/iron/magnesium/zinc/vitamin C/vitamin E in milligrams; vitamin A/vitamin D/vitamin B12/vitamin K/folate in micrograms.
        The [] in unit_options above is only a JSON shape placeholder; replace it with options when a non-gram unit is obvious.
        unit_options is required for obvious non-gram units visible in the image or label — almost every solid or liquid food has one; treat [] as a last resort only for loose, uncountable food (e.g. plain scrambled eggs, mixed stir-fry) where no natural unit exists. Use slice/piece for pizza, cake, bread, cookies, fruit pieces, etc.; use ml/cup/fl oz for drinks, milk, soup, smoothies, sauces, etc.; use tbsp/tsp for spooned foods; use can/packet when packaged. Its quantity must describe the whole analyzed amount, not always 1. For a whole or mostly-whole divisible food like cake, pie, or pizza, count the visible pieces/slices and derive grams_per_unit from serving_size_grams / quantity. If N slices are visible, return quantity N. Use quantity 1 only when a single piece/slice is actually the analyzed portion. If you are uncertain, still give your single best-guess unit and quantity rather than returning []; a plausible guess is always more useful than none. Example: a plate showing 2 visible pizza slices with serving_size_grams 360 should return "unit_options":[{"unit":"slice","quantity":2,"grams_per_unit":180}]. Do not include g/grams in unit_options.
        Use null for any nutrient you cannot estimate.
    """.trimIndent()

    private suspend fun runTierC(
        client: OnDeviceLlmClient,
        scenarios: List<TierCScenario> = TIER_C_SCENARIOS,
    ) {
        val weights = container.weightRepository.entries.first()
        val bodyFats = container.bodyFatRepository.entries.first()
        val foods = container.foodRepository.entries.first()
        val tools = CoachTools(weights, bodyFats, foods, container.foodAnalysis)
        val systemPrompt = if (useFunctionGemmaPrompt) {
            tierCFunctionGemmaSystemPrompt()
        } else {
            tierCSystemPrompt(weights.size, bodyFats.size, foods.size)
        }

        for (scenario in scenarios) {
            val corrupting = scenario.corruptTool != null
            val toolSet = CoachToolsToolSet(tools, corruptToolName = scenario.corruptTool)
            Log.i(
                ON_DEVICE_LLM_TAG,
                "op=ondevice_llm phase=tierC_begin backend=$backendLabel scenario=${scenario.name} " +
                    "corrupting=$corrupting functionGemmaPrompt=$useFunctionGemmaPrompt"
            )
            val start = System.nanoTime()
            try {
                withInferenceHeartbeat(phase = "tierC", detail = "scenario=${scenario.name}") {
                    withTimeout(INFERENCE_TIMEOUT_MS) {
                        client.createToolConversation(systemPrompt, toolSet).use { conversation ->
                            val response = conversation.sendMessage(scenario.message)
                            val ms = (System.nanoTime() - start) / 1_000_000
                            Log.i(
                                ON_DEVICE_LLM_TAG,
                                "op=ondevice_llm phase=tierC backend=$backendLabel scenario=${scenario.name} ms=$ms " +
                                    "corrupting=$corrupting response=${response.plainText().take(400).replace('\n', ' ')}"
                            )
                            if (tierMode == TierMode.DAILY) dailyTierCMs += ms
                        }
                    }
                }
            } catch (e: Throwable) {
                val ms = (System.nanoTime() - start) / 1_000_000
                Log.e(
                    ON_DEVICE_LLM_TAG,
                    "op=ondevice_llm phase=tierC backend=$backendLabel scenario=${scenario.name} ms=$ms " +
                        "status=fail err=${e.message}"
                )
            }
        }
    }

    private fun tierCSystemPrompt(weightCount: Int, bodyFatCount: Int, foodCount: Int): String = """
        You are Coach, an AI nutrition and weight-change assistant inside a calorie tracking app. Answer in plain English, be specific and factual, and ground your answers in the user's own logged data. Be concise — 2-5 sentences per response unless asked for detail.

        ## Current date
        - Today: ${LocalDate.now()}
        - Treat "today" as the date above when choosing tool date ranges, e.g. "last week" means the 7 days ending yesterday, "yesterday" is today minus 1 day.

        ## How to use the data tools
        You have access to functions that fetch the user's history on demand. Call a tool when the user asks about specific past dates, time ranges, individual meals, or trends that need raw data — resolve relative dates like "yesterday" or "last week" yourself from today's date above; do not ask the user to restate them as absolute dates.

        ## Using tool results
        After a tool call returns, its JSON result is real logged data — you must read it and use it directly in your final answer (e.g. list the actual foods returned, compute the actual average requested). Never tell the user data is unavailable or was "not returned" after a tool call has already succeeded — use what it gave you. If a tool result looks broken or truncated, say so explicitly and ask the user to retry, rather than inventing a placeholder answer.

        ## Logging on the user's behalf
        If the user asks you to log/add/track food, weight, or water, call the matching propose_log_* tool. These tools NEVER save anything themselves — they only prepare a confirmation the user must approve in the app. After calling one, briefly tell the user what you're proposing to log.

        ## Data available
        - $weightCount weight entries, $bodyFatCount body-fat readings, $foodCount food entries logged total. Use get_data_summary to see exact date ranges.
    """.trimIndent()

    private fun tierCFunctionGemmaSystemPrompt(): String = """
        You are a function-calling assistant for a calorie tracking app. Today is ${LocalDate.now()}.

        Call the appropriate function when the user asks about logged food, weight, body fat, calorie totals, or wants to propose logging food/weight/water.
        Resolve relative dates yourself: "yesterday" = today minus 1 day; "last week" = the 7 days ending yesterday.
        After receiving a function result, use the returned JSON data directly in your answer. Never claim data was unavailable when a function succeeded.
        propose_log_* functions only prepare a confirmation — tell the user what you are proposing to log.
        Answer in plain English, 2-5 sentences unless more detail is requested.
    """.trimIndent()

    private class CoachToolsToolSet(
        private val tools: CoachTools,
        private val corruptToolName: String? = null
    ) : ToolSet {

        private fun call(name: String, args: JSONObject): JsonElement {
            val start = System.nanoTime()
            var result = runBlocking { tools.execute(name, args) }
            if (name == corruptToolName && result.length > 20) {
                result = result.substring(0, result.length / 2)
            }
            val ms = (System.nanoTime() - start) / 1_000_000
            Log.i(
                ON_DEVICE_LLM_TAG,
                "op=ondevice_llm phase=toolCall tool=$name args=$args ms=$ms result=${result.take(200)}"
            )
            return runCatching { JsonParser.parseString(result) }.getOrElse {
                Log.w(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=toolCallParseFail tool=$name err=${it.message}")
                JsonPrimitive(result)
            }
        }

        @Tool(description = "Get a quick summary of the user's available data: total counts and earliest/latest dates for weights, body-fat readings, and food entries.")
        fun get_data_summary(): JsonElement = call("get_data_summary", JSONObject())

        @Tool(description = "Fetch weight entries between two dates (inclusive, yyyy-MM-dd). Returns date + weight (kg + lbs).")
        fun get_weight_history(
            @ToolParam(description = "Start date yyyy-MM-dd, optional") from: String = "",
            @ToolParam(description = "End date yyyy-MM-dd, optional") to: String = "",
            @ToolParam(description = "Max entries to return, optional") limit: Int = 0
        ): JsonElement = call("get_weight_history", rangeArgs(from, to, limit))

        @Tool(description = "Fetch body-fat readings between two dates (inclusive, yyyy-MM-dd). Returns date + percent.")
        fun get_body_fat_history(
            @ToolParam(description = "Start date yyyy-MM-dd, optional") from: String = "",
            @ToolParam(description = "End date yyyy-MM-dd, optional") to: String = "",
            @ToolParam(description = "Max entries to return, optional") limit: Int = 0
        ): JsonElement = call("get_body_fat_history", rangeArgs(from, to, limit))

        @Tool(description = "Daily calorie totals (sum of logged foods per day) between two dates (inclusive, yyyy-MM-dd).")
        fun get_calorie_totals(
            @ToolParam(description = "Start date yyyy-MM-dd, optional") from: String = "",
            @ToolParam(description = "End date yyyy-MM-dd, optional") to: String = ""
        ): JsonElement = call("get_calorie_totals", rangeArgs(from, to, 0))

        @Tool(description = "Individual logged food items (name + calories + macros) between two dates (inclusive, yyyy-MM-dd).")
        fun get_food_entries(
            @ToolParam(description = "Start date yyyy-MM-dd, optional") from: String = "",
            @ToolParam(description = "End date yyyy-MM-dd, optional") to: String = "",
            @ToolParam(description = "Max entries to return, optional") limit: Int = 0
        ): JsonElement = call("get_food_entries", rangeArgs(from, to, limit))

        @Tool(description = "Propose logging a food entry from a natural-language description. Does NOT save it — requires user confirmation in-app.")
        fun propose_log_food(
            @ToolParam(description = "Free-text food description, e.g. '2 eggs and toast'") description: String,
            @ToolParam(description = "One of breakfast/lunch/dinner/snack, optional") meal_type: String = ""
        ): JsonElement = call(
            "propose_log_food",
            JSONObject().apply {
                put("description", description)
                if (meal_type.isNotBlank()) put("meal_type", meal_type)
            }
        )

        @Tool(description = "Propose logging a body weight entry in kilograms. Does NOT save it — requires user confirmation in-app.")
        fun propose_log_weight(
            @ToolParam(description = "Weight in kilograms") weight_kg: Double
        ): JsonElement = call("propose_log_weight", JSONObject().apply { put("weight_kg", weight_kg) })

        @Tool(description = "Propose logging a water intake entry in milliliters. Does NOT save it — requires user confirmation in-app.")
        fun propose_log_water(
            @ToolParam(description = "Water volume in milliliters") milliliters: Int
        ): JsonElement = call("propose_log_water", JSONObject().apply { put("milliliters", milliliters) })

        private fun rangeArgs(from: String, to: String, limit: Int): JSONObject = JSONObject().apply {
            if (from.isNotBlank()) put("from", from)
            if (to.isNotBlank()) put("to", to)
            if (limit > 0) put("limit", limit)
        }
    }

    private data class TierCScenario(val name: String, val message: String, val corruptTool: String? = null)

    private enum class TierBPromptKind { ANALYZE_FOOD, ANALYZE_AUTO }

    private data class TierBFixture(
        val name: String,
        val assetFile: String,
        val promptKind: TierBPromptKind,
    )

    private enum class TierMode(val runA: Boolean, val runB: Boolean, val runC: Boolean) {
        ALL(runA = true, runB = true, runC = true),
        A(runA = true, runB = false, runC = false),
        B(runA = false, runB = true, runC = false),
        C(runA = false, runB = false, runC = true),
        DAILY(runA = true, runB = true, runC = true),
        ;

        companion object {
            fun fromIntent(value: String): TierMode = when (value.lowercase()) {
                "a" -> A
                "b" -> B
                "c" -> C
                "daily" -> DAILY
                else -> ALL
            }
        }
    }

    private enum class PromptMode {
        FULL,
        COMPACT,
        FEWSHOT_UNITS,
        TWOPASS,
        ;

        companion object {
            fun fromIntent(value: String): PromptMode = when (value.lowercase()) {
                "compact" -> COMPACT
                "fewshot_units" -> FEWSHOT_UNITS
                "twopass" -> TWOPASS
                else -> FULL
            }
        }
    }

    private suspend fun <T> withInferenceHeartbeat(
        phase: String,
        detail: String,
        block: suspend () -> T,
    ): T = coroutineScope {
        val start = System.nanoTime()
        val heartbeat = launch {
            var tick = 0
            while (isActive) {
                delay(15_000)
                tick++
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                Log.i(
                    ON_DEVICE_LLM_TAG,
                    "op=ondevice_llm phase=${phase}_waiting backend=$backendLabel $detail tick=$tick elapsedMs=$elapsedMs"
                )
            }
        }
        try {
            block()
        } finally {
            heartbeat.cancel()
        }
    }

    companion object {
        private const val INFERENCE_TIMEOUT_MS = 300_000L
        private const val TIER_B_ASSET_DIR = "ondevice_llm"

        const val MODEL_FILENAME = OnDeviceLlmDefaults.DEFAULT_MODEL_FILENAME

        private val SAMPLES = listOf(
            "2 slices of pepperoni pizza and a can of coke",
            "a bowl of oatmeal with banana and honey",
            "grilled chicken breast with rice and broccoli"
        )

        private val TIER_B_FIXTURES = listOf(
            TierBFixture("food_plate", "food_plate.jpg", TierBPromptKind.ANALYZE_FOOD),
            TierBFixture("fast_food_combo", "fast_food_combo.jpg", TierBPromptKind.ANALYZE_FOOD),
            TierBFixture("nutrition_label", "nutrition_label.jpg", TierBPromptKind.ANALYZE_AUTO),
            TierBFixture("pizza_slices", "pizza_slices.jpg", TierBPromptKind.ANALYZE_FOOD),
        )

        private val TIER_C_DAILY_SCENARIOS = listOf(
            TierCScenario("single_tool", "What did I eat yesterday?"),
            TierCScenario("ambiguous", "How am I doing?"),
        )

        private val TIER_C_SCENARIOS = listOf(
            TierCScenario("single_tool", "What did I eat yesterday?"),
            TierCScenario("ambiguous", "How am I doing?"),
            TierCScenario(
                "multi_round_chain",
                "How many calories did I average last week, and log that I drank 500ml of water."
            ),
            TierCScenario(
                "malformed_recovery",
                "Give me my data summary, then tell me what my weight history looks like for the last 30 days.",
                corruptTool = "get_weight_history"
            ),
            TierCScenario(
                "six_round_chain",
                "Give me a data summary, my weight history for the last 30 days, my calorie totals for last week, " +
                    "what I ate yesterday, then propose logging 2000ml of water and 75kg weight."
            ),
        )
    }
}
