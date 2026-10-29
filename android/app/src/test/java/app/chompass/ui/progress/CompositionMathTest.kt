package app.chompass.ui.progress

import app.chompass.models.BodyCompositionEntry
import app.chompass.models.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * TBW-PCT: the water card's percentage badge is a derived display value — total body
 * water divided by the nearest weigh-in within ±12 h. These cases pin the join window
 * and the "no denominator, no badge" rule.
 */
class CompositionMathTest {
    private val t0 = Instant.parse("2026-07-20T07:10:00Z")

    @Test
    fun `uses the nearest weigh-in inside the twelve hour window`() {
        val entry = BodyCompositionEntry(date = t0, totalBodyWaterKg = 46.0)
        val weights = listOf(
            WeightEntry(date = t0.minus(Duration.ofHours(11)), weightKg = 80.0),
            WeightEntry(date = t0.plus(Duration.ofHours(2)), weightKg = 79.0),
        )
        // 46 / 79 × 100 — the closer weigh-in wins, not the earlier one.
        assertEquals(58.227, totalBodyWaterPercent(entry, weights)!!, 0.001)
    }

    @Test
    fun `weigh-in outside the window yields no percentage`() {
        val entry = BodyCompositionEntry(date = t0, totalBodyWaterKg = 46.0)
        val weights = listOf(WeightEntry(date = t0.plus(Duration.ofHours(13)), weightKg = 80.0))
        assertNull(totalBodyWaterPercent(entry, weights))
    }

    @Test
    fun `no weigh-in or no water value yields no percentage`() {
        assertNull(totalBodyWaterPercent(BodyCompositionEntry(date = t0, totalBodyWaterKg = 46.0), emptyList()))
        assertNull(
            totalBodyWaterPercent(
                BodyCompositionEntry(date = t0, boneMassKg = 3.2),
                listOf(WeightEntry(date = t0, weightKg = 80.0)),
            )
        )
    }

    @Test
    fun `zero or non-finite water never divides`() {
        val weights = listOf(WeightEntry(date = t0, weightKg = 80.0))
        assertNull(totalBodyWaterPercent(BodyCompositionEntry(date = t0, totalBodyWaterKg = 0.0), weights))
        assertNull(totalBodyWaterPercent(BodyCompositionEntry(date = t0, totalBodyWaterKg = Double.NaN), weights))
        assertNull(
            totalBodyWaterPercent(
                BodyCompositionEntry(date = t0, totalBodyWaterKg = 46.0),
                listOf(WeightEntry(date = t0, weightKg = 0.0)),
            )
        )
    }
}
