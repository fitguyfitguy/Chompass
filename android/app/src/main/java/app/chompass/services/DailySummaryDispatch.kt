package app.chompass.services

import android.content.Context
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.ActiveCalorieSource
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class DailySummaryNotification(
    val title: String,
    val text: String,
    val bigText: String,
)

/**
 * Loads today's diary + burn and formats the Daily summary notification.
 * Returns null when the policy says SKIP (no food logged).
 */
internal suspend fun buildDailySummaryNotification(
    context: Context,
    container: AppContainer,
    fallbackTitle: String,
    fallbackText: String,
    now: ZonedDateTime = ZonedDateTime.now(),
    zone: ZoneId = now.zone,
): DailySummaryNotification? {
    val day = DailySummaryPolicy.summaryDate(now)
    val entries = runCatching { container.foodRepository.entriesForDate(day).first() }
        .getOrDefault(emptyList())
    val profile = runCatching { container.profileRepository.current() }.getOrNull()
    val activity = runCatching { container.homeActivityReader.readForDate(day) }.getOrNull()
    val widget = runCatching { container.prefs.widgetSnapshot.first() }.getOrNull()
    val widgetForDay = widget?.takeIf {
        it.dayStart.atZone(zone).toLocalDate() == day
    }

    val energyLive = activity?.energyLive == true ||
        (activity == null && widgetForDay?.activeCalorieSource == ActiveCalorieSource.MEASURED.storageKey)
    val active = when {
        activity?.energyLive == true -> activity.activeCalories
        widgetForDay != null -> widgetForDay.activeCaloriesToday ?: 0
        else -> activity?.activeCalories ?: 0
    }
    val total = activity?.totalCalories

    val input = DailySummaryInput(
        eatenKcal = entries.sumOf { it.calories },
        proteinG = entries.sumOf { it.protein }.roundToInt(),
        carbsG = entries.sumOf { it.carbs }.roundToInt(),
        fatG = entries.sumOf { it.fat }.roundToInt(),
        bmrKcal = profile?.bmr?.roundToInt() ?: 0,
        activeKcal = active,
        energyLive = energyLive,
        totalKcal = total,
        estimatedActiveKcal = profile?.estimatedDailyActiveCalories ?: 0,
        hasFoodLogged = entries.isNotEmpty(),
    )
    val result = DailySummaryPolicy.evaluate(input)
    return formatDailySummary(context, result, fallbackTitle, fallbackText)
}

internal fun formatDailySummary(
    context: Context,
    result: DailySummaryResult,
    fallbackTitle: String,
    fallbackText: String,
): DailySummaryNotification? {
    when (result.verdict) {
        DailySummaryVerdict.SKIP -> return null
        DailySummaryVerdict.STATIC -> return DailySummaryNotification(
            title = fallbackTitle,
            text = fallbackText,
            bigText = fallbackText,
        )
        else -> Unit
    }
    val nf = NumberFormat.getIntegerInstance()
    val title = when (result.verdict) {
        DailySummaryVerdict.DEFICIT ->
            context.getString(R.string.notif_summary_deficit, nf.format(result.delta))
        DailySummaryVerdict.SURPLUS ->
            context.getString(R.string.notif_summary_surplus, nf.format(abs(result.delta)))
        DailySummaryVerdict.ON_TARGET ->
            context.getString(R.string.notif_summary_on_target)
        else -> fallbackTitle
    }
    val text = context.getString(
        R.string.notif_summary_eaten_burned,
        nf.format(result.eaten),
        nf.format(result.burned),
    )
    val macros = context.getString(
        R.string.notif_summary_macros,
        result.proteinG,
        result.carbsG,
        result.fatG,
    )
    return DailySummaryNotification(
        title = title,
        text = text,
        bigText = "$text\n$macros",
    )
}
