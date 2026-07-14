package org.codeberg.fitguy.nofud.services

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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.services.ai.CoachTools
import org.codeberg.fitguy.nofud.services.ai.FoodJsonParser
import org.codeberg.fitguy.nofud.services.ai.ON_DEVICE_LLM_TAG
import org.codeberg.fitguy.nofud.services.ai.OnDeviceLlmClient
import org.codeberg.fitguy.nofud.services.ai.plainText
import org.json.JSONObject

/**
 * Debug-only on-device LLM smoke test, triggered by the `run_ondevice_llm_test`
 * intent extra in MainActivity (mirrors [EntryPerfBenchmark]'s shape). Runs the
 * same Tier A (`analyzeText`) and Tier C (Coach tool-calling) prompts used in
 * the earlier WSL2/Ollama doability tests against a local LiteRT-LM model, to
 * validate real on-device latency/quality on the Pixel 9a / GrapheneOS target.
 *
 * Model delivery is manual (`adb push` into app-private storage) — see
 * MODEL_FILENAME below and docs/ON_DEVICE_LLM.md for the exact push sequence.
 * Results go to logcat under tag [ON_DEVICE_LLM_TAG] as `op=ondevice_llm phase=... key=value` lines.
 *
 * Backend selection: defaults to GPU. Override with intent extra
 * `--es ondevice_llm_backend cpu` for A/B comparison.
 */
class OnDeviceLlmSmokeTest(
    private val container: AppContainer,
    backendName: String = "gpu",
    private val enableMtp: Boolean = false,
) {

    private val backend: Backend = OnDeviceLlmClient.backendFromIntentValue(backendName)
    private val backendLabel: String = OnDeviceLlmClient.backendLabel(backend)

    suspend fun run() {
        val modelFile = File(container.appContext.filesDir, "models/$MODEL_FILENAME")
        if (!modelFile.exists()) {
            Log.e(
                ON_DEVICE_LLM_TAG,
                "op=ondevice_llm phase=start status=fail err=model_missing path=${modelFile.absolutePath}"
            )
            return
        }
        val cacheDir = container.appContext.cacheDir.absolutePath
        Log.i(
            ON_DEVICE_LLM_TAG,
            "op=ondevice_llm phase=start backend=$backendLabel mtp=$enableMtp cacheDir=$cacheDir path=${modelFile.absolutePath}"
        )

        val client = OnDeviceLlmClient(
            modelPath = modelFile.absolutePath,
            cacheDir = cacheDir,
            backend = backend,
            enableMtp = enableMtp,
        )
        try {
            val loadMs = client.ensureLoaded()
            Log.i(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=modelLoad backend=$backendLabel ms=$loadMs")

            runTierA(client)
            runTierC(client)

            Log.i(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=done backend=$backendLabel")
        } catch (e: Throwable) {
            Log.e(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=fatal backend=$backendLabel err=${e.message}", e)
        } finally {
            client.close()
        }
    }

    // MARK: - Tier A: free-text food description -> structured nutrition JSON

    private suspend fun runTierA(client: OnDeviceLlmClient) {
        for ((i, description) in SAMPLES.withIndex()) {
            Log.i(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=tierA_begin backend=$backendLabel i=$i")
            val start = System.nanoTime()
            try {
                val raw = withInferenceHeartbeat(phase = "tierA", detail = "i=$i") {
                    withTimeout(INFERENCE_TIMEOUT_MS) {
                        client.generate(systemPrompt = "", userPrompt = tierAPrompt(description))
                    }
                }
                val ms = (System.nanoTime() - start) / 1_000_000
                val analysis = runCatching { FoodJsonParser.parseFood(raw) }.getOrNull()
                val status = if (analysis != null) "ok" else "parseFail"
                Log.i(
                    ON_DEVICE_LLM_TAG,
                    "op=ondevice_llm phase=tierA backend=$backendLabel i=$i ms=$ms status=$status " +
                        "name=${analysis?.name} calories=${analysis?.calories} unitOptions=${analysis?.servingUnitOptions?.size}"
                )
            } catch (e: Throwable) {
                val ms = (System.nanoTime() - start) / 1_000_000
                Log.e(
                    ON_DEVICE_LLM_TAG,
                    "op=ondevice_llm phase=tierA backend=$backendLabel i=$i ms=$ms status=fail err=${e.message}"
                )
            }
        }
    }

    private fun tierAPrompt(description: String): String = """
        Estimate the nutritional content for: $description
        Parse any quantities, brands, and multiple items from the text. If a brand is mentioned, use that brand's known nutritional data. If multiple items are described, sum up the total nutrition.
        Respond ONLY with JSON:
        {"name":"...","calories":0,"protein":0.0,"carbs":0.0,"fat":0.0,"serving_size_grams":0.0,"emoji":"<single specific food emoji>","sugar":0.0,"added_sugar":0.0,"fiber":0.0,"saturated_fat":0.0,"monounsaturated_fat":0.0,"polyunsaturated_fat":0.0,"cholesterol":0.0,"sodium":0.0,"potassium":0.0,"trans_fat":0.0,"calcium":0.0,"iron":0.0,"magnesium":0.0,"zinc":0.0,"vitamin_a":0.0,"vitamin_c":0.0,"vitamin_d":0.0,"vitamin_b12":0.0,"vitamin_e":0.0,"vitamin_k":0.0,"folate":0.0,"omega_3":0.0,"unit_options":[]}
        Calories are integers. Protein/carbs/fat are decimal gram values when needed. serving_size_grams is the estimated total weight in grams. Nutrients are numbers: sugar/fiber/sat fat/mono fat/poly fat/trans fat/omega-3 in grams; cholesterol/sodium/potassium/calcium/iron/magnesium/zinc/vitamin C/vitamin E in milligrams; vitamin A/vitamin D/vitamin B12/vitamin K/folate in micrograms.
        The [] in unit_options above is only a JSON shape placeholder; replace it with options when a non-gram unit is obvious.
        unit_options is required when the text names an obvious non-gram serving unit, and optional otherwise. Use slice/piece for pizza, cake, bread, cookies, fruit pieces, etc.; use ml/cup/fl oz for drinks, milk, soup, smoothies, sauces, etc.; use tbsp/tsp for spooned foods; use can/packet when packaged. Its quantity must describe the whole analyzed amount, not always 1. Do not copy any sample number; use the quantity stated or clearly implied by the meal. Use [] only when no non-gram unit is apparent. Do not include g/grams in unit_options.
        For "emoji" pick the single most specific food emoji that depicts this dish — e.g. 🥚 for eggs, 🍕 for pizza, 🍎 for an apple, 🥗 for a salad, 🍔 for a burger, 🍜 for ramen, 🍰 for cake, 🥑 for avocado, ☕ for coffee, 🍣 for sushi. Only fall back to 🍽️ when the food truly cannot be represented by any specific emoji. Use null for any nutrient you cannot estimate.
    """.trimIndent()

    // MARK: - Tier C: Coach tool-calling loop, against real CoachTools + real logged data

    private suspend fun runTierC(client: OnDeviceLlmClient) {
        val weights = container.weightRepository.entries.first()
        val bodyFats = container.bodyFatRepository.entries.first()
        val foods = container.foodRepository.entries.first()
        val tools = CoachTools(weights, bodyFats, foods, container.foodAnalysis)
        val systemPrompt = tierCSystemPrompt(weights.size, bodyFats.size, foods.size)

        for (scenario in TIER_C_SCENARIOS) {
            val corrupting = scenario.corruptTool != null
            val toolSet = CoachToolsToolSet(tools, corruptToolName = scenario.corruptTool)
            Log.i(
                ON_DEVICE_LLM_TAG,
                "op=ondevice_llm phase=tierC_begin backend=$backendLabel scenario=${scenario.name} corrupting=$corrupting"
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

    /**
     * Mirrors the structure (date framing, when-to-call-tools guidance, propose_log_*
     * guidance) of the real [org.codeberg.fitguy.nofud.services.ai.ChatService]'s
     * `buildSystemPrompt`, trimmed of profile/formula/forecast sections this harness
     * has no equivalent for. Adds one explicit instruction beyond the production
     * prompt — "always weave the tool's JSON result into your final answer" — as an
     * experiment: the production prompt has no such line (cloud models apparently
     * infer it), but the first on-device run showed the model calling tools
     * correctly and then ignoring their results in 3/4 scenarios.
     */
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

    /**
     * Wraps the real [CoachTools] as LiteRT-LM `@Tool` functions, one per name in
     * [CoachTools.TOOL_NAMES], so the engine drives the exact same tool set the cloud
     * Coach uses. Each call is logged for round-by-round visibility. When
     * [corruptToolName] matches, that tool's JSON result is truncated before being
     * returned to the model, to probe malformed-tool-result recovery (scenario
     * `malformed_recovery`).
     *
     * Return type is [JsonElement], NOT [String] — LiteRT-LM's `ToolManager.execute`
     * double-encodes raw JSON strings; returning [JsonElement] preserves structure.
     */
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

    /** Emits a log line every 15s while a blocking LiteRT call runs — decode can take minutes on cold GPU. */
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
        /** Per-call inference budget; Tier A/C on GPU should finish well under this. */
        private const val INFERENCE_TIMEOUT_MS = 300_000L

        /** Expected on-device path: `filesDir/models/$MODEL_FILENAME`, landed via `adb push`. */
        const val MODEL_FILENAME = "gemma-e2b-int4.litertlm"

        private val SAMPLES = listOf(
            "2 slices of pepperoni pizza and a can of coke",
            "a bowl of oatmeal with banana and honey",
            "grilled chicken breast with rice and broccoli"
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
            )
        )
    }
}
