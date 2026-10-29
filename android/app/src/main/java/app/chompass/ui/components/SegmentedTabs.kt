package app.chompass.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.chompass.ui.theme.AppMotion

/** Single-choice segmented control. Styling matches [UnitToggle]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChompassSegmentedTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            // M3 SegmentedButton snaps its colors on selection; animate the
            // active↔inactive container/content/border transition on the
            // spatial spring instead (Morphe selection idiom). Label text
            // stays static — only the chrome moves.
            val defaults = SegmentedButtonDefaults.colors()
            val container by animateColorAsState(
                targetValue = if (isSelected) defaults.activeContainerColor else defaults.inactiveContainerColor,
                animationSpec = AppMotion.spatialSpring(),
                label = "segmentedContainer",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) defaults.activeContentColor else defaults.inactiveContentColor,
                animationSpec = AppMotion.spatialSpring(),
                label = "segmentedContent",
            )
            val border by animateColorAsState(
                targetValue = if (isSelected) defaults.activeBorderColor else defaults.inactiveBorderColor,
                animationSpec = AppMotion.spatialSpring(),
                label = "segmentedBorder",
            )
            SegmentedButton(
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = defaults.copy(
                    activeContainerColor = container,
                    inactiveContainerColor = container,
                    activeContentColor = content,
                    inactiveContentColor = content,
                    activeBorderColor = border,
                    inactiveBorderColor = border,
                ),
                label = {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
