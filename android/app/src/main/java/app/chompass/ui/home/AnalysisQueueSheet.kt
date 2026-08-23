package app.chompass.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.QueuedAnalysis
import app.chompass.models.QueueStatus
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.components.rememberQueueThumbnail
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/** Queue vs history tab of the analysis-queue sheet (Codeberg #53). */
enum class QueueTab { QUEUE, HISTORY }

/**
 * Analysis queue + prompt history (Codeberg #53): run failed / staged prompts
 * later (e.g. against a home-PC local HTTP model), edit their note / photos /
 * target day, and inspect what past prompts returned.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisQueueSheet(
    container: AppContainer,
    entries: List<QueuedAnalysis>,
    runningId: UUID?,
    onRun: (UUID) -> Unit,
    onRunAll: () -> Unit,
    onUpdate: (UUID, String?, Double?, LocalDate) -> Unit,
    onAddPhotos: (UUID, List<ByteArray>) -> Unit,
    onRemovePhoto: (UUID, String) -> Unit,
    onDelete: (UUID) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(QueueTab.QUEUE) }
    var editing by remember { mutableStateOf<QueuedAnalysis?>(null) }
    var confirmDelete by remember { mutableStateOf<QueuedAnalysis?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val busy = runningId != null
    val store = container.analysisQueue

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = rememberChompassSheetState(busy = busy),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.analysis_queue_title),
                onCancel = onDismiss,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                QueueTabs(selected = tab, onSelect = { tab = it })
                Spacer(Modifier.height(12.dp))
                when (tab) {
                    QueueTab.QUEUE -> {
                        val pending = entries.filter { it.status == QueueStatus.PENDING }
                        if (pending.isEmpty()) {
                            QueueEmptyState(
                                icon = Icons.Filled.Schedule,
                                text = stringResource(R.string.analysis_queue_empty_queue),
                            )
                        } else {
                            if (pending.size > 1) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = onRunAll, enabled = !busy) {
                                        Text(stringResource(R.string.analysis_queue_run_all))
                                    }
                                }
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(pending, key = { it.id }) { item ->
                                    QueueEntryRow(
                                        item = item,
                                        running = item.id == runningId,
                                        store = store,
                                        onRun = { onRun(item.id) },
                                        onEdit = { editing = item },
                                        onDelete = { confirmDelete = item },
                                    )
                                }
                            }
                        }
                    }
                    QueueTab.HISTORY -> {
                        val history = entries.filter { it.status != QueueStatus.PENDING }
                        if (history.isEmpty()) {
                            QueueEmptyState(
                                icon = Icons.Filled.History,
                                text = stringResource(R.string.analysis_queue_empty_history),
                            )
                        } else {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { confirmClearHistory = true }) {
                                    Text(stringResource(R.string.analysis_queue_clear_history))
                                }
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(history, key = { it.id }) { item ->
                                    HistoryEntryRow(
                                        item = item,
                                        running = item.id == runningId,
                                        store = store,
                                        onRun = { onRun(item.id) },
                                        onDelete = { confirmDelete = item },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        EditQueuedSheet(
            item = item,
            container = container,
            onAddPhotos = { bytes -> onAddPhotos(item.id, bytes) },
            onRemovePhoto = { filename -> onRemovePhoto(item.id, filename) },
            onSave = { note, grams, targetDate ->
                onUpdate(item.id, note, grams, targetDate)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    confirmDelete?.let { item ->
        FudGlassDialog(onDismissRequest = { confirmDelete = null }) {
            Text(
                stringResource(R.string.analysis_queue_delete_confirm),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_delete),
                onPrimary = {
                    onDelete(item.id)
                    confirmDelete = null
                },
                destructive = true,
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { confirmDelete = null },
            )
        }
    }

    if (confirmClearHistory) {
        FudGlassDialog(onDismissRequest = { confirmClearHistory = false }) {
            Text(
                stringResource(R.string.analysis_queue_clear_history_confirm),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.analysis_queue_clear_history),
                onPrimary = {
                    onClearHistory()
                    confirmClearHistory = false
                },
                destructive = true,
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { confirmClearHistory = false },
            )
        }
    }
}

// -- Tabs ------------------------------------------------------------------

@Composable
private fun QueueTabs(selected: QueueTab, onSelect: (QueueTab) -> Unit) {
    val isDark = isDarkTheme()
    val trackColor = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val trackBorder = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(trackColor)
            .border(0.5.dp, trackBorder, RoundedCornerShape(10.dp))
            .padding(2.dp)
    ) {
        for (t in QueueTab.values()) {
            val isSel = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSel) Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd))
                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onSelect(t) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (t) {
                        QueueTab.QUEUE -> stringResource(R.string.analysis_queue_tab_queue)
                        QueueTab.HISTORY -> stringResource(R.string.analysis_queue_tab_history)
                    },
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun QueueEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = AppColors.Calorie.copy(alpha = 0.4f),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// -- Rows ------------------------------------------------------------------

@Composable
private fun QueueEntryRow(
    item: QueuedAnalysis,
    running: Boolean,
    store: app.chompass.data.AnalysisQueueStore,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    QueueRowSurface {
        QueueThumbnail(item.imageFilenames.firstOrNull(), store)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                item.note?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.analysis_queue_photo_only),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                queueTimeCaption(item, stringResource(R.string.analysis_queue_on_day)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.error?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (running) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
            )
        } else {
            IconButton(onClick = onRun, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.analysis_queue_run_now),
                    tint = AppColors.Calorie,
                )
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.analysis_queue_edit),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HistoryEntryRow(
    item: QueuedAnalysis,
    running: Boolean,
    store: app.chompass.data.AnalysisQueueStore,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    QueueRowSurface {
        QueueThumbnail(item.imageFilenames.firstOrNull(), store)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val title = item.result?.name?.takeIf { it.isNotBlank() }
                ?: item.note?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.analysis_queue_photo_only)
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (item.status) {
                QueueStatus.DONE -> item.result?.let { result ->
                    Text(
                        stringResource(
                            R.string.analysis_queue_result,
                            queueTimeCaption(item, stringResource(R.string.analysis_queue_on_day)),
                            result.calories,
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                QueueStatus.FAILED -> Text(
                    item.error?.takeIf { it.isNotBlank() } ?: stringResource(R.string.analysis_queue_failed),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                QueueStatus.PENDING -> Unit
            }
        }
        if (running) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
        } else {
            IconButton(onClick = onRun, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.analysis_queue_re_run),
                    tint = AppColors.Calorie,
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun QueueRowSurface(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val isDark = isDarkTheme()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight)
            .border(
                0.5.dp,
                if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight,
                RoundedCornerShape(16.dp),
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun QueueThumbnail(filename: String?, store: app.chompass.data.AnalysisQueueStore) {
    val bitmap = rememberQueueThumbnail(filename, store)
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
        } else {
            Icon(
                Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Faint),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Relative time + optional target-day chip, e.g. "2 h ago · for Mon, Aug 24". */
@Composable
private fun queueTimeCaption(item: QueuedAnalysis, onDayLabel: String): String {
    val now = remember { Instant.now() }
    val minutes = ChronoUnit.MINUTES.between(item.createdAt, now).coerceAtLeast(0)
    val time = when {
        minutes < 1 -> stringResource(R.string.analysis_queue_just_now)
        minutes < 60 -> stringResource(R.string.analysis_queue_minutes_ago, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.analysis_queue_hours_ago, (minutes / 60).toInt())
        else -> stringResource(R.string.analysis_queue_days_ago, (minutes / (60 * 24)).toInt())
    }
    if (item.targetDate == LocalDate.now()) return time
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    }
    return "$time · $onDayLabel ${item.targetDate.format(formatter)}"
}

// -- Edit sheet ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditQueuedSheet(
    item: QueuedAnalysis,
    container: AppContainer,
    onAddPhotos: (List<ByteArray>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onSave: (String?, Double?, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember(item.id) { mutableStateOf(item.note.orEmpty()) }
    var grams by remember(item.id) {
        mutableStateOf(item.confirmedPortionGrams?.toInt() ?: 0)
    }
    var targetDate by remember(item.id) { mutableStateOf(item.targetDate) }
    val store = container.analysisQueue
    val ctx = LocalContext.current

    // Gallery picks for queue photos land here directly (NOT the share inbox
    // / FoodPhotoSession — those channels belong to the live entry flow).
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes != null && bytes.isNotEmpty()) onAddPhotos(listOf(bytes))
    }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = rememberChompassSheetState(),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            SheetReviewToolbar(
                title = stringResource(R.string.analysis_queue_edit_title),
                onCancel = onDismiss,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FudGlassTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp),
                    placeholder = stringResource(R.string.context_note_placeholder),
                )

                Text(
                    stringResource(R.string.analysis_queue_target_day),
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { targetDate = targetDate.minusDays(1) }) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(R.string.analysis_queue_day_back),
                        )
                    }
                    Text(
                        targetDate.format(
                            remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()) }
                        ),
                        fontWeight = FontWeight.Medium,
                    )
                    IconButton(
                        onClick = { targetDate = targetDate.plusDays(1) },
                        enabled = targetDate.isBefore(LocalDate.now().plusDays(1)),
                    ) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = stringResource(R.string.analysis_queue_day_forward),
                        )
                    }
                }

                Text(
                    stringResource(R.string.context_note_weight_section),
                    fontWeight = FontWeight.SemiBold,
                )
                NumericWheelPicker(
                    value = grams,
                    onValueChange = { grams = it },
                    min = 0,
                    max = 5000,
                    step = 5,
                    unit = stringResource(R.string.context_note_weight_unit),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    stringResource(R.string.meal_photos_count, item.imageFilenames.size),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(item.imageFilenames, key = { _, filename -> filename }) { index, filename ->
                        Box {
                            val bitmap = rememberQueueThumbnail(filename, store)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.meal_photo_cd, index + 1),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 96.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            }
                            IconButton(
                                onClick = { onRemovePhoto(filename) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.62f), CircleShape),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_remove),
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    if (item.imageFilenames.size < app.chompass.services.FoodPhotoSession.MAX_IMAGES) {
                        item(key = "add-photo") {
                            OutlinedButton(
                                onClick = {
                                    galleryPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.size(width = 96.dp, height = 96.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Filled.AddAPhoto,
                                        contentDescription = null,
                                        tint = AppColors.Calorie,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text(
                                        stringResource(R.string.meal_photos_add_photo),
                                        color = AppColors.Calorie,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            SheetStickyPrimaryBar(
                primaryLabel = stringResource(R.string.action_save),
                primaryEnabled = true,
                onPrimary = {
                    onSave(
                        note.trim().takeIf { it.isNotBlank() },
                        grams.takeIf { it > 0 }?.toDouble(),
                        targetDate,
                    )
                },
            )
        }
    }
}
