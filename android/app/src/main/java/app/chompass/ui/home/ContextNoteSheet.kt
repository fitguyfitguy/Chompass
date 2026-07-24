package app.chompass.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.theme.AppColors

/**
 * Intermediate sheet after a photo is captured or picked. Shows the image and an
 * optional note field before sending to the AI. Also offers a second photo for
 * food + nutrition-label composites.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextNoteSheet(
    imageBytes: ByteArray,
    initialNote: String = "",
    isSubmitting: Boolean = false,
    onAnalyze: (note: String) -> Unit,
    onAddPhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by remember(initialNote) { mutableStateOf(initialNote) }
    var submitted by remember { mutableStateOf(false) }
    val busy = isSubmitting || submitted
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
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

                SheetSectionHeader(stringResource(R.string.context_note_section))

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
                            onAnalyze(note)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPhotoCaptureSheet(
    imageBytesList: List<ByteArray>,
    addsFromLibrary: Boolean,
    onAddPhoto: () -> Unit,
    onRemove: (Int) -> Unit,
    onAnalyze: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        SheetReviewToolbar(
            title = stringResource(R.string.meal_photos_title),
            primaryLabel = stringResource(R.string.action_analyze),
            onCancel = onDismiss,
            onPrimary = { onAnalyze(note.takeIf { it.isNotBlank() }) },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
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
                if (imageBytesList.size < 10) {
                    OutlinedButton(onClick = onAddPhoto) {
                        Icon(
                            if (addsFromLibrary) Icons.Filled.PhotoLibrary else Icons.Filled.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(
                                if (addsFromLibrary) R.string.meal_photos_add_from_library
                                else R.string.meal_photos_add_photo,
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
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
                                    .size(width = 240.dp, height = 260.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                            )
                        }
                        IconButton(
                            onClick = { onRemove(index) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(34.dp)
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
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.context_note_section),
                    fontWeight = FontWeight.SemiBold,
                )
                FudGlassTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp),
                    placeholder = stringResource(R.string.context_note_placeholder),
                )
            }
        }
    }
}

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
