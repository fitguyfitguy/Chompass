package app.chompass.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Cross-cutting visual traits a screen can opt into without threading booleans
 * through every call site. The theme resolves them from preferences; components
 * read them through [ChompassTraitsDefaults], never through
 * [LocalChompassTraits] directly, so a new trait lands in one place.
 */
@Immutable
data class ChompassTraits(
    /** Frosted-glass blur behind floating panels; OFF falls back to solid tint. */
    val glassBlur: Boolean = true,
)

/** Provided by [ChompassTheme]; defaults keep previews and isolated hosts rendering. */
val LocalChompassTraits = staticCompositionLocalOf { ChompassTraits() }

/** The only surface components use to read traits. */
object ChompassTraitsDefaults {
    @Composable
    fun glassBlur(): Boolean = LocalChompassTraits.current.glassBlur
}
