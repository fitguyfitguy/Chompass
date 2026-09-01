package app.chompass.data

import app.chompass.models.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Single source of truth for [UserProfile]. Thin wrapper over [PreferencesStore]
 * that exposes reactive reads and suspend writes.
 */
class ProfileRepository(private val prefs: PreferencesStore) {
    val profile: Flow<UserProfile?> = prefs.userProfile

    suspend fun save(profile: UserProfile) = prefs.setUserProfile(profile)

    /**
     * Complete-onboarding save (Codeberg #60). Onboarding collects only the body-stats
     * and goal inputs; everything else on a stored profile — macro day types, macro
     * locks, protein g/kg mode, name, BMR toggle — has no onboarding UI and must survive
     * a re-run instead of being wiped by the fresh default-built profile.
     */
    suspend fun saveFromOnboarding(profile: UserProfile) {
        save(current()?.withOnboardingInputs(profile) ?: profile)
    }

    /** Current snapshot. Suspends until DataStore emits the first value. */
    suspend fun current(): UserProfile? = prefs.userProfile.first()
}

/**
 * Overlay the fields onboarding owns from [onboarded] onto this stored profile.
 * Written as a copy of the stored profile so any future [UserProfile] field is
 * preserved by default; a new onboarding input forgotten here fails visibly during
 * onboarding instead of silently wiping user data.
 */
internal fun UserProfile.withOnboardingInputs(onboarded: UserProfile): UserProfile = copy(
    gender = onboarded.gender,
    birthday = onboarded.birthday,
    heightCm = onboarded.heightCm,
    weightKg = onboarded.weightKg,
    activityLevel = onboarded.activityLevel,
    goal = onboarded.goal,
    dietMode = onboarded.dietMode,
    ketoCarbMode = onboarded.ketoCarbMode,
    ketoCarbManualTarget = onboarded.ketoCarbManualTarget,
    bodyFatPercentage = onboarded.bodyFatPercentage,
    goalBodyFatPercentage = onboarded.goalBodyFatPercentage,
    weeklyChangeKg = onboarded.weeklyChangeKg,
    goalWeightKg = onboarded.goalWeightKg,
    customCalories = onboarded.customCalories,
    customProtein = onboarded.customProtein,
    customFat = onboarded.customFat,
    customCarbs = onboarded.customCarbs,
)
