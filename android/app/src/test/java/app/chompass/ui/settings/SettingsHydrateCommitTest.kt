package app.chompass.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHydrateCommitTest {
    @Test
    fun writeGenUnchanged_takesIncomingWholesale() {
        val current = SettingsUiState(waterCupSizeMl = 300)
        val incoming = SettingsUiState(waterCupSizeMl = 400)
        val result = commitSettingsHydrate(current, incoming, writeGenAtStart = 0, writeGenNow = 0)
        assertEquals(400, result.waterCupSizeMl)
    }

    @Test
    fun writeGenBumped_keepsCurrentCupAndMergesHealth() {
        val current = SettingsUiState(waterCupSizeMl = 500, healthConnectEnabled = false)
        val incoming = SettingsUiState(waterCupSizeMl = 300, healthConnectEnabled = true)
        val result = commitSettingsHydrate(current, incoming, writeGenAtStart = 0, writeGenNow = 1)
        assertEquals(500, result.waterCupSizeMl)
        assertTrue(result.healthConnectEnabled)
    }
}
