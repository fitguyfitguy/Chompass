package org.codeberg.fitguy.nofud.ui.home

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.ui.components.FudGlassPrimaryButton
import org.codeberg.fitguy.nofud.ui.components.FudGlassTextField
import org.codeberg.fitguy.nofud.ui.theme.AppColors

/**
 * Optional grounded entry: text, photo, or photo+text. Nutrients are resolved
 * from USDA / Open Food Facts / history after model recognition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroundedEntrySheet(
    onDismiss: () -> Unit,
    onSubmit: (description: String?, imageBytes: ByteArray?) -> Unit,
    isSubmitting: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var submitted by remember { mutableStateOf(false) }
    val busy = isSubmitting || submitted

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { imageBytes = it }
    }

    val canSubmit = !busy && (note.isNotBlank() || imageBytes != null)

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.grounded_entry_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.grounded_entry_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            FudGlassTextField(
                value = note,
                onValueChange = { if (!busy) note = it },
                placeholder = stringResource(R.string.grounded_entry_placeholder),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 3,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FudGlassPrimaryButton(
                    text = stringResource(R.string.grounded_entry_add_photo),
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }

            imageBytes?.let { bytes ->
                Spacer(Modifier.height(12.dp))
                val bitmap = remember(bytes) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                Box {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        }
                    }
                    IconButton(
                        onClick = { if (!busy) imageBytes = null },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = AppColors.Calorie,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            FudGlassPrimaryButton(
                text = stringResource(R.string.grounded_entry_analyze),
                onClick = {
                    if (!canSubmit) return@FudGlassPrimaryButton
                    submitted = true
                    onSubmit(
                        note.trim().takeIf { it.isNotEmpty() },
                        imageBytes,
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
