package app.chompass.ui

import android.app.Application
import app.chompass.ui.home.EntryAnalysisTipStrip
import app.chompass.ui.home.MealPhotoAddTile
import app.chompass.ui.settings.SettingRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import app.chompass.ui.home.SheetReviewToolbar
import app.chompass.ui.home.SheetStickyPrimaryBar
import app.chompass.ui.home.SheetToolbarPill
import app.chompass.ui.home.ProgressiveMealFooterRow
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
     * Assert this node's text renders on at most [maxLines] lines inside
     * non-zero bounds. Requires a text layout (Text/TextButton semantics).
     */
    private fun SemanticsNodeInteraction.assertVisibleMaxLines(label: String, maxLines: Int) {
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
        assert(lineCount in 1..maxLines) {
            "$label rendered on $lineCount lines (bounds=$bounds); chrome text must stay within $maxLines lines"
        }
    }

    private fun SemanticsNodeInteraction.assertVisibleSingleLine(label: String) =
        assertVisibleMaxLines(label, maxLines = 1)

    private fun setContentToolbar(fontScale: Float? = null) {
        composeRule.setContent {
            MaterialTheme {
                val toolbar: @Composable () -> Unit = {
                    SheetReviewToolbar(
                        title = deTitle,
                        onCancel = {},
                        secondaryLabel = deSecondary,
                        onSecondary = {},
                        primaryLabel = dePrimary,
                        onPrimary = {},
                    )
                }
                if (fontScale != null) {
                    DeviceConfigurationOverride(
                        DeviceConfigurationOverride.FontScale(fontScale)
                    ) { toolbar() }
                } else {
                    toolbar()
                }
            }
        }
    }

    @Test
    fun sheetReviewToolbar_germanActions_stayVisibleSingleLine() {
        setContentToolbar()
        composeRule.onNodeWithText("Cancel").assertVisibleSingleLine("toolbar cancel")
        composeRule.onNodeWithText(deSecondary).assertVisibleSingleLine("toolbar secondary")
        composeRule.onNodeWithText(dePrimary).assertVisibleSingleLine("toolbar primary")
    }

    /**
     * 1.3x is the large-font repo bar (device-pass checks in
     * PLAN_UI_STRING_FIT); 2.0x is the stress leg. The title and secondary
     * pill are designed to yield at large font scale; the primary CTA and
     * cancel must stay visible and single-line (the 0x0 collapse bug).
     */
    @Test
    fun sheetReviewToolbar_germanActions_fontScale130_primaryStaysVisible() {
        setContentToolbar(fontScale = 1.3f)
        composeRule.onNodeWithText("Cancel").assertVisibleSingleLine("toolbar cancel 1.3x")
        composeRule.onNodeWithText(dePrimary).assertVisibleSingleLine("toolbar primary 1.3x")
    }

    @Test
    fun sheetReviewToolbar_germanActions_fontScale200_primaryStaysVisible() {
        setContentToolbar(fontScale = 2f)
        composeRule.onNodeWithText("Cancel").assertVisibleSingleLine("toolbar cancel 2.0x")
        composeRule.onNodeWithText(dePrimary).assertVisibleSingleLine("toolbar primary 2.0x")
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

    @Test
    fun mealPhotoAddTile_germanLabel_staysWithinTwoLines() {
        // "Beschriftung hinzufügen" (meal_photos_add_label) stacked
        // letter-by-letter in the 4.3.0 device pass; the tile now wraps to at
        // most its 2 designed lines and never collapses.
        composeRule.setContent {
            MaterialTheme {
                MealPhotoAddTile(
                    label = "Beschriftung hinzufügen",
                    addsFromLibrary = false,
                    onAddPhoto = {},
                )
            }
        }
        composeRule.onNodeWithText("Beschriftung hinzufügen")
            .assertVisibleMaxLines("add-photo tile label", maxLines = 2)
    }

    /**
     * The result-sheet tip strip's two side-by-side actions (3.19 letter-stack
     * fix). Both buttons are resource-backed, so this leg runs with the de
     * qualifier to resolve the real German strings ("Tipp für die KI
     * hinzufügen (optional)" + "Foto hinzufügen").
     */
    @Test
    @Config(sdk = [34], application = Application::class, qualifiers = "de-w340dp-h720dp")
    fun entryAnalysisTipStrip_germanActions_stayVisibleSingleLine() {
        composeRule.setContent {
            MaterialTheme {
                EntryAnalysisTipStrip(
                    expanded = false,
                    onExpandedChange = {},
                    note = "",
                    onNoteChange = {},
                    weightText = "",
                    onWeightChange = {},
                    canOfferTip = true,
                    canAddPhoto = true,
                    onApplyTip = {},
                    onAddPhoto = {},
                )
            }
        }
        composeRule.onNodeWithText("Tipp für die KI hinzufügen (optional)")
            .assertVisibleSingleLine("tip strip CTA")
        composeRule.onNodeWithText("Foto hinzufügen")
            .assertVisibleSingleLine("tip strip add-photo")
    }

    @Test
    fun settingRow_germanStressValues_stayVisible() {
        // Mirrors SettingRowStressPreviewContent's worst row: long label +
        // very long value. Labels wrap to 2 lines, values ellipsize on one
        // line; neither may stack or collapse.
        composeRule.setContent {
            MaterialTheme {
                SettingRow(
                    label = "Sortierung des Ernährungsprotokolls",
                    value = "Frühstück → Mittagessen → Abendessen (neueste zuletzt)",
                    onClick = {},
                )
                SettingRow(
                    label = "Kalorienanzeige",
                    value = "Tipp: Aktiviere Health Connect, um die aktiven Kalorien der Uhr/des Smartphones anstelle der geschätzten Aktivität zu verwenden.",
                    onClick = {},
                )
            }
        }
        // Unmerged tree: the row is clickable and merges label+value into one
        // semantics node, whose first text layout would be the 2-line label.
        composeRule.onNodeWithText("Sortierung des Ernährungsprotokolls", useUnmergedTree = true)
            .assertVisibleMaxLines("settings sort label", maxLines = 2)
        composeRule.onNodeWithText("Frühstück → Mittagessen → Abendessen (neueste zuletzt)", useUnmergedTree = true)
            .assertVisibleSingleLine("settings sort value")
        composeRule.onNodeWithText("Tipp: Aktiviere Health Connect, um die aktiven Kalorien der Uhr/des Smartphones anstelle der geschätzten Aktivität zu verwenden.", useUnmergedTree = true)
            .assertVisibleSingleLine("settings calorie-mode value")
    }

    @Test
    @Config(sdk = [34], application = Application::class, qualifiers = "de-w340dp-h720dp")
    fun progressiveMealFooter_germanLogItems_staysVisible() {
        composeRule.setContent {
            MaterialTheme {
                ProgressiveMealFooterRow(
                    isSaving = false,
                    canLog = true,
                    named = false,
                    onAddAnother = {},
                    onLogMeal = {},
                )
            }
        }
        // Footer pills allow two lines (Codeberg #84). Gate collapse + stacking.
        composeRule.onNodeWithText("Zutaten loggen")
            .assertVisibleMaxLines("progressive log-items", maxLines = 2)
        composeRule.onNodeWithText("Eine weitere hinzufügen")
            .assertVisibleMaxLines("progressive add-another", maxLines = 2)
    }
}
