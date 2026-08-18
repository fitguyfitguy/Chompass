package app.chompass.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppColors

enum class BodyMetric { WEIGHT, BODY_FAT }

@Composable
internal fun TimeRangePicker(selected: TimeRange, onSelect: (TimeRange) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimeRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                // Single line, sized to fit the 6-chip row at max font scale
                // (PLAN_UI_STRING_FIT: "Alle" wrapped to "All/e" at 1.3×).
                label = {
                    Text(
                        stringResource(range.labelRes),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        fontSize = 12.sp,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun CardSection(content: @Composable () -> Unit) {
    FudGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        padding = 16.dp,
        allowBlur = false
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
        Text(
            value,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp)
        )
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 0.5.sp)
        )
    }
}

@Composable
internal fun BodyMetricToggle(selected: BodyMetric, onSelect: (BodyMetric) -> Unit) {
    val labelWeight = stringResource(R.string.progress_metric_weight)
    val labelBodyFat = stringResource(R.string.progress_metric_body_fat)
    val shape = RoundedCornerShape(AppRadii.Container)
    val isDark = isDarkTheme()
    val trackFill = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val borderColor = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    val shadowAlpha = if (isDark) 0.12f else 0.05f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 8.dp else 3.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = shadowAlpha),
                spotColor = Color.Black.copy(alpha = shadowAlpha)
            )
            .clip(shape)
            .background(trackFill)
            .border(0.5.dp, borderColor, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        listOf(BodyMetric.WEIGHT to labelWeight, BodyMetric.BODY_FAT to labelBodyFat).forEach { (metric, label) ->
            val isSelected = metric == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .then(
                        if (isSelected) Modifier.background(AppColors.CalorieGradient)
                        else Modifier.background(Color.Transparent)
                    )
                    .clickable { onSelect(metric) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
