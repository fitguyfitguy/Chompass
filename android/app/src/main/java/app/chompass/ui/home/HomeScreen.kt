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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import app.chompass.AppContainer
import app.chompass.MainActivity
import app.chompass.R
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.WaterAmountFormat
import app.chompass.services.FoodPhotoSession
import app.chompass.services.MealShare
import app.chompass.services.ShortcutEntryAction
import app.chompass.services.grounding.GroundedEntryFeature
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.InAppCameraCaptureDialog
import app.chompass.ui.components.MacroCard
import app.chompass.ui.components.StepsCard
import app.chompass.ui.components.WeekEnergyStrip
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.util.clockTimePattern
import app.chompass.ui.navigation.BottomNavDockedControlPadding
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, onOpenSettings: (() -> Unit)? = null) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val clockFormatter = remember(ctx) {
        DateTimeFormatter.ofPattern(clockTimePattern(ctx), Locale.getDefault())
    }
    val weekStartsOnMonday by container.prefs.weekStartsOnMonday.collectAsState(initial = true)
    // Codeberg #20 phase 2: master AI-features switch — hides the AI entry tiles
    // and the What-if row, and ignores the camera/voice launcher shortcuts.
    val aiFeaturesEnabled by container.prefs.aiFeaturesEnabled.collectAsState(initial = true)
    // Codeberg #30: last add-food destination tile ("" = the grid). "+"
    // restores it directly; backing out of the destination returns to the grid.
    val lastAddFoodTool by container.prefs.lastAddFoodTool.collectAsState(initial = "")
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) vm.refreshActivitySnapshot()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Upstream #190: sheet-open flags are rememberSaveable so Activity recreation
    // (rotation, theme change) keeps the open dialog/sheet instead of dropping
    // the user back to the diary. Complex values (entries, recipes, recent list)
    // are not Bundle-saveable and stay plain remember; photo bytes already live
    // app-scoped in FoodPhotoSession, never in saveable state.
    var showText by rememberSaveable { mutableStateOf(false) }
    var showVoiceLocal by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var savedMealsTab by rememberSaveable { mutableStateOf<SavedTab?>(null) }
    var showBarcodeScannerLocal by rememberSaveable { mutableStateOf(false) }
    var showCopyFromDay by rememberSaveable { mutableStateOf(false) }
    var showAddFoodSheet by rememberSaveable { mutableStateOf(false) }
    var hubRecentMeals by remember { mutableStateOf<List<FoodEntry>>(emptyList()) }
    var showCustomWaterLog by rememberSaveable { mutableStateOf(false) }
    var showManualActive by rememberSaveable { mutableStateOf(false) }
    var showGroundedEntry by rememberSaveable { mutableStateOf(false) }
    var showFoodSearch by rememberSaveable { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    var editingRecipe by remember { mutableStateOf<app.chompass.models.Recipe?>(null) }
    var showNutritionDetail by rememberSaveable { mutableStateOf(false) }
    // Codeberg #30: add-food flow. Tapping a tile (or the "+" FAB restoring
    // the last tool) marks the flow active; backing out of a flow-launched
    // destination reopens the grid instead of closing the whole flow.
    // Shortcut/share/gallery-launched sheets never enter the flow, so their
    // dismissals close normally.
    var addFoodFlowActive by rememberSaveable { mutableStateOf(false) }

    var showCameraCapture by rememberSaveable { mutableStateOf(false) }
    /** When true, next capture/gallery pick appends into an in-flight analysis re-run. */
    var appendPhotoForReanalyze by rememberSaveable { mutableStateOf(false) }
    var appendReanalyzeNote by rememberSaveable { mutableStateOf<String?>(null) }
    var appendReanalyzeGrams by rememberSaveable { mutableStateOf<Double?>(null) }
    var showAppendPhotoChooser by rememberSaveable { mutableStateOf(false) }
    /** When true, camera opens without clearing staged photos (Add label / Add photo). */
    var appendToStagedPhotos by rememberSaveable { mutableStateOf(false) }
    val photoSession = container.foodPhotoSession
    val stagedPhotoBytes by photoSession.stagedImages.collectAsState()
    val showMultiPhotoCapture by photoSession.reviewOpen.collectAsState()
    val isImportingPhotos by photoSession.importFromLibrary.collectAsState()
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

    LaunchedEffect(importFailedTick) {
        if (importFailedTick == 0) return@LaunchedEffect
        snackbarHostState.showSnackbar(photoImportFailedMessage)
    }

    // Mid-flight "Add photo" from the Log sheet only — never auto-start LLM on staging.
    LaunchedEffect(stagedPhotoBytes, appendPhotoForReanalyze) {
        if (stagedPhotoBytes.isEmpty() || !appendPhotoForReanalyze) return@LaunchedEffect
        val images = stagedPhotoBytes.toList()
        val note = appendReanalyzeNote
        val grams = appendReanalyzeGrams
        photoSession.clear()
        appendPhotoForReanalyze = false
        appendReanalyzeNote = null
        appendReanalyzeGrams = null
        vm.appendPhotosAndReanalyze(images, note, grams)
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
    val showVoice = aiFeaturesEnabled &&
        (showVoiceLocal ||
            (shortcutEntry == ShortcutEntryAction.VOICE && !ui.isEntryAnalysisBusy))
    // Barcode needs CAMERA permission before the sheet is useful — drive visibility
    // from the local flag only; the sticky inbox re-triggers openBarcodeScanner
    // after a remount until dismiss clears it.
    val showBarcodeScanner = showBarcodeScannerLocal

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            appendToStagedPhotos = false
            photoSession.prepareFreshCameraCapture()
            showCameraCapture = true
        } else {
            clearShortcut(ShortcutEntryAction.CAMERA)
            addFoodFlowActive = false
        }
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            appendToStagedPhotos = false
            photoSession.prepareFreshCameraCapture()
            showCameraCapture = true
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    /** Add another photo/label without wiping the staging sheet. */
    fun openCameraAppendStaged() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            appendToStagedPhotos = true
            photoSession.hideReviewKeepStaged()
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
            addFoodFlowActive = false
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
    LaunchedEffect(shortcutEntry, ui.isEntryAnalysisBusy, aiFeaturesEnabled, lifecycleOwner) {
        if (shortcutEntry == null) return@LaunchedEffect
        if (ui.isEntryAnalysisBusy) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            when (container.shortcutEntryInbox.value) {
                // AI logging is off: camera/voice shortcuts are no-ops (cleared so
                // they cannot re-fire after a remount); barcode stays (OFF lookup).
                ShortcutEntryAction.CAMERA ->
                    if (aiFeaturesEnabled) openCamera() else clearShortcut(ShortcutEntryAction.CAMERA)
                ShortcutEntryAction.BARCODE -> openBarcodeScanner()
                ShortcutEntryAction.VOICE ->
                    if (aiFeaturesEnabled) return@repeatOnLifecycle else clearShortcut(ShortcutEntryAction.VOICE)
                null -> return@repeatOnLifecycle
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

    // Codeberg #30: add-food flow helpers. launchAddFoodTool persists the
    // tool id so the next "+" skips the grid (photo excluded — reopening it
    // would re-prompt the camera permission), then opens the destination.
    fun launchAddFoodTool(tool: AddFoodTool) {
        if (tool.persists) {
            scope.launch { container.prefs.setLastAddFoodTool(tool.storageId) }
        }
        addFoodFlowActive = true
        when (tool) {
            AddFoodTool.PHOTO -> openCamera()
            AddFoodTool.NOTE -> showText = true
            AddFoodTool.SAVED_RECENTS -> savedMealsTab = SavedTab.RECENTS
            AddFoodTool.VOICE -> showVoiceLocal = true
            AddFoodTool.BARCODE -> openBarcodeScanner()
            AddFoodTool.MANUAL -> showManual = true
            AddFoodTool.COPY_FROM_DAY -> showCopyFromDay = true
            AddFoodTool.SEARCH -> showFoodSearch = true
            AddFoodTool.MANUAL_ACTIVE -> showManualActive = true
            AddFoodTool.GROUNDED -> if (GroundedEntryFeature.ENABLED) showGroundedEntry = true
        }
    }

    /** Back from a flow-launched destination: reopen the grid. No-op when the
     *  destination came from outside the flow (shortcut, share, gallery). */
    fun returnToAddFoodGrid() {
        if (addFoodFlowActive) showAddFoodSheet = true
    }

    // No topBar: the empty TopAppBar used to act as the status-bar spacer, but the
    // ad strip above this screen (TabWithBanner) now owns that inset.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            // Custom docked bottom nav overlays the Scaffold; lift the host so
            // snackbars (delete-undo, paste confirmation) are not hidden behind it.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = BottomNavDockedControlPadding + 16.dp),
            )
        },
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
                        showActiveCalories = ui.homeDisplay.showActiveCalories,
                        liveActiveBurn = ui.liveActiveBurn,
                        burnShade = ui.activeBurnShade,
                        restingBurn = ui.restingBurnToday,
                        showRestingShade = ui.showRestingBurnShade,
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
                                goal = nutrient.goal(ui.profile, ui.optionalNutrientGoals, ui.macroGoalScale),
                                unit = nutrient.unit,
                                accentColor = AppColors.nutrientColor(nutrient),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (ui.waterTrackingEnabled) {
                        Spacer(Modifier.height(12.dp))
                        val nextDrinkLabel = ui.waterNextPlan?.let { plan ->
                            val amount = if (ui.weightMetric) {
                                stringResource(R.string.water_amount_ml, plan.drinkMl)
                            } else {
                                stringResource(
                                    R.string.water_amount_fl_oz,
                                    WaterAmountFormat.flOzFromMl(plan.drinkMl),
                                )
                            }
                            val fireZone = Instant.ofEpochMilli(plan.nextFireMillis)
                                .atZone(ZoneId.systemDefault())
                            val time = fireZone.format(clockFormatter)
                            if (fireZone.toLocalDate().isAfter(LocalDate.now())) {
                                stringResource(R.string.home_water_next_tomorrow, amount, time)
                            } else {
                                stringResource(R.string.home_water_next, amount, time)
                            }
                        }
                        WaterProgressRow(
                            current = ui.waterTodayMl,
                            goal = ui.waterDailyGoalMl,
                            useMetric = ui.weightMetric,
                            auto = ui.waterGoalDynamic,
                            onAutoClick = onOpenSettings,
                            nextDrinkLabel = nextDrinkLabel,
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
                            totalFiber = group.totalFiber,
                            totalSugar = group.totalSugar,
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
            onClick = {
                // Warm the hub recents while the sheet animates open.
                vm.prefetchQuickRelog()
                // Codeberg #30: "+" reopens the last add-food tool directly
                // (grid skipped). Unavailable or never-used tools fall back to
                // the grid; backing out of the destination returns here.
                val restore = resolveAddFoodRestoreTool(lastAddFoodTool, aiFeaturesEnabled)
                if (restore != null) {
                    launchAddFoodTool(restore)
                } else {
                    addFoodFlowActive = true
                    showAddFoodSheet = true
                }
            },
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
        val showProgressiveChip = !ui.showProgressiveMealSheet &&
            draftForChip != null &&
            draftForChip.items.isNotEmpty() &&
            !ui.showFoodResultSheet &&
            !ui.resumeProgressiveCapture &&
            !showCameraCapture &&
            !showMultiPhotoCapture
        if (showProgressiveChip) {
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
        // Paste chip: diary rows copied via the selection bar, ready to be
        // pasted onto the viewed day (same or another day). In-memory clipboard.
        val copied = ui.copiedEntries
        if (copied.isNotEmpty() && !inSelectionMode && !showProgressiveChip) {
            val pasteBusy = ui.saving
            val paste: () -> Unit = {
                if (!pasteBusy) vm.copyEntriesToSelectedDay(copied)
            }
            FudGlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = BottomNavDockedControlPadding + 16.dp)
                    .fillMaxWidth()
                    .alpha(if (pasteBusy) 0.55f else 1f)
                    .clickable(enabled = !pasteBusy, onClick = paste)
                    .zIndex(1f),
                cornerRadius = 22.dp,
                padding = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(
                            if (pasteBusy) R.string.paste_busy else R.string.paste_n_entries,
                            if (pasteBusy) 0 else copied.size
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    )
                    IconButton(
                        onClick = { vm.clearCopiedEntries() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_dismiss_paste),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        if (inSelectionMode) {
            SelectionActionBar(
                selectedCount = selectedEntryIds.size,
                onCancel = { selectedEntryIds = emptySet() },
                onCopy = {
                    vm.setCopiedEntries(selectedEntries)
                    selectedEntryIds = emptySet()
                },
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
            hubRecentMeals = vm.quickRelogTemplatesCached()
        }
    }

    if (showAddFoodSheet) {
        AddFoodSheet(
            aiFeaturesEnabled = aiFeaturesEnabled,
            waterTrackingEnabled = ui.waterTrackingEnabled,
            waterQuickPresetsMl = ui.waterQuickPresetsMl,
            waterUseMetric = ui.weightMetric,
            recentMeals = hubRecentMeals,
            onPhoto = { launchAddFoodTool(AddFoodTool.PHOTO) },
            onNote = { launchAddFoodTool(AddFoodTool.NOTE) },
            onSavedRecents = { launchAddFoodTool(AddFoodTool.SAVED_RECENTS) },
            onVoice = { launchAddFoodTool(AddFoodTool.VOICE) },
            onBarcode = { launchAddFoodTool(AddFoodTool.BARCODE) },
            onManual = { launchAddFoodTool(AddFoodTool.MANUAL) },
            onCopyFromDay = { launchAddFoodTool(AddFoodTool.COPY_FROM_DAY) },
            onManualActive = { launchAddFoodTool(AddFoodTool.MANUAL_ACTIVE) },
            onGrounded = { launchAddFoodTool(AddFoodTool.GROUNDED) },
            onSearch = { launchAddFoodTool(AddFoodTool.SEARCH) },
            onWater = { ml -> vm.addWater(ml) },
            onWaterCustom = { showCustomWaterLog = true },
            onRelogRecent = { vm.relogMeal(it) },
            onReviewRecent = { vm.reviewSavedMeal(it) },
            onDismiss = {
                showAddFoodSheet = false
                addFoodFlowActive = false
            }
        )
    }

    if (GroundedEntryFeature.ENABLED && showGroundedEntry) {
        GroundedEntrySheet(
            onDismiss = {
                showGroundedEntry = false
                returnToAddFoodGrid()
            },
            isSubmitting = ui.isEntryAnalysisBusy,
            onSubmit = { description, imageBytes ->
                if (!ui.isEntryAnalysisBusy) {
                    showGroundedEntry = false
                    addFoodFlowActive = false
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

    if (showFoodSearch) {
        FoodDatabaseSearchSheet(
            container = container,
            onSelect = { result ->
                showFoodSearch = false
                addFoodFlowActive = false
                vm.selectFoodSearchResult(result)
            },
            onDismiss = {
                showFoodSearch = false
                returnToAddFoodGrid()
            },
        )
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
            onSave = { name, kcal ->
                vm.addManualActive(name, kcal)
                addFoodFlowActive = false
            },
            onDismiss = {
                showManualActive = false
                returnToAddFoodGrid()
            },
        )
    }

    if (showText) {
        TextInputSheet(
            onDismiss = {
                showText = false
                returnToAddFoodGrid()
            },
            isSubmitting = ui.isEntryAnalysisBusy,
            onSubmit = {
                if (!ui.isEntryAnalysisBusy) {
                    showText = false
                    addFoodFlowActive = false
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
                returnToAddFoodGrid()
            },
            isSubmitting = ui.isEntryAnalysisBusy,
            onSubmit = {
                if (!ui.isEntryAnalysisBusy) {
                    showVoiceLocal = false
                    clearShortcut(ShortcutEntryAction.VOICE)
                    addFoodFlowActive = false
                    vm.analyzeText(it)
                }
            }
        )
    }

    if (showManual) {
        ManualEntryDialog(
            isSaving = ui.saving,
            onDismiss = {
                showManual = false
                returnToAddFoodGrid()
            },
            onSave = { name, kcal, p, c, f, micros, meal, servingGrams, unitOptions, selUnit, selQty ->
                if (!ui.saving) {
                    showManual = false
                    addFoodFlowActive = false
                    vm.saveManualEntry(
                        name, kcal, p, c, f, micros, meal,
                        servingGrams, unitOptions, selUnit, selQty,
                    )
                }
            }
        )
    }

    savedMealsTab?.let { tab ->
        SavedMealsSheet(
            container = container,
            initialTab = tab,
            onDismiss = {
                savedMealsTab = null
                returnToAddFoodGrid()
            },
            // Tapping a Saved Meals row opens the FoodResultSheet for review
            // instead of logging immediately — same UX as the photo flow.
            // The sheet dismisses itself after every action, so each one ends
            // the flow: only a plain back (no action) returns to the grid.
            onRelogEntry = { entry ->
                addFoodFlowActive = false
                vm.reviewSavedMeal(entry)
            },
            onLogEntry = { entry ->
                addFoodFlowActive = false
                vm.relogMeal(entry)
            },
            onLogRecipe = { recipe ->
                addFoodFlowActive = false
                vm.logRecipe(recipe)
            },
            onEditRecipe = { recipe ->
                savedMealsTab = null
                addFoodFlowActive = false
                editingRecipe = recipe
            },
            onCreateRecipe = {
                savedMealsTab = null
                addFoodFlowActive = false
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
            container = container,
            targetDate = ui.date,
            isSaving = ui.saving,
            onCopy = { entries, target ->
                if (!ui.saving) {
                    vm.copyEntriesToDate(entries, target)
                    showCopyFromDay = false
                    addFoodFlowActive = false
                }
            },
            onDismiss = {
                showCopyFromDay = false
                returnToAddFoodGrid()
            }
        )
    }

    if (showBarcodeScanner) {
        BarcodeScannerSheet(
            onBarcode = { barcode ->
                showBarcodeScannerLocal = false
                clearShortcut(ShortcutEntryAction.BARCODE)
                addFoodFlowActive = false
                vm.lookupBarcode(barcode)
            },
            onDismiss = {
                showBarcodeScannerLocal = false
                clearShortcut(ShortcutEntryAction.BARCODE)
                returnToAddFoodGrid()
            }
        )
    }

    if (showAppendPhotoChooser) {
        FudGlassDialog(
            onDismissRequest = {
                showAppendPhotoChooser = false
                appendReanalyzeNote = null
                appendReanalyzeGrams = null
            },
        ) {
            Text(
                stringResource(R.string.add_food_photo_source_title),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.add_food_photo_camera),
                onPrimary = {
                    showAppendPhotoChooser = false
                    appendPhotoForReanalyze = true
                    openCamera()
                },
                dismissText = stringResource(R.string.add_food_photo_gallery),
                onDismiss = {
                    showAppendPhotoChooser = false
                    appendPhotoForReanalyze = true
                    openGalleryPicker()
                },
            )
            TextButton(
                onClick = {
                    showAppendPhotoChooser = false
                    appendReanalyzeNote = null
                    appendReanalyzeGrams = null
                },
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }

    if (showCameraCapture) {
        InAppCameraCaptureDialog(
            showScaleTip = !ui.hasSeenCameraScaleTip,
            onScaleTipDismissed = vm::dismissCameraScaleTip,
            onCapture = { bytes ->
                showCameraCapture = false
                clearShortcut(ShortcutEntryAction.CAMERA)
                when {
                    appendPhotoForReanalyze -> {
                        appendPhotoForReanalyze = false
                        appendToStagedPhotos = false
                        val note = appendReanalyzeNote
                        val grams = appendReanalyzeGrams
                        appendReanalyzeNote = null
                        appendReanalyzeGrams = null
                        vm.appendPhotosAndReanalyze(listOf(bytes), note, grams)
                    }
                    else -> {
                        // Stage into pre-Analyze sheet (do not call the LLM yet).
                        appendToStagedPhotos = false
                        photoSession.stageFromCamera(bytes)
                    }
                }
            },
            onOpenGallery = { openGalleryPicker() },
            onDismiss = {
                showCameraCapture = false
                clearShortcut(ShortcutEntryAction.CAMERA)
                appendPhotoForReanalyze = false
                appendToStagedPhotos = false
                appendReanalyzeNote = null
                appendReanalyzeGrams = null
                photoSession.openReviewIfStaged()
                returnToAddFoodGrid()
            }
        )
    }

    if (showMultiPhotoCapture && stagedPhotoBytes.isNotEmpty()) {
        // Staging always wins over a leftover Log sheet so the LLM never runs
        // under the note/add-photo UI.
        LaunchedEffect(Unit) {
            if (ui.showFoodResultSheet) vm.dismissPending()
        }
        MultiPhotoCaptureSheet(
            imageBytesList = stagedPhotoBytes,
            addsFromLibrary = isImportingPhotos,
            showScaleTip = ui.progressiveMeal?.items?.isNotEmpty() == true,
            requireNote = !ui.skipPhotoNotePrompt,
            showDontAskAgain = !ui.skipPhotoNotePrompt &&
                ui.photoNoteSkipCount >= HomeViewModel.PHOTO_NOTE_SKIP_OFFER_THRESHOLD,
            showAccuracyGuide = ui.photoAccuracyGuideCount < HomeViewModel.PHOTO_ACCURACY_GUIDE_COUNT,
            onAddPhoto = {
                if (stagedPhotoBytes.size < FoodPhotoSession.MAX_IMAGES) {
                    if (isImportingPhotos) {
                        openGalleryPicker()
                    } else {
                        openCameraAppendStaged()
                    }
                }
            },
            onRemove = { index ->
                photoSession.removeAt(index)
            },
            onAnalyze = { note, grams, dontAskAgain ->
                val images = photoSession.stagedImages.value
                photoSession.clear()
                if (!ui.isEntryAnalysisBusy) {
                    addFoodFlowActive = false
                    vm.analyzePhotosFromStaging(images, note, grams, dontAskAgain)
                }
            },
            onDismiss = {
                photoSession.clear()
                returnToAddFoodGrid()
            },
        )
    }

    editingEntry?.let { entry ->
        EditFoodEntrySheet(
            entry = entry,
            preferGramsByDefault = ui.preferGramsByDefault,
            aiFeaturesEnabled = aiFeaturesEnabled,
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
            macroScale = ui.macroGoalScale,
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

    if (ui.showFoodResultSheet && !showMultiPhotoCapture) {
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
            portionClarifyEnabled = ui.portionClarifyEnabled && aiFeaturesEnabled,
            portionPreConfirmed = ui.pendingPortionPreConfirmed,
            progressiveMealActive = ui.progressiveMeal?.items?.isNotEmpty() == true,
            onReprocessPortion = if (aiFeaturesEnabled) { answer -> vm.reprocessPendingAnalysis(answer) } else null,
            onWhatIfSuggestion = if (aiFeaturesEnabled) vm::suggestMealWhatIf else null,
            onReanalyzeWithTip = if (
                aiFeaturesEnabled &&
                (ui.pendingImageBytes != null || ui.pendingAnalysisImages.isNotEmpty())
            ) {
                { note, grams -> vm.reanalyzeWithTip(note, grams) }
            } else {
                null
            },
            onAddPhoto = if (
                aiFeaturesEnabled &&
                (ui.pendingImageBytes != null || ui.pendingAnalysisImages.isNotEmpty()) &&
                ui.pendingAnalysisImages.size < FoodPhotoSession.MAX_IMAGES
            ) {
                { note, grams ->
                    appendReanalyzeNote = note
                    appendReanalyzeGrams = grams
                    showAppendPhotoChooser = true
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
    showRestingShade: Boolean = SHOW_RESTING_BURN_SHADE,
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
                            showActiveCalories = ui.homeDisplay.showActiveCalories,
                            liveActiveBurn = ui.liveActiveBurn,
                            burnShade = ui.activeBurnShade,
                            restingBurn = ui.restingBurnToday,
                            showRestingShade = showRestingShade,
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
                                    goal = nutrient.goal(ui.profile, ui.optionalNutrientGoals, ui.macroGoalScale),
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
                                totalFiber = group.totalFiber,
                                totalSugar = group.totalSugar,
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

/**
 * Codeberg #30: add-food destinations. Tapping a tile (or the "+" FAB
 * restoring the last tool) opens the destination; the id persists in
 * PreferencesStore so the next "+" skips the grid. Water quick actions are
 * not destinations and never persist.
 */
enum class AddFoodTool(val storageId: String) {
    PHOTO("photo"),
    NOTE("note"),
    SAVED_RECENTS("savedRecents"),
    VOICE("voice"),
    BARCODE("barcode"),
    MANUAL("manual"),
    COPY_FROM_DAY("copyFromDay"),
    SEARCH("search"),
    MANUAL_ACTIVE("manualActive"),
    GROUNDED("grounded"),
    ;

    /**
     * Destinations that remember their selection. Photo is excluded: reopening
     * it would re-prompt the camera permission. Water quick actions are not
     * tools and never reach this enum.
     */
    val persists: Boolean
        get() = this != PHOTO

    companion object {
        fun fromStorageId(id: String): AddFoodTool? =
            entries.firstOrNull { it.storageId == id }
    }
}

/**
 * Whether the tool can be restored on "+": AI-off hides photo/note/voice and
 * grounded stays behind its feature gate. Mirrors the AddFoodSheet tile
 * visibility rules.
 */
internal fun AddFoodTool.isAvailable(
    aiFeaturesEnabled: Boolean,
    groundedEnabled: Boolean = GroundedEntryFeature.ENABLED,
): Boolean = when (this) {
    AddFoodTool.PHOTO, AddFoodTool.NOTE, AddFoodTool.VOICE -> aiFeaturesEnabled
    AddFoodTool.GROUNDED -> groundedEnabled
    else -> true
}

/**
 * Codeberg #30: restore decision for the "+" FAB. Returns null (grid) when
 * nothing was persisted, the stored id is unknown, or the tool is currently
 * unavailable (e.g. AI-off hides photo/note/voice) — the same runCatching
 * fallback style as SavedMealsSheet's segment restore.
 */
internal fun resolveAddFoodRestoreTool(
    stored: String?,
    aiFeaturesEnabled: Boolean,
    groundedEnabled: Boolean = GroundedEntryFeature.ENABLED,
): AddFoodTool? {
    if (stored.isNullOrBlank()) return null
    return AddFoodTool.fromStorageId(stored)
        ?.takeIf { it.isAvailable(aiFeaturesEnabled, groundedEnabled) }
}
