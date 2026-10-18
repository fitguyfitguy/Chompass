package app.chompass.ui.settings

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.models.DayTargets
import app.chompass.models.GoalPhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * Layout gate for the goal-phase add/edit sheet (2026-10-18 device pass,
 * DEVICE_PASS_5_8_0 §2a): the wheel-heavy body filled the 941 dp window and
 * pushed `Delete phase` (edit mode) past the clip — the row kept zero visible
 * bounds through every scroll attempt, so a phase could never be deleted.
 *
 * The sheet body is always taller than a phone; the pinned footer
 * ([app.chompass.ui.components.ChompassPinnedFooterColumn]) must keep both
 * CTAs fully inside the sheet window.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "de-w411dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalPhaseSheetLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val editing = GoalPhase(
        name = "Pass",
        startDate = "2026-10-10",
        endDate = "2026-10-31",
        calories = 1685,
        proteinG = 150,
        carbsG = 200,
        fatG = 65,
    )

    private fun setSheet(editing: GoalPhase?, fontScale: Float? = null) {
        composeRule.setContent {
            val sheet: @Composable () -> Unit = {
                MaterialTheme {
                    GoalPhaseEditSheet(
                        editing = editing,
                        base = DayTargets(calories = 2000, proteinG = 150, carbsG = 200, fatG = 65),
                        calorieFloor = 1500,
                        calorieCeiling = 6000,
                        onSave = { _, _ -> },
                        onDelete = {},
                        onDismiss = {},
                    )
                }
            }
            if (fontScale != null) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    sheet()
                }
            } else {
                sheet()
            }
        }
    }

    private fun node(text: String): SemanticsNodeInteraction =
        composeRule.onNodeWithText(text)

    /** Bounds fully inside the tree's window root, non-zero size. */
    private fun SemanticsNodeInteraction.assertInsideWindow(label: String): SemanticsNode {
        val node = fetchSemanticsNode()
        val bounds = node.boundsInRoot
        var root = node
        while (root.parent != null) root = root.parent!!
        val window = root.boundsInRoot
        assert(bounds.width > 0f && bounds.height > 0f) {
            "$label collapsed to ${bounds.width.roundToInt()}x${bounds.height.roundToInt()}"
        }
        assert(bounds.top >= window.top - 1f && bounds.bottom <= window.bottom + 1f) {
            "$label is clipped: node=[${bounds.top.roundToInt()}..${bounds.bottom.roundToInt()}] " +
                "window=[${window.top.roundToInt()}..${window.bottom.roundToInt()}]"
        }
        return node
    }

    /**
     * Both CTAs live in the pinned footer, so both must clear the window — and
     * the sheet itself must stay capped inside it (the device clip put the
     * sheet bottom past the screen edge, so `Save` was cut and `Delete phase`
     * measured 0x0 through every scroll attempt).
     */
    private fun assertFooterPinned(deleteLabel: String?) {
        val save = node("Speichern").assertIsDisplayed().assertInsideWindow("save CTA")
        if (deleteLabel != null) {
            node(deleteLabel).assertIsDisplayed().assertInsideWindow("delete action")
        }

        val window = save.let { n -> var r = n; while (r.parent != null) r = r.parent!!; r.boundsInRoot }
        // The sheet surface is the deepest ancestor that does NOT fill the
        // window: a capped sheet leaves headroom, an overflowing one does not.
        var ancestor: SemanticsNode? = save.parent
        var sheetBounds: Rect? = null
        while (ancestor != null) {
            val bounds = ancestor.boundsInRoot
            if (bounds.height < window.height - 1f) {
                sheetBounds = bounds
                break
            }
            ancestor = ancestor.parent
        }
        assert(sheetBounds != null) {
            "sheet fills the window (${window.height.roundToInt()} dp): a tall body must be capped " +
                "so the pinned footer stays reachable"
        }
        assert(sheetBounds!!.bottom <= window.bottom + 1f) {
            "sheet bottom ${sheetBounds.bottom.roundToInt()} exceeds window bottom ${window.bottom.roundToInt()}"
        }
    }

    @Test
    fun addMode_saveStaysInsideSheet() {
        setSheet(editing = null)
        assertFooterPinned(deleteLabel = null)
    }

    @Test
    fun editMode_saveAndDeleteStayInsideSheet() {
        setSheet(editing = editing)
        assertFooterPinned(deleteLabel = "Phase löschen")
    }

    @Test
    fun editMode_fontScale130_footerStaysInsideSheet() {
        setSheet(editing = editing, fontScale = 1.3f)
        assertFooterPinned(deleteLabel = "Phase löschen")
    }
}
