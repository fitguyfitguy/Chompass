package org.codeberg.fitguy.nofud.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Male
import androidx.compose.material.icons.outlined.Female
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.SportsMartialArts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.ActivityLevel
import org.codeberg.fitguy.nofud.models.Gender
import org.codeberg.fitguy.nofud.models.UserProfile
import org.codeberg.fitguy.nofud.models.WeightGoal
import org.codeberg.fitguy.nofud.ui.components.FudGlassSurface
import org.codeberg.fitguy.nofud.ui.components.FudGlassTextField
import org.codeberg.fitguy.nofud.ui.components.FudIconBubble
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import org.codeberg.fitguy.nofud.ui.theme.AppThemeColor
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class SettingsSheet {
    AI_PROVIDER, AI_MODEL, MAX_TOKENS, API_KEY, CUSTOM_BASE_URL, SPEECH_PROVIDER, SPEECH_LANGUAGE, SPEECH_KEY,
    SERVING_UNIT_MODE, SERVING_UNIT_HEURISTICS,
    FALLBACK_PROVIDER, FALLBACK_MODEL, FALLBACK_KEY, FALLBACK_BASE_URL,
    GENDER, BIRTHDAY, HEIGHT, WEIGHT, BODY_FAT, GOAL_BODY_FAT, ACTIVITY, GOAL, DIET_MODE, DIET_CARB_MODE, DIET_CARB_TARGET, GOAL_WEIGHT, GOAL_SPEED,
    CALORIES, PROTEIN, CARBS, FAT, OPTIONAL_NUTRIENTS,
    APPEARANCE, FOOD_LOG_SORT, WEEK_START
}

internal enum class HealthConnectPermissionAction {
    SYNC, ENERGY_GOALS
}

@Composable
internal fun ThemeColorSwatch(themeColor: AppThemeColor, modifier: Modifier = Modifier) {
    val background = if (themeColor.usesSystemPalette) {
        Brush.sweepGradient(
            listOf(
                Color(0xFF6750A4),
                Color(0xFF7D5260),
                Color(0xFFB3261E),
                Color(0xFF006E1C),
                Color(0xFF0061A4),
                Color(0xFF6750A4),
            )
        )
    } else {
        Brush.linearGradient(listOf(themeColor.primary, themeColor.primary))
    }
    Box(
        modifier
            .clip(CircleShape)
            .background(background),
    )
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    // iOS uses sentence-case section titles ("Personal Info", "Goals & Nutrition")
    // in a small grey caption. Match that — no uppercase transform.
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            padding = 0.dp,
            allowBlur = false
        ) {
            Column(Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
internal fun SettingRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    // iOS `.menu` Picker rows render a `chevron.up.chevron.down` instead of a
    // right-chevron to signal the inline dropdown affordance. Pass inlineMenu=true
    // for Gender, Weight Goal, and Activity Level.
    inlineMenu: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            FudIconBubble(icon = icon, size = 22.dp, iconSize = 14.dp)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            maxLines = 1,
        )
        Icon(
            if (inlineMenu) Icons.Filled.UnfoldMore else Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = if (inlineMenu) Modifier.size(18.dp) else Modifier
        )
    }
}

@Composable
internal fun ActivityLevelSettingRow(
    level: ActivityLevel,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FudIconBubble(icon = Icons.AutoMirrored.Outlined.DirectionsRun, size = 22.dp, iconSize = 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                stringResource(R.string.settings_activity_level),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            stringResource(level.displayNameRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Filled.UnfoldMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * A goal row (calories or a macro). Tapping the row opens the value picker. The lock glyph is a
 * READ-ONLY indicator (Filled.Lock pink when locked, Outlined.LockOpen gray when not) — saving a
 * value locks it; the picker's "Reset to Auto-balance" releases it. Dimmed while Adaptive is on.
 */
@Composable
internal fun LockableGoalRow(
    label: String,
    value: String,
    icon: ImageVector,
    locked: Boolean,
    lockEnabled: Boolean,
    onClick: () -> Unit,
    iconTint: Color = AppColors.Calorie,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FudIconBubble(icon = icon, size = 22.dp, iconSize = 14.dp, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            if (locked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
            contentDescription = stringResource(
                if (locked) R.string.settings_macro_locked else R.string.settings_macro_unlocked
            ),
            tint = when {
                !lockEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                locked -> AppColors.Calorie
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            },
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Multi-line text editor with a Save row at the bottom that pulses brand pink
 * when the current text differs from the persisted value, mirrors iOS Custom
 * AI Instructions section.
 */
@Composable
internal fun CustomInstructionsBlock(
    initial: String,
    placeholder: String,
    onSave: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    var saved by remember(initial) { mutableStateOf(initial) }
    val hasChanges = text != saved
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        FudGlassTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
            singleLine = false,
            minLines = 4,
            maxLines = 6
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                onSave(text)
                saved = text.trim()
                text = saved
            },
            enabled = hasChanges,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (hasChanges) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.settings_save),
                color = if (hasChanges) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            FudIconBubble(icon = icon, size = 22.dp, iconSize = 14.dp)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun ToggleRowWithInfo(
    label: String,
    checked: Boolean,
    icon: ImageVector? = null,
    onInfo: () -> Unit,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            FudIconBubble(icon = icon, size = 22.dp, iconSize = 14.dp)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        IconButton(onClick = onInfo, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.action_info),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp)
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
internal fun EnergyBurnGoalsRow(
    checked: Boolean,
    applying: Boolean,
    needsHealthConnect: Boolean,
    onInfo: () -> Unit,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FudIconBubble(icon = Icons.Outlined.LocalFireDepartment, size = 22.dp, iconSize = 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                stringResource(R.string.settings_energy_goals),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (needsHealthConnect) {
                Text(
                    stringResource(R.string.settings_needs_health_connect),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
        if (applying) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = AppColors.Calorie
            )
            Spacer(Modifier.width(14.dp))
        }
        IconButton(onClick = onInfo, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.action_info),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp)
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = !applying)
    }
}

@Composable
internal fun AdaptiveGoalsRow(
    checked: Boolean,
    applying: Boolean,
    onInfo: () -> Unit,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FudIconBubble(icon = Icons.Outlined.TrackChanges, size = 22.dp, iconSize = 14.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.settings_adaptive_goals),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (applying) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = AppColors.Calorie
            )
            Spacer(Modifier.width(14.dp))
        }
        IconButton(onClick = onInfo, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.action_info),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp)
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = !applying)
    }
}

internal fun feetInchesLabel(cm: Int): String {
    // Round to the nearest inch — truncating shows 5'6" for a 170 cm / 5'7" pick.
    val totalInches = Math.round(cm / 2.54).toInt()
    val feet = totalInches / 12
    val inches = totalInches % 12
    return "$feet' $inches\""
}

internal val birthdayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

internal fun birthdayDisplay(profile: UserProfile): String {
    val date = profile.birthday.atZone(ZoneId.systemDefault()).toLocalDate()
    return "${date.format(birthdayFormatter)} (age ${profile.age})"
}

// Closest Material mappings for the iOS SF Symbols used in picker rows.
internal fun genderIcon(g: Gender): ImageVector = when (g) {
    Gender.MALE -> Icons.Outlined.Male
    Gender.FEMALE -> Icons.Outlined.Female
    Gender.OTHER -> Icons.Outlined.Wc
}

internal fun activityIcon(a: ActivityLevel): ImageVector = when (a) {
    ActivityLevel.SEDENTARY -> Icons.Outlined.SelfImprovement
    ActivityLevel.LIGHT -> Icons.AutoMirrored.Outlined.DirectionsWalk
    ActivityLevel.MODERATE -> Icons.AutoMirrored.Outlined.DirectionsRun
    ActivityLevel.ACTIVE -> Icons.Outlined.LocalDining
    ActivityLevel.VERY_ACTIVE -> Icons.Outlined.FitnessCenter
    ActivityLevel.EXTRA_ACTIVE -> Icons.Outlined.SportsMartialArts
}

internal fun goalIcon(g: WeightGoal): ImageVector = when (g) {
    WeightGoal.LOSE -> Icons.AutoMirrored.Filled.TrendingDown
    WeightGoal.MAINTAIN -> Icons.AutoMirrored.Filled.TrendingFlat
    WeightGoal.GAIN -> Icons.AutoMirrored.Outlined.TrendingUp
}

internal fun appearanceIcon(key: String): ImageVector = when (key) {
    "light" -> Icons.Outlined.LightMode
    "dark" -> Icons.Outlined.DarkMode
    else -> Icons.Outlined.SettingsBrightness
}

/**
 * Pink-gradient capsule "Save" button matching the iOS picker sheets
 * (`LinearGradient(colors: AppColors.calorieGradient)` over a 14dp rounded
 * rectangle, white semibold label).
 */
@Composable
internal fun GradientSaveButton(
    text: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val brush = Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd))
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (enabled) brush else Brush.linearGradient(listOf(AppColors.Calorie.copy(alpha = 0.4f), AppColors.Calorie.copy(alpha = 0.4f))))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
            .border(0.7.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text ?: stringResource(R.string.action_save), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}
