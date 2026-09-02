package app.chompass.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Shared modal bottom sheet.
 *
 * **Dismissal policy:** sheets are dismissible by downward drag (and scrim /
 * system back via [onDismissRequest]) by default. Never permanently reject
 * [SheetValue.Hidden] — that blocks the Material3 drag-to-close path while
 * still showing a drag handle. Only block hide while a transient operation is
 * in flight (saving, reprocessing); use [rememberChompassSheetState] with
 * `busy = true` or [allowsSheetHide].
 *
 * **Scrollable content:** long [androidx.compose.foundation.lazy.LazyColumn]
 * lists that compete with sheet drag (e.g. Saved Meals) should apply
 * [blockSheetDragAtLazyListEdges] so overscroll at list edges does not yank
 * the sheet. Do not apply that modifier to short review/edit sheets where
 * drag-from-content dismiss is expected.
 *
 * [contentWindowInsets] defaults to the M3 system-bars insets; sheets whose
 * content reads the bottom insets themselves (sticky footer with
 * navigationBarsPadding/imePadding) should pass `WindowInsets(0, 0, 0, 0)`
 * to avoid the M3 consumeWindowInsets(0,0,0,max(0,offset)) layout feedback
 * loop (see EditFoodEntrySheet, Codeberg #6).
 *
 * **Content height is not managed by the sheet.** [ModalBottomSheet] bounds
 * the content height but never scrolls it: Compose measures a `Column`'s
 * children sequentially, so a control placed after an uncapped growing region
 * (a list) is measured with no space left and laid out past the sheet clip —
 * unreachable by any gesture (Codeberg #84). Growing bodies need
 * [ChompassPinnedFooterSheet]/[ChompassPinnedFooterColumn] or an explicit
 * `weight`/`heightIn` cap on the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChompassBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberChompassSheetState(),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        shape = shape,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/**
 * Pinned-chrome sheet layout: [toolbar] and [footer] are measured first, the
 * [body] yields (Codeberg #84).
 *
 * Mechanics: inside a `Column`, non-weighted children ([toolbar], [footer])
 * claim their natural height first; the body Box gets `weight(1f,
 * fill = false)` = min(body content, remaining space). A scrollable body root
 * (`ChompassSheetLazyColumn` or a `verticalScroll` Column) scrolls when its
 * content exceeds the remainder, while the sheet stays compact when content
 * is short. `heightIn(max = maxHeight * [maxHeightFraction])` caps the sheet
 * at 92% of the available height (FoodResultSheet's `fillMaxHeight(0.92f)`
 * precedent) — the failure mode this prevents is a control placed after an
 * uncapped growing region being measured with no space and laid out past the
 * sheet clip, unreachable by any gesture (#84).
 *
 * New sheets with a pinned CTA use this instead of appending controls after a
 * list.
 */
@Composable
fun ChompassPinnedFooterColumn(
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.92f,
    toolbar: (@Composable () -> Unit)? = null,
    footer: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight * maxHeightFraction)
        ) {
            toolbar?.invoke()
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { body() }
            footer()
        }
    }
}

/**
 * [ChompassBottomSheet] hosting a [ChompassPinnedFooterColumn]: pinned
 * toolbar/footer, scrolling body, height capped at [maxHeightFraction] of the
 * available space. See [ChompassPinnedFooterColumn] for the layout invariant.
 *
 * The sheet host zeroes `contentWindowInsets`, so the footer must pad itself —
 * `.navigationBarsPadding()`, plus `.imePadding()` when the body has text
 * inputs (Codeberg #6/#14: default M3 insets plus a self-padding footer
 * create the consumeWindowInsets feedback loop).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChompassPinnedFooterSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberChompassSheetState(),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    maxHeightFraction: Float = 0.92f,
    toolbar: (@Composable () -> Unit)? = null,
    footer: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    ChompassBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        ChompassPinnedFooterColumn(
            modifier = Modifier,
            maxHeightFraction = maxHeightFraction,
            toolbar = toolbar,
            footer = footer,
            body = body,
        )
    }
}

/**
 * Default Chompass [SheetState]: fully expanded, dismissible by downward drag.
 *
 * @param busy when true, transitions to [SheetValue.Hidden] are rejected so a
 *   mid-flight save/reprocess cannot be aborted by a swipe.
 * @param positionalThreshold minimum downward drag (dp) before a slow release
 *   can settle to Hidden. m3 1.4's default (56.dp) means a slow release only
 *   dismisses once the sheet is within 56dp of fully hidden — the real
 *   dismissal sensitivity is [velocityThreshold]. Kept modest (120.dp) so a
 *   fixed value cannot backfire on short sheets (a large fixed threshold
 *   makes a short sheet dismiss on almost any pull).
 * @param velocityThreshold minimum fling velocity (dp/s) that dismisses. m3's
 *   default 125.dp/s is a gentle swipe — a normal scroll gesture at a list
 *   top exceeds it and dismisses. Raised to 1200.dp/s (maintainer decision
 *   2026-08-18, applied to every swipe-to-dismiss sheet) so only a deliberate
 *   flick dismisses; ordinary scrolls spring back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberChompassSheetState(
    busy: Boolean = false,
    positionalThreshold: Dp = 120.dp,
    velocityThreshold: Dp = 1200.dp,
): SheetState {
    // SheetState keeps the confirmValueChange from first remember; always read
    // the latest busy flag via rememberUpdatedState.
    val busyState = rememberUpdatedState(busy)
    val density = LocalDensity.current
    return remember(busyState, density, positionalThreshold, velocityThreshold) {
        // rememberSheetState is internal in m3 1.4; the public SheetState
        // constructor takes the thresholds as px lambdas (dp converted here).
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { with(density) { positionalThreshold.toPx() } },
            velocityThreshold = { with(density) { velocityThreshold.toPx() } },
            initialValue = SheetValue.Hidden,
            confirmValueChange = { target -> allowsSheetHide(target, busyState.value) },
            skipHiddenState = false,
        )
    }
}

/**
 * Whether a proposed sheet value should be accepted.
 * Hide is allowed unless [busy] (e.g. saving).
 */
@OptIn(ExperimentalMaterial3Api::class)
fun allowsSheetHide(target: SheetValue, busy: Boolean): Boolean =
    target != SheetValue.Hidden || !busy

/**
 * Consume vertical nested scroll/fling at LazyColumn edges so the parent
 * [ModalBottomSheet] does not steal the gesture when the list cannot scroll
 * further. Use on long list sheets with competing horizontal row gestures,
 * and on long edit/review sheets where the bottom edge would otherwise
 * fight the sheet's drag-to-dismiss (visible as a shake when scrolled to the
 * bottom). Omit on genuinely short sheets where drag-from-content dismiss
 * is wanted.
 *
 * Blocking is per-edge so a sheet can keep drag-from-content dismissal at
 * the top edge (finger down on content pulls the sheet) while still
 * suppressing the bottom-edge fight:
 *  - [blockTopEdge]: consume downward drags when the list cannot scroll back.
 *    When on, dismissal from list content needs the handle/scrim.
 *  - [blockBottomEdge]: consume upward drags when the list cannot scroll
 *    forward (the bottom-edge overscroll vs drag-to-dismiss shake).
 * Defaults preserve the original direction-blind behavior for existing
 * sheets.
 */
@Composable
fun Modifier.blockSheetDragAtLazyListEdges(
    listState: LazyListState,
    blockTopEdge: Boolean = true,
    blockBottomEdge: Boolean = true,
): Modifier {
    val connection = remember(listState, blockTopEdge, blockBottomEdge) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val shouldBlock = shouldBlockSheetDrag(
                    availableY = available.y,
                    canScrollBackward = listState.canScrollBackward,
                    canScrollForward = listState.canScrollForward,
                    blockTopEdge = blockTopEdge,
                    blockBottomEdge = blockBottomEdge,
                )
                return if (shouldBlock) Offset(0f, available.y) else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val shouldBlock = shouldBlockSheetDrag(
                    availableY = available.y,
                    canScrollBackward = listState.canScrollBackward,
                    canScrollForward = listState.canScrollForward,
                    blockTopEdge = blockTopEdge,
                    blockBottomEdge = blockBottomEdge,
                )
                return if (shouldBlock) Velocity(0f, available.y) else Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}

/**
 * Whether a user-input vertical scroll/fling delta at a LazyColumn edge
 * should be consumed instead of handing off to the parent sheet drag.
 * Direction-specific: only the configured edge whose list direction is
 * exhausted blocks.
 */
internal fun shouldBlockSheetDrag(
    availableY: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    blockTopEdge: Boolean,
    blockBottomEdge: Boolean,
): Boolean =
    (blockTopEdge && availableY > 0f && !canScrollBackward) ||
        (blockBottomEdge && availableY < 0f && !canScrollForward)

/**
 * Same edge-blocking as [blockSheetDragAtLazyListEdges] for plain
 * [androidx.compose.foundation.verticalScroll] columns (sheets without a lazy
 * list, e.g. TextInputSheet). Without it every downward drag on such a sheet
 * dismisses it, because there is no LazyColumn for
 * [blockSheetDragAtLazyListEdges] to attach to. Block both edges to reject
 * drag-from-content dismissal outright: a short non-lazy column gives no
 * scrollable justification for a top-edge pull, and #14's bottom-edge
 * shake concern does not apply without a lazy list. Dismissal stays on the
 * handle, the scrim and in-sheet actions.
 */
@Composable
fun Modifier.blockSheetDragAtScrollEdges(
    scrollState: ScrollState,
    blockTopEdge: Boolean = true,
    blockBottomEdge: Boolean = true,
): Modifier {
    val connection = remember(scrollState, blockTopEdge, blockBottomEdge) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val shouldBlock = shouldBlockSheetDrag(
                    availableY = available.y,
                    canScrollBackward = scrollState.canScrollBackward,
                    canScrollForward = scrollState.canScrollForward,
                    blockTopEdge = blockTopEdge,
                    blockBottomEdge = blockBottomEdge,
                )
                return if (shouldBlock) Offset(0f, available.y) else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val shouldBlock = shouldBlockSheetDrag(
                    availableY = available.y,
                    canScrollBackward = scrollState.canScrollBackward,
                    canScrollForward = scrollState.canScrollForward,
                    blockTopEdge = blockTopEdge,
                    blockBottomEdge = blockBottomEdge,
                )
                return if (shouldBlock) Velocity(0f, available.y) else Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}

/**
 * LazyColumn for sheet content with the sheet-drag edge blocking applied by
 * default.
 *
 * The recurring sheet-shake class (edit-food, review, photo sheets) came from
 * LazyColumn overscroll at list edges fighting the ModalBottomSheet
 * drag-to-dismiss gesture. The fix was an opt-in modifier
 * ([blockSheetDragAtLazyListEdges]) applied per sheet, so every new sheet
 * re-hit the bug. Use this wrapper for new sheet lists: it applies the
 * blocking automatically. Opt out per edge only when the sheet genuinely
 * wants drag-from-content dismissal at that edge (e.g. short review sheets
 * keep [blockTopEdge] = false so a finger on content still pulls the sheet).
 *
 * @param listState the list state (remembered by the caller so it can scroll
 *   programmatically, e.g. to a selected item).
 * @param blockTopEdge consume downward drags at the list start (default on;
 *   set false to keep drag-from-content dismissal at the top edge).
 * @param blockBottomEdge consume upward drags at the list end (default on;
 *   suppresses the bottom-edge overscroll vs drag-to-dismiss shake).
 */
@Composable
fun ChompassSheetLazyColumn(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    blockTopEdge: Boolean = true,
    blockBottomEdge: Boolean = true,
    verticalArrangement: androidx.compose.foundation.layout.Arrangement.Vertical = androidx.compose.foundation.layout.Arrangement.Top,
    horizontalAlignment: androidx.compose.ui.Alignment.Horizontal = androidx.compose.ui.Alignment.Start,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.blockSheetDragAtLazyListEdges(
            listState,
            blockTopEdge = blockTopEdge,
            blockBottomEdge = blockBottomEdge,
        ),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}
