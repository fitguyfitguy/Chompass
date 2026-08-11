package app.chompass.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge-selection logic for [blockSheetDragAtLazyListEdges].
 *
 * The blocker is direction-specific per edge: the top edge may keep
 * drag-from-content dismissal (blockTopEdge = false) while the bottom edge
 * still suppresses the overscroll vs drag-to-dismiss shake (blockBottomEdge).
 * Regression for the edit-food sheet (Codeberg #6): the original
 * direction-blind blocker consumed top-edge downward drags, making
 * drag-from-content dismissal sluggish.
 */
class SheetEdgeScrollBlockTest {

    @Test
    fun blocksBottomEdge_whenListCannotScrollForward() {
        // Finger up (negative delta) at the list end must be consumed.
        assertTrue(
            shouldBlockSheetDrag(
                availableY = -1f,
                canScrollBackward = true,
                canScrollForward = false,
                blockTopEdge = false,
                blockBottomEdge = true,
            )
        )
        assertTrue(
            shouldBlockSheetDrag(
                availableY = -100f,
                canScrollBackward = true,
                canScrollForward = false,
                blockTopEdge = true,
                blockBottomEdge = true,
            )
        )
    }

    @Test
    fun doesNotBlockBottomEdge_whenListCanScrollForward() {
        assertFalse(
            shouldBlockSheetDrag(
                availableY = -1f,
                canScrollBackward = true,
                canScrollForward = true,
                blockTopEdge = false,
                blockBottomEdge = true,
            )
        )
        // Downward drag at the bottom edge is a normal scroll back up.
        assertFalse(
            shouldBlockSheetDrag(
                availableY = 1f,
                canScrollBackward = true,
                canScrollForward = false,
                blockTopEdge = false,
                blockBottomEdge = true,
            )
        )
    }

    @Test
    fun blocksTopEdge_onlyWhenEnabled() {
        // Downward drag at the list start: blocked only with blockTopEdge.
        assertTrue(
            shouldBlockSheetDrag(
                availableY = 1f,
                canScrollBackward = false,
                canScrollForward = true,
                blockTopEdge = true,
                blockBottomEdge = true,
            )
        )
        assertFalse(
            shouldBlockSheetDrag(
                availableY = 1f,
                canScrollBackward = false,
                canScrollForward = true,
                blockTopEdge = false,
                blockBottomEdge = true,
            )
        )
        // Upward drag at the list start is a normal scroll toward the end.
        assertFalse(
            shouldBlockSheetDrag(
                availableY = -1f,
                canScrollBackward = false,
                canScrollForward = true,
                blockTopEdge = false,
                blockBottomEdge = true,
            )
        )
    }

    @Test
    fun doesNotBlockWhenListCanScrollInThatDirection() {
        // Middle of the list: neither edge blocks in either direction.
        assertFalse(
            shouldBlockSheetDrag(
                availableY = 1f,
                canScrollBackward = true,
                canScrollForward = true,
                blockTopEdge = true,
                blockBottomEdge = true,
            )
        )
        assertFalse(
            shouldBlockSheetDrag(
                availableY = -1f,
                canScrollBackward = true,
                canScrollForward = true,
                blockTopEdge = true,
                blockBottomEdge = true,
            )
        )
    }

    @Test
    fun zeroDeltaNeverBlocks() {
        assertFalse(
            shouldBlockSheetDrag(
                availableY = 0f,
                canScrollBackward = false,
                canScrollForward = false,
                blockTopEdge = true,
                blockBottomEdge = true,
            )
        )
    }
}
