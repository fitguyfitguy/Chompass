package app.chompass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import app.chompass.ui.coach.CoachScreenPreviewContent
import app.chompass.ui.home.EntryAnalysisOverlay
import app.chompass.ui.home.EntryAnalysisPhase
import app.chompass.ui.home.HomeAddFoodScreenshotContent
import app.chompass.ui.home.HomeMealComponentsScreenshotContent
import app.chompass.ui.home.HomeRecipesScreenshotContent
import app.chompass.ui.home.HomeScreenPreviewContent
import app.chompass.ui.home.WhatIfMealImpactDialog
import app.chompass.ui.navigation.ChompassBottomNavBar
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.progress.ProgressScreenPreviewContent
import app.chompass.ui.settings.SettingsScreenPreviewContent
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.ChompassTheme

// 1080×2400 px at xxhdpi (420 dpi): dp = px / (dpi/160) → 411×914 dp.
// Using 1080×2400 *dp* (the old value) laid out on a phone-sized canvas ~2.6× too wide,
// which made text and controls look zoomed out in the exported PNGs.
private const val PHONE = "spec:width=411dp,height=914dp,dpi=420"

// Small-phone class (≈4.5" 320×480 dp @160 dpi, e.g. old Moto E / small Androids) —
// the class of device where tall dialog content clips (Codeberg #17).
private const val SMALL_PHONE = "spec:width=320dp,height=480dp,dpi=160"

@Composable
private fun ReleaseScreenshotFrame(
    currentRoute: String,
    darkTheme: Boolean,
    showNavBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    ChompassTheme(
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
                if (showNavBar) {
                    ChompassBottomNavBar(
                        currentRoute = currentRoute,
                        onTap = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@PreviewTest
@Preview(name = "01-home-light", device = PHONE)
@Composable
fun HomeLightScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.HOME, darkTheme = false) {
        HomeScreenPreviewContent(ui = ScreenshotFixtures.homeUiState())
    }
}

@PreviewTest
@Preview(name = "02-progress-light", device = PHONE)
@Composable
fun ProgressLightScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.PROGRESS, darkTheme = false) {
        ProgressScreenPreviewContent(ui = ScreenshotFixtures.progressUiState())
    }
}

@PreviewTest
@Preview(name = "03-coach-light", device = PHONE)
@Composable
fun CoachLightScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.COACH, darkTheme = false) {
        CoachScreenPreviewContent(ui = ScreenshotFixtures.coachUiState())
    }
}

@PreviewTest
@Preview(name = "04-settings-light", device = PHONE)
@Composable
fun SettingsLightScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.SETTINGS, darkTheme = false) {
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
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.HOME, darkTheme = true) {
        HomeScreenPreviewContent(ui = ScreenshotFixtures.homeUiState())
    }
}

// Burn-shade A/B: active shades only vs active + resting (basal) rim. Pending
// design pick — used to compare both variants, then removed in favour of one.
@PreviewTest
@Preview(name = "burn-shades-no-resting-dark", device = PHONE)
@Composable
fun BurnShadesNoRestingDark() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.HOME, darkTheme = true) {
        HomeScreenPreviewContent(ui = ScreenshotFixtures.homeUiState(), showRestingShade = false)
    }
}

@PreviewTest
@Preview(name = "burn-shades-with-resting-dark", device = PHONE)
@Composable
fun BurnShadesWithRestingDark() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.HOME, darkTheme = true) {
        HomeScreenPreviewContent(ui = ScreenshotFixtures.homeUiState(), showRestingShade = true)
    }
}

@PreviewTest
@Preview(name = "06-progress-dark", device = PHONE)
@Composable
fun ProgressDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.PROGRESS, darkTheme = true) {
        ProgressScreenPreviewContent(ui = ScreenshotFixtures.progressUiState())
    }
}

@PreviewTest
@Preview(name = "07-add-food-light", device = PHONE)
@Composable
fun AddFoodLightScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.HOME, darkTheme = false) {
        HomeAddFoodScreenshotContent(ui = ScreenshotFixtures.homeUiState())
    }
}

@PreviewTest
@Preview(name = "08-coach-dark", device = PHONE)
@Composable
fun CoachDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.COACH, darkTheme = true) {
        CoachScreenPreviewContent(ui = ScreenshotFixtures.coachUiState())
    }
}

@PreviewTest
@Preview(name = "09-settings-dark", device = PHONE)
@Composable
fun SettingsDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.SETTINGS, darkTheme = true) {
        SettingsScreenPreviewContent(
            ui = ScreenshotFixtures.settingsUiState(appearanceMode = "dark"),
            latestMeasurementWaistCm = 84.0,
        )
    }
}

@PreviewTest
@Preview(name = "what-if-small-screen-long-suggestion", device = SMALL_PHONE)
@Composable
fun WhatIfSmallScreenLongSuggestionScreenshot() {
    // Codeberg #17: long AI "What if?" suggestion on a small screen — verify the
    // dialog content stays readable (scrollable) instead of clipping.
    ReleaseScreenshotFrame(
        currentRoute = ChompassRoutes.HOME,
        darkTheme = false,
        showNavBar = false,
    ) {
        WhatIfMealImpactDialog(
            entry = ScreenshotFixtures.foodEntries[1],
            dayEntries = ScreenshotFixtures.foodEntries,
            profile = ScreenshotFixtures.profile,
            onDismiss = {},
            onSuggest = { LONG_WHAT_IF_SUGGESTION },
            initialSuggestion = LONG_WHAT_IF_SUGGESTION,
        )
    }
}

@PreviewTest
@Preview(name = "what-if-standard-screen-long-suggestion", device = PHONE)
@Composable
fun WhatIfStandardScreenLongSuggestionScreenshot() {
    // Control shot: same long suggestion on the standard 411×914 dp phone.
    ReleaseScreenshotFrame(
        currentRoute = ChompassRoutes.HOME,
        darkTheme = false,
        showNavBar = false,
    ) {
        WhatIfMealImpactDialog(
            entry = ScreenshotFixtures.foodEntries[1],
            dayEntries = ScreenshotFixtures.foodEntries,
            profile = ScreenshotFixtures.profile,
            onDismiss = {},
            onSuggest = { LONG_WHAT_IF_SUGGESTION },
            initialSuggestion = LONG_WHAT_IF_SUGGESTION,
        )
    }
}

/** Long multi-line AI suggestion (worst case: several sentences + examples). */
private const val LONG_WHAT_IF_SUGGESTION =
    "Logging this 540 kcal chicken rice bowl would keep you on track for today's " +
    "goal: you'd be at 1,070 kcal of your 2,200 kcal budget, leaving 1,130 kcal " +
    "for dinner. That's a great balance — the 42 g of protein supports your muscle " +
    "retention goal, and the 58 g of carbs give you fuel for your afternoon workout. " +
    "If you want to save more room for an evening meal, consider logging a lighter " +
    "lunch option like the 320 kcal Greek yogurt bowl instead, which would leave " +
    "you 1,350 kcal for dinner and still hit your protein target."

@PreviewTest
@Preview(name = "10-add-food-dark", device = PHONE)
@Composable
fun AddFoodDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.HOME, darkTheme = true) {
        HomeAddFoodScreenshotContent(ui = ScreenshotFixtures.homeUiState())
    }
}

@PreviewTest
@Preview(name = "11-meal-components-dark", device = PHONE)
@Composable
fun MealComponentsDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.HOME, darkTheme = true) {
        HomeMealComponentsScreenshotContent(
            ui = ScreenshotFixtures.homeUiState(),
            constituents = ScreenshotFixtures.mealConstituents,
            mealName = "Chicken rice bowl",
            mealCalories = 540,
            mealEmoji = "🍗",
        )
    }
}

@PreviewTest
@Preview(name = "12-recipes-dark", device = PHONE)
@Composable
fun RecipesDarkScreenshot() {
    ReleaseScreenshotFrame(currentRoute = ChompassRoutes.HOME, darkTheme = true) {
        HomeRecipesScreenshotContent(
            ui = ScreenshotFixtures.homeUiState(),
            recipes = ScreenshotFixtures.demoRecipes,
        )
    }
}

@PreviewTest
@Preview(name = "13-ai-analysis-dark", device = PHONE)
@Composable
fun AiAnalysisDarkScreenshot() {
    // Full-screen overlay (not ModalBottomSheet) so JVM screenshot capture works.
    ReleaseScreenshotFrame(
        currentRoute = ChompassRoutes.HOME,
        darkTheme = true,
        showNavBar = false,
    ) {
        EntryAnalysisOverlay(
            phase = EntryAnalysisPhase.CallingAi,
            partial = ScreenshotFixtures.streamingPartial,
        )
    }
}
