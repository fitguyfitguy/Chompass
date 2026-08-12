package app.chompass.services.health

import app.chompass.data.BodyFatRepository
import app.chompass.data.FoodRepository
import app.chompass.data.PreferencesStore
import app.chompass.data.WaterRepository
import app.chompass.data.WeightRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first

/** Stable labels for the read types a Health Connect changes token was seeded for,
 *  persisted alongside the token so we can detect a newly-granted read capability. */
private const val HEALTH_READ_TYPE_WEIGHT = "weight"
private const val HEALTH_READ_TYPE_BODY_FAT = "bodyfat"
private const val HEALTH_READ_TYPE_NUTRITION = "nutrition"

/**
 * Pull external weight + body-fat readings FROM Health Connect into the app (e.g. a
 * Withings scale that writes weigh-ins to Health Connect). Runs on app foreground and
 * right after the user connects/grants. Read-direction only — gated per metric on READ
 * permission, so a user who granted read but not write still gets their data imported
 * (issue #91). Incremental via a persisted changes token, with a one-time historical
 * backfill when there's no token yet; imports are deduped, so re-runs are harmless.
 */
class HealthConnectReadSync(
    private val prefs: PreferencesStore,
    private val health: HealthConnectManager,
    private val foodRepository: FoodRepository,
    private val weightRepository: WeightRepository,
    private val bodyFatRepository: BodyFatRepository,
    private val waterRepository: WaterRepository,
) {
    @Volatile
    private var inFlight = false

    suspend fun sync() {
        if (inFlight) return
        if (!prefs.healthConnectEnabled.first()) return
        if (!health.isAvailable()) return
        val caps = health.capabilities()
        if (!caps.weightRead && !caps.bodyFatRead && !caps.nutritionRead && !caps.hydrationRead) return

        inFlight = true
        try {
            // One-shot food-log restore: after a reinstall or new phone the local
            // store is empty but our own NutritionRecords survive in Health Connect.
            // Ids already in the log are skipped, so this is a no-op for intact users.
            // A null read means a page failed mid-pagination — leave the flag unset
            // so the restore retries on a later foreground instead of permanently
            // accepting a partial history.
            if (caps.nutritionRead && !prefs.healthFoodRestoreDone.first()) {
                val now = Instant.now()
                val records = health.readNutrition(now.minus(Duration.ofDays(730)), now)
                if (records != null) {
                    foodRepository.restoreFromHealthConnect(records)
                    prefs.setHealthFoodRestoreDone(true)
                }
            }
            // One-shot water-log restore, sibling of the food one: our own
            // HydrationRecords survive reinstalls in Health Connect even though
            // the local DataStore doesn't. Ids already in the log are skipped.
            if (caps.hydrationRead && !prefs.healthHydrationRestoreDone.first()) {
                val now = Instant.now()
                val records = health.readHydration(now.minus(Duration.ofDays(730)), now)
                if (records != null) {
                    waterRepository.restoreFromHealthConnect(records)
                    prefs.setHealthHydrationRestoreDone(true)
                }
            }
            if (!caps.weightRead && !caps.bodyFatRead && !caps.nutritionRead) return

            val desiredTypes = buildSet {
                if (caps.weightRead) add(HEALTH_READ_TYPE_WEIGHT)
                if (caps.bodyFatRead) add(HEALTH_READ_TYPE_BODY_FAT)
                if (caps.nutritionRead) add(HEALTH_READ_TYPE_NUTRITION)
            }
            // If a read type was granted AFTER the token was seeded, the existing token never
            // observes it. Drop the token so we re-enter the backfill branch and import that
            // metric's history + re-seed a token covering everything now granted.
            if (!prefs.healthChangesTokenTypes.first().containsAll(desiredTypes)) {
                prefs.clearHealthChangesToken()
            }

            val token = prefs.healthChangesToken.first()
            if (token == null) {
                // First sync: backfill recent history (two years) so existing scale data shows up.
                val now = Instant.now()
                val from = now.minus(Duration.ofDays(730))
                if (caps.weightRead) {
                    weightRepository.importExternalWeights(health.readWeights(from, now))
                }
                if (caps.bodyFatRead) {
                    bodyFatRepository.importExternalBodyFats(health.readBodyFats(from, now))
                }
                // Seed a token covering only the types we can actually read. Nutrition
                // gets no external backfill on purpose — the one-shot restore above
                // already brings back Fud AI's own history, and silently importing two
                // years of another tracker's diary would be surprising; external meals
                // flow in live from here on.
                val recordTypes = buildSet {
                    if (caps.weightRead) add(androidx.health.connect.client.records.WeightRecord::class)
                    if (caps.bodyFatRead) add(androidx.health.connect.client.records.BodyFatRecord::class)
                    if (caps.nutritionRead) add(androidx.health.connect.client.records.NutritionRecord::class)
                }
                health.getChangesToken(recordTypes)?.let {
                    prefs.setHealthChangesToken(it)
                    prefs.setHealthChangesTokenTypes(desiredTypes)
                }
            } else {
                var next: String? = null
                if (caps.weightRead) {
                    val result = health.consumeWeightChanges(token)
                    if (result == null) { prefs.clearHealthChangesToken(); return }
                    weightRepository.importExternalWeights(result.first)
                    next = result.second
                }
                if (caps.bodyFatRead) {
                    val result = health.consumeBodyFatChanges(token)
                    if (result == null) { prefs.clearHealthChangesToken(); return }
                    bodyFatRepository.importExternalBodyFats(result.first)
                    next = result.second ?: next
                }
                if (caps.nutritionRead) {
                    val result = health.consumeNutritionChanges(token)
                    if (result == null) { prefs.clearHealthChangesToken(); return }
                    foodRepository.importExternalNutrition(result.first)
                    next = result.second ?: next
                }
                next?.let { prefs.setHealthChangesToken(it) }
            }
        } finally {
            inFlight = false
        }
    }
}
