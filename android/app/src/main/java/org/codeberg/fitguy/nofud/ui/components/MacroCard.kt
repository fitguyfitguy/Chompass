package org.codeberg.fitguy.nofud.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.fitguy.nofud.models.MacroValueFormatter
import org.codeberg.fitguy.nofud.ui.navigation.LocalLaunchFillEpoch

/**
 * A single macro shown as a vertical fill bar (rounded tube that fills bottom-up toward the goal),
 * with the value above and the name + goal beneath.
 */
@Composable
fun MacroCard(
    label: String,
    current: Double,
    goal: Int,
    unit: String = "g",
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val progress = if (goal > 0) (current.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val epoch = LocalLaunchFillEpoch.current
    var lastEpoch by rememberSaveable { mutableIntStateOf(0) }
    val animatable = remember { Animatable(if (lastEpoch == epoch) progress else 0f) }
    LaunchedEffect(epoch, progress) {
        val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 55f)
        if (lastEpoch != epoch) {
            animatable.snapTo(0f)
            animatable.animateTo(progress, spec)
            lastEpoch = epoch
        } else {
            animatable.animateTo(progress, spec)
        }
    }
    val animated = animatable.value

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            MacroValueFormatter.string(current),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1,
        )

        Box(
            modifier = Modifier.size(width = 16.dp, height = 74.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
            )
            val fillHeight = (74.dp * animated).coerceAtLeast(16.dp)
            Box(
                Modifier
                    .width(16.dp)
                    .height(fillHeight)
                    .clip(CircleShape)
                    .background(accentColor),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                "/$goal$unit",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
