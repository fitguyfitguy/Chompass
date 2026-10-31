package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.components.ChompassIconBubble

@Composable
internal fun SettingsLocalBackupSection(
    enabled: Boolean,
    folderName: String,
    autoBackup: Boolean,
    lastBackupAt: String?,
    backupStatus: String?,
    onEnabledChange: (Boolean) -> Unit,
    onChooseFolder: () -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_local_backup_section)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.settings_local_backup_blurb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        ToggleRow(
            label = stringResource(R.string.settings_local_backup_enable),
            checked = enabled,
            onChange = onEnabledChange,
        )
        HorizontalDivider()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onChooseFolder() }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChompassIconBubble(
                    icon = Icons.Outlined.FolderOpen,
                    size = 22.dp,
                    iconSize = 14.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_local_backup_folder),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        folderName.ifBlank { stringResource(R.string.settings_local_backup_no_folder) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()
        ToggleRow(
            label = stringResource(R.string.settings_local_backup_auto),
            checked = autoBackup,
            enabled = enabled,
            onChange = onAutoBackupChange,
        )
        Text(
            stringResource(R.string.settings_local_backup_auto_desc),
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onBackupNow() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChompassIconBubble(
                icon = Icons.Outlined.Save,
                size = 22.dp,
                iconSize = 14.dp,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    stringResource(R.string.settings_local_backup_now),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
                Text(
                    syncStatusSubtitle(
                        backupStatus,
                        lastBackupAt,
                        lastLabelRes = R.string.settings_local_backup_last,
                        neverLabelRes = R.string.settings_local_backup_never,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
