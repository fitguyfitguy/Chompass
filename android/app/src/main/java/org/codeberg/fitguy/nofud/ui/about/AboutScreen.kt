package org.codeberg.fitguy.nofud.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.codeberg.fitguy.nofud.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.services.update.AndroidUpdateChecker
import org.codeberg.fitguy.nofud.services.update.AndroidUpdateState
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import kotlinx.coroutines.launch

private const val CODEBERG_REPO = "https://codeberg.org/fitguy/NoFUD"
private const val UPSTREAM_REPO = "https://github.com/apoorvdarshan/fud-ai"
private const val PRIVACY_URL = "https://codeberg.org/fitguy/NoFUD/src/branch/main/docs/PRIVACY.md"
private const val ASSET_CREDITS_URL = "https://codeberg.org/fitguy/NoFUD/src/branch/main/docs/ASSET_CREDITS.md"

@Composable
fun AboutSettingsRows(container: AppContainer) {
    val ctx = LocalContext.current
    val shareText = stringResource(R.string.about_share_message)
    val shareChooser = stringResource(R.string.about_share_chooser)
    val currentVersion = remember(ctx) { AndroidUpdateChecker.currentVersion(ctx) }
    var updateState by remember { mutableStateOf<AndroidUpdateState>(AndroidUpdateState.Idle) }
    val scope = rememberCoroutineScope()

    fun open(url: String) =
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun refreshUpdateState() {
        scope.launch {
            updateState = AndroidUpdateState.Checking
            updateState = AndroidUpdateChecker.check(ctx, currentVersion)
        }
    }

    LaunchedEffect(currentVersion) {
        updateState = AndroidUpdateState.Checking
        updateState = AndroidUpdateChecker.check(ctx, currentVersion)
    }

    fun share() {
        ctx.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            },
            shareChooser
        ))
    }

    Column(Modifier.fillMaxWidth()) {
        UpdateRow(
            state = updateState,
            currentVersion = currentVersion,
            onRefresh = ::refreshUpdateState,
            onOpenStore = {}
        )
        Hairline()
        AboutRow(Icons.Filled.Share, stringResource(R.string.about_share), onClick = ::share)
        Hairline()
        AboutRow(Icons.Filled.Code, stringResource(R.string.about_open_source)) { open(CODEBERG_REPO) }
        Hairline()
        AboutRow(Icons.Filled.Description, stringResource(R.string.about_asset_credits)) { open(ASSET_CREDITS_URL) }
        Hairline()
        AboutRow(Icons.Filled.History, stringResource(R.string.about_upstream)) { open(UPSTREAM_REPO) }
        Hairline()
        AboutRow(Icons.Filled.BugReport, stringResource(R.string.about_report_issue)) {
            open("$CODEBERG_REPO/issues/new")
        }
        Hairline()
        AboutRow(Icons.Filled.Lightbulb, stringResource(R.string.about_request_feature)) {
            open("$CODEBERG_REPO/issues/new")
        }
        Hairline()
        AboutRow(Icons.Filled.Lock, stringResource(R.string.about_privacy)) { open(PRIVACY_URL) }

        Column(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.about_made_by),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                stringResource(R.string.about_with_care),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun UpdateRow(
    state: AndroidUpdateState,
    currentVersion: String,
    onRefresh: () -> Unit,
    onOpenStore: () -> Unit
) {
    when (state) {
        AndroidUpdateState.Checking -> AboutRow(
            icon = Icons.Filled.Sync,
            label = stringResource(R.string.about_update_checking),
            trailing = {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AppColors.Calorie
                )
            },
            onClick = {}
        )
        is AndroidUpdateState.Available -> AboutRow(
            icon = Icons.Filled.SystemUpdate,
            label = stringResource(R.string.about_update_available),
            subtitle = stringResource(R.string.about_update_details_format, state.current, state.latest),
            showDot = true,
            onClick = onOpenStore
        )
        is AndroidUpdateState.Failed -> AboutRow(
            icon = Icons.Filled.Sync,
            label = stringResource(R.string.about_check_updates),
            subtitle = stringResource(R.string.about_version_format, state.current),
            onClick = onRefresh
        )
        is AndroidUpdateState.UpToDate -> AboutRow(
            icon = Icons.Filled.CheckCircle,
            label = stringResource(R.string.about_app_version),
            trailing = {
                Text(
                    state.current,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            },
            onClick = onRefresh
        )
        AndroidUpdateState.Idle -> AboutRow(
            icon = Icons.Filled.Sync,
            label = stringResource(R.string.about_check_updates),
            subtitle = stringResource(R.string.about_version_format, currentVersion),
            onClick = onRefresh
        )
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    showDot: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = AppColors.Calorie,
                modifier = Modifier.size(22.dp)
            )
            if (showDot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AppColors.Calorie)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .padding(start = 54.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    )
}
