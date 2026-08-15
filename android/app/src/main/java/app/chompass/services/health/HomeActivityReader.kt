package app.chompass.services.health

import app.chompass.data.PreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class ActivityDataSource {
    UNAVAILABLE,
    HEALTH_CONNECT,
    DEBUG,
}

@Serializable
data class DebugActivityDay(
    val date: String,
    val steps: Long,
    val activeCalories: Int,
    val totalCalories: Int? = null,
)

data class HomeActivitySnapshot(
    val date: LocalDate,
    val steps: Long = 0L,
    val activeCalories: Int = 0,
    val totalCalories: Int? = null,
    val source: ActivityDataSource = ActivityDataSource.UNAVAILABLE,
    /**
     * A live measured-energy source exists (Health Connect energy permission
     * granted, or debug data), even when nothing has been recorded for the day
     * yet. When true, measured active (possibly 0) wins over the PAL estimate
     * in ADD_ACTIVE: a morning before the first wearable sync shows the
     * sedentary budget instead of substituting the whole estimated day.
     */
    val energyLive: Boolean = false,
)

/**
 * Reads per-day Health Connect activity for the home screen. Aggregates are not
 * persisted — same policy as [HealthConnectManager.readDailyActivity].
 */
class HomeActivityReader(
    private val health: HealthConnectManager,
    private val prefs: PreferencesStore,
) {
    suspend fun readForDate(date: LocalDate): HomeActivitySnapshot {
        debugSnapshotForDate(date)?.let { return it }
        if (!prefs.healthConnectEnabled.first() || !health.isAvailable()) {
            return HomeActivitySnapshot(date = date)
        }
        val caps = health.capabilities()
        val energyLive = caps.energyRead
        val steps = if (caps.stepsRead) {
            health.readDailyActivity(days = daysBackFromToday(date))
                .firstOrNull { it.date == date }
                ?.steps ?: 0L
        } else {
            0L
        }
        val energy = if (energyLive) health.readDailyEnergy(date) else null
        val active = energy?.active?.toInt() ?: 0
        val hasData = steps > 0L || active > 0 || (energy?.total ?: 0.0) > 0.0
        return HomeActivitySnapshot(
            date = date,
            steps = steps,
            activeCalories = active,
            totalCalories = energy?.total?.toInt(),
            source = if (hasData) ActivityDataSource.HEALTH_CONNECT else ActivityDataSource.UNAVAILABLE,
            energyLive = energyLive,
        )
    }

    private suspend fun debugSnapshotForDate(date: LocalDate): HomeActivitySnapshot? {
        val day = prefs.debugActivityDay(date) ?: return null
        return HomeActivitySnapshot(
            date = date,
            steps = day.steps,
            activeCalories = day.activeCalories,
            totalCalories = day.totalCalories,
            source = ActivityDataSource.DEBUG,
            energyLive = true,
        )
    }

    private fun daysBackFromToday(date: LocalDate): Int {
        val today = LocalDate.now()
        return (today.toEpochDay() - date.toEpochDay()).toInt().coerceAtLeast(0) + 1
    }
}
