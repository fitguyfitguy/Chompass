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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import app.chompass.AppContainer
import app.chompass.MainActivity
import app.chompass.R
import app.chompass.models.FoodEntry
import app.chompass.models.LocaleFormat
import app.chompass.models.MacroPlanResolver
import app.chompass.services.grounding.FoodSuggestion
import app.chompass.models.CurrentMealCatalog
import app.chompass.models.FoodSource
import app.chompass.models.CaffeineEntry
import app.chompass.models.NicotineEntry
import app.chompass.models.WaterAmountFormat
import app.chompass.models.builtinCaffeineDefaultMg
import app.chompass.models.HomeTopNutrient
import app.chompass.models.MilkKind
import app.chompass.models.WaterEntry
import app.chompass.services.FoodPhotoSession
import app.chompass.services.MealShare
import app.chompass.services.OpenFoodFactsService
import app.chompass.services.PerfLog
import app.chompass.services.ShortcutEntryAction
import app.chompass.services.grounding.GroundedEntryFeature
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions
import app.chompass.ui.settings.RecalcResultSheet
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.InAppCameraCaptureDialog
import app.chompass.ui.components.MacroCard
import app.chompass.ui.components.StepsCard
import app.chompass.ui.components.WeekEnergyStrip
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.util.formatClockMillis
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.navigation.BottomOverlayPadding
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.dayTypeColor
import app.chompass.ui.theme.nutrientAccentColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenSettings: (() -> Unit)? = null,
    onOpenDayTypes: (() -> Unit)? = null,
    onOpenFoodSettings: (() -> Unit)? = null,
    /** Plan week canvas (meal planning mode). Null where the canvas is not reachable. */
    onPlanWeek: (() -> Unit)? = null,
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val textInputDraft by vm.textInputDraft.collectAsState()
    val seedingSampleData by container.testDataSeeder.seeding.collectAsState()
    val weekStartDay by container.prefs.weekStartDay.collectAsState(initial = app.chompass.models.WeekStartDay.MONDAY)
    // Codeberg #20 phase 2: master AI-features switch — hides the AI entry tiles
    // and the What-if row, and ignores the camera/voice launcher shortcuts.
    val aiFeaturesEnabled by container.prefs.aiFeaturesEnabled.collectAsState(initial = true)
    val useSystemDateTimePickers by container.prefs.useSystemDateTimePickers.collectAsState(initial = false)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) vm.refreshActivitySnapshot()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Codeberg #53 prompt history: distinct saved analysis prompts (queue
    // entries, newest first), capped, for the note-input sheets' auto-fill.
    val recentPrompts = remember(ui.queueEntries) {
        ui.queueEntries
            .asSequence()
            .mapNotNull { it.note?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .take(10)
            .toList()
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
    var hubOpenedAtNs by remember { mutableLongStateOf(0L) }
    var showCustomWaterLog by rememberSaveable { mutableStateOf(false) }
    var showWaterHistory by rememberSaveable { mutableStateOf(false) }
    var editingWaterEntry by remember { mutableStateOf<WaterEntry?>(null) }
    var showDailyNoteEditor by rememberSaveable { mutableStateOf(false) }
    var showNicotineCustom by rememberSaveable { mutableStateOf(false) }
    var showNicotineHistory by rememberSaveable { mutableStateOf(false) }
    var editingNicotineEntry by remember { mutableStateOf<NicotineEntry?>(null) }
    var showCaffeineCustom by rememberSaveable { mutableStateOf(false) }
    var showCaffeineHistory by rememberSaveable { mutableStateOf(false) }
    var editingCaffeineEntry by remember { mutableStateOf<CaffeineEntry?>(null) }
    var showManualActive by rememberSaveable { mutableStateOf(false) }
    var editingManualActive by remember { mutableStateOf<app.chompass.models.ManualActiveEntry?>(null) }
    var showGroundedEntry by rememberSaveable { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    var editingRecipe by remember { mutableStateOf<app.chompass.models.Recipe?>(null) }
    // Codeberg #66: favorite currently open in the saved-food editor.
    var editingFavorite by remember { mutableStateOf<FoodEntry?>(null) }
    var nutritionDetailScope by rememberSaveable { mutableStateOf<String?>(null) }
    // #60: day-type quick-switch sheet (hero chip).
    var showDayTypeSheet by rememberSaveable { mutableStateOf(false) }
    var showUntrackedSheet by rememberSaveable { mutableStateOf(false) }
    // Codeberg #30: add-food flow. Tapping a tile (or the "+" FAB) marks the
    // flow active; backing out of a flow-launched destination reopens the grid
    // instead of closing the whole flow. Shortcut/share/gallery-launched sheets
    // never enter the flow, so their dismissals close normally. (A "+" that
    // restores the last tool was tried and removed on the maintainer device
    // pass 2026-08-18 — "+" always opens the grid.)
    var addFoodFlowActive by rememberSaveable { mutableStateOf(false) }

    var showCameraCapture by rememberSaveable { mutableStateOf(false) }
    /** Photo review's initial note, refreshed before adding another image. */
    var initialPhotoNote by rememberSaveable { mutableStateOf("") }
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
    // Meal planning: suggestion-row Plan action parks the pick here; the
    // day-picker dialog below turns it into a planned entry.
    var planForDayTarget by remember { mutableStateOf<FoodSuggestion?>(null) }

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
        initialPhotoNote = ""
        snackbarHostState.showSnackbar(photoImportFailedMessage)
    }

    // One-shot "planned for <day>" confirmation after a plan action.
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        val plannedForPattern = stringResource(R.string.home_planned_for)
    val plannedDateFormat = remember { LocaleFormat.shortDate() }
    LaunchedEffect(ui.plannedAck) {
        val ack = ui.plannedAck ?: return@LaunchedEffect
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar(plannedForPattern.format(ack.second.format(plannedDateFormat)))
        vm.clearPlannedAck()
    }

    // One-time-per-session nudge when a Health Connect mirror write stayed queued.
    val healthSyncFailedMessage = stringResource(R.string.health_sync_failed)
    LaunchedEffect(ui.healthSyncFailed) {
        if (!ui.healthSyncFailed) return@LaunchedEffect
        snackbarHostState.showSnackbar(healthSyncFailedMessage)
        vm.ackHealthSyncFailed()
    }

    // Plan week canvas handoff: open Home on the tapped chip's day.
    LaunchedEffect(Unit) {
        container.planWeekReturnDay.collect { day ->
            if (day != null) {
                vm.setSelectedDate(day)
                container.planWeekReturnDay.value = null
            }
        }
    }

    // Mid-flight "Add photo" from the Log sheet only — never auto-start LLM on staging.
    // Keyed on booleans: the array identity changed on every session update and
    // re-launched this effect for nothing.
    LaunchedEffect(stagedPhotoBytes.isNotEmpty(), appendPhotoForReanalyze) {
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
    // Busy flips on every analysis phase; read it through the updated state
    // and wait it out instead of restarting repeatOnLifecycle per flip.
    val analysisBusy by rememberUpdatedState(ui.isEntryAnalysisBusy)
    LaunchedEffect(sharedImages, lifecycleOwner) {
        if (sharedImages.isEmpty()) return@LaunchedEffect
        snapshotFlow { analysisBusy }.first { !it }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val images = container.sharedImageInbox.value
            if (images.isEmpty() || analysisBusy) return@repeatOnLifecycle
            container.sharedImageInbox.value = emptyList()
            initialPhotoNote = ""
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
            // Codeberg #78: Add another / Add next ingredient open the full
            // Add Food hub (barcode, frequent, note, photo), not only camera.
            vm.prefetchAddFoodIndex()
            addFoodFlowActive = true
            showAddFoodSheet = true
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
                    if (aiFeaturesEnabled) {
                        initialPhotoNote = ""
                        openCamera()
                    } else {
                        clearShortcut(ShortcutEntryAction.CAMERA)
                    }
                ShortcutEntryAction.BARCODE -> openBarcodeScanner()
                ShortcutEntryAction.VOICE ->
                    if (aiFeaturesEnabled) return@repeatOnLifecycle else clearShortcut(ShortcutEntryAction.VOICE)
                // #182: launcher long-press toggles the fast and clears immediately —
                // there is no destination UI, so the sticky-inbox rule doesn't apply.
                ShortcutEntryAction.FASTING -> {
                    vm.toggleFast()
                    clearShortcut(ShortcutEntryAction.FASTING)
                }
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
    // Derived hero state: resolvedDayTargets walks the goal journal +
    // MacroPlanResolver on EVERY getter access, dayTypeActiveStats merges
    // two maps and computes per profile. Remember them per state change
    // instead of paying that per composable frame.
    val resolvedTargets = remember(ui.profile, ui.date, ui.goalJournal) { ui.resolvedDayTargets }
    val dayTypeStats = remember(ui.goalJournal, ui.healthEnergyActiveByDay, ui.manualActiveByDay, ui.date) {
        ui.dayTypeActiveStats
    }
    // Codeberg #56 repro instrumentation (TEMP, debug-only): log the rendered
    // meal-group view (ids per section) to separate a state drop from a render
    // drop when comparing against op=homeList phase=emission in logcat.
    // Guarded by PerfLog.enabled: release builds paid the string build per
    // group change for nothing (PerfLog.event is a no-op there).
    if (PerfLog.enabled) {
        LaunchedEffect(mealGroups) {
            val groupsView = mealGroups.joinToString("|") { g ->
                "${g.id}:${g.entries.size}[${g.entries.joinToString(",") { it.id.toString().take(8) }}]"
            }
            PerfLog.event("op=homeList phase=renderGroups n=${mealGroups.size} groups=[$groupsView]")
        }
    }
    var selectedEntryIds by remember(ui.date) { mutableStateOf<Set<UUID>>(emptySet()) }
    val selectedEntries = remember(ui.todayEntries, selectedEntryIds) {
        ui.todayEntries.filter { it.id in selectedEntryIds }
    }
    val inSelectionMode = selectedEntryIds.isNotEmpty()
    val scope = rememberCoroutineScope()
    val foodRemovedMessage = stringResource(R.string.home_food_removed)
    val undoLabel = stringResource(R.string.action_undo)
    val dayTypeSwitchedMessage = stringResource(R.string.home_day_type_switched)

    // Codeberg #30: add-food flow helper. Backing out of a flow-launched
    // destination reopens the grid instead of closing the whole flow. No-op
    // when the destination came from outside the flow (shortcut, share,
    // gallery).
    fun returnToAddFoodGrid() {
        if (addFoodFlowActive) showAddFoodSheet = true
    }

    /** After the first ingredient is in the draft, dismiss camera/note/review
     *  back to the meal sheet, not the Add Food hub (chip would sit behind it). */
    fun returnToDraftOrAddFoodGrid() {
        val draft = ui.progressiveMeal
        if (draft != null && draft.items.isNotEmpty()) {
            vm.showProgressiveMealSheet(true)
        } else {
            returnToAddFoodGrid()
        }
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
                modifier = Modifier.padding(bottom = BottomOverlayPadding),
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
            // Week strip — 52 past weeks + current + 8 future (Codeberg #96).
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    // Type dots + untracked dashes for the whole strip window (UI-UX §10).
                    val stripDates = remember(weekStartDay) { weekStripDates(weekStartDay) }
                    val stripMarkers = remember(ui.profile, stripDates) {
                        dayTypeMarkerMaps(ui.profile, vm.weekDayTypes(stripDates))
                    }
                    val stripUntracked = remember(ui.untrackedDays) { isoToLocalDates(ui.untrackedDays) }
                    WeekEnergyStrip(
                        selectedDate = selectedDate,
                        onSelect = { vm.setSelectedDate(it) },
                        weekStartDay = weekStartDay,
                        typeColorsByDate = stripMarkers.first,
                        typeNamesByDate = stripMarkers.second,
                        untrackedDates = stripUntracked,
                    )
                }
            }
            if (seedingSampleData) {
                item {
                    Text(
                        text = stringResource(R.string.home_loading_sample_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                                    if (canAdvanceDiaryDay(selectedDate, today)) vm.setSelectedDate(next)
                                }
                                accum = 0f
                                horizontalLocked = false
                            }
                        )
                    }
                ) {
                    Spacer(Modifier.height(4.dp))
                    if (isToday) {
                        Text(
                            stringResource(R.string.home_today),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    } else {
                        val jumpCd = stringResource(R.string.home_jump_to_today)
                        // Date plus a Today pill: the whole strip jumps back,
                        // not just the date text.
                        Row(
                            Modifier
                                .padding(horizontal = 16.dp)
                                .clickable { vm.setSelectedDate(LocalDate.now()) }
                                .semantics { contentDescription = jumpCd },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                selectedDate.format(LocaleFormat.mediumDate()),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Row(
                                    Modifier.padding(start = 8.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(R.string.home_today),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Outlined.Reply,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                    val baseGoal = ui.gaugeBaseCalorieGoal
                    val activeCalories = ui.displayActiveCalories
                    val calorieMode = ui.effectiveCalorieMode
                    CalorieHero(
                        current = ui.loggedCaloriesToday,
                        baseGoal = baseGoal,
                        planned = ui.plannedCaloriesToday,
                        activeCalories = activeCalories,
                        displayMode = calorieMode,
                        dayTypeLabel = ui.dayTypeLabel,
                        dayTypeColor = ui.resolvedDayTargets.profileId?.let { id ->
                            ui.profile?.macroPlan?.profileById(id)
                                ?.let { p -> dayTypeColor(p.colorKey, id) }
                        },
                        onDayTypeClick = { showDayTypeSheet = true },
                        untracked = ui.viewedDayUntracked,
                        onUntrackedClick = { showUntrackedSheet = true },
                        activeCalorieSource = ui.resolvedActiveBurn?.source,
                        showActiveCalories = ui.homeDisplay.showActiveCalories,
                        liveActiveBurn = ui.liveActiveBurn,
                        burnShade = ui.activeBurnShade,
                        restingBurn = ui.restingBurnToday,
                        recalcDetailsAvailable = ui.lastRecalcSheet != null,
                        onShowRecalcDetails = { vm.openRecalcDetails() },
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
                    // Current/goal resolved once per state change instead of
                    // per card per recomposition (current() scans the day's
                    // entries; goal() re-derives from the resolved targets).
                    val loggedEntries = remember(ui.todayEntries) {
                        ui.todayEntries.filter { !it.planned }
                    }
                    val plannedEntries = remember(ui.todayEntries) {
                        ui.todayEntries.filter { it.planned }
                    }
                    val macroCardValues = remember(
                        ui.homeTopNutrients, loggedEntries, plannedEntries, resolvedTargets,
                        ui.profile, ui.optionalNutrientGoals, ui.macroGoalScale, ui.caffeineTodayMg,
                    ) {
                        ui.homeTopNutrients.associateWith { nutrient ->
                            val logged = if (nutrient == HomeTopNutrient.CAFFEINE) {
                                ui.caffeineTodayMg
                            } else {
                                nutrient.current(loggedEntries)
                            }
                            val planned = if (nutrient == HomeTopNutrient.CAFFEINE) {
                                0.0
                            } else {
                                nutrient.current(plannedEntries)
                            }
                            Triple(logged, planned, nutrient.goal(resolvedTargets, ui.profile, ui.optionalNutrientGoals, ui.macroGoalScale))
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ui.homeTopNutrients.forEach { nutrient ->
                            val (current, planned, goal) = macroCardValues.getValue(nutrient)
                            MacroCard(
                                label = stringResource(nutrient.displayNameRes),
                                current = current,
                                goal = goal,
                                fatFloor = nutrient == HomeTopNutrient.FAT,
                                unit = stringResource(nutrient.unitRes),
                                accentColor = nutrientAccentColor(nutrient),
                                modifier = Modifier.weight(1f),
                                planned = planned,
                            )
                        }
                    }
                    if (ui.fastingEnabled) {
                        Spacer(Modifier.height(12.dp))
                        FastingProgressRow(
                            phase = ui.fastingPhase,
                            fastHours = ui.fastingGoalHours,
                            eatHours = ui.fastingEatHours,
                            fastElapsedMillis = ui.fastingElapsedMillis,
                            eatElapsedMillis = ui.fastingEatingElapsedMillis,
                            nextFastStartMillis = ui.fastingNextFastStartMillis,
                            nowMillis = ui.fastingNowMillis,
                            goalReached = ui.fastingGoalReached,
                            autoStarted = ui.fastingAutoStarted,
                            autoMode = ui.fastingAutoWindows && ui.fastingGoalHours > 0,
                            onStart = vm::startFast,
                            onStop = vm::stopFast,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
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
                            val time = formatClockMillis(ctx, plan.nextFireMillis)
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
                            onClick = { showWaterHistory = true },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (ui.nicotineTrackingEnabled) {
                        Spacer(Modifier.height(12.dp))
                        NicotineProgressRow(
                            current = ui.nicotineTodayCount,
                            limit = ui.nicotineDailyLimit,
                            onClick = { showNicotineHistory = true },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (ui.caffeineTrackingEnabled) {
                        Spacer(Modifier.height(12.dp))
                        CaffeineProgressRow(
                            currentMg = ui.caffeineTodayMg,
                            limit = ui.optionalNutrientGoals.caffeine,
                            onClick = { showCaffeineHistory = true },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.clickable { nutritionDetailScope = "day" }) {
                            ViewMoreButton()
                        }
                    }
                }
            }

            // Food log
            item { Spacer(Modifier.height(8.dp)) }
            // Daily note (Codeberg #58a): day-scoped like the water card, so it
            // follows the selected day (today, past, or planned future). Only when the
            // optional tracker is enabled (Settings → Trackers & Reminders).
            if (ui.dailyNotesEnabled) {
                item(key = "daily-note-${selectedDate}") {
                    DailyNoteCard(
                        note = ui.dailyNote,
                        onClick = { showDailyNoteEditor = true },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            if (ui.diaryLoaded && mealGroups.isEmpty()) {
                item { SectionHeader(if (isToday) stringResource(R.string.home_todays_food) else stringResource(R.string.home_food_log)) }
                item {
                    SectionCardWrapper(isFirst = true, isLast = true) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text(
                                stringResource(
                                    if (selectedDate.isAfter(today)) R.string.home_no_foods_planned
                                    else R.string.home_no_foods_logged,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
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
                            onClick = { nutritionDetailScope = group.id },
                        )
                    }
                    // Group-scoped row keys (#56): an entry moving between meal
                    // groups (Meal Type edit) must dispose its old row and compose
                    // a fresh one under the destination group instead of relying
                    // on LazyColumn move-in-place layout, which on slow devices
                    // left the moved row composed but drawn at a stale offset
                    // (card vanished until restart; state/groups stayed correct).
                    // Membership is part of the key too: the *unmoved* sibling in
                    // the destination group kept a stable key after 3.24.1/4.0.0
                    // and still vanished ~1/10 times (self-heals on date switch).
                    val groupMembership = group.entries.joinToString(",") { it.id.toString() }
                    itemsIndexed(
                        group.entries,
                        key = { _, entry -> "${group.id}:$groupMembership:${entry.id}" },
                        contentType = { _, _ -> "food-row" },
                    ) { index, entry ->
                        // Codeberg #56 repro instrumentation (TEMP, debug-only):
                        // log every row entering/leaving composition so logcat
                        // can catch a render drop — a row present in the groups
                        // (op=homeList phase=renderGroups) but never composed
                        // here, or composed then disposed, is the composition
                        // drop the state/group logs cannot see.
                        DisposableEffect(entry.id) {
                            PerfLog.event(
                                "op=homeList phase=rowComposed id=${entry.id.toString().take(8)} " +
                                    "meal=${entry.mealType} group=${group.id}",
                            )
                            onDispose {
                                PerfLog.event(
                                    "op=homeList phase=rowDisposed id=${entry.id.toString().take(8)} " +
                                        "meal=${entry.mealType} group=${group.id}",
                                )
                            }
                        }
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
                                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                vm.restoreEntry(entry)
                                            }
                                        }
                                    },
                                    onToggleFavorite = { vm.toggleFavorite(entry) },
                                    planningMode = ui.mealPlanningEnabled,
                                    onLogNow = { vm.logPlannedEntryNow(it) },
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
                if (PerfLog.enabled) hubOpenedAtNs = System.nanoTime()
                vm.prefetchAddFoodIndex()
                // Codeberg #30: "+" always opens the grid (a last-tool restore
                // was tried and removed on the maintainer device pass
                // 2026-08-18); backing out of a destination returns here.
                addFoodFlowActive = true
                showAddFoodSheet = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = BottomOverlayPadding),
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
            !showAddFoodSheet &&
            !showCameraCapture &&
            !showMultiPhotoCapture
        if (showProgressiveChip) {
            FloatingActionButton(
                onClick = { vm.showProgressiveMealSheet(true) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, bottom = BottomOverlayPadding),
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
        // Recovered review chip: a dismissed completed AI review, kept so the
        // analysis is not wasted. Tap reopens the review sheet; X discards.
        val recoveredReview = ui.recoveredReview
        val showRecoveredChip = recoveredReview != null &&
            !ui.showFoodResultSheet &&
            !ui.showProgressiveMealSheet &&
            !ui.showAnalysisQueue &&
            !showAddFoodSheet &&
            !showCameraCapture &&
            !showMultiPhotoCapture &&
            !inSelectionMode
        if ((copied.isNotEmpty() && !inSelectionMode && !showProgressiveChip) || showRecoveredChip) {
            val pasteBusy = ui.saving
            val paste: () -> Unit = {
                if (!pasteBusy) vm.copyEntriesToSelectedDay(copied)
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = BottomOverlayPadding)
                    .zIndex(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showRecoveredChip && recoveredReview != null) {
                    FudGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = vm::restoreRecoveredReview),
                        cornerRadius = AppRadii.SectionCard,
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
                                    R.string.recovered_review_chip,
                                    recoveredReview.analysis.name
                                ),
                                modifier = Modifier.weight(1f, fill = false),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = vm::discardRecoveredReview,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_discard_recovered_review),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                if (copied.isNotEmpty() && !inSelectionMode && !showProgressiveChip) {
                    FudGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (pasteBusy) 0.55f else 1f)
                            .clickable(enabled = !pasteBusy, onClick = paste),
                        cornerRadius = AppRadii.SectionCard,
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
                                modifier = Modifier.weight(1f, fill = false),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = { vm.clearCopiedEntries() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_dismiss_paste),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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
            if (PerfLog.enabled && hubOpenedAtNs != 0L) {
                val ms = (System.nanoTime() - hubOpenedAtNs) / 1_000_000
                PerfLog.event("op=hubOpen phase=sheetVisible ms=$ms")
            }
            vm.prefetchAddFoodSavedRows()
        }
    }

    if (showAddFoodSheet) {
        AddFoodSheet(
            aiFeaturesEnabled = aiFeaturesEnabled,
            waterTrackingEnabled = ui.waterTrackingEnabled,
            waterQuickPresetsMl = ui.waterQuickPresetsMl,
            waterUseMetric = ui.weightMetric,
            savedTab = ui.addFoodSavedTab,
            savedRows = ui.addFoodSavedRows,
            onSavedTabChange = vm::selectAddFoodSavedTab,
            onPhoto = { query ->
                addFoodFlowActive = true
                initialPhotoNote = query
                openCamera()
            },
            onNote = {
                addFoodFlowActive = true
                showText = true
            },
            onSavedMeals = {
                addFoodFlowActive = true
                savedMealsTab = SavedTab.RECENTS
            },
            onVoice = {
                addFoodFlowActive = true
                showVoiceLocal = true
            },
            onBarcode = {
                addFoodFlowActive = true
                openBarcodeScanner()
            },
            onManual = {
                addFoodFlowActive = true
                showManual = true
            },
            onCopyFromDay = {
                addFoodFlowActive = true
                showCopyFromDay = true
            },
            onManualActive = {
                addFoodFlowActive = true
                showManualActive = true
            },
            onGrounded = {
                if (GroundedEntryFeature.ENABLED) {
                    addFoodFlowActive = true
                    showGroundedEntry = true
                }
            },
            onQueue = {
                addFoodFlowActive = false
                vm.openQueue()
            },
            queuePendingCount = ui.queuePendingCount,
            onWater = { ml -> vm.addWater(ml) },
            onWaterCustom = { showCustomWaterLog = true },
            nicotineTrackingEnabled = ui.nicotineTrackingEnabled,
            nicotineQuickKinds = ui.nicotineQuickKinds,
            nicotinePresets = ui.nicotinePresets,
            onNicotine = { kind -> vm.addNicotine(kind) },
            onNicotineCustom = { showNicotineCustom = true },
            caffeineTrackingEnabled = ui.caffeineTrackingEnabled,
            caffeineQuickKinds = ui.caffeineQuickKinds,
            caffeinePresets = ui.caffeinePresets,
            onCaffeine = { kind ->
                // A chip without an effective default (e.g. the bare "Other"
                // builtin) opens the custom sheet instead of logging 0 mg.
                val preset = ui.caffeinePresets.firstOrNull { it.id == kind }
                val presetMg = preset?.defaultMg ?: builtinCaffeineDefaultMg(kind)
                if (presetMg != null && presetMg > 0) {
                    vm.addCaffeine(
                        kind,
                        milkKind = MilkKind.fromStorage(preset?.milkKind),
                        milkMl = preset?.milkMl ?: 0,
                    )
                } else {
                    showCaffeineCustom = true
                }
            },
            onCaffeineCustom = { showCaffeineCustom = true },
            fastingEnabled = ui.fastingEnabled,
            fastingPhase = ui.fastingPhase,
            fastingElapsedMillis = ui.fastingElapsedMillis,
            fastingEatingElapsedMillis = ui.fastingEatingElapsedMillis,
            fastingGoalHours = ui.fastingGoalHours,
            fastingEatHours = ui.fastingEatHours,
            fastingGoalReached = ui.fastingGoalReached,
            fastingAutoStarted = ui.fastingAutoStarted,
            fastingNextFastStartMillis = ui.fastingNextFastStartMillis,
            fastingNowMillis = ui.fastingNowMillis,
            fastingAutoWindows = ui.fastingAutoWindows,
            onStartFast = vm::startFast,
            onStopFast = vm::stopFast,
            query = ui.addFoodQuery,
            onQueryChange = vm::onAddFoodQueryChange,
            suggestions = ui.addFoodSuggestions,
            suggestionsNetworkPending = ui.addFoodSuggestNetworkPending,
            packagedSearchEnabled = ui.addFoodPackagedSearchEnabled,
            onPackagedSearchChange = vm::setAddFoodPackagedSearch,
            onOpenFoodSettings = { onOpenFoodSettings?.invoke() },
            onPickSuggestion = {
                addFoodFlowActive = false
                vm.pickSuggestion(it)
                vm.clearAddFoodQuery()
            },
            onReviewSuggestion = {
                addFoodFlowActive = false
                vm.reviewSuggestion(it)
                vm.clearAddFoodQuery()
            },
            planningMode = ui.mealPlanningEnabled,
            onPlanSuggestion = {
                addFoodFlowActive = false
                planForDayTarget = it
            },
            onPlanWeek = { onPlanWeek?.invoke() },
            onAnalyzeQuery = { text ->
                if (!ui.isEntryAnalysisBusy) {
                    addFoodFlowActive = false
                    vm.analyzeText(text)
                    vm.clearAddFoodQuery()
                }
            },
            onDismiss = {
                showAddFoodSheet = false
                addFoodFlowActive = false
                // Closing the flow drops the search; backing out of a
                // sub-destination (returnToAddFoodGrid) deliberately does not,
                // so a typed query survives a detour through Copy-from-day.
                vm.clearAddFoodQuery()
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

    if (showCustomWaterLog) {
        WaterCustomAmountSheet(
            useMetric = ui.weightMetric,
            onDismiss = { showCustomWaterLog = false },
            onAdd = vm::addWater,
        )
    }

    if (showWaterHistory) {
        WaterHistorySheet(
            day = ui.date,
            entries = ui.waterTodayEntries,
            useMetric = ui.weightMetric,
            onDismiss = { showWaterHistory = false },
            onEdit = { editingWaterEntry = it },
            onDelete = { vm.deleteWater(it.id) },
        )
    }

    if (showDailyNoteEditor) {
        DailyNoteSheet(
            day = ui.date,
            initialText = ui.dailyNote.orEmpty(),
            onDismiss = { showDailyNoteEditor = false },
            onSave = vm::setDailyNote,
            onClear = vm::clearDailyNote,
        )
    }

    editingWaterEntry?.let { entry ->
        WaterEditAmountSheet(
            entry = entry,
            useMetric = ui.weightMetric,
            onDismiss = { editingWaterEntry = null },
            onSave = { ml -> vm.updateWater(entry.id, ml) },
        )
    }

    if (showNicotineCustom) {
        NicotineCustomCountSheet(
            presets = ui.nicotinePresets,
            onDismiss = { showNicotineCustom = false },
            onAdd = { kind, count, mg -> vm.addNicotine(kind, count, mg) },
        )
    }

    if (showNicotineHistory) {
        NicotineHistorySheet(
            day = ui.date,
            entries = ui.nicotineTodayEntries,
            presets = ui.nicotinePresets,
            onDismiss = { showNicotineHistory = false },
            onEdit = { editingNicotineEntry = it },
            onDelete = { vm.deleteNicotine(it.id) },
            // Dismiss-then-open keeps one sheet on screen (AddFoodSheet custom path).
            onCustom = {
                showNicotineHistory = false
                showNicotineCustom = true
            },
        )
    }

    editingNicotineEntry?.let { entry ->
        NicotineEditSheet(
            entry = entry,
            presets = ui.nicotinePresets,
            onDismiss = { editingNicotineEntry = null },
            onSave = { kind, count, mg -> vm.updateNicotine(entry.id, kind, count, mg) },
        )
    }

    if (showCaffeineCustom) {
        CaffeineCustomSheet(
            presets = ui.caffeinePresets,
            onDismiss = { showCaffeineCustom = false },
            onAdd = { kind, mg, milkKind, milkMl -> vm.addCaffeine(kind, mg, milkKind, milkMl) },
        )
    }

    if (showCaffeineHistory) {
        CaffeineHistorySheet(
            day = ui.date,
            entries = ui.caffeineTodayEntries,
            presets = ui.caffeinePresets,
            onDismiss = { showCaffeineHistory = false },
            onEdit = { editingCaffeineEntry = it },
            onDelete = { vm.deleteCaffeine(it.id) },
            // Dismiss-then-open keeps one sheet on screen (AddFoodSheet custom path).
            onCustom = {
                showCaffeineHistory = false
                showCaffeineCustom = true
            },
        )
    }

    editingCaffeineEntry?.let { entry ->
        CaffeineEditSheet(
            entry = entry,
            presets = ui.caffeinePresets,
            onDismiss = { editingCaffeineEntry = null },
            onSave = { kind, mg -> vm.updateCaffeine(entry.id, kind, mg) },
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
            todayEntries = ui.manualActiveTodayEntries,
            day = ui.date,
            onEdit = { editingManualActive = it },
            onDelete = { vm.deleteManualActive(it.id) },
        )
    }

    editingManualActive?.let { entry ->
        ManualActiveSheet(
            initial = entry,
            onSave = { name, kcal -> vm.updateManualActive(entry.id, name, kcal) },
            onDismiss = { editingManualActive = null },
        )
    }

    if (showText) {
        TextInputSheet(
            onDismiss = {
                showText = false
                returnToAddFoodGrid()
            },
            isSubmitting = ui.isEntryAnalysisBusy,
            recentPrompts = recentPrompts,
            initialText = textInputDraft,
            onTextChange = vm::onTextInputDraftChange,
            onSubmit = {
                if (!ui.isEntryAnalysisBusy) {
                    showText = false
                    addFoodFlowActive = false
                    vm.analyzeText(it)
                    vm.clearTextInputDraft()
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
            initialMealType = suggestedSlotFor(
                ui.mealTimesEnabled,
                CurrentMealCatalog.value,
                ui.logTimeOverride ?: LocalTime.now(),
            ),
            mealTimesEnabled = ui.mealTimesEnabled,
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
            },
            // Codeberg #66: close the sheet first (same pattern as recipe
            // editing) so the favorite editor renders as its own modal.
            onEditFavorite = { favorite ->
                savedMealsTab = null
                addFoodFlowActive = false
                editingFavorite = favorite
            }
        )
    }

    editingFavorite?.let { favorite ->
        EditFavoriteSheet(
            container = container,
            entry = favorite,
            optionalGoals = ui.optionalNutrientGoals,
            onSave = { updated ->
                vm.updateFavorite(favorite, updated)
                editingFavorite = null
            },
            onDismiss = { editingFavorite = null }
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

    if (ui.mealPlanningEnabled && planForDayTarget != null) {
        PlanForDayDialog(
            onConfirm = { date ->
                planForDayTarget?.let { vm.planSuggestion(it, date) }
                planForDayTarget = null
                vm.clearAddFoodQuery()
            },
            onDismiss = { planForDayTarget = null },
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
                if (stagedPhotoBytes.isEmpty()) {
                    initialPhotoNote = ""
                    returnToDraftOrAddFoodGrid()
                }
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
            initialNote = initialPhotoNote,
            showScaleTip = ui.progressiveMeal?.items?.isNotEmpty() == true,
            requireNote = !ui.skipPhotoNotePrompt,
            showDontAskAgain = !ui.skipPhotoNotePrompt &&
                ui.photoNoteSkipCount >= HomeViewModel.PHOTO_NOTE_SKIP_OFFER_THRESHOLD,
            showAccuracyGuide = ui.photoAccuracyGuideCount < HomeViewModel.PHOTO_ACCURACY_GUIDE_COUNT,
            recentPrompts = recentPrompts,
            onAddPhoto = { note ->
                if (stagedPhotoBytes.size < FoodPhotoSession.MAX_IMAGES) {
                    initialPhotoNote = note
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
                initialPhotoNote = ""
                if (!ui.isEntryAnalysisBusy) {
                    addFoodFlowActive = false
                    vm.analyzePhotosFromStaging(images, note, grams, dontAskAgain)
                }
            },
            onQueueForLater = { note, grams ->
                // Codeberg #53: store time + photos + description WITHOUT an AI
                // call; the queue sheet opens so it can be run later.
                val images = photoSession.stagedImages.value
                photoSession.clear()
                initialPhotoNote = ""
                addFoodFlowActive = false
                vm.queueStaged(images, note, grams)
            },
            onDismiss = {
                photoSession.clear()
                initialPhotoNote = ""
                returnToDraftOrAddFoodGrid()
            },
        )
    }

    if (ui.showAnalysisQueue) {
        AnalysisQueueSheet(
            container = container,
            entries = ui.queueEntries,
            runningId = ui.queueRunningId,
            onRun = vm::runQueuedItem,
            onRunAll = vm::runAllQueued,
            onUpdate = vm::updateQueued,
            onAddPhotos = vm::addQueuedPhotos,
            onRemovePhoto = vm::removeQueuedPhoto,
            onDelete = vm::deleteQueued,
            onClearHistory = vm::clearQueueHistory,
            onDismiss = {
                vm.dismissQueue()
                returnToAddFoodGrid()
            },
        )
    }
    editingEntry?.let { entry ->
        EditFoodEntrySheet(
            entry = entry,
            optionalGoals = ui.optionalNutrientGoals,
            preferGramsByDefault = ui.preferGramsByDefault,
            aiFeaturesEnabled = aiFeaturesEnabled,
            useSystemDateTimePickers = useSystemDateTimePickers,
            dayEntries = ui.todayEntries,
            mealTimesEnabled = ui.mealTimesEnabled,
            onReprocess = { updatedNote, onProgress ->
                vm.reprocessFoodEntry(entry, updatedNote, onProgress)
            },
            onSave = { updated, applyTimeToMeal ->
                vm.updateEntry(entry, updated, applyTimeToMeal)
                editingEntry = null
            },
            onLogNow = { updated ->
                vm.logPlannedEntryNow(updated)
                editingEntry = null
            },
            onDismiss = { editingEntry = null }
        )
    }

    if (nutritionDetailScope != null) {
        val mealGroup = if (nutritionDetailScope != "day") {
            mealGroups.find { it.id == nutritionDetailScope }
        } else {
            null
        }
        NutritionDetailSheet(
            entries = if (nutritionDetailScope == "day") ui.todayEntries else mealGroup?.entries.orEmpty(),
            profile = ui.profile,
            resolved = resolvedTargets,
            homeTopNutrients = ui.homeTopNutrients,
            optionalGoals = ui.optionalNutrientGoals,
            macroScale = ui.macroGoalScale,
            title = mealGroup?.let { mealLabel(it.meal) },
            showHomeCards = nutritionDetailScope == "day",
            trackerCaffeineMg = if (nutritionDetailScope == "day") ui.caffeineTodayEntries.sumOf { it.mg } else 0.0,
            onHomeTopNutrientsChange = vm::setHomeTopNutrients,
            onDismiss = { nutritionDetailScope = null },
        )
    }

    if (showUntrackedSheet) {
        UntrackedDaySheet(
            date = ui.date,
            untracked = ui.viewedDayUntracked,
            kcal = ui.viewedDayUntrackedKcal,
            onSave = { flagged, kcal -> vm.setViewedDayUntracked(flagged, kcal) },
            onDismiss = { showUntrackedSheet = false },
        )
    }

    if (showDayTypeSheet) {
        DayTypeSwitchSheet(
            profile = ui.profile,
            today = LocalDate.now(),
            typicalActiveByProfileId = dayTypeStats.byProfileId
                .filter { it.value.sampleCount >= app.chompass.models.DayTypeActiveStats.MIN_SAMPLES }
                .mapValues { it.value.averageKcal },
            onSwitch = { profileId ->
                vm.switchTodayDayType(profileId)
                // Tapping the type already in effect is a VM-side no-op; skip
                // the undo chip for it too (D3).
                if (profileId == null || profileId != resolvedTargets.profileId) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = dayTypeSwitchedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            vm.undoTodayDayTypeSwitch()
                        }
                    }
                }
            },
            onDismiss = { showDayTypeSheet = false },
            // "Edit day types" deep-links straight into the editor when the
            // nav host provides the route; the Settings hub stays the fallback.
            onOpenSettings = onOpenDayTypes ?: onOpenSettings,
        )
    }

    ui.error?.let { err ->
        val hasRetryableInput = ui.pendingInputImageBytes != null ||
            ui.pendingInputDraftImageFilenames.isNotEmpty()
        val autoSavedToQueue = ui.pendingQueueEntryId != null
        val uriHandler = LocalUriHandler.current
        FudGlassDialog(onDismissRequest = { vm.clearError() }) {
            Text(stringResource(R.string.error_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(err, color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary))
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
            ui.errorNotFoundBarcode?.let { barcode ->
                // The barcode is not in OFF yet: offer to add the product
                // there. No account needed; the user types or scans the code
                // into OFF's form (the URL param does not prefill it).
                TextButton(
                    onClick = {
                        uriHandler.openUri(OpenFoodFactsService.addProductUrl(barcode))
                    },
                ) {
                    Text(stringResource(R.string.barcode_add_to_off))
                }
            }
            if (autoSavedToQueue) {
                // Codeberg #53: the failed photos + description are already in
                // the analysis queue — nothing is lost, and it can run later.
                Text(
                    stringResource(R.string.analysis_queue_saved_note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
                TextButton(
                    onClick = {
                        vm.clearError()
                        vm.openQueue()
                    },
                ) {
                    Text(stringResource(R.string.analysis_queue_open))
                }
            }
        }
    }

    if (ui.showFoodResultSheet && !showMultiPhotoCapture) {
        FoodResultSheet(
            analysis = ui.pendingAnalysis,
            imageBytes = ui.pendingImageBytes,
            preferGramsByDefault = ui.preferGramsByDefault,
            profile = ui.profile,
            resolved = resolvedTargets,
            optionalGoals = ui.optionalNutrientGoals,
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
            portionPreConfirmed = ui.pendingPortionPreConfirmed,
            progressiveMealActive = ui.progressiveMeal?.items?.isNotEmpty() == true,
            // Codeberg #102: Saved Meals review follows the log-time slot when
            // suggestions are on; the stored slot is kept only when they are
            // off (#88 / #66). Fresh analyses still use the time-of-day guess.
            initialMealType = reviewSlotFor(
                ui.pendingReviewSource?.mealType,
                ui.mealTimesEnabled,
                CurrentMealCatalog.value,
                ui.logTimeOverride,
                LocalTime.now(),
            ),
            mealTimesEnabled = ui.mealTimesEnabled,
            logTimeOverride = ui.logTimeOverride,
            useSystemDateTimePickers = useSystemDateTimePickers,
            onLogTimeOverride = vm::setLogTimeOverride,
            mealTypeFromSavedMeal = ui.pendingReviewSource != null,
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
            onSave = { name, grams, scale, mealType, selectedServingUnit, selectedServingQuantity, editedAnalysis, logTime ->
                vm.saveAnalysis(
                    name = name,
                    servingGrams = grams,
                    scale = scale,
                    mealType = mealType,
                    selectedServingUnit = selectedServingUnit,
                    selectedServingQuantity = selectedServingQuantity,
                    editedAnalysis = editedAnalysis,
                    logTime = logTime,
                )
            },
            onAddToProgressiveMeal = { name, grams, _, mealType, selectedServingUnit, selectedServingQuantity, editedAnalysis, resumeCapture, logTime ->
                vm.addToProgressiveMeal(
                    name = name,
                    servingGrams = grams,
                    mealType = mealType,
                    selectedServingUnit = selectedServingUnit,
                    selectedServingQuantity = selectedServingQuantity,
                    editedAnalysis = editedAnalysis,
                    resumeCapture = resumeCapture,
                    logTime = logTime,
                )
            },
            onDismiss = {
                val hasDraft = ui.progressiveMeal?.items?.isNotEmpty() == true
                vm.dismissPending()
                if (hasDraft) vm.showProgressiveMealSheet(true)
            }
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
    // Hero ⓘ → recalc details: reopen the persisted goal-change explanation.
    ui.recalcSheet?.let { sheet ->
        RecalcResultSheet(data = sheet, onDismiss = { vm.dismissRecalcSheet() })
    }
}
/**
 * Strip window in lockstep with WeekStrip's PAST_WEEKS (52) / FUTURE_WEEKS (8):
 * 61 week starts × 7 days around the current week.
 */
internal fun weekStripDates(weekStartDay: app.chompass.models.WeekStartDay): List<LocalDate> {
    val today = LocalDate.now()
    val firstDow = weekStartDay.javaDay
    val curStart = today.minusDays(((today.dayOfWeek.value - firstDow.value) + 7) % 7L)
    return generateSequence(curStart.minusWeeks(52)) { it.plusWeeks(1) }
        .take(61)
        .flatMap { start -> (0L..6L).map { start.plusDays(it) } }
        .toList()
}

/** ISO date -> resolved day-type profile id; empty while the plan is off. */
internal fun resolveDayTypeIds(
    profile: app.chompass.models.UserProfile?,
    dates: List<LocalDate>,
): Map<String, String> {
    if (profile?.macroPlan?.enabled != true) return emptyMap()
    return dates.mapNotNull { date ->
        MacroPlanResolver.targetsFor(profile, date).profileId
            ?.let { date.toString() to it }
    }.toMap()
}

/** Splits resolved ids into the strip's color and TalkBack-name maps. */
internal fun dayTypeMarkerMaps(
    profile: app.chompass.models.UserProfile?,
    idsByDate: Map<String, String>,
): Pair<Map<LocalDate, Color>, Map<LocalDate, String>> {
    val plan = profile?.macroPlan
    val colors = mutableMapOf<LocalDate, Color>()
    val names = mutableMapOf<LocalDate, String>()
    for ((iso, id) in idsByDate) {
        val p = plan?.profileById(id) ?: continue
        val date = LocalDate.parse(iso)
        colors[date] = dayTypeColor(p.colorKey, id)
        names[date] = p.name
    }
    return colors to names
}

internal fun isoToLocalDates(isoDates: Set<String>): Set<LocalDate> =
    isoDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

/** Static home layout for release screenshot previews (no ViewModel / permissions). */
@Composable
internal fun HomeScreenPreviewContent(
    ui: HomeUiState,
    weekStartDay: app.chompass.models.WeekStartDay = app.chompass.models.WeekStartDay.MONDAY,
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
                        val previewWeekStart =
                            if (weekStartsOnMonday) weekStartDay else app.chompass.models.WeekStartDay.SUNDAY
                        val stripDates = remember(previewWeekStart) { weekStripDates(previewWeekStart) }
                        val stripMarkers = remember(ui.profile, stripDates) {
                            dayTypeMarkerMaps(ui.profile, resolveDayTypeIds(ui.profile, stripDates))
                        }
                        val stripUntracked = remember(ui.untrackedDays) { isoToLocalDates(ui.untrackedDays) }
                        WeekEnergyStrip(
                            selectedDate = selectedDate,
                            onSelect = {},
                            weekStartDay = previewWeekStart,
                            typeColorsByDate = stripMarkers.first,
                            typeNamesByDate = stripMarkers.second,
                            untrackedDates = stripUntracked,
                        )
                    }
                }
                item {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.home_today),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        CalorieHero(
                            current = ui.loggedCaloriesToday,
                            baseGoal = ui.gaugeBaseCalorieGoal,
                            planned = ui.plannedCaloriesToday,
                            activeCalories = ui.displayActiveCalories,
                            displayMode = ui.effectiveCalorieMode,
                            activeCalorieSource = ui.resolvedActiveBurn?.source,
                            showActiveCalories = ui.homeDisplay.showActiveCalories,
                            liveActiveBurn = ui.liveActiveBurn,
                            burnShade = ui.activeBurnShade,
                            restingBurn = ui.restingBurnToday,
                            freezeProgress = freezeAnimations,
                        )
                        val previewResolvedTargets = remember(ui.profile, ui.date, ui.goalJournal) { ui.resolvedDayTargets }
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
                                    current = if (nutrient == HomeTopNutrient.CAFFEINE) ui.caffeineTodayMg else nutrient.current(ui.todayEntries),
                                    goal = nutrient.goal(previewResolvedTargets, ui.profile, ui.optionalNutrientGoals, ui.macroGoalScale),
                                    fatFloor = nutrient == HomeTopNutrient.FAT,
                                    unit = stringResource(nutrient.unitRes),
                                    accentColor = nutrientAccentColor(nutrient),
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
                if (ui.diaryLoaded && mealGroups.isEmpty()) {
                    item { SectionHeader(if (isToday) stringResource(R.string.home_todays_food) else stringResource(R.string.home_food_log)) }
                    item {
                        SectionCardWrapper(isFirst = true, isLast = true) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                                Text(
                                    stringResource(R.string.home_no_foods_logged),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
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
                        // Group-scoped keys, see main list above (#56).
                        itemsIndexed(
                            group.entries,
                            key = { _, entry -> "${group.id}:${entry.id}" },
                            contentType = { _, _ -> "food-row" },
                        ) { index, entry ->
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
                    .padding(end = 24.dp, bottom = BottomOverlayPadding),
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
