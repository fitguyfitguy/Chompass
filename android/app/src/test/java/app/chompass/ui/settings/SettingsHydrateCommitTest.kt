package app.chompass.ui.settings

import app.chompass.models.AIProvider
import app.chompass.models.AiProfile
import java.time.Instant
import java.util.UUID
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

    /**
     * Device pass 2026-10-12: on a cold start the slow hydrate (Health Connect,
     * KeyStore, weather) landed after the user had added a profile and picked
     * Custom, repainting the section with the pre-action profile while the store
     * already had the new one active. The AI profile slice must never come from
     * the hydration snapshot.
     */
    @Test
    fun hydrateNeverRepaintsTheAiProfileSlice() {
        val gemini = AiProfile(
            id = UUID.randomUUID(),
            name = "Google Gemini",
            provider = AIProvider.GEMINI,
            model = "gemini-3.5-flash-lite",
            createdAt = Instant.ofEpochMilli(1_700_000_000_000),
        )
        val custom = AiProfile(
            id = UUID.randomUUID(),
            name = "flow-custom",
            provider = AIProvider.CUSTOM_OPENAI,
            model = "",
            createdAt = Instant.ofEpochMilli(1_700_000_100_000),
        )
        // Live state right after the add-profile + provider-pick actions.
        val current = SettingsUiState(
            profiles = listOf(gemini, custom),
            activeProfile = custom,
            apiKeyMasked = "sk-f...flow",
            waterCupSizeMl = 300,
        )
        // Snapshot the hydrate read before those actions.
        val incoming = SettingsUiState(
            profiles = listOf(gemini),
            activeProfile = gemini,
            apiKeyMasked = "AQ.A...lyig",
            waterCupSizeMl = 400,
        )

        // No tracked settings write in flight: the wholesale path used to clobber.
        val wholesale = commitSettingsHydrate(current, incoming, writeGenAtStart = 0, writeGenNow = 0)
        assertEquals(custom, wholesale.activeProfile)
        assertEquals(listOf(gemini, custom), wholesale.profiles)
        assertEquals("sk-f...flow", wholesale.apiKeyMasked)
        assertEquals(400, wholesale.waterCupSizeMl) // hydration still wins for other fields

        val merged = commitSettingsHydrate(current, incoming, writeGenAtStart = 0, writeGenNow = 1)
        assertEquals(custom, merged.activeProfile)
        assertEquals("sk-f...flow", merged.apiKeyMasked)
    }
}
