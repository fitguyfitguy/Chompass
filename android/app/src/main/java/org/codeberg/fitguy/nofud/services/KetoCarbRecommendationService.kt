package org.codeberg.fitguy.nofud.services

import org.codeberg.fitguy.nofud.models.ActivityLevel
import org.codeberg.fitguy.nofud.models.UserProfile
import org.codeberg.fitguy.nofud.models.WeightGoal

/**
 * Deterministic keto net-carb recommendation based on user profile signals.
 * Returns grams/day clamped into a conservative ketogenic range.
 */
object KetoCarbRecommendationService {
    const val MIN_NET_CARBS_G = 20
    const val MAX_NET_CARBS_G = 50

    fun recommendNetCarbs(profile: UserProfile): Int {
        var target = when (profile.goal) {
            WeightGoal.LOSE -> 25
            WeightGoal.MAINTAIN -> 30
            WeightGoal.GAIN -> 40
        }

        target += when (profile.activityLevel) {
            ActivityLevel.SEDENTARY -> -2
            ActivityLevel.LIGHT -> 0
            ActivityLevel.MODERATE -> 2
            ActivityLevel.ACTIVE -> 4
            ActivityLevel.VERY_ACTIVE -> 6
            ActivityLevel.EXTRA_ACTIVE -> 8
        }

        if (profile.goal == WeightGoal.LOSE && (profile.weeklyChangeKg ?: 0.0) >= 0.75) {
            target -= 5
        }

        // Higher body-fat profiles can tolerate stricter starts a bit better.
        if ((profile.bodyFatPercentage ?: 0.0) >= 0.30) {
            target -= 3
        }

        return target.coerceIn(MIN_NET_CARBS_G, MAX_NET_CARBS_G)
    }

    fun clampManualNetCarbs(value: Int): Int = value.coerceIn(MIN_NET_CARBS_G, MAX_NET_CARBS_G)
}
