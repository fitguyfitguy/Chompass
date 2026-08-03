package app.chompass.ui.home

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.util.UUID
import app.chompass.AppContainer
import app.chompass.MainActivity
import app.chompass.R
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.services.FoodPhotoSession
import app.chompass.services.MealShare
import app.chompass.services.ShortcutEntryAction
import app.chompass.services.grounding.GroundedEntryFeature
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.components.InAppCameraCaptureDialog
import app.chompass.ui.components.MacroCard
import app.chompass.ui.components.StepsCard
import app.chompass.ui.components.WeekEnergyStrip
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.navigation.BottomNavDockedControlPadding
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.theme.AppColors
import kotlinx.coroutines.launch

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
    var showVoiceLocal by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var savedMealsTab by remember { mutableStateOf<SavedTab?>(null) }
    var showBarcodeScannerLocal by remember { mutableStateOf(false) }
    var showCopyFromDay by remember { mutableStateOf(false) }
    var showAddFoodSheet by remember { mutableStateOf(false) }
    var hubRecentMeals by remember { mutableStateOf<List<FoodEntry>>(emptyList()) }
    var showCustomWaterLog by remember { mutableStateOf(false) }
    var showManualActive by remember { mutableStateOf(false) }
    var showGroundedEntry by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    var editingRecipe by remember { mutableStateOf<app.chompass.models.Recipe?>(null) }
    var showNutritionDetail by remember { mutableStateOf(false) }

    var showCameraCapture by remember { mutableStateOf(false) }
    var appendPhotoForReanalyze by remember { mutableStateOf(false) }
    val photoSession = container.foodPhotoSession
    val stagedPhotoBytes by photoSession.stagedImages.collectAsState()
    val importFailedTick by photoSession.importFailedTick.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val photoImportFailedMessage = stringResource(R.string.photo_import_failed)

    fun openGalleryPicker() {
        val activity = ctx.findComponentActivity() as? MainActivity ?: return
        if (showCameraCapture) {
            showCameraCapture = false
            // Post after Dialog window teardown so the Activity Result is not lost.
            activity.window.decorView.post {
                activity.launchFoodGalleryPick()
            }
        } else {
            activity.launchFoodGalleryPick()
        }
    }

    fun startAnalyzeFromStaged(images: List<ByteArray>) {
        if (images.isEmpty() || ui.isEntryAnalysisBusy) return
        photoSession.clear()
        vm.analyzePhotos(images)
    }

    LaunchedEffect(importFailedTick) {
        if (importFailedTick == 0) return@LaunchedEffect
        snackbarHostState.showSnackbar(photoImportFailedMessage)
    }

    // Gallery / share staging → analyze immediately (no review sheet).
    LaunchedEffect(stagedPhotoBytes, ui.isEntryAnalysisBusy, appendPhotoForReanalyze, ui.showFoodResultSheet) {
        if (stagedPhotoBytes.isEmpty()) return@LaunchedEffect
        val images = stagedPhotoBytes.toList()
        if (appendPhotoForReanalyze) {
            photoSession.clear()
            appendPhotoForReanalyze = false
            vm.appendPhotosAndReanalyze(images)
            return@LaunchedEffect
        }
        // Don't steal photos while a result sheet is open (unless appending above).
        if (ui.isEntryAnalysisBusy || ui.showFoodResultSheet) return@LaunchedEffect
        startAnalyzeFromStaged(images)
    }

    // Share-sheet photos only. Merge into FoodPhotoSession while RESUMED so a
    // stopped duplicate MainActivity cannot clear the inbox first.
    val sharedImages by container.sharedImageInbox.collectAsState()
    LaunchedEffect(sharedImages, ui.isEntryAnalysisBusy, lifecycleOwner) {
        if (sharedImages.isEmpty()) return@LaunchedEffect
        if (ui.isEntryAnalysisBusy) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val images = container.sharedImageInbox.value
            if (images.isEmpty()) return@repeatOnLifecycle
            container.sharedImageInbox.value = emptyList()
            photoSession.mergeExternalShare(images)
        }
    }

    // Launcher long-press shortcuts (Camera / Voice / Barcode). Keep the action
    // in the app-scoped inbox until the destination UI dismisses — clearing into
    // ephemeral Compose flags alone lost Voice/Barcode after a NavHost remount
    // (e.g. System theme palette refresh on resume), same class of bug as share-ins.
    val shortcutEntry by container.shortcutEntryInbox.collectAsState()
    fun clearShortcut(action: ShortcutEntryAction) {
        if (container.shortcutEntryInbox.value == action) {
            container.shortcutEntryInbox.value = null
        }
    }
    val showVoice =
        showVoiceLocal ||
            (shortcutEntry == ShortcutEntryAction.VOICE && !ui.isEntryAnalysisBusy)
    // Barcode needs CAMERA permission before the sheet is useful — drive visibility
    // from the local flag only; the sticky inbox re-triggers openBarcodeScanner
    // after a remount until dismiss clears it.
    val showBarcodeScanner = showBarcodeScannerLocal

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            photoSession.prepareFreshCameraCapture()
            showCameraCapture = true
        } else {
            clearShortcut(ShortcutEntryAction.CAMERA)
        }
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            photoSession.prepareFreshCameraCapture()
            showCameraCapture = true
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(ui.resumeProgressiveCapture) {
        if (ui.resumeProgressiveCapture) {
            vm.consumeResumeProgressiveCapture()
            openCamera()
        }
    }

    val barcodePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showBarcodeScannerLocal = true
        } else {
            clearShortcut(ShortcutEntryAction.BARCODE)
        }
    }

    fun openBarcodeScanner() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showBarcodeScannerLocal = true
        } else {
            barcodePermission.launch(Manifest.permission.CAMERA)
        }
    }

    // Drive Camera / Barcode openers from the sticky inbox. Voice is derived
    // above (no opener needed). Do not clear here — clear on dismiss/submit.
    LaunchedEffect(shortcutEntry, ui.isEntryAnalysisBusy, lifecycleOwner) {
        if (shortcutEntry == null) return@LaunchedEffect
        if (ui.isEntryAnalysisBusy) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            when (container.shortcutEntryInbox.value) {
                ShortcutEntryAction.CAMERA -> openCamera()
                ShortcutEntryAction.BARCODE -> openBarcodeScanner()
                ShortcutEntryAction.VOICE, null -> return@repeatOnLifecycle
            }
        }
    }

    val today = LocalDate.now()
    val selectedDate = ui.date
    val isToday = selectedDate == today
    val isDark = isDarkTheme()
    val mealGroups = remember(ui.todayEntries, ui.foodLogSortOrder) {
        foodLogMealGroups(ui.todayEntries, ui.foodLogSortOrder)
    }
    var selectedEntryIds by remember(ui.date) { mutableStateOf<Set<UUID>>(emptySet()) }
    val selectedEntries = remember(ui.todayEntries, selectedEntryIds) {
        ui.todayEntries.filter { it.id in selectedEntryIds }
    }
    val inSelectionMode = selectedEntryIds.isNotEmpty()
    val scope = rememberCoroutineScope()
    val foodRemovedMessage = stringResource(R.string.home_food_removed)
    val undoLabel = stringResource(R.string.action_undo)

    // No topBar: the empty TopAppBar used to act as the status-bar spacer, but the
    // ad strip above this screen (TabWithBanner) now owns that inset.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        var horizontalLocked = false
                        val threshold = 120.dp.toPx()
                        val deadZone = 16.dp.toPx()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                accum = 0f
                                horizontalLocked = false
                            },
                            onDragCancel = {
                                accum = 0f
                                horizontalLocked = false
                            },
                            onHorizontalDrag = { change, amount ->
                                accum += amount
                                if (!horizontalLocked && kotlin.math.abs(accum) > deadZone) {
                                    horizontalLocked = true
                                }
                                if (horizontalLocked) {
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (accum > threshold) {
                                    vm.setSelectedDate(selectedDate.minusDays(1))
                                } else if (accum < -threshold) {
                                    val next = selectedDate.plusDays(1)
                                    if (!next.isAfter(today)) vm.setSelectedDate(next)
                                }
                                accum = 0f
                                horizontalLocked = false
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
                        awaitingActiveBurn = ui.homeDisplay.calorieDisplayMode ==
                            HomeCalorieDisplayMode.ADD_ACTIVE &&
                            calorieMode == HomeCalorieDisplayMode.STATIC,
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
                            useMetric = ui.weightMetric,
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
                                    onDelete = {
                                        vm.deleteEntry(entry)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = foodRemovedMessage,
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                vm.restoreEntry(entry)
                                            }
                                        }
                                    },
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
        val draftForChip = ui.progressiveMeal
        if (!ui.showProgressiveMealSheet &&
            draftForChip != null &&
            draftForChip.items.isNotEmpty() &&
            !ui.showFoodResultSheet &&
            !ui.resumeProgressiveCapture &&
            !showCameraCapture
        ) {
            FloatingActionButton(
                onClick = { vm.showProgressiveMealSheet(true) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, bottom = BottomNavDockedControlPadding + 16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    stringResource(R.string.progressive_meal_continue, draftForChip.items.size),
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontWeight = FontWeight.Medium,
                )
            }
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

    LaunchedEffect(showAddFoodSheet) {
        if (showAddFoodSheet) {
            hubRecentMeals = container.foodRepository.quickRelogTemplates(limit = 6)
        }
    }

    if (showAddFoodSheet) {
        AddFoodSheet(
            waterTrackingEnabled = ui.waterTrackingEnabled,
            waterQuickPresetsMl = ui.waterQuickPresetsMl,
            waterUseMetric = ui.weightMetric,
            recentMeals = hubRecentMeals,
            onPhoto = { openCamera() },
            onNote = { showText = true },
            onSavedRecents = { savedMealsTab = SavedTab.RECENTS },
            onVoice = { showVoiceLocal = true },
            onBarcode = { openBarcodeScanner() },
            onManual = { showManual = true },
            onCopyFromDay = { showCopyFromDay = true },
            onManualActive = { showManualActive = true },
            onGrounded = {
                if (GroundedEntryFeature.ENABLED) {
                    showGroundedEntry = true
                }
            },
            onWater = { ml -> vm.addWater(ml) },
            onWaterCustom = { showCustomWaterLog = true },
            onRelogRecent = { vm.relogMeal(it) },
            onReviewRecent = { vm.reviewSavedMeal(it) },
            onDismiss = { showAddFoodSheet = false }
        )
    }

    if (GroundedEntryFeature.ENABLED && showGroundedEntry) {
        GroundedEntrySheet(
            onDismiss = { showGroundedEntry = false },
            isSubmitting = ui.isEntryAnalysisBusy,
            onSubmit = { description, imageBytes ->
                if (!ui.isEntryAnalysisBusy) {
                    showGroundedEntry = false
                    vm.analyzeGrounded(description, imageBytes)
                }
            },
        )
    }

    if (GroundedEntryFeature.ENABLED) {
        ui.pendingGroundedReview?.let { review ->
            GroundedCandidateSheet(
                review = review,
                onDismiss = vm::dismissGroundedReview,
                isSubmitting = ui.isEntryAnalysisBusy,
                onConfirm = { selected, grams ->
                    vm.resolveGroundedChoices(selected, grams)
                },
            )
        }
    }

    if (showCustomWaterLog) {
        WaterCustomAmountSheet(
            useMetric = ui.weightMetric,
            onDismiss = { showCustomWaterLog = false },
            onAdd = vm::addWater,
        )
    }

    if (showManualActive) {
        ManualActiveSheet(
            onSave = { name, kcal -> vm.addManualActive(name, kcal) },
            onDismiss = { showManualActive = false },
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
            onDismiss = {
                showVoiceLocal = false
                clearShortcut(ShortcutEntryAction.VOICE)
            },
            isSubmitting = ui.isEntryAnalysisBusy,
            onSubmit = {
                if (!ui.isEntryAnalysisBusy) {
                    showVoiceLocal = false
                    clearShortcut(ShortcutEntryAction.VOICE)
                    vm.analyzeText(it)
                }
            }
        )
    }

    if (showManual) {
        ManualEntryDialog(
            isSaving = ui.saving,
            onDismiss = { showManual = false },
            onSave = { name, kcal, p, c, f, fiber, meal ->
                if (!ui.saving) {
                    showManual = false
                    vm.saveManualEntry(name, kcal, p, c, f, fiber, meal)
                }
            }
        )
    }

    savedMealsTab?.let { tab ->
        SavedMealsSheet(
            container = container,
            initialTab = tab,
            onDismiss = { savedMealsTab = null },
            // Tapping a Saved Meals row opens the FoodResultSheet for review
            // instead of logging immediately — same UX as the photo flow.
            onRelogEntry = { vm.reviewSavedMeal(it) },
            onLogEntry = { vm.relogMeal(it) },
            onLogRecipe = { vm.logRecipe(it) },
            onEditRecipe = { recipe -> savedMealsTab = null; editingRecipe = recipe },
            onCreateRecipe = {
                savedMealsTab = null
                editingRecipe = app.chompass.models.Recipe(name = "")
            }
        )
    }

    editingRecipe?.let { recipe ->
        RecipeBuilderSheet(
            container = container,
            recipe = recipe,
            onDismiss = { editingRecipe = null },
            onSave = { updated ->
                vm.saveRecipe(updated)
                editingRecipe = null
            },
            onLogNow = { updated ->
                vm.saveRecipe(updated)
                vm.logRecipe(updated)
                editingRecipe = null
            }
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
                showBarcodeScannerLocal = false
                clearShortcut(ShortcutEntryAction.BARCODE)
                vm.lookupBarcode(barcode)
            },
            onDismiss = {
                showBarcodeScannerLocal = false
                clearShortcut(ShortcutEntryAction.BARCODE)
            }
        )
    }

    if (showCameraCapture) {
        InAppCameraCaptureDialog(
            showScaleTip = !ui.hasSeenCameraScaleTip,
            onScaleTipDismissed = vm::dismissCameraScaleTip,
            onCapture = { bytes ->
                showCameraCapture = false
                clearShortcut(ShortcutEntryAction.CAMERA)
                if (appendPhotoForReanalyze) {
                    appendPhotoForReanalyze = false
                    vm.appendPhotosAndReanalyze(listOf(bytes))
                } else {
                    photoSession.clear()
                    if (!ui.isEntryAnalysisBusy) vm.analyzePhotos(listOf(bytes))
                }
            },
            onOpenGallery = { openGalleryPicker() },
            onDismiss = {
                showCameraCapture = false
                clearShortcut(ShortcutEntryAction.CAMERA)
                appendPhotoForReanalyze = false
            }
        )
    }

    editingEntry?.let { entry ->
        EditFoodEntrySheet(
            entry = entry,
            preferGramsByDefault = ui.preferGramsByDefault,
            onReprocess = { updatedNote, onProgress ->
                vm.reprocessFoodEntry(entry, updatedNote, onProgress)
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

    if (ui.showFoodResultSheet) {
        FoodResultSheet(
            analysis = ui.pendingAnalysis,
            imageBytes = ui.pendingImageBytes,
            preferGramsByDefault = ui.preferGramsByDefault,
            profile = ui.profile,
            dayEntries = ui.todayEntries,
            isSaving = ui.saving,
            inferringUnits = ui.inferringUnits,
            analysisPhase = ui.analysisPhase,
            partial = ui.analysisPartial,
            analysisReady = ui.analysisReadyForEdit,
            imageCount = ui.pendingAnalysisImages.size.coerceAtLeast(
                if (ui.pendingImageBytes != null) 1 else 0,
            ),
            source = ui.pendingReviewSource?.source
                ?: ui.pendingFoodSource
                ?: if (ui.pendingImageBytes != null) FoodSource.SNAP_FOOD else FoodSource.TEXT_INPUT,
            portionClarifyEnabled = ui.portionClarifyEnabled,
            portionPreConfirmed = ui.pendingPortionPreConfirmed,
            progressiveMealActive = ui.progressiveMeal?.items?.isNotEmpty() == true,
            onReprocessPortion = { answer -> vm.reprocessPendingAnalysis(answer) },
            onWhatIfSuggestion = vm::suggestMealWhatIf,
            onReanalyzeWithTip = if (ui.pendingImageBytes != null || ui.pendingAnalysisImages.isNotEmpty()) {
                { note, grams -> vm.reanalyzeWithTip(note, grams) }
            } else {
                null
            },
            onAddPhoto = if (
                (ui.pendingImageBytes != null || ui.pendingAnalysisImages.isNotEmpty()) &&
                ui.pendingAnalysisImages.size < FoodPhotoSession.MAX_IMAGES
            ) {
                {
                    appendPhotoForReanalyze = true
                    openCamera()
                }
            } else {
                null
            },
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
            onAddToProgressiveMeal = { name, grams, _, mealType, selectedServingUnit, selectedServingQuantity, editedAnalysis, resumeCapture ->
                vm.addToProgressiveMeal(
                    name = name,
                    servingGrams = grams,
                    mealType = mealType,
                    selectedServingUnit = selectedServingUnit,
                    selectedServingQuantity = selectedServingQuantity,
                    editedAnalysis = editedAnalysis,
                    resumeCapture = resumeCapture,
                )
            },
            onDismiss = { vm.dismissPending() }
        )
    }

    val progressiveDraft = ui.progressiveMeal
    if (ui.showProgressiveMealSheet && progressiveDraft != null &&
        !ui.showFoodResultSheet
    ) {
        ProgressiveMealSheet(
            draft = progressiveDraft,
            isSaving = ui.saving,
            onNameChange = { vm.updateProgressiveMealMeta(it, progressiveDraft.mealType) },
            onMealTypeChange = { vm.updateProgressiveMealMeta(progressiveDraft.name, it) },
            onRemoveItem = { vm.removeProgressiveMealItem(it) },
            onAddAnother = { vm.continueProgressiveCapture() },
            onLogMeal = { vm.logProgressiveMeal() },
            onDiscard = { vm.discardProgressiveMeal() },
            onDismiss = { vm.showProgressiveMealSheet(false) },
        )
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

/** Unwrap LocalContext through ContextWrappers to the hosting ComponentActivity. */
private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return current as? ComponentActivity
}
