package app.chompass.ui.home

import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.blockSheetDragAtScrollEdges
import app.chompass.ui.components.rememberChompassSheetState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import app.chompass.R
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppTextOpacity

/**
 * Bottom sheet for text-only food logging (Note hero tile). Opens with the
 * keyboard focused so the user can type immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputSheet(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    isSubmitting: Boolean = false,
    /** Codeberg #53: previously saved analysis prompts for quick reuse. */
    recentPrompts: List<String> = emptyList(),
) {
    val isDark = isDarkTheme()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Codeberg #30: hoisted so the sheet-drag blocker can read it. This sheet
    // has no lazy list (blockSheetDragAtLazyListEdges never applied), so every
    // downward drag used to dismiss it mid-note.
    val scrollState = rememberScrollState()

    // Keep the input composable stable so rotating placeholder examples do not drop IME focus.
    val placeholders = listOf(
        stringResource(R.string.text_input_placeholder_1),
        stringResource(R.string.text_input_placeholder_2),
        stringResource(R.string.text_input_placeholder_3),
        stringResource(R.string.text_input_placeholder_4)
    )
    // Upstream #190: saveable so rotation keeps the typed draft and the busy state.
    var input by rememberSaveable { mutableStateOf("") }
    var placeholderIdx by rememberSaveable { mutableIntStateOf(0) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val busy = isSubmitting || submitted
    val sheetState = rememberChompassSheetState(busy = busy)
    // Grey auto-fill (device walk 2026-08-24): a picked history prompt renders
    // muted until the user edits it, so old prompts read as placeholders.
    var autofilled by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            if (input.isEmpty()) placeholderIdx = (placeholderIdx + 1) % placeholders.size
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val submit = {
        if (input.isNotBlank() && !busy) {
            submitted = true
            onSubmit(input.trim())
        }
    }

    ChompassBottomSheet(
        onDismiss = { if (!busy) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                // Codeberg #30: block drag-from-content dismissal outright. No
                // lazy list here, so a top-edge pull has no scroll to justify
                // it; #14's bottom-edge concern does not apply. Dismissal stays
                // on the handle, the scrim and Cancel.
                .blockSheetDragAtScrollEdges(scrollState)
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.text_input_title),
                onCancel = { if (!busy) onDismiss() },
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FudGlassTextField(
                    value = input,
                    onValueChange = { if (!busy) { input = it; autofilled = false } },
                    placeholder = placeholders[placeholderIdx],
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (autofilled) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ),
                )

                if (recentPrompts.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            keyboardController?.hide()
                            showHistory = true
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.prompt_history_button),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        )
                    }
                }

                if (showHistory) {
                    PromptHistorySheet(
                        prompts = recentPrompts,
                        onPick = { prompt ->
                            input = prompt
                            autofilled = true
                            showHistory = false
                        },
                        onDismiss = { showHistory = false },
                    )
                }

                FudGlassPrimaryButton(
                    text = stringResource(R.string.action_analyze),
                    onClick = submit,
                    enabled = input.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    content = if (busy) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        null
                    }
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
