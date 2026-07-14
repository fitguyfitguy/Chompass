package org.codeberg.fitguy.nofud.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.ui.theme.AppColors

@Composable
internal fun NotificationsStep(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    // iOS notificationsStep: centered bell.badge.fill in pink + big headline
    // "Be reminded to\nlog meals" + subtitle + pink CTA.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onToggle(granted) }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.NotificationsActive,
            contentDescription = null,
            tint = AppColors.Calorie,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onboarding_notifications_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_notifications_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd))
                )
                .clickable {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onToggle(true)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (enabled) stringResource(R.string.onboarding_notifications_enabled) else stringResource(R.string.onboarding_notifications_allow),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_notifications_change_anytime),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        )
    }
}

@Composable
internal fun HealthConnectStep(container: AppContainer, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    // iOS appleHealthStep: heart.fill in pink circle, title "Connect to\nApple Health",
    // feature row list, pink CTA "Connect". Android maps Apple Health → Health Connect.
    val hcLauncher = rememberLauncherForActivityResult(
        container.health.permissionRequestContract()
    ) { granted ->
        onToggle(granted.containsAll(container.health.permissions))
    }
    val available = remember { container.health.isAvailable() }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = AppColors.Calorie
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onboarding_health_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_health_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier.padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HealthFeatureRow(icon = Icons.Outlined.Restaurant, label = stringResource(R.string.onboarding_health_feature_nutrition))
            HealthFeatureRow(icon = Icons.Outlined.MonitorWeight, label = stringResource(R.string.onboarding_health_feature_weight))
            HealthFeatureRow(icon = Icons.Outlined.Accessibility, label = stringResource(R.string.onboarding_health_feature_body))
        }
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (available)
                        Brush.horizontalGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd))
                    else
                        Brush.horizontalGradient(listOf(
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                        ))
                )
                .clickable(enabled = available) {
                    hcLauncher.launch(container.health.permissions)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    !available -> stringResource(R.string.onboarding_health_unavailable)
                    enabled -> stringResource(R.string.onboarding_health_connected)
                    else -> stringResource(R.string.onboarding_health_connect)
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HealthFeatureRow(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
