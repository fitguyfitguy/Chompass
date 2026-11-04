package app.chompass.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Fixed-height / pill-height containers: chips, pills, capsule input bars.
 * Stays a perfect capsule even when the content grows (e.g. multiline input).
 *
 * Growable containers MUST use fixed M3 slots instead (sheets/dialogs
 * [Shapes.extraLarge], cards [Shapes.large]); percent-based corner shapes are
 * forbidden on growable containers because the radius scales with the smaller
 * side — multi-row cards grew 100-180dp circular corners that clipped content
 * (precedent comment in FoodSheetPrimitives.kt SheetPillCard, 6.0.0 beta
 * device pass 2026-10-31).
 */
val CapsuleShape = CircleShape
