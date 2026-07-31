package app.chompass.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChompassBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberChompassSheetState(),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        shape = shape,
        content = content,
    )
}

/**
 * Default Chompass [SheetState]: fully expanded, dismissible by downward drag.
 *
 * @param busy when true, transitions to [SheetValue.Hidden] are rejected so a
 *   mid-flight save/reprocess cannot be aborted by a swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberChompassSheetState(
    busy: Boolean = false,
): SheetState {
    // SheetState keeps the confirmValueChange from first remember; always read
    // the latest busy flag via rememberUpdatedState.
    val busyState = rememberUpdatedState(busy)
    return rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> allowsSheetHide(target, busyState.value) },
    )
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
 * further. Use on long list sheets with competing horizontal row gestures;
 * omit on short review/edit sheets.
 */
@Composable
fun Modifier.blockSheetDragAtLazyListEdges(listState: LazyListState): Modifier {
    val connection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val shouldBlock =
                    (available.y > 0f && !listState.canScrollBackward) ||
                        (available.y < 0f && !listState.canScrollForward)
                return if (shouldBlock) Offset(0f, available.y) else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val shouldBlock =
                    (available.y > 0f && !listState.canScrollBackward) ||
                        (available.y < 0f && !listState.canScrollForward)
                return if (shouldBlock) Velocity(0f, available.y) else Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}
