package app.chompass.services

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import app.chompass.ui.theme.AppThemeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Device-free verification of the launcher-icon alias machinery (#13, #21):
 * exactly one alias owns MAIN/LAUNCHER after every apply, the fixed-icon option
 * pins the teal alias, and re-applies are idempotent. The swap deferral itself
 * lives in [MainActivity.onStop] (not testable on JVM) — this pins the
 * PackageManager state machine it drives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AndroidAppIconManagerRobolectricTest {
    private val context = RuntimeEnvironment.getApplication()

    private val aliasNames = mapOf(
        AppThemeColor.TEAL to "LauncherTeal",
        AppThemeColor.BLUE to "LauncherBlue",
        AppThemeColor.GREEN to "LauncherGreen",
        AppThemeColor.PURPLE to "LauncherPurple",
        AppThemeColor.PINK to "LauncherBabyPink",
        AppThemeColor.ORANGE to "LauncherOrange",
        AppThemeColor.INDIGO to "LauncherIndigo",
        AppThemeColor.NEUTRAL to "LauncherGraphite",
    )

    private fun component(color: AppThemeColor) =
        ComponentName(context.packageName, "app.chompass.${aliasNames.getValue(color)}")

    private fun isEnabled(color: AppThemeColor): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(color))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            // Manifest default: only Teal starts enabled.
            else -> color == AppThemeColor.TEAL
        }

    private fun enabledCount(): Int =
        AppThemeColor.iconSelectableColors().count(::isEnabled)

    @Test
    fun manifestDefault_enablesOnlyTeal() {
        assertEquals(1, enabledCount())
        assertTrue(isEnabled(AppThemeColor.TEAL))
        assertEquals(
            ComponentName(context.packageName, "app.chompass.LauncherTeal"),
            AndroidAppIconManager.enabledLauncherComponent(context),
        )
    }

    @Test
    fun apply_fixedColor_enablesOnlyThatAlias() {
        AndroidAppIconManager.apply(context, AppThemeColor.BLUE)

        assertEquals(1, enabledCount())
        assertTrue(isEnabled(AppThemeColor.BLUE))
        assertTrue(!isEnabled(AppThemeColor.TEAL))
        assertEquals(
            ComponentName(context.packageName, "app.chompass.LauncherBlue"),
            AndroidAppIconManager.enabledLauncherComponent(context),
        )
    }

    @Test
    fun apply_switchingColorMovesExactlyOneAlias() {
        AndroidAppIconManager.apply(context, AppThemeColor.BLUE)
        AndroidAppIconManager.apply(context, AppThemeColor.NEUTRAL)

        assertEquals(1, enabledCount())
        assertTrue(isEnabled(AppThemeColor.NEUTRAL))
        assertTrue(!isEnabled(AppThemeColor.BLUE))
        assertEquals(
            ComponentName(context.packageName, "app.chompass.LauncherGraphite"),
            AndroidAppIconManager.enabledLauncherComponent(context),
        )
    }

    @Test
    fun apply_reapplySameColor_isIdempotent() {
        AndroidAppIconManager.apply(context, AppThemeColor.BLUE)
        AndroidAppIconManager.apply(context, AppThemeColor.BLUE)

        assertEquals(1, enabledCount())
        assertTrue(isEnabled(AppThemeColor.BLUE))
    }

    @Test
    fun apply_fixedIcon_pinsTealRegardlessOfThemeColor() {
        AndroidAppIconManager.apply(context, AppThemeColor.BLUE)
        AndroidAppIconManager.apply(context, AppThemeColor.BLUE, fixedIcon = true)

        assertEquals(1, enabledCount())
        assertTrue(isEnabled(AppThemeColor.TEAL))
        assertTrue(!isEnabled(AppThemeColor.BLUE))
        assertEquals(
            ComponentName(context.packageName, "app.chompass.LauncherTeal"),
            AndroidAppIconManager.enabledLauncherComponent(context),
        )
    }

    @Test
    fun apply_systemColor_neverLeavesZeroOrTwoAliasesEnabled() {
        // Robolectric's wallpaper palette is not the point here: whatever SYSTEM
        // resolves to, exactly one alias must stay enabled (never zero, never two).
        AndroidAppIconManager.apply(context, AppThemeColor.SYSTEM)
        assertEquals(1, enabledCount())

        AndroidAppIconManager.apply(context, AppThemeColor.SYSTEM, fixedIcon = true)
        assertEquals(1, enabledCount())
        assertTrue(isEnabled(AppThemeColor.TEAL))
    }
}
