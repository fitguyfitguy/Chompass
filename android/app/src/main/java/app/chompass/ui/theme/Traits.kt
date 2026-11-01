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
 * How hard the ambient field reads against the background. BALANCED is the
 * shipped look, including the OLED dim that keeps true black close to off.
 * STRONG is the opt-in for a sharper, brighter field on that black.
 */
enum class AmbientContrast(
    val storage: String,
    @param:StringRes val labelRes: Int,
    /** Multiplier on wax alpha, stacked with [AmbientIntensity]. 1 keeps today's brightness. */
    val alphaScale: Float,
    /** OLED dim. BALANCED is the shipped 0.55 so black stays black until the user asks. */
    val oledAlphaScale: Float,
    /** Metaball body smoothstep edges. BALANCED is the shipped 0.85–1.15. */
    val bodyLo: Float,
    val bodyHi: Float,
    /** Halo smoothstep edges and gain. BALANCED is the shipped 0.3–0.9 at 0.3. */
    val haloLo: Float,
    val haloHi: Float,
    val haloGain: Float,
    /** Middle-band alpha floor. 0.75 is the shipped recess behind the primary card. */
    val midFloor: Float,
    /** Soft-oval fallback radius scale. 1 keeps the authored size. */
    val softRadiusScale: Float,
    /** Dark-background value floor. 0 leaves authored brightness untouched. */
    val darkValueFloor: Float,
) {
    SOFT(
        "soft", R.string.ambient_contrast_soft,
        0.72f, 0.40f, 0.70f, 1.35f, 0.15f, 1.05f, 0.42f, 0.55f, 1.12f, 0f,
    ),
    BALANCED(
        "balanced", R.string.ambient_contrast_balanced,
        1f, 0.55f, 0.85f, 1.15f, 0.3f, 0.9f, 0.3f, 0.75f, 1f, 0f,
    ),
    STRONG(
        "strong", R.string.ambient_contrast_strong,
        1.55f, 0.95f, 0.96f, 1.06f, 0.55f, 0.95f, 0.14f, 0.92f, 0.86f, 0.58f,
    );

    companion object {
        val DEFAULT = BALANCED
        fun fromStorage(raw: String?): AmbientContrast =
            entries.firstOrNull { it.storage == raw } ?: DEFAULT
    }
}

/**
 * Chroma of the ambient field. NATURAL leaves the authored hues untouched.
 * RICH pushes them away from gray and, on a dark background, lifts muddy
 * colors enough to read on black.
 */
enum class AmbientSaturation(
    val storage: String,
    @param:StringRes val labelRes: Int,
    /** Multiplier on HSV saturation. 1 is the authored chroma. */
    val chromaScale: Float,
    /** Dark-background value floor. 0 leaves authored brightness untouched. */
    val darkValueFloor: Float,
) {
    MUTED("muted", R.string.ambient_saturation_muted, 0.62f, 0f),
    NATURAL("natural", R.string.ambient_saturation_natural, 1f, 0f),
    RICH("rich", R.string.ambient_saturation_rich, 1.4f, 0.68f);

    companion object {
        val DEFAULT = NATURAL
        fun fromStorage(raw: String?): AmbientSaturation =
            entries.firstOrNull { it.storage == raw } ?: DEFAULT
    }
}

/** Drift speed of the ambient wax field. STANDARD is the shipped look. */
enum class AmbientSpeed(
    val storage: String,
    @param:StringRes val labelRes: Int,
    /** Multiplier on accumulated wall time, so a live change never jumps the phase. */
    val timeScale: Float,
) {
    CALM("calm", R.string.ambient_speed_calm, 0.6f),
    STANDARD("standard", R.string.ambient_speed_standard, 1f),
    LIVELY("lively", R.string.ambient_speed_lively, 1.6f);

    companion object {
        val DEFAULT = STANDARD
        fun fromStorage(raw: String?): AmbientSpeed = entries.firstOrNull { it.storage == raw } ?: DEFAULT
    }
}

/** Where the ambient field takes its three hues from. */
enum class AmbientPalette(
    val storage: String,
    @param:StringRes val labelRes: Int,
) {
    /** The theme accent plus two hue-rotated counterpoints. */
    ACCENT("accent", R.string.ambient_palette_accent),
    /** The protein / carbs / fat swatches. */
    MACRO("macro", R.string.ambient_palette_macro);

    companion object {
        val DEFAULT = ACCENT
        fun fromStorage(raw: String?): AmbientPalette = entries.firstOrNull { it.storage == raw } ?: DEFAULT
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
    val ambientContrast: AmbientContrast = AmbientContrast.DEFAULT,
    val ambientSaturation: AmbientSaturation = AmbientSaturation.DEFAULT,
    val ambientSpeed: AmbientSpeed = AmbientSpeed.DEFAULT,
    val ambientPalette: AmbientPalette = AmbientPalette.DEFAULT,
    /** Gyro parallax on the ambient field; OFF keeps the sensor unregistered. */
    val ambientTilt: Boolean = true,
    /** True-black neutrals. Balanced contrast dims the ambient field so black stays nearly off; Strong eases that dim. */
    val oled: Boolean = false,
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

    @Composable
    fun ambientContrast(): AmbientContrast = LocalChompassTraits.current.ambientContrast

    @Composable
    fun ambientSaturation(): AmbientSaturation = LocalChompassTraits.current.ambientSaturation

    @Composable
    fun ambientSpeed(): AmbientSpeed = LocalChompassTraits.current.ambientSpeed

    @Composable
    fun ambientPalette(): AmbientPalette = LocalChompassTraits.current.ambientPalette

    @Composable
    fun ambientTilt(): Boolean = LocalChompassTraits.current.ambientTilt

    @Composable
    fun oled(): Boolean = LocalChompassTraits.current.oled
}
