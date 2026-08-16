package app.chompass.ui.theme

import android.app.Application
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Codeberg #28: the window chrome (values/themes.xml → chompass_* colors)
 * must not follow the system night mode while the in-app Appearance setting
 * drives the actual theme. `values-night/colors.xml` now only overrides the
 * pre-Compose splash background; the window colors resolve identically in
 * both modes so the XML layer can never contradict the in-app Light/Dark/
 * System choice (visible as dark status/window chrome or a dark flash on
 * ROMs that re-resolve night resources without recreating the activity).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ThemeNightResourcesTest {
    @Test
    fun windowColors_areModeNeutral_despiteNightConfiguration() {
        val context = RuntimeEnvironment.getApplication()

        val night = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val nightAppContext = context.createConfigurationContext(night)
        val nightBackgroundColor = nightAppContext.resources.getColor(app.chompass.R.color.chompass_background, null)
        val dayBackgroundColor = context.resources.getColor(app.chompass.R.color.chompass_background, null)

        assertEquals("windowBackground must stay mode-neutral (#28)", dayBackgroundColor, nightBackgroundColor)
        assertEquals(0xFFFEF7FF.toInt(), nightBackgroundColor)

        val nightSplash = nightAppContext.resources.getColor(app.chompass.R.color.app_splash_background, null)
        val daySplash = context.resources.getColor(app.chompass.R.color.app_splash_background, null)
        assertEquals("splash may follow the system night mode", 0xFF1C1B1F.toInt(), nightSplash)
        assertEquals(0xFFFEF7FF.toInt(), daySplash)
    }
}
