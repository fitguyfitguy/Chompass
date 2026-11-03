package app.chompass.ui.progress

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.models.UserProfile
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.ChompassTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #153 empty-state gate (2026-11-03): the "Scale metrics" log action lived only
 * inside the Scale metrics card, which rendered solely when the selected range
 * held composition rows — a profile with zero rows (fresh install, weight-only
 * use) had no manual entry point at all, and the Lee muscle estimate stayed
 * hidden behind the same gate. With a profile the card now renders header +
 * note + log pill + estimate even with no readings; without a profile nothing
 * renders.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgressCompositionEmptyStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyRange_withProfile_showsLogActionAndEstimate() {
        composeRule.setContent {
            ChompassTheme(darkTheme = true, themeColor = AppThemeColor.TEAL) {
                CompositionSection(
                    compositions = emptyList(),
                    weights = emptyList(),
                    useMetric = true,
                    profile = UserProfile(),
                    hasMeasuredMuscle = false,
                )
            }
        }

        // The header title and the log pill share the label "Scale metrics".
        composeRule.onAllNodesWithText("Scale metrics").assertCountEquals(2)
        composeRule.onNodeWithText("Muscle mass (estimated)").assertIsDisplayed()
    }

    @Test
    fun emptyRange_withoutProfile_rendersNothing() {
        composeRule.setContent {
            ChompassTheme(darkTheme = true, themeColor = AppThemeColor.TEAL) {
                CompositionSection(
                    compositions = emptyList(),
                    weights = emptyList(),
                    useMetric = true,
                    profile = null,
                )
            }
        }

        composeRule.onAllNodesWithText("Scale metrics").assertCountEquals(0)
    }
}
