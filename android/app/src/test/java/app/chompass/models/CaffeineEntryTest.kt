package app.chompass.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CaffeineEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializesRoundTrip() {
        val entry = CaffeineEntry(
            kind = CaffeineKind.TEA,
            mg = 28.0,
        )
        val decoded = json.decodeFromString<CaffeineEntry>(json.encodeToString(CaffeineEntry.serializer(), entry))
        assertEquals(entry.id, decoded.id)
        assertEquals(CaffeineKind.TEA, decoded.kind)
        assertEquals(28.0, decoded.mg, 0.001)
    }

    @Test
    fun forNowUsesKindDefaultMg() {
        assertEquals(95.0, CaffeineEntry.forNow(CaffeineKind.COFFEE).mg, 0.001)
        assertEquals(28.0, CaffeineEntry.forNow(CaffeineKind.TEA).mg, 0.001)
        assertEquals(80.0, CaffeineEntry.forNow(CaffeineKind.ENERGY).mg, 0.001)
        assertEquals(0.0, CaffeineEntry.forNow(CaffeineKind.OTHER).mg, 0.001)

        val explicit = CaffeineEntry.forNow(CaffeineKind.COFFEE, mg = 150.0)
        assertEquals(150.0, explicit.mg, 0.001)
        // Negative mg clamps to 0 (repository drops such entries).
        assertEquals(0.0, CaffeineEntry.forNow(CaffeineKind.COFFEE, mg = -5.0).mg, 0.001)
    }

    @Test
    fun kindStorageRoundTripAndFallback() {
        assertEquals(CaffeineKind.TEA, CaffeineKind.fromStorage("tea"))
        assertEquals(CaffeineKind.OTHER, CaffeineKind.fromStorage("espresso_future"))
        assertEquals(CaffeineKind.OTHER, CaffeineKind.fromStorage(null))

        val defaultKinds = CaffeineKind.quickKindsFromStorage(null)
        assertEquals(listOf(CaffeineKind.COFFEE, CaffeineKind.TEA, CaffeineKind.ENERGY), defaultKinds)
        val custom = CaffeineKind.quickKindsFromStorage("tea,energy,tea,coffee")
        assertEquals(listOf(CaffeineKind.TEA, CaffeineKind.ENERGY, CaffeineKind.COFFEE), custom)
        assertEquals("tea,energy,coffee", CaffeineKind.quickKindsToStorage(custom))
    }
}
