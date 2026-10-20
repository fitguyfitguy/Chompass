package app.chompass.models

import androidx.annotation.StringRes
import app.chompass.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How the user's body-fat readings are measured. Drives three trust knobs:
 *  - [accuracyPoints]: validity — how far a single reading can sit from the
 *    truth; stated to the AI prompt so the model weighs the number correctly;
 *  - [repeatabilityPoints]: day-to-day noise of the same device on the same
 *    body — scales the profile-sync spike guard (a jump beyond it waits for a
 *    confirming reading). Validity and repeatability differ: a cheap BIA
 *    scale can be self-consistent yet biased, a DEXA scan is both tight;
 *  - [recencyDays]: how long a reading stays fresh for Katch-McArdle — a
 *    clinical DEXA scan outlives a daily smart-scale estimate.
 *
 * The user can override the accuracy number
 * ([UserProfile.bodyFatAccuracyPoints]); the override then also widens the
 * guard, since someone asserting a wider band asserts both.
 */
@Serializable
enum class BodyFatMethod(
    val accuracyPoints: Double,
    val repeatabilityPoints: Double,
    val recencyDays: Long,
) {
    @SerialName("dexa") DEXA(1.5, 1.0, 365),
    @SerialName("bia8") BIA_EIGHT(3.2, 2.0, 90),
    @SerialName("bia4") BIA_FOUR(5.0, 3.0, 90),
    @SerialName("tape") TAPE(3.5, 2.5, 90);

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
