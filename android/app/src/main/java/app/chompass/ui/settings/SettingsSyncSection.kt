package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.theme.AppColors

@Composable
internal fun SettingsSyncSection(
    webDavUrl: String,
    webDavUsername: String,
    webDavPassword: String,
    lastSyncAt: String?,
    syncStatus: String?,
    onWebDavUrlChange: (String) -> Unit,
    onWebDavUsernameChange: (String) -> Unit,
    onWebDavPasswordChange: (String) -> Unit,
    onSaveWebDav: () -> Unit,
    onExportSync: () -> Unit,
    onImportSync: () -> Unit,
    onSyncNow: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_sync_section)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.settings_sync_blurb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = webDavUrl,
                onValueChange = onWebDavUrlChange,
                label = { Text(stringResource(R.string.settings_webdav_url)) },
                placeholder = { Text(stringResource(R.string.settings_webdav_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = webDavUsername,
                onValueChange = onWebDavUsernameChange,
                label = { Text(stringResource(R.string.settings_webdav_username)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            OutlinedTextField(
                value = webDavPassword,
                onValueChange = onWebDavPasswordChange,
                label = { Text(stringResource(R.string.settings_webdav_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSaveWebDav() }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FudIconBubble(icon = Icons.Outlined.CloudSync, size = 22.dp, iconSize = 14.dp, tint = AppColors.Calorie)
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.settings_webdav_save),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onExportSync() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FudIconBubble(icon = Icons.Outlined.Upload, size = 22.dp, iconSize = 14.dp, tint = AppColors.Calorie)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.export_sync_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onImportSync() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FudIconBubble(icon = Icons.Outlined.Download, size = 22.dp, iconSize = 14.dp, tint = AppColors.Calorie)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.import_sync_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onSyncNow() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FudIconBubble(icon = Icons.Outlined.Sync, size = 22.dp, iconSize = 14.dp, tint = AppColors.Calorie)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    stringResource(R.string.settings_sync_now),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                val subtitle = syncStatus
                    ?: lastSyncAt?.let { stringResource(R.string.settings_last_sync, it) }
                    ?: stringResource(R.string.settings_not_synced)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
