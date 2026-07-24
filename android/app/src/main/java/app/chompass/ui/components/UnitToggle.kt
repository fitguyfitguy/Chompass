package app.chompass.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Two-option segmented control, e.g. metric/imperial unit switches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitToggle(
    leftLabel: String,
    rightLabel: String,
    isLeft: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = isLeft,
            onClick = { if (!isLeft) onSelect(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(leftLabel, style = MaterialTheme.typography.titleSmall) },
        )
        SegmentedButton(
            selected = !isLeft,
            onClick = { if (isLeft) onSelect(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(rightLabel, style = MaterialTheme.typography.titleSmall) },
        )
    }
}
