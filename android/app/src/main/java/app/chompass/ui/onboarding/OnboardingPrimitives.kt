package app.chompass.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.components.NumericWheelPicker
import app.chompass.ui.components.SplitDecimalWheelPicker
import app.chompass.ui.theme.AppColors
import androidx.compose.material3.Icon

@Composable
internal fun StepHeader(title: String, subtitle: String? = null) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * iOS selectionCard parity — rounded card with leading icon, title, optional
 * subtitle, and a trailing checkmark.circle.fill / circle. Selected state adds
 * a 2pt onBackground stroke; matches AppColors.appCard background.
 */
@Composable
internal fun SelectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = if (selected) AppColors.Calorie else MaterialTheme.colorScheme.onBackground
    val selectedBorder = if (selected) {
        Modifier.border(BorderStroke(1.4.dp, AppColors.Calorie.copy(alpha = 0.55f)), RoundedCornerShape(20.dp))
    } else {
        Modifier
    }
    FudGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(selectedBorder)
            .clickable(onClick = onClick),
        cornerRadius = 20.dp,
        padding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FudIconBubble(icon = icon, size = 40.dp, iconSize = 21.dp, tint = accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) AppColors.Calorie else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun ChoiceRow(label: String, subtitle: String? = null, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        Brush.linearGradient(listOf(AppColors.CalorieStart.copy(alpha = 0.18f), AppColors.CalorieEnd.copy(alpha = 0.10f)))
    } else {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) AppColors.Calorie else Color.Transparent)
                    .padding(3.dp)
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.95f))
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ToggleCard(label: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    FudGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!enabled) },
        cornerRadius = 20.dp,
        padding = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
internal fun WheeledColumn(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
internal fun OnboardingWeightWheel(kg: Double, weightMetric: Boolean, onWeightChange: (Double) -> Unit) {
    if (weightMetric) {
        SplitDecimalWheelPicker(
            value = kg.coerceIn(30.0, 250.0),
            onValueChange = onWeightChange,
            min = 30,
            max = 250,
            unit = stringResource(R.string.unit_kg)
        )
    } else {
        val lbs = (kg * 2.20462).coerceIn(60.0, 500.0)
        SplitDecimalWheelPicker(
            value = lbs,
            onValueChange = { newLbs -> onWeightChange(newLbs / 2.20462) },
            min = 60,
            max = 500,
            unit = stringResource(R.string.unit_lbs)
        )
    }
}

@Composable
internal fun HeightWeightMetricWheels(
    cm: Int,
    kg: Double,
    weightMetric: Boolean,
    onHeightChange: (Int) -> Unit,
    onWeightChange: (Double) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        WheeledColumn(label = stringResource(R.string.onboarding_height), modifier = Modifier.weight(0.9f)) {
            NumericWheelPicker(value = cm, onValueChange = onHeightChange, min = 100, max = 250, unit = stringResource(R.string.unit_cm))
        }
        // Weight column gets extra width — it has int + "." + tenths + unit (4 cells)
        // vs the height column's int + unit (2 cells), so 1f / 1f cramped the digits.
        WheeledColumn(label = stringResource(R.string.onboarding_weight), modifier = Modifier.weight(1.4f)) {
            OnboardingWeightWheel(kg = kg, weightMetric = weightMetric, onWeightChange = onWeightChange)
        }
    }
}

@Composable
internal fun HeightWeightImperialWheels(
    cm: Int,
    kg: Double,
    weightMetric: Boolean,
    onHeightChange: (Int) -> Unit,
    onWeightChange: (Double) -> Unit
) {
    // Round to nearest inch both ways, or 5'7" (170 cm) snaps back to 5'6".
    val totalInches = Math.round(cm / 2.54).toInt().coerceIn(36, 96)
    val feet = (totalInches / 12).coerceIn(3, 8)
    val inches = (totalInches % 12).coerceIn(0, 11)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        WheeledColumn(label = stringResource(R.string.onboarding_feet), modifier = Modifier.weight(0.7f)) {
            NumericWheelPicker(
                value = feet,
                onValueChange = { newFt ->
                    val newCm = Math.round((newFt * 12 + inches) * 2.54).toInt()
                    onHeightChange(newCm)
                },
                min = 3,
                max = 8,
                unit = stringResource(R.string.unit_ft)
            )
        }
        WheeledColumn(label = stringResource(R.string.onboarding_inches), modifier = Modifier.weight(0.7f)) {
            NumericWheelPicker(
                value = inches,
                onValueChange = { newIn ->
                    val newCm = Math.round((feet * 12 + newIn) * 2.54).toInt()
                    onHeightChange(newCm)
                },
                min = 0,
                max = 11,
                unit = stringResource(R.string.unit_in)
            )
        }
        // Weight column needs ~50% of the row — three-digit lbs (e.g. 152) plus
        // "." + tenths + "lbs" can't fit when all three columns share width 1:1:1.
        WheeledColumn(label = stringResource(R.string.onboarding_weight), modifier = Modifier.weight(1.6f)) {
            OnboardingWeightWheel(kg = kg, weightMetric = weightMetric, onWeightChange = onWeightChange)
        }
    }
}
