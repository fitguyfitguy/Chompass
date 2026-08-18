package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.services.weather.OmCity
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.FudGlassTextButton
import app.chompass.ui.components.FudGlassTextField
import app.chompass.ui.theme.AppColors
import app.chompass.ui.util.clockTimePattern
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** "updated 14:32" today, "yesterday 23:12" / "12 Aug 08:00" otherwise. */
@Composable
internal fun updatedAtText(updatedAtMillis: Long?): String? {
    if (updatedAtMillis == null || updatedAtMillis <= 0L) return null
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val updated = Instant.ofEpochMilli(updatedAtMillis).atZone(zone)
    val today = LocalDate.now()
    val formatter = remember(context) {
        DateTimeFormatter.ofPattern(clockTimePattern(context), Locale.getDefault())
    }
    return when {
        updated.toLocalDate() == today ->
            stringResource(R.string.settings_weather_updated_today, updated.format(formatter))
        updated.toLocalDate() == today.minusDays(1) ->
            stringResource(R.string.settings_weather_updated_yesterday, updated.format(formatter))
        else -> stringResource(
            R.string.settings_weather_updated_date,
            updated.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
            updated.format(formatter),
        )
    }
}

/**
 * Open-Meteo city picker: search field → result list; tapping a city selects
 * it and fetches today's high. Carries the current status + manual refresh
 * for the already-selected city.
 */
@Composable
internal fun OpenMeteoCitySheet(
    currentCity: OmCity?,
    currentHighC: Int?,
    updatedAtMillis: Long?,
    manualHighC: Int,
    onSearch: suspend (String) -> List<OmCity>,
    onSelect: (OmCity) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OmCity>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_weather_city_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_weather_city_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        Spacer(Modifier.height(12.dp))
        FudGlassTextField(
            value = query,
            onValueChange = { query = it; searched = false },
            placeholder = stringResource(R.string.settings_weather_city_search_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        GradientSaveButton(
            text = stringResource(R.string.settings_weather_city_search),
            onClick = {
                scope.launch {
                    searching = true
                    searched = true
                    results = onSearch(query)
                    searching = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        when {
            searching -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.width(24.dp).height(24.dp), strokeWidth = 2.5.dp)
            }
            searched && results.isNullOrEmpty() -> Text(
                stringResource(R.string.settings_weather_city_search_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
            !results.isNullOrEmpty() -> LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(results.orEmpty()) { city ->
                    val selected = city.id == currentCity?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(city) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                city.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) AppColors.Calorie else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (selected) {
                            Text(
                                stringResource(R.string.settings_weather_city_selected),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Calorie,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        // Current selection + status + manual refresh.
        FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = AppRadii.Container,
            padding = 0.dp,
        ) {
            Column {
                SettingRow(
                    label = stringResource(R.string.settings_weather_status_label),
                    value = currentHighC?.let {
                        stringResource(R.string.settings_weather_status_value, it, updatedAtText(updatedAtMillis) ?: "—")
                    } ?: stringResource(R.string.settings_weather_status_fallback, manualHighC),
                ) { onRefresh() }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_weather_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        Spacer(Modifier.height(14.dp))
        FudGlassTextButton(
            text = stringResource(R.string.action_close),
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
        Spacer(Modifier.height(8.dp))
    }
}
