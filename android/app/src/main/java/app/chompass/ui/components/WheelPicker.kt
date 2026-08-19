package app.chompass.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.ServingUnitOption
import app.chompass.models.LocaleFormat
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.YearMonth
import app.chompass.models.UnitFormat
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity

private val ITEM_HEIGHT = 44.dp
private const val VISIBLE_ITEMS = 5
private val ROW_HEIGHT = ITEM_HEIGHT * VISIBLE_ITEMS
/** Unit wheels and other tiny ranges: kill ballistic fling so a flick is one row. */
private const val SHORT_LIST_ITEM_COUNT = 5

/**
 * Scrolling wheel picker. Items snap to the center row. The highlighted row
 * sits on a Material3 secondaryContainer tonal band (same selection pattern as
 * BottomNavBar's indicator); rows away from center fade to onSurfaceVariant.
 */
@Composable
fun <T> WheelPicker(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String = { it.toString() },
    /**
     * When false, the wheel does NOT paint its own selected-row capsule. Useful
     * when several WheelPickers sit in a Row and the parent overlays a single
     * shared capsule spanning every column (matches iOS UIDatePicker).
     */
    showSelectionHighlight: Boolean = true
) {
    if (items.isEmpty()) return
    val initialIndex = items.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    // Always compose both flings so a list that grows past SHORT_LIST_ITEM_COUNT
    // (custom unit added) does not change the remember count.
    val defaultFling = rememberSnapFlingBehavior(lazyListState = listState)
    val shortDecay = remember { exponentialDecay<Float>(frictionMultiplier = 8f) }
    val shortSnap = remember { spring<Float>(stiffness = Spring.StiffnessMediumLow) }
    val shortProvider = remember(listState) { SnapLayoutInfoProvider(lazyListState = listState) }
    val shortFling = remember(shortProvider, shortDecay, shortSnap) {
        snapFlingBehavior(
            snapLayoutInfoProvider = shortProvider,
            decayAnimationSpec = shortDecay,
            snapAnimationSpec = shortSnap,
        )
    }
    val fling = if (items.size <= SHORT_LIST_ITEM_COUNT) shortFling else defaultFling

    val centerIndex by remember {
        derivedStateOf { listState.centeredIndex() ?: listState.firstVisibleItemIndex }
    }

    // rememberUpdatedState forwards the latest onSelect / selected into the
    // LaunchedEffect without restarting it. Without this, the effect captures
    // the first-composition closure and fires stale state when sibling wheels
    // (e.g. year + day in a date picker) move independently.
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val currentItems by rememberUpdatedState(items)

    // Commit only after the snap settles. Mid-scroll firstVisibleItemIndex is
    // the top visible row, not the capsule row — on a 3-item unit wheel the
    // last row never becomes first-visible (Codeberg #42).
    LaunchedEffect(listState) {
        snapshotFlow {
            val inProgress = listState.isScrollInProgress
            val idx = listState.centeredIndex()
            inProgress to idx
        }
            .distinctUntilChanged()
            .collect { (inProgress, idx) ->
                if (inProgress) return@collect
                val snapped = currentItems.getOrNull(idx ?: return@collect) ?: return@collect
                if (snapped != currentSelected) currentOnSelect(snapped)
            }
    }

    // Follow externally-changed selection (e.g. Reset to Auto-balance in the
    // goals picker): the list state is pinned to the first composition's index,
    // so a programmatic change would otherwise leave the wheel on a stale row.
    // Skipped while the user is dragging — the gesture wins, and the scroll
    // collector above re-syncs the parent state when it settles.
    LaunchedEffect(selected, items) {
        val targetIndex = items.indexOf(selected)
        if (targetIndex >= 0 &&
            listState.centeredIndex() != targetIndex &&
            !listState.isScrollInProgress
        ) {
            listState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier.height(ROW_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        // iOS UIPickerView paints a single rounded "capsule" tint behind the
        // selected row instead of two divider lines. Match that look.
        if (showSelectionHighlight) {
            WheelSelectionHighlight(Modifier.align(Alignment.Center))
        }

        LazyColumn(
            state = listState,
            flingBehavior = fling,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = ITEM_HEIGHT * (VISIBLE_ITEMS / 2)),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items.size) { index ->
                val item = items[index]
                val isSelected = index == centerIndex
                val alpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.6f,
                    label = "wheelAlpha"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "wheelTextColor"
                )
                Box(
                    modifier = Modifier
                        .height(ITEM_HEIGHT)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label(item),
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = if (isSelected) 24.sp else 20.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor.copy(alpha = alpha),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Triple-column iOS-style date picker (Month / Day / Year). Updates the caller
 * any time any wheel lands on a new value.
 */
@Composable
fun DateWheelPicker(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    minYear: Int = 1920,
    maxYear: Int = LocalDate.now().year,
    modifier: Modifier = Modifier
) {
    val months = remember { (1..12).toList() }
    val years = remember(minYear, maxYear) { (minYear..maxYear).toList().reversed() }
    val daysInMonth = remember(selected.year, selected.monthValue) {
        YearMonth.of(selected.year, selected.monthValue).lengthOfMonth()
    }
    val days = remember(daysInMonth) { (1..daysInMonth).toList() }

    // iOS DatePicker shows full month names (April, not Apr) — localized.
    val monthNames = remember {
        java.time.Month.values().map { m ->
            m.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale.getDefault())
                .replaceFirstChar { it.uppercase() }
        }
    }

    // iOS column order is Day | Month | Year (matches iOS UIDatePicker default).
    // The capsule highlight spans all three wheels — paint it on the parent Box
    // and tell each wheel to skip its own per-column highlight.
    Box(
        modifier = modifier.fillMaxWidth().height(ROW_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        WheelSelectionHighlight(Modifier.align(Alignment.Center))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelPicker(
                items = days,
                selected = selected.dayOfMonth.coerceAtMost(daysInMonth),
                onSelect = { d -> onSelect(LocalDate.of(selected.year, selected.monthValue, d)) },
                modifier = Modifier.weight(0.5f),
                showSelectionHighlight = false
            )
            WheelPicker(
                items = months,
                selected = selected.monthValue,
                onSelect = { m ->
                    val clampedDay = selected.dayOfMonth.coerceAtMost(YearMonth.of(selected.year, m).lengthOfMonth())
                    onSelect(LocalDate.of(selected.year, m, clampedDay))
                },
                label = { monthNames[it - 1] },
                modifier = Modifier.weight(1.2f),
                showSelectionHighlight = false
            )
            WheelPicker(
                items = years,
                selected = selected.year,
                onSelect = { y ->
                    val clampedDay = selected.dayOfMonth.coerceAtMost(YearMonth.of(y, selected.monthValue).lengthOfMonth())
                    onSelect(LocalDate.of(y, selected.monthValue, clampedDay))
                },
                modifier = Modifier.weight(0.7f),
                showSelectionHighlight = false
            )
        }
    }
}

@Composable
internal fun WheelSelectionHighlight(modifier: Modifier = Modifier) {
    // Material3 tonal indicator — same secondaryContainer band BottomNavBar
    // uses for its selected-tab pill, instead of a translucent glass capsule.
    val shape = RoundedCornerShape(AppRadii.Field)
    Box(
        modifier
            .fillMaxWidth()
            .height(ITEM_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
    )
}

/** Single-column wheel picker specialized for a numeric range with optional unit suffix. */
@Composable
fun NumericWheelPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    unit: String? = null,
    modifier: Modifier = Modifier,
    step: Int = 1
) {
    val items = remember(min, max, step) { (min..max step step).toList() }
    // Snap incoming value onto the stepped grid so the wheel always has a
    // matching item to highlight.
    val snapped = run {
        val coerced = value.coerceIn(min, max)
        val offset = coerced - min
        min + (offset / step) * step
    }
    val clamped = snapped
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WheelPicker(
            items = items,
            selected = clamped,
            onSelect = onValueChange,
            modifier = Modifier.weight(1f)
        )
        if (unit != null) {
            val compactUnit = unit.length <= 2
            Spacer(Modifier.width(if (compactUnit) 4.dp else 8.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.width(if (compactUnit) 30.dp else 48.dp).padding(start = 4.dp),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Imperial height picker — feet + inches dual wheel. Converts to/from total cm
 * externally so the ViewModel only ever stores one source of truth (cm).
 */
@Composable
fun FeetInchesWheelPicker(
    cm: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Use round-trip-safe math: round() for both directions so 5'7" -> 170cm -> 5'7" instead
    // of truncating to 5'6".
    val totalInches = UnitFormat.cmToInchesRounded(cm).coerceIn(36, 95) // 3'0" to 7'11"
    val feet = totalInches / 12
    val inches = totalInches % 12

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WheelPicker(
            items = (3..7).toList(),
            selected = feet,
            onSelect = { f ->
                val newTotal = f * 12 + inches
                onValueChange(UnitFormat.inchesToCmRounded(newTotal))
            },
            label = { stringResource(R.string.ft_label_format, it) },
            modifier = Modifier.weight(1f)
        )
        WheelPicker(
            items = (0..11).toList(),
            selected = inches,
            onSelect = { i ->
                val newTotal = feet * 12 + i
                onValueChange(UnitFormat.inchesToCmRounded(newTotal))
            },
            label = { stringResource(R.string.in_label_format, it) },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * iOS-style split decimal picker — integer wheel + tenths wheel + unit label.
 * e.g. 72 . 4 kg. Much nicer than a single 2000-row DecimalWheelPicker for weight.
 */
@Composable
fun SplitDecimalWheelPicker(
    value: Double,
    onValueChange: (Double) -> Unit,
    min: Int,
    max: Int,
    unit: String? = null,
    modifier: Modifier = Modifier,
    showSelectionHighlight: Boolean = true,
) {
    val clampedValue = value.coerceIn(min.toDouble(), max.toDouble())
    val intPart = clampedValue.toInt().coerceIn(min, max)
    val tenthsPart = ((clampedValue - intPart) * 10).toInt().coerceIn(0, 9)
    val ints = remember(min, max) { (min..max).toList() }
    val tenths = remember { (0..9).toList() }
    val decimalSeparator = remember { LocaleFormat.decimalSeparator() }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WheelPicker(
            items = ints,
            selected = intPart,
            onSelect = { newInt -> onValueChange(newInt + tenthsPart / 10.0) },
            modifier = Modifier.weight(1f),
            showSelectionHighlight = showSelectionHighlight,
        )
        Text(
            decimalSeparator.toString(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        WheelPicker(
            items = tenths,
            selected = tenthsPart,
            onSelect = { newTenth -> onValueChange(intPart + newTenth / 10.0) },
            modifier = Modifier.weight(0.6f),
            showSelectionHighlight = showSelectionHighlight,
        )
        if (unit != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.width(48.dp).padding(start = 4.dp)
            )
        }
    }
}

/** Decimal (one-digit-precision) wheel picker. Stores as Int*10 under the hood. */
@Composable
fun DecimalWheelPicker(
    value: Double,
    onValueChange: (Double) -> Unit,
    min: Double,
    max: Double,
    step: Double = 0.1,
    unit: String? = null,
    modifier: Modifier = Modifier
) {
    val scaled = remember(step) { (1.0 / step).toInt() }
    val items = remember(min, max, scaled) {
        val start = (min * scaled).toInt()
        val end = (max * scaled).toInt()
        (start..end).toList()
    }
    val currentScaled = (value * scaled).toInt().coerceIn(items.first(), items.last())
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WheelPicker(
            items = items,
            selected = currentScaled,
            onSelect = { onValueChange(it.toDouble() / scaled) },
            label = { String.format("%.1f", it.toDouble() / scaled) },
            modifier = Modifier.weight(1f)
        )
        if (unit != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.width(48.dp).padding(start = 4.dp)
            )
        }
    }
}

/**
 * Time wheel picker — single column with 15-minute steps, locale-aware 12h/24h format.
 * Used for meal time boundaries (breakfast/lunch/dinner/snack start times).
 */
@Composable
fun TimeWheelPicker(
    minutes: Int,
    onChange: (Int) -> Unit,
    is24Hour: Boolean = false,
    modifier: Modifier = Modifier
) {
    val items = remember { (0..1439 step 15).toList() }
    val formatter = remember(is24Hour) {
        java.time.format.DateTimeFormatter.ofPattern(
            if (is24Hour) "HH:mm" else "h:mm a",
            java.util.Locale.getDefault()
        )
    }
    val clampedMinutes = minutes.coerceIn(0, 1439)
    val snapped = ((clampedMinutes + 7) / 15) * 15
    val selected = snapped.coerceIn(0, 1439)

    WheelPicker(
        items = items,
        selected = selected,
        onSelect = onChange,
        label = { m ->
            val time = java.time.LocalTime.of(m / 60, m % 60)
            time.format(formatter)
        },
        modifier = modifier
    )
}

/**
 * Macro wheel picker — NumericWheelPicker with an accent color for the unit label
 * and selected row highlight. Used in 2x2 grid for Calories/Protein/Carbs/Fat.
 */
@Composable
fun MacroWheelPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    unit: String,
    accentColor: androidx.compose.ui.graphics.Color,
    step: Int = 1,
    modifier: Modifier = Modifier
) {
    val items = remember(min, max, step) { (min..max step step).toList() }
    val snapped = value.coerceIn(min, max)
    val clamped = (snapped - min) / step * step + min

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WheelPicker(
            items = items,
            selected = clamped,
            onSelect = onValueChange,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            unit,
            style = MaterialTheme.typography.titleMedium,
            color = accentColor.copy(alpha = 0.85f),
            modifier = Modifier.width(48.dp).padding(start = 4.dp)
        )
    }
}

/**
 * Expandable macro picker — shows a summary row with label, current value, and unit.
 * On tap, expands to show a full-width wheel picker. The summary row already
 * names the nutrient, so the expanded state does not reprint the title.
 */
@Composable
fun ExpandableMacroPicker(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    unit: String,
    accentColor: androidx.compose.ui.graphics.Color,
    step: Int = 1,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(min, max, step) { (min..max step step).toList() }
    val snapped = value.coerceIn(min, max)
    val clamped = (snapped - min) / step * step + min

    Column(modifier = modifier.fillMaxWidth()) {
        // Summary row - tap to expand/collapse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .clickable { onExpandChange(!expanded) }
                .background(Color.Transparent, RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label with accent color indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.weight(1f))
            // Current value + unit
            Text(
                "$value $unit",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
            Spacer(Modifier.width(8.dp))
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = spring(dampingRatio = 0.75f)
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .graphicsLayer { rotationZ = rotation }
            )
        }

        // Expanded wheel picker
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.75f)),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.75f))
        ) {
            WheelPicker(
                items = items,
                selected = clamped,
                onSelect = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

/**
 * Always-visible unit wheel (g, serving, slice, …). No summary chrome — the
 * serving card already names Quantity. Pass [showSelectionHighlight] false
 * when a parent paints one capsule across quantity + unit.
 */
@Composable
fun UnitWheelPicker(
    options: List<ServingUnitOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    showSelectionHighlight: Boolean = true,
) {
    val pickerOptions = remember(options) { ServingUnitOption.pickerOptions(options) }
    val selectedOption = remember(selectedId, pickerOptions) {
        ServingUnitOption.optionMatching(selectedId, options)
    }
    val servingLabel = stringResource(R.string.unit_serving)
    val servingPluralLabel = stringResource(R.string.unit_serving_plural)
    WheelPicker(
        items = pickerOptions,
        selected = selectedOption,
        onSelect = { onSelect(it.id) },
        label = { it.displayUnit(null, servingLabel, servingPluralLabel) },
        modifier = modifier,
        showSelectionHighlight = showSelectionHighlight,
    )
}

/**
 * Split integer picker — thousands wheel + remainder wheel.
 * For large integer ranges like calories (0-5000). Much faster than single 5000-row wheel.
 */
@Composable
fun SplitIntegerWheelPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    max: Int = 5000,
    unit: String? = null,
    modifier: Modifier = Modifier
) {
    val thousands = value / 1000
    val remainder = value % 1000
    val maxThousands = max / 1000
    val thousandItems = remember(maxThousands) { (0..maxThousands).toList() }
    val remainderItems = remember { (0..999).toList() }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WheelPicker(
            items = thousandItems,
            selected = thousands,
            onSelect = { newThousand -> onValueChange(newThousand * 1000 + remainder) },
            label = { "${it}K" },
            modifier = Modifier.weight(0.5f)
        )
        Text(
            ",",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        WheelPicker(
            items = remainderItems,
            selected = remainder,
            onSelect = { newRemainder -> onValueChange(thousands * 1000 + newRemainder) },
            modifier = Modifier.weight(0.7f)
        )
        if (unit != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.width(48.dp).padding(start = 4.dp)
            )
        }
    }
}
