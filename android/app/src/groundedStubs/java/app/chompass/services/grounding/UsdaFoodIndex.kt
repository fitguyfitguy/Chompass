package app.chompass.services.grounding

import android.content.Context

/** Release stub — USDA SQLite ships in debug assets only. */
class UsdaFoodIndex(
    context: Context,
    assetPath: String = ASSET_PATH,
) {
    init {
        @Suppress("UNUSED_VARIABLE")
        val unused = context to assetPath
    }

    fun version(): String = "unavailable"
    fun foodCount(): Int = 0

    companion object {
        const val ASSET_PATH = "usda/usda_foods.sqlite"
        const val MANIFEST_ASSET_PATH = "usda/usda_foods.manifest.json"
        const val AMBIGUITY_SCORE_DELTA = 1.5

        fun assetAvailable(context: Context, assetPath: String = ASSET_PATH): Boolean = false
    }
}
