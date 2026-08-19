package app.chompass.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Center-of-viewport selection for [WheelPicker] (Codeberg #42).
 *
 * Numbers match the 5-row / 44 dp wheel: viewport 0..220, center 110.
 */
class WheelPickerSelectionTest {
    @Test
    fun lastOfThree_whenClosestToCenter() {
        val visible = listOf(
            CenteredItem(index = 0, offset = 0, size = 44),
            CenteredItem(index = 1, offset = 44, size = 44),
            CenteredItem(index = 2, offset = 88, size = 44),
        )
        assertEquals(2, centeredItemIndex(visible, viewportStart = 0, viewportEnd = 220))
    }

    @Test
    fun firstOfThree_whenClosestToCenter() {
        val visible = listOf(
            CenteredItem(index = 0, offset = 88, size = 44),
            CenteredItem(index = 1, offset = 132, size = 44),
            CenteredItem(index = 2, offset = 176, size = 44),
        )
        assertEquals(0, centeredItemIndex(visible, viewportStart = 0, viewportEnd = 220))
    }

    @Test
    fun middleOfThree_whenClosestToCenter() {
        val visible = listOf(
            CenteredItem(index = 0, offset = 44, size = 44),
            CenteredItem(index = 1, offset = 88, size = 44),
            CenteredItem(index = 2, offset = 132, size = 44),
        )
        assertEquals(1, centeredItemIndex(visible, viewportStart = 0, viewportEnd = 220))
    }

    @Test
    fun emptyVisible_returnsNull() {
        assertNull(centeredItemIndex(emptyList(), viewportStart = 0, viewportEnd = 220))
    }

    @Test
    fun emptyViewport_returnsNull() {
        val visible = listOf(CenteredItem(index = 0, offset = 0, size = 44))
        assertNull(centeredItemIndex(visible, viewportStart = 0, viewportEnd = 0))
    }
}
