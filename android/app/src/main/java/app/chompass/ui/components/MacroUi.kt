package app.chompass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.chompass.ui.theme.MacroKind

@Composable
fun MacroChip(
    kind: MacroKind,
    value: Double,
    modifier: Modifier = Modifier,
    shrinkToFit: Boolean = false,
) {
    val color = kind.color()
    val glyph = stringResource(kind.glyphRes())
    Box(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = if (shrinkToFit) 4.dp else 8.dp, vertical = 3.dp)
    ) {
        Text(
            "$glyph ${macroGramsText(value)}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = if (shrinkToFit) {
                TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp)
            } else {
                null
            },
        )
    }
}
