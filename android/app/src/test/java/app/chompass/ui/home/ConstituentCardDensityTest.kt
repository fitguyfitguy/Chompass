package app.chompass.ui.home

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.models.FoodConstituent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Collapsed ingredient cards must fit three name-to-disclosure spans in the
 * Edit Food feature-shot viewport. Font scale 1.0 only.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w411dp-h914dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConstituentCardDensityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun threeCollapsedCardsFitTheFeatureShotFold() {
        composeRule.setContent {
            MaterialTheme {
                ConstituentsSection(
                    rows = listOf(
                        row("Chicken thigh", 150.0, "🍗"),
                        row("Brown rice", 200.0, "🍚"),
                        row("Roasted broccoli", 120.0, "🥦"),
                        row("", 50.0, null),
                    ),
                    expanded = true,
                    onExpandedChange = {},
                    onRowsChange = {},
                    modifier = Modifier.width(371.dp),
                )
            }
        }

        val detailed = composeRule.onAllNodesWithText("Detailed Nutrition")
        assert(detailed.fetchSemanticsNodes().size == 4) {
            "expected 4 Detailed Nutrition nodes, found ${detailed.fetchSemanticsNodes().size}"
        }
        val spanPx = detailed[2].fetchSemanticsNode().boundsInRoot.bottom -
            composeRule.onNodeWithText("Chicken thigh").fetchSemanticsNode().boundsInRoot.top
        val limitPx = with(composeRule.density) { 540.dp.toPx() }
        assert(spanPx <= limitPx) {
            val spanDp = with(composeRule.density) { spanPx.toDp().value }
            "third Detailed Nutrition bottom is ${spanDp}dp from Chicken thigh top; limit 540dp"
        }

        val nameNodes = composeRule.onAllNodesWithText("Name").fetchSemanticsNodes()
        assert(nameNodes.size == 1) {
            "filled rows must not show a Name label; found ${nameNodes.size}"
        }

        val removeNodes = composeRule.onAllNodesWithContentDescription("Remove ingredient")
        val removeHeight = removeNodes[0].fetchSemanticsNode().boundsInRoot.height
        val minTargetPx = with(composeRule.density) { 48.dp.toPx() }
        assert(removeHeight >= minTargetPx) {
            val heightDp = with(composeRule.density) { removeHeight.toDp().value }
            "remove target is ${heightDp}dp; minimum 48dp"
        }

        composeRule.onNodeWithText("Add ingredient").assertIsDisplayed()
        val quantityNodes = composeRule.onAllNodesWithText("Quantity").fetchSemanticsNodes()
        assert(quantityNodes.size == 4) {
            "expected 4 Quantity cards, found ${quantityNodes.size}"
        }
    }

    private fun row(name: String, grams: Double, emoji: String?) = FoodConstituent(
        name = name,
        calories = 0,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        servingSizeGrams = grams,
        emoji = emoji,
        fiber = 1.0,
    )
}
