package org.codeberg.fitguy.nofud.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.saveable.rememberSaveable
import org.codeberg.fitguy.nofud.ui.navigation.LocalLaunchFillEpoch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import org.codeberg.fitguy.nofud.ui.util.clockTimePattern
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.services.MealShare
import org.codeberg.fitguy.nofud.models.FoodSource
import org.codeberg.fitguy.nofud.models.MacroValueFormatter
import org.codeberg.fitguy.nofud.models.MealType
import org.codeberg.fitguy.nofud.models.ServingUnitOption
import org.codeberg.fitguy.nofud.services.ai.FoodAnalysis
import org.codeberg.fitguy.nofud.ui.components.InAppCameraCaptureDialog
import org.codeberg.fitguy.nofud.ui.components.MacroChip
import org.codeberg.fitguy.nofud.ui.components.MacroCard
import org.codeberg.fitguy.nofud.models.HomeTopNutrient
import org.codeberg.fitguy.nofud.ui.components.DateWheelPicker
import org.codeberg.fitguy.nofud.ui.components.FudGlassDialog
import org.codeberg.fitguy.nofud.ui.components.FudGlassDialogActions
import org.codeberg.fitguy.nofud.ui.components.FudGlassPrimaryButton
import org.codeberg.fitguy.nofud.ui.components.FudGlassSurface
import org.codeberg.fitguy.nofud.ui.components.FudGlassTextField
import org.codeberg.fitguy.nofud.ui.components.WeekEnergyStrip
import org.codeberg.fitguy.nofud.ui.navigation.BottomNavDockedControlPadding
import org.codeberg.fitguy.nofud.ui.navigation.BottomNavScrollPadding
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import org.codeberg.fitguy.nofud.ui.theme.MacroKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val weekStartsOnMonday by container.prefs.weekStartsOnMonday.collectAsState(initial = true)
    val allEntries by container.foodRepository.entries.collectAsState(initial = emptyList())

    var showText by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showCopyFromDay by remember { mutableStateOf(false) }
    var showAddFoodSheet by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    var showNutritionDetail by remember { mutableStateOf(false) }

    var showCameraCapture by remember { mutableStateOf(false) }
    var cameraCaptureWantsSecondPhoto by remember { mutableStateOf(false) }
    var pendingCameraPairFirstImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showCameraPairTransition by remember { mutableStateOf(false) }
    var pendingNoteImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val firstPair = pendingCameraPairFirstImageBytes
                if (firstPair != null) {
                    pendingCameraPairFirstImageBytes = null
                    cameraCaptureWantsSecondPhoto = false
                    vm.analyzePhotos(firstPair, bytes)
                } else {
                    pendingNoteImageBytes = bytes
                }
            }
        }
    }

    // Photos shared into the app via the system share sheet (filled by
    // MainActivity). One image enters the same context-note flow as an in-app
    // capture; two are analyzed side-by-side like dual capture.
    val sharedImages by container.sharedImageInbox.collectAsState()
    LaunchedEffect(sharedImages) {
        val images = sharedImages
        if (images.isEmpty()) return@LaunchedEffect
        container.sharedImageInbox.value = emptyList()
        if (images.size >= 2) {
            vm.analyzePhotos(images[0], images[1])
        } else {
            pendingNoteImageBytes = images[0]
        }
    }

    var permissionWantsSecondPhoto by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraCaptureWantsSecondPhoto = permissionWantsSecondPhoto
            showCameraCapture = true
        }
        permissionWantsSecondPhoto = false
    }

    fun openCamera(withSecondPhoto: Boolean = false) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraCaptureWantsSecondPhoto = withSecondPhoto
            if (!withSecondPhoto) {
                pendingCameraPairFirstImageBytes = null
                showCameraPairTransition = false
            }
            showCameraCapture = true
        } else {
            permissionWantsSecondPhoto = withSecondPhoto
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(showCameraPairTransition) {
        if (showCameraPairTransition) {
            kotlinx.coroutines.delay(650)
            showCameraPairTransition = false
            if (pendingCameraPairFirstImageBytes != null) {
                cameraCaptureWantsSecondPhoto = true
                showCameraCapture = true
            }
        }
    }

    val barcodePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showBarcodeScanner = true
    }

    fun openBarcodeScanner() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showBarcodeScanner = true
        } else {
            barcodePermission.launch(Manifest.permission.CAMERA)
        }
    }

    val today = LocalDate.now()
    val selectedDate = ui.date
    val isToday = selectedDate == today
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mealGroups = remember(ui.todayEntries, ui.foodLogSortOrder) {
        foodLogMealGroups(ui.todayEntries, ui.foodLogSortOrder)
    }
    var selectedEntryIds by remember(ui.date) { mutableStateOf<Set<UUID>>(emptySet()) }
    val selectedEntries = remember(ui.todayEntries, selectedEntryIds) {
        ui.todayEntries.filter { it.id in selectedEntryIds }
    }
    val inSelectionMode = selectedEntryIds.isNotEmpty()

    // No topBar: the empty TopAppBar used to act as the status-bar spacer, but the
    // ad strip above this screen (TabWithBanner) now owns that inset.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = if (inSelectionMode) 72.dp else 8.dp,
                bottom = BottomNavScrollPadding
            )
        ) {
            // Week strip — verbatim port of WeekEnergyStrip in HomeComponents.swift,
            // with horizontal pagination across 53 weeks of history.
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    WeekEnergyStrip(
                        selectedDate = selectedDate,
                        onSelect = { vm.setSelectedDate(it) },
                        weekStartsOnMonday = weekStartsOnMonday
                    )
                }
            }

            // Calorie hero + macros + View More — grouped so the day-swipe gesture covers only
            // this top region, not the food log below "View More". Swipe left/right to change day;
            // the horizontal-only detector lets the LazyColumn keep scrolling vertically.
            item {
                Column(
                    modifier = Modifier.pointerInput(selectedDate) {
                        var accum = 0f
                        val threshold = 80.dp.toPx()
                        detectHorizontalDragGestures(
                            onDragStart = { accum = 0f },
                            onDragCancel = { accum = 0f },
                            onHorizontalDrag = { change, amount -> accum += amount; change.consume() },
                            onDragEnd = {
                                if (accum > threshold) {
                                    vm.setSelectedDate(selectedDate.minusDays(1))
                                } else if (accum < -threshold) {
                                    val next = selectedDate.plusDays(1)
                                    if (!next.isAfter(today)) vm.setSelectedDate(next)
                                }
                                accum = 0f
                            }
                        )
                    }
                ) {
                    Spacer(Modifier.height(4.dp))
                    CalorieHero(current = ui.caloriesToday, goal = ui.profile?.effectiveCalories ?: 2000)
                    Spacer(Modifier.height(20.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ui.homeTopNutrients.forEach { nutrient ->
                            MacroCard(
                                label = stringResource(nutrient.displayNameRes),
                                current = nutrient.current(ui.todayEntries),
                                goal = nutrient.goal(ui.profile, ui.optionalNutrientGoals),
                                unit = nutrient.unit,
                                accentColor = AppColors.nutrientColor(nutrient),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.clickable { showNutritionDetail = true }) {
                            ViewMoreButton()
                        }
                    }
                }
            }

            // Food log
            item { Spacer(Modifier.height(8.dp)) }
            if (mealGroups.isEmpty()) {
                item { SectionHeader(if (isToday) stringResource(R.string.home_todays_food) else stringResource(R.string.home_food_log)) }
                item {
                    SectionCardWrapper(isFirst = true, isLast = true) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text(
                                stringResource(R.string.home_no_foods_logged),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            } else {
                for ((groupIndex, group) in mealGroups.withIndex()) {
                    item(key = "header-${group.id}") {
                        MealSectionHeader(
                            meal = group.meal,
                            totalCalories = group.totalCalories,
                            totalProtein = group.totalProtein,
                            totalCarbs = group.totalCarbs,
                            totalFat = group.totalFat
                        )
                    }
                    itemsIndexed(group.entries, key = { _, entry -> entry.id }) { index, entry ->
                        val isFirst = index == 0
                        val isLast = index == group.entries.lastIndex
                        val rowShape = sectionCardShape(isFirst, isLast)
                        SectionCardWrapper(isFirst = isFirst, isLast = isLast, transparent = true) {
                            // Tap row -> open EditFoodEntrySheet (matches iOS .onTapGesture).
                            // Swipe trailing edge -> delete; swipe leading edge -> toggle favorite.
                            // Mirrors iOS ContentView.swift .swipeActions(edge: .trailing) on the row,
                            // which exposes Delete (destructive) + Favorite/Unfavorite buttons.
                            val isSelected = selectedEntryIds.contains(entry.id)
                            val onSelectToggle = {
                                selectedEntryIds = if (selectedEntryIds.contains(entry.id)) {
                                    selectedEntryIds - entry.id
                                } else {
                                    selectedEntryIds + entry.id
                                }
                            }
                            if (inSelectionMode) {
                                Box(
                                    modifier = Modifier.combinedClickable(
                                        onClick = onSelectToggle,
                                        onLongClick = onSelectToggle
                                    )
                                ) {
                                    FoodRow(
                                        entry = entry,
                                        isFavorite = ui.isFavorite(entry),
                                        rowShape = rowShape,
                                        isSelected = isSelected
                                    )
                                }
                            } else {
                                val isFav = ui.isFavorite(entry)
                                SwipeableFoodRow(
                                    entry = entry,
                                    isFavorite = isFav,
                                    rowShape = rowShape,
                                    onTap = { editingEntry = entry },
                                    onLongPress = { selectedEntryIds = setOf(entry.id) },
                                    onDelete = { vm.deleteEntry(entry.id) },
                                    onToggleFavorite = { vm.toggleFavorite(entry) }
                                )
                            }
                            if (index != group.entries.lastIndex) Divider()
                        }
                    }
                }
            }
        }

        // Floating "+" add button — overlaid bottom-right and lifted above the docked
        // bottom nav bar. The parent Scaffold renders content full-screen behind the
        // bar, so the Scaffold FAB slot would sit hidden underneath it. Mirrors the iOS
        // ContentView FAB: .overlay(alignment: .bottomTrailing) + .padding(.bottom).
        FloatingActionButton(
            onClick = { showAddFoodSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = BottomNavDockedControlPadding + 16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_add_food),
            )
        }
        if (inSelectionMode) {
            SelectionActionBar(
                selectedCount = selectedEntryIds.size,
                onCancel = { selectedEntryIds = emptySet() },
                onShare = {
                    MealShare.share(ctx, selectedEntries)
                    selectedEntryIds = emptySet()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .zIndex(2f)
            )
        }
        }
    }

    if (showAddFoodSheet) {
        AddFoodSheet(
            onPhoto = { openCamera() },
            onNote = { showText = true },
            onSaved = { showSaved = true },
            onVoice = { showVoice = true },
            onBarcode = { openBarcodeScanner() },
            onManual = { showManual = true },
            onCopyFromDay = { showCopyFromDay = true },
            onDismiss = { showAddFoodSheet = false }
        )
    }

    if (showText) {
        TextInputSheet(
            onDismiss = { showText = false },
            onSubmit = { showText = false; vm.analyzeText(it) }
        )
    }

    if (showVoice) {
        VoiceInputSheet(
            container = container,
            onDismiss = { showVoice = false },
            onSubmit = { showVoice = false; vm.analyzeText(it) }
        )
    }

    if (showManual) {
        ManualEntryDialog(
            onDismiss = { showManual = false },
            onSave = { name, kcal, p, c, f, meal ->
                showManual = false
                vm.saveManualEntry(name, kcal, p, c, f, meal)
            }
        )
    }

    if (showSaved) {
        SavedMealsSheet(
            container = container,
            onDismiss = { showSaved = false },
            // Tapping a Saved Meals row opens the FoodResultSheet for review
            // instead of logging immediately — same UX as the photo flow.
            onRelogEntry = { vm.reviewSavedMeal(it) }
        )
    }

    if (showCopyFromDay) {
        CopyFromDaySheet(
            targetDate = ui.date,
            allEntries = allEntries,
            onCopy = { entries ->
                vm.copyEntriesToSelectedDay(entries)
                showCopyFromDay = false
            },
            onDismiss = { showCopyFromDay = false }
        )
    }

    if (showBarcodeScanner) {
        BarcodeScannerSheet(
            onBarcode = { barcode ->
                showBarcodeScanner = false
                vm.lookupBarcode(barcode)
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }

    if (showCameraCapture) {
        InAppCameraCaptureDialog(
            onCapture = { bytes ->
                val wantsSecondPhoto = cameraCaptureWantsSecondPhoto
                val firstPairImage = pendingCameraPairFirstImageBytes
                showCameraCapture = false
                cameraCaptureWantsSecondPhoto = false
                if (wantsSecondPhoto && firstPairImage == null) {
                    pendingCameraPairFirstImageBytes = bytes
                    showCameraPairTransition = true
                } else if (wantsSecondPhoto && firstPairImage != null) {
                    pendingCameraPairFirstImageBytes = null
                    vm.analyzePhotos(firstPairImage, bytes)
                } else {
                    pendingNoteImageBytes = bytes
                }
            },
            onOpenGallery = {
                showCameraCapture = false
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = {
                showCameraCapture = false
                cameraCaptureWantsSecondPhoto = false
                if (pendingCameraPairFirstImageBytes != null && !showCameraPairTransition) {
                    pendingNoteImageBytes = pendingCameraPairFirstImageBytes
                }
                pendingCameraPairFirstImageBytes = null
                showCameraPairTransition = false
            }
        )
    }

    editingEntry?.let { entry ->
        EditFoodEntrySheet(
            entry = entry,
            preferGramsByDefault = ui.preferGramsByDefault,
            onReprocess = { updatedNote ->
                vm.reprocessFoodEntry(entry, updatedNote)
            },
            onSave = { updated ->
                vm.updateEntry(updated)
                editingEntry = null
            },
            onDismiss = { editingEntry = null }
        )
    }

    if (showNutritionDetail) {
        NutritionDetailSheet(
            entries = ui.todayEntries,
            profile = ui.profile,
            homeTopNutrients = ui.homeTopNutrients,
            optionalGoals = ui.optionalNutrientGoals,
            onHomeTopNutrientsChange = vm::setHomeTopNutrients,
            onDismiss = { showNutritionDetail = false }
        )
    }

    // Photo captured or picked -> optional note -> analyze.
    // If a failed camera+note attempt was restored from disk, reuse that image+note.
    val contextSheetImageBytes = pendingNoteImageBytes ?: ui.pendingInputImageBytes
    contextSheetImageBytes?.let { bytes ->
        val isRestoredFailedInput = pendingNoteImageBytes == null
        ContextNoteSheet(
            imageBytes = bytes,
            initialNote = if (isRestoredFailedInput) ui.pendingInputNote.orEmpty() else "",
            onAnalyze = { note ->
                pendingNoteImageBytes = null
                vm.analyzePhotoWithNote(bytes, note)
            },
            onAddPhoto = {
                pendingCameraPairFirstImageBytes = bytes
                pendingNoteImageBytes = null
                if (isRestoredFailedInput) vm.dismissFailedInput()
                openCamera(withSecondPhoto = true)
            },
            onDismiss = {
                pendingNoteImageBytes = null
                if (isRestoredFailedInput) vm.dismissFailedInput()
            }
        )
    }

    if (ui.analyzing) AnalyzingOverlay(imageBytes = ui.pendingImageBytes)
    if (showCameraPairTransition) CameraPairTransitionOverlay()

    ui.pendingAnalysis?.let { analysis ->
        FoodResultSheet(
            analysis = analysis,
            imageBytes = ui.pendingImageBytes,
            preferGramsByDefault = ui.preferGramsByDefault,
            profile = ui.profile,
            dayEntries = ui.todayEntries,
            source = ui.pendingReviewSource?.source
                ?: ui.pendingFoodSource
                ?: if (ui.pendingImageBytes != null) FoodSource.SNAP_FOOD else FoodSource.TEXT_INPUT,
            onWhatIfSuggestion = vm::suggestMealWhatIf,
            onSave = { name, grams, scale, mealType, selectedServingUnit, selectedServingQuantity, editedAnalysis ->
                vm.saveAnalysis(
                    name = name,
                    servingGrams = grams,
                    scale = scale,
                    mealType = mealType,
                    selectedServingUnit = selectedServingUnit,
                    selectedServingQuantity = selectedServingQuantity,
                    editedAnalysis = editedAnalysis
                )
            },
            onDismiss = { vm.dismissPending() }
        )
    }

    ui.error?.let { err ->
        val hasRetryableInput = ui.pendingInputImageBytes != null || ui.pendingInputDraftImageFilename != null
        FudGlassDialog(onDismissRequest = { vm.clearError() }) {
            Text(stringResource(R.string.error_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(err, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
            if (hasRetryableInput) {
                FudGlassDialogActions(
                    primaryText = stringResource(R.string.action_retry),
                    onPrimary = { vm.retryFailedInput() },
                    dismissText = stringResource(R.string.action_discard),
                    onDismiss = { vm.dismissFailedInput() }
                )
            } else {
                FudGlassDialogActions(
                    primaryText = stringResource(R.string.action_ok),
                    onPrimary = { vm.clearError() }
                )
            }
        }
    }
}

// ── Week strip (iOS port) ────────────────────────────────────────────

@Composable
private fun WeekStripSection(selectedDate: LocalDate, onSelect: (LocalDate) -> Unit) {
    val firstDow = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weekStart = remember(selectedDate, firstDow) {
        val offset = ((selectedDate.dayOfWeek.value - firstDow.value) + 7) % 7
        selectedDate.minusDays(offset.toLong())
    }
    val today = remember { LocalDate.now() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val isSel = date == selectedDate
            val isTdy = date == today
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(date) }
                    )
            ) {
                Text(
                    shortDay(date.dayOfWeek),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSel) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSel) AppColors.CalorieGradient
                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .then(
                            if (isTdy && !isSel) Modifier.border(1.5.dp, AppColors.Calorie.copy(alpha = 0.35f), CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isSel -> Color.White
                            isTdy -> AppColors.Calorie
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

private fun shortDay(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "M"
    DayOfWeek.TUESDAY -> "T"
    DayOfWeek.WEDNESDAY -> "W"
    DayOfWeek.THURSDAY -> "T"
    DayOfWeek.FRIDAY -> "F"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "S"
}

// ── Calorie hero ─────────────────────────────────────────────────────

/**
 * Verbatim port of the calorie hero block in HomeView.body
 * (ios/calorietracker/ContentView.swift, lines ~322–362):
 *
 *   VStack(spacing: 20) {
 *     VStack(spacing: 4) {
 *       Text("\(selectedCalories)")
 *         .font(.system(size: 72, weight: .bold, design: .rounded))
 *         .foregroundStyle(LinearGradient(colors: AppColors.calorieGradient,
 *                                         startPoint: .topLeading,
 *                                         endPoint: .bottomTrailing))
 *         .contentTransition(.numericText())
 *         .animation(.snappy, value: selectedCalories)
 *       Text("of \(calorieGoal) kcal")
 *         .font(.system(.callout, design: .rounded, weight: .medium))
 *         .foregroundStyle(.tertiary)
 *     }
 *     GeometryReader { geo in
 *       ZStack(alignment: .leading) {
 *         Capsule().fill(AppColors.calorie.opacity(0.10)).frame(height: 10)
 *         Capsule().fill(LinearGradient(.leading, .trailing))
 *                  .frame(width: max(10, geo.size.width * progress), height: 10)
 *                  .shadow(color: AppColors.calorie.opacity(0.35), radius: 8, y: 3)
 *                  .animation(.spring(response: 0.8, dampingFraction: 0.75), value: selectedCalories)
 *       }
 *     }.frame(height: 10).padding(.horizontal, 24)
 *     Text("\(caloriesRemaining) left")
 *       .font(.system(.footnote, design: .rounded, weight: .medium))
 *       .foregroundStyle(.secondary)
 *   }
 *   .padding(.vertical, 20)
 */
@Composable
private fun CalorieHero(current: Int, goal: Int) {
    val ratio = if (goal > 0) (current.toFloat() / goal).coerceIn(0f, 1f) else 0f
    // Fill-from-zero on app open. lastEpoch is saveable so it survives tab switches
    // (where Home leaves/re-enters composition) — only a real app-open (new epoch)
    // replays the sweep; tab returns snap to the current value.
    val epoch = LocalLaunchFillEpoch.current
    var lastEpoch by rememberSaveable { mutableIntStateOf(0) }
    val animatedRatio = remember { Animatable(if (lastEpoch == epoch) ratio else 0f) }
    LaunchedEffect(epoch, ratio) {
        val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 55f)
        if (lastEpoch != epoch) {
            animatedRatio.snapTo(0f)
            animatedRatio.animateTo(ratio, spec)
            lastEpoch = epoch
        } else {
            animatedRatio.animateTo(ratio, spec)
        }
    }
    val remaining = maxOf(0, goal - current)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Segmented (dashed) semicircle speedometer arc. Fixed 260dp dome to mirror
        // iOS CalorieGauge's hard .frame(width: 260) (244dp arc + 16dp stroke = 260dp).
        Canvas(
            modifier = Modifier
                .width(260.dp)
                .aspectRatio(2f)
        ) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.width - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = 180f,
                sweepAngle = 180f * animatedRatio.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        // Centered readout, sitting inside the dome
        Column(
            modifier = Modifier.padding(top = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "CALORIES",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                "$current",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            // Flame + remaining, mirroring iOS HStack(spacing: 5) { flame.fill (11pt) ;
            // Text("\(remaining) left") } tinted to AppColors.calorie — a pink monochrome
            // glyph, not a multicolor emoji, and the count is un-grouped (no thousands comma).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    "$remaining left",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Macro card (iOS port) ────────────────────────────────────────────

// MacroCard moved to ui/components/MacroCard.kt as a verbatim port of
// HomeComponents.swift's struct MacroCard. Imported above.


@Composable
private fun ViewMoreButton() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            stringResource(R.string.home_view_more),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Calorie.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = AppColors.Calorie.copy(alpha = 0.6f),
            modifier = Modifier.size(11.dp)
        )
    }
}

// ── Section headers / cards / rows ──────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    // iOS Section header in .insetGrouped List renders the title in sentence case
    // (no uppercase transform), bold, ~22sp on the iOS calorie/food page. Match that.
    Text(
        title,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun MealSectionHeader(
    meal: MealType,
    totalCalories: Int? = null,
    totalProtein: Double = 0.0,
    totalCarbs: Double = 0.0,
    totalFat: Double = 0.0
) {
    // iOS layout: small dim icon + sentence-case label, regular weight ~17sp.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 24.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            mealIcon(meal),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(meal.displayNameRes),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
        // Combined nutrients for this meal (issue #103: chicken + pasta + sauce = one total)
        if (totalCalories != null) {
            Spacer(Modifier.weight(1f))
            val summary = buildAnnotatedString {
                append("$totalCalories kcal · ")
                withStyle(SpanStyle(color = AppColors.Protein)) { append("${totalProtein.roundToInt()}P") }
                append(' ')
                withStyle(SpanStyle(color = AppColors.Carbs)) { append("${totalCarbs.roundToInt()}C") }
                append(' ')
                withStyle(SpanStyle(color = AppColors.Fat)) { append("${totalFat.roundToInt()}F") }
            }
            Text(
                summary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    FudGlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        padding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = 180f }
                )
            }
            Text(
                text = selectedCount.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShare, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = stringResource(R.string.cd_share_meal),
                    tint = if (selectedCount > 0) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private data class FoodLogMealGroup(
    val id: String,
    val meal: MealType,
    val entries: List<FoodEntry>
) {
    // Combined nutrients for this meal group (issue #103: chicken + pasta + sauce = one total).
    val totalCalories: Int get() = entries.sumOf { it.calories }
    val totalProtein: Double get() = entries.sumOf { it.protein }
    val totalCarbs: Double get() = entries.sumOf { it.carbs }
    val totalFat: Double get() = entries.sumOf { it.fat }
}

private fun foodLogMealGroups(
    entries: List<FoodEntry>,
    sortOrder: FoodLogSortOrder
): List<FoodLogMealGroup> = when (sortOrder) {
    FoodLogSortOrder.STANDARD -> {
        val grouped = entries.groupBy { it.mealType }
        listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK, MealType.OTHER)
            .mapNotNull { meal ->
                val mealEntries = grouped[meal].orEmpty()
                if (mealEntries.isEmpty()) null else FoodLogMealGroup(
                    id = "standard-${meal.name}",
                    meal = meal,
                    entries = mealEntries
                )
            }
    }
    FoodLogSortOrder.LATEST_MEALS_FIRST -> latestMealRuns(entries)
}

private fun latestMealRuns(entries: List<FoodEntry>): List<FoodLogMealGroup> {
    val sortedEntries = entries.sortedByDescending { it.timestamp }
    val groups = mutableListOf<FoodLogMealGroup>()
    var currentMeal: MealType? = null
    val currentEntries = mutableListOf<FoodEntry>()

    fun appendCurrentGroup() {
        val meal = currentMeal ?: return
        if (currentEntries.isEmpty()) return
        groups += FoodLogMealGroup(
            id = "latest-${groups.size}-${meal.name}-${currentEntries.first().id}",
            meal = meal,
            entries = currentEntries.toList()
        )
    }

    for (entry in sortedEntries) {
        if (entry.mealType == currentMeal) {
            currentEntries += entry
        } else {
            appendCurrentGroup()
            currentMeal = entry.mealType
            currentEntries.clear()
            currentEntries += entry
        }
    }

    appendCurrentGroup()
    return groups
}

private fun mealIcon(meal: MealType): ImageVector = when (meal) {
    MealType.BREAKFAST -> Icons.Filled.WbTwilight
    MealType.LUNCH -> Icons.Filled.WbSunny
    MealType.DINNER -> Icons.Filled.Bedtime
    MealType.SNACK -> Icons.Filled.Coffee
    MealType.OTHER -> Icons.Filled.Restaurant
}

private fun sectionCardShape(isFirst: Boolean, isLast: Boolean): RoundedCornerShape {
    // 22dp corners on the meal card matches the softer iOS look (was 14dp).
    return when {
        isFirst && isLast -> RoundedCornerShape(22.dp)
        isFirst -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        isLast -> RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
        else -> RoundedCornerShape(0.dp)
    }
}

@Composable
private fun SectionCardWrapper(
    isFirst: Boolean,
    isLast: Boolean,
    transparent: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = sectionCardShape(isFirst, isLast)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(if (transparent) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow)
    ) { content() }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(start = 102.dp, end = 14.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    )
}

/**
 * Swipe-to-action wrapper around FoodRow.
 *
 * - Swipe right-to-left (trailing) past threshold → delete (mirrors iOS swipeActions
 *   trailing destructive button).
 * - Swipe left-to-right (leading) past threshold → toggle favorite (mirrors iOS
 *   .swipeActions secondary heart button).
 * - Tap → open EditFoodEntrySheet (matches iOS .onTapGesture).
 *
 * The dismiss state is reset on a no-confirm swing-back so partial swipes don't
 * leave the row stuck mid-flight when the user releases short of the threshold.
 */
@Composable
private fun SwipeableFoodRow(
    entry: FoodEntry,
    isFavorite: Boolean,
    rowShape: RoundedCornerShape,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val density = LocalDensity.current
    val favoriteTriggerPx = with(density) { 150.dp.toPx() }
    val deleteTriggerPx = with(density) { 220.dp.toPx() }
    var offsetPx by remember(entry.id) { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val maxSwipePx = with(density) { maxWidth.toPx() * 0.72f }
        Box(Modifier.fillMaxWidth()) {
            SwipeBackground(offsetPx = offsetPx, isFavorite = isFavorite)
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetPx.roundToInt(), 0) }
                    .pointerInput(entry.id, maxSwipePx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetPx = (offsetPx + dragAmount).coerceIn(-maxSwipePx, maxSwipePx)
                            },
                            onDragEnd = {
                                val finalOffset = offsetPx
                                offsetPx = 0f
                                when {
                                    finalOffset <= -deleteTriggerPx -> onDelete()
                                    finalOffset >= favoriteTriggerPx -> onToggleFavorite()
                                }
                            },
                            onDragCancel = {
                                offsetPx = 0f
                            }
                        )
                    }
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = onLongPress
                    )
            ) {
                FoodRow(entry = entry, isFavorite = isFavorite, rowShape = rowShape)
            }
        }
    }
}

@Composable
private fun BoxScope.SwipeBackground(offsetPx: Float, isFavorite: Boolean) {
    if (offsetPx == 0f) {
        Box(Modifier.matchParentSize())
        return
    }
    val (bg, icon, label) = if (offsetPx < 0f) {
        Triple(
            Color(0xFFD32F2F),
            Icons.Filled.Delete,
            stringResource(R.string.home_swipe_delete)
        )
    } else {
        Triple(
            AppColors.Calorie,
            if (isFavorite) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
            if (isFavorite) stringResource(R.string.home_swipe_unfavorite) else stringResource(R.string.home_swipe_favorite)
        )
    }
    // iOS Mail-style trailing reveal: paint only the area the foreground has
    // moved out of, pinned to the matching edge. Width = absolute offset.
    val widthPx = kotlin.math.abs(offsetPx)
    val widthDp = with(LocalDensity.current) { widthPx.toDp() }
    val alignment = if (offsetPx < 0f) Alignment.CenterEnd else Alignment.CenterStart

    Box(Modifier.matchParentSize()) {
        Box(
            Modifier
                .align(alignment)
                .fillMaxHeight()
                .width(widthDp)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            if (widthPx > 24f) {
                Icon(icon, contentDescription = label, tint = Color.White)
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun FoodRow(
    entry: FoodEntry,
    isFavorite: Boolean = false,
    rowShape: RoundedCornerShape = RoundedCornerShape(22.dp),
    isSelected: Boolean = false
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ctx = LocalContext.current
    val timeFmt = DateTimeFormatter.ofPattern(clockTimePattern(ctx), Locale.US).withZone(ZoneId.systemDefault())
    val container = (ctx.applicationContext as org.codeberg.fitguy.nofud.NoFUDApp).container
    val bitmap = remember(entry.imageFilename) {
        entry.imageFilename?.let { container.imageStore.loadThumbnail(it) }
    }
    // iOS layout: large 76dp square thumb · column with (Name + heart on left,
    // time on right) · pink kcal · serving · macro tag pills row.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                if (isSelected) AppColors.Calorie.copy(alpha = if (isDark) 0.25f else 0.12f)
                else if (isDark) AppColors.TranslucentSurfaceDark
                else AppColors.TranslucentSurfaceLight
            )
            .border(
                if (isSelected) 1.dp else 0.5.dp,
                if (isSelected) AppColors.Calorie.copy(alpha = 0.65f)
                else if (isDark) AppColors.HairlineBorderDark
                else AppColors.HairlineBorderLight,
                rowShape
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = entry.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
                entry.emoji != null -> Text(entry.emoji ?: "", fontSize = 36.sp)
                else -> Icon(
                    Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Column(
            Modifier.weight(1f).padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Name (+ heart) on the left, time on the top-right.
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        entry.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isFavorite) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.cd_favorited),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    timeFmt.format(entry.timestamp).lowercase(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            // Pink kcal · gray serving size.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "${entry.calories} kcal",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                entry.servingSizeGrams?.takeIf { it > 0 }?.let { grams ->
                    Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    val gramsText = if (grams == grams.toInt().toDouble()) "${grams.toInt()}g"
                                    else String.format("%.1fg", grams)
                    Text(
                        gramsText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Macro pills (P / C / F) — tinted dark capsules with gray text.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroChip(MacroKind.PROTEIN, entry.protein)
                MacroChip(MacroKind.CARBS, entry.carbs)
                MacroChip(MacroKind.FAT, entry.fat)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyFromDaySheet(
    targetDate: LocalDate,
    allEntries: List<FoodEntry>,
    onCopy: (List<FoodEntry>) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    var sourceDate by remember(targetDate) { mutableStateOf(targetDate.minusDays(1)) }
    var showDatePicker by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d", Locale.US) }
    val sourceEntries = remember(allEntries, sourceDate) {
        allEntries
            .filter { it.timestamp.atZone(zone).toLocalDate() == sourceDate }
            .sortedByDescending { it.timestamp }
    }
    val groups = remember(sourceEntries) {
        foodLogMealGroups(sourceEntries, FoodLogSortOrder.STANDARD)
    }
    val targetText = if (targetDate == LocalDate.now()) "today" else targetDate.format(dateFmt)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        SheetReviewToolbar(
            title = stringResource(R.string.home_menu_copy_from_day),
            primaryLabel = if (sourceEntries.isEmpty()) stringResource(R.string.action_done) else stringResource(R.string.copy_all),
            onCancel = onDismiss,
            onPrimary = { if (sourceEntries.isEmpty()) onDismiss() else onCopy(sourceEntries) }
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SheetSectionHeader(stringResource(R.string.section_source))
                    SheetPillRow(onClick = { showDatePicker = true }) {
                        Text(stringResource(R.string.copy_from), fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(
                            sourceDate.format(dateFmt),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Foods will be copied to $targetText. Original entries stay unchanged.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }
            }

            if (sourceEntries.isEmpty()) {
                item {
                    SectionCardWrapper(isFirst = true, isLast = true) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = AppColors.Calorie.copy(alpha = 0.45f),
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                stringResource(R.string.copy_no_foods_on_day),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                item {
                    FudGlassPrimaryButton(
                        text = pluralStringResource(R.plurals.copy_foods_to, sourceEntries.size, sourceEntries.size, targetText),
                        onClick = { onCopy(sourceEntries) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                }

                groups.forEach { group ->
                    item(key = "copy-header-${group.id}") {
                        MealSectionHeader(meal = group.meal)
                    }
                    item(key = "copy-meal-${group.id}") {
                        FudGlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            cornerRadius = 18.dp,
                            padding = 0.dp
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onCopy(group.entries) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    stringResource(R.string.copy_meal_format, stringResource(group.meal.displayNameRes)),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    itemsIndexed(group.entries, key = { _, entry -> "copy-entry-${entry.id}" }) { index, entry ->
                        val isFirst = index == 0
                        val isLast = index == group.entries.lastIndex
                        val rowShape = sectionCardShape(isFirst, isLast)
                        SectionCardWrapper(isFirst = isFirst, isLast = isLast, transparent = true) {
                            Box(Modifier.clickable { onCopy(listOf(entry)) }) {
                                FoodRow(entry = entry, rowShape = rowShape)
                            }
                            if (index != group.entries.lastIndex) Divider()
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        var pickedDate by remember(sourceDate) { mutableStateOf(sourceDate) }
        FudGlassDialog(onDismissRequest = { showDatePicker = false }) {
            Text(stringResource(R.string.copy_from), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            DateWheelPicker(
                selected = pickedDate,
                onSelect = { pickedDate = it },
                minYear = LocalDate.now().year - 10,
                maxYear = LocalDate.now().year,
                modifier = Modifier.fillMaxWidth()
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_done),
                onPrimary = {
                    sourceDate = pickedDate
                    showDatePicker = false
                },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

// ── Dialogs (unchanged styling polish) ──────────────────────────────

@Composable
private fun AnalyzingOverlay(imageBytes: ByteArray? = null) {
    // Verbatim port of ios/calorietracker/Views/AnalyzingView.swift:
    //   VStack { (image | text.magnifyingglass) → ProgressView(.large) → "Analyzing your food..." }
    //   filling the screen, opaque background, calorie-pink accents.
    val bitmap = rememberDecodedBitmap(imageBytes)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Icon(
                    Icons.Filled.ImageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
            CircularProgressIndicator(
                color = AppColors.Calorie,
                strokeWidth = 4.dp,
                modifier = Modifier.size(40.dp)
            )
            // iOS uses two different copies depending on the input mode — photo flows
            // say "Analyzing your food..." while text/voice flows say
            // "Looking up nutrition..." (see ContentView.swift cases .analyzing /
            // .analyzingText). pendingImageBytes is the discriminator.
            Text(
                if (bitmap != null) stringResource(R.string.home_analyzing_food) else stringResource(R.string.home_looking_up_nutrition),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Calorie
            )
        }
    }
}

@Composable
private fun rememberDecodedBitmap(bytes: ByteArray?): android.graphics.Bitmap? {
    val state = produceState<android.graphics.Bitmap?>(initialValue = null, key1 = bytes) {
        value = if (bytes == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }
    return state.value
}

@Composable
private fun CameraPairTransitionOverlay() {
    var entered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "cameraPairTransitionScale"
    )

    LaunchedEffect(Unit) {
        entered = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        FudGlassSurface(
            modifier = Modifier
                .width(250.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            cornerRadius = 28.dp,
            padding = 22.dp,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(AppColors.CalorieGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AddAPhoto,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    stringResource(R.string.home_first_photo_saved),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.home_take_second_shot),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
        }
    }
}

@Composable
private fun AnalysisResultDialog(
    analysis: org.codeberg.fitguy.nofud.services.ai.FoodAnalysis,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text("${analysis.emoji ?: "🍽"}  ${analysis.name}", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${analysis.calories} kcal", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AppColors.Calorie)
                MacroLine(stringResource(R.string.macro_protein_format, MacroValueFormatter.withUnit(analysis.protein)), AppColors.Protein)
                MacroLine(stringResource(R.string.macro_carbs_format, MacroValueFormatter.withUnit(analysis.carbs)), AppColors.Carbs)
                MacroLine(stringResource(R.string.macro_fat_format, MacroValueFormatter.withUnit(analysis.fat)), AppColors.Fat)
                if (analysis.fiber != null || analysis.sugar != null || analysis.sodium != null) {
                    Spacer(Modifier.height(2.dp))
                    analysis.fiber?.let { MacroLine(stringResource(R.string.nutrient_fiber_format, it.toString()), AppColors.Fiber, fontSize = 12.sp) }
                    analysis.sugar?.let { Text(stringResource(R.string.nutrient_sugar_format, it.toString()), fontSize = 12.sp) }
                    analysis.saturatedFat?.let { Text(stringResource(R.string.nutrient_sat_fat_format, it.toString()), fontSize = 12.sp) }
                    analysis.sodium?.let { Text(stringResource(R.string.nutrient_sodium_format, it.toString()), fontSize = 12.sp) }
                    analysis.potassium?.let { Text(stringResource(R.string.nutrient_potassium_format, it.toString()), fontSize = 12.sp) }
                    analysis.cholesterol?.let { Text(stringResource(R.string.nutrient_cholesterol_format, it.toString()), fontSize = 12.sp) }
                }
                Text(
                    stringResource(R.string.home_serving_format, analysis.servingSizeGrams.toInt()),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
        FudGlassDialogActions(
            primaryText = stringResource(R.string.action_save),
            onPrimary = onSave,
            dismissText = stringResource(R.string.action_discard),
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, calories: Int, protein: Double, carbs: Double, fat: Double, mealType: MealType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(MealType.currentMeal) }
    var mealMenuExpanded by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && calories.toIntOrNull() != null

    FudGlassDialog(onDismissRequest = onDismiss) {
                Text(stringResource(R.string.manual_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

                FudGlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.manual_name_placeholder),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(stringResource(R.string.manual_calories), calories, { calories = it.filter(Char::isDigit) }, Modifier.weight(1f), accentColor = AppColors.Calorie)
                    NumberField(stringResource(R.string.manual_protein), protein, { protein = filterDecimalInput(it) }, Modifier.weight(1f), decimal = true, accentColor = AppColors.Protein)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(stringResource(R.string.manual_carbs), carbs, { carbs = filterDecimalInput(it) }, Modifier.weight(1f), decimal = true, accentColor = AppColors.Carbs)
                    NumberField(stringResource(R.string.manual_fat), fat, { fat = filterDecimalInput(it) }, Modifier.weight(1f), decimal = true, accentColor = AppColors.Fat)
                }

                // Meal Type — DropdownMenu styled to match the FoodResultSheet /
                // EditFoodEntrySheet meal pickers (icon + label, pink, anchored
                // to the right cluster).
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { mealMenuExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.sheet_meal_type), fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Box {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                sheetMealIcon(mealType),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(mealType.displayNameRes),
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        SheetGlassDropdownMenu(
                            expanded = mealMenuExpanded,
                            onDismissRequest = { mealMenuExpanded = false },
                            menuWidth = 184.dp
                        ) {
                            for (m in MealType.values()) {
                                SheetGlassDropdownMenuItem(
                                    label = stringResource(m.displayNameRes),
                                    leadingIcon = sheetMealIcon(m),
                                    selected = m == mealType,
                                    onClick = {
                                        mealType = m
                                        mealMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                FudGlassPrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        onSave(
                            name.trim(),
                            calories.toIntOrNull() ?: 0,
                            ServingUnitOption.parseQuantity(protein) ?: 0.0,
                            ServingUnitOption.parseQuantity(carbs) ?: 0.0,
                            ServingUnitOption.parseQuantity(fat) ?: 0.0,
                            mealType
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    accentColor: Color = AppColors.Calorie,
) {
    FudGlassTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = label,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (decimal) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Number
        ),
        accentColor = accentColor,
        modifier = modifier
    )
}

private fun filterDecimalInput(value: String): String =
    value.filter { it.isDigit() || it == '.' || it == ',' }

@Composable
private fun MacroLine(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit = 16.sp) {
    Text(text, fontSize = fontSize, color = color, fontWeight = FontWeight.Medium)
}
