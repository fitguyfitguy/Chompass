package app.chompass.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.ui.home.SheetReviewToolbar
import app.chompass.ui.home.SheetStickyPrimaryBar
import app.chompass.ui.home.SheetToolbarPill
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Overflow gate for localized sheet chrome: action pills and toolbar labels
 * must stay visible and single-line at narrow widths, whatever the locale
 * string lengths are. Two failure shapes from the 4.3.0 device pass
 * ("Mahlzeit protokollieren" stacking letter-by-letter):
 *
 *  - letter stacking: text wraps one glyph per line when squeezed without
 *    wrap constraints (`lineCount` > 1)
 *  - collapse: a trailing sibling measures to 0x0 when earlier siblings
 *    consume the row (bounds width/height == 0, invisible CTA)
 *
 * The gate asserts on `lineCount` and node bounds, never absolute pixel
 * sizes, and never `hasVisualOverflow` (that flag is true for every
 * well-behaved ellipsized pill).
 *
 * Labels are passed as the known-longest German strings as literals:
 * `stringResource` in Robolectric resolves the default (en) locale, so
 * resource-based tests would never exercise the long translations.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w340dp-h720dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverflowGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val deTitle = "Mahlzeit erstellen"
    private val deSecondary = "Eine weitere hinzufügen"
    private val dePrimary = "Mahlzeit protokollieren"

    /**
     * Assert this node's text renders on exactly one line inside non-zero
     * bounds. Requires a text layout (Text/TextButton semantics).
     */
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
            "$label rendered on $lineCount lines (bounds=$bounds); chrome text must stay single-line"
        }
    }

    @Test
    fun sheetReviewToolbar_germanActions_stayVisibleSingleLine() {
        composeRule.setContent {
            MaterialTheme {
                SheetReviewToolbar(
                    title = deTitle,
                    onCancel = {},
                    secondaryLabel = deSecondary,
                    onSecondary = {},
                    primaryLabel = dePrimary,
                    onPrimary = {},
                )
            }
        }
        composeRule.onNodeWithText("Cancel").assertVisibleSingleLine("toolbar cancel")
        composeRule.onNodeWithText(deSecondary).assertVisibleSingleLine("toolbar secondary")
        composeRule.onNodeWithText(dePrimary).assertVisibleSingleLine("toolbar primary")
    }

    @Test
    fun sheetToolbarPill_squeezedBox_staysSingleLine() {
        // Pins the 4.3.0 fix: maxLines=1 + ellipsis means a hard-squeezed pill
        // truncates ("M...") instead of stacking one glyph per line.
        composeRule.setContent {
            MaterialTheme {
                Column {
                    Box(Modifier.width(48.dp)) {
                        SheetToolbarPill(label = dePrimary, onClick = {})
                    }
                    Box(Modifier.width(48.dp)) {
                        SheetToolbarPill(label = deSecondary, bold = true, onClick = {})
                    }
                }
            }
        }
        composeRule.onNodeWithText(dePrimary).assertVisibleSingleLine("pill regular")
        composeRule.onNodeWithText(deSecondary).assertVisibleSingleLine("pill bold")
    }

    @Test
    fun sheetStickyPrimaryBar_germanLabels_stayVisibleSingleLine() {
        composeRule.setContent {
            MaterialTheme {
                SheetStickyPrimaryBar(
                    primaryLabel = dePrimary,
                    onPrimary = {},
                    textActionLabel = deSecondary,
                    onTextAction = {},
                )
            }
        }
        composeRule.onNodeWithText(dePrimary).assertVisibleSingleLine("sticky primary")
        composeRule.onNodeWithText(deSecondary).assertVisibleSingleLine("sticky text action")
    }
}
