package app.chompass.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown

import androidx.compose.material.icons.filled.WaterDrop
import app.chompass.services.grounding.GroundedEntryFeature
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import app.chompass.R
import app.chompass.services.grounding.FoodSuggestion
import app.chompass.models.FastingPhase
import app.chompass.models.HabitPreset
import app.chompass.models.HabitPresetCatalog
import app.chompass.models.HabitPresetDomain

import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.ChompassSheetCornerRadius
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.components.blockSheetDragAtScrollEdges
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import app.chompass.ui.theme.warning
import app.chompass.ui.theme.water
import app.chompass.ui.theme.caffeine
import app.chompass.models.WaterQuickPresets
import app.chompass.models.WaterAmountFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(
    onPhoto: () -> Unit,
    onNote: () -> Unit,
    onSavedMeals: () -> Unit,
    onVoice: () -> Unit,
    onBarcode: () -> Unit,
    onManual: () -> Unit,
    onCopyFromDay: () -> Unit,
    onManualActive: () -> Unit = {},
    onGrounded: () -> Unit = {},
    onQueue: () -> Unit = {},
    queuePendingCount: Int = 0,
    onDismiss: () -> Unit,
    aiFeaturesEnabled: Boolean = true,
    barcodeEnabled: Boolean = true,
    waterTrackingEnabled: Boolean = false,
    waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    waterUseMetric: Boolean = true,
    onWater: (Int) -> Unit = {},
    onWaterCustom: () -> Unit = {},
    nicotineTrackingEnabled: Boolean = false,
    nicotineQuickKinds: List<String> = HabitPresetDomain.NICOTINE.defaultQuickKindIds,
    nicotinePresets: List<HabitPreset> = HabitPresetDomain.NICOTINE.defaultCatalog.presets,
    onNicotine: (String) -> Unit = {},
    onNicotineCustom: () -> Unit = {},
    caffeineTrackingEnabled: Boolean = false,
    caffeineQuickKinds: List<String> = HabitPresetDomain.CAFFEINE.defaultQuickKindIds,
    caffeinePresets: List<HabitPreset> = HabitPresetDomain.CAFFEINE.defaultCatalog.presets,
    onCaffeine: (String) -> Unit = {},
    onCaffeineCustom: () -> Unit = {},
    fastingEnabled: Boolean = false,
    fastingPhase: FastingPhase = FastingPhase.IDLE,
    fastingElapsedMillis: Long = 0L,
    fastingEatingElapsedMillis: Long = 0L,
    fastingGoalHours: Int = 0,
    fastingEatHours: Int = 0,
    fastingGoalReached: Boolean = false,
    fastingAutoStarted: Boolean = false,
    fastingNextFastStartMillis: Long? = null,
    fastingNowMillis: Long = 0L,
    fastingAutoWindows: Boolean = false,
    onStartFast: () -> Unit = {},
    onStopFast: () -> Unit = {},
    savedTab: SavedTab = SavedTab.RECENTS,
    savedRows: List<FoodSuggestion> = emptyList(),
    onSavedTabChange: (SavedTab) -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    suggestions: List<FoodSuggestion> = emptyList(),
    suggestionsNetworkPending: Boolean = false,
    packagedSearchEnabled: Boolean = false,
    onPackagedSearchChange: (Boolean) -> Unit = {},
    onOpenFoodSettings: () -> Unit = {},
    onPickSuggestion: (FoodSuggestion) -> Unit = {},
    onReviewSuggestion: (FoodSuggestion) -> Unit = {},
    onAnalyzeQuery: (String) -> Unit = {},
) {
    // Two anchors, and a content height that never changes. The sheet animates
    // its own offset between them (expand/partialExpand), which is the only
    // motion M3 actually animates: anchors are derived from content size and
    // settled to, so growing the body made the sheet lag the content and then
    // snap at the end of the tween.
    val sheetState = rememberChompassSheetState(skipPartiallyExpanded = false)
    // At the expanded anchor the sheet covers the screen, and rounded top
    // corners then have nothing to sit against — they cut two notches out of
    // the display's own corners. Flatten them as it settles there so the sheet
    // reads as a screen, and round them back on the way down. Keyed on
    // targetValue, so the corners travel with the sheet instead of squaring off
    // after it has already landed.
    val topCornerRadius by animateDpAsState(
        targetValue = if (sheetState.targetValue == SheetValue.Expanded) 0.dp else ChompassSheetCornerRadius,
        animationSpec = tween(durationMillis = 220),
        label = "addFoodSheetCorner",
    )
    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius),
        // Codeberg #6: this sheet's content pads itself with imePadding, so
        // the default chrome insets feed the M3 feedback loop — consumeWindowInsets(0,0,0,max(0,offset))
        // changes as the sheet moves, which re-pads the content, which
        // re-measures it, which moves the anchors and re-bases the offset. That
        // is what made the grow-on-scroll expansion snap instead of glide.
        // Every other self-padding sheet here zeroes these for the same reason.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        AddFoodSheetContent(
            autoFocusQuery = true,
            sheetState = sheetState,
            onPhoto = { onDismiss(); onPhoto() },
            onNote = { onDismiss(); onNote() },
            onSavedMeals = { onDismiss(); onSavedMeals() },
            onVoice = { onDismiss(); onVoice() },
            onBarcode = { onDismiss(); onBarcode() },
            onManual = { onDismiss(); onManual() },
            onCopyFromDay = { onDismiss(); onCopyFromDay() },
            onManualActive = { onDismiss(); onManualActive() },
            onGrounded = { onDismiss(); onGrounded() },
            onQueue = { onDismiss(); onQueue() },
            queuePendingCount = queuePendingCount,
            aiFeaturesEnabled = aiFeaturesEnabled,
            barcodeEnabled = barcodeEnabled,
            waterTrackingEnabled = waterTrackingEnabled,
            waterQuickPresetsMl = waterQuickPresetsMl,
            waterUseMetric = waterUseMetric,
            onWater = { ml -> onDismiss(); onWater(ml) },
            onWaterCustom = { onDismiss(); onWaterCustom() },
            nicotineTrackingEnabled = nicotineTrackingEnabled,
            nicotineQuickKinds = nicotineQuickKinds,
            nicotinePresets = nicotinePresets,
            onNicotine = { kind -> onDismiss(); onNicotine(kind) },
            onNicotineCustom = { onDismiss(); onNicotineCustom() },
            caffeineTrackingEnabled = caffeineTrackingEnabled,
            caffeineQuickKinds = caffeineQuickKinds,
            caffeinePresets = caffeinePresets,
            onCaffeine = { kind -> onDismiss(); onCaffeine(kind) },
            onCaffeineCustom = { onDismiss(); onCaffeineCustom() },
            fastingEnabled = fastingEnabled,
            fastingPhase = fastingPhase,
            fastingElapsedMillis = fastingElapsedMillis,
            fastingEatingElapsedMillis = fastingEatingElapsedMillis,
            fastingGoalHours = fastingGoalHours,
            fastingEatHours = fastingEatHours,
            fastingGoalReached = fastingGoalReached,
            fastingAutoStarted = fastingAutoStarted,
            fastingNextFastStartMillis = fastingNextFastStartMillis,
            fastingNowMillis = fastingNowMillis,
            fastingAutoWindows = fastingAutoWindows,
            onStartFast = { onDismiss(); onStartFast() },
            onStopFast = { onDismiss(); onStopFast() },
            savedTab = savedTab,
            savedRows = savedRows,
            // Switching tabs stays in the sheet, like typing does.
            onSavedTabChange = onSavedTabChange,
            // Typing must not close the sheet — unlike every other callback
            // here, this one is not an action that leaves for a destination.
            query = query,
            onQueryChange = onQueryChange,
            suggestions = suggestions,
            suggestionsNetworkPending = suggestionsNetworkPending,
            packagedSearchEnabled = packagedSearchEnabled,
            // Toggling the opt-in re-runs the search; it must not dismiss.
            onPackagedSearchChange = onPackagedSearchChange,
            onOpenFoodSettings = onOpenFoodSettings,
            onPickSuggestion = { s -> onDismiss(); onPickSuggestion(s) },
            onReviewSuggestion = { s -> onDismiss(); onReviewSuggestion(s) },
            onAnalyzeQuery = { text -> onDismiss(); onAnalyzeQuery(text) },
        )
    }
}

/** Sheet body without ModalBottomSheet — used for JVM screenshot capture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddFoodSheetContent(
    /** Non-null lets the results pane expand the sheet itself. */
    sheetState: SheetState? = null,
    /**
     * Fixed pane height for hosts that wrap their content instead of bounding
     * it — the release screenshot harness draws this body in a plain Surface,
     * where filling the height would stretch it over the whole screen and a
     * weighted child has no remainder to divide.
     */
    fixedPaneHeight: Dp? = null,
    onPhoto: () -> Unit = {},
    onNote: () -> Unit = {},
    onSavedMeals: () -> Unit = {},
    onVoice: () -> Unit = {},
    onBarcode: () -> Unit = {},
    onManual: () -> Unit = {},
    onCopyFromDay: () -> Unit = {},
    onManualActive: () -> Unit = {},
    onGrounded: () -> Unit = {},
    /** Codeberg #53: open the analysis queue (pending badge in the label). */
    onQueue: () -> Unit = {},
    queuePendingCount: Int = 0,
    /** Codeberg #20 phase 2: false hides the AI logging tiles (photo/note/voice);
     *  barcode, search, manual, recents, copy-from-day and water all stay. */
    aiFeaturesEnabled: Boolean = true,
    barcodeEnabled: Boolean = true,
    waterTrackingEnabled: Boolean = false,
    waterQuickPresetsMl: List<Int> = WaterQuickPresets.DEFAULT_AMOUNTS_ML,
    waterUseMetric: Boolean = true,
    onWater: (Int) -> Unit = {},
    onWaterCustom: () -> Unit = {},
    nicotineTrackingEnabled: Boolean = false,
    nicotineQuickKinds: List<String> = HabitPresetDomain.NICOTINE.defaultQuickKindIds,
    nicotinePresets: List<HabitPreset> = HabitPresetDomain.NICOTINE.defaultCatalog.presets,
    onNicotine: (String) -> Unit = {},
    onNicotineCustom: () -> Unit = {},
    caffeineTrackingEnabled: Boolean = false,
    caffeineQuickKinds: List<String> = HabitPresetDomain.CAFFEINE.defaultQuickKindIds,
    caffeinePresets: List<HabitPreset> = HabitPresetDomain.CAFFEINE.defaultCatalog.presets,
    onCaffeine: (String) -> Unit = {},
    onCaffeineCustom: () -> Unit = {},
    fastingEnabled: Boolean = false,
    fastingPhase: FastingPhase = FastingPhase.IDLE,
    fastingElapsedMillis: Long = 0L,
    fastingEatingElapsedMillis: Long = 0L,
    fastingGoalHours: Int = 0,
    fastingEatHours: Int = 0,
    fastingGoalReached: Boolean = false,
    fastingAutoStarted: Boolean = false,
    fastingNextFastStartMillis: Long? = null,
    fastingNowMillis: Long = 0L,
    fastingAutoWindows: Boolean = false,
    onStartFast: () -> Unit = {},
    onStopFast: () -> Unit = {},
    savedTab: SavedTab = SavedTab.RECENTS,
    savedRows: List<FoodSuggestion> = emptyList(),
    onSavedTabChange: (SavedTab) -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    suggestions: List<FoodSuggestion> = emptyList(),
    suggestionsNetworkPending: Boolean = false,
    packagedSearchEnabled: Boolean = false,
    onPackagedSearchChange: (Boolean) -> Unit = {},
    onOpenFoodSettings: () -> Unit = {},
    onPickSuggestion: (FoodSuggestion) -> Unit = {},
    onReviewSuggestion: (FoodSuggestion) -> Unit = {},
    onAnalyzeQuery: (String) -> Unit = {},
    autoFocusQuery: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val expandedLabel = stringResource(R.string.cd_expanded)
    val collapsedLabel = stringResource(R.string.cd_collapsed)
    val searching = query.isNotBlank()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        focusManager.clearFocus()
        keyboard?.hide()
        Unit
    }
    val queryFocus = remember { FocusRequester() }
    // Modal open: arrive tall with the field focused and the keyboard up.
    // The expand is fired without awaiting its settle so the IME and the
    // sheet animation run together; keyboard dismissal returns to the user
    // (back gesture or the field).
    LaunchedEffect(autoFocusQuery) {
        if (!autoFocusQuery) return@LaunchedEffect
        scope.launch { sheetState?.expand() }
        queryFocus.requestFocus()
        keyboard?.show()
    }
    val expandLabel = stringResource(R.string.home_view_more)
    Column(
        Modifier
            .fillMaxWidth()
            // Constant height, so the sheet's anchors are computed once and
            // never move. Everything inside divides this space up; nothing
            // changes the total.
            .then(if (fixedPaneHeight == null) Modifier.fillMaxHeight() else Modifier)
            // No bottom padding and no navigationBarsPadding: the content runs
            // to the bottom edge and draws under the system navigation bar, so
            // the pane is never shortened by a margin the user cannot see past.
            // The breathing room lives at the end of the scrolling content
            // instead (ADD_FOOD_BOTTOM_SPACE), where it is only reached by
            // scrolling all the way down. imePadding stays, because the
            // keyboard must not cover the field it is typing into.
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp)
    ) {
        Text(
            stringResource(R.string.add_food_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))

        AddFoodQueryRow(
            query = query,
            onQueryChange = onQueryChange,
            fieldModifier = Modifier.focusRequester(queryFocus),
            onPhoto = onPhoto,
            onVoice = onVoice,
            onBarcode = onBarcode,
            onAnalyze = onAnalyzeQuery,
            onNote = onNote,
            onSavedMeals = onSavedMeals,
            onManual = onManual,
            onCopyFromDay = onCopyFromDay,
            onManualActive = onManualActive,
            onQueue = onQueue,
            onGrounded = onGrounded,
            queuePendingCount = queuePendingCount,
            groundedEnabled = GroundedEntryFeature.ENABLED,
            aiFeaturesEnabled = aiFeaturesEnabled,
            barcodeEnabled = barcodeEnabled,
        )

        // The tabs' slot out of search. In search it keeps only enough height
        // to stay a tap target, because the band has nothing to say any more:
        // the "still searching" signal moved into the database section's own
        // label, down in the results, and so did the packaged-products opt-in.
        // The results pane takes the height this gives up (it is the weighted
        // child), so the sheet itself does not resize when a query starts —
        // only the split inside it changes.
        //
        // It swallows the spacers that used to sit either side of it so the
        // whole strip between the tool pills and the results is one target:
        // tapping it expands the sheet. Children with their own click handler
        // (the tabs) win, so only the dead space around them expands.
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (searching) 16.dp else 64.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = sheetState != null,
                    onClickLabel = expandLabel,
                    role = Role.Button,
                ) {
                    scope.launch { runCatching { sheetState?.expand() } }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!searching) {
                SegmentedTabs(
                    selected = savedTab,
                    // The tabs are a "show me what I already have" gesture; the
                    // keyboard has nothing left to contribute once it is made.
                    onSelect = { tab -> dismissKeyboard(); onSavedTabChange(tab) },
                )
            }
        }

        // Whether anything is rendered below the pane decides who carries the
        // trailing space: the list when it is the last thing in the sheet, the
        // tracker scroller when that is.
        val enabledTrackerCount = listOf(
            waterTrackingEnabled,
            nicotineTrackingEnabled,
            caffeineTrackingEnabled,
            fastingEnabled,
        ).count { it }
        val hasTrackers = enabledTrackerCount > 0

        // One pane, two data sources, one reserved height. Showing the saved
        // meals here rather than a shorter hub means the sheet is already the
        // size the search results need, so the first keystroke swaps the rows
        // without resizing the sheet and shoving the input field upward.
        AddFoodSuggestionList(
            suggestions = if (searching) suggestions else savedRows,
            networkPending = suggestionsNetworkPending,
            searching = searching,
            // The query itself, not just "searching": every edit re-parks the
            // list at the top, and each one has to disarm the grow-on-scroll
            // gesture or it reads that as the user collapsing the sheet. The
            // opt-in rides along for a narrower reason: switching it off takes
            // rows away, and a list that loses enough of them clamps its own
            // scroll back toward the top. That clamp is not the user asking for
            // the sheet back either.
            listIdentity = if (searching) query to packagedSearchEnabled else savedTab,
            sheetState = sheetState,
            packagedSearchEnabled = packagedSearchEnabled,
            onPackagedSearchChange = onPackagedSearchChange,
            onOpenFoodSettings = onOpenFoodSettings,
            bottomSpace = if (hasTrackers) 0.dp else ADD_FOOD_BOTTOM_SPACE,
            // Takes the space the fixed-height rows above and below leave over.
            modifier = if (fixedPaneHeight == null) {
                Modifier.weight(1f)
            } else {
                Modifier.height(fixedPaneHeight)
            },
            onPick = onPickSuggestion,
            onReview = onReviewSuggestion,
        )

        // Nothing below the pane unless a tracker is actually switched on.
        // The scroll column used to emit its spacers regardless, which left a
        // dead strip along the bottom of the sheet once More options moved out
        // of here and into the pill row.
        if (!hasTrackers) return@Column
        // The trackers stay rendered in both states. They sit below a
        // fixed-height results pane so they can never push results around, and
        // keeping them is what makes the two states the same total height.
        Spacer(Modifier.height(10.dp))

        // Tracker content scrolls on its own. Device pass #2 (2026-08-24):
        // the trackers outgrow the sheet on shorter screens, so this column has
        // to scroll; like TextInputSheet it blocks drag-from-content dismissal
        // (handle/scrim still dismiss). The scroller lives here rather than on
        // the outer column so the search branch can host a lazy list.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .blockSheetDragAtScrollEdges(scrollState)
        ) {
        // Four trackers at once bury the results pane, so the section folds.
        // Two or fewer start open: at that size the rows are the reason the
        // user pulled the sheet up, and hiding them costs a tap for nothing.
        var trackersExpanded by rememberSaveable { mutableStateOf(enabledTrackerCount <= 2) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    onClick = { trackersExpanded = !trackersExpanded },
                    role = Role.Button,
                )
                .semantics {
                    stateDescription = if (trackersExpanded) expandedLabel else collapsedLabel
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.add_food_trackers_section),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (trackersExpanded) Icons.Filled.KeyboardArrowDown
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
        }
        AnimatedVisibility(
            visible = trackersExpanded,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.75f)),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.75f)),
        ) {
            Column {
                if (waterTrackingEnabled) {
                    Spacer(Modifier.height(12.dp))
                    AddFoodWaterQuickRow(
                        presetsMl = waterQuickPresetsMl,
                        useMetric = waterUseMetric,
                        onWater = onWater,
                        onWaterCustom = onWaterCustom,
                    )
                }
                if (nicotineTrackingEnabled) {
                    Spacer(Modifier.height(12.dp))
                    AddFoodNicotineQuickRow(
                        quickKinds = nicotineQuickKinds,
                        presets = nicotinePresets,
                        onNicotine = onNicotine,
                        onNicotineCustom = onNicotineCustom,
                    )
                }
                if (caffeineTrackingEnabled) {
                    Spacer(Modifier.height(12.dp))
                    AddFoodCaffeineQuickRow(
                        quickKinds = caffeineQuickKinds,
                        presets = caffeinePresets,
                        onCaffeine = onCaffeine,
                        onCaffeineCustom = onCaffeineCustom,
                    )
                }
                if (fastingEnabled) {
                    Spacer(Modifier.height(12.dp))
                    FastingHubControl(
                        phase = fastingPhase,
                        fastHours = fastingGoalHours,
                        eatHours = fastingEatHours,
                        fastElapsedMillis = fastingElapsedMillis,
                        eatElapsedMillis = fastingEatingElapsedMillis,
                        goalReached = fastingGoalReached,
                        autoStarted = fastingAutoStarted,
                        nextFastStartMillis = fastingNextFastStartMillis,
                        nowMillis = fastingNowMillis,
                        autoMode = fastingAutoWindows,
                        onStart = onStartFast,
                        onStop = onStopFast,
                    )
                }
            }
        }
        // Same trailing space the suggestion pane carries when nothing follows
        // it: inside the scroller, so it is reached by scrolling to the end
        // rather than shortening the visible tracker area.
        Spacer(Modifier.height(ADD_FOOD_BOTTOM_SPACE))
        }
    }
}

/**
 * Breathing room below the last row of whichever part of the sheet scrolls.
 * The sheet draws under the system navigation bar, so this is what keeps the
 * final row clear of it — but only once the user has scrolled that far, rather
 * than costing the pane the same height at every scroll position.
 */
private val ADD_FOOD_BOTTOM_SPACE = 60.dp

@Composable
private fun AddFoodWaterQuickRow(
    presetsMl: List<Int>,
    useMetric: Boolean,
    onWater: (Int) -> Unit,
    onWaterCustom: () -> Unit,
) {
    val presets = remember(presetsMl) {
        WaterQuickPresets(presetsMl).validatedOrDefault().amountsMl
    }
    var selectedIndex by remember(presets) {
        mutableIntStateOf((presets.size / 2).coerceAtMost(presets.lastIndex).coerceAtLeast(0))
    }
    LaunchedEffect(presets) {
        selectedIndex = selectedIndex.coerceIn(0, presets.lastIndex)
    }
    val selectedMl = presets[selectedIndex]

    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.WaterDrop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.water.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
        )
        if (presets.size > 1) {
            Slider(
                value = selectedIndex.toFloat(),
                onValueChange = { selectedIndex = it.roundToInt().coerceIn(0, presets.lastIndex) },
                valueRange = 0f..presets.lastIndex.toFloat(),
                steps = (presets.size - 2).coerceAtLeast(0),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AppColors.Calorie,
                    activeTrackColor = AppColors.Calorie.copy(alpha = 0.75f),
                    inactiveTrackColor = AppColors.Calorie.copy(alpha = 0.16f),
                ),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            waterAmountLabel(selectedMl, useMetric),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            modifier = Modifier.widthIn(min = 52.dp),
        )
        IconButton(
            onClick = { onWater(selectedMl) },
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_log_water),
                tint = AppColors.Calorie,
                modifier = Modifier.size(22.dp),
            )
        }
        TextButton(
            onClick = onWaterCustom,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Icon(
                Icons.Filled.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.water_custom_short),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun waterAmountLabel(ml: Int, useMetric: Boolean): String =
    if (useMetric) {
        stringResource(R.string.water_amount_ml, ml)
    } else {
        stringResource(R.string.water_amount_fl_oz, WaterAmountFormat.flOzFromMl(ml))
    }

/**
 * Nicotine quick-log row (optional tracker): one chip per quick kind (+1) and
 * a Custom button opening [NicotineCustomCountSheet]. Mirrors the water row.
 */
@Composable
private fun AddFoodNicotineQuickRow(
    quickKinds: List<String>,
    presets: List<HabitPreset>,
    onNicotine: (String) -> Unit,
    onNicotineCustom: () -> Unit,
) {
    // Enabled presets in catalog order (custom ids survive the selection;
    // empty selection falls back to the defaults via hubPresets).
    val chips = remember(quickKinds, presets) {
        HabitPresetDomain.NICOTINE.hubPresets(quickKinds, HabitPresetCatalog(presets))
    }

    FlowRow(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { preset ->
            AssistChip(
                onClick = { onNicotine(preset.id) },
                label = {
                    Text(
                        stringResource(
                            R.string.nicotine_quick_plus_one,
                            trackerPresetLabel(presets, preset.id, HabitPresetDomain.NICOTINE),
                        ),
                        maxLines = 1,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.12f),
                ),
            )
        }
        TextButton(
            onClick = onNicotineCustom,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Icon(
                Icons.Filled.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.nicotine_custom_short),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Caffeine quick-log row (optional tracker): one chip per quick kind (+1
 * with the kind's default mg) and a Custom button opening
 * [CaffeineCustomSheet]. Mirrors the nicotine row.
 */
@Composable
private fun AddFoodCaffeineQuickRow(
    quickKinds: List<String>,
    presets: List<HabitPreset>,
    onCaffeine: (String) -> Unit,
    onCaffeineCustom: () -> Unit,
) {
    // Enabled presets in catalog order; +1 logs the preset default mg.
    val chips = remember(quickKinds, presets) {
        HabitPresetDomain.CAFFEINE.hubPresets(quickKinds, HabitPresetCatalog(presets))
    }

    FlowRow(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { preset ->
            AssistChip(
                onClick = { onCaffeine(preset.id) },
                label = {
                    Text(
                        stringResource(
                            R.string.caffeine_quick_plus_one,
                            trackerPresetLabel(presets, preset.id, HabitPresetDomain.CAFFEINE),
                        ),
                        maxLines = 1,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.caffeine.copy(alpha = 0.12f),
                ),
            )
        }
        TextButton(
            onClick = onCaffeineCustom,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Icon(
                Icons.Filled.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.caffeine_custom_short),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Home with a static add-food sheet overlay for release screenshots (no ModalBottomSheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeAddFoodScreenshotContent(
    ui: HomeUiState,
    weekStartsOnMonday: Boolean = true,
    savedRows: List<FoodSuggestion> = emptyList(),
    /** Screenshot host wraps its content, so the pane needs a concrete height. */
    fixedPaneHeight: Dp = 320.dp,
    /** Non-blank renders the search state instead of the zero-query tabs. */
    query: String = "",
    suggestions: List<FoodSuggestion> = emptyList(),
    /** Renders the packaged-products toggle in its on state. */
    packagedSearchEnabled: Boolean = false,
) {
    Box(Modifier.fillMaxSize()) {
        HomeScreenPreviewContent(ui = ui, weekStartsOnMonday = weekStartsOnMonday)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            AddFoodSheetContent(
                savedRows = savedRows,
                fixedPaneHeight = fixedPaneHeight,
                query = query,
                suggestions = suggestions,
                packagedSearchEnabled = packagedSearchEnabled,
            )
        }
    }
}
