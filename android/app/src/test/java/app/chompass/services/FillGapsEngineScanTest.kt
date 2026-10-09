package app.chompass.services

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Pure truth tables for the fill-missing scan shaping and Home banner gate:
 * favorites before diary, gated on macro-level gaps (micro-only gaps stay
 * per-entry in the edit sheet), stable per-item-set signatures, and the
 * once-per-day / sticky-dismissal banner rules.
 */
class FillGapsEngineScanTest {

    private fun gapped(name: String, ts: Instant = Instant.parse("2026-10-10T12:00:00Z")) = FoodEntry(
        name = name,
        calories = 0,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        timestamp = ts,
        source = FoodSource.MANUAL,
    )

    /** Non-zero macros: no macro gaps even though micros are unset. */
    private fun complete(name: String, ts: Instant = Instant.parse("2026-10-10T12:00:00Z")) = FoodEntry(
        name = name,
        calories = 100,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        timestamp = ts,
        source = FoodSource.MANUAL,
    )

    @Test
    fun favoritesComeFirst_diarySecond_bothGapGated() {
        val favorites = listOf(complete("Aaa"), gapped("Zed"))
        val noon = Instant.parse("2026-10-10T12:00:00Z")
        val morning = Instant.parse("2026-10-10T08:00:00Z")
        val month = listOf(
            gapped("D1", noon),
            complete("Ccc", noon),
            gapped("D2", morning),
        )

        val scan = FillGapsEngine.computeScan(favorites, month)

        assertEquals(listOf("Zed", "D1", "D2"), scan.items.map { it.entry.name })
        assertEquals(listOf(true, false, false), scan.items.map { it.favorite })
        assertEquals(3, scan.count)
    }

    @Test
    fun signature_stableForSameSet_changesWhenSetChanges() {
        val favorites = listOf(gapped("Zed"))
        val month = listOf(gapped("D1"), gapped("D2", Instant.parse("2026-10-10T08:00:00Z")))

        val a = FillGapsEngine.computeScan(favorites, month)
        val b = FillGapsEngine.computeScan(favorites.shuffled(), month.shuffled())
        assertEquals(a.signature, b.signature)

        val c = FillGapsEngine.computeScan(favorites, month + gapped("D3"))
        assertNotEquals(a.signature, c.signature)
    }

    @Test
    fun banner_hiddenWhenAiDisabled_orEmptyScan_orDismissed() {
        val scan = FillGapsEngine.computeScan(listOf(gapped("D1")), emptyList())

        assertNull(FillGapsEngine.bannerState(scan, aiEnabled = false, dismissed = emptySet()))
        assertNull(
            FillGapsEngine.bannerState(
                FillGapsEngine.computeScan(emptyList(), emptyList()),
                aiEnabled = true, dismissed = emptySet(),
            )
        )
        assertNull(FillGapsEngine.bannerState(scan, aiEnabled = true, dismissed = setOf(scan.signature)))
        // Visible otherwise; the once-per-day gate lives in the HomeViewModel.
        assertEquals(
            FillGapsBanner(scan.count, scan.signature),
            FillGapsEngine.bannerState(scan, aiEnabled = true, dismissed = emptySet()),
        )
    }
}
