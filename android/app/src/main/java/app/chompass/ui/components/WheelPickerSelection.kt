package app.chompass.ui.components

import androidx.compose.foundation.lazy.LazyListState

/**
 * One visible row in a wheel picker, in the same coordinate space as
 * [androidx.compose.foundation.lazy.LazyListLayoutInfo].
 */
internal data class CenteredItem(
    val index: Int,
    val offset: Int,
    val size: Int,
)

/**
 * Index of the visible item whose midpoint is closest to the viewport
 * midpoint. Null when nothing is visible or the viewport has no size.
 *
 * Wheel pickers snap a row to the center capsule; [androidx.compose.foundation.lazy.LazyListState.firstVisibleItemIndex]
 * is the wrong proxy once earlier rows are still on screen (the 3-item
 * unit wheel never lets the last row become first-visible).
 */
internal fun centeredItemIndex(
    visible: List<CenteredItem>,
    viewportStart: Int,
    viewportEnd: Int,
): Int? {
    if (visible.isEmpty() || viewportEnd <= viewportStart) return null
    val center = (viewportStart + viewportEnd) / 2
    return visible.minByOrNull { item ->
        kotlin.math.abs(item.offset + item.size / 2 - center)
    }?.index
}

internal fun LazyListState.centeredIndex(): Int? {
    val info = layoutInfo
    return centeredItemIndex(
        visible = info.visibleItemsInfo.map { CenteredItem(it.index, it.offset, it.size) },
        viewportStart = info.viewportStartOffset,
        viewportEnd = info.viewportEndOffset,
    )
}
