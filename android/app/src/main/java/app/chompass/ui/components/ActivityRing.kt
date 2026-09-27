package app.chompass.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Activity ring. Flat accent arc on a tinted track, endpoint dot at the sweep.
 */
@Composable
fun ActivityRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    strokeWidth: Dp = 14.dp,
    color: Color = AppColors.Calorie,
    centerContent: @Composable () -> Unit = {}
) {
    val animated = remember { Animatable(0f) }
    // First run mirrors .onAppear { withAnimation(.spring(response: 1.2,
    // dampingFraction: 0.75).delay(0.15)) }; later changes use the faster
    // .onChange spec. Folding both into one effect removes the redundant
    // first-frame double animation (two animators racing to the same value).
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(progress) {
        val firstRun = !appeared
        if (firstRun) {
            appeared = true
            delay(150)
        }
        animated.animateTo(
            targetValue = progress.coerceIn(0f, 1.5f),
            animationSpec = if (firstRun) {
                spring(dampingRatio = 0.75f, stiffness = 30f) // response 1.2 ≈ stiffness 30
            } else {
                spring(dampingRatio = 0.85f, stiffness = 110f) // response 0.6 ≈ stiffness 110
            }
        )
    }

    val trackColor = color.copy(alpha = 0.15f)

    Box(modifier = modifier.size(size).aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val side = min(this.size.width, this.size.height)
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val diameter = side - strokeWidth.toPx()
            val topLeft = Offset(
                x = (this.size.width - diameter) / 2f,
                y = (this.size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            val centerX = this.size.width / 2f
            val centerY = this.size.height / 2f
            val radius = diameter / 2f

            // Background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            val sweepDegrees = 360f * animated.value.coerceAtMost(1f)
            if (animated.value > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweepDegrees,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            }
            if (animated.value > 0.01f) {
                val endAngleRad = Math.toRadians((sweepDegrees - 90f).toDouble())
                val dotX = centerX + radius * cos(endAngleRad).toFloat()
                val dotY = centerY + radius * sin(endAngleRad).toFloat()
                drawCircle(
                    color = color,
                    radius = strokeWidth.toPx() / 2f,
                    center = Offset(dotX, dotY)
                )
            }
        }
        centerContent()
    }
}

/** Ready-made center label: big number + small label (e.g. "1,247\nkcal"). */
@Composable
fun RingCenterLabel(primary: String, secondary: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            primary,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            secondary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
        )
    }
}
