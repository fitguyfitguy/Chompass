package app.chompass.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Flat accent: [AppColors.Calorie] and [AppColors.CalorieStart] mirror the
 * scheme primary passed to [AppColors.setThemeColor]. Widgets read the same
 * primary; the hero's ramp tip is a derived stop ([heroGradientEnd]), never a
 * second scheme color.
 *
 * Contrast assertions pin the warm palette's readability: the neutrals are a
 * deliberate recolour of the old gray-purple set, and every fixed accent — the
 * brand Plum/Peach pair included — must clear 4.5:1 for onPrimary in both light
 * and dark. Screenshots and device passes cannot catch a numeric regression.
 */
class AppColorsContrastTest {
    @After
    fun tearDown() {
        AppColors.setThemeColor(AppThemeColor.SYSTEM)
    }

    @Test
    fun calorie_mirrorsPrimaryOverride() {
        val primary = Color(0xFF006B5E)
        AppColors.setThemeColor(AppThemeColor.TEAL, primary)
        assertEquals(primary, AppColors.Calorie)
        assertEquals(primary, AppColors.CalorieStart)
    }

    @Test
    fun calorie_fallsBackToThemeStartWithoutOverride() {
        AppColors.setThemeColor(AppThemeColor.BLUE)
        assertEquals(AppThemeColor.BLUE.start, AppColors.Calorie)
        assertEquals(AppColors.Calorie, AppColors.CalorieStart)
    }

    @Test
    fun calorie_staysFlatWhenPrimaryIsNearWhite() {
        val primary = Color(0xFFE6E6E6)
        AppColors.setThemeColor(AppThemeColor.SYSTEM, primary)
        assertEquals(primary, AppColors.Calorie)
        assertEquals(primary, AppColors.CalorieStart)
    }

    @Test
    fun lightBodyAndMutedText_meetWcagAA() {
        assertTrue(
            "ink on cream",
            contrastRatio(AppColors.OnLight, AppColors.AppBackgroundLight) >= 4.5,
        )
        assertTrue(
            "muted on cream",
            contrastRatio(AppColors.MutedLight, AppColors.AppBackgroundLight) >= 4.5,
        )
    }

    @Test
    fun darkBodyAndMutedText_meetWcagAA() {
        assertTrue(
            "blush on plum",
            contrastRatio(AppColors.OnDark, AppColors.AppBackgroundDark) >= 4.5,
        )
        assertTrue(
            "muted on plum",
            contrastRatio(AppColors.MutedDark, AppColors.AppBackgroundDark) >= 4.5,
        )
    }

    @Test
    fun brandAccents_clearWcagAAOnFilledSurfaces() {
        // The refresh's requirement: the default (Plum) and the alternative (Peach)
        // must be readable as filled buttons in both schemes.
        listOf(AppThemeColor.PLUM, AppThemeColor.PEACH).forEach { accent ->
            val light = lightColors(accent)
            assertTrue(
                "${accent.key} light onPrimary ${light.onPrimary} on ${light.primary}",
                contrastRatio(light.onPrimary, light.primary) >= 4.5,
            )
            val dark = darkColors(accent)
            assertTrue(
                "${accent.key} dark onPrimary ${dark.onPrimary} on ${dark.primary}",
                contrastRatio(dark.onPrimary, dark.primary) >= 4.5,
            )
        }
    }

    @Test
    fun everyFixedAccent_filledSurfaceMeetsWcagAaLargeText() {
        // The dark scheme renders an accent at a mid tone, which caps the best possible
        // label contrast near 4.4:1 (indigo is the tightest). Reaching full AA there needs a
        // lighter dark primary tone — a deliberate design change, not part of this recolour.
        // 3:1 (AA large text) is the floor this refresh must never break.
        AppThemeColor.entries.filterNot { it.usesSystemPalette }.forEach { accent ->
            val light = lightColors(accent)
            assertTrue(
                "${accent.key} light onPrimary ${light.onPrimary} on ${light.primary}",
                contrastRatio(light.onPrimary, light.primary) >= 3.0,
            )
            val dark = darkColors(accent)
            assertTrue(
                "${accent.key} dark onPrimary ${dark.onPrimary} on ${dark.primary}",
                contrastRatio(dark.onPrimary, dark.primary) >= 3.0,
            )
        }
    }

    @Test
    fun heroRamp_keepsNonTextContrastAndBrightensTowardTheTip() {
        // The hero arc's ramp tip still reads as progress, so it keeps the 3:1
        // non-text floor on its ground; the only direction the ramp may move is
        // toward white, or the hero stops being the theme color.
        AppThemeColor.entries.filterNot { it.usesSystemPalette }.forEach { accent ->
            listOf(
                Triple("light", lightColors(accent).primary, AppColors.AppBackgroundLight),
                Triple("dark", darkColors(accent).primary, AppColors.AppBackgroundDark),
            ).forEach { (scheme, start, ground) ->
                val tip = heroGradientEnd(start)
                assertTrue(
                    "${accent.key} $scheme hero ramp start on its ground",
                    contrastRatio(start, ground) >= 3.0,
                )
                assertTrue(
                    "${accent.key} $scheme hero ramp tip on its ground",
                    contrastRatio(tip, ground) >= 3.0,
                )
                assertTrue(
                    "${accent.key} $scheme hero ramp brightens toward the tip",
                    tip.luminance() > start.luminance(),
                )
            }
        }
    }
}
