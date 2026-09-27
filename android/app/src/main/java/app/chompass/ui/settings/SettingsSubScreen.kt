package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity

/**
 * Pinned bar for a pushed settings screen. [backContentDescription] is where
 * Back returns (the parent screen name), not a visible "< Settings" chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String = stringResource(R.string.nav_settings),
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backContentDescription,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/**
 * Shared scaffold for settings drill-down screens: top bar, then a vertically
 * scrolling column of section cards. Optional [snackbarHost] for one-shot
 * notices (e.g. the keto→day-types pause snackbar on Goals).
 */
@Composable
fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    backLabel: String = stringResource(R.string.nav_settings),
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = snackbarHost,
        topBar = { SettingsTopBar(title = title, onBack = onBack, backContentDescription = backLabel) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
            Spacer(Modifier.height(BottomNavScrollPadding))
        }
    }
}

/**
 * Back label for cross-linked settings sub-screens, keyed by the `from` nav
 * argument so users can retrace their path (e.g. Goals → Water → back to Goals).
 */
@Composable
internal fun settingsBackLabel(from: String): String = when (from) {
    "goals" -> stringResource(R.string.settings_section_goals)
    "water" -> stringResource(R.string.settings_water_title)
    "nicotine" -> stringResource(R.string.settings_nicotine_title)
    "caffeine" -> stringResource(R.string.settings_caffeine_title)
    "notifications" -> stringResource(R.string.settings_notifications)
    "data" -> stringResource(R.string.settings_group_data)
    "trackers" -> stringResource(R.string.settings_group_trackers)
    "ai" -> stringResource(R.string.settings_group_ai)
    "food" -> stringResource(R.string.settings_group_food)
    else -> stringResource(R.string.nav_settings)
}

/** A related-settings link row (Rule C cross-link footer). */
internal data class RelatedLink(
    val label: String,
    val onClick: () -> Unit,
)

/** "Related" footer shown at the bottom of settings sub-screens. */
@Composable
internal fun RelatedLinks(rows: List<RelatedLink>) {
    Column {
        Text(
            stringResource(R.string.settings_related),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        app.chompass.ui.components.FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = AppRadii.Container,
            padding = 0.dp,
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = row.onClick)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled),
                        )
                    }
                }
            }
        }
    }
}

/** Hub navigation row: icon + title + optional subtitle + chevron. */
@Composable
internal fun SettingsHubRow(
    label: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FudIconBubble(icon = icon, size = 28.dp, iconSize = 16.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Disabled),
        )
    }
}
