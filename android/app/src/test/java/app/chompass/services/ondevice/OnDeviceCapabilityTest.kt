package app.chompass.services.ondevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #46: usable-RAM vs marketed-RAM floors in [OnDeviceCapability].
 *
 * `ActivityManager.MemoryInfo.totalMem` (= kernel MemTotal) reports usable
 * RAM only — a "6 GB phone" (e.g. Pixel 6a) reports ≈5.1–5.5 GiB because
 * ~0.3–0.6 GB is reserved (GPU carveout, kernel, modem). The coarse floor is
 * therefore 4 GiB usable ≈ "6 GB marketed", and E4B gets its own 7 GiB floor
 * (≈ 8 GB marketed — the class that validated it on Pixel 9a).
 */
class OnDeviceCapabilityTest {
    private val GB = 1024L * 1024 * 1024

    @Test
    fun pixel6aClass_6GbMarketed_passesCoarseAndE2B() {
        // 5.5 GiB usable — typical MemTotal for 6 GB marketed (benchmarks
        // detected 5.43 GB on Pixel 6a). Must now clear the floor.
        assertTrue(OnDeviceCapability.hasEnoughRamFor((5.5 * GB).toLong()))
        assertTrue(OnDeviceCapability.hasEnoughRamFor((5.5 * GB).toLong(), ModelCatalog.E2B))
    }

    @Test
    fun pixel6aClass_rejectsE4B() {
        // The 4B model is the documented OOM risk — never offered on 6 GB devices.
        assertFalse(OnDeviceCapability.hasEnoughRamFor((5.5 * GB).toLong(), ModelCatalog.E4B))
    }

    @Test
    fun fourGbMarketedPhone_stillRejected() {
        // ≈3.5 GiB usable is what a 4 GB marketed phone reports — below the coarse floor.
        assertFalse(OnDeviceCapability.hasEnoughRamFor((3.5 * GB).toLong()))
    }

    @Test
    fun eightGbClass_passesE4B() {
        // ≈7.2 GiB usable — Pixel 9a class (8 GB marketed), which validated E4B.
        assertTrue(OnDeviceCapability.hasEnoughRamFor((7.2 * GB).toLong(), ModelCatalog.E4B))
    }

    @Test
    fun floors_are4GiBCoarseAnd7GiBForE4B() {
        assertEquals(4 * GB, OnDeviceCapability.ramFloorFor(null))
        assertEquals(4 * GB, OnDeviceCapability.ramFloorFor(ModelCatalog.E2B))
        assertEquals(7 * GB, OnDeviceCapability.ramFloorFor(ModelCatalog.E4B))
    }

    @Test
    fun visionHeadroom_scalesWithDeviceRam() {
        // 8 GB marketed (OnePlus 6T reports ≈7.4 GiB usable): 5% ≈ 379 MiB.
        val total = (7.4 * GB).toLong() // truncation, same as production input
        assertEquals(total / 20, OnDeviceCapability.visionMemoryHeadroomBytes(total))
    }

    @Test
    fun visionHeadroom_floorsAt256MiB_onSmallDevices() {
        // 4 GiB usable: 5% would be ~205 MiB — clamped up to the min cushion.
        assertEquals(256L * 1024 * 1024, OnDeviceCapability.visionMemoryHeadroomBytes(4 * GB))
    }

    @Test
    fun visionHeadroom_capsAt1GiB_onLargeDevices() {
        // 24 GiB-class: 5% would be 1.2 GiB — capped at the ceiling.
        assertEquals(1_000L * 1024 * 1024, OnDeviceCapability.visionMemoryHeadroomBytes(24 * GB))
    }

    @Test
    fun visionHeadroom_staysProportionalOn16GbClass() {
        // Pixel 10 class (16 GB): 5% ≈ 819 MiB, under the cap.
        assertEquals(
            ((15.0 * GB).toLong()) / 20,
            OnDeviceCapability.visionMemoryHeadroomBytes((15.0 * GB).toLong()),
        )
    }

    @Test
    fun loadHeadroom_scalesLikeVisionButTighter() {
        val total = (7.4 * GB).toLong()
        assertEquals(total / 20, OnDeviceCapability.loadMemoryHeadroomBytes(total))
        // Small devices clamp at 128 MiB, large at the old fixed 512 MiB.
        assertEquals(128L * 1024 * 1024, OnDeviceCapability.loadMemoryHeadroomBytes(2 * GB))
        assertEquals(512L * 1024 * 1024, OnDeviceCapability.loadMemoryHeadroomBytes(32 * GB))
    }

    @Test
    fun visionFloor_onePlus6t_everydayFreeMemoryNowPasses() {
        // End-to-end pure decision the #46 reboot test pinned: 7.4 GiB total,
        // 3.2 GB decimal free (the reporter's refused everyday reading —
        // 4.6.1's 10% margin floored above it) must pass; a starved 2 GiB
        // free must still fail.
        val total = (7.4 * GB).toLong()
        val floor = ModelCatalog.E2B.sizeBytes + OnDeviceCapability.visionMemoryHeadroomBytes(total)
        assertTrue(3_200_000_000L >= floor)
        assertTrue((3.5 * GB).toLong() >= floor)
        assertFalse((2.0 * GB).toLong() >= floor)
    }
}
