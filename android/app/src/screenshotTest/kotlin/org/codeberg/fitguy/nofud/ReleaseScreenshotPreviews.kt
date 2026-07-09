package org.codeberg.fitguy.nofud

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.codeberg.fitguy.nofud.ui.coach.CoachScreenPreviewContent
import org.codeberg.fitguy.nofud.ui.home.HomeAddFoodScreenshotContent
import org.codeberg.fitguy.nofud.ui.home.HomeScreenPreviewContent
import org.codeberg.fitguy.nofud.ui.navigation.NoFUDBottomNavBar
import org.codeberg.fitguy.nofud.ui.navigation.NoFUDRoutes
import org.codeberg.fitguy.nofud.ui.progress.ProgressScreenPreviewContent
import org.codeberg.fitguy.nofud.ui.settings.SettingsScreenPreviewContent
import org.codeberg.fitguy.nofud.ui.theme.AppThemeColor
import org.codeberg.fitguy.nofud.ui.theme.NoFUDTheme

// 1080×2400 px at xxhdpi (420 dpi): dp = px / (dpi/160) → 411×914 dp.
// Using 1080×2400 *dp* (the old value) laid out on a phone-sized canvas ~2.6× too wide,
// which made text and controls look zoomed out in the exported PNGs.
private const val PHONE = "spec:width=411dp,height=914dp,dpi=420"

@Composable
private fun ReleaseScreenshotFrame(
    currentRoute: String,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    NoFUDTheme(
        darkTheme = darkTheme,
        themeColor = AppThemeColor.TEAL,
        glassBlurEnabled = false,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
                NoFUDBottomNavBar(
                    currentRoute = currentRoute,
                    onTap = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "01-home-light", device = PHONE)
@Composable
fun HomeLightScreenshot() {
    ReleaseScreenshotFrame(currentRoute = NoFUDRoutes.HOME, darkTheme = false) {
        HomeScreenPreviewContent(ui = ScreenshotFixtures.homeUiState())
    }
}

@PreviewTest
@Preview(name = "02-progress-light", device = PHONE)
@Composable
fun ProgressLightScreenshot() {
    ReleaseScreenshotFrame(currentRoute = NoFUDRoutes.PROGRESS, darkTheme = false) {
        ProgressScreenPreviewContent(ui = ScreenshotFixtures.progressUiState())
    }
}

@PreviewTest
@Preview(name = "03-coach-light", device = PHONE)
@Composable
fun CoachLightScreenshot() {
    ReleaseScreenshotFrame(currentRoute = NoFUDRoutes.COACH, darkTheme = false) {
        CoachScreenPreviewContent(ui = ScreenshotFixtures.coachUiState())
    }
}

@PreviewTest
@Preview(name = "04-settings-light", device = PHONE)
@Composable
fun SettingsLightScreenshot() {
    ReleaseScreenshotFrame(currentRoute = NoFUDRoutes.SETTINGS, darkTheme = false) {
        SettingsScreenPreviewContent(
            ui = ScreenshotFixtures.settingsUiState(),
            latestMeasurementWaistCm = 84.0,
        )
    }
}

@PreviewTest
@Preview(name = "05-home-dark", device = PHONE)
@Composable
fun HomeDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = NoFUDRoutes.HOME, darkTheme = true) {
        HomeScreenPreviewContent(ui = ScreenshotFixtures.homeUiState())
    }
}

@PreviewTest
@Preview(name = "06-progress-dark", device = PHONE)
@Composable
fun ProgressDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = NoFUDRoutes.PROGRESS, darkTheme = true) {
        ProgressScreenPreviewContent(ui = ScreenshotFixtures.progressUiState())
    }
}

@PreviewTest
@Preview(name = "07-add-food-light", device = PHONE)
@Composable
fun AddFoodLightScreenshot() {
    ReleaseScreenshotFrame(currentRoute = NoFUDRoutes.HOME, darkTheme = false) {
        HomeAddFoodScreenshotContent(ui = ScreenshotFixtures.homeUiState())
    }
}

@PreviewTest
@Preview(name = "08-coach-dark", device = PHONE)
@Composable
fun CoachDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = NoFUDRoutes.COACH, darkTheme = true) {
        CoachScreenPreviewContent(ui = ScreenshotFixtures.coachUiState())
    }
}

@PreviewTest
@Preview(name = "09-settings-dark", device = PHONE)
@Composable
fun SettingsDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = NoFUDRoutes.SETTINGS, darkTheme = true) {
        SettingsScreenPreviewContent(
            ui = ScreenshotFixtures.settingsUiState(appearanceMode = "dark"),
            latestMeasurementWaistCm = 84.0,
        )
    }
}

@PreviewTest
@Preview(name = "10-add-food-dark", device = PHONE)
@Composable
fun AddFoodDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = NoFUDRoutes.HOME, darkTheme = true) {
        HomeAddFoodScreenshotContent(ui = ScreenshotFixtures.homeUiState())
    }
}
