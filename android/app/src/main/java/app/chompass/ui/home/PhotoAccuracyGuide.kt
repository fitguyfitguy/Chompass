package app.chompass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppColors

/**
 * Progressive accuracy guidance for photo entry: a dismissible tip card for early
 * entries, otherwise a compact Info control that opens the same body in a dialog.
 */
@Composable
fun PhotoAccuracyGuide(
    showProminentCard: Boolean,
    modifier: Modifier = Modifier,
    sectionTitle: String? = null,
) {
    var cardDismissed by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val showCard = showProminentCard && !cardDismissed

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showCard) {
            PhotoAccuracyTipCard(onDismiss = { cardDismissed = true })
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (sectionTitle != null) {
                Text(
                    sectionTitle,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!showCard) {
                PhotoAccuracyInfoButton(onClick = { showDialog = true })
            }
        }
    }

    if (showDialog) {
        PhotoAccuracyGuideDialog(onDismiss = { showDialog = false })
    }
}

@Composable
fun PhotoAccuracyTipCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(
                AppColors.Calorie.copy(alpha = 0.10f),
                RoundedCornerShape(AppRadii.Field),
            )
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.photo_accuracy_guide_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Calorie,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.photo_accuracy_guide_dismiss),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            stringResource(R.string.photo_accuracy_guide_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
    }
}

@Composable
fun PhotoAccuracyInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(R.string.photo_accuracy_guide_cd),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun PhotoAccuracyGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photo_accuracy_guide_title)) },
        text = {
            Text(
                stringResource(R.string.photo_accuracy_guide_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.photo_accuracy_guide_dismiss))
            }
        },
    )
}
