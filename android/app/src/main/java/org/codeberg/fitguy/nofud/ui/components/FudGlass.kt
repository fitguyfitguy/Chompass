package org.codeberg.fitguy.nofud.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import org.codeberg.fitguy.nofud.ui.theme.LocalGlassBlurEnabled

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun translucentFill(isDark: Boolean): Color {
    val alpha = if (isDark) 0.80f else 0.78f
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

@Composable
fun hairlineBorder(isDark: Boolean): Color {
    val alpha = if (isDark) 0.18f else 0.14f
    return MaterialTheme.colorScheme.outline.copy(alpha = alpha)
}

@Composable
fun Modifier.fudTranslucentSurface(
    shape: Shape,
    elevated: Boolean = true,
    isDark: Boolean = false
): Modifier {
    val fill = translucentFill(isDark)
    val borderColor = hairlineBorder(isDark)
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.06f)
    val elevation = if (elevated) (if (isDark) 8.dp else 4.dp) else 0.dp
    return this
        .then(
            if (elevated) {
                Modifier.shadow(
                    elevation = elevation,
                    shape = shape,
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                )
            } else {
                Modifier
            }
        )
        .clip(shape)
        .background(fill)
        .border(0.5.dp, borderColor, shape)
}

@Composable
fun FudGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    padding: Dp = 16.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    elevated: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    val blurEnabled = LocalGlassBlurEnabled.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.06f)
    val elevationDp = if (elevated) (if (isDark) 8.dp else 4.dp) else 0.dp

    val outline = hairlineBorder(isDark)
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val glassAlpha = if (isDark) 0.80f else 0.78f
    val glassBrush = Brush.linearGradient(
        listOf(
            surface.copy(alpha = glassAlpha),
            surfaceVariant.copy(alpha = glassAlpha)
        )
    )

    Box(
        modifier = modifier
            .then(
                if (elevated) {
                    Modifier.shadow(
                        elevation = elevationDp,
                        shape = shape,
                        ambientColor = shadowColor,
                        spotColor = shadowColor
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
    ) {
        // Background layer: optionally blurred to give the glass look without
        // smearing the child content.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(glassBrush)
                .then(
                    if (blurEnabled) {
                        Modifier.blur(radius = if (isDark) 20.dp else 16.dp)
                    } else {
                        Modifier
                    }
                )
        )

        // Crisp border on top of the background layer (not blurred).
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .border(0.5.dp, outline, shape)
        )

        // Content is never blurred; keep alignment + padding semantics unchanged.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(padding),
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

@Composable
fun FudGlassColumn(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    FudGlassSurface(
        modifier = modifier,
        cornerRadius = cornerRadius,
        padding = 0.dp
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun FudIconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    iconSize: Dp = 19.dp,
    tint: Color = AppColors.Calorie
) {
    val plainIconSize = if (iconSize < size * 0.88f) size * 0.88f else iconSize
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(plainIconSize)
        )
    }
}

@Composable
fun FudGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )
) {
    val shape = RoundedCornerShape(14.dp)
    val isDark = isDarkTheme()
    val fieldFill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.60f else 0.55f)
    val borderColor = hairlineBorder(isDark)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = textStyle,
        cursorBrush = SolidColor(AppColors.Calorie),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (singleLine) 52.dp else 118.dp)
            .clip(shape)
            .background(fieldFill)
            .border(0.5.dp, borderColor, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
            ) {
                if (value.isEmpty() && placeholder.isNotBlank()) {
                    Text(
                        placeholder,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        fontSize = textStyle.fontSize,
                        fontWeight = FontWeight.Medium
                    )
                }
                inner()
            }
        }
    )
}

@Composable
fun FudGlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FudGlassSurface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            cornerRadius = 20.dp,
            padding = 20.dp
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun FudGlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    height: Dp = 50.dp,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val brush = if (enabled) {
        Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd))
    } else {
        Brush.linearGradient(
            listOf(
                AppColors.Calorie.copy(alpha = 0.35f),
                AppColors.Calorie.copy(alpha = 0.35f)
            )
        )
    }
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (content != null) {
            content()
        } else {
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
fun FudGlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppColors.Calorie
) {
    val isDark = isDarkTheme()
    val shape = RoundedCornerShape(12.dp)
    val borderColor = hairlineBorder(isDark)
    Box(
        modifier = modifier
            .clip(shape)
            .border(0.5.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FudGlassDialogActions(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dismissText != null && onDismiss != null) {
            FudGlassTextButton(
                text = dismissText,
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            Spacer(Modifier.width(6.dp))
        }
        val primaryColor = if (destructive) Color(0xFFFF453A) else AppColors.Calorie
        FudGlassTextButton(text = primaryText, onClick = onPrimary, color = primaryColor)
    }
}
