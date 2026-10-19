package app.chompass.ui.progress

import android.app.Application
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.WeightEntry
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.ChompassTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout gate for the Progress body card (2026-10-19): the verbose chips
 * (`Gewicht` / `Körperfett` / site names) wrapped the toggle row and the long
 * `Gewicht eintragen` / `Körperfett erfassen` pills crowded the `Körper`
 * title. Weight and body fat now use symbol chips, sites use short labels, and
 * the two add actions stay right-aligned with their full names announced.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "de-w411dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgressBodySectionLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-10-18T08:00:00Z")

    private fun setBodySection() {
        composeRule.setContent {
            ChompassTheme(darkTheme = true, themeColor = AppThemeColor.TEAL) {
                BodySection(
                    weights = listOf(WeightEntry(date = now, weightKg = 73.5)),
                    bodyFats = listOf(BodyFatEntry(date = now, bodyFatFraction = 0.174)),
                    measurements = listOf(BodyMeasurement(date = now, waistCm = 84.0)),
                    sites = listOf(BodyMeasurement.Site.WAIST),
                    selectedKeys = setOf(OVERLAY_KEY_WEIGHT, OVERLAY_KEY_BODY_FAT, "waist"),
                    onToggleSeries = {},
                    weightStats = WeightSummaryStats(currentKg = 73.5, netChangeKg = -1.5, averageKg = 73.9),
                    bodyFatStats = BodyFatSummaryStats(
                        currentFraction = 0.174,
                        netChangePercent = -0.8,
                        averagePercent = 17.9,
                    ),
                    profileBodyFatFraction = 0.174,
                    goalWeightKg = 70.0,
                    goalBodyFatFraction = 0.15,
                    useMetricWeight = true,
                    useMetricLength = true,
                    bodyFatAvailable = true,
                )
            }
        }
    }

    private fun boundsOf(text: String): SemanticsNode =
        composeRule.onNodeWithText(text).fetchSemanticsNode()

    @Test
    fun addActionsUseShortLabelsAndStayRightAligned() {
        setBodySection()

        val title = boundsOf("Körper")
        val weightAction = boundsOf("Gewicht")
        val bodyFatAction = boundsOf("Körperfett")

        composeRule.onNodeWithText("Gewicht eintragen").assertDoesNotExist()
        composeRule.onNodeWithText("Körperfett erfassen").assertDoesNotExist()
        composeRule.onNodeWithText("Gewicht").assertIsDisplayed()
        composeRule.onNodeWithText("Körperfett").assertIsDisplayed()

        assert(weightAction.boundsInRoot.left > title.boundsInRoot.right) {
            "weight action starts at ${weightAction.boundsInRoot.left}, title ends at ${title.boundsInRoot.right}"
        }
        assert(bodyFatAction.boundsInRoot.left > weightAction.boundsInRoot.right) {
            "body-fat action is not to the right of the weight action"
        }
    }

    @Test
    fun compactChipsAnnounceFullSeriesNames() {
        setBodySection()

        // Debug builds append the selected state for uiautomator.
        composeRule.onNodeWithContentDescription("Gewicht, selected=true").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Körperfett, selected=true").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Taille, selected=true").assertIsDisplayed()

        // The site chip itself is abbreviated; the legend below keeps the name.
        composeRule.onNodeWithText("Ta").assertIsDisplayed()
    }
}
