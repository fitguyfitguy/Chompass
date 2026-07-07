package org.codeberg.fitguy.nofud.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(
    onPhoto: () -> Unit,
    onNote: () -> Unit,
    onSaved: () -> Unit,
    onVoice: () -> Unit,
    onBarcode: () -> Unit,
    onManual: () -> Unit,
    onCopyFromDay: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var moreExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (isDark) Color(0xF2141416) else Color(0xFFFAF3EE)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.add_food_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AddFoodHeroTile(
                    label = stringResource(R.string.add_food_hero_photo),
                    subtitle = stringResource(R.string.add_food_hero_photo_sub),
                    icon = Icons.Filled.PhotoCamera,
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onPhoto()
                    }
                )
                AddFoodHeroTile(
                    label = stringResource(R.string.add_food_hero_note),
                    subtitle = stringResource(R.string.add_food_hero_note_sub),
                    icon = Icons.Filled.Edit,
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onNote()
                    }
                )
                AddFoodHeroTile(
                    label = stringResource(R.string.add_food_hero_saved),
                    subtitle = stringResource(R.string.add_food_hero_saved_sub),
                    icon = Icons.Filled.Bookmark,
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onSaved()
                    }
                )
            }
            Spacer(Modifier.height(18.dp))
            SheetHairline()
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { moreExpanded = !moreExpanded }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.add_food_more_section),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(if (moreExpanded) 180f else 0f)
                )
            }
            AnimatedVisibility(
                visible = moreExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AddFoodMoreRow(
                        label = stringResource(R.string.home_menu_voice),
                        icon = Icons.Filled.Mic,
                        isDark = isDark,
                        onClick = {
                            onDismiss()
                            onVoice()
                        }
                    )
                    AddFoodMoreRow(
                        label = stringResource(R.string.home_menu_barcode),
                        icon = Icons.Filled.QrCodeScanner,
                        isDark = isDark,
                        onClick = {
                            onDismiss()
                            onBarcode()
                        }
                    )
                    AddFoodMoreRow(
                        label = stringResource(R.string.home_menu_manual_entry),
                        icon = Icons.Filled.DriveFileRenameOutline,
                        isDark = isDark,
                        onClick = {
                            onDismiss()
                            onManual()
                        }
                    )
                    AddFoodMoreRow(
                        label = stringResource(R.string.home_menu_copy_from_day),
                        icon = Icons.Filled.CalendarMonth,
                        isDark = isDark,
                        onClick = {
                            onDismiss()
                            onCopyFromDay()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFoodHeroTile(
    label: String,
    subtitle: String,
    icon: ImageVector,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 100.dp)
            .clip(shape)
            .background(
                if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                else Color(0xFFEDE3DD).copy(alpha = 0.76f)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.08f else 0.18f),
                        Color.White.copy(alpha = if (isDark) 0.02f else 0.04f),
                        AppColors.Calorie.copy(alpha = if (isDark) 0.03f else 0.06f)
                    )
                )
            )
            .border(
                0.7.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.16f else 0.46f),
                        AppColors.Calorie.copy(alpha = if (isDark) 0.10f else 0.18f)
                    )
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AppColors.Calorie,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 2,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun AddFoodMoreRow(
    label: String,
    icon: ImageVector,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                else Color(0xFFEDE3DD).copy(alpha = 0.55f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AppColors.Calorie,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp)
        )
    }
}
