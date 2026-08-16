package app.chompass.services

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Intents that bring the user into the existing Chompass task.
 *
 * Always target the **enabled launcher activity-alias**, not [app.chompass.MainActivity]
 * directly. With `launchMode=singleTop`, `CLEAR_TOP` / `SINGLE_TOP` against a mismatched
 * component stacks a second activity and drops camera/gallery/share results.
 */
object ChompassLaunchIntents {
    /** Codeberg #27: a notification can carry a `chompass://go/<dest>` data URI to open on tap. */
    fun openApp(context: Context, destination: String? = null): Intent =
        Intent().apply {
            component = AndroidAppIconManager.enabledLauncherComponent(context)
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            if (destination != null) data = Uri.parse("chompass://go/$destination")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
