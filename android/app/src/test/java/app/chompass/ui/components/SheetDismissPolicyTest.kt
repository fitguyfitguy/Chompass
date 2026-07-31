package app.chompass.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for modal sheet drag-to-dismiss policy.
 *
 * Permanently rejecting [SheetValue.Hidden] blocks Material3 downward drag
 * while still showing a drag handle (the Edit / Recipe / Copy-from-day bug).
 * Hide may only be refused while a transient operation is busy.
 */
@OptIn(ExperimentalMaterial3Api::class)
class SheetDismissPolicyTest {

    @Test
    fun allowsHide_whenIdle() {
        assertTrue(allowsSheetHide(SheetValue.Hidden, busy = false))
        assertTrue(allowsSheetHide(SheetValue.Expanded, busy = false))
        assertTrue(allowsSheetHide(SheetValue.PartiallyExpanded, busy = false))
    }

    @Test
    fun blocksOnlyHide_whenBusy() {
        assertFalse(allowsSheetHide(SheetValue.Hidden, busy = true))
        assertTrue(allowsSheetHide(SheetValue.Expanded, busy = true))
        assertTrue(allowsSheetHide(SheetValue.PartiallyExpanded, busy = true))
    }
}
