package app.chompass.ui.home

import androidx.compose.runtime.Composable

/** Release stub — grounded entry UI is compiled only for debug/test. */
@Composable
fun GroundedEntrySheet(
    onDismiss: () -> Unit,
    onSubmit: (description: String?, imageBytes: ByteArray?) -> Unit,
    isSubmitting: Boolean = false,
) {
    // No-op: GroundedEntryFeature.ENABLED is false in release.
    @Suppress("UNUSED_EXPRESSION")
    onDismiss to onSubmit to isSubmitting
}

/** Release stub — grounded candidate review UI is debug/test only. */
@Composable
fun GroundedCandidateSheet(
    review: PendingGroundedReview,
    onDismiss: () -> Unit,
    onConfirm: (selectedSourceIds: Map<Int, String>, gramOverrides: Map<Int, Double>) -> Unit,
    isSubmitting: Boolean = false,
) {
    @Suppress("UNUSED_EXPRESSION")
    review to onDismiss to onConfirm to isSubmitting
}
