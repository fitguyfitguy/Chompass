package app.chompass.services.ondevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #46 dev setting: the vision/load headroom percent is user-adjustable, but
 * the clamp floors stay — the reserve can never drop below 256 MiB vision /
 * 128 MiB load. Defaults (no override) reproduce the shipped 5% math exactly.
 */
class OnDeviceCapabilityHeadroomTest {
    private val GB = 1024L * 1024 * 1024

    @Test
    fun override_percentAppliedBeforeClamp() {
        // Reporter's numbers: ≈7.4 GiB usable, ~2.73 GB free vs the 2.41 GiB
        // E2B that refuses at 5% (≈370 MiB reserve). 4% ≈ 296 MiB passes.
        val total = (7.4 * GB).toLong()

        val fourPercent = OnDeviceCapability.visionMemoryHeadroomBytes(total, headroomPercentOverride = 4)

        assertTrue(fourPercent < OnDeviceCapability.visionMemoryHeadroomBytes(total))
        assertEquals(total * 4 / 100, fourPercent)
        assertTrue(fourPercent >= 256L * 1024 * 1024)
    }

    @Test
    fun override_zero_clampsToMinimum() {
        assertEquals(256L * 1024 * 1024, OnDeviceCapability.visionMemoryHeadroomBytes(8 * GB, headroomPercentOverride = 0))
        assertEquals(128L * 1024 * 1024, OnDeviceCapability.loadMemoryHeadroomBytes(8 * GB, headroomPercentOverride = 0))
    }

    @Test
    fun default_isUnchangedFivePercent() {
        val total = (7.4 * GB).toLong()

        assertEquals(total / 20, OnDeviceCapability.visionMemoryHeadroomBytes(total))
        assertEquals(total / 20, OnDeviceCapability.visionMemoryHeadroomBytes(total, headroomPercentOverride = null))
        assertEquals(total / 20, OnDeviceCapability.loadMemoryHeadroomBytes(total))
    }
}
