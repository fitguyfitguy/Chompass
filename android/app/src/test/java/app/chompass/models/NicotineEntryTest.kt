package app.chompass.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NicotineEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializesRoundTrip() {
        val entry = NicotineEntry(
            kind = "vape",
            count = 3,
            mg = 6.0,
        )
        val decoded = json.decodeFromString<NicotineEntry>(json.encodeToString(NicotineEntry.serializer(), entry))
        assertEquals(entry.id, decoded.id)
        assertEquals("vape", decoded.kind)
        assertEquals(3, decoded.count)
        assertEquals(6.0, decoded.mg!!, 0.001)
    }

    @Test
    fun decodesLegacyEnumNameKinds() {
        // Enum-era month buckets stored the constant name ("VAPE").
        val dateMs = java.time.Instant.parse("2026-08-01T08:00:00Z").toEpochMilli()
        val legacy = """[{"id":"9f1d0a52-3a4c-4b6e-8f2a-1c2d3e4f5a6b","date":$dateMs,"kind":"VAPE","count":2,"mg":6.5}]"""
        val decoded = json.decodeFromString<List<NicotineEntry>>(legacy)
        assertEquals("vape", decoded.single().kind)
        assertEquals(2, decoded.single().count)
        assertEquals(6.5, decoded.single().mg!!, 0.001)
    }

    @Test
    fun decodesCustomPresetIdsVerbatim() {
        val raw = """{"kind":"t_abcd1234","count":1}"""
        assertEquals("t_abcd1234", json.decodeFromString<NicotineEntry>(raw).kind)
    }

    @Test
    fun forNowClampsCountAndDropsZeroMg() {
        val zero = NicotineEntry.forNow("patch", count = 0)
        assertEquals(1, zero.count)
        assertEquals(null, zero.mg)

        val withMg = NicotineEntry.forNow("pouch", count = 2, mg = 12.0)
        assertEquals(2, withMg.count)
        assertEquals(12.0, withMg.mg!!, 0.001)
    }

    @Test
    fun kindStorageRoundTripAndFallback() {
        assertEquals(NicotineKind.POUCH, NicotineKind.fromStorage("pouch"))
        assertEquals(NicotineKind.OTHER, NicotineKind.fromStorage("snus_future"))
        assertEquals(NicotineKind.OTHER, NicotineKind.fromStorage(null))
    }
}
