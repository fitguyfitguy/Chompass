package app.chompass.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import app.chompass.ui.components.kcalText
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
            modifier = Modifier.onPreviewKeyEvent { event ->
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
    /** Resets the grow-on-scroll state when the list underneath changes. */
    listIdentity: Any,
    /** Non-null lets scrolling expand the sheet itself. */
    sheetState: SheetState? = null,
    modifier: Modifier = Modifier,
    onPick: (FoodSuggestion) -> Unit,
    onReview: (FoodSuggestion) -> Unit,
) {
    val sections = remember(suggestions, searching) {
        if (searching) groupSuggestions(suggestions) else emptyList()
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
    // Only the user's own scrolling moves the sheet. Switching tab or starting a
    // query swaps the list underneath and resets its scroll to the top, which
    // used to read as "scrolled back up" and collapsed a sheet the user had
    // deliberately expanded — so a changed identity re-arms the gesture instead
    // of acting on it.
    val lastIdentity = remember { mutableStateOf(listIdentity) }
    LaunchedEffect(scrolled, worthExpanding, sheetState, listIdentity) {
        if (sheetState == null) return@LaunchedEffect
        val identityChanged = lastIdentity.value != listIdentity
        lastIdentity.value = listIdentity
        if (identityChanged) return@LaunchedEffect
        runCatching {
            if (scrolled && worthExpanding) sheetState.expand() else sheetState.partialExpand()
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (searching) {
                sections.forEach { section ->
                    item(key = "section:${section.group.name}") {
                        AddFoodSectionLabel(
                            section.group,
                            pending = networkPending &&
                                section.group == AddFoodGroup.DATABASES,
                        )
                    }
                    items(section.rows, key = { it.key }) { suggestion ->
                        AddFoodSuggestionRow(
                            suggestion = suggestion,
                            onClick = { onPick(suggestion) },
                            onLongClick = { onReview(suggestion) },
                        )
                    }
                }
                // Nothing back from the databases yet: the label lands ahead of
                // its rows so the wait has somewhere to show, and the rows fill
                // in underneath it instead of pushing a spinner out of the way.
                if (networkPending && sections.none { it.group == AddFoodGroup.DATABASES }) {
                    item(key = "section:pending") {
                        AddFoodSectionLabel(AddFoodGroup.DATABASES, pending = true)
                    }
                }
            } else {
                items(suggestions, key = { it.key }) { suggestion ->
                    AddFoodSuggestionRow(
                        suggestion = suggestion,
                        onClick = { onPick(suggestion) },
                        onLongClick = { onReview(suggestion) },
                    )
                }
            }
            if (suggestions.isEmpty() && !networkPending) {
                item {
                    Text(
                        stringResource(
                            if (searching) {
                                R.string.saved_meals_no_match
                            } else {
                                R.string.add_food_quick_relog_empty
                            },
                        ),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Divider between two sources in the one list. A label, not a tab: the rows
 * below it are already on screen, so it says where they came from instead of
 * asking the user to pick a source before seeing anything.
 */
@Composable
private fun AddFoodSectionLabel(group: AddFoodGroup, pending: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The dots get a mirrored slot on the other side of the label, so the
        // label sits at the same x whether or not they are showing: the text
        // must not slide sideways the moment the network settles.
        if (pending) Spacer(Modifier.width(PENDING_DOTS_SLOT))
        Text(
            stringResource(group.labelRes),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            textAlign = TextAlign.Center,
        )
        if (pending) {
            Box(Modifier.width(PENDING_DOTS_SLOT), contentAlignment = Alignment.Center) {
                PendingDots()
            }
        }
    }
}

/** Three 5dp dots plus the gap that separates them from the label. */
private val PENDING_DOTS_SLOT = 27.dp

/**
 * The only "still working" signal in the sheet: three dots breathing in
 * sequence beside the section whose rows are still arriving. A spinner in a
 * band of its own cost 64dp of empty sheet and read as a blocking wait; this
 * sits inside the label, so the rows already found stay the loudest thing on
 * screen and the dots simply stop when the network settles.
 */
@Composable
private fun PendingDots() {
    val transition = rememberInfiniteTransition(label = "pendingDots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            // One driver per dot, read as both fade and scale: a dot that only
            // fades reads as a flicker, and one that only grows reads as a
            // wobble. Together they breathe.
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    // Eased rather than linear, so each dot lingers at the ends
                    // of its travel instead of sawtoothing between them, and
                    // offset by a third of the cycle so the swell moves left to
                    // right rather than the three pulsing as one blob.
                    animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 170),
                ),
                label = "pendingDot$index",
            )
            Box(
                Modifier
                    .size(5.dp)
                    .graphicsLayer {
                        alpha = 0.22f + 0.68f * phase
                        val scale = 0.62f + 0.38f * phase
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(AppColors.Calorie, CircleShape),
            )
        }
    }
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
                    kcalText(suggestionCalories(suggestion)),
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
        // Mirrors the re-log chip's affordance, so "tap adds it" reads the same
        // in the list as it does in the chip row above.
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = AppColors.Calorie,
            modifier = Modifier.size(18.dp),
        )
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

private fun suggestionCalories(suggestion: FoodSuggestion): Int = when (suggestion) {
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
