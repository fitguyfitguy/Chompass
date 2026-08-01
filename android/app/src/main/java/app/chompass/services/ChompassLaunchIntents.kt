package app.chompass.services

import android.content.Context
import android.content.Intent

/**
 * Intents that bring the user into the existing Chompass task.
 *
 * Always target the **enabled launcher activity-alias**, not [app.chompass.MainActivity]
 * directly. With `launchMode=singleTop`, `CLEAR_TOP` / `SINGLE_TOP` against a mismatched
 * component stacks a second activity and drops camera/gallery/share results.
 */
object ChompassLaunchIntents {
    fun openApp(context: Context): Intent =
        Intent().apply {
            component = AndroidAppIconManager.enabledLauncherComponent(context)
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
