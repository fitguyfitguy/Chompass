package app.chompass.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.chompass.MainActivity
import app.chompass.R
import app.chompass.models.WaterAmountFormat
import app.chompass.models.WidgetSnapshot
import app.chompass.ui.util.clockTimePattern
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class WaterAppWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotLoader.load(context)

        provideContent {
            GlanceTheme {
                WaterWidgetContent(context, snapshot)
            }
        }
    }

    companion object {
        val SMALL_SIZE = DpSize(140.dp, 140.dp)
    }
}

class WaterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaterAppWidget()
}

@Composable
private fun WaterWidgetContent(context: Context, snapshot: WidgetSnapshot) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.backgroundProvider(snapshot.appearanceMode))
            .cornerRadius(22.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (snapshot.waterTrackingEnabled) {
            WaterProgressContent(context, snapshot)
        } else {
            WaterDisabledContent(context, snapshot.appearanceMode)
        }
    }
}

@Composable
private fun WaterProgressContent(context: Context, snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    val contentW = size.width.value - 28f
    val contentH = size.height.value - 28f
    // Next planned drink, hidden once its fire time is in the past (the widget
    // cannot tick like Home — a stale snapshot must not show an old fire).
    val nextFireMillis = snapshot.waterNextFireAtMillis
        ?.takeIf { it > System.currentTimeMillis() }
    val hasNextDrink = nextFireMillis != null
    // The extra footer row shrinks the gauge's height budget.
    val gaugeW = minOf(contentW, (contentH - if (hasNextDrink) 62f else 44f) / 0.58f)
        .toInt().coerceAtLeast(80)

    val currentLabel = if (snapshot.waterUseMetric) {
        snapshot.waterCurrentMl.toString()
    } else {
        WaterAmountFormat.flOzFromMl(snapshot.waterCurrentMl).toString()
    }
    val goalLabel = if (snapshot.waterUseMetric) {
        "${snapshot.waterGoalMl} ml"
    } else {
        "${WaterAmountFormat.flOzFromMl(snapshot.waterGoalMl)} fl oz"
    }
    val remainingLabel = if (snapshot.waterUseMetric) {
        context.getString(R.string.widget_ml_left_format, snapshot.waterRemaining)
    } else {
        context.getString(R.string.widget_fl_oz_left_format, WaterAmountFormat.flOzFromMl(snapshot.waterRemaining))
    }
    val nextDrinkLabel = nextFireMillis?.let { fireMillis ->
        val amount = if (snapshot.waterUseMetric) {
            context.getString(R.string.water_amount_ml, snapshot.waterNextDrinkMl)
        } else {
            context.getString(
                R.string.water_amount_fl_oz,
                WaterAmountFormat.flOzFromMl(snapshot.waterNextDrinkMl),
            )
        }
        val fireZone = Instant.ofEpochMilli(fireMillis).atZone(ZoneId.systemDefault())
        val time = fireZone.format(
            DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault()),
        )
        // Widget line is tight: amount and time only (Codeberg #3).
        "$amount · $time"
    }

    Column(modifier = GlanceModifier.fillMaxSize()) {
        WidgetHeader(iconRes = R.drawable.ic_widget_water, label = context.getString(R.string.water), appearance = snapshot.appearanceMode)
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            SpeedometerWithCenter(
                progress = snapshot.waterProgress.toFloat(),
                gaugeWidthDp = gaugeW,
                startHex = snapshot.themeStartHex,
                endHex = snapshot.themeEndHex,
                centerLarge = currentLabel,
                centerSmall = "/ $goalLabel",
                appearance = snapshot.appearanceMode,
            )
        }
        Text(
            text = remainingLabel,
            style = TextStyle(
                color = WidgetTheme.themeTextProvider(snapshot.themeStartHex),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            ),
        )
        if (nextDrinkLabel != null) {
            Text(
                text = nextDrinkLabel,
                style = TextStyle(
                    color = WidgetTheme.secondaryTextProvider(snapshot.appearanceMode),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WaterDisabledContent(context: Context, appearance: String?) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_water),
                contentDescription = null,
                modifier = GlanceModifier.size(30.dp),
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = context.getString(R.string.widget_water_tracking),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = context.getString(R.string.widget_water_enable_short),
                style = TextStyle(color = WidgetTheme.secondaryTextProvider(appearance), fontSize = 12.sp),
            )
        }
    }
}
