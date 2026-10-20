package app.chompass.models

import androidx.annotation.StringRes
import app.chompass.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How the user's body-fat readings are measured. Drives two trust knobs:
 *  - [accuracyPoints]: how many percentage points a single reading can be off —
 *    scales the profile-sync spike guard (a jump beyond it waits for a
 *    confirming reading) and the AI prompt's reliability note;
 *  - [recencyDays]: how long a reading stays fresh for Katch-McArdle — a
 *    clinical DEXA scan outlives a daily smart-scale estimate.
 *
 * A one-off scan (DEXA) is not repeatable noise; daily BIA is. The user can
 * override the accuracy number ([UserProfile.bodyFatAccuracyPoints]).
 */
@Serializable
enum class BodyFatMethod(
    val accuracyPoints: Double,
    val recencyDays: Long,
) {
    @SerialName("dexa") DEXA(1.5, 365),
    @SerialName("bia8") BIA_EIGHT(3.2, 90),
    @SerialName("bia4") BIA_FOUR(5.0, 90),
    @SerialName("tape") TAPE(3.5, 90);

    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            DEXA -> R.string.body_fat_method_dexa
            BIA_EIGHT -> R.string.body_fat_method_bia8
            BIA_FOUR -> R.string.body_fat_method_bia4
            TAPE -> R.string.body_fat_method_tape
        }

    /** English prompt wording (prompts are not localized). */
    val promptLabel: String
        get() = when (this) {
            DEXA -> "DEXA scan"
            BIA_EIGHT -> "8-electrode BIA scale"
            BIA_FOUR -> "4-electrode BIA scale"
            TAPE -> "tape measurements"
        }
}
