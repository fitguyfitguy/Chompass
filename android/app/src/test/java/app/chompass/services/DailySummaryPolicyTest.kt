package app.chompass.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DailySummaryPolicyTest {
    private fun input(
        eaten: Int = 1800,
        protein: Int = 140,
        carbs: Int = 180,
        fat: Int = 60,
        bmr: Int = 1600,
        active: Int = 0,
        energyLive: Boolean = false,
        total: Int? = null,
        estimatedActive: Int = 800,
        hasFood: Boolean = true,
    ) = DailySummaryInput(
        eatenKcal = eaten,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat,
        bmrKcal = bmr,
        activeKcal = active,
        energyLive = energyLive,
        totalKcal = total,
        estimatedActiveKcal = estimatedActive,
        hasFoodLogged = hasFood,
    )

    @Test
    fun noFood_skips() {
        val result = DailySummaryPolicy.evaluate(input(hasFood = false))
        assertEquals(DailySummaryVerdict.SKIP, result.verdict)
    }

    @Test
    fun total2300_eaten1800_deficit500() {
        val result = DailySummaryPolicy.evaluate(input(eaten = 1800, total = 2300, energyLive = true))
        assertEquals(DailySummaryVerdict.DEFICIT, result.verdict)
        assertEquals(2300, result.burned)
        assertEquals(1800, result.eaten)
        assertEquals(500, result.delta)
    }

    @Test
    fun total1800_eaten2300_surplus500() {
        val result = DailySummaryPolicy.evaluate(input(eaten = 2300, total = 1800, energyLive = true))
        assertEquals(DailySummaryVerdict.SURPLUS, result.verdict)
        assertEquals(-500, result.delta)
    }

    @Test
    fun total2000_eaten1950_onTarget() {
        val result = DailySummaryPolicy.evaluate(input(eaten = 1950, total = 2000, energyLive = true))
        assertEquals(DailySummaryVerdict.ON_TARGET, result.verdict)
        assertEquals(50, result.delta)
    }

    @Test
    fun bandEdge_100_isOnTarget_101_isDeficit() {
        assertEquals(
            DailySummaryVerdict.ON_TARGET,
            DailySummaryPolicy.evaluate(input(eaten = 1900, total = 2000, energyLive = true)).verdict,
        )
        assertEquals(
            DailySummaryVerdict.DEFICIT,
            DailySummaryPolicy.evaluate(input(eaten = 1899, total = 2000, energyLive = true)).verdict,
        )
    }

    @Test
    fun energyLive_noTotal_usesBmrPlusMeasuredActive() {
        val result = DailySummaryPolicy.evaluate(
            input(eaten = 1800, bmr = 1600, active = 400, energyLive = true, total = null),
        )
        assertEquals(2000, result.burned)
        assertEquals(DailySummaryVerdict.DEFICIT, result.verdict)
        assertEquals(200, result.delta)
    }

    @Test
    fun energyLive_measuredZero_doesNotAddPalEstimate() {
        val result = DailySummaryPolicy.evaluate(
            input(
                eaten = 1800,
                bmr = 1600,
                active = 0,
                energyLive = true,
                total = null,
                estimatedActive = 800,
            ),
        )
        assertEquals(1600, result.burned)
        assertEquals(DailySummaryVerdict.SURPLUS, result.verdict)
        assertEquals(-200, result.delta)
    }

    @Test
    fun noLiveSource_usesBmrPlusPalEstimate() {
        val result = DailySummaryPolicy.evaluate(
            input(
                eaten = 2000,
                bmr = 1600,
                active = 0,
                energyLive = false,
                total = null,
                estimatedActive = 800,
            ),
        )
        assertEquals(2400, result.burned)
        assertEquals(DailySummaryVerdict.DEFICIT, result.verdict)
        assertEquals(400, result.delta)
    }

    @Test
    fun noBurnSource_static() {
        val result = DailySummaryPolicy.evaluate(
            input(
                bmr = 0,
                active = 0,
                energyLive = false,
                total = null,
                estimatedActive = 0,
            ),
        )
        assertEquals(DailySummaryVerdict.STATIC, result.verdict)
    }

    @Test
    fun totalZero_fallsThroughToBmrPlusActive() {
        val result = DailySummaryPolicy.evaluate(
            input(eaten = 1800, bmr = 1600, active = 500, energyLive = true, total = 0),
        )
        assertEquals(2100, result.burned)
        assertEquals(DailySummaryVerdict.DEFICIT, result.verdict)
    }

    @Test
    fun resolveBurned_nullWhenNothingResolvable() {
        assertNull(
            DailySummaryPolicy.resolveBurned(
                input(bmr = 0, active = 0, energyLive = false, total = null, estimatedActive = 0),
            ),
        )
    }

    @Test
    fun summaryDate_eveningIsToday() {
        val now = ZonedDateTime.of(2026, 8, 20, 21, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(now.toLocalDate(), DailySummaryPolicy.summaryDate(now))
    }

    @Test
    fun summaryDate_afterMidnightUsesYesterday() {
        val now = ZonedDateTime.of(2026, 8, 21, 1, 30, 0, 0, ZoneOffset.UTC)
        assertEquals(now.toLocalDate().minusDays(1), DailySummaryPolicy.summaryDate(now))
    }

    @Test
    fun netCarbs_subtractsFiber_notBelowZero() {
        assertEquals(140, DailySummaryPolicy.netCarbsG(160.0, 20.0))
        assertEquals(0, DailySummaryPolicy.netCarbsG(10.0, 20.0))
        assertEquals(160, DailySummaryPolicy.netCarbsG(160.0, 0.0))
    }

    @Test
    fun summaryDate_fourAmIsToday() {
        val now = ZonedDateTime.of(2026, 8, 21, 4, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(now.toLocalDate(), DailySummaryPolicy.summaryDate(now))
    }
}
