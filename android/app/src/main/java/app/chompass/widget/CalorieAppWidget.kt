package app.chompass.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.chompass.MainActivity
import app.chompass.R
import app.chompass.models.WidgetSnapshot

class CalorieAppWidget : GlanceAppWidget() {
    // Exact so LocalSize reports the real widget dimensions — the gauge and
    // bars scale to fill instead of floating at bucket-minimum size.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotLoader.load(context)

        provideContent {
            GlanceTheme {
                CalorieWidgetContent(context, snapshot)
            }
        }
    }

    companion object {
        val SMALL_SIZE = DpSize(140.dp, 140.dp)
        val MEDIUM_SIZE = DpSize(280.dp, 140.dp)
    }
}

class CalorieWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalorieAppWidget()
}

@Composable
private fun CalorieWidgetContent(context: Context, snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.backgroundProvider(snapshot.appearanceMode))
            .cornerRadius(22.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        if (size.width < CalorieAppWidget.MEDIUM_SIZE.width) {
            CalorieSmall(context, snapshot)
        } else {
            CalorieMedium(context, snapshot)
        }
    }
}

@Composable
private fun CalorieSmall(context: Context, snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    // Content area after the outer 14dp padding; the gauge fills whatever the
    // header (~18dp) and the bottom line (~16dp) leave over.
    val contentW = size.width.value - 28f
    val contentH = size.height.value - 28f
    val gaugeW = minOf(contentW, (contentH - 44f) / 0.58f).toInt().coerceAtLeast(80)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        WidgetHeader(iconRes = R.drawable.ic_widget_flame, label = context.getString(R.string.widget_today), appearance = snapshot.appearanceMode)
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center
        ) {
            SpeedometerWithCenter(
                progress = snapshot.calorieProgress.toFloat(),
                gaugeWidthDp = gaugeW,
                startHex = snapshot.themeStartHex,
                endHex = snapshot.themeEndHex,
                centerLarge = snapshot.calories.toString(),
                centerSmall = "/ ${snapshot.resolvedDisplayGoalTarget}",
                appearance = snapshot.appearanceMode
            )
        }
        Text(
            text = context.getString(R.string.widget_kcal_left_format, snapshot.caloriesRemaining),
            style = TextStyle(
                color = WidgetTheme.themeTextProvider(snapshot.themeStartHex),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun CalorieMedium(context: Context, snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    val contentH = size.height.value - 28f
    val gaugeW = minOf(size.width.value * 0.40f, (contentH - 22f) / 0.58f).toInt().coerceAtLeast(90)
    val barH = (contentH - 58f).toInt().coerceAtLeast(36)

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SpeedometerWithCenter(
                progress = snapshot.calorieProgress.toFloat(),
                gaugeWidthDp = gaugeW,
                startHex = snapshot.themeStartHex,
                endHex = snapshot.themeEndHex,
                centerLarge = snapshot.calories.toString(),
                centerSmall = "/ ${snapshot.resolvedDisplayGoalTarget}",
                appearance = snapshot.appearanceMode
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = context.getString(R.string.widget_kcal_left_format, snapshot.caloriesRemaining),
                style = TextStyle(
                    color = WidgetTheme.themeTextProvider(snapshot.themeStartHex),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            )
            val typical = snapshot.activeBurnTypical
            if (typical != null && typical > 0 && snapshot.displayGoalTarget != null) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = context.getString(
                        R.string.home_active_burn_caption_progress,
                        snapshot.activeCaloriesToday ?: 0,
                        typical,
                    ),
                    style = TextStyle(
                        color = WidgetTheme.secondaryTextProvider,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        Box(modifier = GlanceModifier.defaultWeight()) {
            NutrientBarsRow(
                snapshot,
                barHeightDp = barH,
                barWidthDp = (barH * 0.26f).toInt().coerceIn(11, 18),
                valueFontSp = 15
            )
        }
    }
}
