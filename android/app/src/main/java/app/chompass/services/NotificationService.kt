package app.chompass.services

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.chompass.services.update.AndroidUpdateChecker
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.chompass.ChompassApp
import app.chompass.R
import app.chompass.models.WaterAmountFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Calendar

/**
 * Schedules + fires the local reminders the app supports:
 *   - Weight log reminder: daily at 8:00am — gated by the master Notifications
 *     toggle so it tracks the system permission state and never fires silently
 *   - Streak reminder + Daily summary: present in code for parity with iOS but
 *     not wired to the Settings UI yet
 *
 * Uses inexact alarms (setAndAllowWhileIdle) — a daily nudge firing within a
 * few minutes of the chosen time is perfectly fine, and Play Store reserves
 * the exact-alarm permission for calendar / alarm-clock apps only.
 *
 * Also owns the "weight_goal" channel used from WeightRepository crossings.
 *
 * OriginOS / MIUI / HyperOS aggressive battery optimization can still kill
 * inexact alarms unless the app is whitelisted — Settings UI exposes a deep
 * link to the battery-optimization exception screen.
 */
class NotificationService(private val context: Context) {
    fun createChannels() {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val streak = NotificationChannel(
            CHANNEL_STREAK,
            context.getString(R.string.notif_channel_streak),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_channel_streak_desc) }

        val daily = NotificationChannel(
            CHANNEL_DAILY,
            context.getString(R.string.notif_channel_daily),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = context.getString(R.string.notif_channel_daily_desc) }

        val goal = NotificationChannel(
            CHANNEL_WEIGHT_GOAL,
            context.getString(R.string.notif_channel_weight_goal),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.notif_channel_weight_goal_desc) }

        val weight = NotificationChannel(
            CHANNEL_WEIGHT_LOG,
            context.getString(R.string.notif_channel_weight_log),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_channel_weight_log_desc) }

        val bodyFat = NotificationChannel(
            CHANNEL_BODY_FAT_LOG,
            context.getString(R.string.notif_channel_body_fat),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_channel_body_fat_desc) }

        val appUpdate = NotificationChannel(
            CHANNEL_APP_UPDATE,
            context.getString(R.string.notif_channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_channel_updates_desc) }

        val water = NotificationChannel(
            CHANNEL_WATER,
            context.getString(R.string.notif_channel_water),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_channel_water_desc) }

        mgr.createNotificationChannels(listOf(streak, daily, goal, weight, bodyFat, appUpdate, water))
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showGoalReached() {
        val intent = ChompassLaunchIntents.openApp(context, destination = DESTINATION_PROGRESS)
        val content = PendingIntent.getActivity(
            context, REQUEST_GOAL, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_WEIGHT_GOAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_goal_weight_title))
            .setContentText(context.getString(R.string.notif_goal_weight_text))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notifySafely(GOAL_NOTIFICATION_ID, notif)
    }

    /** Post a "new version available" notification. Tapping it opens the Play Store listing
     *  (market:// → web fallback), mirroring the About screen's open-store behavior. */
    fun showUpdateAvailable() {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse(AndroidUpdateChecker.PLAY_STORE_MARKET_URL)).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        val resolvable = marketIntent.resolveActivity(context.packageManager) != null
        val intent = if (resolvable) marketIntent else
            Intent(Intent.ACTION_VIEW, Uri.parse(AndroidUpdateChecker.PLAY_STORE_WEB_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val pi = PendingIntent.getActivity(
            context, REQUEST_APP_UPDATE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_APP_UPDATE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_update_title))
            .setContentText(context.getString(R.string.notif_update_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notifySafely(APP_UPDATE_NOTIFICATION_ID, notif)
    }

    // -- Scheduling ------------------------------------------------------

    fun scheduleStreakReminder(hour: Int, minute: Int) = schedule(
        REQUEST_STREAK, hour, minute, CHANNEL_STREAK,
        title = context.getString(R.string.notif_streak_title),
        text = context.getString(R.string.notif_streak_text)
    )

    fun scheduleDailySummary(hour: Int, minute: Int) = schedule(
        REQUEST_DAILY, hour, minute, CHANNEL_DAILY,
        title = context.getString(R.string.notif_summary_title),
        text = context.getString(R.string.notif_summary_text)
    )

    fun scheduleWeightReminder(hour: Int = 8, minute: Int = 0) = schedule(
        REQUEST_WEIGHT, hour, minute, CHANNEL_WEIGHT_LOG,
        title = context.getString(R.string.notif_weight_log_title),
        text = context.getString(R.string.notif_weight_log_text)
    )

    fun scheduleBodyFatReminder(hour: Int = 8, minute: Int = 0) = schedule(
        REQUEST_BODY_FAT, hour, minute, CHANNEL_BODY_FAT_LOG,
        title = context.getString(R.string.notif_body_fat_log_title),
        text = context.getString(R.string.notif_body_fat_log_text)
    )

    /**
     * Arms the adaptive water chain to fire at [fireAtMillis]. The receiver
     * recomputes the next fire from live state, so the cadence always reflects
     * the latest entries. Uses the same request code as the old fixed-time
     * reminder, so previously armed alarms are replaced in place.
     */
    fun scheduleWaterReminderAt(fireAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_CHANNEL, CHANNEL_WATER)
            putExtra(EXTRA_TITLE, context.getString(R.string.notif_water_title))
            putExtra(EXTRA_TEXT, context.getString(R.string.notif_water_text))
            putExtra(EXTRA_REQUEST, REQUEST_WATER)
        }
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_WATER, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pi)
    }

    fun cancelStreakReminder() = cancel(REQUEST_STREAK)
    fun cancelDailySummary() = cancel(REQUEST_DAILY)
    fun cancelWeightReminder() = cancel(REQUEST_WEIGHT)
    fun cancelBodyFatReminder() = cancel(REQUEST_BODY_FAT)
    fun cancelWaterReminder() = cancel(REQUEST_WATER)

    /**
     * Arms a silent daily alarm for just after midnight that rewrites the
     * widget snapshot to "today" (issue #16). The receiver re-arms the chain;
     * ChompassApp re-arms on cold start (reboots drop alarms). No notification
     * is ever posted — this is a data refresh only.
     */
    fun scheduleWidgetMidnightRefresh() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_CHANNEL, CHANNEL_WIDGET_MIDNIGHT)
        }
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_WIDGET_MIDNIGHT, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextMidnight = nextMidnightMillis()
        // +60s: let day-boundary writes settle; the exact instant is irrelevant
        // as long as the widget rolls over shortly after midnight.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnight + 60_000L, pi)
    }

    fun cancelWidgetMidnightRefresh() = cancel(REQUEST_WIDGET_MIDNIGHT)

    private fun schedule(requestCode: Int, hour: Int, minute: Int, channel: String, title: String, text: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_CHANNEL, channel)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_REQUEST, requestCode)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val now = Calendar.getInstance()
        val fire = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }

        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.timeInMillis, pi)
    }

    private fun cancel(requestCode: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    companion object {
        const val CHANNEL_STREAK = "streak_reminder"
        const val CHANNEL_DAILY = "daily_summary"
        const val CHANNEL_WEIGHT_GOAL = "weight_goal"
        const val CHANNEL_WEIGHT_LOG = "weight_log_reminder"
        const val CHANNEL_BODY_FAT_LOG = "body_fat_log_reminder"
        const val CHANNEL_APP_UPDATE = "app_update"
        const val CHANNEL_WATER = "water_reminder"
        const val CHANNEL_WIDGET_MIDNIGHT = "widget_midnight_refresh"

        /** Notification tap destinations (Codeberg #27); also openable via `chompass://go/<dest>`. */
        const val DESTINATION_PROGRESS = "progress"

        /**
         * Where a notification tap should land, by channel. Weight/body-fat/goal
         * reminders open the Progress tab; everything else stays on the Home tab.
         */
        fun destinationForChannel(channel: String): String? = when (channel) {
            CHANNEL_WEIGHT_GOAL, CHANNEL_WEIGHT_LOG, CHANNEL_BODY_FAT_LOG -> DESTINATION_PROGRESS
            else -> null
        }
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_REQUEST = "request"
        private const val GOAL_NOTIFICATION_ID = 4242
        private const val APP_UPDATE_NOTIFICATION_ID = 5555
        private const val REQUEST_STREAK = 1001
        private const val REQUEST_DAILY = 1002
        private const val REQUEST_WEIGHT = 1003
        private const val REQUEST_BODY_FAT = 1004
        private const val REQUEST_APP_UPDATE = 1005
        private const val REQUEST_WATER = 1006
        private const val REQUEST_WIDGET_MIDNIGHT = 1007
        private const val REQUEST_GOAL = 1008
    }
}

/**
 * Pure gate for streak reminders: skip the nudge when the diary already has
 * food logged today (upstream #150). Non-streak channels ignore this helper.
 */
fun shouldNotifyStreak(hasFoodLoggedToday: Boolean): Boolean = !hasFoodLoggedToday

/**
 * Pure: millis of the first instant strictly after [nowMillis]'s local
 * midnight in [zone] — the widget-rollover arm instant (issue #16).
 */
internal fun nextMidnightMillis(
    nowMillis: Long = System.currentTimeMillis(),
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): Long = Instant.ofEpochMilli(nowMillis)
    .atZone(zone)
    .toLocalDate()
    .plusDays(1)
    .atStartOfDay(zone)
    .toInstant()
    .toEpochMilli()

/** Fired by the alarm. Posts the notification and re-schedules the same alarm +24h. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Reboot drops all alarms. Re-arm the adaptive water chain. Other daily
        // reminders keep their existing reboot gap (out of scope; the receiver is
        // the natural home for a follow-up that re-arms them too).
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val container = (context.applicationContext as? ChompassApp)?.container
                    if (container != null) WaterReminderPlanner.rearm(container)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val channel = intent.getStringExtra(NotificationService.EXTRA_CHANNEL) ?: return

        // Silent midnight widget rollover: rewrite the snapshot to today's data
        // and re-arm the chain. No notification is posted (issue #16).
        if (channel == NotificationService.CHANNEL_WIDGET_MIDNIGHT) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val container = (context.applicationContext as? ChompassApp)?.container
                    container?.widgetSnapshotWriter?.refresh()
                } finally {
                    NotificationService(context).scheduleWidgetMidnightRefresh()
                    pendingResult.finish()
                }
            }
            return
        }

        val title = intent.getStringExtra(NotificationService.EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(NotificationService.EXTRA_TEXT) ?: return
        val request = intent.getIntExtra(NotificationService.EXTRA_REQUEST, -1)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Water reminders recompute the cadence at fire time, so the plan
                // reflects any entries logged since this alarm was armed.
                val container = (context.applicationContext as? ChompassApp)?.container
                val waterPlan = if (channel == NotificationService.CHANNEL_WATER) {
                    container?.let { WaterReminderPlanner.next(it) }
                } else {
                    null
                }

                val shouldPost = if (channel == NotificationService.CHANNEL_STREAK) {
                    val hasFoodToday = runCatching {
                        val c = (context.applicationContext as? ChompassApp)?.container
                            ?: return@runCatching false
                        c.foodRepository.entriesForDate(LocalDate.now()).first().isNotEmpty()
                    }.getOrDefault(false) // fail open: post if diary read fails / app not ready
                    shouldNotifyStreak(hasFoodToday)
                } else if (channel == NotificationService.CHANNEL_WATER) {
                    // Nothing to post when the reminder got disabled since arming.
                    waterPlan != null
                } else {
                    true
                }

                if (shouldPost) {
                    val open = PendingIntent.getActivity(
                        context, request,
                        ChompassLaunchIntents.openApp(context, destination = NotificationService.destinationForChannel(channel)),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val notifText = if (channel == NotificationService.CHANNEL_WATER && waterPlan != null) {
                        // Tell the user how much to drink: one cup (capped by the goal
                        // remainder), in the app's own unit (ml or fl oz).
                        val useMetric = container?.prefs?.weightUnit?.first() == "kg"
                        val qty = if (useMetric) {
                            context.getString(R.string.water_amount_ml, waterPlan.drinkMl)
                        } else {
                            context.getString(
                                R.string.water_amount_fl_oz,
                                WaterAmountFormat.flOzFromMl(waterPlan.drinkMl),
                            )
                        }
                        val interval = waterPlan.intervalMinutes
                        if (interval != null) {
                            context.getString(R.string.notif_water_text_next_qty, qty, interval)
                        } else {
                            context.getString(R.string.notif_water_text_qty, qty)
                        }
                    } else {
                        text
                    }
                    val notif = NotificationCompat.Builder(context, channel)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(notifText)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                    NotificationManagerCompat.from(context).notifySafely(request, notif)
                }

                if (channel == NotificationService.CHANNEL_WATER) {
                    // Re-arm from fresh state: next interval, or tomorrow's window start.
                    if (waterPlan != null) {
                        NotificationService(context).scheduleWaterReminderAt(waterPlan.nextFireMillis)
                    } else {
                        NotificationService(context).cancelWaterReminder()
                    }
                } else {
                    // Re-arm for +24h so the reminder fires daily (even when streak was skipped).
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val nextFire = System.currentTimeMillis() + 24L * 60 * 60 * 1000
                    val reIntent = Intent(context, ReminderReceiver::class.java).apply { putExtras(intent) }
                    val pi = PendingIntent.getBroadcast(
                        context, request, reIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFire, pi)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private fun NotificationManagerCompat.notifySafely(id: Int, notif: android.app.Notification) {
    runCatching { notify(id, notif) } // swallow SecurityException if permission revoked.
}
