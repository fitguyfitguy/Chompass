package app.chompass.ui.home

import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.rememberChompassSheetState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import app.chompass.R
import app.chompass.models.ServingUnitOption
import app.chompass.services.FoodPhotoSession
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.theme.AppColors

/**
 * Intermediate sheet after a photo is captured or picked. Shows the image and an
 * optional note field before sending to the AI. Also offers a second photo for
 * food + nutrition-label composites, plus an optional exact total-weight field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextNoteSheet(
    imageBytes: ByteArray,
    initialNote: String = "",
    initialConfirmedPortionGrams: Double? = null,
    isSubmitting: Boolean = false,
    onAnalyze: (note: String, confirmedPortionGrams: Double?) -> Unit,
    onAddPhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember(initialNote) { mutableStateOf(initialNote) }
    var weightText by remember(initialConfirmedPortionGrams) {
        mutableStateOf(
            initialConfirmedPortionGrams
                ?.takeIf { it > 0 }
                ?.let { ServingUnitOption.formatQuantity(it) }
                .orEmpty()
        )
    }
    var submitted by remember { mutableStateOf(false) }
    val busy = isSubmitting || submitted
    val state = rememberChompassSheetState(busy = busy)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val bitmap = remember(imageBytes) {
        android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    ChompassBottomSheet(
        onDismiss = { if (!busy) onDismiss() },
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
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
                TextButton(onClick = { if (!busy) onDismiss() }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = AppColors.Calorie,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                }
                Text(
                    stringResource(R.string.context_note_title),
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

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(20.dp))
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SheetSectionHeader(stringResource(R.string.context_note_section))
                    var showGuide by remember { mutableStateOf(false) }
                    PhotoAccuracyInfoButton(onClick = { showGuide = true })
                    if (showGuide) {
                        PhotoAccuracyGuideDialog(onDismiss = { showGuide = false })
                    }
                }

                Text(
                    stringResource(R.string.context_note_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                FudGlassTextField(
                    value = note,
                    onValueChange = { if (!busy) note = it },
                    placeholder = stringResource(R.string.context_note_placeholder),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp)
                        .focusRequester(focusRequester)
                )

                SheetSectionHeader(stringResource(R.string.context_note_weight_section))
                Text(
                    stringResource(R.string.context_note_weight_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FudGlassTextField(
                        value = weightText,
                        onValueChange = { if (!busy) weightText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        placeholder = stringResource(R.string.context_note_weight_placeholder),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.context_note_weight_unit),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
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
                OutlinedButton(
                    onClick = onAddPhoto,
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.AddAPhoto,
                        contentDescription = null,
                        tint = AppColors.Calorie,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.add_food_add_photo),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                }
                FudGlassPrimaryButton(
                    text = stringResource(R.string.action_analyze),
                    onClick = {
                        if (!busy) {
                            submitted = true
                            onAnalyze(note, parsePositiveGrams(weightText))
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    height = 48.dp,
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
        }
    }
}

/**
 * Lightweight pre-LLM staging only — never starts analysis by itself.
 * Photos first, then an optional tip note; Analyze asks for confirmation when
 * the note is empty and/or there is only one photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPhotoCaptureSheet(
    imageBytesList: List<ByteArray>,
    addsFromLibrary: Boolean,
    showScaleTip: Boolean = false,
    /** When true, expand the note field by default (until user opts out in settings). */
    requireNote: Boolean = true,
    /** After several empty-note analyzes, offer a persistent opt-out checkbox. */
    showDontAskAgain: Boolean = false,
    /** First N photo Analyzes: show the prominent accuracy tip card. */
    showAccuracyGuide: Boolean = false,
    onAddPhoto: () -> Unit,
    onRemove: (Int) -> Unit,
    onAnalyze: (note: String?, confirmedPortionGrams: Double?, dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var tipExpanded by remember(requireNote) { mutableStateOf(requireNote) }
    var dontAskAgain by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf(false) }

    fun commitAnalyze() {
        val trimmed = note.trim()
        onAnalyze(
            trimmed.takeIf { it.isNotEmpty() },
            parsePositiveGrams(weightText),
            dontAskAgain && showDontAskAgain,
        )
    }

    fun requestAnalyze() {
        val trimmed = note.trim()
        if (needsAnalyzeConfirm(requireNote, trimmed.isEmpty(), imageBytesList.size)) {
            pendingConfirm = true
        } else {
            commitAnalyze()
        }
    }

    val confirmMessage = when {
        note.isBlank() && imageBytesList.size < 2 ->
            stringResource(R.string.meal_photos_confirm_sparse)
        note.isBlank() ->
            stringResource(R.string.meal_photos_confirm_no_note)
        else ->
            stringResource(R.string.meal_photos_confirm_one_photo)
    }

    if (pendingConfirm) {
        AlertDialog(
            onDismissRequest = { pendingConfirm = false },
            title = { Text(stringResource(R.string.meal_photos_confirm_title)) },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingConfirm = false
                        commitAnalyze()
                    },
                ) {
                    Text(stringResource(R.string.meal_photos_confirm_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = false }) {
                    Text(stringResource(R.string.meal_photos_confirm_back))
                }
            },
        )
    }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.meal_photos_title),
                onCancel = onDismiss,
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(
                        if (requireNote) R.string.meal_photos_subtitle
                        else R.string.meal_photos_subtitle_note_optional,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                if (showScaleTip) {
                    Text(
                        stringResource(R.string.progressive_meal_scale_tip),
                        fontSize = 14.sp,
                        color = AppColors.Calorie,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.meal_photos_count, imageBytesList.size),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                ) {
                    itemsIndexed(imageBytesList, key = { index, bytes -> "$index-${bytes.size}" }) { index, bytes ->
                        val bitmap = remember(bytes) { decodePreviewBitmap(bytes) }
                        Box {
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.meal_photo_cd, index + 1),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(width = 160.dp, height = 180.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                )
                            }
                            IconButton(
                                onClick = { onRemove(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.62f), CircleShape),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_remove),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                    if (imageBytesList.size < FoodPhotoSession.MAX_IMAGES) {
                        item(key = "add-photo") {
                            OutlinedButton(
                                onClick = onAddPhoto,
                                modifier = Modifier.size(width = 120.dp, height = 180.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        if (addsFromLibrary) Icons.Filled.PhotoLibrary else Icons.Filled.AddAPhoto,
                                        contentDescription = null,
                                        tint = AppColors.Calorie,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Text(
                                        stringResource(
                                            if (addsFromLibrary) R.string.meal_photos_add_from_library
                                            else if (imageBytesList.size == 1) R.string.meal_photos_add_label
                                            else R.string.meal_photos_add_photo,
                                        ),
                                        color = AppColors.Calorie,
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (requireNote || tipExpanded) {
                        PhotoAccuracyGuide(
                            showProminentCard = showAccuracyGuide,
                            sectionTitle = stringResource(R.string.meal_photos_note_section),
                        )
                        Text(
                            stringResource(R.string.context_note_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        FudGlassTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 88.dp),
                            placeholder = stringResource(R.string.context_note_placeholder),
                        )
                        Text(
                            stringResource(R.string.context_note_weight_section),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FudGlassTextField(
                                value = weightText,
                                onValueChange = {
                                    weightText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                                },
                                placeholder = stringResource(R.string.context_note_weight_placeholder),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.context_note_weight_unit),
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        if (showDontAskAgain) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = dontAskAgain,
                                    onCheckedChange = { dontAskAgain = it },
                                )
                                Text(
                                    stringResource(R.string.meal_photos_dont_ask_note),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    } else {
                        TextButton(onClick = { tipExpanded = true }) {
                            Text(
                                stringResource(R.string.entry_analysis_tip_cta),
                                color = AppColors.Calorie,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            SheetStickyPrimaryBar(
                primaryLabel = stringResource(R.string.action_analyze),
                primaryEnabled = true,
                onPrimary = { requestAnalyze() },
            )
        }
    }
}

/** Parse a user-entered grams string; blank or non-positive → null. */
internal fun parsePositiveGrams(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

/**
 * Analyze gate for the photo staging sheet. Turning off the “Ask for a photo
 * note” setting also disables the empty-note / single-photo confirmation, so
 * Analyze runs straight through. With the setting on, the confirmation still
 * appears for a blank note and/or a single photo.
 */
internal fun needsAnalyzeConfirm(requireNote: Boolean, noteBlank: Boolean, imageCount: Int): Boolean =
    requireNote && (noteBlank || imageCount < 2)

private fun decodePreviewBitmap(bytes: ByteArray): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 720) sample *= 2
    return android.graphics.BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
    )
}
