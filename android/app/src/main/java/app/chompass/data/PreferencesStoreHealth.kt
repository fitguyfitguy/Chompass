package app.chompass.data

import androidx.datastore.preferences.core.edit
import app.chompass.models.AutoBalanceMacro
import app.chompass.models.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
private data class HealthEnergyGoalTargetSnapshot(
    val customCalories: Int? = null,
    val customProtein: Int? = null,
    val customFat: Int? = null,
    val customCarbs: Int? = null,
    val autoBalanceMacro: AutoBalanceMacro? = null
)

// -- Health Connect ---------------------------------------------------
internal val PreferencesStore.healthConnectEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.HEALTH_CONNECT_ENABLED] ?: false }
internal suspend fun PreferencesStore.setHealthConnectEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.HEALTH_CONNECT_ENABLED] = v } }

internal val PreferencesStore.healthPermissionsVersionImpl: Flow<Int> get() = dataStore.data.map { it[Keys.HEALTH_TYPES_VERSION] ?: 0 }
internal suspend fun PreferencesStore.setHealthPermissionsVersionImpl(v: Int) { dataStore.edit { it[Keys.HEALTH_TYPES_VERSION] = v } }

    /// Opaque Health Connect changes token for incremental weight/body-fat read-sync.
    /// Null means "no sync yet" → the coordinator does a one-time historical backfill.
internal val PreferencesStore.healthChangesTokenImpl: Flow<String?> get() = dataStore.data.map { it[Keys.HEALTH_CHANGES_TOKEN] }
internal suspend fun PreferencesStore.setHealthChangesTokenImpl(v: String) { dataStore.edit { it[Keys.HEALTH_CHANGES_TOKEN] = v } }
internal suspend fun PreferencesStore.clearHealthChangesTokenImpl() {
        dataStore.edit { it.remove(Keys.HEALTH_CHANGES_TOKEN); it.remove(Keys.HEALTH_CHANGES_TOKEN_TYPES) }
    }

    /// Which read types the current changes token was seeded for (e.g. {"weight","bodyfat"}).
    /// If a newly-granted read type isn't covered, the coordinator drops the token and
    /// re-backfills so the new metric's history is imported.
internal val PreferencesStore.healthChangesTokenTypesImpl: Flow<Set<String>> get() = dataStore.data.map {
        it[Keys.HEALTH_CHANGES_TOKEN_TYPES]?.split(",")?.filter { s -> s.isNotBlank() }?.toSet() ?: emptySet()
    }
internal suspend fun PreferencesStore.setHealthChangesTokenTypesImpl(types: Set<String>) {
        dataStore.edit { it[Keys.HEALTH_CHANGES_TOKEN_TYPES] = types.joinToString(",") }
    }

    /// One-shot flag for the food-log restore from Health Connect. Cleared with the
    /// rest of the store on Delete All Data / fresh install, which is exactly when
    /// the restore should be allowed to run again.
internal val PreferencesStore.healthFoodRestoreDoneImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.HEALTH_FOOD_RESTORE_DONE] ?: false }
internal suspend fun PreferencesStore.setHealthFoodRestoreDoneImpl(v: Boolean) { dataStore.edit { it[Keys.HEALTH_FOOD_RESTORE_DONE] = v } }

internal val PreferencesStore.healthEnergyGoalsEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.HEALTH_ENERGY_GOALS_ENABLED] ?: false }
internal suspend fun PreferencesStore.setHealthEnergyGoalsEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.HEALTH_ENERGY_GOALS_ENABLED] = v } }

    /** Opt-in periodic background Health Connect sync. Default OFF — see HealthSyncWorker. */
internal val PreferencesStore.healthBackgroundSyncEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.HEALTH_BACKGROUND_SYNC_ENABLED] ?: false }
internal suspend fun PreferencesStore.setHealthBackgroundSyncEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.HEALTH_BACKGROUND_SYNC_ENABLED] = v } }

internal val PreferencesStore.healthEnergyGoalsLastAutoRefreshDayImpl: Flow<String?> get() = dataStore.data.map {
        it[Keys.HEALTH_ENERGY_GOALS_LAST_AUTO_REFRESH_DAY]
    }
internal suspend fun PreferencesStore.setHealthEnergyGoalsLastAutoRefreshDayImpl(v: String) {
        dataStore.edit { it[Keys.HEALTH_ENERGY_GOALS_LAST_AUTO_REFRESH_DAY] = v }
    }

internal val PreferencesStore.reviewPromptedAfterFirstLogImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.REVIEW_PROMPTED_AFTER_FIRST_LOG] ?: false }
internal suspend fun PreferencesStore.setReviewPromptedAfterFirstLogImpl(v: Boolean) { dataStore.edit { it[Keys.REVIEW_PROMPTED_AFTER_FIRST_LOG] = v } }

internal val PreferencesStore.adaptiveGoalsEnabledImpl: Flow<Boolean> get() = dataStore.data.map { it[Keys.ADAPTIVE_GOALS_ENABLED] ?: false }
internal suspend fun PreferencesStore.setAdaptiveGoalsEnabledImpl(v: Boolean) { dataStore.edit { it[Keys.ADAPTIVE_GOALS_ENABLED] = v } }

internal val PreferencesStore.adaptiveGoalsLastCheckDayImpl: Flow<String?> get() = dataStore.data.map {
        it[Keys.ADAPTIVE_GOALS_LAST_CHECK_DAY]
    }
internal suspend fun PreferencesStore.setAdaptiveGoalsLastCheckDayImpl(v: String) {
        dataStore.edit { it[Keys.ADAPTIVE_GOALS_LAST_CHECK_DAY] = v }
    }

internal suspend fun PreferencesStore.saveAdaptiveGoalPreviousTargetsIfNeededImpl(profile: UserProfile) {
        dataStore.edit { prefs ->
            if (prefs[Keys.ADAPTIVE_GOALS_PREVIOUS_TARGETS] != null) return@edit
            val snapshot = HealthEnergyGoalTargetSnapshot(
                customCalories = profile.customCalories,
                customProtein = profile.customProtein,
                customFat = profile.customFat,
                customCarbs = profile.customCarbs,
                autoBalanceMacro = profile.autoBalanceMacro
            )
            prefs[Keys.ADAPTIVE_GOALS_PREVIOUS_TARGETS] =
                json.encodeToString(HealthEnergyGoalTargetSnapshot.serializer(), snapshot)
        }
    }

internal suspend fun PreferencesStore.restoreAdaptiveGoalPreviousTargetsImpl(profile: UserProfile): UserProfile {
        val snapshot = dataStore.data.first()[Keys.ADAPTIVE_GOALS_PREVIOUS_TARGETS]
            ?.let { runCatching { json.decodeFromString<HealthEnergyGoalTargetSnapshot>(it) }.getOrNull() }
            ?: return profile
        return profile.copy(
            customCalories = snapshot.customCalories,
            customProtein = snapshot.customProtein,
            customFat = snapshot.customFat,
            customCarbs = snapshot.customCarbs,
            autoBalanceMacro = snapshot.autoBalanceMacro
        )
    }

internal suspend fun PreferencesStore.clearAdaptiveGoalPreviousTargetsImpl() {
        dataStore.edit { it.remove(Keys.ADAPTIVE_GOALS_PREVIOUS_TARGETS) }
    }

internal suspend fun PreferencesStore.saveHealthEnergyGoalPreviousTargetsIfNeededImpl(profile: UserProfile) {
        dataStore.edit { prefs ->
            if (prefs[Keys.HEALTH_ENERGY_GOALS_PREVIOUS_TARGETS] != null) return@edit
            val snapshot = HealthEnergyGoalTargetSnapshot(
                customCalories = profile.customCalories,
                customProtein = profile.customProtein,
                customFat = profile.customFat,
                customCarbs = profile.customCarbs,
                autoBalanceMacro = profile.autoBalanceMacro
            )
            prefs[Keys.HEALTH_ENERGY_GOALS_PREVIOUS_TARGETS] =
                json.encodeToString(HealthEnergyGoalTargetSnapshot.serializer(), snapshot)
        }
    }

internal suspend fun PreferencesStore.restoreHealthEnergyGoalPreviousTargetsImpl(profile: UserProfile): UserProfile {
        val snapshot = dataStore.data.first()[Keys.HEALTH_ENERGY_GOALS_PREVIOUS_TARGETS]
            ?.let { runCatching { json.decodeFromString<HealthEnergyGoalTargetSnapshot>(it) }.getOrNull() }
        return if (snapshot == null) {
            profile.copy(
                customCalories = null,
                customProtein = null,
                customFat = null,
                customCarbs = null,
                autoBalanceMacro = null
            )
        } else {
            profile.copy(
                customCalories = snapshot.customCalories,
                customProtein = snapshot.customProtein,
                customFat = snapshot.customFat,
                customCarbs = snapshot.customCarbs,
                autoBalanceMacro = snapshot.autoBalanceMacro
            )
        }
    }

internal suspend fun PreferencesStore.clearHealthEnergyGoalPreviousTargetsImpl() {
        dataStore.edit { it.remove(Keys.HEALTH_ENERGY_GOALS_PREVIOUS_TARGETS) }
    }

    
