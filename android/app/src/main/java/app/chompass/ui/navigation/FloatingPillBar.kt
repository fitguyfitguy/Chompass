package app.chompass.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.chompass.ui.components.LocalBottomBarHaze
import app.chompass.ui.components.hazeGlassFill
import app.chompass.ui.components.pressScale
import app.chompass.ui.theme.AppMotion
import kotlin.math.roundToInt

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * The floating pill bar below is ported from MorpheApp/morphe-manager (GPL-3),
 * adapted for Chompass.
 */

/**
 * One action of the bottom pill.
 *
 * @param selected Set for a tab: whether it is on screen, which the pill's indicator
 *   slides under.
 * @param badge Draws the small update-available dot on the icon.
 */
@Immutable
class FloatingPillItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val selected: Boolean? = null,
    val badge: Boolean = false,
)

/**
 * Tabs on a pill floating over the bottom of the content, which scrolls on beneath it to
 * the edge of the screen. The glass fill comes from [LocalBottomBarHaze]'s sources; the
 * bar takes the navigation-bar inset itself, so content under it should not, and should
 * leave room for inset + 16 + 64 dp (see [BottomNavScrollPadding]).
 */
@Composable
fun ChompassFloatingPillBar(items: List<FloatingPillItem>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                // As wide as its actions need up to the room there is, so a few of them sit
                // on a compact pill in the middle rather than spread across the whole screen
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = PillInset * 2 + PillSlotMaxWidth * items.size)
                    .fillMaxWidth()
                    .height(FloatingPillHeight)
                    .hazeGlassFill(
                        MaterialTheme.colorScheme.surfaceContainer,
                        CircleShape,
                        LocalBottomBarHaze.current,
                    )
                    .border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        CircleShape,
                    ),
                shape = CircleShape,
                color = Color.Transparent,
            ) {
                BoxWithConstraints(modifier = Modifier.padding(horizontal = PillInset)) {
                    val weights = pillLabelWeights(items.map { it.label }, maxWidth)
                    // Horizontal span of each slot from the pill's left edge, which the
                    // selection indicator travels between. Measured from the left on either
                    // layout direction, so the indicator is placed from that edge too rather
                    // than mirrored onto it
                    val slots = remember { mutableStateMapOf<Int, IntRange>() }

                    SelectionIndicator(
                        span = slots[items.indexOfFirst { it.selected == true }],
                        modifier = Modifier.align(AbsoluteAlignment.CenterLeft),
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEachIndexed { index, item ->
                            PillAction(
                                item = item,
                                weight = weights?.get(index) ?: 1f,
                                showLabel = weights != null,
                                modifier = Modifier.onPlaced { coordinates ->
                                    val left = coordinates.positionInParent().x.roundToInt()
                                    slots[index] = left until left + coordinates.size.width
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fill behind the selected tab, sliding over to the next one and taking its width rather
 * than jumping there.
 */
@Composable
private fun SelectionIndicator(span: IntRange?, modifier: Modifier = Modifier) {
    span ?: return
    val left by animateIntAsState(span.first, AppMotion.pressSpring(), label = "pill_indicator_left")
    val width by animateIntAsState(
        span.last + 1 - span.first,
        AppMotion.pressSpring(),
        label = "pill_indicator_width",
    )
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .absoluteOffset { IntOffset(left, 0) }
            .width(with(density) { width.toDp() })
            .height(PillActionHeight)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
    )
}

/**
 * One action of the pill: its icon over a short label, in a [weight] share of the pill.
 * The selected tab sits on the shared indicator. Without its label the action explains
 * itself through a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.PillAction(
    item: FloatingPillItem,
    weight: Float,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val isSelected = item.selected == true
    val iconColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pill_icon_color",
    )
    val labelColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pill_label_color",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val icon: @Composable () -> Unit = {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }

    val action: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(PillActionHeight)
                .pressScale(interactionSource)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Tab,
                    onClick = item.onClick,
                )
                .semantics {
                    contentDescription = item.label
                    item.selected?.let { selected = it }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (item.badge) {
                BadgedBox(
                    badge = {
                        Badge(
                            modifier = Modifier
                                .offset(x = (-4).dp, y = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape),
                        )
                    },
                ) {
                    icon()
                }
            } else {
                icon()
            }
            if (showLabel) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = PillLabelPadding)
                        .clearAndSetSemantics {},
                )
            }
        }
    }

    // The weight must land on this Box so Row still sees it as the direct child; TooltipBox
    // applies its own modifier to an inner wrapper, which Row can't see
    Box(modifier = modifier.weight(weight)) {
        if (showLabel) {
            action()
        } else {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(item.label) } },
                state = rememberTooltipState(),
            ) {
                action()
            }
        }
    }
}

/**
 * Shares of the pill's [width] for actions labelled [labels]: equal while every label fits
 * its equal share, sized to the labels while they fit together, and null once they cannot
 * all fit, which leaves the actions to their icons rather than cutting any label short.
 */
@Composable
private fun pillLabelWeights(labels: List<String>, width: Dp): List<Float>? {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val labelWidths = remember(labels, style, density) {
        labels.map { label -> with(density) { measurer.measure(label, style).size.width.toDp() } }
    }

    return remember(labelWidths, width) {
        if (labels.isEmpty()) return@remember null
        val needed = labelWidths.map { it + PillLabelPadding * 2 }
        val total = needed.fold(0.dp) { sum, labelWidth -> sum + labelWidth }
        when {
            needed.all { it <= width / labels.size } -> labels.map { 1f }
            // Room left over is shared evenly, so a short label is not squeezed against a
            // long one
            total <= width -> needed.map { (it + (width - total) / labels.size).value }
            else -> null
        }
    }
}

private val FloatingPillHeight = 64.dp
private val PillActionHeight = 52.dp
private val PillInset = 6.dp
private val PillSlotMaxWidth = 96.dp
private val PillLabelPadding = 4.dp
