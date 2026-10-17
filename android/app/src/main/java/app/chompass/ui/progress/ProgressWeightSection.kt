package app.chompass.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.UnitFormat
import app.chompass.ui.components.ChompassSurface
import app.chompass.ui.components.ChompassIconBubble
import app.chompass.ui.theme.AppTextOpacity

internal fun formatWeight(kg: Double, useMetric: Boolean): String =
    UnitFormat.weight(kg, useMetric)

internal fun formatWeightChange(deltaKg: Double, useMetric: Boolean): String {
    val displayValue = if (useMetric) deltaKg else UnitFormat.kgToLbs(deltaKg)
    val unit = if (useMetric) "kg" else "lbs"
    return "${UnitFormat.signedDelta(displayValue)} $unit"
}

@Composable
internal fun LegendSwatch(
    color: Color,
    label: String,
    dashed: Boolean = false,
    /** Hollow 8dp ring — raw readings drawn without a connecting line. */
    dot: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dot) {
            Box(
                Modifier
                    .size(8.dp)
                    .border(2.dp, color, CircleShape)
            )
        } else if (dashed) {
            Canvas(Modifier.width(18.dp).height(3.dp)) {
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }
        } else {
            Canvas(Modifier.width(18.dp).height(3.dp)) {
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                )
            }
        }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Subtle))
    }
}

@Composable
internal fun WeightHistoryLink(count: Int, onClick: () -> Unit) {
    ChompassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChompassIconBubble(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                size = 28.dp,
                iconSize = 16.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.progress_weight_history), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.progress_history_count_format, count),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun BodyFatHistoryLink(count: Int, onClick: () -> Unit) {
    ChompassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChompassIconBubble(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                size = 28.dp,
                iconSize = 16.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.progress_body_fat_history), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.progress_history_count_format, count),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
