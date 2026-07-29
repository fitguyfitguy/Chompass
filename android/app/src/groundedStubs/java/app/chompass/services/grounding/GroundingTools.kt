package app.chompass.services.grounding

import app.chompass.data.PreferencesStore
import app.chompass.models.FoodEntry
import app.chompass.services.OpenFoodFactsService
import app.chompass.services.ai.FoodAnalysis
import org.json.JSONObject

/** Release stub for the grounded tool kit. */
class GroundingTools(
    usdaIndex: UsdaFoodIndex?,
    historyPool: List<FoodEntry>,
    prefs: PreferencesStore?,
    barcodeLookup: (suspend (String) -> FoodAnalysis)? = null,
    offSearch: (suspend (String, String?, Int) -> List<OpenFoodFactsService.SearchHit>)? = null,
    onToolUsed: (String) -> Unit = {},
) {
    init {
        @Suppress("UNUSED_EXPRESSION")
        usdaIndex to historyPool to prefs to barcodeLookup to offSearch to onToolUsed
    }

    var lastFinalize: FinalizePayload? = null
        private set
    var searchUsdaCount: Int = 0
        private set
    var searchHistoryCount: Int = 0
        private set
    var searchOffCount: Int = 0
        private set
    var barcodeLookupCount: Int = 0
        private set
    val seenSourceIds: Set<String> get() = emptySet()

    data class FinalizeComponent(
        val name: String,
        val brand: String? = null,
        val preparation: String? = null,
        val sourceId: String? = null,
        val sourceKind: String? = null,
        val grams: Double? = null,
        val portionHint: String? = null,
        val barcode: String? = null,
        val quantity: Double? = null,
        val unit: String? = null,
        val rejectToEstimate: Boolean = false,
        val needsUserChoice: Boolean = false,
    )

    data class FinalizePayload(
        val mealName: String,
        val emoji: String? = null,
        val notes: String? = null,
        val components: List<FinalizeComponent>,
    )

    suspend fun execute(name: String, args: JSONObject): String {
        throw UnsupportedOperationException("GroundingTools unavailable in release")
    }

    companion object {
        val TOOL_NAMES: List<String> = emptyList()
        val TOOL_DESCRIPTIONS: Map<String, String> = emptyMap()
        fun parametersSchema(toolName: String): JSONObject =
            JSONObject().put("type", "object").put("properties", JSONObject())
    }
}
