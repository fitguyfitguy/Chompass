package app.chompass.ui.progress

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.BuildConfig
import app.chompass.R
import app.chompass.ui.components.AnimatedNumberText
import app.chompass.ui.components.ChompassSurface
import app.chompass.ui.components.pressScale

/**
 * uiautomator reports every Compose FilterChip as selected="false". Debug
 * builds append the real state to the description so device passes can assert
 * it from a dump; release keeps the normal announced label.
 */
internal fun Modifier.debugChipState(label: String, selected: Boolean): Modifier =
    if (BuildConfig.DEBUG) semantics { contentDescription = "$label, selected=$selected" }
    else this

/**
 * Chip semantics for labels that are symbols or abbreviations: the announced
 * name stays the full series name, while debug builds append the selected
 * state for uiautomator (Compose FilterChip always dumps selected="false").
 */
internal fun Modifier.chipAccessibility(label: String, selected: Boolean): Modifier =
    if (BuildConfig.DEBUG) semantics { contentDescription = "$label, selected=$selected" }
    else semantics { contentDescription = label }

/** Compact `+` beside a measurement card title: opens that site's value wheel. */
@Composable
internal fun MeasurementAddButton(siteLabel: String, onClick: () -> Unit) {
    val description = stringResource(R.string.progress_log_measurement_format, siteLabel)
    val addInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .pressScale(addInteraction)
            .clickable(interactionSource = addInteraction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.AddCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
internal fun TimeRangePicker(selected: TimeRange, onSelect: (TimeRange) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimeRange.entries.forEach { range ->
            val labelText = stringResource(range.labelRes)
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                // Single line, sized to fit the 6-chip row at max font scale
                // (PLAN_UI_STRING_FIT: "Alle" wrapped to "All/e" at 1.3×).
                label = {
                    Text(
                        labelText,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        fontSize = 12.sp,
                        // Center within the weight(1f) chip — the label slot
                        // alone wrap-sizes and sits leading-biased (finding M2).
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .debugChipState(labelText, range == selected),
            )
        }
    }
}

@Composable
internal fun CardSection(content: @Composable () -> Unit) {
    ChompassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        padding = 16.dp,
    ) { content() }
}

@Composable
internal fun StatBadgeRow(items: List<Pair<String, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, value) ->
            StatBadge(
                label = label,
                value = value,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun StatBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AnimatedNumberText(
            value = value,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp)
        )
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 0.5.sp)
        )
    }
}

/**
 * Collapse affordance for Progress section headers (audit M11): the whole
 * header row toggles, and TalkBack reads the expanded/collapsed state (same
 * state strings as the Add Food trackers pill).
 */
@Composable
internal fun Modifier.progressCollapseHeader(expanded: Boolean, onToggle: () -> Unit): Modifier {
    val expandedLabel = stringResource(R.string.cd_expanded)
    val collapsedLabel = stringResource(R.string.cd_collapsed)
    return this
        .clickable(onClick = onToggle)
        .semantics(mergeDescendants = true) {
            stateDescription = if (expanded) expandedLabel else collapsedLabel
        }
}

@Composable
internal fun CollapseChevron(expanded: Boolean) {
    Icon(
        if (expanded) Icons.Filled.KeyboardArrowDown
        else Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}
