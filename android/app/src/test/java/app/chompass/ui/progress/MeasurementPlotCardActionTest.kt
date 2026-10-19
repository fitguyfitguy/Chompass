package app.chompass.ui.progress

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.models.BodyMeasurement
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.ChompassTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The `+` that logs a value lives on each singular measurement card title
 * (Neck, Waist, …), not on the shared body-chart chips.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "de-w411dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MeasurementPlotCardActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun titlePlusOpensThatSitesLogger() {
        var clicked = false
        composeRule.setContent {
            ChompassTheme(darkTheme = true, themeColor = AppThemeColor.TEAL) {
                MeasurementPlotCard(
                    site = BodyMeasurement.Site.WAIST,
                    entries = listOf(
                        BodyMeasurement(
                            date = Instant.parse("2026-10-18T08:00:00Z"),
                            waistCm = 84.0,
                        )
                    ),
                    useMetric = true,
                    chartsImmediate = true,
                    onLog = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Taille erfassen")
            .assertIsDisplayed()
            .performClick()

        assert(clicked) { "measurement card + did not invoke onLog" }
    }
}
