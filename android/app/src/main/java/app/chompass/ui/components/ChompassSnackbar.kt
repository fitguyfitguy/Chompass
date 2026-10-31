package app.chompass.ui.components

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Themed, swipe-dismissable [SnackbarHost]: surface background instead of the inverse default,
 * and left/right swipe dismisses like a timeout (`SnackbarResult.Dismissed`).
 *
 * Solid by design — it composes inside the `bottomBarHazeSource` Box in
 * ChompassNavHost, so a haze effect here would blur itself. Chrome still joins
 * the frosted family: 16dp corner (`shapes.large`) + hairline border.
 */
@Composable
fun ChompassSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        key(data) { // fresh swipe state per snackbar instance
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        data.dismiss()
                        true
                    } else {
                        false
                    }
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {},
            ) {
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    actionContentColor = MaterialTheme.colorScheme.primary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
