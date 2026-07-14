package org.codeberg.fitguy.nofud.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.util.UUID
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.FoodSource
import org.codeberg.fitguy.nofud.services.MealShare
import org.codeberg.fitguy.nofud.ui.components.FudGlassDialog
import org.codeberg.fitguy.nofud.ui.components.FudGlassDialogActions
import org.codeberg.fitguy.nofud.ui.components.InAppCameraCaptureDialog
import org.codeberg.fitguy.nofud.ui.components.MacroCard
import org.codeberg.fitguy.nofud.ui.components.StepsCard
import org.codeberg.fitguy.nofud.ui.components.WeekEnergyStrip
import org.codeberg.fitguy.nofud.ui.navigation.BottomNavDockedControlPadding
import org.codeberg.fitguy.nofud.ui.navigation.BottomNavScrollPadding
import org.codeberg.fitguy.nofud.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val weekStartsOnMonday by container.prefs.weekStartsOnMonday.collectAsState(initial = true)
    val allEntries by container.foodRepository.entries.collectAsState(initial = emptyList())
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) vm.refreshActivitySnapshot()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showText by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showCopyFromDay by remember { mutableStateOf(false) }
    var showAddFoodSheet by remember { mutableStateOf(false) }
    var showCustomWaterLog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    var showNutritionDetail by remember { mutableStateOf(false) }

    var showCameraCapture by remember { mutableStateOf(false) }
    var showMultiPhotoCapture by remember { mutableStateOf(false) }
    var pendingCaptureImageBytes by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var isImportingPhotos by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        val remaining = 10 - pendingCaptureImageBytes.size
        val imported = uris.take(remaining).mapNotNull { uri ->
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        if (imported.isNotEmpty()) {
            pendingCaptureImageBytes = (pendingCaptureImageBytes + imported).take(10)
        }
        if (pendingCaptureImageBytes.isNotEmpty()) showMultiPhotoCapture = true
    }

    // Photos shared into the app via the system share sheet (filled by
    // MainActivity). Up to 10 images enter the multi-photo review sheet.
    val sharedImages by container.sharedImageInbox.collectAsState()
    LaunchedEffect(sharedImages, ui.isEntryAnalysisBusy) {
        val images = sharedImages
        if (images.isEmpty()) return@LaunchedEffect
        if (ui.isEntryAnalysisBusy) return@LaunchedEffect
        container.sharedImageInbox.value = emptyList()
        pendingCaptureImageBytes = images.take(10)
        isImportingPhotos = true
        showMultiPhotoCapture = true
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isImportingPhotos = false
            pendingCaptureImageBytes = emptyList()
            showCameraCapture = true
        }
    }

    fun openCamera() {
        isImportingPhotos = false
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingCaptureImageBytes = emptyList()
            showCameraCapture = true
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
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
                    val baseGoal = ui.gaugeBaseCalorieGoal
                    val activeCalories = ui.displayActiveCalories
                    val calorieMode = ui.effectiveCalorieMode
                    CalorieHero(
                        current = ui.caloriesToday,
                        baseGoal = baseGoal,
                        activeCalories = activeCalories,
                        displayMode = calorieMode,
                        activeCalorieSource = ui.resolvedActiveBurn?.source,
                    )
                    if (ui.homeDisplay.showSteps) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StepsCard(
                                steps = ui.activitySnapshot.steps,
                                goal = ui.homeDisplay.stepGoal,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
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
                    if (ui.waterTrackingEnabled) {
                        Spacer(Modifier.height(12.dp))
                        WaterProgressRow(
                            current = ui.waterTodayMl,
                            goal = ui.waterDailyGoalMl,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
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
                            totalFat = group.totalFat,
                            macroChips = ui.foodLogMacroChips,
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
                                        isSelected = isSelected,
                                        macroChips = ui.foodLogMacroChips,
                                    )
                                }
                            } else {
                                val isFav = ui.isFavorite(entry)
                                SwipeableFoodRow(
                                    entry = entry,
                                    isFavorite = isFav,
                                    rowShape = rowShape,
                                    macroChips = ui.foodLogMacroChips,
                                    onTap = { editingEntry = entry },
                                    onLongPress = { selectedEntryIds = setOf(entry.id) },
                                    onDelete = { vm.deleteEntry(entry) },
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
            waterTrackingEnabled = ui.waterTrackingEnabled,
            waterQuickPresetsMl = ui.waterQuickPresetsMl,
            waterUseMetric = ui.weightMetric,
            onPhoto = { openCamera() },
            onNote = { showText = true },
            onSaved = { showSaved = true },
            onVoice = { showVoice = true },
            onBarcode = { openBarcodeScanner() },
            onManual = { showManual = true },
            onCopyFromDay = { showCopyFromDay = true },
            onWater = { ml -> vm.addWater(ml) },
            onWaterCustom = { showCustomWaterLog = true },
            onDismiss = { showAddFoodSheet = false }
        )
    }

    if (showCustomWaterLog) {
        WaterCustomAmountSheet(
            onDismiss = { showCustomWaterLog = false },
            onAdd = vm::addWater,
        )
    }

    if (showText) {
        TextInputSheet(
            onDismiss = { showText = false },
            isSubmitting = ui.isEntryAnalysisBusy,
            onSubmit = {
                if (!ui.isEntryAnalysisBusy) {
                    showText = false
                    vm.analyzeText(it)
                }
            }
        )
    }

    if (showVoice) {
        VoiceInputSheet(
            container = container,
            onDismiss = { showVoice = false },
            isSubmitting = ui.isEntryAnalysisBusy,
            onSubmit = {
                if (!ui.isEntryAnalysisBusy) {
                    showVoice = false
                    vm.analyzeText(it)
                }
            }
        )
    }

    if (showManual) {
        ManualEntryDialog(
            isSaving = ui.saving,
            onDismiss = { showManual = false },
            onSave = { name, kcal, p, c, f, meal ->
                if (!ui.saving) {
                    showManual = false
                    vm.saveManualEntry(name, kcal, p, c, f, meal)
                }
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
            isSaving = ui.saving,
            onCopy = { entries ->
                if (!ui.saving) {
                    vm.copyEntriesToSelectedDay(entries)
                    showCopyFromDay = false
                }
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
                showCameraCapture = false
                pendingCaptureImageBytes = (pendingCaptureImageBytes + bytes).take(10)
                showMultiPhotoCapture = true
            },
            onOpenGallery = {
                showCameraCapture = false
                isImportingPhotos = true
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = {
                showCameraCapture = false
                if (pendingCaptureImageBytes.isNotEmpty()) {
                    showMultiPhotoCapture = true
                }
            }
        )
    }

    if (showMultiPhotoCapture && pendingCaptureImageBytes.isNotEmpty()) {
        MultiPhotoCaptureSheet(
            imageBytesList = pendingCaptureImageBytes,
            addsFromLibrary = isImportingPhotos,
            onAddPhoto = {
                if (pendingCaptureImageBytes.size < 10) {
                    if (isImportingPhotos) {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        showMultiPhotoCapture = false
                        showCameraCapture = true
                    }
                }
            },
            onRemove = { index ->
                pendingCaptureImageBytes = pendingCaptureImageBytes.filterIndexed { itemIndex, _ -> itemIndex != index }
                if (pendingCaptureImageBytes.isEmpty()) showMultiPhotoCapture = false
            },
            onAnalyze = { note ->
                val images = pendingCaptureImageBytes
                pendingCaptureImageBytes = emptyList()
                showMultiPhotoCapture = false
                if (!ui.isEntryAnalysisBusy) vm.analyzePhotos(images, note)
            },
            onDismiss = {
                showMultiPhotoCapture = false
                pendingCaptureImageBytes = emptyList()
            },
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
                vm.updateEntry(entry, updated)
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

    // Restored failed single-photo input — optional note before retry.
    ui.pendingInputImageBytes?.let { bytes ->
        ContextNoteSheet(
            imageBytes = bytes,
            initialNote = ui.pendingInputNote.orEmpty(),
            isSubmitting = ui.isEntryAnalysisBusy,
            onAnalyze = { note ->
                if (!ui.isEntryAnalysisBusy) {
                    vm.analyzePhotoWithNote(bytes, note)
                }
            },
            onAddPhoto = {
                vm.dismissFailedInput()
                openCamera()
            },
            onDismiss = { vm.dismissFailedInput() },
        )
    }

    ui.analysisPhase?.let { phase ->
        EntryAnalysisOverlay(
            phase = phase,
            preview = ui.analysisPreview,
            imageBytes = ui.pendingImageBytes,
        )
    }
    if (ui.analyzing && ui.analysisPhase == null) {
        AnalyzingOverlay(imageBytes = ui.pendingImageBytes)
    }

    ui.pendingAnalysis?.let { analysis ->
        FoodResultSheet(
            analysis = analysis,
            imageBytes = ui.pendingImageBytes,
            preferGramsByDefault = ui.preferGramsByDefault,
            profile = ui.profile,
            dayEntries = ui.todayEntries,
            isSaving = ui.saving,
            inferringUnits = ui.inferringUnits,
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
                    primaryEnabled = !ui.isEntryAnalysisBusy,
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
/** Static home layout for release screenshot previews (no ViewModel / permissions). */
@Composable
internal fun HomeScreenPreviewContent(
    ui: HomeUiState,
    weekStartsOnMonday: Boolean = true,
    freezeAnimations: Boolean = true,
) {
    val selectedDate = ui.date
    val isToday = true
    val mealGroups = remember(ui.todayEntries, ui.foodLogSortOrder) {
        foodLogMealGroups(ui.todayEntries, ui.foodLogSortOrder)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp,
                    bottom = BottomNavScrollPadding,
                ),
            ) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        WeekEnergyStrip(
                            selectedDate = selectedDate,
                            onSelect = {},
                            weekStartsOnMonday = weekStartsOnMonday,
                        )
                    }
                }
                item {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        CalorieHero(
                            current = ui.caloriesToday,
                            baseGoal = ui.gaugeBaseCalorieGoal,
                            activeCalories = ui.displayActiveCalories,
                            displayMode = ui.effectiveCalorieMode,
                            activeCalorieSource = ui.resolvedActiveBurn?.source,
                            freezeProgress = freezeAnimations,
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (ui.homeDisplay.showSteps) {
                                StepsCard(
                                    steps = ui.activitySnapshot.steps,
                                    goal = ui.homeDisplay.stepGoal,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ui.homeTopNutrients.forEach { nutrient ->
                                MacroCard(
                                    label = stringResource(nutrient.displayNameRes),
                                    current = nutrient.current(ui.todayEntries),
                                    goal = nutrient.goal(ui.profile, ui.optionalNutrientGoals),
                                    unit = nutrient.unit,
                                    accentColor = AppColors.nutrientColor(nutrient),
                                    modifier = Modifier.weight(1f),
                                    freezeProgress = freezeAnimations,
                                )
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ViewMoreButton()
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
                if (mealGroups.isEmpty()) {
                    item { SectionHeader(if (isToday) stringResource(R.string.home_todays_food) else stringResource(R.string.home_food_log)) }
                    item {
                        SectionCardWrapper(isFirst = true, isLast = true) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                                Text(
                                    stringResource(R.string.home_no_foods_logged),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                )
                            }
                        }
                    }
                } else {
                    for (group in mealGroups) {
                        item(key = "header-${group.id}") {
                            MealSectionHeader(
                                meal = group.meal,
                                totalCalories = group.totalCalories,
                                totalProtein = group.totalProtein,
                                totalCarbs = group.totalCarbs,
                                totalFat = group.totalFat,
                                macroChips = ui.foodLogMacroChips,
                            )
                        }
                        itemsIndexed(group.entries, key = { _, entry -> entry.id }) { index, entry ->
                            val isFirst = index == 0
                            val isLast = index == group.entries.lastIndex
                            val rowShape = sectionCardShape(isFirst, isLast)
                            SectionCardWrapper(isFirst = isFirst, isLast = isLast, transparent = true) {
                                FoodRow(
                                    entry = entry,
                                    isFavorite = ui.isFavorite(entry),
                                    rowShape = rowShape,
                                    macroChips = ui.foodLogMacroChips,
                                )
                                if (index != group.entries.lastIndex) Divider()
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = {},
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
        }
    }
}
