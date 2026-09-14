package app.chompass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Add Food list's grow-on-scroll decision (no Android instrumentation).
 *
 * The rule the sheet depends on: only the user's own scrolling moves it. The
 * same inputs also change when the list grows or shrinks underneath them — a
 * search leg landing, the packaged-product opt-in going on or off — and none
 * of that is a request to resize the sheet.
 */
class AddFoodSheetGrowTest {
    @Test
    fun scrollingALongListAsksToExpand() {
        assertEquals(
            SheetGrow.EXPAND,
            sheetGrowAction(scrolled = true, worthExpanding = true),
        )
    }

    @Test
    fun backAtTheTopGivesTheSheetBack() {
        assertEquals(
            SheetGrow.COLLAPSE,
            sheetGrowAction(scrolled = false, worthExpanding = true),
        )
        assertEquals(
            SheetGrow.COLLAPSE,
            sheetGrowAction(scrolled = false, worthExpanding = false),
        )
    }

    @Test
    fun aListShrinkingUnderAScrolledUserLeavesTheSheetAlone() {
        // Toggling the packaged-product opt-in off drops its rows, which can
        // take the list under the expand threshold while the user is still
        // scrolled into it. That used to read as "collapse" and shut a sheet
        // the user had opened.
        assertEquals(
            SheetGrow.KEEP,
            sheetGrowAction(scrolled = true, worthExpanding = false),
        )
    }
}
