package app.chompass.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Flat accent: [AppColors.Calorie] and [AppColors.CalorieStart] mirror the
 * scheme primary passed to [AppColors.setThemeColor]. Widgets and Compose
 * must not drift onto a second gradient stop.
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
}
