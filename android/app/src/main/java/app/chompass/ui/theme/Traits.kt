package app.chompass.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import app.chompass.R

/** Strength of the frost on glass chrome. MEDIUM is the shipped look. */
enum class GlassStrength(
    val storage: String,
    @param:StringRes val labelRes: Int,
    val tintAlpha: Float,
    /** null keeps the haze library default blur radius. */
    val blurRadiusDp: Float?,
    /** Window-surface alpha for bottom sheets when glass blur is on. */
    val sheetAlpha: Float,
    /** Window-surface alpha for dialogs when glass blur is on. */
    val dialogAlpha: Float,
) {
    LIGHT("light", R.string.frost_strength_light, 0.18f, 12f, 0.82f, 0.86f),
    MEDIUM("medium", R.string.frost_strength_medium, 0.30f, null, 0.88f, 0.92f),
    STRONG("strong", R.string.frost_strength_strong, 0.44f, 28f, 0.94f, 0.96f);

    companion object {
        val DEFAULT = MEDIUM
        fun fromStorage(raw: String?): GlassStrength = entries.firstOrNull { it.storage == raw } ?: DEFAULT
    }
}

/** Drift visibility of the ambient background fields. BALANCED is the shipped look. */
enum class AmbientIntensity(
    val storage: String,
    @param:StringRes val labelRes: Int,
    val alphaScale: Float,
) {
    SUBTLE("subtle", R.string.ambient_intensity_subtle, 0.55f),
    BALANCED("balanced", R.string.ambient_intensity_balanced, 1f),
    VIVID("vivid", R.string.ambient_intensity_vivid, 1.7f);

    companion object {
        val DEFAULT = BALANCED
        fun fromStorage(raw: String?): AmbientIntensity = entries.firstOrNull { it.storage == raw } ?: DEFAULT
    }
}

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
    /** Ambient drifting background behind every screen; OFF removes the layer entirely. */
    val ambientMotion: Boolean = true,
    val glassStrength: GlassStrength = GlassStrength.DEFAULT,
    val ambientIntensity: AmbientIntensity = AmbientIntensity.DEFAULT,
)

/** Provided by [ChompassTheme]; defaults keep previews and isolated hosts rendering. */
val LocalChompassTraits = staticCompositionLocalOf { ChompassTraits() }

/** The only surface components use to read traits. */
object ChompassTraitsDefaults {
    @Composable
    fun glassBlur(): Boolean = LocalChompassTraits.current.glassBlur

    @Composable
    fun ambientMotion(): Boolean = LocalChompassTraits.current.ambientMotion

    @Composable
    fun glassStrength(): GlassStrength = LocalChompassTraits.current.glassStrength

    @Composable
    fun ambientIntensity(): AmbientIntensity = LocalChompassTraits.current.ambientIntensity
}
