package app.chompass.ui.settings

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.ui.components.WheelPicker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression gate for the 2026-09-16 UI-audit wheel pass (H1 + the M8/M9
 * confirm-model decisions D1/D2), kept at the semantics level so the TalkBack
 * contract and the confirm-model survive refactors without a device:
 *
 *  - H1: wheel rows are one merged a11y node announcing the label and the
 *    selected state (the audit dumped `selected=false`, `clickable=false`,
 *    bare-text rows), and the wheel container exposes the live center value
 *    as its state description.
 *  - D2: [NutritionPickerSheet] opens on the stored value verbatim — an
 *    off-grid stored value renders as its own row instead of snapping to the
 *    grid before the first scroll.
 *  - D1: value-editing sheets show an explicit Cancel beside the gradient
 *    Save (AddWeightDialog pairing); scrim/back keep dismissing as Cancel.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w340dp-h720dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WheelPickerA11yTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun configOf(interaction: SemanticsNodeInteraction) =
        interaction.fetchSemanticsNode()

    /** Climbs to the enclosing wheel container carrying [SemanticsProperties.StateDescription]. */
    private fun containerStateDescription(node: SemanticsNode): String? {
        var current: SemanticsNode? = node
        while (current != null) {
            current.config.getOrNull(SemanticsProperties.StateDescription)?.let { return it }
            current = current.parent
        }
        return null
    }

    @Test
    fun wheelRowsExposeLabelSelectedStateAndContainerState() {
        composeRule.setContent {
            MaterialTheme {
                WheelPicker(items = (0..9).toList(), selected = 3, onSelect = {})
            }
        }

        val centerNode = composeRule.onNodeWithText("3").fetchSemanticsNode()
        val center = centerNode.config
        assert(center.getOrNull(SemanticsProperties.Selected) == true) {
            "center wheel row must expose selected=true (audit H1)"
        }
        assert(center.getOrNull(SemanticsProperties.ContentDescription)?.contains("3") == true) {
            "center wheel row must expose its label as contentDescription (audit H1)"
        }

        val offCenter = composeRule.onNodeWithText("4").fetchSemanticsNode().config
        assert(offCenter.getOrNull(SemanticsProperties.Selected) == false) {
            "non-center wheel row must expose selected=false (audit H1)"
        }

        assert(containerStateDescription(centerNode) == "3") {
            "wheel container must expose the live center value as its state (audit H1)"
        }
    }

    @Test
    fun nutritionPickerOpensOnStoredValueVerbatim() {
        // 1917 is off the 50-step grid: the audit's device pass caught the
        // sheet snapping it to 1900 (or 1985) before any user touch.
        composeRule.setContent {
            MaterialTheme {
                NutritionPickerSheet(
                    label = "Calories",
                    unit = "kcal",
                    currentValue = 1917,
                    range = 1200..3500,
                    step = 50,
                    onSave = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("1917").assertExists()
    }

    @Test
    fun heightSheetShowsCancelBesideSave() {
        composeRule.setContent {
            MaterialTheme {
                HeightSheet(current = 170, useMetric = true, onUnitChange = {}, onSave = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Cancel").assertExists()
        composeRule.onNodeWithText("Save").assertExists()
    }
}
