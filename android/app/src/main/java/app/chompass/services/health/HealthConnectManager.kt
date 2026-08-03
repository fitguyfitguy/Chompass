package app.chompass.services.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import app.chompass.R
import app.chompass.models.BodyFatEntry
import app.chompass.models.FoodEntry
import app.chompass.models.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Single boundary for Health Connect I/O. Port of iOS HealthKitManager.
 *
 * Conventions:
 * - Each sample carries [Metadata.clientRecordId] = "fudai_<uuid>" so we can
 *   dedup in-app vs external writes and delete our own records cleanly.
 * - Nutrition records include macros plus every optional nutrient Health Connect
 *   can represent from Fud AI's food model.
 * - The "typesVersion" integer bumps when we add new record types so existing
 *   users get a re-authorization prompt.
 *
 * Availability: on API ≤33 Jetpack talks to the Play Store HC APK; on API 34+
 * it uses the platform `HEALTHCONNECT_SERVICE` (no Play package check). Feature
 * flags ([HealthConnectFeatures]) gate background/history and newer types.
 */
class HealthConnectManager(private val context: Context) {
    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    private val writer by lazy { HealthConnectWriter(client = { client }) }
    private val reader by lazy {
        HealthConnectReader(
            client = { client },
            granted = { granted() },
            stepsRead = stepsRead,
            exerciseRead = exerciseRead,
            sleepRead = sleepRead,
            restingHrRead = restingHrRead,
            hydrationRead = hydrationRead,
            hasEnergyRead = { hasEnergyRead() },
        )
    }

    fun sdkStatus(): HealthConnectSdkStatus =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectSdkStatus.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectSdkStatus.UpdateRequired
            else -> HealthConnectSdkStatus.Unavailable
        }

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectSdkStatus.Available

    /** True when the HC module supports [HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND]. */
    fun isBackgroundReadAvailable(): Boolean =
        isFeatureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)

    /** True when the HC module supports reading beyond the default ~30-day window. */
    fun isHistoryReadAvailable(): Boolean =
        isFeatureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)

    private fun isFeatureAvailable(feature: Int): Boolean {
        if (!isAvailable()) return false
        val c = client ?: return false
        return runCatching {
            c.features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
    }

    // Individual permission strings so each direction can be gated independently.
    // The old all-or-nothing gate meant a user who granted only READ (e.g. to pull
    // weigh-ins from a Withings scale) got no sync at all — see issue #91.
    private val weightRead = HealthPermission.getReadPermission(WeightRecord::class)
    private val weightWrite = HealthPermission.getWritePermission(WeightRecord::class)
    private val bodyFatRead = HealthPermission.getReadPermission(BodyFatRecord::class)
    private val bodyFatWrite = HealthPermission.getWritePermission(BodyFatRecord::class)
    private val nutritionRead = HealthPermission.getReadPermission(NutritionRecord::class)
    private val nutritionWrite = HealthPermission.getWritePermission(NutritionRecord::class)
    private val activeEnergyRead = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val totalEnergyRead = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    private val stepsRead = HealthPermission.getReadPermission(StepsRecord::class)
    private val exerciseRead = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val heightWrite = HealthPermission.getWritePermission(HeightRecord::class)
    private val sleepRead = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private val restingHrRead = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    private val hydrationRead = HealthPermission.getReadPermission(HydrationRecord::class)

    private val corePermissions: Set<String> = setOf(
        weightRead, weightWrite, nutritionRead, nutritionWrite,
        bodyFatRead, bodyFatWrite, activeEnergyRead, totalEnergyRead,
        stepsRead, exerciseRead, heightWrite, sleepRead, restingHrRead, hydrationRead
    )

    /**
     * Permissions requested when connecting Health Connect. Includes history read
     * when the module supports it so restore/import can see data older than ~30 days.
     * Background read is requested separately when enabling [HealthSyncWorker].
     */
    val permissions: Set<String> by lazy {
        if (isHistoryReadAvailable()) {
            corePermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        } else {
            corePermissions
        }
    }

    val backgroundReadPermission: String =
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    /** Message for a non-[HealthConnectSdkStatus.Available] status (never "install from Play" on API 34+). */
    @StringRes
    fun unavailableMessageRes(): Int {
        val framework = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        return when (sdkStatus()) {
            HealthConnectSdkStatus.Available -> R.string.settings_health_denied
            HealthConnectSdkStatus.UpdateRequired ->
                if (framework) R.string.settings_health_update_required
                else R.string.settings_health_update_required_apk
            HealthConnectSdkStatus.Unavailable ->
                if (framework) R.string.settings_health_unavailable_platform
                else R.string.settings_health_unavailable_install
        }
    }

    /**
     * Optional secondary action when HC is not ready: Play Store on API ≤33,
     * Health Connect Settings on API 34+ (soft-path; may still fail on ROMs without a binder).
     */
    fun availabilityActionIntent(): Intent? =
        when (sdkStatus()) {
            HealthConnectSdkStatus.Available -> null
            HealthConnectSdkStatus.UpdateRequired,
            HealthConnectSdkStatus.Unavailable ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    healthConnectSettingsIntent()
                } else {
                    playStoreProviderIntent()
                }
        }

    @StringRes
    fun availabilityActionLabelRes(): Int? =
        when (sdkStatus()) {
            HealthConnectSdkStatus.Available -> null
            HealthConnectSdkStatus.UpdateRequired,
            HealthConnectSdkStatus.Unavailable ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    R.string.settings_health_open_settings
                } else {
                    R.string.settings_health_open_play_store
                }
        }

    private fun healthConnectSettingsIntent(): Intent =
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun playStoreProviderIntent(): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$HC_PROVIDER_PACKAGE")
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private suspend fun granted(): Set<String> =
        runCatching { client?.permissionController?.getGrantedPermissions() }.getOrNull() ?: emptySet()

    /** The "connected" state: at least one Fud AI permission granted. Partial grants
     *  are valid — a read-only user still syncs the read direction. */
    suspend fun hasAnyPermission(): Boolean = granted().any { it in permissions }

    suspend fun hasWeightRead(): Boolean = weightRead in granted()
    suspend fun hasWeightWrite(): Boolean = weightWrite in granted()
    suspend fun hasBodyFatRead(): Boolean = bodyFatRead in granted()
    suspend fun hasBodyFatWrite(): Boolean = bodyFatWrite in granted()
    suspend fun hasNutritionRead(): Boolean = nutritionRead in granted()
    suspend fun hasNutritionWrite(): Boolean = nutritionWrite in granted()
    suspend fun hasEnergyRead(): Boolean = granted().let { activeEnergyRead in it && totalEnergyRead in it }
    suspend fun hasActivityRead(): Boolean = granted().let { stepsRead in it || exerciseRead in it }
    suspend fun hasHeightWrite(): Boolean = heightWrite in granted()
    suspend fun hasWellnessRead(): Boolean =
        granted().let { sleepRead in it || restingHrRead in it || hydrationRead in it }
    suspend fun hasBackgroundRead(): Boolean = backgroundReadPermission in granted()
    suspend fun hasHistoryRead(): Boolean =
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted()

    /** One permission read snapshotting every capability — used by the read-sync coordinator. */
    suspend fun capabilities(): HealthCapabilities {
        val g = granted()
        return HealthCapabilities(
            weightRead = weightRead in g,
            weightWrite = weightWrite in g,
            bodyFatRead = bodyFatRead in g,
            bodyFatWrite = bodyFatWrite in g,
            nutritionRead = nutritionRead in g,
            nutritionWrite = nutritionWrite in g,
            energyRead = activeEnergyRead in g && totalEnergyRead in g,
            stepsRead = stepsRead in g,
            exerciseRead = exerciseRead in g,
            heightWrite = heightWrite in g,
            sleepRead = sleepRead in g,
            restingHrRead = restingHrRead in g,
            hydrationRead = hydrationRead in g
        )
    }

    /** True for records Fud AI itself wrote, so read-sync can tell them apart from
     *  external sources (change-token consumers skip them; the restore path keeps them). */
    fun isOwnRecord(clientRecordId: String?): Boolean =
        clientRecordId?.startsWith(CLIENT_PREFIX) == true

    /** The original in-app entry UUID embedded in one of our own clientRecordIds
     *  ("fudai_<uuid>"), or null for external/malformed tags. Restoring with the
     *  original id keeps future edits/deletes targeting the matching HC record. */
    fun ownRecordId(clientRecordId: String?): UUID? {
        if (clientRecordId == null || !clientRecordId.startsWith(CLIENT_PREFIX)) return null
        return runCatching { UUID.fromString(clientRecordId.removePrefix(CLIENT_PREFIX)) }.getOrNull()
    }

    /** Used to build the permission-request ActivityResultContract on the UI side. */
    fun permissionRequestContract() = PermissionController.createRequestPermissionResultContract()

    /**
     * Opens Health Connect's permission UI. Android 14+ supports an app-specific destination;
     * older devices use the standalone Health Connect app's settings screen.
     */
    fun manageAccessIntent(): Intent {
        val appSpecific = Intent(ACTION_MANAGE_HEALTH_PERMISSIONS)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        val generic = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appSpecific
        } else {
            generic
        }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun writeWeight(entry: WeightEntry): Boolean = writer.writeWeight(entry)

    suspend fun deleteWeight(entryId: UUID): Boolean = writer.deleteWeight(entryId)

    suspend fun readWeights(from: Instant, to: Instant): List<ExternalWeight> =
        reader.readWeights(from, to)

    suspend fun writeBodyFat(entry: BodyFatEntry): Boolean = writer.writeBodyFat(entry)

    suspend fun deleteBodyFat(entryId: UUID): Boolean = writer.deleteBodyFat(entryId)

    suspend fun readBodyFats(from: Instant, to: Instant): List<ExternalBodyFat> =
        reader.readBodyFats(from, to)

    suspend fun writeHeight(heightCm: Double): Boolean = writer.writeHeight(heightCm)

    suspend fun writeNutrition(entry: FoodEntry): Boolean = writer.writeNutrition(entry)

    suspend fun updateNutrition(entry: FoodEntry): Boolean = writer.updateNutrition(entry)

    suspend fun deleteNutrition(entryId: UUID): Boolean = writer.deleteNutrition(entryId)

    suspend fun readNutrition(from: Instant, to: Instant): List<ExternalNutrition>? =
        reader.readNutrition(from, to)

    suspend fun readDailyEnergy(date: LocalDate): DailyEnergyBurn? = reader.readDailyEnergy(date)

    suspend fun readRecentEnergySummary(days: Int = 14): HealthEnergySummary? =
        reader.readRecentEnergySummary(days)

    suspend fun readDailyActivity(days: Int = 7): List<DailyActivity> = reader.readDailyActivity(days)

    suspend fun readDailyWellness(days: Int = 7): List<DailyWellness> = reader.readDailyWellness(days)

    suspend fun getChangesToken(
        recordTypes: Set<kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>> =
            setOf(WeightRecord::class, BodyFatRecord::class)
    ): String? = reader.getChangesToken(recordTypes)

    suspend fun consumeWeightChanges(sinceToken: String): Pair<List<ExternalWeight>, String?>? =
        reader.consumeWeightChanges(sinceToken)

    suspend fun consumeBodyFatChanges(sinceToken: String): Pair<List<ExternalBodyFat>, String?>? =
        reader.consumeBodyFatChanges(sinceToken)

    suspend fun consumeNutritionChanges(sinceToken: String): Pair<List<ExternalNutrition>, String?>? =
        reader.consumeNutritionChanges(sinceToken)

    companion object {
        private const val ACTION_MANAGE_HEALTH_PERMISSIONS =
            "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS"
        /** Play Store package for the standalone HC APK (API ≤33). Public constant is internal in Jetpack. */
        private const val HC_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        internal const val CLIENT_PREFIX = "fudai_"

        /** Bump this when we add a new record type so users re-auth.
         *  v2 = added BodyFatRecord read+write permissions.
         *  v3 = added energy burn read permissions.
         *  v4 = added NutritionRecord read permission (food-log restore).
         *  v5 = added Steps + ExerciseSession read permissions (activity card).
         *  v6 = added Height write + Sleep/RestingHeartRate/Hydration reads (wellness card).
         *  v7 = added READ_HEALTH_DATA_HISTORY when the module supports it. */
        const val CURRENT_TYPES_VERSION = 7
    }
}
