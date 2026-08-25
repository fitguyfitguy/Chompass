package app.chompass.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.LocaleFormat
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanResolver
import app.chompass.models.UserProfile
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import java.time.LocalDate

/**
 * Day-type quick-switch sheet (#60 phase 1), opened from the hero chip: pick
 * today's profile (a per-day override via [MacroPlan.dayAssignments]), clear
 * the override, peek tomorrow, and jump to Settings. One tap from the ring —
 * this is the manual-toggle ask.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayTypeSwitchSheet(
    profile: UserProfile?,
    today: LocalDate,
    onSwitch: (profileId: String?) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val plan = profile?.macroPlan?.takeIf { it.enabled }
    // Resolution snapshots while the sheet is open: profile re-emits after a
    // switch (the app re-reads DataStore), recomposing the selected rows.
    val resolvedToday = remember(profile, today) {
        profile?.let { MacroPlanResolver.targetsFor(it, today) }
    }
    val resolvedTomorrow = remember(profile, today) {
        profile?.let { MacroPlanResolver.targetsFor(it, today.plusDays(1)) }
    }
    val hasOverrideToday = plan?.dayAssignments?.containsKey(today.toString()) == true

    ChompassBottomSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.day_type_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.day_type_sheet_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (plan == null) {
                Text(
                    stringResource(R.string.day_type_plan_off_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
            } else {
                SheetPillCard {
                    plan.profiles.forEach { p ->
                        if (p !== plan.profiles.first()) SheetHairline()
                        DayTypeRow(
                            profile = p,
                            selected = resolvedToday?.profileId == p.id,
                            onClick = {
                                onSwitch(p.id)
                                onDismiss()
                            },
                        )
                    }
                    if (resolvedToday?.profileId == null) {
                        SheetHairline()
                        DayTypeFallbackRow()
                    }
                }
                if (hasOverrideToday) {
                    TextButton(onClick = {
                        onSwitch(null)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.day_type_clear_override))
                    }
                }
                resolvedTomorrow?.let { tomorrow ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Event,
                            contentDescription = null,
                            tint = AppColors.Calorie,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            stringResource(
                                R.string.day_type_tomorrow,
                                tomorrow.profileName
                                    ?: stringResource(R.string.day_type_base_goals),
                                LocaleFormat.integer(tomorrow.targets.calories),
                            ),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                        )
                    }
                }
            }
            if (onOpenSettings != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenSettings()
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.day_type_edit_settings),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** One selectable day-type row: name + targets, check mark when in effect today. */
@Composable
private fun DayTypeRow(
    profile: MacroDayProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(profile.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    R.string.day_type_targets_summary,
                    LocaleFormat.integer(profile.calories),
                    profile.proteinG,
                    profile.carbsG,
                    profile.fatG,
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.sheet_selected_a11y),
                tint = AppColors.Calorie,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Shown when today resolved to base targets (plan enabled, no profile in effect). */
@Composable
private fun DayTypeFallbackRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.day_type_base_goals),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.Check,
            contentDescription = stringResource(R.string.sheet_selected_a11y),
            tint = AppColors.Calorie,
            modifier = Modifier.size(20.dp),
        )
    }
}
