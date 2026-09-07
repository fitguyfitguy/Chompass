package app.chompass.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity

/**
 * Prompt history (Codeberg #53): a compact sheet listing previously saved
 * analysis prompts (distinct, newest first). Tapping one auto-fills the note
 * input it was opened from; the caller renders the fill in grey until edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptHistorySheet(
    prompts: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberChompassSheetState()
    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.prompt_history_title),
                onCancel = onDismiss,
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (prompts.isEmpty()) {
                    Text(
                        stringResource(R.string.prompt_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    prompts.forEachIndexed { index, prompt ->
                        if (index > 0) HorizontalDivider()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(prompt) }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                prompt,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.prompt_history_use),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.Calorie,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
