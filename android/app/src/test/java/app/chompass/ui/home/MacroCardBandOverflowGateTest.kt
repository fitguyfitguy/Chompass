package app.chompass.ui.home

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.models.HomeMacroCardLayout
import app.chompass.ui.components.MacroCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Overflow gate for the home nutrient band card (`CompactBarsMacroCard` via
 * [MacroCard]): at the real 4-card width (~69dp) the value and the
 * signed-delta status must each stay visible and single-line — the status has
 * no weight and must never truncate, the value autosizes into the remaining
 * space. Same gate at the repo's 1.3x large-font bar.
 *
 * Asserts on `lineCount` and node bounds, never absolute pixel sizes.
 * Literal strings, not resources: Robolectric resolves the default (en)
 * locale, and the stress numbers (4820/3840) are the interesting widths.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w340dp-h720dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MacroCardBandOverflowGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun SemanticsNodeInteraction.assertVisibleSingleLine(label: String) {
        val node = fetchSemanticsNode()
        val bounds = node.boundsInRoot
        assert(bounds.width > 0f && bounds.height > 0f) {
            "$label collapsed to ${bounds.width.toInt()}x${bounds.height.toInt()} (invisible)"
        }
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts)
        val lineCount = layouts.firstOrNull()?.lineCount
        assert(lineCount == 1) {
            "$label rendered on $lineCount lines (bounds=$bounds); band text must stay on one line"
        }
    }

    private fun setBandCard(
        label: String,
        shortLabel: String,
        current: Double,
        goal: Int,
        unit: String,
        isEnergy: Boolean = false,
        fatFloor: Boolean = false,
        fontScale: Float? = null,
        layout: HomeMacroCardLayout? = null,
    ) {
        composeRule.setContent {
            MaterialTheme {
                val card: @Composable () -> Unit = {
                    MacroCard(
                        label = label,
                        shortLabel = shortLabel,
                        current = current,
                        goal = goal,
                        unit = unit,
                        modifier = Modifier.width(69.dp),
                        isEnergy = isEnergy,
                        fatFloor = fatFloor,
                        layout = layout,
                    )
                }
                if (fontScale != null) {
                    DeviceConfigurationOverride(
                        DeviceConfigurationOverride.FontScale(fontScale)
                    ) { card() }
                } else {
                    card()
                }
            }
        }
    }

    @Test
    fun energyStress_deltaAndValue_staySingleLineAt69dp() {
        // 4820 eaten vs a 7640 kcal goal: "−2820" is the widest left-delta the
        // band shows; the status must not truncate and the value must autosize in.
        setBandCard(
            label = "Calories",
            shortLabel = "Cal",
            current = 4820.0,
            goal = 7640,
            unit = "kcal",
            isEnergy = true,
        )
        composeRule.onNodeWithText("−2820").assertVisibleSingleLine("energy delta")
        composeRule.onNodeWithText("4820").assertVisibleSingleLine("energy value")
    }

    @Test
    fun energyStress_overDelta_staySingleLineAtFontScale130() {
        // Over goal flips to the accent "+2820" path; same widths at 1.3x.
        setBandCard(
            label = "Calories",
            shortLabel = "Cal",
            current = 4820.0,
            goal = 2000,
            unit = "kcal",
            isEnergy = true,
            fontScale = 1.3f,
        )
        composeRule.onNodeWithText("+2820").assertVisibleSingleLine("energy delta 1.3x")
        composeRule.onNodeWithText("4820").assertVisibleSingleLine("energy value 1.3x")
    }

    @Test
    fun nonEnergyStress_deltaAndValue_staySingleLineAt69dp() {
        // mg nutrients render larger raw numbers (3840 vs a 7280 mg goal).
        setBandCard(
            label = "Sodium",
            shortLabel = "Sodium",
            current = 3840.0,
            goal = 7280,
            unit = "mg",
        )
        composeRule.onNodeWithText("−3440").assertVisibleSingleLine("sodium delta")
        composeRule.onNodeWithText("3840").assertVisibleSingleLine("sodium value")
    }

    @Test
    fun floorMet_showsCheckGlyph() {
        setBandCard(
            label = "Fat",
            shortLabel = "F",
            current = 95.0,
            goal = 80,
            unit = "g",
            fatFloor = true,
        )
        composeRule.onNodeWithText("✓").assertVisibleSingleLine("floor met glyph")
    }

    @Test
    fun noGoal_hidesStatus_showsValue() {
        setBandCard(
            label = "Fat",
            shortLabel = "F",
            current = 95.0,
            goal = 0,
            unit = "g",
        )
        composeRule.onNodeWithText("95").assertVisibleSingleLine("no-goal value")
        composeRule.onNodeWithText("✓").assertDoesNotExist()
        composeRule.onNodeWithText("−95").assertDoesNotExist()
        composeRule.onNodeWithText("+95").assertDoesNotExist()
    }

    @Test
    fun slimLine_glyphRow_staysSingleLineAt69dp() {
        // Slim packs label + unit + value + status on ONE line: only the glyph
        // vocabulary keeps that inside ~69dp at 4 cards.
        setBandCard(
            label = "Calories",
            shortLabel = "Cal",
            current = 4820.0,
            goal = 2000,
            unit = "kcal",
            isEnergy = true,
            layout = HomeMacroCardLayout.SLIM_LINE,
        )
        composeRule.onNodeWithText("+2820").assertVisibleSingleLine("slim delta")
        composeRule.onNodeWithText("4820").assertVisibleSingleLine("slim value")
    }

    @Test
    fun slimLine_fullNameMode_nameYieldsButSurvives() {
        // Full-name toggle at the 4-card width: the capped label ellipsizes to
        // a sliver but never collapses to 0x0, and value + status stay
        // single-line. (Shorter numbers leave the name more room.)
        setBandCard(
            label = "Carbs",
            shortLabel = "Kohlenhydrate",
            current = 204.0,
            goal = 248,
            unit = "g",
            layout = HomeMacroCardLayout.SLIM_LINE,
        )
        composeRule.onNodeWithText("Kohlenhydrate").assertVisibleSingleLine("slim full name")
        composeRule.onNodeWithText("204").assertVisibleSingleLine("slim full-name value")
        composeRule.onNodeWithText("−44").assertVisibleSingleLine("slim full-name delta")
    }
}
