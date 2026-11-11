package app.chompass.scale

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.chompass.ChompassApp
import app.chompass.R
import app.chompass.services.ChompassLaunchIntents
import app.chompass.services.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground host for one [ScaleSyncController] run (`connectedDevice` type).
 * Started when the user starts a sync; the run's coroutine work is launched
 * here, so a weigh-in survives the sheet, the screen turning off, and the UI
 * process dropping to background priority. The controller stops the service
 * when the run ends; this class stops itself as a belt-and-braces fallback.
 *
 * BLUETOOTH_CONNECT (the while-in-use requisite for the type) is granted by
 * ScaleSyncSheet before the run can start; POST_NOTIFICATIONS only controls
 * whether the user sees this notification, not whether the run proceeds.
 */
class ScaleSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var launched = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SCALE_SYNC,
                getString(R.string.notif_channel_scale_sync),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.notif_channel_scale_sync_desc) },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as ChompassApp).container.scaleSyncController
        val fgsType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notif_scale_sync_scanning)),
            fgsType,
        )
        if (!launched) {
            launched = true
            scope.launch {
                launch {
                    // Keep the progress notification in step with the run.
                    controller.state.collect { state -> notifyTextFor(state) }
                }
                try {
                    controller.startAndAwait()
                } finally {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notifyTextFor(state: ScaleSyncState) {
        val text = when (state) {
            is ScaleSyncState.Scanning -> getString(R.string.notif_scale_sync_scanning)
            is ScaleSyncState.Connecting, is ScaleSyncState.Reading ->
                getString(R.string.notif_scale_sync_reading, deviceNameOf(state))
            is ScaleSyncState.AwaitingUser -> getString(R.string.notif_scale_sync_awaiting_user)
            is ScaleSyncState.AwaitingConsent -> getString(R.string.notif_scale_sync_awaiting_consent)
            // Terminal states: the service is stopping; no further updates.
            is ScaleSyncState.Idle -> null
            is ScaleSyncState.Done -> null
            is ScaleSyncState.Failed -> null
            is ScaleSyncState.BondIssue -> null
        }
        if (text == null) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun deviceNameOf(state: ScaleSyncState): String = when (state) {
        is ScaleSyncState.Connecting -> state.deviceName
        is ScaleSyncState.Reading -> state.deviceName
        else -> getString(R.string.progress_sync_scale_metrics)
    }

    private fun buildNotification(text: String): android.app.Notification {
        val content = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            ChompassLaunchIntents.openApp(this, destination = NotificationService.DESTINATION_PROGRESS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_SCALE_SYNC)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.notif_scale_sync_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(content)
            .build()
    }

    companion object {
        const val CHANNEL_SCALE_SYNC = "scale_sync"
        const val NOTIFICATION_ID = 41

        /** Starts the foreground run host. Call after BLUETOOTH permissions are granted. */
        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, ScaleSyncService::class.java),
            )
        }
    }
}
