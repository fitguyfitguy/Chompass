package app.chompass.services.health

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import app.chompass.ChompassApp
import java.util.concurrent.TimeUnit

/**
 * Opt-in periodic background sync of Health Connect reads. A thin shell around
 * [app.chompass.AppContainer.syncHealthConnectReads], which is
 * already idempotent, in-flight-guarded and self-gating on the connect + capability
 * state — so the worker only needs to check the opt-in pref and delegate.
 */
class HealthSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? ChompassApp)?.container ?: return Result.failure()
        if (!container.prefs.healthBackgroundSyncEnabled.first()) return Result.success()
        return runCatching { container.syncHealthConnectReads() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val UNIQUE_NAME = "health_connect_sync"
        private const val INTERVAL_HOURS = 6L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
