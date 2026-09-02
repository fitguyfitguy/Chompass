package app.chompass.ui.home

import app.chompass.R
import app.chompass.models.DailyNote
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Home card for the selected day's note (Codeberg #58a). Empty days show a
 * muted affordance row; days with a note show a compact preview. Tap opens
 * [DailyNoteSheet]. Day-scoped like the water card, so it works for any
 * selected day, not just today.
 */
@Composable
internal fun DailyNoteCard(
    note: String?,
    onClick: () -> Unit,
) {
    if (note.isNullOrBlank()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.width(18.dp),
            )
            Text(
                stringResource(R.string.home_note_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable(onClick = onClick)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.note_edit_title),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .width(18.dp),
            )
        }
    }
}

/**
 * Day-note editor (Codeberg #58a). Multi-line plain-text field with a length
 * counter; Save commits (an empty field clears), Clear removes the day's note.
 * Follows [ContextNoteSheet]'s keyboard-safe sheet layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DailyNoteSheet(
    day: LocalDate,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    val sheetState = rememberChompassSheetState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val dayLabel = remember(day) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(day)
    }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        // Keyboard-safe: the footer pads itself; zero the chrome insets (same
        // reasoning as ContextNoteSheet — no wiggle while the IME opens).
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
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
                    stringResource(R.string.note_edit_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.width(72.dp))
            }

            // A long note (or the open keyboard) used to grow the sheet past
            // the screen and push the Clear/Save footer out of reach (#84
            // class). The note column scrolls: weight = remaining height
            // after toolbar + footer are measured, fill=false so the sheet
            // stays compact for short notes (WaterQuickPresetsSheet pattern).
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )

                FudGlassTextField(
                    value = text,
                    onValueChange = { if (it.length <= DailyNote.MAX_TEXT_LENGTH) text = it },
                    placeholder = stringResource(R.string.note_placeholder),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .focusRequester(focusRequester),
                )

                Text(
                    stringResource(R.string.note_char_count, text.length, DailyNote.MAX_TEXT_LENGTH),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                    modifier = Modifier.align(Alignment.End),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (initialText.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            onClear()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
                Button(
                    onClick = {
                        onSave(text)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Calorie),
                ) {
                    Text(
                        stringResource(R.string.action_save),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
