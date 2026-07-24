package app.chompass.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import app.chompass.R
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.theme.AppColors

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
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Keep the input composable stable so rotating placeholder examples do not drop IME focus.
    val placeholders = listOf(
        stringResource(R.string.text_input_placeholder_1),
        stringResource(R.string.text_input_placeholder_2),
        stringResource(R.string.text_input_placeholder_3),
        stringResource(R.string.text_input_placeholder_4)
    )
    var input by remember { mutableStateOf("") }
    var placeholderIdx by remember { mutableIntStateOf(0) }
    var submitted by remember { mutableStateOf(false) }
    val busy = isSubmitting || submitted

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = AppColors.Calorie,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                }
                Text(
                    stringResource(R.string.text_input_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(72.dp))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FudGlassTextField(
                    value = input,
                    onValueChange = { if (!busy) input = it },
                    placeholder = placeholders[placeholderIdx],
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

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
