package app.chompass.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Man
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Woman
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.Gender
import app.chompass.ui.components.DateWheelPicker
import app.chompass.ui.components.DecimalWheelPicker
import app.chompass.ui.components.UnitToggle
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppThemeColor
import app.chompass.ui.theme.AppTextOpacity
import java.time.LocalDate

@Composable
internal fun WelcomeStep() {
    // 1:1 port of iOS OnboardingView.welcomeStep — broccoli logo, two-line
    // "Eat Smart, / Live Better" headline (second line uses the pink gradient),
    // and a centered two-line subheading.
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = onboardingLogoRes(AppColors.ThemeColor)),
            contentDescription = stringResource(R.string.onboarding_logo_description),
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onboarding_welcome_line1),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        // Second line of the headline uses the pink gradient as a foreground
        // brush — matches iOS .foregroundStyle(LinearGradient(...)).
        Text(
            stringResource(R.string.onboarding_welcome_line2),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            style = LocalTextStyle.current.copy(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(AppColors.CalorieStart, AppColors.CalorieEnd)
                )
            )
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        // Quick feature tour — everything is free and already unlocked (iOS parity).
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WelcomeFeatureRow(Icons.Outlined.PhotoCamera, stringResource(R.string.onboarding_feature_snap))
            WelcomeFeatureRow(Icons.Outlined.Forum, stringResource(R.string.onboarding_feature_coach))
            WelcomeFeatureRow(Icons.Outlined.MonitorHeart, stringResource(R.string.onboarding_feature_library))
            WelcomeFeatureRow(Icons.Outlined.Widgets, stringResource(R.string.onboarding_feature_widgets))
        }
    }
}

internal fun onboardingLogoRes(@Suppress("UNUSED_PARAMETER") themeColor: AppThemeColor): Int =
    R.drawable.ic_logo_teal

@Composable
private fun WelcomeFeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.Calorie,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun GenderStep(selected: Gender, onSelect: (Gender) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            stringResource(R.string.onboarding_gender_title),
            subtitle = stringResource(R.string.onboarding_gender_subtitle)
        )
        Spacer(Modifier.weight(1f))
        for (g in Gender.values()) {
            SelectionCard(
                icon = when (g) {
                    Gender.MALE -> Icons.Outlined.Man
                    Gender.FEMALE -> Icons.Outlined.Woman
                    Gender.OTHER -> Icons.Outlined.Accessibility
                },
                title = stringResource(g.displayNameRes),
                selected = g == selected
            ) { onSelect(g) }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun BirthdayStep(current: LocalDate, onChange: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            stringResource(R.string.onboarding_birthday_title),
            subtitle = stringResource(R.string.onboarding_birthday_subtitle)
        )
        Spacer(Modifier.weight(1f))
        DateWheelPicker(selected = current, onSelect = onChange)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun HeightWeightStep(
    cm: Int,
    kg: Double,
    heightMetric: Boolean,
    weightMetric: Boolean,
    onHeightChange: (Int) -> Unit,
    onWeightChange: (Double) -> Unit,
    onToggle: (Boolean) -> Unit
) {
    // iOS combines height + weight onto a single onboarding step. The
    // Imperial layout shows three columns (Feet | Inches | Weight) and the
    // Metric layout shows two (Height | Weight). Match that. The height and
    // weight wheels each follow their own unit pref (mixed configs are valid);
    // the single toggle shows Metric only when both are metric, and writes both.
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            stringResource(R.string.onboarding_height_weight_title),
            subtitle = stringResource(R.string.onboarding_height_weight_subtitle)
        )
        UnitToggle(
            leftLabel = stringResource(R.string.onboarding_imperial),
            rightLabel = stringResource(R.string.onboarding_metric),
            // metric=false → Imperial selected (left segment).
            isLeft = !(heightMetric && weightMetric),
            onSelect = { isLeftSel -> onToggle(!isLeftSel) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        if (heightMetric) {
            HeightWeightMetricWheels(
                cm = cm,
                kg = kg,
                weightMetric = weightMetric,
                onHeightChange = onHeightChange,
                onWeightChange = onWeightChange
            )
        } else {
            HeightWeightImperialWheels(
                cm = cm,
                kg = kg,
                weightMetric = weightMetric,
                onHeightChange = onHeightChange,
                onWeightChange = onWeightChange
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun BodyFatStep(
    bodyFat: Double?,
    goalBodyFat: Double?,
    onChange: (Double?) -> Unit,
    onGoalChange: (Double?) -> Unit
) {
    // Mirrors iOS: Yes/No SelectionCards. "No" reveals a small explanatory
    // ƒ(x) message; "Yes" reveals a body-fat % wheel picker + an optional Goal toggle.
    val knows = bodyFat != null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StepHeader(
            stringResource(R.string.onboarding_body_fat_title),
            subtitle = stringResource(R.string.onboarding_body_fat_subtitle)
        )
        SelectionCard(
            icon = Icons.Outlined.CheckCircle,
            title = stringResource(R.string.onboarding_yes),
            selected = knows,
            onClick = { if (!knows) onChange(0.20) }
        )
        Spacer(Modifier.height(12.dp))
        SelectionCard(
            icon = Icons.Outlined.Cancel,
            title = stringResource(R.string.onboarding_no),
            selected = !knows,
            onClick = { if (knows) onChange(null) }
        )
        Spacer(Modifier.height(20.dp))
        if (knows) {
            DecimalWheelPicker(
                value = (bodyFat ?: 0.20) * 100,
                onValueChange = { onChange(it / 100.0) },
                min = 3.0,
                max = 60.0,
                step = 0.5,
                unit = stringResource(R.string.unit_percent)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_body_fat_ranges),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Optional goal sub-section. Off by default — the toggle defaults
            // the goal value to the current body-fat % so the wheel has a sane
            // starting point. Goal body fat is display-only (Progress chart
            // overlay) and does NOT participate in BMR/TDEE/macro math.
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.onboarding_goal_optional),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = goalBodyFat != null,
                    onCheckedChange = { isOn ->
                        onGoalChange(if (isOn) bodyFat else null)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = AppColors.Calorie)
                )
            }
            if (goalBodyFat != null) {
                Spacer(Modifier.height(8.dp))
                DecimalWheelPicker(
                    value = goalBodyFat * 100,
                    onValueChange = { onGoalChange(it / 100.0) },
                    min = 3.0,
                    max = 60.0,
                    step = 0.5,
                    unit = stringResource(R.string.unit_percent)
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.onboarding_goal_set_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Faint)
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "ƒ(x)",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.onboarding_body_fat_no_worries),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
