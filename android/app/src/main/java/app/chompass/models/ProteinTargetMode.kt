package app.chompass.models

import app.chompass.R
import kotlinx.serialization.Serializable

/** How the user pins protein: absolute g/day, or g/kg of total weight / lean mass. */
@Serializable
enum class ProteinTargetMode(val displayNameRes: Int) {
    GRAMS_PER_DAY(R.string.protein_target_mode_grams),
    G_PER_KG_TOTAL(R.string.protein_target_mode_g_per_kg_total),
    G_PER_KG_LBM(R.string.protein_target_mode_g_per_kg_lbm);

    val usesRate: Boolean get() = this != GRAMS_PER_DAY

    companion object {
        val Default = GRAMS_PER_DAY
    }
}
