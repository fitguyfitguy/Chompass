package app.chompass.services

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.chompass.MainActivity
import app.chompass.R

/**
 * Publishes the static set of Home entry shortcuts (Camera, Voice, Barcode).
 * Dynamic publication works across launcher-icon activity-aliases; static XML
 * shortcuts would only attach to whichever alias currently owns MAIN/LAUNCHER.
 */
object LauncherShortcuts {
    const val EXTRA_SHORTCUT = "shortcut_entry"

    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(
                context,
                id = "camera",
                labelRes = R.string.shortcut_camera_short,
                longLabelRes = R.string.shortcut_camera_long,
                iconRes = R.drawable.ic_shortcut_camera,
                action = ShortcutEntryAction.CAMERA,
            ),
            shortcut(
                context,
                id = "voice",
                labelRes = R.string.shortcut_voice_short,
                longLabelRes = R.string.shortcut_voice_long,
                iconRes = R.drawable.ic_shortcut_voice,
                action = ShortcutEntryAction.VOICE,
            ),
            shortcut(
                context,
                id = "barcode",
                labelRes = R.string.shortcut_barcode_short,
                longLabelRes = R.string.shortcut_barcode_long,
                iconRes = R.drawable.ic_shortcut_barcode,
                action = ShortcutEntryAction.BARCODE,
            ),
        )
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun shortcut(
        context: Context,
        id: String,
        labelRes: Int,
        longLabelRes: Int,
        iconRes: Int,
        action: ShortcutEntryAction,
    ): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action.action
            putExtra(EXTRA_SHORTCUT, action.name.lowercase())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(labelRes))
            .setLongLabel(context.getString(longLabelRes))
            .setIcon(IconCompat.createWithResource(context, iconRes))
            .setIntent(intent)
            .build()
    }
}
