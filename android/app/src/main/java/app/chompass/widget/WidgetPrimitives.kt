package app.chompass.widget

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.chompass.models.MacroValueFormatter
import app.chompass.models.WidgetNutrient
import app.chompass.models.WidgetSnapshot

// Building blocks shared by every Chompass Glance widget. They live here
// rather than inside one widget's file so a new widget composes them
// instead of copying the skeleton.

@Composable
internal fun WidgetHeader(iconRes: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(12.dp)
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = label,
            style = TextStyle(
                color = WidgetTheme.secondaryTextProvider,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        )
    }
}

/**
 * Home-style dashed speedometer with the readout inside the dome. The gauge
 * bitmap is gaugeWidth x (0.58 * gaugeWidth); texts are centered over it.
 */
@Composable
internal fun SpeedometerWithCenter(
    progress: Float,
    gaugeWidthDp: Int,
    startHex: Int?,
    endHex: Int?,
    centerLarge: String,
    centerSmall: String
) {
    val density = Resources.getSystem().displayMetrics.density
    val sizePx = (gaugeWidthDp * density).toInt().coerceAtLeast(1)
    // Stroke and fonts scale with the gauge so bigger widgets get a
    // proportionally bigger dial, not the same dial with more air.
    val strokePx = (gaugeWidthDp * 0.085f * density).coerceAtLeast(6f)
    val bitmap = speedometerBitmap(
        diameterPx = sizePx,
        progress = progress,
        strokeWidthPx = strokePx,
        startRgb = WidgetTheme.themeStart(startHex),
        endRgb = WidgetTheme.themeEnd(endHex)
    )
    val gaugeHeightDp = (gaugeWidthDp * 0.58f).toInt()
    val centerLargeFontSize = gaugeCenterFontSizeSp(gaugeWidthDp, centerLarge).sp
    val centerSmallFontSize = gaugeSecondaryFontSizeSp(gaugeWidthDp, centerSmall).sp

    Box(
        modifier = GlanceModifier.size(gaugeWidthDp.dp, gaugeHeightDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize()
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLarge,
                style = TextStyle(
                    color = WidgetTheme.themeTextProvider(startHex),
                    fontWeight = FontWeight.Bold,
                    fontSize = centerLargeFontSize
                )
            )
            Text(
                text = centerSmall,
                style = TextStyle(
                    color = WidgetTheme.secondaryTextProvider,
                    fontSize = centerSmallFontSize
                )
            )
        }
    }
}

/**
 * Keeps the primary readout inside the clear center of the semicircle. Values
 * can be much wider than calories when the Protein widget follows a selected
 * micronutrient (for example, "1234mg"), so gauge width and text length both
 * participate in sizing.
 */
internal fun gaugeCenterFontSizeSp(gaugeWidthDp: Int, text: String): Int {
    val baseSize = (gaugeWidthDp * 0.19f).toInt().coerceIn(17, 34)
    val characterCount = text.length.coerceAtLeast(1)
    val widthSafeSize = (gaugeWidthDp * 0.80f / characterCount).toInt()
    return minOf(baseSize, widthSafeSize).coerceAtLeast(10)
}

/** Goal/subtitle equivalent of [gaugeCenterFontSizeSp]. */
internal fun gaugeSecondaryFontSizeSp(gaugeWidthDp: Int, text: String): Int {
    val baseSize = (gaugeWidthDp * 0.10f).toInt().coerceIn(10, 15)
    val characterCount = text.length.coerceAtLeast(1)
    val widthSafeSize = (gaugeWidthDp * 0.90f / characterCount).toInt()
    return minOf(baseSize, widthSafeSize).coerceAtLeast(9)
}

/** The user's 4 selected Home nutrients as vertical fill tubes, like the app's Home bars. */
@Composable
internal fun NutrientBarsRow(
    snapshot: WidgetSnapshot,
    barHeightDp: Int,
    barWidthDp: Int = 11,
    valueFontSp: Int = 13
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        snapshot.displayedHomeNutrients.forEach { nutrient ->
            Box(modifier = GlanceModifier.defaultWeight()) {
                VerticalNutrientBarCell(
                    nutrient = nutrient,
                    barRgb = snapshot.nutrientColorHex(nutrient.id),
                    barHeightDp = barHeightDp,
                    barWidthDp = barWidthDp,
                    valueFontSp = valueFontSp
                )
            }
        }
    }
}

@Composable
internal fun VerticalNutrientBarCell(
    nutrient: WidgetNutrient,
    barRgb: Int,
    barHeightDp: Int,
    barWidthDp: Int,
    valueFontSp: Int
) {
    val density = Resources.getSystem().displayMetrics.density
    val bitmap = verticalBarBitmap(
        widthPx = (barWidthDp * density).toInt().coerceAtLeast(2),
        heightPx = (barHeightDp * density).toInt().coerceAtLeast(2),
        progress = nutrient.progress.toFloat(),
        startRgb = barRgb,
        endRgb = barRgb
    )
    Column(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = MacroValueFormatter.string(nutrient.value),
            style = TextStyle(
                color = WidgetTheme.colorProvider(barRgb),
                fontWeight = FontWeight.Bold,
                fontSize = valueFontSp.sp
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.size(barWidthDp.dp, barHeightDp.dp)
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        Text(
            text = nutrient.label,
            style = TextStyle(
                color = WidgetTheme.primaryTextProvider,
                fontWeight = FontWeight.Medium,
                fontSize = (valueFontSp - 3).coerceAtLeast(10).sp
            ),
            maxLines = 1
        )
        Text(
            text = "/${MacroValueFormatter.string(nutrient.goal)}${nutrient.unit}",
            style = TextStyle(
                color = WidgetTheme.secondaryTextProvider,
                fontSize = (valueFontSp - 4).coerceAtLeast(9).sp
            ),
            maxLines = 1
        )
    }
}
