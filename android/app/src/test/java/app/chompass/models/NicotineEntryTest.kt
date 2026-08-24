package app.chompass.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NicotineEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializesRoundTrip() {
        val entry = NicotineEntry(
            kind = NicotineKind.VAPE,
            count = 3,
            mg = 6.0,
        )
        val decoded = json.decodeFromString<NicotineEntry>(json.encodeToString(NicotineEntry.serializer(), entry))
        assertEquals(entry.id, decoded.id)
        assertEquals(NicotineKind.VAPE, decoded.kind)
        assertEquals(3, decoded.count)
        assertEquals(6.0, decoded.mg)
    }

    @Test
    fun forNowClampsCountAndDropsZeroMg() {
        val zero = NicotineEntry.forNow(NicotineKind.PATCH, count = 0)
        assertEquals(1, zero.count)
        assertEquals(null, zero.mg)

        val withMg = NicotineEntry.forNow(NicotineKind.POUCH, count = 2, mg = 12.0)
        assertEquals(2, withMg.count)
        assertEquals(12.0, withMg.mg)
    }

    @Test
    fun kindStorageRoundTripAndFallback() {
        assertEquals(NicotineKind.POUCH, NicotineKind.fromStorage("pouch"))
        assertEquals(NicotineKind.OTHER, NicotineKind.fromStorage("snus_future"))
        assertEquals(NicotineKind.OTHER, NicotineKind.fromStorage(null))

        val defaultKinds = NicotineKind.quickKindsFromStorage(null)
        assertEquals(listOf(NicotineKind.CIGARETTE, NicotineKind.VAPE, NicotineKind.POUCH), defaultKinds)
        val custom = NicotineKind.quickKindsFromStorage("pouch,gum,pouch,vape")
        assertEquals(listOf(NicotineKind.POUCH, NicotineKind.GUM, NicotineKind.VAPE), custom)
        assertEquals("pouch,gum,vape", NicotineKind.quickKindsToStorage(custom))
    }
}
