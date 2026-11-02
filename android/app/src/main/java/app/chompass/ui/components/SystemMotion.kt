package app.chompass.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * System-level motion gates shared by every animated surface in the app.
 *
 * Two independent signals mean "do not animate": the user turned on battery saver, or
 * they removed animations in Developer options (animator duration scale 0). Both must
 * take effect while the app is in the foreground, so this observes the power-save
 * broadcast and the animation-scale setting instead of sampling them on resume.
 */

internal fun Context.isPowerSaveMode(): Boolean =
    getSystemService(PowerManager::class.java)?.isPowerSaveMode ?: false

internal fun Context.isAnimatorScaleOff(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** True while system motion must stand still (battery saver / animations removed). */
@Composable
internal fun rememberSystemMotionPaused(): State<Boolean> {
    val context = LocalContext.current
    val paused = remember {
        mutableStateOf(context.isPowerSaveMode() || context.isAnimatorScaleOff())
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                paused.value = context.isPowerSaveMode()
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        val animatorObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                paused.value = context.isAnimatorScaleOff()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animatorObserver,
        )
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(animatorObserver)
        }
    }

    return paused
}
