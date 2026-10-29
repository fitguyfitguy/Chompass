package app.chompass.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Morphe composition cue: main content reads comfortably on wide surfaces
 * (foldables, tablets, resized windows) by capping its measure at 560dp and
 * centering inside the offered space. Chrome (top bars, bottom nav) stays
 * full-bleed — apply this to content columns only.
 */
fun Modifier.contentMaxWidth(maxWidth: Dp = 560.dp): Modifier =
    fillMaxSize()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)
