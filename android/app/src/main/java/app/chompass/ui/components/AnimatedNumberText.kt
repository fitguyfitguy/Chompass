package app.chompass.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import app.chompass.ui.theme.AppMotion

/**
 * Numeric label that slides half a line up/down with a fade when the value
 * changes (the verified hero-counter recipe, generalized). Enter runs on the
 * default 220ms tween, exit on the fast 180ms tween — incoming rows arrive
 * after outgoing ones start leaving, so the swap reads as one motion.
 */
@Composable
fun AnimatedNumberText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    autoSize: TextAutoSize? = null,
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (fadeIn(tween(AppMotion.TweenDefaultMs, easing = AppMotion.EasingStandard)) +
                slideInVertically(tween(AppMotion.TweenDefaultMs, easing = AppMotion.EasingStandard)) { it / 2 })
                .togetherWith(
                    fadeOut(tween(AppMotion.TweenFastMs, easing = AppMotion.EasingStandard)) +
                        slideOutVertically(tween(AppMotion.TweenFastMs, easing = AppMotion.EasingStandard)) { -it / 2 }
                )
        },
        label = "animatedNumberText",
        modifier = modifier,
    ) { v ->
        androidx.compose.material3.Text(
            text = v,
            style = style,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            autoSize = autoSize,
            softWrap = false,
        )
    }
}
