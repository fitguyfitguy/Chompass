package app.chompass.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Provenance of the stored calorie/macro targets. AUTO sources ([FORMULA],
 * [AI], [ADAPTIVE]) may be silently re-clamped into the CAL-SAFE envelope
 * when weight or body-fat syncs move BMR/TDEE ([UserProfile.reclampedAutoGoal]);
 * [MANUAL] goals are the user's own numbers and [UNKNOWN] (legacy payloads)
 * stays untouched — both are never auto-adjusted.
 */
@Serializable
enum class GoalSource {
    @SerialName("unknown") UNKNOWN,
    @SerialName("formula") FORMULA,
    @SerialName("ai") AI,
    @SerialName("adaptive") ADAPTIVE,
    @SerialName("manual") MANUAL,
}
