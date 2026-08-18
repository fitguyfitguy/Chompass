package app.chompass.ui.home

import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.components.ChompassBottomSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.WaterAmountFormat
import app.chompass.ui.theme.AppColors

@Composable
private fun waterProgressLabel(currentMl: Int, goalMl: Int, useMetric: Boolean): String =
    if (useMetric) {
        stringResource(R.string.water_progress, currentMl, goalMl)
    } else {
        stringResource(
            R.string.water_progress_fl_oz,
            WaterAmountFormat.flOzFromMl(currentMl),
            WaterAmountFormat.flOzFromMl(goalMl),
        )
    }

@Composable
fun WaterProgressRow(
    current: Int,
    goal: Int,
    useMetric: Boolean = true,
    auto: Boolean = false,
    onAutoClick: (() -> Unit)? = null,
    /** Preformatted "Next 300 ml · 15:24" hint under the bar; null hides it. */
    nextDrinkLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val progress = if (goal > 0) (current.toFloat() / goal).coerceIn(0f, 1f) else 0f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = AppColors.Calorie,
                modifier = Modifier.size(17.dp),
            )
            Text(
                stringResource(R.string.water),
                modifier = Modifier.padding(start = 6.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            if (auto) {
                val badgeModifier = if (onAutoClick != null) {
                    Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onAutoClick)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                } else {
                    Modifier.padding(start = 6.dp)
                }
                Text(
                    stringResource(R.string.settings_water_auto_badge),
                    modifier = badgeModifier,
                    color = AppColors.Calorie,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                waterProgressLabel(current, goal, useMetric),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                fontSize = 12.sp,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = AppColors.Calorie,
            trackColor = AppColors.Calorie.copy(alpha = 0.16f),
        )
        if (nextDrinkLabel != null) {
            Text(
                nextDrinkLabel,
                color = AppColors.Calorie,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterCustomAmountSheet(
    useMetric: Boolean = true,
    onDismiss: () -> Unit,
    onAdd: (Int) -> Unit,
) {
    val sheetState = rememberChompassSheetState()
    var customAmount by remember { mutableStateOf("") }
    val amountMl = if (useMetric) {
        customAmount.toIntOrNull()?.takeIf { it > 0 }
    } else {
        customAmount.toIntOrNull()?.takeIf { it > 0 }?.let(WaterAmountFormat::mlFromFlOz)
    }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.water_log_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.padding(horizontal = 31.dp))
            }

            Text(
                stringResource(R.string.water_how_much),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )

            OutlinedTextField(
                value = customAmount,
                onValueChange = { customAmount = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.water_custom_amount)) },
                suffix = {
                    Text(
                        stringResource(if (useMetric) R.string.unit_ml else R.string.unit_fl_oz)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Button(
                onClick = {
                    amountMl?.let(onAdd)
                    onDismiss()
                },
                enabled = amountMl != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Calorie),
            ) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null)
                Text(
                    stringResource(R.string.water_add),
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
