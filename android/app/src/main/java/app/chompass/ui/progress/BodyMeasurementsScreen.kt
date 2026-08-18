package app.chompass.ui.progress

import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.components.ChompassBottomSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.LocaleFormat
import app.chompass.models.BodyMeasurement
import app.chompass.models.Gender
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.UnitToggle
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.settings.NutritionPickerSheet
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppColors
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.chompass.models.UnitFormat

private val measurementHistoryFmt: DateTimeFormatter =
    LocaleFormat.mediumDateZoned()

private fun displayLengthCm(context: android.content.Context, cm: Double, useMetric: Boolean): String =
    if (useMetric) String.format(Locale.getDefault(), "%.1f %s", cm, context.getString(R.string.unit_cm))
    else String.format(Locale.getDefault(), "%.1f %s", UnitFormat.cmToInches(cm), context.getString(R.string.unit_in))

/** Logged sites in display order, skipping any that weren't entered. */
private fun measurementSiteList(context: android.content.Context, m: BodyMeasurement): List<Pair<String, Double>> = buildList {
    m.neckCm?.let { add(context.getString(R.string.measure_neck) to it) }
    m.waistCm?.let { add(context.getString(R.string.measure_waist) to it) }
    m.hipsCm?.let { add(context.getString(R.string.measure_hips) to it) }
    m.chestCm?.let { add(context.getString(R.string.measure_chest) to it) }
    m.upperArmCm?.let { add(context.getString(R.string.measure_upper_arm) to it) }
    m.thighCm?.let { add(context.getString(R.string.measure_thigh) to it) }
    m.calfCm?.let { add(context.getString(R.string.measure_calf) to it) }
    m.wristCm?.let { add(context.getString(R.string.measure_wrist) to it) }
}

/** Derived metrics computable from this entry + profile, skipping any missing their inputs. */
private fun derivedMetricList(context: android.content.Context, m: BodyMeasurement, gender: Gender, heightCm: Double): List<Pair<String, String>> = buildList {
    m.waistToHipRatio?.let { add(context.getString(R.string.derived_waist_to_hip) to String.format(Locale.getDefault(), "%.2f", it)) }
    m.waistToHeightRatio(heightCm)?.let { add(context.getString(R.string.derived_waist_to_height) to String.format(Locale.getDefault(), "%.2f", it)) }
    m.usNavyBodyFatPercent(gender, heightCm)?.let { add(context.getString(R.string.derived_body_fat) to String.format(Locale.getDefault(), "%.0f%%", it)) }
    m.wristFrame(gender, heightCm)?.let { add(context.getString(R.string.derived_frame) to context.getString(it.labelRes)) }
}

private fun measurementHistorySummary(context: android.content.Context, m: BodyMeasurement, gender: Gender, heightCm: Double, useMetric: Boolean): String {
    val sites = measurementSiteList(context, m).map { "${it.first} ${displayLengthCm(context, it.second, useMetric)}" }
    val bf = m.usNavyBodyFatPercent(gender, heightCm)?.let { "BF ${String.format(Locale.getDefault(), "%.0f%%", it)}" }
    return (sites + listOfNotNull(bf)).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyMeasurementsHistorySheet(
    entries: List<BodyMeasurement>,
    gender: Gender,
    heightCm: Double,
    useMetric: Boolean,
    onDelete: (java.util.UUID) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberChompassSheetState()
    val sheetSurface = MaterialTheme.colorScheme.surfaceContainerLow
    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
        containerColor = sheetSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.measurement_history), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done), color = AppColors.Calorie) }
            }
            Spacer(Modifier.height(12.dp))
            FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp) {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(vertical = 4.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(measurementHistoryFmt.format(entry.date), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    measurementHistorySummary(LocalContext.current, entry, gender, heightCm, useMetric),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                            IconButton(onClick = { onDelete(entry.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Settings → Personal Info detail screen for body-circumference measurements. Mirrors the Other
 * Nutrients screen: a tappable row per body part that opens a wheel picker to set its value, plus
 * the AI-derived metrics and history. Talks to BodyMeasurementRepository directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMeasurementsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by container.bodyMeasurementRepository.entries.collectAsState(initial = emptyList())
    val profile by container.profileRepository.profile.collectAsState(initial = null)
    val heightUnit by container.prefs.heightUnit.collectAsState(initial = "cm")
    val heightMetric = heightUnit == "cm"
    val gender = profile?.gender ?: Gender.MALE
    val heightCm = profile?.heightCm ?: 0.0
    val latest = entries.maxByOrNull { it.date }
    val unit = if (heightMetric) "cm" else "in"

    var editing by remember { mutableStateOf<BodyMeasurement.Site?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    val notSet = stringResource(R.string.settings_not_set)
    val cmUnit = stringResource(R.string.unit_cm)
    val inUnit = stringResource(R.string.unit_in)
    fun displayValue(site: BodyMeasurement.Site): String {
        val cm = latest?.value(site) ?: return notSet
        return if (heightMetric) String.format(Locale.getDefault(), "%.0f %s", cm, cmUnit) else String.format(Locale.getDefault(), "%.0f %s", UnitFormat.cmToInches(cm), inUnit)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = BottomNavScrollPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppColors.Calorie, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.nav_settings), color = AppColors.Calorie, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                Text(stringResource(R.string.body_measurements_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Optional. Chompass turns these into waist-to-hip, waist-to-height, body-fat %, and frame size, and reads them when it recalculates your goals and in Coach.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            item {
                FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 0.dp) {
                    Column {
                        BodyMeasurement.Site.values().forEachIndexed { index, site ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editing = site }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(site.labelRes), modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(displayValue(site), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            }
                            if (index != BodyMeasurement.Site.values().lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            }
                        }
                    }
                }
            }
            if (latest != null) {
                val derived = derivedMetricList(context, latest, gender, heightCm)
                if (derived.isNotEmpty()) {
                    item {
                        FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = AppRadii.SectionCard, padding = 16.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.label_derived), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                                derived.forEach { (label, value) ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
                                        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Calorie)
                                    }
                                }
                            }
                        }
                    }
                }
                if (entries.size > 1) {
                    item {
                        FudGlassSurface(
                            modifier = Modifier.fillMaxWidth().clickable { showHistory = true },
                            cornerRadius = 16.dp,
                            padding = 14.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.measurement_history), modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text("${entries.size}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { site ->
        val current = latest?.value(site)
        var editorValue by remember(site) {
            mutableStateOf(
                current?.let { if (heightMetric) Math.round(it).toInt() else Math.round(UnitFormat.cmToInches(it)).toInt() }
                    ?: if (heightMetric) 80 else 32
            )
        }
        FudGlassDialog(onDismissRequest = { editing = null }) {
            UnitToggle(
                stringResource(R.string.unit_cm),
                stringResource(R.string.unit_in),
                heightMetric,
                { metric ->
                    if (metric != heightMetric) {
                        editorValue = if (metric) {
                            UnitFormat.inchesToCmRounded(editorValue).coerceIn(10, 250)
                        } else {
                            UnitFormat.cmToInchesRounded(editorValue).coerceIn(4, 100)
                        }
                        scope.launch { container.prefs.setHeightUnit(if (metric) "cm" else "ftin") }
                    }
                },
                Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            key(heightMetric) {
                NutritionPickerSheet(
                    label = stringResource(site.labelRes),
                    unit = unit,
                    currentValue = editorValue,
                    range = if (heightMetric) 10..250 else 4..100,
                    step = 1,
                    onSave = { v ->
                        val cm = if (heightMetric) v.toDouble() else UnitFormat.inchesToCm(v.toDouble())
                        scope.launch { container.bodyMeasurementRepository.setValue(site, cm) }
                        editing = null
                    },
                    onResetToAuto = if (current != null) {
                        { scope.launch { container.bodyMeasurementRepository.setValue(site, null) }; editing = null }
                    } else null,
                    resetLabel = stringResource(R.string.action_clear),
                    onValueChange = { editorValue = it }
                )
            }
        }
    }
    if (showHistory) {
        BodyMeasurementsHistorySheet(
            entries = entries.sortedByDescending { it.date },
            gender = gender,
            heightCm = heightCm,
            useMetric = heightMetric,
            onDelete = { id -> scope.launch { container.bodyMeasurementRepository.deleteEntry(id) } },
            onDismiss = { showHistory = false }
        )
    }
}
