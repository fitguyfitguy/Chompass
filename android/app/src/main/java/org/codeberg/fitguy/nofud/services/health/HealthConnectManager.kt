package org.codeberg.fitguy.nofud.services.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
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
import org.codeberg.fitguy.nofud.models.BodyFatEntry
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.WeightEntry
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

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

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

    val permissions: Set<String> = setOf(
        weightRead, weightWrite, nutritionRead, nutritionWrite,
        bodyFatRead, bodyFatWrite, activeEnergyRead, totalEnergyRead,
        stepsRead, exerciseRead, heightWrite, sleepRead, restingHrRead, hydrationRead
    )

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
        internal const val CLIENT_PREFIX = "fudai_"

        /** Bump this when we add a new record type so users re-auth.
         *  v2 = added BodyFatRecord read+write permissions.
         *  v3 = added energy burn read permissions.
         *  v4 = added NutritionRecord read permission (food-log restore).
         *  v5 = added Steps + ExerciseSession read permissions (activity card).
         *  v6 = added Height write + Sleep/RestingHeartRate/Hydration reads (wellness card). */
        const val CURRENT_TYPES_VERSION = 6
    }
}
