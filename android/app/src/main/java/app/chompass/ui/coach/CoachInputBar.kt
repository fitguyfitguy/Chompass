package app.chompass.ui.coach

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.services.decodeSampledBitmap
import app.chompass.ui.navigation.BottomNavDockedControlPadding
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.components.isDarkTheme

/**
 * Horizontal scrolling chips. Verbatim port of `promptChips`.
 *   ScrollView(.horizontal) HStack spacing 8
 *     Capsule (translucent surface + subtle accent tint + hairline stroke)
 *     padding 14h × 9v, footnote rounded medium, calorie text
 */
@Composable
internal fun PromptChipRow(chips: List<String>, enabled: Boolean, onTap: (String) -> Unit) {
    if (chips.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips) { chip -> PromptChip(chip, enabled, onTap) }
    }
}

@Composable
private fun PromptChip(text: String, enabled: Boolean, onTap: (String) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val isDark = isDarkTheme()
    val borderColor = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    Box(
        Modifier
            .clip(shape)
            .background(if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight)
            .border(0.5.dp, borderColor, shape)
            .clickable(enabled = enabled) { onTap(text) }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Calorie
        )
    }
}

/**
 * Capsule input bar. Verbatim port of `inputBar`.
 *   capsule containing TextField + 34dp gradient send button
 *   translucent fill + hairline stroke + soft shadow
 *   send: arrow.up icon, 16sp bold, white-on-gradient when canSend, gray otherwise
 */
@Composable
internal fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    attachedImageBytes: ByteArray?,
    sending: Boolean,
    onPickImage: () -> Unit,
    onCaptureImage: () -> Unit,
    voice: CoachVoiceController,
    onRemoveImage: () -> Unit,
    onSend: () -> Unit
) {
    val canSend = !sending && (value.trim().isNotEmpty() || attachedImageBytes != null)
    val capsule = MaterialTheme.shapes.extraLarge

    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(capsule)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 4.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
    ) {
        attachedImageBytes?.let { bytes ->
            val bitmap = rememberDecodedBitmap(bytes) { decodeSampledBitmap(bytes) }
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp)
                        .size(width = 88.dp, height = 70.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_remove_image), tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (voice.phase != VoicePhase.Idle) {
                // Recording: the media pill + text field are replaced by the live
                // recording indicator (timer + slide-to-cancel hint / live text).
                CoachRecordingIndicator(voice, Modifier.weight(1f))
            } else {
                CoachMediaActions(
                    enabled = !sending,
                    onPickImage = onPickImage,
                    onCaptureImage = onCaptureImage
                )

                Box(Modifier.weight(1f).padding(horizontal = 2.dp, vertical = 8.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.coach_input_placeholder),
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Faint)
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Trailing control. Keep the mic at a stable call site (the else branch)
            // so a held press survives the left region swapping to the indicator.
            when {
                voice.phase == VoicePhase.Locked -> {
                    CoachVoiceCancelButton { voice.cancel() }
                    SendButton(canSend = true) { voice.stopAndSend() }
                }
                voice.phase == VoicePhase.Transcribing -> Unit
                canSend -> SendButton(canSend = canSend, onClick = onSend)
                else -> CoachMicButton(voice)
            }
        }
    }
}

@Composable
internal fun CoachMediaActions(
    enabled: Boolean,
    onPickImage: () -> Unit,
    onCaptureImage: () -> Unit
) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(AppColors.Calorie.copy(alpha = 0.075f))
            .border(
                0.6.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.16f),
                        AppColors.Calorie.copy(alpha = 0.12f)
                    )
                ),
                shape
            )
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoachMediaActionButton(
            icon = Icons.Filled.PhotoLibrary,
            contentDescription = stringResource(R.string.cd_add_image),
            enabled = enabled,
            onClick = onPickImage
        )
        CoachMediaActionButton(
            icon = Icons.Filled.CameraAlt,
            contentDescription = stringResource(R.string.cd_open_camera),
            enabled = enabled,
            onClick = onCaptureImage
        )
    }
}

@Composable
private fun CoachMediaActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                if (enabled) AppColors.Calorie.copy(alpha = 0.11f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun SendButton(canSend: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilledIconButton(
        onClick = onClick,
        enabled = canSend,
        modifier = Modifier.size(40.dp),
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            Icons.Filled.ArrowUpward,
            contentDescription = stringResource(R.string.coach_send_a11y),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun CoachInputBarPreviewStub(value: String, sending: Boolean) {
    val capsule = MaterialTheme.shapes.extraLarge
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(capsule)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = value.ifEmpty { stringResource(R.string.coach_input_placeholder) },
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            color = if (value.isEmpty()) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Faint)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        // Same production send button (audit 2.8: the stub used to re-paint a
        // gradient capsule that drifted from the real M3 FilledIconButton).
        SendButton(canSend = value.isNotBlank() && !sending, onClick = {})
    }
}

/** Static coach layout for release screenshot previews (no microphone / camera). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CoachScreenPreviewContent(
    ui: CoachUiState,
    input: String = "",
) {
    val listState = rememberLazyListState()
    val resolvedChips = ui.suggestions.map { stringResource(it) }
    val resolvedError = ui.error ?: ui.errorRes?.let { stringResource(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.coach_title), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Replay,
                            contentDescription = stringResource(R.string.coach_reset_chat_a11y),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(bottom = BottomNavDockedControlPadding),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (ui.messages.isEmpty()) {
                    EmptyState(modifier = Modifier.fillMaxSize())
                } else {
                    MessageList(
                        messages = ui.messages,
                        sending = ui.sending,
                        error = resolvedError,
                        listState = listState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            PromptChipRow(chips = resolvedChips, enabled = !ui.sending, onTap = {})
            CoachInputBarPreviewStub(value = input, sending = ui.sending)
        }
    }
}
