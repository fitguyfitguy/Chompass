package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test

class ProteinTargetModeTest {
    @Test
    fun gPerKgTotal_scalesWithWeight() {
        val base = UserProfile(weightKg = 80.0, bodyFatPercentage = 0.20)
            .withProteinTargetMode(ProteinTargetMode.G_PER_KG_TOTAL)
            .withProteinGramsPerKg(2.0)
        assertEquals(160, base.effectiveProtein)

        val heavier = base.copy(weightKg = 90.0)
        assertEquals(180, heavier.effectiveProtein)
    }

    @Test
    fun gPerKgLbm_usesLeanMassWhenBfSet() {
        val profile = UserProfile(weightKg = 80.0, bodyFatPercentage = 0.25)
            .withProteinTargetMode(ProteinTargetMode.G_PER_KG_LBM)
            .withProteinGramsPerKg(2.0)
        // LBM = 80 * 0.75 = 60 → 120 g
        assertEquals(120, profile.effectiveProtein)
    }

    @Test
    fun gramsPerDay_usesCustomProtein() {
        val profile = UserProfile(customProtein = 155)
            .withProteinTargetMode(ProteinTargetMode.GRAMS_PER_DAY)
        assertEquals(155, profile.effectiveProtein)
    }
}
