package app.chompass.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import app.chompass.R
import app.chompass.models.NutrientSourceKind
import app.chompass.services.grounding.DatabaseSearchResult
import app.chompass.services.grounding.FoodSuggestion
import app.chompass.services.grounding.SuggestionKind
import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.components.energyText
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The Add Food sheet's single input: one field over the user's saved foods and
 * the bundled/remote food databases, with the capture tools beside it. Replaces
 * the tile grid that made the user choose a tool before saying what they ate.
 */
@Composable
internal fun AddFoodQueryRow(
    /** Meal planning mode: opens the week planner from the More menu. */
    planningMode: Boolean = false,
    onPlanWeek: () -> Unit = {},
    query: String,
    onQueryChange: (String) -> Unit,
    onPhoto: () -> Unit,
    onVoice: () -> Unit,
    onBarcode: () -> Unit,
    onAnalyze: (String) -> Unit,
    onNote: () -> Unit,
    onSavedMeals: () -> Unit,
    onManual: () -> Unit,
    onCopyFromDay: () -> Unit,
    onManualActive: () -> Unit,
    onQueue: () -> Unit,
    onGrounded: () -> Unit,
    queuePendingCount: Int,
    groundedEnabled: Boolean,
    aiFeaturesEnabled: Boolean,
    barcodeEnabled: Boolean,
    fieldModifier: Modifier = Modifier,
    /** Trackers pill (5.0.0 MUST 1): shown while any tracker row renders. */
    trackersPillVisible: Boolean = false,
    trackersExpanded: Boolean = false,
    onTrackersToggle: () -> Unit = {},
    /** Quick-log rows revealed by the pill; null when no tracker renders. */
    trackersContent: (@Composable () -> Unit)? = null,
) {
    val submit = { if (query.isNotBlank() && aiFeaturesEnabled) onAnalyze(query.trim()) }
    Column(Modifier.fillMaxWidth()) {
        // Full width: the field is the primary control, and sharing its row with
        // three buttons squeezed the placeholder into an ellipsis.
        FudGlassTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.food_search_placeholder),
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                    modifier = Modifier.size(18.dp),
                )
            },
            // The trailing slot carries whatever the field can do for you right
            // now: dictate while it is empty, analyse once there is text. With
            // AI off there is neither, so it falls back to clearing the field.
            trailingIcon = when {
                query.isEmpty() && aiFeaturesEnabled -> {
                    {
                        IconButton(onClick = onVoice) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.home_menu_voice),
                                tint = AppColors.Calorie,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                query.isEmpty() -> null
                aiFeaturesEnabled -> {
                    {
                        IconButton(onClick = submit) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = stringResource(R.string.action_analyze),
                                tint = AppColors.Calorie,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                else -> {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_clear),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            // Whichever action the device's keyboard actually shows — Send,
            // Done, Go, Search — runs the analysis rather than just closing the
            // keyboard.
            keyboardActions = KeyboardActions(
                onSend = { submit() },
                onDone = { submit() },
                onGo = { submit() },
                onSearch = { submit() },
            ),
            modifier = fieldModifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) {
                    false
                } else {
                    when (event.key) {
                        // Enter only. Escape conventionally cancels, so leave it
                        // to the platform rather than spending an AI call on it.
                        Key.Enter, Key.NumPadEnter -> {
                            submit()
                            true
                        }
                        else -> false
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        // Labelled pills under the field rather than bare glyphs beside it:
        // these are the three ways in that are not typing, and they read as
        // choices instead of field decoration.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (aiFeaturesEnabled) {
                AddFoodToolButton(
                    icon = Icons.Filled.PhotoCamera,
                    label = stringResource(R.string.add_food_hero_photo),
                    onClick = onPhoto,
                    modifier = Modifier.weight(1f),
                )
            }
            AddFoodMoreButton(
                onNote = onNote,
                onSavedMeals = onSavedMeals,
                onManual = onManual,
                onCopyFromDay = onCopyFromDay,
                onManualActive = onManualActive,
                onQueue = onQueue,
                onGrounded = onGrounded,
                queuePendingCount = queuePendingCount,
                aiFeaturesEnabled = aiFeaturesEnabled,
                groundedEnabled = groundedEnabled,
                /** Meal planning mode: opens the week planner from the menu. */
                planningMode = planningMode,
                onPlanWeek = onPlanWeek,
                modifier = Modifier.weight(1f),
            )
            if (barcodeEnabled) {
                AddFoodToolButton(
                    icon = Icons.Filled.QrCodeScanner,
                    label = stringResource(R.string.home_menu_barcode),
                    onClick = onBarcode,
                    modifier = Modifier.weight(1f),
                )
            }
            // Fourth pill (MUST 1): the trackers fold in here instead of a
            // standalone section between the field and the results. The
            // chevron is the header affordance the section used to carry;
            // TalkBack reads the expanded/collapsed state.
            if (trackersPillVisible) {
                val expandedLabel = stringResource(R.string.cd_expanded)
                val collapsedLabel = stringResource(R.string.cd_collapsed)
                AddFoodToolButton(
                    icon = if (trackersExpanded) Icons.Filled.KeyboardArrowDown
                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    label = stringResource(R.string.add_food_trackers_section),
                    onClick = onTrackersToggle,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            stateDescription = if (trackersExpanded) expandedLabel else collapsedLabel
                        },
                )
            }
        }

        // The quick-log rows open directly under the pill that toggles them:
        // one tap from the open sheet to a +1 log. Expanding changes the
        // split inside the sheet's fixed-height pane, never the sheet's own
        // height, so the anchors (and the grow-on-scroll logic below) stay
        // put.
        trackersContent?.let { content ->
            AnimatedVisibility(
                visible = trackersExpanded,
                enter = expandVertically(animationSpec = spring(dampingRatio = 0.75f)),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.75f)),
            ) {
                content()
            }
        }
    }
}

/**
 * One capture tool under the search field: icon plus label on the app's
 * standard translucent-with-hairline surface, so it matches the suggestion
 * rows and the More entries rather than inventing a third button style.
 */
@Composable
private fun AddFoodToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isDarkTheme()
    val shape = RoundedCornerShape(AppRadii.Field)
    val fill = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val border = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(fill)
            .border(0.5.dp, border, shape)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.Calorie, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The Open Food Facts on-state chip on the "Search food databases" heading
 * (5.0.0 MUST 4). With the search on, this is the fast privacy-off: one tap
 * on the heading line turns the packaged-products feed back off — no full
 * row, no detour into Settings. The Settings > Food & Entry switch keeps the
 * authority; this only mirrors and flips it, so the two never disagree.
 *
 * The off state is the heading's existing inline hint (settings link), so
 * both states occupy the same single heading line and the results list below
 * never gains or loses a row to the toggle. Role.Switch lets TalkBack read
 * the on/off state; the label is the source's own name — the same resource
 * the Settings row, the off-hint and the result badges use.
 */
@Composable
private fun PackagedSearchOffChip(onOff: () -> Unit) {
    val shape = RoundedCornerShape(AppRadii.Field)
    Row(
        Modifier
            // 48dp touch target without a taller visual chip: the heading
            // grows into its own tap area, the chip stays a one-line accent.
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(AppColors.Calorie.copy(alpha = 0.10f))
            .border(0.5.dp, AppColors.Calorie.copy(alpha = 0.45f), shape)
            .toggleable(
                value = true,
                role = Role.Switch,
                onValueChange = { onOff() },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            // The icon Settings gives this source, so the two controls read
            // as one seen twice.
            Icons.Outlined.Public,
            contentDescription = null,
            tint = AppColors.Calorie,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.food_search_source_off),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The unified result list. It fills whatever the fixed-height rows above and
 * below leave over and never changes size itself, so results streaming in
 * cannot move the search field. Scrolling a long list expands the sheet.
 *
 * Search results carry rows from every source at once, so they are split into
 * labelled sections. The zero-query list is one source already — whichever tab
 * is selected above it — and stays a plain list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddFoodSuggestionList(
    suggestions: List<FoodSuggestion>,
    networkPending: Boolean,
    /** False renders the zero-query saved-meals rows. */
    searching: Boolean,
    /**
     * Disarms the grow-on-scroll gesture whenever the list underneath changes.
     * Must vary with the query, not just with search-vs-saved, or editing a
     * query collapses a sheet the user scrolled open.
     */
    listIdentity: Any,
    /** Non-null lets scrolling expand the sheet itself. */
    sheetState: SheetState? = null,
    /** State of the Open Food Facts opt-in under the databases heading. */
    packagedSearchEnabled: Boolean = false,
    onPackagedSearchChange: (Boolean) -> Unit = {},
    onOpenFoodSettings: () -> Unit = {},
    /** Meal planning mode: saved rows carry a Plan-for-a-day action. */
    planningMode: Boolean = false,
    onPlanSuggestion: ((FoodSuggestion) -> Unit)? = null,
    /**
     * Trailing space below the last row. Lives inside the scroll range, so it
     * is only reached by scrolling to the end of the list — the pane itself
     * still runs to the bottom edge of the sheet.
     */
    bottomSpace: Dp = 0.dp,
    modifier: Modifier = Modifier,
    onPick: (FoodSuggestion) -> Unit,
    onReview: (FoodSuggestion) -> Unit,
) {
    val sections = remember(suggestions, searching) {
        if (searching) groupSuggestions(suggestions) else emptyList()
    }
    // The databases block is pulled out of the ordered run because it is the
    // only one that renders on an empty hand: it carries the Open Food Facts
    // opt-in, and an opt-in the user cannot find when nothing matched is no
    // opt-in at all. The ranker's score bands already sort it last, so pinning
    // it there changes no order anyone has seen.
    val localSections = remember(sections) {
        sections.filterNot { it.group == AddFoodGroup.DATABASES }
    }
    val databaseRows = remember(sections) {
        sections.firstOrNull { it.group == AddFoodGroup.DATABASES }?.rows.orEmpty()
    }
    val listState = rememberLazyListState()
    // Scrolling a long list asks the SHEET to expand, rather than growing this
    // pane and letting the sheet chase the new size. M3 animates its own offset
    // between anchors properly; it only settles when the anchors themselves
    // move, which is what content-height growth caused.
    val worthExpanding = suggestions.size >= EXPAND_MIN_ROWS
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    // Only the user's own scrolling moves the sheet. Swapping the list
    // underneath — switching tab, editing the query, results streaming in —
    // parks the new list at the top, and a `scrolled == false` reading there is
    // the list sitting where it was placed, not the user asking for the sheet
    // back. So the gesture stays disarmed until the user actually scrolls this
    // list, and every identity change disarms it again.
    //
    // Both halves matter. Without the arming flag, the first results to land
    // after a keystroke re-run this effect and collapse the sheet; without the
    // query in [listIdentity], only the first keystroke of a query disarms and
    // every later one collapses a sheet the user had scrolled open. The
    // packaged-products opt-in is in that identity for the same reason: it
    // swaps the list without touching the query.
    val lastIdentity = remember { mutableStateOf(listIdentity) }
    val armed = remember { mutableStateOf(false) }
    LaunchedEffect(scrolled, worthExpanding, sheetState, listIdentity) {
        if (sheetState == null) return@LaunchedEffect
        if (lastIdentity.value != listIdentity) {
            lastIdentity.value = listIdentity
            armed.value = false
            return@LaunchedEffect
        }
        if (scrolled) armed.value = true
        if (!armed.value) return@LaunchedEffect
        runCatching {
            when (sheetGrowAction(scrolled = scrolled, worthExpanding = worthExpanding)) {
                SheetGrow.EXPAND -> sheetState.expand()
                SheetGrow.COLLAPSE -> sheetState.partialExpand()
                SheetGrow.KEEP -> Unit
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        // Everything lives inside the one lazy list, the Analyze row included:
        // sitting it below the list clipped it under the sheet edge once the
        // results filled the viewport (caught rendering the preview).
        ChompassSheetLazyColumn(
            listState = listState,
            // Fills whatever the fixed-height rows above and below leave over.
            // Nothing here ever changes size, so results streaming in cannot
            // move the search field, and switching between the saved list and
            // the search results cannot resize the sheet.
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomSpace),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (searching) {
                localSections.forEach { section ->
                    item(key = "section:${section.group.name}") {
                        AddFoodSectionLabel(section.group)
                    }
                    items(section.rows, key = { it.key }, contentType = { it.kind }) { suggestion ->
                        AddFoodSuggestionRow(
                            suggestion = suggestion,
                            onClick = { onPick(suggestion) },
                            onLongClick = { onReview(suggestion) },
                            planningMode = planningMode,
                            onPlanSuggestion = onPlanSuggestion,
                        )
                    }
                }
                // Said before the databases block rather than after it, so the
                // list reads "nothing of yours matched — here is another place
                // to look" instead of trailing the verdict after the offer.
                if (localSections.isEmpty() && databaseRows.isEmpty() && !networkPending) {
                    item(key = "empty") { AddFoodEmptyLine(R.string.saved_meals_no_match) }
                }
                // Always, matched or not. The label lands ahead of its rows so
                // a wait has somewhere to show and the rows fill in underneath
                // it. Either opt-in state stays on this one heading line (5.0.0
                // MUST 4): off, a muted hint links to the setting; on, the
                // Open Food Facts chip on the same line turns it back off.
                item(key = "section:DATABASES") {
                    AddFoodSectionLabel(
                        AddFoodGroup.DATABASES,
                        pending = networkPending,
                        trailing = if (packagedSearchEnabled) {
                            { PackagedSearchOffChip(onOff = { onPackagedSearchChange(false) }) }
                        } else {
                            {
                                Row(
                                    Modifier
                                        .clickable(onClick = onOpenFoodSettings, role = Role.Button)
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(R.string.food_search_source_off),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                    )
                                    Icon(
                                        Icons.Outlined.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        },
                    )
                }
                items(databaseRows, key = { it.key }, contentType = { it.kind }) { suggestion ->
                    AddFoodSuggestionRow(
                        suggestion = suggestion,
                        onClick = { onPick(suggestion) },
                        onLongClick = { onReview(suggestion) },
                        planningMode = planningMode,
                        onPlanSuggestion = onPlanSuggestion,
                    )
                }
            } else {
                items(suggestions, key = { it.key }, contentType = { it.kind }) { suggestion ->
                    AddFoodSuggestionRow(
                        suggestion = suggestion,
                        onClick = { onPick(suggestion) },
                        onLongClick = { onReview(suggestion) },
                        planningMode = planningMode,
                        onPlanSuggestion = onPlanSuggestion,
                    )
                }
                if (suggestions.isEmpty()) {
                    item(key = "empty") { AddFoodEmptyLine(R.string.add_food_quick_relog_empty) }
                }
            }
        }
    }
}

/** What an armed scroll reading asks of the sheet. See [sheetGrowAction]. */
internal enum class SheetGrow { EXPAND, COLLAPSE, KEEP }

/**
 * The grow-on-scroll decision, extracted from the effect that applies it so it
 * can be reasoned about and tested without a sheet.
 *
 * [KEEP] is the case worth naming. The inputs change for two different reasons:
 * the user scrolling, and the list underneath them growing or shrinking as
 * legs of the search land, get switched off, or get switched on. Only the
 * first is a request. A list that has just dropped below the expand threshold
 * because its rows went away says nothing about whether the user still wants
 * the sheet they opened — collapsing there is how toggling the packaged-product
 * opt-in used to shut a sheet the user had scrolled up.
 */
internal fun sheetGrowAction(scrolled: Boolean, worthExpanding: Boolean): SheetGrow = when {
    scrolled && worthExpanding -> SheetGrow.EXPAND
    // Back at the top under their own finger: they are done with the list.
    !scrolled -> SheetGrow.COLLAPSE
    else -> SheetGrow.KEEP
}

/** The one-line "nothing here" note, in either of the list's two states. */
@Composable
private fun AddFoodEmptyLine(@StringRes textRes: Int) {
    Text(
        stringResource(textRes),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
    )
}

/**
 * Divider between two sources in the one list. A label, not a tab: the rows
 * below it are already on screen, so it says where they came from instead of
 * asking the user to pick a source before seeing anything.
 */
@Composable
private fun AddFoodSectionLabel(
    group: AddFoodGroup,
    pending: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        // The clearance the track needs doubles as the label's own breathing
        // room, which is why the row itself no longer adds any: the heading
        // ends up a couple of dp taller than the padded text it replaces
        // rather than the full inset taller. The weighted label keeps a
        // narrow trailing element from shifting the heading.
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            // The track is inset from the text on every label, pending or not,
            // so the heading sits at the same place and the sections keep the
            // same height whether or not something is circling them.
            Modifier
                .weight(1f)
                .pendingOrbit(pending)
                .padding(horizontal = ORBIT_INSET_X, vertical = ORBIT_INSET_Y),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(group.labelRes),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                textAlign = TextAlign.Center,
            )
        }
        trailing?.invoke()
    }
}

/**
 * The clearance the orbit runs in, kept free of the label's text. Tight enough
 * that the track reads as belonging to these words rather than as a ring drawn
 * round the middle of the sheet — the ends clear the caps by about the width of
 * the comet itself, which is all the separation a moving stroke needs.
 */
private val ORBIT_INSET_X = 8.dp
private val ORBIT_INSET_Y = 3.dp

/** How thick the comet's head is, and how much of the lap it trails behind it. */
private val ORBIT_STROKE = 2.dp
private const val ORBIT_TAIL_FRACTION = 0.3f

/** Segments the tail is drawn in. Enough that the fade reads as a gradient. */
private const val ORBIT_TAIL_STEPS = 16

/** One lap, in milliseconds. Slow enough to read as travel, not as a spin. */
private const val ORBIT_LAP_MILLIS = 1900

/**
 * How long the comet takes to arrive and to leave. It never blinks on or off:
 * a wait that begins and ends on a single frame reads as a glitch beside the
 * label, and the fade-out is the longer of the two so the results landing
 * underneath are what the eye goes to, not the signal clearing away.
 */
private const val ORBIT_FADE_IN_MILLIS = 240
private const val ORBIT_FADE_OUT_MILLIS = 420

/**
 * The only "still working" signal in the sheet: a comet lapping the heading of
 * the section whose rows are still arriving. A spinner in a band of its own
 * cost 64dp of empty sheet and read as a blocking wait, and dots parked beside
 * the label only ever held one edge of it; circling the words ties the wait to
 * the thing being waited on from every side, leaves the rows already found as
 * the loudest thing on screen, and simply fades out when the network settles.
 */
@Composable
private fun Modifier.pendingOrbit(pending: Boolean): Modifier {
    // Driven from a standing zero rather than from whatever `pending` says on
    // the first frame, so the comet fades in even when the label enters the
    // list already waiting — which is the common case, since the databases
    // heading appears with its search already in flight.
    val fadeIn = remember { Animatable(0f) }
    LaunchedEffect(pending) {
        fadeIn.animateTo(
            targetValue = if (pending) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (pending) ORBIT_FADE_IN_MILLIS else ORBIT_FADE_OUT_MILLIS,
                easing = LinearEasing,
            ),
        )
    }
    val alpha = fadeIn.value
    // Faded fully out is the same as never having been there: dropping the
    // whole branch takes the infinite transition with it, so a settled sheet is
    // not invalidating a draw every frame for something nobody can see.
    if (alpha <= 0f) return this
    val transition = rememberInfiniteTransition(label = "pendingOrbit")
    // Linear, and restarting rather than reversing: an eased or reversing lap
    // reads as something rocking back and forth, not as something going round.
    val lap by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ORBIT_LAP_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pendingOrbitLap",
    )
    val color = AppColors.Calorie
    val track = remember { Path() }
    val arc = remember { Path() }
    val measure = remember { PathMeasure() }
    return drawBehind {
        val stroke = ORBIT_STROKE.toPx()
        val bounds = size.toRect().deflate(stroke / 2f)
        if (bounds.width <= 0f || bounds.height <= 0f) return@drawBehind
        track.rewind()
        // Fully rounded ends, so the corners never catch the eye as corners and
        // the comet keeps an even speed the whole way round the words.
        track.addRoundRect(RoundRect(bounds, CornerRadius(bounds.height / 2f)))
        measure.setPath(track, forceClosed = true)
        val length = measure.length
        if (length <= 0f) return@drawBehind
        val head = lap * length
        val tail = length * ORBIT_TAIL_FRACTION
        // Drawn as a chain of short arcs rather than one stroke: each carries
        // its own alpha and width, so the streak thins and fades out behind the
        // head instead of ending on a hard edge.
        repeat(ORBIT_TAIL_STEPS) { step ->
            val fade = 1f - step.toFloat() / ORBIT_TAIL_STEPS
            drawOrbitArc(
                measure = measure,
                arc = arc,
                from = head - tail * (step + 1) / ORBIT_TAIL_STEPS,
                to = head - tail * step / ORBIT_TAIL_STEPS,
                length = length,
                color = color.copy(alpha = fade * fade * alpha),
                width = stroke * (0.4f + 0.6f * fade),
            )
        }
    }
}

/**
 * One arc of the comet, wrapped onto the closed track: a tail that has run off
 * the start of the path is drawn in two pieces rather than clamped, which is
 * what keeps the streak whole as the head crosses the seam.
 */
private fun DrawScope.drawOrbitArc(
    measure: PathMeasure,
    arc: Path,
    from: Float,
    to: Float,
    length: Float,
    color: Color,
    width: Float,
) {
    val start = ((from % length) + length) % length
    val stop = start + (to - from)
    if (stop <= length) {
        strokeOrbitSegment(measure, arc, start, stop, color, width)
    } else {
        strokeOrbitSegment(measure, arc, start, length, color, width)
        strokeOrbitSegment(measure, arc, 0f, stop - length, color, width)
    }
}

private fun DrawScope.strokeOrbitSegment(
    measure: PathMeasure,
    arc: Path,
    start: Float,
    stop: Float,
    color: Color,
    width: Float,
) {
    if (stop <= start) return
    arc.rewind()
    measure.getSegment(start, stop, arc, startWithMoveTo = true)
    drawPath(arc, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/**
 * One suggestion. Tap logs it, long-press opens the review sheet — the same
 * contract the hub re-log chips have always had, so the gesture the user
 * already knows keeps working now that the chips have a list beside them.
 */
@Composable
private fun AddFoodSuggestionRow(
    suggestion: FoodSuggestion,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** Meal planning mode: saved rows carry a Plan-for-a-day action. */
    planningMode: Boolean = false,
    onPlanSuggestion: ((FoodSuggestion) -> Unit)? = null,
) {
    val isDark = isDarkTheme()
    val shape = RoundedCornerShape(AppRadii.Container)
    val fill = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val border = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    val reviewLabel = stringResource(R.string.sheet_review_food)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(0.5.dp, border, shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = reviewLabel,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SuggestionThumbnail(suggestion)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                suggestion.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    energyText(suggestionCalories(suggestion)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Calorie,
                )
                Text(
                    suggestionSubtitle(suggestion),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            suggestionMacros(suggestion)?.let { macros ->
                Text(
                    macros,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Faint),
                    maxLines = 1,
                )
            }
        }
        // Plan action (meal planning mode): saved meals/recipes only — a
        // database hit is not yet anything the app remembers to plan with.
        if (planningMode && onPlanSuggestion != null &&
            suggestion !is FoodSuggestion.DatabaseHit
        ) {
            IconButton(onClick = { onPlanSuggestion(suggestion) }) {
                Icon(
                    Icons.Outlined.EventAvailable,
                    contentDescription = stringResource(R.string.cd_plan_suggestion),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            // Mirrors the re-log chip's affordance, so "tap adds it" reads the
            // same in the list as it does in the chip row above.
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = AppColors.Calorie,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Square emoji/glyph tile, matching the Saved Meals row thumbnail so one food
 * looks the same wherever it is listed. Database hits carry no emoji of their
 * own, so they show the search glyph rather than a generic plate.
 */
@Composable
private fun SuggestionThumbnail(suggestion: FoodSuggestion) {
    val shape = RoundedCornerShape(AppRadii.Field)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .border(1.dp, AppColors.Calorie.copy(alpha = 0.15f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val emoji = suggestionEmoji(suggestion)
        if (emoji != null) {
            Text(emoji, fontSize = 22.sp)
        } else {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = AppColors.Calorie,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * "More options" as a pill beside the capture tools, opening the entries that
 * are not part of "say what you ate": manual entry, copying a past day,
 * non-food logging and the analysis queue.
 *
 * A dropdown rather than an inline panel on purpose — a menu is an overlay, so
 * opening it cannot change the sheet's height. The inline version had to be
 * animated out whenever the results pane grew, and any height it contributed
 * fought the pane's own animation.
 */
@Composable
private fun AddFoodMoreButton(
    onNote: () -> Unit,
    onSavedMeals: () -> Unit,
    onManual: () -> Unit,
    onCopyFromDay: () -> Unit,
    onManualActive: () -> Unit,
    onQueue: () -> Unit,
    onGrounded: () -> Unit,
    queuePendingCount: Int,
    aiFeaturesEnabled: Boolean,
    groundedEnabled: Boolean,
    /** Meal planning mode: opens the week planner from the menu. */
    planningMode: Boolean = false,
    onPlanWeek: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    // DropdownMenu anchors to its parent's start edge, which left the menu
    // hanging off to the right of a third-width pill. Measuring the anchor lets
    // us shift it back by half the difference so it sits centred on the pill.
    val density = LocalDensity.current
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val menuOffset = DpOffset(x = (anchorWidth - ADD_FOOD_MENU_WIDTH) / 2, y = 4.dp)
    Box(modifier.onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }) {
        AddFoodToolButton(
            icon = Icons.Filled.MoreHoriz,
            label = stringResource(R.string.home_view_more),
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
        )
        SheetGlassDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            menuWidth = ADD_FOOD_MENU_WIDTH,
            offset = menuOffset,
        ) {
            // The note sheet is not just another way to type a food: it stages
            // a prompt (optional label photo, grams) and offers the 7-day
            // prompt history to re-run (Codeberg #53), none of which the search
            // field's one-tap Analyze can do.
            if (aiFeaturesEnabled) {
                SheetGlassDropdownMenuItem(
                    label = stringResource(R.string.add_food_hero_note),
                    leadingIcon = Icons.Filled.Edit,
                ) { open = false; onNote() }
            }
            // The inline tabs list the rows; the full sheet is still the only
            // way to search them, re-sort them (Codeberg #76), edit a favorite
            // (Codeberg #66) or create a recipe.
            SheetGlassDropdownMenuItem(
                label = stringResource(R.string.home_menu_saved_meals),
                leadingIcon = Icons.Filled.Bookmarks,
            ) { open = false; onSavedMeals() }
            if (planningMode) {
                SheetGlassDropdownMenuItem(
                    label = stringResource(R.string.plan_week_title),
                    leadingIcon = Icons.Outlined.EventAvailable,
                ) { open = false; onPlanWeek() }
            }
            SheetGlassDropdownMenuItem(
                label = stringResource(R.string.home_menu_manual_entry),
                leadingIcon = Icons.Filled.DriveFileRenameOutline,
            ) { open = false; onManual() }
            SheetGlassDropdownMenuItem(
                label = stringResource(R.string.home_menu_copy_from_day),
                leadingIcon = Icons.Filled.CalendarMonth,
            ) { open = false; onCopyFromDay() }
            SheetGlassDropdownMenuItem(
                label = stringResource(R.string.home_menu_manual_active),
                leadingIcon = Icons.AutoMirrored.Filled.DirectionsRun,
            ) { open = false; onManualActive() }
            if (aiFeaturesEnabled) {
                SheetGlassDropdownMenuItem(
                    label = if (queuePendingCount > 0) {
                        stringResource(R.string.analysis_queue_tile_label_count, queuePendingCount)
                    } else {
                        stringResource(R.string.analysis_queue_tile_label)
                    },
                    leadingIcon = Icons.Filled.Schedule,
                ) { open = false; onQueue() }
            }
            if (groundedEnabled) {
                SheetGlassDropdownMenuItem(
                    label = stringResource(R.string.home_menu_grounded),
                    leadingIcon = Icons.Filled.Science,
                ) { open = false; onGrounded() }
            }
        }
    }
}

private fun suggestionEmoji(suggestion: FoodSuggestion): String? = when (suggestion) {
    is FoodSuggestion.SavedFood -> suggestion.template.emoji ?: "🍽"
    is FoodSuggestion.SavedRecipe -> suggestion.recipe.emoji ?: "🍲"
    is FoodSuggestion.DatabaseHit -> null
}

internal fun suggestionCalories(suggestion: FoodSuggestion): Int = when (suggestion) {
    is FoodSuggestion.SavedFood -> suggestion.template.calories
    is FoodSuggestion.SavedRecipe -> suggestion.recipe.totalCalories
    is FoodSuggestion.DatabaseHit -> suggestion.result.displayCalories
}

@Composable
private fun suggestionSubtitle(suggestion: FoodSuggestion): String = when (suggestion) {
    is FoodSuggestion.SavedFood -> when (suggestion.kind) {
        SuggestionKind.FAVORITE -> stringResource(R.string.home_swipe_favorite)
        SuggestionKind.FREQUENT -> stringResource(R.string.saved_meals_tab_frequent)
        else -> stringResource(R.string.saved_meals_sort_recent)
    }
    is FoodSuggestion.SavedRecipe -> stringResource(R.string.saved_meals_tab_recipes)
    is FoodSuggestion.DatabaseHit -> resultSourceSubtitle(suggestion.result)
}

private fun suggestionMacros(suggestion: FoodSuggestion): String? = when (suggestion) {
    is FoodSuggestion.DatabaseHit -> resultMacroLine(suggestion.result)
    else -> null
}

/*
 * Row text helpers, moved here verbatim when the standalone "Search food" sheet
 * folded into the unified Add Food suggestion list. Same package, so
 * FoodDatabaseSearchMacroLineTest keeps compiling untouched.
 */

@Composable
internal fun resultSourceSubtitle(result: DatabaseSearchResult): String {
    val source = when (result.sourceKind) {
        NutrientSourceKind.OPEN_FOOD_FACTS -> stringResource(R.string.food_search_source_off)
        NutrientSourceKind.USDA -> stringResource(R.string.food_search_source_usda)
        NutrientSourceKind.SWISS -> stringResource(R.string.food_search_source_swiss)
        else -> result.sourceKind.name
    }
    return listOfNotNull(
        result.brand,
        source,
        result.lang?.takeIf { it != "en" }?.uppercase(Locale.ROOT),
    ).joinToString(" · ")
}

internal fun resultMacroLine(result: DatabaseSearchResult): String {
    fun v(x: Double?) = x?.let { it.roundToInt().toString() } ?: "—"
    val macros = listOf(
        v(result.proteinPerServing),
        v(result.carbsPerServing),
        v(result.fatPerServing),
    )
    // Always a 3-element list: OFF hits routinely lack one or two macros
    // (incompleteEnergy), and a fixed `macros[2]` on a shorter list crashed
    // the sheet with IndexOutOfBoundsException while rendering (Codeberg #26).
    return if (macros.all { it == "—" }) "P · C · F" else "P ${macros[0]} · C ${macros[1]} · F ${macros[2]}"
}

/** Rows below which expanding the sheet on scroll is more disruptive than useful. */
private const val EXPAND_MIN_ROWS = 8

/** Width of the More options menu; wide enough for its longest localized row. */
private val ADD_FOOD_MENU_WIDTH = 240.dp
